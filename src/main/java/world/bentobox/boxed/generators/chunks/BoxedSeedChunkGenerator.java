package world.bentobox.boxed.generators.chunks;

import java.util.List;

import org.bukkit.Chunk;
import org.bukkit.World.Environment;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

import world.bentobox.boxed.Boxed;

/**
 * Generates the seed world chunks
 * @author tastybento
 *
 */
public class BoxedSeedChunkGenerator extends AbstractBoxedChunkGenerator {

    private final BiomeProvider biomeProvider;
    private final Environment env;

    /**
     * @param boxed - addon
     * @param env - environment
     */
    public BoxedSeedChunkGenerator(Boxed boxed, Environment env) {
        super(boxed);
        this.biomeProvider = null;
        this.env = env;
    }

    /**
     * @param boxed - addon
     * @param env - environment
     * @param bp - biome provider
     */
    public BoxedSeedChunkGenerator(Boxed boxed, Environment env, BiomeProvider bp) {
        super(boxed);
        this.biomeProvider = bp;
        this.env = env;
    }


    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        // If null then vanilla biomes are used
        return biomeProvider;
    }

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

    @Override
    protected List<EntityData> getEnts(Chunk chunk) {
        // These won't be stored
        return null;
    }

    @Override
    protected List<ChestData> getChests(Chunk chunk) {
        // These won't be stored
        return null;
    }
}
