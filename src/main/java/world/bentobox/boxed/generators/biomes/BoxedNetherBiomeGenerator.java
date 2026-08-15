package world.bentobox.boxed.generators.biomes;

import org.bukkit.block.Biome;

import world.bentobox.boxed.Boxed;

/**
 * Generator for the nether world
 * @author tastybento
 *
 */
public class BoxedNetherBiomeGenerator extends AbstractCopyBiomeProvider {

    public BoxedNetherBiomeGenerator(Boxed boxed) {
        super(boxed, Biome.BASALT_DELTAS);
    }

}