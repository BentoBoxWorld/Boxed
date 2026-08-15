package world.bentobox.boxed.listeners;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;
import world.bentobox.boxed.Boxed;

/**
 * Listens for Ender Pearl throws and moves the island center
 * @author tastybento
 *
 */
public class EnderPearlListener implements Listener {

    private final Boxed addon;
    private Set<Player> movingPlayer = new HashSet<>();

    /**
     * @param addon addon
     */
    private static final String NO_TELEPORT_OUTSIDE = "boxed.general.errors.no-teleport-outside";

    public EnderPearlListener(Boxed addon) {
        this.addon = addon;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent e) {
        if (e.getCause() == TeleportCause.ENDER_PEARL && movingPlayer.contains(e.getPlayer())) {
            movingPlayer.remove(e.getPlayer());
            return; // Allow the teleport this one time
        }
        if (!addon.inWorld(e.getFrom()) || !e.getPlayer().getGameMode().equals(GameMode.SURVIVAL)
                || !addon.inWorld(e.getTo())
                || addon.getIslands().getSpawn(e.getFrom().getWorld()).map(spawn -> spawn.onIsland(e.getTo())).orElse(false)
                ) {
            return;
        }

        User u = User.getInstance(e.getPlayer());
        // If the to-location is outside the box, cancel it
        addon.getIslands().getIslandAt(e.getTo()).ifPresent(i -> {
            if (!i.onIsland(e.getTo())) {
                u.sendMessage(NO_TELEPORT_OUTSIDE);
                addon.logWarning(e.getPlayer().getName() + " tried to teleport outside of their box from "
                        + e.getFrom() + " to " + e.getTo());
                e.setCancelled(true);
            }
        });
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEnderPearlLand(ProjectileHitEvent e) {
        if (!e.getEntityType().equals(EntityType.ENDER_PEARL)
                || e.getHitBlock() == null
                || !addon.inWorld(e.getHitBlock().getLocation())
                || !Boxed.ALLOW_MOVE_BOX.isSetForWorld(e.getHitBlock().getWorld())
                ) {
            return;
        }  
        // Moving box is allowed
        Location l = e.getHitBlock().getRelative(BlockFace.UP).getLocation();
        World w = e.getHitBlock().getWorld();
        if (!(e.getEntity() instanceof EnderPearl ep) || !(ep.getShooter() instanceof Player player)) {
            return;
        }
        User u = User.getInstance(player);
        // Check if enderpearl is inside or outside the box
        // Get user's box
        Island is = addon.getIslands().getIsland(w, u);
        if (is == null) {
            return; // Nothing to do
        }
        // Get the box that the player is in and check that it is their box
        addon.getIslands().getIslandAt(u.getLocation())
        .filter(fromIsland -> is.getUniqueId().equals(fromIsland.getUniqueId()))
        .ifPresent(fromIsland -> handlePearlLanding(e, u, fromIsland, l));
    }

    /**
     * Handles an ender pearl thrown by a player from inside their own box.
     * @param e projectile hit event
     * @param u the thrower
     * @param fromIsland the thrower's box
     * @param l where the pearl landed
     */
    private void handlePearlLanding(ProjectileHitEvent e, User u, Island fromIsland, Location l) {
        // Find where the pearl landed
        addon.getIslands().getIslandAt(l).ifPresentOrElse(toIsland -> {
            if (fromIsland.getUniqueId().equals(toIsland.getUniqueId())) {
                if (!toIsland.onIsland(l)) {
                    // Moving is allowed
                    moveBox(u, fromIsland, l);
                    Util.teleportAsync(u.getPlayer(), l, TeleportCause.ENDER_PEARL);
                }
            } else {
                // Different box. This is never allowed. Cancel the throw
                cancelThrow(e, u, fromIsland, toIsland.getCenter());
            }
        }, () -> cancelThrow(e, u, fromIsland, "some place outside")); // No box. This is never allowed. Cancel the throw
    }

    private void cancelThrow(ProjectileHitEvent e, User u, Island fromIsland, Object destination) {
        e.setCancelled(true);
        u.sendMessage(NO_TELEPORT_OUTSIDE);
        addon.logWarning("Enderpearl: " + u.getName() + " tried to teleport between boxes from "
                + fromIsland.getCenter() + " to " + destination);
    }



    private void moveBox(User u, Island fromIsland, Location l) {
        // Reset home locations
        fromIsland.getMemberSet().forEach(uuid -> addon.getIslands().setHomeLocation(uuid, l));
        try {
            fromIsland.setProtectionCenter(l);
            fromIsland.setSpawnPoint(l.getWorld().getEnvironment(), l);
            u.getPlayer().playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 2F, 2F);
            movingPlayer.add(u.getPlayer());
        } catch (IOException e1) {
            addon.logError("Could not move box " + e1.getMessage());
        }

    }

}
