package world.bentobox.boxed.generators.biomes;

import java.util.Arrays;
import java.util.List;

import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.Vector;
import org.eclipse.jdt.annotation.Nullable;

import com.google.common.base.Enums;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.generators.chunks.AbstractBoxedChunkGenerator;
import world.bentobox.boxed.generators.chunks.AbstractBoxedChunkGenerator.ChunkStore;

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
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        chunkX = AbstractBoxedChunkGenerator.repeatCalc(chunkX);
        chunkZ = AbstractBoxedChunkGenerator.repeatCalc(chunkZ);
        @Nullable ChunkStore c = addon.getChunkGenerator(worldInfo.getEnvironment()).getChunk(chunkX, chunkZ);

        if (c != null) {
            int xx = Math.floorMod(x, 16);
            int zz = Math.floorMod(z, 16);
            return c.chunkBiomes().getOrDefault(new Vector(xx, y, zz), defaultBiome);
        } else {
            BentoBox.getInstance().logWarning("Snapshot at " + chunkX + " " + chunkZ + " is not stored");
            return defaultBiome;
        }
    }

    @Override
    public List<Biome> getBiomes(WorldInfo worldInfo) {
        // Return all of them for now!
        return Arrays.stream(Biome.values()).filter(b -> !b.equals(Biome.CUSTOM)).filter(b -> !b.equals(Enums.getIfPresent(Biome.class, "CHERRY_GROVE").orNull())).toList();
    }

}
