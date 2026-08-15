package world.bentobox.boxed.generators.biomes;

import java.util.List;

import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.Vector;
import org.eclipse.jdt.annotation.Nullable;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
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

    protected AbstractCopyBiomeProvider(Boxed boxed, Biome defaultBiome) {
        this.addon = boxed;
        this.defaultBiome = defaultBiome;
        dist = addon.getSettings().getIslandDistance();
    }

    @Override
    public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
        AbstractBoxedChunkGenerator gen = addon.getChunkGenerator(worldInfo.getEnvironment());
        int chunkX = gen.repeatCalc(x >> 4);
        int chunkZ = gen.repeatCalc(z >> 4);
        @Nullable ChunkStore c = gen.getChunk(chunkX, chunkZ);

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
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).stream().toList();
    }

}
