package world.bentobox.boxed.generators;

import org.bukkit.World.Environment;
import org.bukkit.block.Biome;

import world.bentobox.boxed.Boxed;

/**
 * @author tastybento
 *
 */
public class NetherBiomeGenerator extends AbstractBoxedBiomeGenerator {

    public NetherBiomeGenerator(Boxed boxed) {
        super(boxed, Environment.NETHER, Biome.NETHER_WASTES);
    }

}
