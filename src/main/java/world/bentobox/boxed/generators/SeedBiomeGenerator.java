package world.bentobox.boxed.generators;

import org.bukkit.World.Environment;
import org.bukkit.block.Biome;

import world.bentobox.boxed.Boxed;

/**
 * @author tastybento
 *
 */
public class SeedBiomeGenerator extends AbstractSeedBiomeProvider {

    public SeedBiomeGenerator(Boxed boxed) {
        super(boxed, Environment.NORMAL, Biome.OCEAN);
    }

}