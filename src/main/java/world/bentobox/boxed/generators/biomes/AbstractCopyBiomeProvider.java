package world.bentobox.boxed.generators.biomes;

import java.util.Arrays;
import java.util.List;

import org.bukkit.ChunkSnapshot;
import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.generators.chunks.BoxedChunkGenerator;

/**
 * Copies biomes from seed world
 * @author tastybento
 *
 */
public abstract class AbstractCopyBiomeProvider extends BiomeProvider {

    private final Boxed addon;
    private final Biome defaultBiome;

    protected final int dist;

    protected AbstractCopyBiomeProvider(Boxed boxed, Environment env, Biome defaultBiome) {
        this.addon = boxed;
        this.defaultBiome = defaultBiome;
        dist = addon.getSettings().getIslandDistance();
    }

    @Override
    public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
        int chunkX = (int)((double)x/16);
        int chunkZ = (int)((double)z/16);
        chunkX = BoxedChunkGenerator.repeatCalc(chunkX);
        chunkZ = BoxedChunkGenerator.repeatCalc(chunkZ);
        ChunkSnapshot c = addon.getChunkGenerator(worldInfo.getEnvironment()).getChunk(chunkX, chunkZ);

        if (c != null) {
            int xx = Math.floorMod(x, 16);
            int zz = Math.floorMod(z, 16);
            int yy = Math.max(Math.min(y * 4, worldInfo.getMaxHeight()), worldInfo.getMinHeight()); // To handle bug in Spigot

            return c.getBiome(xx, yy, zz);
        } else {
            return defaultBiome;
        }
    }

    @Override
    public List<Biome> getBiomes(WorldInfo worldInfo) {
        // Return all of them for now!
        return Arrays.stream(Biome.values()).filter(b -> !b.equals(Biome.CUSTOM)).toList();
    }

}
