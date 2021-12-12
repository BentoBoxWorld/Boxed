package world.bentobox.boxed.generators;

import java.util.Random;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.noise.SimplexNoiseGenerator;

import world.bentobox.boxed.Boxed;

/**
 * @author tastybento
 *
 */
public class BoxedChunkGenerator extends ChunkGenerator {

    //private final WorldRef wordRef;
    private final Boxed addon;
    private FileConfiguration config;
    private SimplexNoiseGenerator generator;
    private final int dist;

    //private final WorldRef wordRefNether;

    public BoxedChunkGenerator(Boxed addon) {
        this.addon = addon;
        this.config = addon.getConfig();
        this.generator = new SimplexNoiseGenerator(new Random(addon.getSettings().getSeed()));
        this.dist = addon.getSettings().getIslandDistance();

        //wordRef = WorldRef.ofName(addon.getSettings().getWorldName());
        //wordRefNether = WorldRef.ofName(addon.getSettings().getWorldName() + "_nether");
    }

    @Override
    public boolean canSpawn(World world, int x, int z)
    {
        return true;
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return true;
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return true;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return true;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return true;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return true;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return true;
    }


    @Override
    public void generateNoise(WorldInfo worldInfo, Random r, int chunkX, int chunkZ, ChunkData cd) {
        // Biome scale
        Biome biome = Biome.PLAINS;
        double noiseScaleHorizontal = config.getDouble("biomes." + biome.name() + ".scale", 8D) / 1000D;
        //generator.setScale(noiseScaleHorizontal);
        // Biome height
        double biomeHeight = config.getDouble("biomes." + biome.name() + ".height", 8D);

        Material material;
        if (worldInfo.getEnvironment() == World.Environment.NORMAL) {
            material = Material.STONE;
        } else {
            material = Material.NETHERRACK;
        }

        int worldX = ((chunkX * 16) % dist) + dist;
        int worldZ = ((chunkZ * 16) % dist) + dist;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {

                double noise = generator.noise(worldX + x, worldZ + z) / noiseScaleHorizontal;
                int height = (int) (noise * 40);
                height += 50;
                height += biomeHeight;
                if (height > cd.getMaxHeight()) {
                    height = cd.getMaxHeight();
                }
                if (height < cd.getMinHeight()) {
                    height = cd.getMinHeight();
                }
                for (int y = 0; y < height; y++) {
                    cd.setBlock(x, y, z, material);
                }
            }
        }
    }

}
