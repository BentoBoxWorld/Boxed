package world.bentobox.boxed.generators.biomes;

import java.io.File;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import org.bukkit.Registry;
import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.generator.BiomeParameterPoint;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.Vector;
import org.eclipse.jdt.annotation.NonNull;

import world.bentobox.boxed.Boxed;

/**
 * Generates the biomes for the seed world. A seed world is the template for the chunks that
 * are used to generate areas for the players to play it.s
 * @author tastybento
 *
 */
public abstract class AbstractSeedBiomeProvider extends BiomeProvider {

    private static final Map<Environment, String> ENV_MAP;

    static {
        Map<Environment, String> e = new EnumMap<>(Environment.class);
        e.put(Environment.NORMAL, "distribution.overworld");
        e.put(Environment.NETHER, "distribution.nether");
        e.put(Environment.THE_END, "distribution.the_end");
        ENV_MAP = Collections.unmodifiableMap(e);
    }

    /**
     * Heights / ridges by erosion
     */
    private enum Ridges {
        VALLEYS(-1.0, -0.85), LOW(-0.85, -0.6), MID(-0.6, 0.2), HIGH(0.2, 0.7), PEAKS(0.7, 1.0);

        private double low;
        private double high;

        Ridges(double low, double high) {
            this.low = low;
            this.high = high;
        }

        public static Ridges getRidge(double erosion) {
            for (Ridges r : Ridges.values()) {
                if (erosion >= r.low && erosion < r.high) {
                    return r;
                }
            }
            return MID;
        }

    }

    /**
     * Badland Biones by Humidity Zone
     */
    private enum BadlandBiome {
        HZONE0(0, Biome.BADLANDS, Biome.ERODED_BADLANDS), HZONE1(1, Biome.BADLANDS, Biome.ERODED_BADLANDS),
        HZONE2(2, Biome.BADLANDS, Biome.BADLANDS), HZONE3(3, Biome.WOODED_BADLANDS, Biome.WOODED_BADLANDS),
        HZONE4(4, Biome.WOODED_BADLANDS, Biome.WOODED_BADLANDS);

        private int humidityZone;
        private Biome biome;
        private Biome biome2;

        BadlandBiome(int h, Biome biome, Biome biome2) {
            this.humidityZone = h;
            this.biome = biome;
            this.biome2 = biome2;
        }

        public static Biome getBiome(int humidity, double weirdness) {
            for (BadlandBiome mb : BadlandBiome.values()) {
                if (mb.humidityZone == humidity) {
                    if (weirdness < 0) {
                        return mb.biome;
                    } else {
                        return mb.biome2;
                    }
                }
            }
            throw new IllegalArgumentException("badlands biome h = " + humidity);
        }
    }

    /**
     * Middle Biomes by temperature and humidity zones
     */
    private enum MiddleBiome {
        X00(0, 0, Biome.SNOWY_PLAINS, Biome.ICE_SPIKES), X01(0, 1, Biome.PLAINS, Biome.PLAINS),
        X02(0, 2, Biome.FLOWER_FOREST, Biome.SUNFLOWER_PLAINS), x03(0, 3, Biome.SAVANNA, Biome.SAVANNA),
        X04(0, 4, Biome.DESERT, Biome.DESERT),

        X10(1, 0, Biome.SNOWY_PLAINS, Biome.SNOWY_PLAINS), X11(1, 1, Biome.PLAINS, Biome.PLAINS),
        X12(1, 2, Biome.PLAINS, Biome.PLAINS), X13(1, 3, Biome.SAVANNA, Biome.SAVANNA),
        X14(1, 4, Biome.DESERT, Biome.DESERT),

        X20(2, 0, Biome.SNOWY_PLAINS, Biome.SNOWY_TAIGA), X21(2, 1, Biome.FOREST, Biome.FOREST),
        X22(2, 2, Biome.FOREST, Biome.FOREST), X23(2, 3, Biome.FOREST, Biome.PLAINS),
        X24(2, 4, Biome.DESERT, Biome.DESERT),

        X30(3, 0, Biome.SNOWY_TAIGA, Biome.SNOWY_TAIGA), X31(3, 1, Biome.TAIGA, Biome.TAIGA),
        X32(3, 2, Biome.BIRCH_FOREST, Biome.OLD_GROWTH_BIRCH_FOREST), X33(3, 3, Biome.JUNGLE, Biome.SPARSE_JUNGLE),
        X34(3, 4, Biome.DESERT, Biome.DESERT),

        X40(4, 0, Biome.TAIGA, Biome.TAIGA), X41(4, 1, Biome.OLD_GROWTH_SPRUCE_TAIGA, Biome.OLD_GROWTH_PINE_TAIGA),
        X42(4, 2, Biome.DARK_FOREST, Biome.DARK_FOREST), X43(4, 3, Biome.JUNGLE, Biome.BAMBOO_JUNGLE),
        X44(4, 4, Biome.DESERT, Biome.DESERT),
        ;

        private int temperature;
        private int humidity;
        private Biome biome;
        private Biome weirdBiome; // What the biome be if the weirdness is high enough

        MiddleBiome(int h, int t, Biome b, Biome weirdBiome) {
            this.humidity = h;
            this.temperature = t;
            this.weirdBiome = weirdBiome;
            this.biome = b;
        }

        public static Biome getBiome(int humidity, int temperature, double weirdness) {
            for (MiddleBiome mb : MiddleBiome.values()) {
                if (mb.humidity == humidity && mb.temperature == temperature) {
                    if (weirdness < 0) {
                        return mb.biome;
                    } else {
                        return mb.weirdBiome;
                    }
                }
            }
            throw new IllegalArgumentException("middle biome h = " + humidity + " t = " + temperature);
        }
    }

    /**
     * Plateau biomes by temperature and humidity zones
     */
    private enum PlateauBiome {
        X00(0, 0, Biome.SNOWY_PLAINS, Biome.ICE_SPIKES), x01(0, 1, Biome.MEADOW, Biome.CHERRY_GROVE),
        X02(0, 2, Biome.MEADOW, Biome.CHERRY_GROVE), x03(0, 3, Biome.SAVANNA_PLATEAU, Biome.SAVANNA_PLATEAU),
        X04(0, 4, Biome.BADLANDS, Biome.ERODED_BADLANDS),

