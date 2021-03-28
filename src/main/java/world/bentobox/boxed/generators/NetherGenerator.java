package world.bentobox.boxed.generators;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.noise.NoiseGenerator;
import org.bukkit.util.noise.SimplexNoiseGenerator;

import com.google.common.base.Enums;

import nl.rutgerkok.worldgeneratorapi.BaseNoiseGenerator;
import nl.rutgerkok.worldgeneratorapi.BiomeGenerator;
import world.bentobox.boxed.Boxed;

/**
 * Generates the Nether
 * @author tastybento
 *
 */
public class NetherGenerator implements BaseNoiseGenerator {

    private final BiomeNoise DEFAULT_NOISE = new BiomeNoise(10D, 0D, 2D);
    private final NoiseGenerator mainNoiseGenerator;
    private final Boxed addon;
    private final YamlConfiguration config;
    private final Map<Biome, BiomeNoise> biomeNoiseMap;


    public NetherGenerator(Boxed addon, long seed) {
        this.addon = addon;
        // Initialize the noise generator based on the world seed
        this.mainNoiseGenerator = new SimplexNoiseGenerator(seed);
        // Load the config
        File biomeFile = new File(addon.getDataFolder(), "biomes.yml");
        if (!biomeFile.exists()) {
            addon.saveResource("biomes.yml", true);
        }
        config = YamlConfiguration.loadConfiguration(biomeFile);
        biomeNoiseMap = new EnumMap<>(Biome.class);
        if (config.isConfigurationSection("nether.biomes")) {
            for (String key : config.getConfigurationSection("nether.biomes").getKeys(false)) {
                double noiseScaleHorizontal = config.getDouble("nether.biomes." + key + ".scale", 10D);
                double height = config.getDouble("nether.biomes." + key + ".height", 0D);
                double noiseScaleVertical = config.getDouble("nether.biomes." + key + ".vscale", 2D);
                Enums.getIfPresent(Biome.class, key).toJavaUtil()
                .ifPresent(biome -> biomeNoiseMap.put(biome, new BiomeNoise(noiseScaleHorizontal, height, noiseScaleVertical)));
            }
        }
    }

    class BiomeNoise {
        double noiseScaleHorizontal = 10D;
        double height = 0D;
        double noiseScaleVertical = 2D;
        /**
         * @param noiseScaleHorizontal
         * @param height
         * @param noiseScaleVertical
         */
        public BiomeNoise(double noiseScaleHorizontal, double height, double noiseScaleVertical) {
            this.noiseScaleHorizontal = noiseScaleHorizontal;
            this.height = height;
            this.noiseScaleVertical = noiseScaleVertical;
        }
        @Override
        public String toString() {
            return "BiomeNoise [noiseScaleHorizontal=" + noiseScaleHorizontal + ", height=" + height
                    + ", noiseScaleVertical=" + noiseScaleVertical + "]";
        }

    }
    @Override
    public TerrainSettings getTerrainSettings() {
        TerrainSettings ts = new TerrainSettings();
        ts.stoneBlock = Material.NETHERRACK.createBlockData();
        ts.waterBlock = Material.LAVA.createBlockData();
        return ts;
    }

    @Override
    public void getNoise(BiomeGenerator biomeGenerator, double[] buffer, int scaledX, int scaledZ) {
        // Repeat on an island boundary
        int dist = addon.getSettings().getIslandDistance();

        Biome biome = biomeGenerator.getZoomedOutBiome(scaledX, scaledZ);

        if (biome == null) {
            // edge of island
            biome = Biome.NETHER_WASTES;
        }
        BiomeNoise bm = this.biomeNoiseMap.getOrDefault(biome, DEFAULT_NOISE);
        double x = ((((double)scaledX*4) % dist) / 4) / bm.noiseScaleHorizontal;
        double z = ((((double)scaledZ*4) % dist) / 4) / bm.noiseScaleHorizontal;
        for (int y = 0; y < 16; y++) {
            double noise = this.mainNoiseGenerator.noise(x, y / Math.max(0.5, bm.noiseScaleVertical), z);
            double heightOffset = y < 12 && bm.height != 0 ? bm.height - y : 0;
            buffer[y] = noise + heightOffset;
        }


        /*
        for (int y = 0; y < buffer.length; y++) {
            double noise = this.mainNoiseGenerator.noise(x, y, z);
            double heightOffset = height - y;
            buffer[y] = noise + heightOffset;
        }
        // Ceiling

        x = ((((double)scaledX*4) % dist) / 4);
        z = ((((double)scaledZ*4) % dist) / 4);
        for (int y = 15; y > height + 2; y--) {
            double noise = this.mainNoiseGenerator.noise(x, y, z) * 2;
            double heightOffset = y - height;
            buffer[y] = noise + heightOffset;
        }*/
    }

}