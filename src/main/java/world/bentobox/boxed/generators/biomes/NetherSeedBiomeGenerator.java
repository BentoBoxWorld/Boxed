package world.bentobox.boxed.generators.biomes;

import org.bukkit.World.Environment;

import world.bentobox.boxed.Boxed;

/**
 * Generator for the Nether seed world
 * @author tastybento
 *
 */
public class NetherSeedBiomeGenerator extends AbstractSeedBiomeProvider {

    public NetherSeedBiomeGenerator(Boxed boxed) {
        super(boxed, Environment.NETHER);
    }

}