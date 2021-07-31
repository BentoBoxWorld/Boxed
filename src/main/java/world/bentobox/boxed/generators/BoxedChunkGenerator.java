package world.bentobox.boxed.generators;

import org.bukkit.generator.ChunkGenerator;

import nl.rutgerkok.worldgeneratorapi.WorldGeneratorApi;
import nl.rutgerkok.worldgeneratorapi.WorldRef;
import nl.rutgerkok.worldgeneratorapi.decoration.DecorationType;
import world.bentobox.boxed.Boxed;

/**
 * @author tastybento
 *
 */
public class BoxedChunkGenerator {

    private final WorldRef wordRef;
    private final Boxed addon;
    private final WorldRef wordRefNether;

    public BoxedChunkGenerator(Boxed addon) {
        this.addon = addon;
        wordRef = WorldRef.ofName(addon.getSettings().getWorldName());
        wordRefNether = WorldRef.ofName(addon.getSettings().getWorldName() + "_nether");
    }

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

}
