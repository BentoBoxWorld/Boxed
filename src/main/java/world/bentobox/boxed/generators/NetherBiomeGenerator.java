package world.bentobox.boxed.generators;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import org.bukkit.block.Biome;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import nl.rutgerkok.worldgeneratorapi.BiomeGenerator;
import world.bentobox.boxed.Boxed;

/**
 * @author tastybento
 *
 */
public class NetherBiomeGenerator implements BiomeGenerator {

    private static final double ONE = 0.05;
    private static final double TWO = 0.25;
    private static final double THREE = 0.5;
    private static final double FOUR = 0.75;
    private static final double FIVE = 1.0;
    private static final double SIX = 1.25;
    private static final double SEVEN = 1.50;
    private static final double EIGHT = 1.75;
    private static final double LAST = 2.0;


    private static final TreeMap<Double, Biome> NORTH_EAST = new TreeMap<>();
    static {
        NORTH_EAST.put(ONE, Biome.BASALT_DELTAS);
        NORTH_EAST.put(TWO, Biome.CRIMSON_FOREST);
        NORTH_EAST.put(THREE, Biome.SOUL_SAND_VALLEY);
        NORTH_EAST.put(FOUR, Biome.WARPED_FOREST);
        NORTH_EAST.put(FIVE, Biome.BASALT_DELTAS);
        NORTH_EAST.put(SIX, Biome.CRIMSON_FOREST);
        NORTH_EAST.put(SEVEN, Biome.SOUL_SAND_VALLEY);
        NORTH_EAST.put(EIGHT, Biome.WARPED_FOREST);
        NORTH_EAST.put(LAST, Biome.NETHER_WASTES);
    }
    private static final TreeMap<Double, Biome> SOUTH_EAST = new TreeMap<>();
    static {
        SOUTH_EAST.put(ONE, Biome.NETHER_WASTES);
        SOUTH_EAST.put(TWO, Biome.BASALT_DELTAS);
        SOUTH_EAST.put(THREE, Biome.SOUL_SAND_VALLEY);
        SOUTH_EAST.put(FOUR, Biome.WARPED_FOREST);
        SOUTH_EAST.put(FIVE, Biome.NETHER_WASTES);
        SOUTH_EAST.put(SIX, Biome.BASALT_DELTAS);
        SOUTH_EAST.put(SEVEN, Biome.SOUL_SAND_VALLEY);
        SOUTH_EAST.put(EIGHT, Biome.WARPED_FOREST);
        SOUTH_EAST.put(LAST, Biome.CRIMSON_FOREST);
    }

    private static final TreeMap<Double, Biome> NORTH_WEST = new TreeMap<>();
    static {
        NORTH_WEST.put(ONE, Biome.NETHER_WASTES);
        NORTH_WEST.put(TWO, Biome.NETHER_WASTES);
        NORTH_WEST.put(THREE, Biome.SOUL_SAND_VALLEY);
        NORTH_WEST.put(FOUR, Biome.WARPED_FOREST);
        NORTH_WEST.put(FIVE, Biome.BASALT_DELTAS);
        NORTH_WEST.put(SIX, Biome.CRIMSON_FOREST);
        NORTH_WEST.put(SEVEN, Biome.SOUL_SAND_VALLEY);
        NORTH_WEST.put(EIGHT, Biome.WARPED_FOREST);
        NORTH_WEST.put(LAST, Biome.NETHER_WASTES);
    }

    private static final TreeMap<Double, Biome> SOUTH_WEST = new TreeMap<>();
    static {
        SOUTH_WEST.put(ONE, Biome.NETHER_WASTES);
        SOUTH_WEST.put(TWO, Biome.SOUL_SAND_VALLEY);
        SOUTH_WEST.put(THREE, Biome.WARPED_FOREST);
        SOUTH_WEST.put(FOUR, Biome.SOUL_SAND_VALLEY);
        SOUTH_WEST.put(FIVE, Biome.BASALT_DELTAS);
        SOUTH_WEST.put(SIX, Biome.CRIMSON_FOREST);
        SOUTH_WEST.put(SEVEN, Biome.WARPED_FOREST);
        SOUTH_WEST.put(EIGHT, Biome.SOUL_SAND_VALLEY);
        SOUTH_WEST.put(LAST, Biome.NETHER_WASTES);
    }
    private static final Map<BlockFace, SortedMap<Double, Biome>> QUADRANTS;
    static {
        Map<BlockFace, SortedMap<Double, Biome>> q = new EnumMap<>(BlockFace.class);
        q.put(BlockFace.NORTH_EAST, NORTH_EAST);
        q.put(BlockFace.NORTH_WEST, NORTH_WEST);
        q.put(BlockFace.SOUTH_EAST, SOUTH_EAST);
        q.put(BlockFace.SOUTH_WEST, SOUTH_WEST);
        QUADRANTS = Collections.unmodifiableMap(q);
    }


    private final Boxed addon;
    private final int dist;
    private final int offsetX;
    private final int offsetZ;


    public NetherBiomeGenerator(Boxed boxed) {
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
        Vector s = new Vector(x * 4, 0, z * 4);
        Vector l = getClosestIsland(s);
        double dis = l.distanceSquared(s);
        double d = dis / (dist * dist);
        Vector direction = s.subtract(l);
        if (direction.getBlockX() <= 0 && direction.getBlockZ() <= 0) {
            return getBiome(BlockFace.NORTH_WEST, d);
        } else if (direction.getBlockX() > 0 && direction.getBlockZ() <= 0) {
            return getBiome(BlockFace.NORTH_EAST, d);
        } else if (direction.getBlockX() <= 0 && direction.getBlockZ() > 0) {
            return getBiome(BlockFace.SOUTH_WEST, d);
        }
        return getBiome(BlockFace.SOUTH_EAST, d);
    }

    private Biome getBiome(BlockFace dir, double d) {
        Entry<Double, Biome> en = ((TreeMap<Double, Biome>) QUADRANTS.get(dir)).ceilingEntry(d);
        return en == null ? Biome.NETHER_WASTES : en.getValue();
    }

    Vector getClosestIsland(Vector v) {
        int d = dist * 2;
        long x = Math.round((double) v.getBlockX() / d) * d + offsetX;
        long z = Math.round((double) v.getBlockZ() / d) * d + offsetZ;
        return new Vector(x, 0, z);
    }

}
