package world.bentobox.boxed.generators.biomes;

import org.bukkit.World.Environment;
import org.bukkit.block.Biome;

import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.generators.chunks.AbstractBoxedChunkGenerator;

/**
 * @author tastybento
 *
 */
public class NetherSeedBiomeGenerator extends AbstractSeedBiomeProvider {

    public NetherSeedBiomeGenerator(Boxed boxed, AbstractBoxedChunkGenerator seedGen) {
        super(boxed, Environment.NETHER, Biome.BASALT_DELTAS, seedGen);
    }

}