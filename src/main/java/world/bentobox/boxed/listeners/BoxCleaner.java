package world.bentobox.boxed.listeners;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.bentobox.api.events.BentoBoxReadyEvent;
import world.bentobox.bentobox.api.events.island.IslandDeletedEvent;
import world.bentobox.bentobox.api.events.island.IslandEvent;
import world.bentobox.bentobox.api.events.island.IslandEvent.Reason;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.generators.chunks.BoxedBlockPopulator;
import world.bentobox.boxed.generators.template.TemplateChunk;

/**
 * Puts the land of a deleted box back the way it was, so the position can be used again.
 * <p>
 * BentoBox cleans up deleted islands by deleting whole region files, and only when nothing
 * else needs the file. Box positions share their region files with their neighbours, so on a
 * busy server that would almost never happen and deleted boxes would never go away. Boxed
 * knows exactly what the land of every position should be, because it is a copy of the area's
 * template, so it restores the land itself: every generated chunk of the position, in the
 * overworld and the Nether, is compared with the template and anything different is put back.
 * Structures, mobs and items go too. When that is done the old box's record is removed, which
 * frees the position for a new box.
 * <p>
 * The work is done a little each tick. Boxes that are waiting when the server stops are picked
 * up again when it starts, because they are still marked as deleted.
 *
 * @author tastybento
 */
public class BoxCleaner implements Listener {

    /**
     * How long the cleaner may work each tick. A tick is 50 ms.
     */
    private static final long BUDGET = TimeUnit.MILLISECONDS.toNanos(10);

    /**
     * A box position to clean
     * @param islandId id of the deleted box whose record is removed afterwards, or null
     * @param centre middle of the position in the overworld
     */
    private record Job(@Nullable String islandId, Location centre) {
    }

    private record ChunkRef(World world, int x, int z) {
    }

    private final Boxed addon;
    private final BoxedBlockPopulator populator;
    private final Queue<Job> jobs = new ArrayDeque<>();
    private final Set<String> queued = new HashSet<>();
    private Job current;
    private Iterator<ChunkRef> remaining;
    private boolean loading;
    private int chunksRestored;
    private int blocksRestored;

    /**
     * @param addon Boxed
     */
    public BoxCleaner(Boxed addon) {
        this.addon = addon;
        this.populator = new BoxedBlockPopulator(addon);
        Bukkit.getScheduler().runTaskTimer(addon.getPlugin(), this::tick, 100, 1);
    }

    /**
     * Queues deleted boxes that were not cleaned before the server last stopped.
     * @param event BentoBoxReadyEvent
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBentoBoxReady(BentoBoxReadyEvent event) {
        World world = addon.getOverWorld();
        if (world != null) {
            List.copyOf(addon.getIslands().getIslands(world)).stream().filter(Island::isDeletable)
                    .forEach(this::clean);
        }
    }

    /**
     * Queues a box when it is deleted or replaced by a reset.
     * @param event IslandDeletedEvent
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandDeleted(IslandDeletedEvent event) {
        Island island = event.getIsland();
        if (island != null && addon.inWorld(island.getWorld()) && island.isDeletable()) {
            clean(island);
        }
    }

    /**
     * Queues a deleted box for cleaning.
     * @param island the deleted box
     */
    public void clean(Island island) {
        queue(new Job(island.getUniqueId(), addon.getBoxCentre(island.getCenter())));
    }

    /**
     * Queues a box position for cleaning. If there is a deleted box there its record is removed
     * afterwards. Positions with a box that is in use are refused.
     * @param location anywhere in the position, in any of the game worlds
     * @return true if the position was queued, false if a box is in use there
     */
    public boolean clean(Location location) {
        Location centre = addon.getBoxCentre(location);
        centre.setWorld(addon.getOverWorld());
        Optional<Island> box = addon.getIslands().getIslandAt(centre);
        if (box.isPresent() && !box.get().isDeletable()) {
            return false;
        }
        queue(new Job(box.map(Island::getUniqueId).orElse(null), centre));
        return true;
    }

    private void queue(Job job) {
        if (queued.add(key(job))) {
            jobs.add(job);
        }
    }

    private static String key(Job job) {
        return job.centre().getBlockX() + "," + job.centre().getBlockZ();
    }

    /**
     * @return the number of box positions waiting to be cleaned, including the one in progress
     */
    public int getQueueSize() {
        return queued.size();
    }

    /**
     * Does a tick's worth of cleaning.
     */
    void tick() {
        if (current == null) {
            current = jobs.poll();
            if (current == null) {
                return;
            }
            remaining = chunks(current).iterator();
            chunksRestored = 0;
            blocksRestored = 0;
        }
        long start = System.nanoTime();
        // Only one chunk is loaded at a time, and nothing else is done while waiting for it
        while (!loading && System.nanoTime() - start < BUDGET) {
            if (!remaining.hasNext()) {
                finish();
                return;
            }
            restore(remaining.next());
        }
    }

