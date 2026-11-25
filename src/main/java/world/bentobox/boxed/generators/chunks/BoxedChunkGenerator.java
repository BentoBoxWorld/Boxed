package world.bentobox.boxed.generators.chunks;

import java.util.List;
import java.util.Random;

import org.bukkit.World;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.WorldInfo;

import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.generators.template.TemplateChunk;

/**
 * Chunk generator for the game worlds. It does not generate land itself, it copies it from
 * the area's template.
 * @author tastybento
 *
 */
public class BoxedChunkGenerator extends AbstractBoxedChunkGenerator {

    private final BlockPopulator boxedBlockPopulator;

    public BoxedChunkGenerator(Boxed addon) {
        super(addon);
        boxedBlockPopulator = new BoxedBlockPopulator(addon);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return addon.getBoxedBiomeProvider();
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        // Only add it once
        if (!world.getPopulators().contains(boxedBlockPopulator)) {
            world.getPopulators().add(boxedBlockPopulator);
        }
        return world.getPopulators();
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random r, int chunkX, int chunkZ, ChunkData cd) {
        TemplateChunk chunk = addon.getTemplateChunk(worldInfo.getEnvironment(), chunkX, chunkZ);
        if (chunk != null) {
            chunk.copyTo(cd);
        }
    }

}
