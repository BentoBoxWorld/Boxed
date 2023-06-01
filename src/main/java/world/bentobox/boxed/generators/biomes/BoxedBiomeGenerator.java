package world.bentobox.boxed.generators.biomes;

import org.bukkit.World.Environment;
import org.bukkit.block.Biome;

import world.bentobox.boxed.Boxed;

/**
 * Generator for the over world
 * @author tastybento
 *
 */
public class BoxedBiomeGenerator extends AbstractCopyBiomeProvider {

    public BoxedBiomeGenerator(Boxed boxed) {
        super(boxed, Environment.NORMAL, Biome.OCEAN);
    }

}