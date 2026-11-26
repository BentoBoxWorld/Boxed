package world.bentobox.boxed.listeners;

import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

import world.bentobox.boxed.Boxed;

/**
 * Makes the fortress in the Nether of a box work. The Nether is copied block by block, so
 * the game does not know there is a fortress there and never spawns its mobs. This swaps
 * some of the mobs that spawn on nether bricks for wither skeletons, blazes and skeletons,
 * in the same proportions as a real fortress.
 *
 * @author tastybento
 */
public class FortressMobsListener implements Listener {

    /**
     * How often each mob spawns in a real fortress, out of {@link #TOTAL}. The rest of the
     * time the mob the game picked is left alone.
     */
    private static final int WITHER_SKELETON = 8;
    private static final int BLAZE = 10;
    private static final int SKELETON = 2;
    private static final int TOTAL = 28;
    /**
     * Skeletons need it this dark. Blazes spawn in a little more light.
     */
    private static final int DARK = 7;
    private static final int BLAZE_LIGHT = 11;

    private final Boxed addon;
    private final Random random;
    private boolean swapping;

    /**
     * @param addon addon
     */
    public FortressMobsListener(Boxed addon) {
        this(addon, new Random());
    }

    /**
     * @param addon addon
     * @param random source of the dice rolls
     */
    FortressMobsListener(Boxed addon, Random random) {
        this.addon = addon;
        this.random = random;
    }

    /**
     * Swaps a mob that is spawning on nether bricks for a fortress mob.
     * @param e event
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        Location loc = e.getLocation();
        if (swapping || e.getSpawnReason() != SpawnReason.NATURAL || !addon.getSettings().isNetherFortressMobs()
                || loc.getWorld() == null || !loc.getWorld().equals(addon.getNetherWorld())) {
            return;
        }
        Block feet = loc.getBlock();
        if (feet.getRelative(BlockFace.DOWN).getType() != Material.NETHER_BRICKS) {
            return;
        }
        EntityType type = pick(feet);
        if (type == null || type == e.getEntityType()) {
            return;
        }
        e.setCancelled(true);
        // The new mob fires this event too
        swapping = true;
        try {
            loc.getWorld().spawnEntity(loc, type, SpawnReason.NATURAL);
        } finally {
            swapping = false;
        }
    }

    /**
     * @param feet the block the mob spawns in
     * @return the fortress mob to spawn instead, or null to leave the spawn alone
     */
    private EntityType pick(Block feet) {
        int roll = random.nextInt(TOTAL);
        int light = feet.getLightFromBlocks();
        if (roll < WITHER_SKELETON) {
            // Wither skeletons are nearly three blocks tall
            boolean room = feet.getRelative(BlockFace.UP).isPassable() && feet.getRelative(BlockFace.UP, 2).isPassable();
            return light <= DARK && room ? EntityType.WITHER_SKELETON : null;
        }
        if (roll < WITHER_SKELETON + BLAZE) {
            return light <= BLAZE_LIGHT ? EntityType.BLAZE : null;
        }
        if (roll < WITHER_SKELETON + BLAZE + SKELETON) {
            return light <= DARK ? EntityType.SKELETON : null;
        }
        return null;
    }
}
