package world.bentobox.boxed.generators;

import java.io.File;

import org.bukkit.block.Biome;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.noise.SimplexNoiseGenerator;

import nl.rutgerkok.worldgeneratorapi.BaseNoiseGenerator;
import nl.rutgerkok.worldgeneratorapi.BiomeGenerator;
import world.bentobox.boxed.Boxed;

/**
 * @author tastybento
 *
 */
public class BasicWorldGenerator implements BaseNoiseGenerator {

    private final SimplexNoiseGenerator mainNoiseGenerator;
    private final Boxed addon;
    private final YamlConfiguration config;


    public BasicWorldGenerator(Boxed addon, long seed) {
        this.addon = addon;
        // Initialize the noise generator based on the world seed
        this.mainNoiseGenerator = new SimplexNoiseGenerator(seed);
        // Load the config
        File biomeFile = new File(addon.getDataFolder(), "biomes.yml");
        if (!biomeFile.exists()) {
            addon.saveResource("biomes.yml", true);
        }
        config = YamlConfiguration.loadConfiguration(biomeFile);
    }


    @Override
    public void getNoise(BiomeGenerator biomeGenerator, double[] buffer, int scaledX, int scaledZ) {
        // Repeat on an island boundary
        int dist = addon.getSettings().getIslandDistance();
        Biome biome = biomeGenerator.getZoomedOutBiome(scaledX, scaledZ);
        double noiseScaleHorizontal = config.getDouble("biomes." + biome.name() + ".scale", 10D);
        double height = config.getDouble("biomes." + biome.name() + ".height", 8D);
        double x = ((((double)scaledX*4) % dist) / 4) / noiseScaleHorizontal;
        double z = ((((double)scaledZ*4) % dist) / 4) / noiseScaleHorizontal;

        for (int y = 0; y < buffer.length; y++) {
            double noise = this.mainNoiseGenerator.noise(x, y, z);
            double heightOffset = height - y;
            buffer[y] = noise + heightOffset;
        }
    }

}