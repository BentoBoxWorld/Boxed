package world.bentobox.boxed.generators;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.lang.math.NumberUtils;
import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;

import com.google.common.base.Enums;

import world.bentobox.boxed.Boxed;

/**
 * @author tastybento
 *
 */
abstract class AbstractBoxedBiomeGeneratorV2 {

    private static final Map<Environment, String> ENV_MAP;
    static {
        Map<Environment, String> e = new EnumMap<>(Environment.class);
        e.put(Environment.NORMAL, "distribution.overworld");
        e.put(Environment.NETHER, "distribution.nether");
        e.put(Environment.THE_END, "distribution.the_end");
        ENV_MAP = Collections.unmodifiableMap(e);
    }

    protected final Map<BlockFace, SortedMap<Double, Biome>> quadrants;

    private final Boxed addon;
    protected final int dist;
    private final int offsetX;
    private final int offsetZ;
    public final Biome defaultBiome;
		public ArrayList<Biome> BiomeList = new ArrayList<Biome>();


    protected AbstractBoxedBiomeGeneratorV2(Boxed boxed, Environment env, Biome defaultBiome) {
        this.addon = boxed;
        this.defaultBiome = defaultBiome;
        dist = addon.getSettings().getIslandDistance();
        offsetX = addon.getSettings().getIslandXOffset();
        offsetZ = addon.getSettings().getIslandZOffset();
        // Load the config
        File biomeFile = new File(addon.getDataFolder(), "biomes.yml");
        if (!biomeFile.exists()) {
            addon.saveResource("biomes.yml", true);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(biomeFile);
        SortedMap<Double, Biome> northEast = loadQuad(config, ENV_MAP.get(env) + ".north-east");
        SortedMap<Double, Biome> southEast = loadQuad(config, ENV_MAP.get(env) + ".south-east");
        SortedMap<Double, Biome> northWest = loadQuad(config, ENV_MAP.get(env) + ".north-west");
        SortedMap<Double, Biome> southWest = loadQuad(config, ENV_MAP.get(env) + ".south-west");

        quadrants = new EnumMap<>(BlockFace.class);
        quadrants.put(BlockFace.NORTH_EAST, northEast);
        quadrants.put(BlockFace.NORTH_WEST, northWest);
        quadrants.put(BlockFace.SOUTH_EAST, southEast);
        quadrants.put(BlockFace.SOUTH_WEST, southWest);
    }


    private SortedMap<Double, Biome> loadQuad(YamlConfiguration config, String string) {
        SortedMap<Double, Biome> result = new TreeMap<>();
        if (!config.contains(string)) {
            return result;
        }
        for (String ring : config.getStringList(string)) {
            String[] split = ring.split(":");
            if (split.length == 2 && NumberUtils.isNumber(split[0])) {
                try {
                    double d = Double.parseDouble(split[0]);
                    Biome biome = Enums.getIfPresent(Biome.class, split[1].toUpperCase(Locale.ENGLISH)).orNull();
                    if (biome == null) {
                        addon.logError(split[1].toUpperCase(Locale.ENGLISH) + " is an unknown biome on this server.");
                    } else {
                        result.put(d, biome);
												addon.log("yoo");
												BiomeList.add(biome);
												addon.log("after");
											}
                } catch(Exception e) {
                    addon.logError(string + ": " + split[0] + " does not seem to be a double. For integers add a .0 to the end");
                }
            } else {
                addon.logError(ring + " must be in the format ratio:biome where ratio is a double.");
            }
        }
        return result;
    }

    // @Override
    public Biome getZoomedOutBiome(int x, int z) {
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
        Entry<Double, Biome> en = ((TreeMap<Double, Biome>) quadrants.get(dir)).ceilingEntry(d);
        return en == null ? defaultBiome : en.getValue();
    }

    private Vector getClosestIsland(Vector v) {
        int d = dist * 2;
        long x = Math.round((double) v.getBlockX() / d) * d + offsetX;
        long z = Math.round((double) v.getBlockZ() / d) * d + offsetZ;
        return new Vector(x, 0, z);
    }

}
