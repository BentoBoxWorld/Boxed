package world.bentobox.boxed.generators.biomes;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.generator.BiomeParameterPoint;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.Vector;
import org.eclipse.jdt.annotation.NonNull;

import com.google.common.base.Enums;

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

    private enum Ridges {
        VALLEYS(-1.0, -0.85), LOW(-0.85, -0.6), MID(-0.6, 0.2), HIGH(0.2, 0.7), PEAKS(0.7, 1.0);

        private double low;
        private double high;

        Ridges(double low, double high) {
            this.low = low;
            this.high = high;
        }

        public static Ridges getRidge(double x) {
            for (Ridges r : Ridges.values()) {
                if (x >= r.low && x < r.high) {
                    return r;
                }
            }
            return MID;
        }

    }

    private enum BadlandBiome {
        h0(0, Biome.BADLANDS, Biome.ERODED_BADLANDS), h1(1, Biome.BADLANDS, Biome.ERODED_BADLANDS),
        h2(2, Biome.BADLANDS, Biome.BADLANDS), h3(3, Biome.WOODED_BADLANDS, Biome.WOODED_BADLANDS),
        h4(4, Biome.WOODED_BADLANDS, Biome.WOODED_BADLANDS);

        private int h;
        private Biome biome;
        private Biome biome2;

        BadlandBiome(int h, Biome biome, Biome biome2) {
            this.h = h;
            this.biome = biome;
            this.biome2 = biome2;
        }

        public static Biome getBiome(int h, double we) {
            for (BadlandBiome mb : BadlandBiome.values()) {
                if (mb.h == h) {
                    if (we < 0) {
                        return mb.biome;
                    } else {
                        return mb.biome2;
                    }
                }
            }
            throw new IllegalArgumentException("badlands biome h = " + h);
        }
    }

    private enum MiddleBiome {
        x00(0, 0, Biome.SNOWY_PLAINS, Biome.ICE_SPIKES), x01(0, 1, Biome.PLAINS, Biome.PLAINS),
        x02(0, 2, Biome.FLOWER_FOREST, Biome.SUNFLOWER_PLAINS), x03(0, 3, Biome.SAVANNA, Biome.SAVANNA),
        x04(0, 4, Biome.DESERT, Biome.DESERT),

        x10(1, 0, Biome.SNOWY_PLAINS, Biome.SNOWY_PLAINS), x11(1, 1, Biome.PLAINS, Biome.PLAINS),
        x12(1, 2, Biome.PLAINS, Biome.PLAINS), x13(1, 3, Biome.SAVANNA, Biome.SAVANNA),
        x14(1, 4, Biome.DESERT, Biome.DESERT),

        x20(2, 0, Biome.SNOWY_PLAINS, Biome.SNOWY_TAIGA), x21(2, 1, Biome.FOREST, Biome.FOREST),
        x22(2, 2, Biome.FOREST, Biome.FOREST), x23(2, 3, Biome.FOREST, Biome.PLAINS),
        x24(2, 4, Biome.DESERT, Biome.DESERT),

        x30(3, 0, Biome.SNOWY_TAIGA, Biome.SNOWY_TAIGA), x31(3, 1, Biome.TAIGA, Biome.TAIGA),
        x32(3, 2, Biome.BIRCH_FOREST, Biome.OLD_GROWTH_BIRCH_FOREST), x33(3, 3, Biome.JUNGLE, Biome.SPARSE_JUNGLE),
        x34(3, 4, Biome.DESERT, Biome.DESERT),

        x40(4, 0, Biome.TAIGA, Biome.TAIGA), x41(4, 1, Biome.OLD_GROWTH_SPRUCE_TAIGA, Biome.OLD_GROWTH_PINE_TAIGA),
        x42(4, 2, Biome.DARK_FOREST, Biome.DARK_FOREST), x43(4, 3, Biome.JUNGLE, Biome.BAMBOO_JUNGLE),
        x44(4, 4, Biome.DESERT, Biome.DESERT),
        ;

        private int t;
        private int h;
        private Biome b;
        private Biome weirdBiome;

        MiddleBiome(int h, int t, Biome b, Biome weirdBiome) {
            this.h = h;
            this.t = t;
            this.weirdBiome = weirdBiome;
            this.b = b;
        }

        public static Biome getBiome(int h, int t, double we) {
            for (MiddleBiome mb : MiddleBiome.values()) {
                if (mb.h == h && mb.t == t) {
                    if (we < 0) {
                        return mb.b;
                    } else {
                        return mb.weirdBiome;
                    }
                }
            }
            throw new IllegalArgumentException("middle biome h = " + h + " t = " + t);
        }
    }

    private enum PlateauBiome {
        x00(0, 0, Biome.SNOWY_PLAINS, Biome.ICE_SPIKES), x01(0, 1, Biome.MEADOW, Biome.CHERRY_GROVE),
        x02(0, 2, Biome.MEADOW, Biome.CHERRY_GROVE), x03(0, 3, Biome.SAVANNA_PLATEAU, Biome.SAVANNA_PLATEAU),
        x04(0, 4, Biome.BADLANDS, Biome.ERODED_BADLANDS),

        x10(1, 0, Biome.SNOWY_PLAINS, Biome.SNOWY_PLAINS), x11(1, 1, Biome.MEADOW, Biome.MEADOW),
        x12(1, 2, Biome.MEADOW, Biome.CHERRY_GROVE), x13(1, 3, Biome.SAVANNA_PLATEAU, Biome.SAVANNA_PLATEAU),
        x14(1, 4, Biome.BADLANDS, Biome.ERODED_BADLANDS),

        x20(2, 0, Biome.SNOWY_PLAINS, Biome.SNOWY_TAIGA), x21(2, 1, Biome.FOREST, Biome.MEADOW),
        x22(2, 2, Biome.MEADOW, Biome.BIRCH_FOREST), x23(2, 3, Biome.FOREST, Biome.FOREST),
        x24(2, 4, Biome.BADLANDS, Biome.BADLANDS),

        x30(3, 0, Biome.SNOWY_TAIGA, Biome.SNOWY_TAIGA), x31(3, 1, Biome.TAIGA, Biome.MEADOW),
        x32(3, 2, Biome.MEADOW, Biome.BIRCH_FOREST), x33(3, 3, Biome.FOREST, Biome.FOREST),
        x34(3, 4, Biome.WOODED_BADLANDS, Biome.WOODED_BADLANDS),

        x40(4, 0, Biome.SNOWY_TAIGA, Biome.SNOWY_TAIGA),
        x41(4, 1, Biome.OLD_GROWTH_SPRUCE_TAIGA, Biome.OLD_GROWTH_PINE_TAIGA),
        x42(4, 2, Biome.DARK_FOREST, Biome.DARK_FOREST), x43(4, 3, Biome.JUNGLE, Biome.JUNGLE),
        x44(4, 4, Biome.WOODED_BADLANDS, Biome.WOODED_BADLANDS),;

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

    private enum ShatteredBiome {
        x00(0, 0, Biome.WINDSWEPT_GRAVELLY_HILLS, Biome.WINDSWEPT_GRAVELLY_HILLS),
        x01(0, 1, Biome.WINDSWEPT_GRAVELLY_HILLS, Biome.WINDSWEPT_GRAVELLY_HILLS),
        x02(0, 2, Biome.WINDSWEPT_HILLS, Biome.WINDSWEPT_HILLS), x03(0, 3, Biome.SAVANNA, Biome.SAVANNA),
        x04(0, 4, Biome.DESERT, Biome.DESERT),

        x10(1, 0, Biome.WINDSWEPT_GRAVELLY_HILLS, Biome.WINDSWEPT_GRAVELLY_HILLS),
        x11(1, 1, Biome.WINDSWEPT_GRAVELLY_HILLS, Biome.WINDSWEPT_GRAVELLY_HILLS),
        x12(1, 2, Biome.WINDSWEPT_HILLS, Biome.WINDSWEPT_HILLS),
        x13(1, 3, Biome.SAVANNA_PLATEAU, Biome.SAVANNA_PLATEAU), x14(1, 4, Biome.DESERT, Biome.DESERT),

        x20(2, 0, Biome.WINDSWEPT_HILLS, Biome.WINDSWEPT_HILLS),
        x21(2, 1, Biome.WINDSWEPT_HILLS, Biome.WINDSWEPT_HILLS),
        x22(2, 2, Biome.WINDSWEPT_HILLS, Biome.WINDSWEPT_HILLS), x23(2, 3, Biome.FOREST, Biome.FOREST),
        x24(2, 4, Biome.DESERT, Biome.DESERT),

        x30(3, 0, Biome.WINDSWEPT_FOREST, Biome.WINDSWEPT_FOREST),
        x31(3, 1, Biome.WINDSWEPT_FOREST, Biome.WINDSWEPT_FOREST),
        x32(3, 2, Biome.WINDSWEPT_FOREST, Biome.WINDSWEPT_FOREST), x33(3, 3, Biome.JUNGLE, Biome.SPARSE_JUNGLE),
        x34(3, 4, Biome.DESERT, Biome.DESERT),

        x40(4, 0, Biome.WINDSWEPT_FOREST, Biome.WINDSWEPT_FOREST),
        x41(4, 1, Biome.WINDSWEPT_FOREST, Biome.WINDSWEPT_FOREST),
        x42(4, 2, Biome.WINDSWEPT_FOREST, Biome.WINDSWEPT_FOREST), x43(4, 3, Biome.JUNGLE, Biome.BAMBOO_JUNGLE),
        x44(4, 4, Biome.DESERT, Biome.DESERT),;

        private int t;
        private int h;
        private Biome b;
        private Biome weirdBiome;

        ShatteredBiome(int h, int t, Biome b, Biome weirdBiome) {
            this.h = h;
            this.t = t;
            this.weirdBiome = weirdBiome;
            this.b = b;
        }

        public static Biome getBiome(int h, int t, double we) {
            for (ShatteredBiome mb : ShatteredBiome.values()) {
                if (mb.h == h && mb.t == t) {
                    if (we < 0) {
                        return mb.b;
                    } else {
                        return mb.weirdBiome;
                    }
                }
            }
            throw new IllegalArgumentException("shattered biome h = " + h + " t = " + t);
        }
    }

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

        public static ContLoc getCont(double value) {
            for (ContLoc c : ContLoc.values()) {
                if (value >= c.min && value < c.max) {
                    return c;
                }
            }
            throw new IllegalArgumentException("contloc out of spec value = " + value);
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

    private @NonNull Biome farInlandBiome(int h, int t, int e, double we) {
        Ridges r = Ridges.getRidge(convertToY(we));
        switch (r) {
        case HIGH:
            if (e == 0) {
                if (t < 3 && (h == 0 || h == 1)) {
                    return Biome.SNOWY_SLOPES;
                }
                if (t < 3 && (h == 2 || h == 3 || h == 4)) {
                    return Biome.GROVE;
                }
                return PlateauBiome.getBiome(h, t, we);
            } else if (e == 1) {
                if (t == 0 && (h == 0 || h == 1)) {
                    return Biome.SNOWY_SLOPES;
                }
                if (t == 0 && (h == 2 || h == 3 || h == 4)) {
                    return Biome.GROVE;
                }
                if (t > 0 && t < 4) {
                    return MiddleBiome.getBiome(h, t, we);
                }
                return BadlandBiome.getBiome(h, we);
            } else if (e == 2 || e == 3 || e == 4) {
                return PlateauBiome.getBiome(h, t, we);
            } else if (e == 4 || e == 6) {
                return MiddleBiome.getBiome(h, t, we);
            }
            return ShatteredBiome.getBiome(h, t, we);
        case LOW:
            if (e >= 0 && e < 2) {
                if (t < 4) {
                    return MiddleBiome.getBiome(h, t, we);
                }
                return BadlandBiome.getBiome(h, we);
            } else if (e >= 2 && e < 5) {
                // Middle Biomes
                return MiddleBiome.getBiome(h, t, we);
            } else if (e == 5) {
                if (we < 0 && (t == 0 || t == 1 || h == 4)) {
                    // Middle biomes
                    return MiddleBiome.getBiome(h, t, we);
                }
                if (we > 0 && (t > 2 && t <= 4 && h >= 0 && h <= 4)) {
                    return Biome.WINDSWEPT_SAVANNA;
                }
            }
            if (t == 0) {
                return MiddleBiome.getBiome(h, t, we);
            } else if (t == 1 || t == 2) {
                return Biome.SWAMP;
            }
            return Biome.MANGROVE_SWAMP;
        case MID:
            if (e == 0) {
                if (t < 3 && (h == 0 || h == 1)) {
                    return Biome.SNOWY_SLOPES;
                }
                if (t < 3 && (h == 2 || h == 3 || h == 4)) {
                    return Biome.GROVE;
                }
                return PlateauBiome.getBiome(h, t, we);
            } else if (e == 1) {
                if (t == 0 && (h == 0 || h == 1)) {
                    return Biome.SNOWY_SLOPES;
                }
                if (t == 0 && (h == 2 || h == 3 || h == 4)) {
                    return Biome.GROVE;
                }
                return PlateauBiome.getBiome(h, t, we);
            } else if (e == 2) {
                return PlateauBiome.getBiome(h, t, we);
            } else if (e == 3) {
                // Middle biomes
                return MiddleBiome.getBiome(h, t, we);
            } else if (e == 5) {
                if (we < 0 && (t == 0 || t == 1) || h == 4) {
                    // Middle biomes
                    return MiddleBiome.getBiome(h, t, we);
                }
                if (we > 0 && (t > 2 && t <= 4 && h >= 0 && h <= 4)) {
                    return Biome.WINDSWEPT_SAVANNA;
                }
            }
            if (t == 0) {
                // Middle Biomes
                return MiddleBiome.getBiome(h, t, we);
            }
            if (t == 1 || t == 2) {
                return Biome.SWAMP;
            }
            return Biome.MANGROVE_SWAMP;
        case PEAKS:
            if (e == 0) {
                if (t >= 0 && t <= 2) {
                    if (we < 0) {
                        return Biome.JAGGED_PEAKS;
                    } else {
                        return Biome.FROZEN_PEAKS;
                    }
                } else if (t == 3) {
                    return Biome.STONY_PEAKS;
                }
                return BadlandBiome.getBiome(h, we);

            } else if (e == 1) {
                if (t == 0 && (h == 0 || h == 1)) {
                    return Biome.SNOWY_SLOPES;
                } else if (t == 0 && h > 1) {
                    return Biome.GROVE;
                } else if (t < 4) {
                    return MiddleBiome.getBiome(h, t, we);
                }
                return BadlandBiome.getBiome(h, we);
            } else if (e >= 2 && e <= 4) {
                // Middle biomes
                return MiddleBiome.getBiome(h, t, we);
            } else if (e == 5) {
                if (we < 0 && (t == 0 || t == 1 || h == 4)) {
                    // Shattered biomes
                    return ShatteredBiome.getBiome(h, t, we);
                }
                if (we > 0 && (t > 2 && t <= 4 && h >= 0 && h <= 4)) {
                    return Biome.WINDSWEPT_SAVANNA;
                }
            }
            // middle biomes
            return MiddleBiome.getBiome(h, t, we);
        default:
            //case VALLEYS:
            if (e >= 0 && e < 6) {
                if (t > 0D) {
                    return Biome.RIVER;
                } else {
                    return Biome.FROZEN_RIVER;
                }
            }
            // e == 6
            if (t == 0) {
                return Biome.FROZEN_RIVER;
            }
            if (t == 1 || t == 2) {
                return Biome.SWAMP;
            }
            return Biome.MANGROVE_SWAMP;
        }
    }

    private @NonNull Biome nearInlandBiome(int h, int t, int e, double we) {
        Ridges r = Ridges.getRidge(convertToY(we)); // Normalize
        switch (r) {
        case HIGH:
            if (e == 0) {
                if (t < 3 && (h == 0 || h == 1)) {
                    return Biome.SNOWY_SLOPES;
                }
                if (t < 3 && (h == 2 || h == 3 || h == 4)) {
                    return Biome.GROVE;
                }
                return PlateauBiome.getBiome(h, t, we);
            } else if (e == 1) {
                if (t == 0 && (h == 0 || h == 1)) {
                    return Biome.SNOWY_SLOPES;
                }
                if (t == 0 && (h == 2 || h == 3 || h == 4)) {
                    return Biome.GROVE;
                }
                if (t > 0 && t < 4) {
                    return MiddleBiome.getBiome(h, t, we);
                }
                return BadlandBiome.getBiome(h, we);
            } else if (e >= 2 && e <= 4) {
                // Middle biomes
                return MiddleBiome.getBiome(h, t, we);
            } else if (e == 5) {
                if (we < 0 && (t == 0 || t == 1) || h == 4) {
                    // Middle biomes
                    return MiddleBiome.getBiome(h, t, we);
                }
                if (we > 0 && (t > 2 && t <= 4 && h >= 0 && h <= 4)) {
                    return Biome.WINDSWEPT_SAVANNA;
                }
            }
            return MiddleBiome.getBiome(h, t, we);
        case LOW:
            if (e >= 0 && e < 2) {
                if (t < 4) {
                    return MiddleBiome.getBiome(h, t, we);
                }
                return BadlandBiome.getBiome(h, we);
            } else if (e >= 2 && e < 5) {
                // Middle Biomes
                return MiddleBiome.getBiome(h, t, we);
            } else if (e == 5) {
                if (we < 0 && (t == 0 || t == 1 || h == 4)) {
                    // Middle biomes
                    return MiddleBiome.getBiome(h, t, we);
                }
                if (we > 0 && (t > 2 && t <= 4 && h >= 0 && h <= 4)) {
                    return Biome.WINDSWEPT_SAVANNA;
                }
            }
            if (t == 0) {
                return MiddleBiome.getBiome(h, t, we);
            } else if (t == 1 || t == 2) {
                return Biome.SWAMP;
            }
            return Biome.MANGROVE_SWAMP;
        case MID:
            if (e == 0) {
                if (t < 3 && (h == 0 || h == 1)) {
                    return Biome.SNOWY_SLOPES;
                }
                if (t < 3 && (h == 2 || h == 3 || h == 4)) {
                    return Biome.GROVE;
                }
                return PlateauBiome.getBiome(h, t, we);
            } else if (e == 1) {
                if (t == 0 && (h == 0 || h == 1)) {
                    return Biome.SNOWY_SLOPES;
                }
                if (t == 0 && (h == 2 || h == 3 || h == 4)) {
                    return Biome.GROVE;
                }
                if (t > 0 && t < 4) {
                    return MiddleBiome.getBiome(h, t, we);
                }
                return BadlandBiome.getBiome(h, we);
            } else if (e >= 2 && e <= 4) {
                // Middle biomes
                return MiddleBiome.getBiome(h, t, we);
            } else if (e == 5) {
                if (we < 0 && (t == 0 || t == 1) || h == 4) {
                    // Middle biomes
                    return MiddleBiome.getBiome(h, t, we);
                }
                if (we > 0 && (t > 2 && t <= 4 && h >= 0 && h <= 4)) {
                    return Biome.WINDSWEPT_SAVANNA;
                }
            }
            if (t == 0) {
                // Middle Biomes
                return MiddleBiome.getBiome(h, t, we);
            }
            if (t == 1 || t == 2) {
                return Biome.SWAMP;
            }
            return Biome.MANGROVE_SWAMP;
        case PEAKS:
            if (e == 0) {
                if (t >= 0 && t <= 2) {
                    if (we < 0) {
                        return Biome.JAGGED_PEAKS;
                    } else {
                        return Biome.FROZEN_PEAKS;
                    }
                } else if (t == 3) {
                    return Biome.STONY_PEAKS;
                }
                return BadlandBiome.getBiome(h, we);

            } else if (e == 1) {
                if (t == 0 && (h == 0 || h == 1)) {
                    return Biome.SNOWY_SLOPES;
                } else if (t == 0 && h > 1) {
                    return Biome.GROVE;
                } else if (t < 4) {
                    return MiddleBiome.getBiome(h, t, we);
                }
                return BadlandBiome.getBiome(h, we);
            } else if (e >= 2 && e <= 4) {
                // Middle biomes
                return MiddleBiome.getBiome(h, t, we);
            } else if (e == 5) {
                if (we < 0 && (t == 0 || t == 1 || h == 4)) {
                    // Shattered biomes
                    return ShatteredBiome.getBiome(h, t, we);
                }
                if (we > 0 && (t > 2 && t <= 4 && h >= 0 && h <= 4)) {
                    return Biome.WINDSWEPT_SAVANNA;
                }
            }
            // middle biomes
            return MiddleBiome.getBiome(h, t, we);
        default:
            //case VALLEYS:
            if (e >= 0 && e < 6) {
                if (t > 0D) {
                    return Biome.RIVER;
                } else {
                    return Biome.FROZEN_RIVER;
                }
            }
            // e == 6
            if (t == 0) {
                return Biome.FROZEN_RIVER;
            }
            if (t == 1 || t == 2) {
                return Biome.SWAMP;
            }
            return Biome.MANGROVE_SWAMP;
        }
    }

    private @NonNull Biome midInlandBiome(int h, int t, int e, double we) {
        Ridges r = Ridges.getRidge(convertToY(we)); // Normalize
        switch (r) {
        case HIGH:
            if (e == 0) {
                if (t < 3 && we < 0D) {
                    return Biome.JAGGED_PEAKS;
                }
                if (t < 3 && we > 0.0D) {
                    return Biome.FROZEN_PEAKS;
                }
                if (t == 3) {
                    return Biome.STONY_PEAKS;
                }
                return BadlandBiome.getBiome(h, we);
            } else if (e == 1) {
                if (t < 3 && (h == 0 || h == 1)) {
                    return Biome.SNOWY_SLOPES;
                }
                if (t < 3 && (h == 2 || h == 3 || h == 4)) {
                    return Biome.GROVE;
                }
                return PlateauBiome.getBiome(h, t, we);
            } else if (e == 2) {
                return PlateauBiome.getBiome(h, t, we);
            } else if (e == 3) {
                if (t < 4) {
                    // Middle biomes
                    return MiddleBiome.getBiome(h, t, we);
                }
                return BadlandBiome.getBiome(h, we);
            } else if (e == 4) {
                // Middle biomes
                return MiddleBiome.getBiome(h, t, we);
            } else if (e == 5) {
                return ShatteredBiome.getBiome(h, t, we);
            }
            return MiddleBiome.getBiome(h, t, we);
        case LOW:
            if (e == 0 || e == 1) {
                if (t == 0 && (h == 0 || h == 1)) {
                    return Biome.SNOWY_SLOPES;
                } else if (t == 0 && h > 1) {
                    return Biome.GROVE;
                } else if (t < 4) {
                    return MiddleBiome.getBiome(h, t, we);
                }
                return BadlandBiome.getBiome(h, we);
            }
            if (e == 2 || e == 3) {
                if (t < 4) {
                    return MiddleBiome.getBiome(h, t, we);
                }
                return BadlandBiome.getBiome(h, we);
            }
            // e == 6
            if (t == 0) {
                // Middle Biomes
                return MiddleBiome.getBiome(h, t, we);
            }
            if (t == 1 || t == 2) {
                return Biome.SWAMP;
            }
            return Biome.MANGROVE_SWAMP;
        case MID:
            if (e == 0) {
                if (t < 3 && (h == 0 || h == 1)) {
                    return Biome.SNOWY_SLOPES;
                }
                if (t < 3 && (h == 2 || h == 3 || h == 4)) {
                    return Biome.GROVE;
                }
                return PlateauBiome.getBiome(h, t, we);
            } else if (e == 1) {
                if (t == 0 && (h == 0 || h == 1)) {
                    return Biome.SNOWY_SLOPES;
                }
                if (t == 0 && (h == 2 || h == 3 || h == 4)) {
                    return Biome.GROVE;
                }
                if (t > 0 && t < 4) {
                    return MiddleBiome.getBiome(h, t, we);
                }
                return BadlandBiome.getBiome(h, we);
            } else if (e == 2 || e == 3) {
                if (t < 4) {
                    return MiddleBiome.getBiome(h, t, we);
                }
                return BadlandBiome.getBiome(h, we);
            } else if (e == 4) {
                // Middle biomes
                return MiddleBiome.getBiome(h, t, we);
            } else if (e == 5) {
                return ShatteredBiome.getBiome(h, t, we);
            }
            if (t == 0) {
                // Middle Biomes
                return MiddleBiome.getBiome(h, t, we);
            }
            if (t == 1 || t == 2) {
                return Biome.SWAMP;
            }
            return Biome.MANGROVE_SWAMP;
        case PEAKS:
            if (e == 0 || e == 1) {
                if (t >= 0 && t <= 2) {
                    if (we < 0) {
                        return Biome.JAGGED_PEAKS;
                    } else {
                        return Biome.FROZEN_PEAKS;
                    }
                } else if (t == 3) {
                    return Biome.STONY_PEAKS;
                }
                return BadlandBiome.getBiome(h, we);

            } else if (e == 2) {
                return PlateauBiome.getBiome(h, t, we);
            } else if (e == 3) {
                if (t < 4) {
                    // Middle biomes
                    return MiddleBiome.getBiome(h, t, we);
                }
                return BadlandBiome.getBiome(h, we);
            } else if (e == 4 || e == 6) {
                return MiddleBiome.getBiome(h, t, we);
            }
            return ShatteredBiome.getBiome(h, t, we);
        default:
            //case VALLEYS:
            if (e == 0 || e == 1) {
                if (t < 4) {
                    return MiddleBiome.getBiome(h, t, we);
                } else {
                    return BadlandBiome.getBiome(h, we);
                }
            }
            if (e >= 2 && e <= 5) {
                if (t == 0) {
                    return Biome.FROZEN_RIVER;
                } else {
                    return Biome.RIVER;
                }
            }
            if (t == 0) {
                return Biome.FROZEN_RIVER;
            }
            if (t == 1 || t == 2) {
                return Biome.SWAMP;
            }
            return Biome.MANGROVE_SWAMP;
        }
    }

    private @NonNull Biome coastBiome(int h, int t, int e, double we) {
        Ridges r = Ridges.getRidge(convertToY(we)); // Normalize
        switch (r) {
        case HIGH:
            if (e >= 0 && e < 5) {
                // Middle Biomes
                return MiddleBiome.getBiome(h, t, we);
            } else if (e == 5) {
                if (we < 0 && (t == 0 || t == 1 || h == 4)) {
                    // Middle biomes
                    return MiddleBiome.getBiome(h, t, we);
                }
                if (we > 0 && (t > 2 && t <= 4 && h >= 0 && h <= 4)) {
                    return Biome.WINDSWEPT_SAVANNA;
                }
            }
            // Middle Biomes
            return MiddleBiome.getBiome(h, t, we);
        case LOW:
            if (e >= 0 && e < 3) {
                return Biome.STONY_SHORE;
            } else if (e >= 3 && e < 5) {
                // Beach Biomes
                return getBeachBiome(t);
            } else if (e == 5) {
                if (we < 0) {
                    // Beach Biomes
                    return getBeachBiome(t);
                }
                if (we < 0 && (t == 0 || t == 1 || h == 4)) {
                    // Middle biomes
                    return MiddleBiome.getBiome(h, t, we);
                }
                if (we > 0 && (t > 2 && t <= 4 && h >= 0 && h <= 4)) {
                    return Biome.WINDSWEPT_SAVANNA;
                }
            }
            // Else Beach biomes
            return getBeachBiome(t);
        case MID:
            if (e > 0 && e < 3) {
                return Biome.STONY_SHORE;
            } else if (e == 3) {
                // Middle Biomes
                return MiddleBiome.getBiome(h, t, we);
            } else if (e == 4) {
                if (we < 0) {
                    // Beach Biomes
                    return getBeachBiome(t);
                } else {
                    // Middle biomes
                    return MiddleBiome.getBiome(h, t, we);
                }
            } else if (e == 5) {
                if (we < 0) {
                    // Beach Biomes
                    return getBeachBiome(t);
                }
                if (we < 0 && (t == 0 || t == 1 || h == 4)) {
                    // Middle biomes
                    return MiddleBiome.getBiome(h, t, we);
                }
                if (we > 0 && (t > 2 && t <= 4 && h >= 0 && h <= 4)) {
                    return Biome.WINDSWEPT_SAVANNA;
                }

            } else if (e == 6) {
                if (we < 0D) {
                    // Beach Biomes
                    return getBeachBiome(t);
                } else {
                    // Middle Biomes
                    return MiddleBiome.getBiome(h, t, we);
                }
            }
            // Else Beach biomes
            return getBeachBiome(t);
        case PEAKS:
            if (e == 0) {
                if (t >= 0 && t <= 2) {
                    if (we < 0) {
                        return Biome.JAGGED_PEAKS;
                    } else {
                        return Biome.FROZEN_PEAKS;
                    }
                } else if (t == 3) {
                    return Biome.STONY_PEAKS;
                }
                return BadlandBiome.getBiome(h, we);

            } else if (e == 1) {
                if (t == 0 && (h == 0 || h == 1)) {
                    return Biome.SNOWY_SLOPES;
                } else if (t == 0 && h > 1) {
                    return Biome.GROVE;
                } else if (t < 4) {
                    return MiddleBiome.getBiome(h, t, we);
                }
                return BadlandBiome.getBiome(h, we);
            } else if (e >= 2 && e <= 4) {
                // Middle biomes
                return MiddleBiome.getBiome(h, t, we);
            } else if (e == 5) {
                if (we < 0 && (t == 0 || t == 1 || h == 4)) {
                    // Shattered biomes
                    return ShatteredBiome.getBiome(h, t, we);
                }
                if (we > 0 && (t > 2 && t <= 4 && h >= 0 && h <= 4)) {
                    return Biome.WINDSWEPT_SAVANNA;
                }
            }
            // middle biomes
            return MiddleBiome.getBiome(h, t, we);
        default:
            //case VALLEYS:
            if (t > 0D) {

                return Biome.RIVER;
            }
            return Biome.FROZEN_RIVER;
        }
    }


    Biome getBeachBiome(int t) {
        return switch (t) {
        case 0 -> Biome.SNOWY_BEACH;
        case 4 -> Biome.DESERT;
        default -> Biome.BEACH;
        };
    }

    public static double convertToY(double x) {
        x = Math.max(-1, Math.min(1, x)); // Clamp value
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
        return Arrays.stream(Biome.values()).filter(b -> !b.equals(Biome.CUSTOM)).toList();
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
                    Biome biome = Enums.getIfPresent(Biome.class, split[1].toUpperCase(Locale.ENGLISH)).orNull();
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
