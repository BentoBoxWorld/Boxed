package world.bentobox.boxed.generators.biomes;

import org.bukkit.block.Biome;

import world.bentobox.boxed.Boxed;

/**
 * Generator for the over world
 * @author tastybento
 *
 */
public class BoxedBiomeGenerator extends AbstractCopyBiomeProvider {

    public BoxedBiomeGenerator(Boxed boxed) {
        super(boxed, Biome.OCEAN);
    }

}