package world.bentobox.boxed.generators;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

import world.bentobox.boxed.Boxed;

/**
 * @author tastybento
 *
 */
public class DeleteGen extends ChunkGenerator {

    private final Map<World, World> backMap;
    /**
     * @param addon
     */
    public DeleteGen(Boxed addon) {
        backMap = new HashMap<>();
        backMap.put(addon.getOverWorld(), Bukkit.getWorld(addon.getOverWorld().getName() + "_bak"));
        backMap.put(addon.getNetherWorld(), Bukkit.getWorld(addon.getNetherWorld().getName() + "_bak"));
        backMap.put(addon.getEndWorld(), Bukkit.getWorld(addon.getEndWorld().getName() + "_bak"));
    }

    @Override
    public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biomeGrid) {
        ChunkData result = createChunkData(world);
        if (backMap.containsKey(world)) {
            // Load the chunk from the back world
            Chunk chunk = backMap.get(world).getChunkAt(chunkX, chunkZ);
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < world.getMaxHeight(); y++) {
                    for (int z = 0; z < 16; z++) {
                        result.setBlock(x, y, z, chunk.getBlock(x, y, z).getBlockData());
                    }
                }
            }
        }
        return result;
    }

}
