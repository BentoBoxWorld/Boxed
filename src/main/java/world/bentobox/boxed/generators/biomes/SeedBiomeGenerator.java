package world.bentobox.boxed.generators.biomes;

import org.bukkit.World.Environment;

import world.bentobox.boxed.Boxed;

/**
 * Generator for the seed world
 * @author tastybento
 *
 */
public class SeedBiomeGenerator extends AbstractSeedBiomeProvider {

    public SeedBiomeGenerator(Boxed boxed) {
        super(boxed, Environment.NORMAL);
    }

    /**
     * @param boxed addon
     * @param biomesFile name of the file that defines the biomes
     */
    public SeedBiomeGenerator(Boxed boxed, String biomesFile) {
        super(boxed, Environment.NORMAL, biomesFile);
    }

    /**
     * @param boxed addon
     * @param biomesFile name of the file that defines the biomes
     * @param centreX block x of the spot the biomes are laid out around
     * @param centreZ block z of the spot the biomes are laid out around
     */
    public SeedBiomeGenerator(Boxed boxed, String biomesFile, int centreX, int centreZ) {
        super(boxed, Environment.NORMAL, biomesFile, centreX, centreZ);
    }
}
