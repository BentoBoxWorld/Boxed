package world.bentobox.boxed;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import world.bentobox.bentobox.BentoBox;

/**
 * @author tastybento
 *
 */
public class DebugListener implements Listener {

    private final Boxed addon;
    private int size;

    /**
     * @param addon
     */
    public DebugListener(Boxed addon) {
        this.addon = addon;
        this.size = addon.getSettings().getIslandDistance();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo().getBlockX() != e.getFrom().getBlockX() || e.getTo().getBlockZ() != e.getFrom().getBlockZ()) {
            int chunkX = e.getTo().getChunk().getX();
            int chunkZ = e.getTo().getChunk().getZ();
            int xx = Math.floorMod(chunkX, size);
            int zz = Math.floorMod(chunkZ, size);
            BentoBox.getInstance().logDebug("x = " + e.getTo().getBlockX() + " z = " + e.getTo().getBlockZ());
            BentoBox.getInstance().logDebug("ChunkX = " + chunkX + " ChunkZ = " + chunkZ);
            BentoBox.getInstance().logDebug("CalcChunk X = " + xx + " Calc ChunkZ = " + zz + " should loop");

        }
    }
}
