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
public class BoxedBiomeGenerator implements BiomeGenerator {

    private static final TreeMap<Double, Biome> NORTH_EAST = new TreeMap<>();
    static {
        NORTH_EAST.put(0.05, Biome.PLAINS);
        NORTH_EAST.put(0.1, Biome.DESERT);
        NORTH_EAST.put(0.2, Biome.SAVANNA);
        NORTH_EAST.put(0.4, Biome.JUNGLE_EDGE);
        NORTH_EAST.put(0.5, Biome.JUNGLE);
        NORTH_EAST.put(0.6, Biome.JUNGLE_HILLS);
        NORTH_EAST.put(0.75, Biome.BAMBOO_JUNGLE_HILLS);
        NORTH_EAST.put(0.8, Biome.BAMBOO_JUNGLE);
        NORTH_EAST.put(0.9, Biome.BADLANDS_PLATEAU);
        NORTH_EAST.put(1.0, Biome.BADLANDS);
        NORTH_EAST.put(1.1, Biome.LUKEWARM_OCEAN);
        NORTH_EAST.put(20.0, Biome.WARM_OCEAN);
    }
    private static final TreeMap<Double, Biome> SOUTH_EAST = new TreeMap<>();
    static {
        SOUTH_EAST.put(0.05, Biome.PLAINS);
        SOUTH_EAST.put(0.3, Biome.SAVANNA);
        SOUTH_EAST.put(0.4, Biome.DESERT);
        SOUTH_EAST.put(0.5, Biome.SHATTERED_SAVANNA);
        SOUTH_EAST.put(0.65, Biome.DESERT_HILLS);
        SOUTH_EAST.put(0.7, Biome.GRAVELLY_MOUNTAINS);
        SOUTH_EAST.put(0.9, Biome.BADLANDS_PLATEAU);
        SOUTH_EAST.put(1.0, Biome.ERODED_BADLANDS);
        SOUTH_EAST.put(1.1, Biome.MUSHROOM_FIELD_SHORE);
        SOUTH_EAST.put(20.0, Biome.WARM_OCEAN);
    }

    private static final TreeMap<Double, Biome> NORTH_WEST = new TreeMap<>();
    static {
        NORTH_WEST.put(0.05, Biome.PLAINS);
        NORTH_WEST.put(0.25, Biome.SUNFLOWER_PLAINS);
        NORTH_WEST.put(0.3, Biome.FLOWER_FOREST);
        NORTH_WEST.put(0.4, Biome.DARK_FOREST);
        NORTH_WEST.put(0.5, Biome.SNOWY_TAIGA);
        NORTH_WEST.put(0.65, Biome.SNOWY_TAIGA_HILLS);
        NORTH_WEST.put(0.7, Biome.SNOWY_MOUNTAINS);
        NORTH_WEST.put(0.9, Biome.MOUNTAIN_EDGE);
        NORTH_WEST.put(1.1, Biome.BEACH);
        NORTH_WEST.put(20.0, Biome.COLD_OCEAN);
    }

    private static final TreeMap<Double, Biome> SOUTH_WEST = new TreeMap<>();
    static {
        SOUTH_WEST.put(0.05, Biome.PLAINS);
        SOUTH_WEST.put(0.25, Biome.SWAMP);
        SOUTH_WEST.put(0.3, Biome.FOREST);
        SOUTH_WEST.put(0.4, Biome.DARK_FOREST);
        SOUTH_WEST.put(0.5, Biome.SNOWY_TAIGA);
        SOUTH_WEST.put(0.65, Biome.SNOWY_TAIGA_HILLS);
        SOUTH_WEST.put(0.7, Biome.SNOWY_MOUNTAINS);
        SOUTH_WEST.put(0.9, Biome.MOUNTAIN_EDGE);
        SOUTH_WEST.put(1.1, Biome.ICE_SPIKES);
        SOUTH_WEST.put(20.0, Biome.COLD_OCEAN);
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
        return en == null ? Biome.OCEAN : en.getValue();
    }

    Vector getClosestIsland(Vector v) {
        int d = dist * 2;
        long x = Math.round((double) v.getBlockX() / d) * d + offsetX;
        long z = Math.round((double) v.getBlockZ() / d) * d + offsetZ;
        return new Vector(x, 0, z);
    }

}
