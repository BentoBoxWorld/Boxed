package world.bentobox.boxed.generators;

import org.bukkit.block.Biome;
import org.bukkit.util.Vector;

import nl.rutgerkok.worldgeneratorapi.BiomeGenerator;
import world.bentobox.boxed.Boxed;

/**
 * @author tastybento
 *
 */
public class BoxedBiomeGenerator implements BiomeGenerator {

    private final Boxed addon;
    private final int dist;
    private final int offsetX;
    private final int offsetZ;

    public BoxedBiomeGenerator(Boxed boxed) {
        this.addon = boxed;
        dist = addon.getSettings().getIslandDistance();
        offsetX = addon.getSettings().getIslandXOffset();
        offsetZ = addon.getSettings().getIslandZOffset();
    }

    @Override
    public Biome getZoomedOutBiome(int x, int y, int z) {
        /*
         * The given x, y and z coordinates are scaled down by a factor of 4. So when Minecraft
         * wants to know the biome at x=112, it will ask the biome generator for a biome at x=112/4=28.
         */
        /*
         * Biomes go around the island centers
         *
         */
        Vector s = new Vector(x * 4, y * 4, z * 4);
        Vector l = getClosestIsland(s);
        double d = l.distance(s) / addon.getSettings().getIslandDistance();
        if (d < 0.2) {
            return Biome.PLAINS;
        } else if (d < 0.3) {
            return Biome.FOREST;
        } else if (d < 0.33) {
            return Biome.SNOWY_TAIGA;
        } else if (d < 0.4) {
            return Biome.DARK_FOREST;
        } else if (d < 0.45) {
            return Biome.MOUNTAINS;
        } else if (d < 0.52) {
            return Biome.DESERT;
        }
        return Biome.BADLANDS;
    }

    public Vector getClosestIsland(Vector v) {
        long x = Math.round((double) v.getBlockX() / dist) * dist + offsetX;
        long z = Math.round((double) v.getBlockZ() / dist) * dist + offsetZ;
        return new Vector(x, v.getBlockY(), z);
    }
}
