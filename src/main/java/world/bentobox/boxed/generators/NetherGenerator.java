package world.bentobox.boxed.generators;

import java.io.File;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.noise.PerlinNoiseGenerator;

import nl.rutgerkok.worldgeneratorapi.BaseNoiseGenerator;
import nl.rutgerkok.worldgeneratorapi.BiomeGenerator;
import world.bentobox.boxed.Boxed;

/**
 * Generates the Nether
 * @author tastybento
 *
 */
public class NetherGenerator implements BaseNoiseGenerator {

    private final PerlinNoiseGenerator mainNoiseGenerator;
    private final Boxed addon;
    private final YamlConfiguration config;


    public NetherGenerator(Boxed addon, long seed) {
        this.addon = addon;
        // Initialize the noise generator based on the world seed
        this.mainNoiseGenerator = new PerlinNoiseGenerator(seed);
        // Load the config
        File biomeFile = new File(addon.getDataFolder(), "biomes.yml");
        if (!biomeFile.exists()) {
            addon.saveResource("biomes.yml", true);
        }
        config = YamlConfiguration.loadConfiguration(biomeFile);
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
        double noiseScaleHorizontal = 10D;
        double height = 8D;
        Biome biome = biomeGenerator.getZoomedOutBiome(scaledX, scaledZ);
        if (biome == null) {
            // edge of island
            biome = Biome.NETHER_WASTES;
            height = 6;
        } else {
            noiseScaleHorizontal = config.getDouble("nether.biomes." + biome.name() + ".scale", 10D);
            height = config.getDouble("nether.biomes." + biome.name() + ".height", 8D);
        }
        double x = ((((double)scaledX*4) % dist) / 4) / noiseScaleHorizontal;
        double z = ((((double)scaledZ*4) % dist) / 4) / noiseScaleHorizontal;

        for (int y = 0; y < buffer.length; y++) {
            double noise = this.mainNoiseGenerator.noise(x, y, z);
            double heightOffset = height - y;
            buffer[y] = noise + heightOffset;
        }
        // Ceiling
        x = ((((double)scaledX*4) % dist) / 4);
        z = ((((double)scaledZ*4) % dist) / 4);
        for (int y = 15; y > height + 2; y--) {
            double noise = this.mainNoiseGenerator.noise(x, y, z);
            double heightOffset = y - height;
            buffer[y] = noise + heightOffset;
        }
    }

}