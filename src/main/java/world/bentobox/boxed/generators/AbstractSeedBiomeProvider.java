package world.bentobox.boxed.generators;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

import org.bukkit.ChunkSnapshot;
import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.Vector;
import org.eclipse.jdt.annotation.NonNull;

import com.google.common.base.Enums;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.util.Pair;
import world.bentobox.bentobox.util.Util;
import world.bentobox.boxed.Boxed;

/**
 * Generates the biomes for the seed world
 * @author tastybento
 *
 */
public abstract class AbstractSeedBiomeProvider extends BiomeProvider {

    private static final Map<Environment, String> ENV_MAP;
    private static final int DEPTH = 50;

    static {
        Map<Environment, String> e = new EnumMap<>(Environment.class);
        e.put(Environment.NORMAL, "distribution.overworld");
        e.put(Environment.NETHER, "distribution.nether");
        e.put(Environment.THE_END, "distribution.the_end");
        ENV_MAP = Collections.unmodifiableMap(e);
    }

    private final Boxed addon;
    private final Biome defaultBiome;

    protected final int dist;

    private final int offsetX;
    private final int offsetZ;
    protected final Map<BlockFace, SortedMap<Double, Biome>> quadrants;
    private final AbstractBoxedChunkGenerator seedGen;


    protected AbstractSeedBiomeProvider(Boxed boxed, Environment env, Biome defaultBiome, AbstractBoxedChunkGenerator seedGen) {
        this.addon = boxed;
        this.defaultBiome = defaultBiome;
        this.seedGen = seedGen;
        dist = addon.getSettings().getIslandDistance();
        offsetX = addon.getSettings().getIslandXOffset();
        offsetZ = addon.getSettings().getIslandZOffset();
        // Load the config
        File biomeFile = new File(addon.getDataFolder(), "biomes.yml");
        // Check if it exists and if not, save it from the jar
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

    private Biome getQuadrantBiome(BlockFace dir, double d) {
        Entry<Double, Biome> en = ((TreeMap<Double, Biome>) quadrants.get(dir)).ceilingEntry(d);
        return en == null ? null : en.getValue();
    }

    @Override
    public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
        // Custom biomes are not 3D yet
        if (y < DEPTH) {
            Biome result = getVanillaBiome(worldInfo, x, y, z);
            return Objects.requireNonNull(result);
        }
        Biome result = getMappedBiome(x,z);
        if (result == null || result.equals(Biome.CUSTOM)) {
            result = getVanillaBiome(worldInfo, x, y, z);

        }
        return Objects.requireNonNull(result);
    }

    private @NonNull Biome getVanillaBiome(WorldInfo worldInfo, int x, int y, int z) {
        // Vanilla biomes
        int chunkX = BoxedChunkGenerator.repeatCalc(x >> 4);
        int chunkZ = BoxedChunkGenerator.repeatCalc(z >> 4);
        ChunkSnapshot snapshot = this.seedGen.getChunk(chunkX, chunkZ);
        if (snapshot == null) {
            return defaultBiome;
        }
        int xx = Math.floorMod(x, 16);
        int zz = Math.floorMod(z, 16);
        int yy = Math.max(Math.min(y * 4, worldInfo.getMaxHeight()), worldInfo.getMinHeight()); // To handle bug in Spigot

        Biome b = snapshot.getBiome(xx, yy, zz);
        if (y > DEPTH )
            BentoBox.getInstance().logDebug("Returning vanilla biome " + b + " for " + worldInfo.getName() + "  " + x + " " + y + " " + z);
        return Objects.requireNonNull(b);
    }

    private Map<Pair<Integer, Integer>, Biome> biomeCache = new HashMap<>();
    /**
     * Get the mapped 2D biome at position x,z
     * @param x - block coord
     * @param z - block coord
     * @return Biome
     */
    private Biome getMappedBiome(int x, int z) {
        /*
         * Biomes go around the island centers
         *
         */
        Biome result = biomeCache.get((new Pair<Integer, Integer>(x,z)));
        if (result != null) {
            return result;
        }
        Vector s = new Vector(x, 0, z);
        Vector l = getClosestIsland(s);
        BentoBox.getInstance().logDebug("Closest island is " + Util.xyz(l));
        double dis = l.distance(s);
        double d = dis / dist; // Normalize
        Vector direction = s.subtract(l);
        if (direction.getBlockX() <= 0 && direction.getBlockZ() <= 0) {
            result = getQuadrantBiome(BlockFace.NORTH_WEST, d);
        } else if (direction.getBlockX() > 0 && direction.getBlockZ() <= 0) {
            result = getQuadrantBiome(BlockFace.NORTH_EAST, d);
        } else if (direction.getBlockX() <= 0 && direction.getBlockZ() > 0) {
            result = getQuadrantBiome(BlockFace.SOUTH_WEST, d);
        } else {
            result = getQuadrantBiome(BlockFace.SOUTH_EAST, d);
        }
        biomeCache.put(new Pair<Integer, Integer>(x,z), result);
        return result;

    }

    @Override
    public List<Biome> getBiomes(WorldInfo worldInfo) {
        // Return all of them for now!
        return Arrays.stream(Biome.values()).filter(b -> !b.equals(Biome.CUSTOM)).toList();
    }

    /**
     * Get the island center closest to this vector
     * @param v - vector
     * @return island center vector (no y value)
     */
    private Vector getClosestIsland(Vector v) {
        int d = dist * 2;
        long x = Math.round((double) v.getBlockX() / d) * d + offsetX;
        long z = Math.round((double) v.getBlockZ() / d) * d + offsetZ;
        return new Vector(x, 0, z);
    }

    private SortedMap<Double, Biome> loadQuad(YamlConfiguration config, String string) {
        SortedMap<Double, Biome> result = new TreeMap<>();
        if (!config.contains(string)) {
            return result;
        }
        for (String ring : config.getStringList(string)) {
            String[] split = ring.split(":");
            if (split.length == 2) {
                try {
                    double d = Double.parseDouble(split[0]);
                    Biome biome = Enums.getIfPresent(Biome.class, split[1].toUpperCase(Locale.ENGLISH)).orNull();
                    if (biome == null) {
                        addon.logError(split[1].toUpperCase(Locale.ENGLISH) + " is an unknown biome on this server.");
                        result.put(d, Biome.CUSTOM);
                    } else {
                        // A biome of null means that no alternative biome should be applied
                        result.put(d, biome);
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

}
