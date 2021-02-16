package world.bentobox.boxed.generators;

import org.bukkit.block.Biome;
import org.bukkit.util.noise.SimplexNoiseGenerator;

import nl.rutgerkok.worldgeneratorapi.BaseNoiseGenerator;
import nl.rutgerkok.worldgeneratorapi.BiomeGenerator;
import nl.rutgerkok.worldgeneratorapi.WorldRef;
import world.bentobox.boxed.Boxed;

/**
 * @author tastybento
 *
 */
public class BasicWorldGenerator implements BaseNoiseGenerator {

    private final SimplexNoiseGenerator mainNoiseGenerator;
    private final Boxed addon;


    public BasicWorldGenerator(Boxed addon, WorldRef world, long seed) {
        this.addon = addon;
        // Initialize the noise generator based on the world seed
        this.mainNoiseGenerator = new SimplexNoiseGenerator(seed);
    }


    @Override
    public void getNoise(BiomeGenerator biomeGenerator, double[] buffer, int scaledX, int scaledZ) {
        //addon.getPlugin().logDebug("1 Scaled x = " + scaledX + " scaled z = " + scaledZ);
        // Repeat on an island boundary
        int dist = addon.getSettings().getIslandDistance();
        double height = 8;
        scaledX = ((scaledX*4) % dist) / 4;
        scaledZ = ((scaledZ*4) % dist) / 4;
        Biome biome = biomeGenerator.getZoomedOutBiome(scaledX, scaledZ);
        double noiseScaleHorizontal = addon.getSettings().getNoiseScaleHorizontal();
        if (biome.equals(Biome.SNOWY_TAIGA)) {
            noiseScaleHorizontal = noiseScaleHorizontal / 2;
        } else if (biome.equals(Biome.MOUNTAINS)) {
            height = 10;
            noiseScaleHorizontal = noiseScaleHorizontal / 4;
        } else if (biome.equals(Biome.DESERT)) {
            height = 9;
            noiseScaleHorizontal = noiseScaleHorizontal * 1.5F;
        } else if (biome.equals(Biome.BADLANDS)) {
            height = 8.5;
            noiseScaleHorizontal = noiseScaleHorizontal * 1.5F;
        }
        for (int y = 0; y < buffer.length; y++) {
            double noise = this.mainNoiseGenerator.noise(scaledX / noiseScaleHorizontal, y, scaledZ / noiseScaleHorizontal);
            double heightOffset = height - y;
            buffer[y] = noise + heightOffset;
        }
    }

}