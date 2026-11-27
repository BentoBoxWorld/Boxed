package world.bentobox.boxed.workshop;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Makes a square of chunks in one world the same as in another: blocks, biomes and what is
 * in chests and the like. Only what differs is changed. The chunks are loaded and compared
 * without stopping the server, and the changes are made a few milliseconds at a time.
 * <p>
 * The workshop uses this to make land again after its biomes change. A chunk cannot simply
 * be generated again where it is, because trees and the like that hang over from the chunks
 * next to it would be lost. So the land is generated in a world that is thrown away
 * afterwards, where everything is whole, and copied across.
 *
 * @author tastybento
 */
final class LandCopier {

    /**
     * How long changes are made for in one tick, in nanoseconds
     */
    private static final long BUDGET = 20_000_000L;
    /**
     * Blocks that have no states, so two of the same kind are the same block. These make up
     * most of the ground, and knowing this saves looking at them closely.
     */
    private static final Set<Material> PLAIN = EnumSet.of(Material.AIR, Material.CAVE_AIR, Material.STONE,
            Material.DIRT, Material.GRANITE, Material.DIORITE, Material.ANDESITE, Material.TUFF, Material.BEDROCK,
            Material.NETHERRACK, Material.SAND, Material.SANDSTONE, Material.GRAVEL);

    /**
     * What a copy changed.
     * @param blocks how many blocks
     * @param biomes how many 4 by 4 by 4 cells of biome
     */
    record Result(int blocks, int biomes) {
    }

    private record BlockChange(int x, int y, int z, BlockData data) {
    }

    private record BiomeChange(int x, int y, int z, Biome biome) {
    }

    private record Source(ChunkSnapshot snapshot, List<BlockState> states) {
    }

    private final Plugin plugin;
    private final World from;
    private final World to;
    private final int minY;
    private final int maxY;
    private final List<BlockChange> blocks = new ArrayList<>();
    private final List<BiomeChange> biomes = new ArrayList<>();
    private final List<BlockState> states = new ArrayList<>();
    private final List<Runnable> held = new ArrayList<>();

    /**
     * @param plugin plugin to schedule with
     * @param from world to copy from
     * @param to world to change. It must be as tall as the other.
     */
    LandCopier(Plugin plugin, World from, World to) {
        this.plugin = plugin;
        this.from = from;
        this.to = to;
        this.minY = to.getMinHeight();
        this.maxY = to.getMaxHeight();
    }

