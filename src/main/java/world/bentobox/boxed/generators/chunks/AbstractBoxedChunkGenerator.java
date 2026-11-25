package world.bentobox.boxed.generators.chunks;


import java.util.Random;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.util.Vector;

import world.bentobox.bentobox.blueprints.dataobjects.BlueprintBlock;
import world.bentobox.bentobox.blueprints.dataobjects.BlueprintEntity;
import world.bentobox.boxed.Boxed;

/**
 * Chunk generator for all environments
 * @author tastybento
 *
 */
public abstract class AbstractBoxedChunkGenerator extends ChunkGenerator {

    protected final Boxed addon;
    /**
     * Half-width of the repeating seed region in chunks
     */
    protected final int size;
    public record EntityData(Vector relativeLoc, BlueprintEntity entity) {}

    public record ChestData(Vector relativeLoc, BlueprintBlock chest) {}

    protected AbstractBoxedChunkGenerator(Boxed addon) {
        this.addon = addon;
        size = (int)(addon.getSettings().getIslandDistance() / 16D); // Size is chunks
    }

    /**
     * Works out which row of the box grid a chunk is in. Row 0 is centred on z = 0.
     * @param chunkZ chunk z
     * @param size half-width of a box's area in chunks
     * @return grid row
     */
    public static int rowCalc(int chunkZ, int size) {
        return Math.floorDiv(chunkZ + size, size * 2);
    }

    /**
     * @return half-width of the repeating seed region in chunks
     */
    public int getSize() {
        return size;
    }

    /**
     * Gives the world a fixed spawn point. Without this the server searches for a safe spawn
     * when the world is created, loading chunks one at a time, which takes minutes in the Nether.
     */
    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0, 64, 0);
    }

    @Override
    public boolean canSpawn(World world, int x, int z)
    {
        return true;
    }


    /**
     * Maps a chunk coordinate back into this generator's repeating seed region
     * @param chunkCoord chunk coord
     * @return mapped chunk coord in the range [-size, size)
     */
    public int repeatCalc(int chunkCoord) {
        return repeatCalc(chunkCoord, size);
    }

    /**
     * Maps a chunk coordinate back into a repeating region of the given half-width
     * @param chunkCoord chunk coord
     * @param size half-width of the region in chunks
     * @return mapped chunk coord in the range [-size, size)
     */
    public static int repeatCalc(int chunkCoord, int size) {
        return Math.floorMod(chunkCoord + size, size*2) - size;
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
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return true;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return this.addon.getSettings().isAllowStructures();
    }

}
