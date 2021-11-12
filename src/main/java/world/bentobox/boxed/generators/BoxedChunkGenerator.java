package world.bentobox.boxed.generators;

import java.util.Random;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import world.bentobox.boxed.Boxed;

/**
 * @author tastybento
 *
 */
public class BoxedChunkGenerator extends ChunkGenerator {

    //private final WorldRef wordRef;
    private final Boxed addon;
    //private final WorldRef wordRefNether;

    public BoxedChunkGenerator(Boxed addon) {
        this.addon = addon;
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
        return true;
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

    /*
    @SuppressWarnings({ "removal", "deprecation" })
    public ChunkGenerator getGenerator() {
        return WorldGeneratorApi
                .getInstance(addon.getPlugin(), 0, 5)
                .createCustomGenerator(wordRef, generator -> {
                    // Set the noise generator
                    generator.setBaseNoiseGenerator(new OverWorldGenerator(addon, addon.getSettings().getSeed()));
                    if (!addon.getSettings().isAllowStructures()) {
                        generator.getWorldDecorator().withoutDefaultDecorations(DecorationType.SURFACE_STRUCTURES);
                    }
                    if (!addon.getSettings().isAllowStrongholds()) {
                        generator.getWorldDecorator().withoutDefaultDecorations(DecorationType.STRONGHOLDS);
                    }
                    generator.setBiomeGenerator(new BoxedBiomeGenerator(addon));
                });
    }

    @SuppressWarnings({ "removal", "deprecation" })
    public ChunkGenerator getNetherGenerator() {
        return WorldGeneratorApi
                .getInstance(addon.getPlugin(), 0, 5)
                .createCustomGenerator(wordRefNether, generator -> {
                    // Set the noise generator
                    generator.setBaseNoiseGenerator(new NetherGenerator(addon, addon.getSettings().getSeed()));
                    if (!addon.getSettings().isAllowStructures()) {
                        generator.getWorldDecorator().withoutDefaultDecorations(DecorationType.SURFACE_STRUCTURES);
                    }
                    if (!addon.getSettings().isAllowStrongholds()) {
                        generator.getWorldDecorator().withoutDefaultDecorations(DecorationType.STRONGHOLDS);
                    }
                    generator.setBiomeGenerator(new NetherBiomeGenerator(addon));
                });
    }
     */
}
