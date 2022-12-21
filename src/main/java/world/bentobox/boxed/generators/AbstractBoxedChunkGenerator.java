package world.bentobox.boxed.generators;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Sign;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Attachable;
import org.bukkit.material.Colorable;
import org.bukkit.util.Vector;

import world.bentobox.bentobox.blueprints.dataobjects.BlueprintBlock;
import world.bentobox.bentobox.blueprints.dataobjects.BlueprintCreatureSpawner;
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
    protected Map<Pair<Integer, Integer>, ChunkStore> chunks = new HashMap<>();
    public record ChunkStore(ChunkSnapshot snapshot, List<EntityData> bpEnts, List<ChestData> chests) {};
    public record EntityData(Vector relativeLoc, BlueprintEntity entity) {};
    public record ChestData(Vector relativeLoc, BlueprintBlock chest) {};

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
        chunks.put(new Pair<>(x, z), new ChunkStore(chunk.getChunkSnapshot(false, true, false), getEnts(chunk), getChests(chunk)));
    }

    protected abstract List<EntityData> getEnts(Chunk chunk);

    protected abstract List<ChestData> getChests(Chunk chunk);

    /**
     * @param x chunk x
     * @param z chunk z
     * @return chunk snapshot or null if there is none
     */
    public ChunkSnapshot getChunk(int x, int z) {
        return chunks.get(new Pair<>(x, z)).snapshot;
    }

    @Override
    public boolean canSpawn(World world, int x, int z)
    {
        return true;
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random r, int chunkX, int chunkZ, ChunkData cd) {

        int height = worldInfo.getMaxHeight();
        int minY = worldInfo.getMinHeight();
        int xx = repeatCalc(chunkX);
        int zz = repeatCalc(chunkZ);
        Pair<Integer, Integer> coords = new Pair<>(xx, zz);
        if (!chunks.containsKey(coords)) {
            // This should never be needed because islands should abut each other
            cd.setRegion(0, minY, 0, 16, 0, 16, Material.WATER);
            return;
        }
        // Copy the chunk
        ChunkSnapshot chunk = chunks.get(coords).snapshot;
        copyChunkVerbatim(cd, chunk, minY, height);

    }

    private void copyChunkVerbatim(ChunkData cd, ChunkSnapshot chunk, int minY, int height) {
        for (int x = 0; x < 16; x ++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < height; y++) {
                    cd.setBlock(x, y, z, chunk.getBlockData(x, y, z));
                }
            }
        }
    }

    /**
     * Calculates the repeating value for a given size
     * @param chunkCoord chunk coord
     * @return mapped chunk coord
     */
    public static int repeatCalc(int chunkCoord) {
        int xx;
        if (chunkCoord > 0) {
            xx = Math.floorMod(chunkCoord + size, size*2) - size;
        } else {
            xx = Math.floorMod(chunkCoord - size, -size*2) + size;
        }
        return xx;
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
        return this.addon.getSettings().isGenerateMobs();
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
        //return this.addon.getSettings().isAllowStructures();
    }

}
