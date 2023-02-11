package world.bentobox.boxed.generators.biomes;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.Vector;
import org.eclipse.jdt.annotation.NonNull;

import com.google.common.base.Enums;

import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.generators.chunks.AbstractBoxedChunkGenerator;
import world.bentobox.boxed.generators.chunks.AbstractBoxedChunkGenerator.ChunkStore;
import world.bentobox.boxed.generators.chunks.BoxedChunkGenerator;

/**
 * Generates the biomes for the seed world. A seed world is the template for the chunks that
 * are used to generate areas for the players to play it.s
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
    //private Map<Pair<Integer, Integer>, Biome> biomeCache = new HashMap<>();

    protected final int dist;

    private final int spawnX;
    private final int spawnZ;
    protected final Map<BlockFace, SortedMap<Double, Biome>> quadrants;
    private final AbstractBoxedChunkGenerator seedGen;


    protected AbstractSeedBiomeProvider(Boxed boxed, Environment env, Biome defaultBiome, AbstractBoxedChunkGenerator seedGen) {
        this.addon = boxed;
        this.defaultBiome = defaultBiome;
        this.seedGen = seedGen;
        // These fields are used to determine the biomes around the spawn point
        this.dist = addon.getSettings().getIslandDistance();
        if (env.equals(Environment.NORMAL)) {
            spawnX = addon.getSettings().getSeedX();
            spawnZ = addon.getSettings().getSeedZ();
        } else {
            spawnX = addon.getSettings().getNetherSeedX();
            spawnZ = addon.getSettings().getNetherSeedZ();
        }
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

    /**
     * x, y, and z are block coordinates
     */
    @Override
    public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
        return this.getMappedBiome(worldInfo, x, y, z);
    }

    /**
     * Get the vanilla biome at this coordinate
     * @param worldInfo world info
     * @param x x
     * @param y y
     * @param z z
     * @return Biome
     */
    @NonNull
    private Biome getVanillaBiome(WorldInfo worldInfo, int x, int y, int z) {
        // Get the chunk coordinates
        int chunkX = BoxedChunkGenerator.repeatCalc(x >> 4);
        int chunkZ = BoxedChunkGenerator.repeatCalc(z >> 4);
        // Get the stored snapshot
        ChunkStore snapshot = this.seedGen.getChunk(chunkX, chunkZ);
        if (snapshot == null) {
            // This snapshot is not stored...
            return defaultBiome;
        }
        // Get the in-chunk coordinates
        int xx = Math.floorMod(x, 16);
        int zz = Math.floorMod(z, 16);
        //int yy = Math.max(Math.min(y * 4, worldInfo.getMaxHeight()), worldInfo.getMinHeight()); // To handle bug in Spigot

        Biome b = snapshot.chunkBiomes().getOrDefault(new Vector(xx, y, zz), defaultBiome);

        return Objects.requireNonNull(b);
    }

    /**
     * Get the mapped 2D biome at position x,z
     * @param worldInfo world info
     * @param x - block coord
     * @param y - block coord
     * @param z - block coord
     * @return Biome
     */
    private Biome getMappedBiome(WorldInfo worldInfo, int x, int y, int z) {

        // Custom biomes are not 3D yet
        if (y < DEPTH) {
            Biome result = getVanillaBiome(worldInfo, x, y, z);
            return Objects.requireNonNull(result);
        }

        /*
         * Biomes go around the island centers
         *
         */

        // Try to get the cached value
        //Biome result = biomeCache.get((new Pair<Integer, Integer>(x,z)));
        //if (result != null) {
        //return result;
        //}
        Vector s = new Vector(x, 0, z);
        Vector l = new Vector(spawnX,0,spawnZ);
        double dis = l.distance(s);
        if (dis > dist * 2) {
            // Only customize biomes around the spawn location
            return getVanillaBiome(worldInfo, x, y, z);
        }
        // Provide custom biomes
        Biome result;
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

        if (result == null || result.equals(Biome.CUSTOM)) {
            result = getVanillaBiome(worldInfo, x, y, z);

        }
        // Cache good result
        //biomeCache.put(new Pair<Integer, Integer>(x,z), result);
        return result;

    }

    @Override
    public List<Biome> getBiomes(WorldInfo worldInfo) {
        // Return all of them for now!
        return Arrays.stream(Biome.values()).filter(b -> !b.equals(Biome.CUSTOM)).toList();
    }

    /**
     * Loads the custom biomes from the config file
     * @param config - Yaml configuration object
     * @param sector - the direction section to load
     * @return
     */
    private SortedMap<Double, Biome> loadQuad(YamlConfiguration config, String sector) {
        SortedMap<Double, Biome> result = new TreeMap<>();
        if (!config.contains(sector)) {
            return result;
        }
        for (String ring : config.getStringList(sector)) {
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
                    addon.logError(sector + ": " + split[0] + " does not seem to be a double. For integers add a .0 to the end");
                }
            } else {
                addon.logError(ring + " must be in the format ratio:biome where ratio is a double.");
            }
        }
        return result;
    }

}
