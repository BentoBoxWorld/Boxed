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

    public BoxedChunkGenerator(Boxed addon) {
        this.addon = addon;
        wordRef = WorldRef.ofName(addon.getSettings().getWorldName());
    }

    public ChunkGenerator getGenerator() {
        return WorldGeneratorApi
                .getInstance(addon.getPlugin(), 0, 5)
                .createCustomGenerator(wordRef, generator -> {
                    // Set the noise generator
                    generator.setBaseNoiseGenerator(new BasicWorldGenerator(addon, addon.getSettings().getSeed()));
                    if (addon.getSettings().isAllowStructures()) {
                        generator.getWorldDecorator().withoutDefaultDecorations(DecorationType.SURFACE_STRUCTURES);
                    }
                    if (addon.getSettings().isAllowStrongholds()) {
                        generator.getWorldDecorator().withoutDefaultDecorations(DecorationType.STRONGHOLDS);
                    }
                    generator.setBiomeGenerator(new BoxedBiomeGenerator(addon));
                });
    }

}
