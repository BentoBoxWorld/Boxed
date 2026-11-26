package world.bentobox.boxed.workshop;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntConsumer;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.World.Environment;

import world.bentobox.boxed.reach.LandSurvey;
import world.bentobox.boxed.reach.LandSurvey.Kind;

/**
 * Finds which biomes the land of a box has, and how big the box has to be to reach each.
 * The land is generated if it has not been already, a few chunks at a time, so this takes a
 * while the first time and the server keeps running.
 *
 * @author tastybento
 */
final class BiomeSurveyor {

    /**
     * How many chunks are asked for at once
     */
    private static final int BATCH = 24;
    private static final int STEP = 4;
    private static final int STEP_Y = 8;

    private final World world;
    private final int centreX;
    private final int centreZ;
    private final LandSurvey survey;
    private final Kind kind;
    private final IntConsumer progress;
    private final List<int[]> chunks = new ArrayList<>();
    private final CompletableFuture<LandSurvey> result = new CompletableFuture<>();

    /**
     * @param world world to look in
     * @param centreX block x of the middle of the box
     * @param centreZ block z of the middle of the box
     * @param radius the largest box size
     * @param progress told the percentage done as it goes up
     */
    BiomeSurveyor(World world, int centreX, int centreZ, int radius, IntConsumer progress) {
        this.world = world;
        this.centreX = centreX;
        this.centreZ = centreZ;
        this.survey = new LandSurvey(radius);
        this.kind = world.getEnvironment() == Environment.NETHER ? Kind.NETHER_BIOME : Kind.BIOME;
        this.progress = progress;
        for (int x = (centreX - radius) >> 4; x <= (centreX + radius - 1) >> 4; x++) {
            for (int z = (centreZ - radius) >> 4; z <= (centreZ + radius - 1) >> 4; z++) {
                chunks.add(new int[] { x, z });
            }
        }
    }

    /**
     * Starts looking. Call on the main thread.
     * @return the biomes of the box. Completes on the main thread.
     */
    CompletableFuture<LandSurvey> run() {
        next(0);
        return result;
    }

    private void next(int from) {
        if (from >= chunks.size()) {
            result.complete(survey);
            return;
        }
        int to = Math.min(chunks.size(), from + BATCH);
        List<CompletableFuture<Chunk>> batch = new ArrayList<>();
        for (int[] c : chunks.subList(from, to)) {
            // Chunks are read as they arrive, which is on the main thread, before they can unload
            batch.add(world.getChunkAtAsync(c[0], c[1]).thenApply(this::count));
        }
        CompletableFuture.allOf(batch.toArray(CompletableFuture[]::new)).whenComplete((v, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
                return;
            }
            progress.accept(to * 100 / chunks.size());
            next(to);
        });
    }

    private Chunk count(Chunk chunk) {
        int blockX = chunk.getX() << 4;
        int blockZ = chunk.getZ() << 4;
        for (int x = blockX; x < blockX + 16; x += STEP) {
            for (int z = blockZ; z < blockZ + 16; z += STEP) {
                int size = LandSurvey.sizeFor(x - centreX, z - centreZ);
                for (int y = world.getMinHeight(); y < world.getMaxHeight(); y += STEP_Y) {
                    survey.add(kind, world.getBiome(x, y, z).getKey().getKey(), size, 1);
                }
            }
        }
        return chunk;
    }
}
