package world.bentobox.boxed.generators;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import world.bentobox.boxed.Boxed;

/**
 * @author tastybento
 *
 */
public class DeleteGen extends ChunkGenerator {

    private final Map<String, World> backMap;
    /**
     * @param addon addon
     */
    public DeleteGen(Boxed addon) {
        backMap = new HashMap<>();
        backMap.put(addon.getOverWorld().getName(), Bukkit.getWorld(addon.getOverWorld().getName() + "_bak"));
        if (addon.getNetherWorld() != null) {
            backMap.put(addon.getNetherWorld().getName(), Bukkit.getWorld(addon.getNetherWorld().getName() + "_bak"));
        }
    }

    @Override
    public void generateSurface(WorldInfo worldInfo, Random random, int x, int z, ChunkData chunkData) {
        if (backMap.containsKey(worldInfo.getName())) {
            // Load the chunk from the back world
            Chunk chunk = backMap.get(worldInfo.getName()).getChunkAt(x, z);
            for (int xx = 0; xx < 16; xx++) {
                for (int y = 0; y < worldInfo.getMaxHeight(); y++) {
                    for (int zz = 0; zz < 16; zz++) {
                        chunkData.setBlock(xx, y, zz, chunk.getBlock(xx, y, zz).getBlockData());
                    }
                }
            }
        }
    }

    @Override
    public boolean canSpawn(World world, int x, int z)
    {
        return true;
    }

    @Override
    public boolean shouldGenerateNoise() {
        return true;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return true;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return true;
    }

}
