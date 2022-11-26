package world.bentobox.boxed.generators;

import java.util.List;
import java.util.Random;

import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.noise.SimplexOctaveGenerator;
import org.eclipse.jdt.annotation.NonNull;

import com.google.common.collect.Lists;

public class SimpleBiomeProvider extends BiomeProvider {

    @NonNull
    @Override
    public Biome getBiome(@NonNull WorldInfo worldInfo, int x, int y, int z) {
        SimplexOctaveGenerator generator = new SimplexOctaveGenerator(new Random(worldInfo.getSeed()), 6);
        generator.setScale(0.01);


        if (generator.noise(x, z, 1, 1, true) < 0) {
            return Biome.OCEAN;
        } else {
            return Biome.DESERT;
        }
    }

    @NonNull
    @Override
    public List<Biome> getBiomes(@NonNull WorldInfo worldInfo) {
        return Lists.newArrayList(Biome.OCEAN, Biome.DESERT);
    }
}