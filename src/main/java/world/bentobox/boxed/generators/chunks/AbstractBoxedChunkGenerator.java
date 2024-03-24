package world.bentobox.boxed.generators.chunks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.util.Vector;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.bentobox.blueprints.dataobjects.BlueprintBlock;
import world.bentobox.bentobox.blueprints.dataobjects.BlueprintEntity;
import world.bentobox.bentobox.util.Pair;
import world.bentobox.boxed.Boxed;

/**
 * Chunk generator for all environments
 * @author tastybento
 *
 */
public abstract class AbstractBoxedChunkGenerator extends ChunkGenerator {

    protected final Boxed addon;
    protected static int size;
    protected final Map<Pair<Integer, Integer>, ChunkStore> chunks = new HashMap<>();
    public record ChunkStore(ChunkSnapshot snapshot, List<EntityData> bpEnts, List<ChestData> chests, Map<Vector, Biome> chunkBiomes) {}

    public record EntityData(Vector relativeLoc, BlueprintEntity entity) {}

    public record ChestData(Vector relativeLoc, BlueprintBlock chest) {}

    //private final WorldRef wordRefNether;

    public AbstractBoxedChunkGenerator(Boxed addon) {
        this.addon = addon;
        size = (int)(addon.getSettings().getIslandDistance() / 16D); // Size is chunks

    }

    /**
     * Save a chunk
     * @param z - chunk z coord
     * @param x - chunk x coord
     * @param chunk the chunk to set
     */
    public void setChunk(int x, int z, Chunk chunk) {
        Map<Vector, Biome> chunkBiomes = new HashMap<>();
        for (int xx = 0; xx < 16; xx+=4) {
            for (int zz = 0; zz < 16; zz+=4) {
                for (int yy = chunk.getWorld().getMinHeight(); yy < chunk.getWorld().getMaxHeight(); yy+=4) { // TODO: every 4th yy?
                    chunkBiomes.put(new Vector(xx, yy, zz), chunk.getBlock(xx, yy, zz).getBiome());
                }
            }
        }
        chunks.put(new Pair<>(x, z), new ChunkStore(chunk.getChunkSnapshot(), getEnts(chunk), getTileEnts(chunk), chunkBiomes));
    }

    protected abstract List<EntityData> getEnts(Chunk chunk);

    protected abstract List<ChestData> getTileEnts(Chunk chunk);

    /**
     * Get the chunk store for these chunk coords or null if there is none.
     * @param x chunk x
     * @param z chunk z
     * @return chunk store or null if there is none
     */
    @Nullable
    public ChunkStore getChunk(int x, int z) {
        return chunks.get(new Pair<>(x, z));
    }

    @Override
    public boolean canSpawn(World world, int x, int z)
    {
        return true;
    }


    /**
     * Calculates the repeating value for a given size
     * @param chunkCoord chunk coord
     * @return mapped chunk coord
     */
    public static int repeatCalc(int chunkCoord) {
        return Math.floorMod(chunkCoord + size, size*2) - size;
        /*
        int xx;
        if (chunkCoord > 0) {
            xx = Math.floorMod(chunkCoord + size, size*2) - size;
        } else {
            xx = Math.floorMod(chunkCoord - size, -size*2) + size;
        }
        return xx;*/
    }

    /**
     * @return the chunks
     */
    public Map<Pair<Integer, Integer>, ChunkStore> getChunks() {
        return chunks;
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {

        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
        //return this.addon.getSettings().isGenerateCaves();
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
        //return this.addon.getSettings().isGenerateDecorations();
    }

    @Override
    public boolean shouldGenerateMobs() {
        return true;
        //return this.addon.getSettings().isGenerateMobs();
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
        //return this.addon.getSettings().isAllowStructures();
    }

}
