package world.bentobox.boxed.generators;

import org.bukkit.World.Environment;
import org.bukkit.block.Biome;

import world.bentobox.boxed.Boxed;

/**
 * @author tastybento
 *
 */
public class BoxedBiomeGeneratorV2 extends AbstractBoxedBiomeGeneratorV2 {

    public BoxedBiomeGeneratorV2(Boxed boxed) {
        super(boxed, Environment.NORMAL, Biome.OCEAN);
    }

}
