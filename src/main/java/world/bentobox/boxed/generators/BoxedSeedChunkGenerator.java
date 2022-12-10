package world.bentobox.boxed.generators;

import org.bukkit.World.Environment;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;

import world.bentobox.boxed.Boxed;

/**
 * Generates the seed world chunks
 * @author tastybento
 *
 */
public class BoxedSeedChunkGenerator extends ChunkGenerator {

    private final BiomeProvider seedBiomeProvider;
    private final Environment env;

    /**
     * @param env
     * @param seedBiomeProvider
     */
    public BoxedSeedChunkGenerator(Boxed boxed, Environment env) {
        this.seedBiomeProvider = new SeedBiomeGenerator(boxed);
        this.env = env;
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
        return env.equals(Environment.NETHER); // We allow structures in the Nether
    }
}