        X10(1, 0, Biome.SNOWY_PLAINS, Biome.SNOWY_PLAINS), x11(1, 1, Biome.MEADOW, Biome.MEADOW),
        X12(1, 2, Biome.MEADOW, Biome.CHERRY_GROVE), x13(1, 3, Biome.SAVANNA_PLATEAU, Biome.SAVANNA_PLATEAU),
        X14(1, 4, Biome.BADLANDS, Biome.ERODED_BADLANDS),

        X20(2, 0, Biome.SNOWY_PLAINS, Biome.SNOWY_TAIGA), x21(2, 1, Biome.FOREST, Biome.MEADOW),
        X22(2, 2, Biome.MEADOW, Biome.BIRCH_FOREST), x23(2, 3, Biome.FOREST, Biome.FOREST),
        X24(2, 4, Biome.BADLANDS, Biome.BADLANDS),

        X30(3, 0, Biome.SNOWY_TAIGA, Biome.SNOWY_TAIGA), x31(3, 1, Biome.TAIGA, Biome.MEADOW),
        X32(3, 2, Biome.MEADOW, Biome.BIRCH_FOREST), x33(3, 3, Biome.FOREST, Biome.FOREST),
        X34(3, 4, Biome.WOODED_BADLANDS, Biome.WOODED_BADLANDS),

        X40(4, 0, Biome.SNOWY_TAIGA, Biome.SNOWY_TAIGA),
        X41(4, 1, Biome.OLD_GROWTH_SPRUCE_TAIGA, Biome.OLD_GROWTH_PINE_TAIGA),
        X42(4, 2, Biome.DARK_FOREST, Biome.DARK_FOREST), x43(4, 3, Biome.JUNGLE, Biome.JUNGLE),
        X44(4, 4, Biome.WOODED_BADLANDS, Biome.WOODED_BADLANDS),;

        private int temp;
        private int humidity;
        private Biome b;
        private Biome weirdBiome;

        PlateauBiome(int humidity, int temp, Biome b, Biome weirdBiome) {
            this.humidity = humidity;
            this.temp = temp;
            this.weirdBiome = weirdBiome;
            this.b = b;
        }

        public static Biome getBiome(int humidity, int temp, double weirdness) {
            for (PlateauBiome mb : PlateauBiome.values()) {
                if (mb.humidity == humidity && mb.temp == temp) {
                    if (weirdness < 0) {
                        return mb.b;
                    } else {
                        return mb.weirdBiome;
                    }
                }
            }
            throw new IllegalArgumentException("plateau biome h = " + humidity + " t = " + temp);
        }
    }

    /**
     * Shattered biomes by temperature and humidity
     */
    private enum ShatteredBiome {
        X00(0, 0, Biome.WINDSWEPT_GRAVELLY_HILLS, Biome.WINDSWEPT_GRAVELLY_HILLS),
        X01(0, 1, Biome.WINDSWEPT_GRAVELLY_HILLS, Biome.WINDSWEPT_GRAVELLY_HILLS),
        X02(0, 2, Biome.WINDSWEPT_HILLS, Biome.WINDSWEPT_HILLS), X03(0, 3, Biome.SAVANNA, Biome.SAVANNA),
        X04(0, 4, Biome.DESERT, Biome.DESERT),

        X10(1, 0, Biome.WINDSWEPT_GRAVELLY_HILLS, Biome.WINDSWEPT_GRAVELLY_HILLS),
        X11(1, 1, Biome.WINDSWEPT_GRAVELLY_HILLS, Biome.WINDSWEPT_GRAVELLY_HILLS),
        X12(1, 2, Biome.WINDSWEPT_HILLS, Biome.WINDSWEPT_HILLS),
        X13(1, 3, Biome.SAVANNA_PLATEAU, Biome.SAVANNA_PLATEAU), X14(1, 4, Biome.DESERT, Biome.DESERT),

        X20(2, 0, Biome.WINDSWEPT_HILLS, Biome.WINDSWEPT_HILLS),
        X21(2, 1, Biome.WINDSWEPT_HILLS, Biome.WINDSWEPT_HILLS),
        X22(2, 2, Biome.WINDSWEPT_HILLS, Biome.WINDSWEPT_HILLS), X23(2, 3, Biome.FOREST, Biome.FOREST),
        X24(2, 4, Biome.DESERT, Biome.DESERT),

        X30(3, 0, Biome.WINDSWEPT_FOREST, Biome.WINDSWEPT_FOREST),
        X31(3, 1, Biome.WINDSWEPT_FOREST, Biome.WINDSWEPT_FOREST),
        X32(3, 2, Biome.WINDSWEPT_FOREST, Biome.WINDSWEPT_FOREST), X33(3, 3, Biome.JUNGLE, Biome.SPARSE_JUNGLE),
        X34(3, 4, Biome.DESERT, Biome.DESERT),

        X40(4, 0, Biome.WINDSWEPT_FOREST, Biome.WINDSWEPT_FOREST),
        X41(4, 1, Biome.WINDSWEPT_FOREST, Biome.WINDSWEPT_FOREST),
        X42(4, 2, Biome.WINDSWEPT_FOREST, Biome.WINDSWEPT_FOREST), X43(4, 3, Biome.JUNGLE, Biome.BAMBOO_JUNGLE),
        X44(4, 4, Biome.DESERT, Biome.DESERT),;

        private int temperature;
        private int humidity;
        private Biome biome;
        private Biome weirdBiome;

        ShatteredBiome(int h, int t, Biome b, Biome weirdBiome) {
            this.humidity = h;
            this.temperature = t;
            this.weirdBiome = weirdBiome;
            this.biome = b;
        }

        public static Biome getBiome(int h, int t, double we) {
            for (ShatteredBiome mb : ShatteredBiome.values()) {
                if (mb.humidity == h && mb.temperature == t) {
                    if (we < 0) {
                        return mb.biome;
                    } else {
                        return mb.weirdBiome;
                    }
                }
            }
            throw new IllegalArgumentException("shattered biome h = " + h + " t = " + t);
        }
    }