    /**
     * Lists every chunk of a box position in the overworld and the Nether.
     */
    private List<ChunkRef> chunks(Job job) {
        int size = addon.getChunkGenerator(World.Environment.NORMAL).getSize();
        int cx = job.centre().getBlockX() >> 4;
        int cz = job.centre().getBlockZ() >> 4;
        List<ChunkRef> list = new ArrayList<>();
        for (World world : new World[] { addon.getOverWorld(), addon.getNetherWorld() }) {
            if (world == null) {
                continue;
            }
            for (int x = cx - size; x < cx + size; x++) {
                for (int z = cz - size; z < cz + size; z++) {
                    list.add(new ChunkRef(world, x, z));
                }
            }
        }
        return list;
    }

    private void restore(ChunkRef ref) {
        // Chunks nobody has been to will be made from the template when they are needed
        if (!ref.world().isChunkGenerated(ref.x(), ref.z())) {
            return;
        }
        if (ref.world().isChunkLoaded(ref.x(), ref.z())) {
            restore(ref.world().getChunkAt(ref.x(), ref.z()));
            return;
        }
        loading = true;
        ref.world().getChunkAtAsync(ref.x(), ref.z(), false).whenComplete((chunk, error) -> {
            try {
                if (chunk != null) {
                    restore(chunk);
                }
            } finally {
                loading = false;
            }
        });
    }

    private void restore(Chunk chunk) {
        TemplateChunk template = addon.getTemplateChunk(chunk.getWorld().getEnvironment(), chunk.getX(),
                chunk.getZ());
        if (template != null) {
            blocksRestored += restore(chunk, template, populator);
            chunksRestored++;
        }
    }

    /**
     * Makes a chunk the same as its template again: removes everything that is not a player,
     * puts back every block that is different, refills the template's chests and spawners and
     * brings back its mobs.
     * @param chunk chunk in a game world
     * @param template the template for the chunk
     * @param populator used to fill chests and set up spawners
     * @return the number of blocks that were put back
     */
    static int restore(Chunk chunk, TemplateChunk template, BoxedBlockPopulator populator) {
        World world = chunk.getWorld();
        for (Entity entity : chunk.getEntities()) {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        }
        int changed = 0;
        ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        for (int sectionY = minY; sectionY < maxY; sectionY += 16) {
            // Most of the sky is air in both, which is quick to check
            if (snapshot.isSectionEmpty((sectionY - minY) >> 4) && template.isAirSection(sectionY)) {
                continue;
            }
            for (int y = sectionY; y < sectionY + 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockData wanted = template.getBlockData(x, y, z);
                        if (wanted != null && !wanted.equals(snapshot.getBlockData(x, y, z))) {
                            chunk.getBlock(x, y, z).setBlockData(wanted, false);
                            changed++;
                        }
                    }
                }
            }
        }
        // Chests, spawners and so on get their contents back, whether or not the block changed
        template.getChests().forEach(c -> {
            Block block = chunk.getBlock(c.relativeLoc().getBlockX(), c.relativeLoc().getBlockY(),
                    c.relativeLoc().getBlockZ());
            populator.setBlockState(block.getState(), c.chest());
        });
        template.getEntities().forEach(e -> {
            Location l = chunk.getBlock(0, 0, 0).getLocation().add(e.relativeLoc());
            l.setY(e.relativeLoc().getY());
            e.entity().configureEntity(world.spawnEntity(l, e.entity().getType()));
        });
        return changed;
    }

    /**
     * Ends the current job. The record of the deleted box is removed so that the position is
     * free, unless the box has been brought back in the meantime.
     */
    private void finish() {
        Job job = current;
        current = null;
        remaining = null;
        queued.remove(key(job));
        String where = job.centre().getBlockX() + "," + job.centre().getBlockZ();
        String id = job.islandId();
        if (id != null) {
            Optional<Island> box = addon.getIslands().getIslandById(id);
            if (box.isPresent() && box.get().isDeletable()) {
                addon.getIslands().getIslandCache().deleteIslandFromCache(id);
                addon.getIslands().deleteIslandId(id);
                // Tell other addons the box is completely gone, as BentoBox does
                IslandEvent.builder().island(box.get()).reason(Reason.PURGED).build();
            }
        }
        addon.log("Cleaned the box position at " + where + ": put back " + blocksRestored + " blocks in "
                + chunksRestored + " chunks. It can be used again.");
    }
}
