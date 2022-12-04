package world.bentobox.boxed.generators;

import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import world.bentobox.boxed.Boxed;

/**
 * Generates the seed world chunks
 * @author tastybento
 *
 */
public class BoxedSeedChunkGenerator extends ChunkGenerator {

    BiomeProvider seedBiomeProvider;

    /**
     * @param seedBiomeProvider
     */
    public BoxedSeedChunkGenerator(Boxed boxed) {
        this.seedBiomeProvider = new SeedBiomeGenerator(boxed);
    }

    /*
    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return seedBiomeProvider;
    }
     */
    @Override
    public boolean shouldGenerateNoise() {
        return true;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return true;
        // return this.addon.getSettings().isGenerateSurface();
    }

    @Override
    public boolean shouldGenerateCaves() {
        return true;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return true;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }
}