    /**
     * Copies a square of chunks. Call on the main thread.
     * @param minX lowest chunk x
     * @param minZ lowest chunk z
     * @param maxX highest chunk x
     * @param maxZ highest chunk z
     * @return what changed. Completes on the main thread, with an error if the land could not be loaded.
     */
    CompletableFuture<Result> copy(int minX, int minZ, int maxX, int maxZ) {
        List<CompletableFuture<Source>> sources = new ArrayList<>();
        List<CompletableFuture<ChunkSnapshot>> targets = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // Chunks are read as they arrive, which is on the main thread, before they can unload
                sources.add(from.getChunkAtAsync(x, z)
                        .thenApply(c -> new Source(c.getChunkSnapshot(false, true, false), List.of(c.getTileEntities()))));
                // The chunks that are changed have to stay loaded until the end: a biome
                // cannot be set in a chunk that is not
                int chunkX = x;
                int chunkZ = z;
                to.addPluginChunkTicket(x, z, plugin);
                held.add(() -> to.removePluginChunkTicket(chunkX, chunkZ, plugin));
                targets.add(to.getChunkAtAsync(x, z).thenApply(c -> c.getChunkSnapshot(false, true, false)));
            }
        }
        CompletableFuture<Result> result = new CompletableFuture<>();
        // Completes on the main thread, whichever way it goes
        result.whenComplete((r, error) -> held.forEach(Runnable::run));
        List<CompletableFuture<?>> all = new ArrayList<>(sources);
        all.addAll(targets);
        CompletableFuture.allOf(all.toArray(CompletableFuture[]::new)).whenComplete((v, error) -> {
            if (error != null) {
                Bukkit.getScheduler().runTask(plugin, () -> result.completeExceptionally(error));
                return;
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    for (int i = 0; i < sources.size(); i++) {
                        Source source = sources.get(i).join();
                        compare(source.snapshot(), targets.get(i).join());
                        states.addAll(source.states());
                    }
                } catch (RuntimeException e) {
                    Bukkit.getScheduler().runTask(plugin, () -> result.completeExceptionally(e));
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> apply(result));
            });
        });
        return result;
    }

    /**
     * Finds what differs between two copies of the same chunk. Can run on any thread.
     */
    private void compare(ChunkSnapshot source, ChunkSnapshot target) {
        int blockX = source.getX() << 4;
        int blockZ = source.getZ() << 4;
        for (int section = 0; section < (maxY - minY) >> 4; section++) {
            if (source.isSectionEmpty(section) && target.isSectionEmpty(section)) {
                continue;
            }
            int bottom = minY + (section << 4);
            for (int y = bottom; y < bottom + 16; y++) {
                compareLayer(source, target, blockX, y, blockZ);
            }
        }
        for (int y = minY; y < maxY; y += 4) {
            compareBiomes(source, target, blockX, y, blockZ);
        }
    }

    private void compareBiomes(ChunkSnapshot source, ChunkSnapshot target, int blockX, int y, int blockZ) {
        for (int x = 0; x < 16; x += 4) {
            for (int z = 0; z < 16; z += 4) {
                Biome biome = source.getBiome(x, y, z);
                if (!biome.equals(target.getBiome(x, y, z))) {
                    biomes.add(new BiomeChange(blockX + x, y, blockZ + z, biome));
                }
            }
        }
    }

    private void compareLayer(ChunkSnapshot source, ChunkSnapshot target, int blockX, int y, int blockZ) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                Material type = source.getBlockType(x, y, z);
                boolean sameType = type == target.getBlockType(x, y, z);
                if (sameType && PLAIN.contains(type)) {
                    continue;
                }
                BlockData data = source.getBlockData(x, y, z);
                if (!sameType || !data.equals(target.getBlockData(x, y, z))) {
                    blocks.add(new BlockChange(blockX + x, y, blockZ + z, data));
                }
            }
        }
    }

    /**
     * Makes the changes, a few milliseconds each tick. Call on the main thread.
     */
    private void apply(CompletableFuture<Result> result) {
        new BukkitRunnable() {
            private int block;
            private int biome;
            private int state;
            private final Set<Long> recoloured = new HashSet<>();

            /**
             * Makes changes until the time is up: blocks first, then biomes, then what is in
             * chests and the like.
             */
            private void step(long stop) {
                if (setBlocks(stop) && setBiomes(stop)) {
                    while (state < states.size() && System.nanoTime() < stop) {
                        BlockState s = states.get(state++);
                        s.copy(new Location(to, s.getX(), s.getY(), s.getZ())).update(true, false);
                    }
                }
            }

            /**
             * @return true when every block has been set
             */
            private boolean setBlocks(long stop) {
                // Blocks go in without physics: this is a copy of land that is already settled
                while (block < blocks.size() && System.nanoTime() < stop) {
                    BlockChange c = blocks.get(block++);
                    to.getBlockAt(c.x(), c.y(), c.z()).setBlockData(c.data(), false);
                }
                return block == blocks.size();
            }

            /**
             * @return true when every biome has been set
             */
            private boolean setBiomes(long stop) {
                while (biome < biomes.size() && System.nanoTime() < stop) {
                    BiomeChange c = biomes.get(biome++);
                    to.setBiome(c.x(), c.y(), c.z(), c.biome());
                    recoloured.add(((long) (c.x() >> 4) << 32) | ((c.z() >> 4) & 0xFFFFFFFFL));
                }
                return biome == biomes.size();
            }

            @Override
            public void run() {
                long stop = System.nanoTime() + BUDGET;
                try {
                    step(stop);
                } catch (RuntimeException e) {
                    cancel();
                    result.completeExceptionally(e);
                    return;
                }
                if (state == states.size() && biome == biomes.size() && block == blocks.size()) {
                    cancel();
                    // Players only see the new colours of grass and water when the chunk is sent again
                    recoloured.forEach(key -> to.refreshChunk((int) (key >> 32), key.intValue()));
                    result.complete(new Result(blocks.size(), biomes.size()));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
