package world.bentobox.boxed.generators;

import java.io.File;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.noise.SimplexNoiseGenerator;

import nl.rutgerkok.worldgeneratorapi.BaseNoiseProvider;
import world.bentobox.boxed.Boxed;

/**
 * @author tastybento
 *
 */
public class OverWorldGenerator implements BaseNoiseProvider {

    private final SimplexNoiseGenerator mainNoiseGenerator;
    private final Boxed addon;
    private final YamlConfiguration config;


    public OverWorldGenerator(Boxed addon, long seed) {
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
    public TerrainConfig getTerrainSettings() {
        TerrainConfig ts = new TerrainConfig();
        ts.stoneBlock = Material.STONE.createBlockData();
        ts.waterBlock = Material.WATER.createBlockData();
        return ts;
    }

    @Override
    public void getNoise(WorldInfo worldInfo, double[] buffer, int scaledX, int scaledZ) {
        BiomeProvider bp = addon.getPlugin().getDefaultBiomeProvider(worldInfo.getName(), "");
        // Repeat on an island boundary
        int dist = addon.getSettings().getIslandDistance();
        int blockX = scaledX * 4;
        int blockZ = scaledZ * 4;
        Biome biome = bp.getBiome(worldInfo, blockX, 63, blockZ);
        double noiseScaleHorizontal = config.getDouble("biomes." + biome.name() + ".scale", 10D);
        double height = config.getDouble("biomes." + biome.name() + ".height", 8D);
        double x = ((((double)blockX) % dist) / 4) / noiseScaleHorizontal;
        double z = ((((double)blockZ) % dist) / 4) / noiseScaleHorizontal;

        for (int y = 0; y < buffer.length; y++) {
            double noise = this.mainNoiseGenerator.noise(x, y, z);
            double heightOffset = height - y;
            buffer[y] = noise + heightOffset;
        }

    }

}