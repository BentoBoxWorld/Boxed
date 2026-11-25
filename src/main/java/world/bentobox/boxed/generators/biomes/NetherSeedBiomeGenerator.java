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

    /**
     * @param boxed addon
     * @param biomesFile name of the file that defines the biomes
     */
    public NetherSeedBiomeGenerator(Boxed boxed, String biomesFile) {
        super(boxed, Environment.NETHER, biomesFile);
    }
}
