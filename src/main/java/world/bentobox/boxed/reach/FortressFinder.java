package world.bentobox.boxed.reach;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.generator.structure.Structure;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.StructureSearchResult;

/**
 * Finds the fortresses of a Nether and looks at the land around them, so that
 * {@link FortressSpot} can say where the middle of a box should go.
 *
 * @author tastybento
 */
public final class FortressFinder {

    /**
     * How far around a place the land is looked at, in chunks
     */
    public static final int REACH = 8;
    /**
     * How many fortresses are found
     */
    private static final int FORTRESSES = 3;
    private static final int SEARCH_CHUNKS = 100;
    private static final int[][] SEARCHES = { { 0, 0 }, { 1500, 0 }, { -1500, 0 }, { 0, 1500 }, { 0, -1500 } };
    /**
     * The lava sea is below this
     */
    private static final int NETHER_FLOOR = 32;
    private static final int NETHER_ROOF = 110;

    /**
     * What is on a square of Nether land.
     * @param chunkX chunk x of its middle
     * @param chunkZ chunk z of its middle
     * @param spawners block x and z of each blaze spawner
     * @param bricks block x and z of each column that has fortress in it
     * @param snapshots the chunks, by {@link FortressFinder#key(int, int)}
     */
    public record Land(int chunkX, int chunkZ, List<int[]> spawners, List<int[]> bricks,
            Map<Long, ChunkSnapshot> snapshots) {

        /**
         * @return the best place on this land for the middle of a box, if it has one
         */
        public Optional<FortressSpot.Spot> choose() {
            int[] min = { (chunkX - REACH) << 4, (chunkZ - REACH) << 4 };
            int[] max = { ((chunkX + REACH) << 4) + 15, ((chunkZ + REACH) << 4) + 15 };
            return FortressSpot.choose(min, max, spawners, bricks, (x, z) -> {
                ChunkSnapshot s = snapshots.get(key(x >> 4, z >> 4));
                return s != null && isOpen(s, x & 15, z & 15) ? 1 : 0;
            });
        }

        /**
         * @param x block x
         * @param z block z
         * @return how near a place on this land is to a blaze spawner and to the fortress.
         *         The sizes are {@link Integer#MAX_VALUE} if the land has none.
         */
        public FortressSpot.Spot measure(int x, int z) {
            return FortressSpot.at(x, z, spawners, bricks);
        }
    }

    private FortressFinder() {
    }

    /**
     * Finds the fortresses around the middle of a Nether and a way off on each side. Call
     * on the main thread.
     * @param nether a Nether world
     * @return the chunk x and z of each fortress, nearest the middle first
     */
    public static List<int[]> locate(World nether) {
        List<int[]> fortresses = new ArrayList<>();
        for (int[] from : SEARCHES) {
            StructureSearchResult result = nether.locateNearestStructure(new Location(nether, from[0], 64, from[1]),
                    Structure.FORTRESS, SEARCH_CHUNKS, false);
            if (result != null && fortresses.size() < FORTRESSES) {
                int[] at = { result.getLocation().getBlockX() >> 4, result.getLocation().getBlockZ() >> 4 };
                if (fortresses.stream().noneMatch(f -> f[0] == at[0] && f[1] == at[1])) {
                    fortresses.add(at);
                }
            }
        }
        return fortresses;
    }

    /**
     * Generates the land around a chunk and looks at it. The server keeps running.
     * @param plugin plugin to schedule with
     * @param nether a Nether world
     * @param chunkX chunk x of the middle
     * @param chunkZ chunk z of the middle
     * @param discard true to unload the chunks without saving them, for a world that is thrown away
     * @return the land. Completes on the main thread.
     */
    public static CompletableFuture<Land> look(Plugin plugin, World nether, int chunkX, int chunkZ, boolean discard) {
        List<CompletableFuture<Chunk>> chunks = new ArrayList<>();
        for (int x = -REACH; x <= REACH; x++) {
            for (int z = -REACH; z <= REACH; z++) {
                chunks.add(nether.getChunkAtAsync(chunkX + x, chunkZ + z));
            }
        }
        CompletableFuture<Land> result = new CompletableFuture<>();
        CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new))
                .whenComplete((v, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        result.completeExceptionally(error);
                        return;
                    }
                    List<int[]> spawners = new ArrayList<>();
                    Map<Long, ChunkSnapshot> snapshots = new HashMap<>();
                    for (CompletableFuture<Chunk> chunk : chunks) {
                        Chunk c = chunk.join();
                        addBlazeSpawners(c, spawners);
                        snapshots.put(key(c.getX(), c.getZ()), c.getChunkSnapshot(false, false, false));
                        if (discard) {
                            nether.unloadChunk(c.getX(), c.getZ(), false);
                        }
                    }
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        Land land = new Land(chunkX, chunkZ, spawners, bricks(snapshots), snapshots);
                        Bukkit.getScheduler().runTask(plugin, () -> result.complete(land));
                    });
                }));
        return result;
    }

    private static List<int[]> bricks(Map<Long, ChunkSnapshot> snapshots) {
        List<int[]> bricks = new ArrayList<>();
        snapshots.values().forEach(s -> {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (hasBricks(s, x, z)) {
                        bricks.add(new int[] { (s.getX() << 4) + x, (s.getZ() << 4) + z });
                    }
                }
            }
        });
        return bricks;
    }

    private static void addBlazeSpawners(Chunk chunk, List<int[]> spawners) {
        for (BlockState tile : chunk.getTileEntities()) {
            if (tile instanceof CreatureSpawner spawner && spawner.getSpawnedType() == EntityType.BLAZE) {
                spawners.add(new int[] { tile.getX(), tile.getZ() });
            }
        }
    }

    static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private static boolean isBrick(Material m) {
        return m == Material.NETHER_BRICKS || m == Material.NETHER_BRICK_FENCE || m == Material.NETHER_BRICK_STAIRS;
    }

    /**
     * @return true if a column of the Nether has part of a fortress in it
     */
    private static boolean hasBricks(ChunkSnapshot s, int x, int z) {
        for (int y = NETHER_FLOOR; y < NETHER_ROOF; y++) {
            if (isBrick(s.getBlockType(x, y, z))) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return true if a column of the Nether has solid ground with room to stand on it
     */
    private static boolean isOpen(ChunkSnapshot s, int x, int z) {
        for (int y = NETHER_FLOOR; y < NETHER_ROOF; y++) {
            Material floor = s.getBlockType(x, y, z);
            if (floor.isSolid() && !isBrick(floor) && s.getBlockType(x, y + 1, z).isAir()
                    && s.getBlockType(x, y + 2, z).isAir()) {
                return true;
            }
        }
        return false;
    }
}
