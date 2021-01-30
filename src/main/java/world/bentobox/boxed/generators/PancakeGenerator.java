package world.bentobox.boxed.generators;

import org.bukkit.Material;

import nl.rutgerkok.worldgeneratorapi.BaseTerrainGenerator;
import nl.rutgerkok.worldgeneratorapi.BiomeGenerator;
import world.bentobox.boxed.Boxed;

public class PancakeGenerator implements BaseTerrainGenerator {

    private Boxed addon;

    public PancakeGenerator(Boxed addon) {
        this.addon = addon;
    }

    @Override
    public int getHeight(BiomeGenerator biomeGenerator, int x, int z, HeightType type) {
        return addon.getSettings().getIslandHeight();
    }

    @Override
    public void setBlocksInChunk(GeneratingChunk chunk) {
        chunk.getBlocksForChunk().setRegion(0, 0, 0, CHUNK_SIZE, 63, CHUNK_SIZE, Material.STONE);
    }

}
