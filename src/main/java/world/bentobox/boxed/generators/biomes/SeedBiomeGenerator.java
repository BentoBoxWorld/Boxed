package world.bentobox.boxed.generators.biomes;

import org.bukkit.World.Environment;
import org.bukkit.block.Biome;

import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.generators.chunks.AbstractBoxedChunkGenerator;

/**
 * Generator for the seed world
 * @author tastybento
 *
 */
public class SeedBiomeGenerator extends AbstractSeedBiomeProvider {

    public SeedBiomeGenerator(Boxed boxed, AbstractBoxedChunkGenerator seedGen) {
        super(boxed, Environment.NORMAL, Biome.PLAINS, seedGen);
    }

}