package world.bentobox.boxed.generators;

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


    public BasicWorldGenerator(Boxed addon, WorldRef world) {
        this.addon = addon;
        // Initialize the noise generator based on the world seed
        this.mainNoiseGenerator = new SimplexNoiseGenerator(123456789L);
    }


    @Override
    public void getNoise(BiomeGenerator biomeGenerator, double[] buffer, int scaledX, int scaledZ) {
        //addon.getPlugin().logDebug("1 Scaled x = " + scaledX + " scaled z = " + scaledZ);
        // Repeat on an island boundary
        int dist = addon.getSettings().getIslandDistance();
        scaledX = ((scaledX*4) % dist) / 4;
        scaledZ = ((scaledZ*4) % dist) / 4;

        float noiseScaleHorizontal = addon.getSettings().getNoiseScaleHorizontal();
        for (int y = 0; y < buffer.length; y++) {
            double noise = this.mainNoiseGenerator.noise(scaledX / noiseScaleHorizontal, y, scaledZ / noiseScaleHorizontal);
            int heightOffset = -y + 8;
            buffer[y] = noise + heightOffset;
        }
    }

}