package world.bentobox.boxed.generators;

import java.util.Random;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import world.bentobox.boxed.Boxed;

/**
 * @author tastybento
 *
 */
public class CopyGenerator extends ChunkGenerator {

    private final int distChunks;
    private final World world;
    /**
     * @param addon addon
     */
    public CopyGenerator(Boxed addon, World world) {
        this.world = world;
        this.distChunks = addon.getSettings().getIslandDistance() / 16;

    }

    @Override
    public void generateSurface(WorldInfo worldInfo, Random random, int x, int z, ChunkData chunkData) {
        int worldX = (x % distChunks);
        int worldZ = (z % distChunks);

        // Load the chunk from the back world
        Chunk chunk = world.getChunkAt(worldX, worldZ);
        for (int xx = 0; xx < 16; xx++) {
            for (int y = worldInfo.getMinHeight(); y < worldInfo.getMaxHeight(); y++) {
                for (int zz = 0; zz < 16; zz++) {
                    chunkData.setBlock(xx, y, zz, chunk.getBlock(xx, y, zz).getBlockData());
                }
            }
        }
    }
}
