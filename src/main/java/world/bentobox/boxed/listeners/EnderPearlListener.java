package world.bentobox.boxed.listeners;

import java.io.IOException;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.managers.RanksManager;
import world.bentobox.boxed.Boxed;

/**
 * Listens for Ender Pearl throws and moves the island center
 * @author tastybento
 *
 */
public class EnderPearlListener implements Listener {

    private final Boxed addon;

    /**
     * @param addon
     */
    public EnderPearlListener(Boxed addon) {
        this.addon = addon;
    }



    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEnderPearlLand(ProjectileHitEvent e) {
        if (!e.getEntityType().equals(EntityType.ENDER_PEARL)
                || e.getHitBlock() == null
                || !addon.getPlugin().getIWM().inWorld(e.getHitBlock().getLocation())) {
            return;
        }
        Location l = e.getHitBlock().getRelative(BlockFace.UP).getLocation();
        EnderPearl ep = (EnderPearl)e.getEntity();
        if (ep.getShooter() instanceof Player) {
            User u = User.getInstance((Player)ep.getShooter());
            addon.getIslands().getIslandAt(l).ifPresent(i -> {
                // TODO make this a flag
                if (i.getMemberSet(RanksManager.OWNER_RANK).contains(u.getUniqueId())
                        && addon.getIslands().isSafeLocation(l)) {
                    // Reset home locations
                    i.getMemberSet().forEach(uuid -> {
                        addon.getPlayers().getPlayer(uuid).clearHomeLocations(l.getWorld());
                        addon.getPlayers().getPlayer(uuid).setHomeLocation(l);
                    });
                    try {
                        i.setProtectionCenter(l);
                        u.getPlayer().playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 2F, 2F);
                    } catch (IOException e1) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    }
                }
            });
        }
    }

}
