package world.bentobox.boxed.reach;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Generates the land around a place in a world and scores it as a place to start a box.
 *
 * @author tastybento
 */
public final class SpotScorer {

    private SpotScorer() {
    }

    /**
     * Scores a place. The chunks are generated without stopping the server and the counting
     * is done off the main thread.
     * @param plugin plugin to schedule with
     * @param world world to look in
     * @param x block x of the place
     * @param z block z of the place
     * @param discard true to drop the generated chunks without saving them, for a world that
     *                is going to be thrown away
     * @param bounty what the starter blueprint gives a new box, which the place need not have itself
     * @return the score. Completes on the main thread, with an error if the land could not be generated.
     */
    public static CompletableFuture<SpotSurvey.Score> score(Plugin plugin, World world, int x, int z,
            boolean discard, StarterBounty bounty) {
        CompletableFuture<SpotSurvey.Score> result = new CompletableFuture<>();
        int reach = SpotSurvey.RADIUS >> 4;
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        List<CompletableFuture<Chunk>> chunks = new ArrayList<>();
        for (int cx = -reach; cx < reach; cx++) {
            for (int cz = -reach; cz < reach; cz++) {
                chunks.add(world.getChunkAtAsync(chunkX + cx, chunkZ + cz));
            }
        }
        CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new)).whenComplete((v, error) ->
        // Chunks have to be read on the main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) {
                result.completeExceptionally(error);
                return;
            }
            List<ChunkSnapshot> snapshots = new ArrayList<>();
            for (CompletableFuture<Chunk> chunk : chunks) {
                Chunk c = chunk.join();
                snapshots.add(c.getChunkSnapshot(true, false, false));
                if (discard) {
                    world.unloadChunk(c.getX(), c.getZ(), false);
                }
            }
            int minY = world.getMinHeight();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                SpotSurvey spotSurvey = new SpotSurvey();
                snapshots.forEach(s -> spotSurvey.add(s, (s.getX() - chunkX) << 4, (s.getZ() - chunkZ) << 4, minY));
                SpotSurvey.Score score = spotSurvey.score(bounty);
                Bukkit.getScheduler().runTask(plugin, () -> result.complete(score));
            });
        }));
        return result;
    }
}