    /**
     * Continental location by continentalness
     */
    private enum ContLoc {
        MUSHROOM_FIELDS(-1.2, -1.05),
        DEEP_OCEAN(-1.05, -0.455),
        OCEAN(-0.455, -0.19),
        COAST(-0.19, -0.11),
        NEAR_INLAND(-0.11, 0.03),
        MID_INLAND(0.03, 0.3),
        FAR_INLAND(0.3, 10.0);

        private double min;
        private double max;

        ContLoc(double min, double max) {
            this.min = min;
            this.max = max;
        }

        public static ContLoc getCont(double continentalness) {
            for (ContLoc c : ContLoc.values()) {
                if (continentalness >= c.min && continentalness < c.max) {
                    return c;
                }
            }
            throw new IllegalArgumentException("contloc out of spec value = " + continentalness);
        }
    }

    private final Boxed addon;
    protected final int dist;
    private final int spawnX;
    private final int spawnZ;
    protected final Map<BlockFace, SortedMap<Double, Biome>> quadrants;

    protected AbstractSeedBiomeProvider(Boxed boxed, Environment env) {
        this.addon = boxed;
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

    @Override
    public Biome getBiome(WorldInfo worldInfo, int x, int y, int z, BiomeParameterPoint biomeParameterPoint) {
        return this.getMappedBiome(worldInfo, x, y, z, biomeParameterPoint);
    }

    @Override
    public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
        throw new IllegalStateException("This method should never be called.");
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
    private Biome getVanillaBiome(WorldInfo worldInfo, int x, int y, int z, BiomeParameterPoint bpb, Biome def) {
        if (worldInfo.getEnvironment() == Environment.NORMAL) {
            return getNormalBiome(bpb, def);
        }
        return getNetherBiome(bpb);

    }

    private @NonNull Biome getNetherBiome(BiomeParameterPoint bpb) {
        // Bring these values to 1 decimal place
        double temp = Math.round(bpb.getTemperature() * 10.0) / 10.0;
        double humidity = Math.round(bpb.getHumidity() * 10.0) / 10.0;
        if (temp == -0.5D && humidity == 0.0D) {
            return Biome.BASALT_DELTAS;
        } else if (temp == 0.4D && humidity == 0.0D) {
            return Biome.CRIMSON_FOREST;
        } else if (temp == 0.0D && humidity == -0.5D) {
            return Biome.SOUL_SAND_VALLEY;
        } else if (temp == -0.5D && humidity == 0.5D) {
            return Biome.WARPED_FOREST;
        }

        return Biome.NETHER_WASTES;
    }

    private @NonNull Biome getNormalBiome(BiomeParameterPoint bpb, Biome def) {
        /* 
         * Caves
         */
        double d = bpb.getDepth();
        if (d > 0.2 && d < 0.9) {
            if (bpb.getHumidity() >= 0.4 && bpb.getHumidity() < 1.0) {
                def = Biome.LUSH_CAVES;
            }
            if (bpb.getContinentalness() >= 0.8D && bpb.getContinentalness() <= 1.0D) {
                def = Biome.DRIPSTONE_CAVES;
            }
        }
        if (d >= 0.9) { // Vanilla has this as 1.1
            def = Biome.DEEP_DARK;
        }
        if (def != null) {
            return def;
        }

        /*
         * Continentalness (also known as continents) is used to decide between ocean/beach/land biomes. 
         */
        int temp = getTemp(bpb.getTemperature());
        int humidity = getHumidity(bpb.getHumidity());
        int erosion = getErosion(bpb.getErosion());
        double we = bpb.getWeirdness();

        return switch (ContLoc.getCont(bpb.getContinentalness())) {
        case COAST -> coastBiome(humidity, temp, erosion, we);
        case DEEP_OCEAN -> deepOceanBiome(temp);
        case FAR_INLAND -> farInlandBiome(humidity, temp, erosion, we);
        case MID_INLAND -> midInlandBiome(humidity, temp, erosion, we);
        case MUSHROOM_FIELDS -> Biome.MUSHROOM_FIELDS;
        case NEAR_INLAND -> nearInlandBiome(humidity, temp, erosion, we);
        case OCEAN -> oceanBiome(temp);
        default -> farInlandBiome(humidity, temp, erosion, we);
        };
    }

    /**
     * Erosion values are divided into 7 levels. The corresponding ranges from level 0 to level 6 are:
     * -1.0~-0.78, -0.78~-0.375, -0.375~-0.2225, -0.2225~0.05, 0.05~0.45, 0.45~0.55, 0.55~1.0.
     *
     * @param erosion The erosion value
     * @return The erosion level (0-6)
     */
    private int getErosion(double erosion) {
        if (erosion < -0.78) {
            return 0;
        } else if (erosion < -0.375) {
            return 1;
        } else if (erosion < -0.2225) {
            return 2;
        } else if (erosion < 0.05) {
            return 3;
        } else if (erosion < 0.45) {
            return 4;
        } else if (erosion < 0.55) {
            return 5;
        } else {
            return 6;
        }
    }

    /**
     * Humidity values are also divided into 5 levels. The corresponding ranges from level 0 to level 4 are:
     * -1.0~-0.35, -0.35~-0.1, -0.1~0.1, 0.1~0.3, 0.3~1.0.
     *
     * @param humidity The humidity value
     * @return The humidity level (0-4)
     */
    private int getHumidity(double humidity) {
        if (humidity < -0.35) {
            return 0;
        } else if (humidity < -0.1) {
            return 1;
        } else if (humidity < 0.1) {
            return 2;
        } else if (humidity < 0.3) {
            return 3;
        } else {
            return 4;
        }
    }

    /**
     * Temperature is a noise parameter used only in biome generation and does not affect terrain generation.
     * Temperature values are divided into 5 levels. The corresponding ranges from level 0 to level 4 are:
     * -1.0~-0.45, -0.45~-0.15, -0.15~0.2, 0.2~0.55, 0.55~1.0.
     *
     * @param temp The temperature value
     * @return The temperature level (0-4)
     */
    private int getTemp(double temp) {
        if (temp < -0.45) {
            return 0;
        } else if (temp < -0.15) {
            return 1;
        } else if (temp < 0.2) {
            return 2;
        } else if (temp < 0.55) {
            return 3;
        } else {
            return 4;
        }
    }


    private @NonNull Biome oceanBiome(int temp) {
        return switch (temp) {
        case 0 -> Biome.FROZEN_OCEAN;
        case 1 -> Biome.COLD_OCEAN;
        case 2 -> Biome.OCEAN;
        case 3 -> Biome.LUKEWARM_OCEAN;
        case 4 -> Biome.WARM_OCEAN;
        default -> Biome.OCEAN;
        };
    }

    private @NonNull Biome deepOceanBiome(int temp) {
        return switch (temp) {
        case 0 -> Biome.DEEP_FROZEN_OCEAN;
        case 1 -> Biome.DEEP_COLD_OCEAN;
        case 2 -> Biome.DEEP_OCEAN;
        case 3 -> Biome.DEEP_LUKEWARM_OCEAN;
        case 4 -> Biome.WARM_OCEAN;
        default -> Biome.DEEP_OCEAN;
        };
    }

    private @NonNull Biome farInlandBiome(int humidity, int temperature, int erosion, double weirdness) {
        return switch (Ridges.getRidge(convertToY(weirdness))) {
        case HIGH -> getFarInlandHighBiome(humidity, temperature, erosion, weirdness);
        case LOW -> getFarInlandLowBiome(humidity, temperature, erosion, weirdness);
        case MID -> getFarInlandMidBiome(humidity, temperature, erosion, weirdness);
        case PEAKS -> getFarInlandPeaksBiome(humidity, temperature, erosion, weirdness);
        default -> getFarInlandValleysBiome(humidity, temperature, erosion, weirdness);
        };
    }

    private @NonNull Biome getFarInlandValleysBiome(int humidity, int temperature, int erosion, double weirdness) {
        if (erosion >= 0 && erosion < 6) {
            if (temperature > 0D) {
                return Biome.RIVER;
            } else {
                return Biome.FROZEN_RIVER;
            }
        }
        // e == 6
        if (temperature == 0) {
            return Biome.FROZEN_RIVER;
        }
        if (temperature == 1 || temperature == 2) {
            return Biome.SWAMP;
        }
        return Biome.MANGROVE_SWAMP;
    }

    private @NonNull Biome getValleysNearInlandBiome(int humidity, int temperature, int erosion, double weirdness) {
        return getFarInlandValleysBiome(humidity, temperature, erosion, weirdness);
    }

    private @NonNull Biome getFarInlandPeaksBiome(int humidity, int temperature, int erosion, double weirdness) {
        if (erosion == 0) {
            if (temperature >= 0 && temperature <= 2) {
                if (weirdness < 0) {
                    return Biome.JAGGED_PEAKS;
                } else {
                    return Biome.FROZEN_PEAKS;
                }
            } else if (temperature == 3) {
                return Biome.STONY_PEAKS;
            }
            return BadlandBiome.getBiome(humidity, weirdness);
        } else if (erosion == 1) {
            if (temperature == 0 && (humidity == 0 || humidity == 1)) {
                return Biome.SNOWY_SLOPES;
            } else if (temperature == 0 && humidity > 1) {
                return Biome.GROVE;
            } else if (temperature < 4) {
                return MiddleBiome.getBiome(humidity, temperature, weirdness);
            }
            return BadlandBiome.getBiome(humidity, weirdness);
        } else if (erosion >= 2 && erosion <= 4) {
            // Middle biomes
            return MiddleBiome.getBiome(humidity, temperature, weirdness);
        } else if (erosion == 5) {
            if (weirdness < 0 && (temperature == 0 || temperature == 1 || humidity == 4)) {
                // Shattered biomes
                return ShatteredBiome.getBiome(humidity, temperature, weirdness);
            }
            if (weirdness > 0 && (temperature > 2 && temperature <= 4 && humidity >= 0 && humidity <= 4)) {
                return Biome.WINDSWEPT_SAVANNA;
            }
        }
        // middle biomes
        return MiddleBiome.getBiome(humidity, temperature, weirdness);
    }

    private @NonNull Biome getFarInlandMidBiome(int humidity, int temperature, int erosion, double weirdness) {
        if (erosion == 0) {
            if (temperature < 3 && (humidity == 0 || humidity == 1)) {
                return Biome.SNOWY_SLOPES;
            }
            if (temperature < 3 && (humidity == 2 || humidity == 3 || humidity == 4)) {
                return Biome.GROVE;
            }
            return PlateauBiome.getBiome(humidity, temperature, weirdness);
        } else if (erosion == 1) {
            if (temperature == 0 && (humidity == 0 || humidity == 1)) {
                return Biome.SNOWY_SLOPES;
            }
            if (temperature == 0 && (humidity == 2 || humidity == 3 || humidity == 4)) {
                return Biome.GROVE;
            }
            return PlateauBiome.getBiome(humidity, temperature, weirdness);
        } else if (erosion == 2) {
            return PlateauBiome.getBiome(humidity, temperature, weirdness);
        } else if (erosion == 3) {
            // Middle biomes
            return MiddleBiome.getBiome(humidity, temperature, weirdness);
        } else if (erosion == 5) {
            if (weirdness < 0 && (temperature == 0 || temperature == 1) || humidity == 4) {
                // Middle biomes
                return MiddleBiome.getBiome(humidity, temperature, weirdness);
            }
            if (weirdness > 0 && (temperature > 2 && temperature <= 4 && humidity >= 0 && humidity <= 4)) {
                return Biome.WINDSWEPT_SAVANNA;
            }
        }
        if (temperature == 0) {
            // Middle Biomes
            return MiddleBiome.getBiome(humidity, temperature, weirdness);
        }
        if (temperature == 1 || temperature == 2) {
            return Biome.SWAMP;
        }
        return Biome.MANGROVE_SWAMP;
    }

    private @NonNull Biome getFarInlandLowBiome(int humidity, int temperature, int erosion, double weirdness) {
        if (erosion >= 0 && erosion < 2) {
            if (temperature < 4) {
                return MiddleBiome.getBiome(humidity, temperature, weirdness);
            }
            return BadlandBiome.getBiome(humidity, weirdness);
        } else if (erosion >= 2 && erosion < 5) {
            // Middle Biomes
            return MiddleBiome.getBiome(humidity, temperature, weirdness);
        } else if (erosion == 5) {
            if (weirdness < 0 && (temperature == 0 || temperature == 1 || humidity == 4)) {
                // Middle biomes
                return MiddleBiome.getBiome(humidity, temperature, weirdness);
            }
            if (weirdness > 0 && (temperature > 2 && temperature <= 4 && humidity >= 0 && humidity <= 4)) {
                return Biome.WINDSWEPT_SAVANNA;
            }
        }
        if (temperature == 0) {
            return MiddleBiome.getBiome(humidity, temperature, weirdness);
        } else if (temperature == 1 || temperature == 2) {
            return Biome.SWAMP;
        }
        return Biome.MANGROVE_SWAMP;
    }

    private @NonNull Biome getFarInlandHighBiome(int humidity, int temperature, int erosion, double weirdness) {
        if (erosion == 0) {
            if (temperature < 3 && (humidity == 0 || humidity == 1)) {
                return Biome.SNOWY_SLOPES;
            }
            if (temperature < 3 && (humidity == 2 || humidity == 3 || humidity == 4)) {
                return Biome.GROVE;
            }
            return PlateauBiome.getBiome(humidity, temperature, weirdness);
        } else if (erosion == 1) {
            if (temperature == 0 && (humidity == 0 || humidity == 1)) {
                return Biome.SNOWY_SLOPES;
            }
            if (temperature == 0 && (humidity == 2 || humidity == 3 || humidity == 4)) {
                return Biome.GROVE;
            }
            if (temperature > 0 && temperature < 4) {
                return MiddleBiome.getBiome(humidity, temperature, weirdness);
            }
            return BadlandBiome.getBiome(humidity, weirdness);
        } else if (erosion == 2 || erosion == 3 || erosion == 4) {
            return PlateauBiome.getBiome(humidity, temperature, weirdness);
        } else if (erosion == 4 || erosion == 6) {
            return MiddleBiome.getBiome(humidity, temperature, weirdness);
        }
        return ShatteredBiome.getBiome(humidity, temperature, weirdness);
    }

    private @NonNull Biome nearInlandBiome(int humidity, int temperature, int erosion, double weirdness) {
        return switch (Ridges.getRidge(convertToY(weirdness))) {
        case HIGH -> getHighNearInlandBiome(humidity, temperature, erosion, weirdness);
        case LOW -> getLowNearInlandBiome(humidity, temperature, erosion, weirdness);
        case MID -> getMidNearInlandBiome(humidity, temperature, erosion, weirdness);
        case PEAKS -> getPeaksNearInlandBiome(humidity, temperature, erosion, weirdness);
        default -> getValleysNearInlandBiome(humidity, temperature, erosion, weirdness);
        };
    }

    private @NonNull Biome getPeaksNearInlandBiome(int humidity, int temperature, int erosion, double weirdness) {
        if (erosion == 0) {
            if (temperature >= 0 && temperature <= 2) {
                if (weirdness < 0) {
                    return Biome.JAGGED_PEAKS;
                } else {
                    return Biome.FROZEN_PEAKS;
                }
            } else if (temperature == 3) {
                return Biome.STONY_PEAKS;
            }
            return BadlandBiome.getBiome(humidity, weirdness);

        } else if (erosion == 1) {
            if (temperature == 0 && (humidity == 0 || humidity == 1)) {
                return Biome.SNOWY_SLOPES;
            } else if (temperature == 0 && humidity > 1) {
                return Biome.GROVE;
            } else if (temperature < 4) {
                return MiddleBiome.getBiome(humidity, temperature, weirdness);
            }
            return BadlandBiome.getBiome(humidity, weirdness);
        } else if (erosion >= 2 && erosion <= 4) {
            // Middle biomes
            return MiddleBiome.getBiome(humidity, temperature, weirdness);
        } else if (erosion == 5) {
            if (weirdness < 0 && (temperature == 0 || temperature == 1 || humidity == 4)) {
                // Shattered biomes
                return ShatteredBiome.getBiome(humidity, temperature, weirdness);
            }
            if (weirdness > 0 && (temperature > 2 && temperature <= 4 && humidity >= 0 && humidity <= 4)) {
                return Biome.WINDSWEPT_SAVANNA;
            }
        }
        // middle biomes
        return MiddleBiome.getBiome(humidity, temperature, weirdness);
    }

    private @NonNull Biome getMidNearInlandBiome(int humidity, int temperature, int erosion, double weirdness) {
        if (erosion == 0) {
            if (temperature < 3 && (humidity == 0 || humidity == 1)) {
                return Biome.SNOWY_SLOPES;
            }
            if (temperature < 3 && (humidity == 2 || humidity == 3 || humidity == 4)) {
                return Biome.GROVE;
            }
            return PlateauBiome.getBiome(humidity, temperature, weirdness);
        } else if (erosion == 1) {
            if (temperature == 0 && (humidity == 0 || humidity == 1)) {
                return Biome.SNOWY_SLOPES;
            }
            if (temperature == 0 && (humidity == 2 || humidity == 3 || humidity == 4)) {
                return Biome.GROVE;
            }
            if (temperature > 0 && temperature < 4) {
                return MiddleBiome.getBiome(humidity, temperature, weirdness);
            }
            return BadlandBiome.getBiome(humidity, weirdness);
        } else if (erosion >= 2 && erosion <= 4) {
            // Middle biomes
            return MiddleBiome.getBiome(humidity, temperature, weirdness);
        } else if (erosion == 5) {
            if (weirdness < 0 && (temperature == 0 || temperature == 1) || humidity == 4) {
                // Middle biomes
                return MiddleBiome.getBiome(humidity, temperature, weirdness);
            }
            if (weirdness > 0 && (temperature > 2 && temperature <= 4 && humidity >= 0 && humidity <= 4)) {
                return Biome.WINDSWEPT_SAVANNA;
            }
        }
        if (temperature == 0) {
            // Middle Biomes
            return MiddleBiome.getBiome(humidity, temperature, weirdness);
        }
        if (temperature == 1 || temperature == 2) {
            return Biome.SWAMP;
        }
        return Biome.MANGROVE_SWAMP;
    }

    private @NonNull Biome getLowNearInlandBiome(int humidity, int temperature, int erosion, double weirdness) {
        if (erosion >= 0 && erosion < 2) {
            if (temperature < 4) {
                return MiddleBiome.getBiome(humidity, temperature, weirdness);
            }
            return BadlandBiome.getBiome(humidity, weirdness);
        } else if (erosion >= 2 && erosion < 5) {
            // Middle Biomes
            return MiddleBiome.getBiome(humidity, temperature, weirdness);
        } else if (erosion == 5) {
            if (weirdness < 0 && (temperature == 0 || temperature == 1 || humidity == 4)) {
                // Middle biomes
                return MiddleBiome.getBiome(humidity, temperature, weirdness);
            }
            if (weirdness > 0 && (temperature > 2 && temperature <= 4 && humidity >= 0 && humidity <= 4)) {
                return Biome.WINDSWEPT_SAVANNA;
            }
        }
        if (temperature == 0) {
            return MiddleBiome.getBiome(humidity, temperature, weirdness);
        } else if (temperature == 1 || temperature == 2) {
            return Biome.SWAMP;
        }
        return Biome.MANGROVE_SWAMP;
    }

    private @NonNull Biome getHighNearInlandBiome(int humidity, int temperature, int erosion, double weirdness) {
        if (erosion == 0) {
            if (temperature < 3 && (humidity == 0 || humidity == 1)) {
                return Biome.SNOWY_SLOPES;
            }
            if (temperature < 3 && (humidity == 2 || humidity == 3 || humidity == 4)) {
                return Biome.GROVE;
            }
            return PlateauBiome.getBiome(humidity, temperature, weirdness);
        } else if (erosion == 1) {
            if (temperature == 0 && (humidity == 0 || humidity == 1)) {
                return Biome.SNOWY_SLOPES;
            }
            if (temperature == 0 && (humidity == 2 || humidity == 3 || humidity == 4)) {
                return Biome.GROVE;
            }
            if (temperature > 0 && temperature < 4) {
                return MiddleBiome.getBiome(humidity, temperature, weirdness);
            }
            return BadlandBiome.getBiome(humidity, weirdness);
        } else if (erosion >= 2 && erosion <= 4) {
            // Middle biomes
            return MiddleBiome.getBiome(humidity, temperature, weirdness);
        } else if (erosion == 5) {
            if (weirdness < 0 && (temperature == 0 || temperature == 1) || humidity == 4) {
                // Middle biomes
                return MiddleBiome.getBiome(humidity, temperature, weirdness);
            }
            if (weirdness > 0 && (temperature > 2 && temperature <= 4 && humidity >= 0 && humidity <= 4)) {
                return Biome.WINDSWEPT_SAVANNA;
            }
        }
        return MiddleBiome.getBiome(humidity, temperature, weirdness);
    }

    private @NonNull Biome midInlandBiome(int humidity, int temperature, int erosion, double weirdness) {
        return switch (Ridges.getRidge(convertToY(weirdness))) {
        case HIGH -> getHighMidInlandBiome(humidity, temperature, erosion, weirdness);
        case LOW -> getLowMidInlandBiome(humidity, temperature, erosion, weirdness);
        case MID -> getMidMidInlandBiome(humidity, temperature, erosion, weirdness);
        case PEAKS -> getPeaksMidInlandBiome(humidity, temperature, erosion, weirdness);
        default -> getValleysMidInlandBiome(humidity, temperature, erosion, weirdness);
        };
    }

    private @NonNull Biome getValleysMidInlandBiome(int humidity, int temperature, int erosion, double weirdness) {
        if (erosion == 0 || erosion == 1) {
            if (temperature < 4) {
                return MiddleBiome.getBiome(humidity, temperature, weirdness);
            } else {
                return BadlandBiome.getBiome(humidity, weirdness);
            }
        }
        if (erosion >= 2 && erosion <= 5) {
            if (temperature == 0) {
                return Biome.FROZEN_RIVER;
            } else {
                return Biome.RIVER;
            }
        }
        if (temperature == 0) {
            return Biome.FROZEN_RIVER;
        }
        if (temperature == 1 || temperature == 2) {
            return Biome.SWAMP;
        }
        return Biome.MANGROVE_SWAMP;
    }

    private @NonNull Biome getPeaksMidInlandBiome(int humidity, int temperature, int erosion, double weirdness) {
        if (erosion == 0 || erosion == 1) {
            if (temperature >= 0 && temperature <= 2) {
                if (weirdness < 0) {
                    return Biome.JAGGED_PEAKS;
                } else {
                    return Biome.FROZEN_PEAKS;
                }
            } else if (temperature == 3) {
                return Biome.STONY_PEAKS;
            }
            return BadlandBiome.getBiome(humidity, weirdness);

        } else if (erosion == 2) {
            return PlateauBiome.getBiome(humidity, temperature, weirdness);
        } else if (erosion == 3) {
            if (temperature < 4) {
                // Middle biomes
                return MiddleBiome.getBiome(humidity, temperature, weirdness);
            }
            return BadlandBiome.getBiome(humidity, weirdness);
        } else if (erosion == 4 || erosion == 6) {
            return MiddleBiome.getBiome(humidity, temperature, weirdness);
        }
        return ShatteredBiome.getBiome(humidity, temperature, weirdness);
    }

    private @NonNull Biome getMidMidInlandBiome(int humidity, int temperature, int erosion, double weirdness) {
        if (erosion == 0) {
            if (temperature < 3 && (humidity == 0 || humidity == 1)) {
                return Biome.SNOWY_SLOPES;
            }
            if (temperature < 3 && (humidity == 2 || humidity == 3 || humidity == 4)) {
                return Biome.GROVE;
            }
            return PlateauBiome.getBiome(humidity, temperature, weirdness);
        } else if (erosion == 1) {
            if (temperature == 0 && (humidity == 0 || humidity == 1)) {
                return Biome.SNOWY_SLOPES;
            }
            if (temperature == 0 && (humidity == 2 || humidity == 3 || humidity == 4)) {
                return Biome.GROVE;
            }
            if (temperature > 0 && temperature < 4) {
                return MiddleBiome.getBiome(humidity, temperature, weirdness);
            }
            return BadlandBiome.getBiome(humidity, weirdness);
        } else if (erosion == 2 || erosion == 3) {
            if (temperature < 4) {
                return MiddleBiome.getBiome(humidity, temperature, weirdness);
            }
            return BadlandBiome.getBiome(humidity, weirdness);
        } else if (erosion == 4) {
            // Middle biomes
            return MiddleBiome.getBiome(humidity, temperature, weirdness);
        } else if (erosion == 5) {
            return ShatteredBiome.getBiome(humidity, temperature, weirdness);
        }
        if (temperature == 0) {
            // Middle Biomes
            return MiddleBiome.getBiome(humidity, temperature, weirdness);
        }
        if (temperature == 1 || temperature == 2) {
            return Biome.SWAMP;
        }
        return Biome.MANGROVE_SWAMP;
    }

    private @NonNull Biome getLowMidInlandBiome(int humidity, int temperature, int erosion, double weirdness) {
        if (erosion == 0 || erosion == 1) {
            if (temperature == 0 && (humidity == 0 || humidity == 1)) {
                return Biome.SNOWY_SLOPES;
            } else if (temperature == 0 && humidity > 1) {
                return Biome.GROVE;
            } else if (temperature < 4) {
                return MiddleBiome.getBiome(humidity, temperature, weirdness);
            }
            return BadlandBiome.getBiome(humidity, weirdness);
        }
        if (erosion == 2 || erosion == 3) {
            if (temperature < 4) {
                return MiddleBiome.getBiome(humidity, temperature, weirdness);
            }
            return BadlandBiome.getBiome(humidity, weirdness);
        }
        // e == 6
        if (temperature == 0) {
            // Middle Biomes
            return MiddleBiome.getBiome(humidity, temperature, weirdness);
        }
        if (temperature == 1 || temperature == 2) {
            return Biome.SWAMP;
        }
        return Biome.MANGROVE_SWAMP;
    }

    private @NonNull Biome getHighMidInlandBiome(int humidity, int temperature, int erosion, double weirdness) {
        if (erosion == 0) {
            if (temperature < 3 && weirdness < 0D) {
                return Biome.JAGGED_PEAKS;
            }
            if (temperature < 3 && weirdness > 0.0D) {
                return Biome.FROZEN_PEAKS;
            }
            if (temperature == 3) {
                return Biome.STONY_PEAKS;
            }
            return BadlandBiome.getBiome(humidity, weirdness);
        } else if (erosion == 1) {
            if (temperature < 3 && (humidity == 0 || humidity == 1)) {
                return Biome.SNOWY_SLOPES;
            }
            if (temperature < 3 && (humidity == 2 || humidity == 3 || humidity == 4)) {
                return Biome.GROVE;
            }
            return PlateauBiome.getBiome(humidity, temperature, weirdness);
        } else if (erosion == 2) {
            return PlateauBiome.getBiome(humidity, temperature, weirdness);
        } else if (erosion == 3) {
            if (temperature < 4) {
                // Middle biomes
                return MiddleBiome.getBiome(humidity, temperature, weirdness);
            }
            return BadlandBiome.getBiome(humidity, weirdness);
        } else if (erosion == 4) {
            // Middle biomes
            return MiddleBiome.getBiome(humidity, temperature, weirdness);
        } else if (erosion == 5) {
            return ShatteredBiome.getBiome(humidity, temperature, weirdness);
        }
        return MiddleBiome.getBiome(humidity, temperature, weirdness);
    }

    private @NonNull Biome coastBiome(int humidity, int temperature, int erosion, double weirdness) {
        return switch (Ridges.getRidge(convertToY(weirdness))) {
        case HIGH -> getHighCoastBionme(humidity, temperature, erosion, weirdness);
        case LOW -> getLowCoastBionme(humidity, temperature, erosion, weirdness);
        case MID -> getMidCoastBionme(humidity, temperature, erosion, weirdness);
        case PEAKS -> getPeaksCoastBionme(humidity, temperature, erosion, weirdness);
        default -> getValleysCoastBionme(humidity, temperature, erosion, weirdness);
        };
    }

    private @NonNull Biome getValleysCoastBionme(int humidity, int temperature, int erosion, double weirdness) {
        if (temperature > 0D) {
            return Biome.RIVER;
        }
        return Biome.FROZEN_RIVER;
    }

    private @NonNull Biome getPeaksCoastBionme(int humidity, int temperature, int erosion, double weirdness) {
        if (erosion == 0) {
            if (temperature >= 0 && temperature <= 2) {
                if (weirdness < 0) {
                    return Biome.JAGGED_PEAKS;
                } else {
                    return Biome.FROZEN_PEAKS;
                }
            } else if (temperature == 3) {
                return Biome.STONY_PEAKS;
            }
            return BadlandBiome.getBiome(humidity, weirdness);

        } else if (erosion == 1) {
            if (temperature == 0 && (humidity == 0 || humidity == 1)) {
                return Biome.SNOWY_SLOPES;
            } else if (temperature == 0 && humidity > 1) {
                return Biome.GROVE;
            } else if (temperature < 4) {
                return MiddleBiome.getBiome(humidity, temperature, weirdness);
            }
            return BadlandBiome.getBiome(humidity, weirdness);
        } else if (erosion >= 2 && erosion <= 4) {
            // Middle biomes
            return MiddleBiome.getBiome(humidity, temperature, weirdness);
        } else if (erosion == 5) {
            if (weirdness < 0 && (temperature == 0 || temperature == 1 || humidity == 4)) {
                // Shattered biomes
                return ShatteredBiome.getBiome(humidity, temperature, weirdness);
            }
            if (weirdness > 0 && (temperature > 2 && temperature <= 4 && humidity >= 0 && humidity <= 4)) {
                return Biome.WINDSWEPT_SAVANNA;
            }
        }
        // middle biomes
        return MiddleBiome.getBiome(humidity, temperature, weirdness);
    }

    private @NonNull Biome getMidCoastBionme(int humidity, int temperature, int erosion, double weirdness) {
        if (erosion > 0 && erosion < 3) {
            return Biome.STONY_SHORE;
        } else if (erosion == 3) {
            // Middle Biomes
            return MiddleBiome.getBiome(humidity, temperature, weirdness);
        } else if (erosion == 4) {
            if (weirdness < 0) {
                // Beach Biomes
                return getBeachBiome(temperature);
            } else {
                // Middle biomes
                return MiddleBiome.getBiome(humidity, temperature, weirdness);
            }
        } else if (erosion == 5) {
            if (weirdness < 0) {
                // Beach Biomes
                return getBeachBiome(temperature);
            }
            if (weirdness < 0 && (temperature == 0 || temperature == 1 || humidity == 4)) {
                // Middle biomes
                return MiddleBiome.getBiome(humidity, temperature, weirdness);
            }
            if (weirdness > 0 && (temperature > 2 && temperature <= 4 && humidity >= 0 && humidity <= 4)) {
                return Biome.WINDSWEPT_SAVANNA;
            }

        } else if (erosion == 6) {
            if (weirdness < 0D) {
                // Beach Biomes
                return getBeachBiome(temperature);
            } else {
                // Middle Biomes
                return MiddleBiome.getBiome(humidity, temperature, weirdness);
            }
        }
        // Else Beach biomes
        return getBeachBiome(temperature);
    }

    private @NonNull Biome getLowCoastBionme(int humidity, int temperature, int erosion, double weirdness) {
        if (erosion >= 0 && erosion < 3) {
            return Biome.STONY_SHORE;
        } else if (erosion >= 3 && erosion < 5) {
            // Beach Biomes
            return getBeachBiome(temperature);
        } else if (erosion == 5) {
            if (weirdness < 0) {
                // Beach Biomes
                return getBeachBiome(temperature);
            }
            if (weirdness < 0 && (temperature == 0 || temperature == 1 || humidity == 4)) {
                // Middle biomes
                return MiddleBiome.getBiome(humidity, temperature, weirdness);
            }
            if (weirdness > 0 && (temperature > 2 && temperature <= 4 && humidity >= 0 && humidity <= 4)) {
                return Biome.WINDSWEPT_SAVANNA;
            }
        }
        // Else Beach biomes
        return getBeachBiome(temperature);
    }

    private @NonNull Biome getHighCoastBionme(int humidity, int temperature, int erosion, double weirdness) {
        if (erosion >= 0 && erosion < 5) {
            // Middle Biomes
            return MiddleBiome.getBiome(humidity, temperature, weirdness);
        } else if (erosion == 5) {
            if (weirdness < 0 && (temperature == 0 || temperature == 1 || humidity == 4)) {
                // Middle biomes
                return MiddleBiome.getBiome(humidity, temperature, weirdness);
            }
            if (weirdness > 0 && (temperature > 2 && temperature <= 4 && humidity >= 0 && humidity <= 4)) {
                return Biome.WINDSWEPT_SAVANNA;
            }
        }
        // Middle Biomes
        return MiddleBiome.getBiome(humidity, temperature, weirdness);
    }

    Biome getBeachBiome(int t) {
        return switch (t) {
        case 0 -> Biome.SNOWY_BEACH;
        case 4 -> Biome.DESERT;
        default -> Biome.BEACH;
        };
    }

    public static double convertToY(double x) {
        x = Math.clamp(x, -1, 1);
        if (x >= -1 && x < -0.5) {
            return 2 * x + 1;
        } else if (x >= -0.5 && x < 0) {
            return -2 * x;
        } else if (x >= 0 && x < 0.5) {
            return 2 * x;
        } else if (x >= 0.5 && x <= 1) {
            return -2 * x + 1;
        } else {
            throw new IllegalArgumentException("Invalid x value. x must be in the range [-1, 1]. Value = " + x);
        }
    }

    /**
     * Get the mapped 2D biome at position x,z
     * @param worldInfo world info
     * @param x - block coord
     * @param y - block coord
     * @param z - block coord
     * @param biomeParameterPoint 
     * @return Biome
     */
    private Biome getMappedBiome(WorldInfo worldInfo, int x, int y, int z, BiomeParameterPoint biomeParameterPoint) {
        /*
         * Biomes go around the island centers
         *
         */

        Vector s = new Vector(x, 0, z);
        Vector l = new Vector(spawnX, 0, spawnZ);
        double dis = l.distance(s);
        if (dis > dist * 2) {
            // Only customize biomes around the spawn location
            return getVanillaBiome(worldInfo, x, y, z, biomeParameterPoint, null);
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
            result = getVanillaBiome(worldInfo, x, y, z, biomeParameterPoint, null);

        }

        // Caves
        if (biomeParameterPoint.getDepth() > 0.2) {
            result = getVanillaBiome(worldInfo, x, y, z, biomeParameterPoint, null);
        }

        return result;

    }

    @Override
    public List<Biome> getBiomes(WorldInfo worldInfo) {
        // Return all of them for now!
        return Registry.BIOME.stream().filter(b -> !b.equals(Biome.CUSTOM)).toList();
    }

    /**
     * Loads the custom biomes from the config file
     * @param config - Yaml configuration object
     * @param sector - the direction section to load
     * @return sorted map of the biomes and their probabilities as keys
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
                    Biome biome = Biome.valueOf(split[1].toUpperCase(Locale.ENGLISH));
                    if (biome == null) {
                        addon.logError(split[1].toUpperCase(Locale.ENGLISH) + " is an unknown biome on this server.");
                        result.put(d, Biome.CUSTOM);
                    } else {
                        // A biome of null means that no alternative biome should be applied
                        result.put(d, biome);
                    }
                } catch (Exception e) {
                    addon.logError(sector + ": " + split[0]
                            + " does not seem to be a double. For integers add a .0 to the end");
                }
            } else {
                addon.logError(ring + " must be in the format ratio:biome where ratio is a double.");
            }
        }
        return result;
    }

}
