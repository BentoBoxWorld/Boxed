package world.bentobox.boxed.generators;

import org.bukkit.World.Environment;
import org.bukkit.block.Biome;

import world.bentobox.boxed.Boxed;

/**
 * @author tastybento
 *
 */
public class BoxedNetherBiomeGenerator extends AbstractCopyBiomeProvider {

    public BoxedNetherBiomeGenerator(Boxed boxed) {
        super(boxed, Environment.NETHER, Biome.BASALT_DELTAS);
    }

}