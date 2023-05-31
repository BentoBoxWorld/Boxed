package world.bentobox.boxed.listeners;

import java.io.IOException;

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

    /**
     * @param addon addon
     */
    public EnderPearlListener(Boxed addon) {
        this.addon = addon;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent e) {        
        if (!addon.inWorld(e.getFrom()) || !e.getPlayer().getGameMode().equals(GameMode.SURVIVAL)
                || (e.getTo() != null && !addon.inWorld(e.getTo()))
                || addon.getIslands().getSpawn(e.getFrom().getWorld()).map(spawn -> spawn.onIsland(e.getTo())).orElse(false)
                ) {
            return;
        }
        
        User u = User.getInstance(e.getPlayer());
        // If the to is outside the box, cancel it
        if (e.getTo() != null) {
            Island i = addon.getIslands().getIsland(e.getFrom().getWorld(), u);
            if (i == null || !i.onIsland(e.getTo())) {
                u.sendMessage("boxed.general.errors.no-teleport-outside");
                e.setCancelled(true);
                return;
            }
        }
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
        if (e.getEntity() instanceof EnderPearl ep && ep.getShooter() instanceof Player player) {
            User u = User.getInstance(player);
            // Check if enderpearl is inside or outside the box
            // Get user's box
            Island is = addon.getIslands().getIsland(w, u);
            if (is == null) {
                return; // Nothing to do
            }
            
            // Get the box that the player is in
            addon.getIslands().getIslandAt(u.getLocation()).ifPresent(fromIsland -> {
                // Check that it is their box
                if (!is.getUniqueId().equals(fromIsland.getUniqueId())) {
                    return;
                }
                // Find where the pearl landed
                addon.getIslands().getIslandAt(l).ifPresentOrElse(toIsland -> {
                    if (fromIsland.getUniqueId().equals(toIsland.getUniqueId())) {
                        if (!toIsland.onIsland(l)) {
                            // Moving is allowed
                            moveBox(u, fromIsland, l);
                            Util.teleportAsync(player, l);
                            return;
                        }
                    } else {
                        // Different box. This is never allowed. Cancel the throw
                        e.setCancelled(true);
                        u.sendMessage("boxed.general.errors.no-teleport-outside");
                        return;            
                    }
                }, () -> {
                    // No box. This is never allowed. Cancel the throw
                    e.setCancelled(true);
                    u.sendMessage("boxed.general.errors.no-teleport-outside");
                    return;     
                });

            });
        }
    }



    private void moveBox(User u, Island fromIsland, Location l) {
        // Reset home locations
        fromIsland.getMemberSet().forEach(uuid -> addon.getIslands().setHomeLocation(uuid, l));
        try {
            fromIsland.setProtectionCenter(l);
            fromIsland.setSpawnPoint(l.getWorld().getEnvironment(), l);
            u.getPlayer().playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 2F, 2F);
        } catch (IOException e1) {
            addon.logError("Could not move box " + e1.getMessage());
        }

    }

}
