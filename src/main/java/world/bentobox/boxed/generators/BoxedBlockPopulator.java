package world.bentobox.boxed.generators;

import java.util.Map;
import java.util.Objects;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Entity;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.util.Vector;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.blueprints.dataobjects.BlueprintBlock;
import world.bentobox.bentobox.blueprints.dataobjects.BlueprintCreatureSpawner;
import world.bentobox.bentobox.util.Pair;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.generators.BoxedChunkGenerator.ChestData;
import world.bentobox.boxed.generators.BoxedChunkGenerator.ChunkStore;

/**
 * @author tastybento
 *
 */
public class BoxedBlockPopulator extends BlockPopulator {

    private Boxed addon;
    private int size;

    /**
     * @param addon
     */
    public BoxedBlockPopulator(Boxed addon) {
        this.addon = addon;
        this.size = (int)(addon.getSettings().getIslandDistance() / 16D); // Size is chunks

    }

    @Override
    public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion limitedRegion) {
        Map<Pair<Integer, Integer>, ChunkStore> chunks = addon.getChunkGenerator(worldInfo.getEnvironment()).getChunks();
        // TODO: Make this work for the Nether!
        //// BentoBox.getInstance().logDebug("Populate " + chunkX + " " + chunkZ);
        World world = Bukkit.getWorld(worldInfo.getUID());
        int height = worldInfo.getMaxHeight();
        int minY = worldInfo.getMinHeight();
        int xx = BoxedChunkGenerator.repeatCalc(chunkX, size);
        int zz = BoxedChunkGenerator.repeatCalc(chunkZ, size);
        Pair<Integer, Integer> coords = new Pair<>(xx, zz);
        if (chunks.containsKey(coords)) {
            //// BentoBox.getInstance().logDebug("Populating ");
            ChunkStore data = chunks.get(coords);
            // Paste entities
            data.bpEnts().forEach(e -> {

                Location l = getLoc(world, e.relativeLoc().clone(), chunkX, chunkZ);
                if (limitedRegion.isInRegion(l)) {
                    Entity ent = limitedRegion.spawnEntity(l, e.entity().getType());
                    e.entity().configureEntity(ent);
                }
            });
            //// BentoBox.getInstance().logDebug("Tile Entities ");
            // Fill chests
            limitedRegion.getTileEntities().forEach(te -> {
                // BentoBox.getInstance().logDebug("Tile entity = " + te.getType() + " at " + te.getLocation());
                for (ChestData cd : data.chests()) {
                    // TODO: HANG HERE
                    Location chestLoc = getLoc(world, cd.relativeLoc().clone(), chunkX, chunkZ);
                    if (limitedRegion.isInRegion(chestLoc) && te.getLocation().equals(chestLoc)) {
                        // BentoBox.getInstance().logDebug("Expected location " + chestLoc);
                        this.setBlockState(te, cd.chest());
                    }
                }
            });
            //// BentoBox.getInstance().logDebug("Done");
        }
    }

    private Location getLoc(World w, Vector v, int chunkX, int chunkZ) {
        v.add(new Vector(chunkX << 4, 0, chunkZ << 4));
        return v.toLocation(w);
    }

    /**
     * Handles signs, chests and mob spawner blocks
     *
     * @param block   - block
     * @param bpBlock - config
     */
    public void setBlockState(BlockState bs, BlueprintBlock bpBlock) {
        // BentoBox.getInstance().logDebug(bpBlock.getBlockData());
        // Chests, in general
        if (bs instanceof InventoryHolder holder) {
            // BentoBox.getInstance().logDebug("Type: " + bs.getType());
            Inventory ih = holder.getInventory();
            // BentoBox.getInstance().logDebug("holder size = " + ih.getSize());
            // BentoBox.getInstance().logDebug("stored inventory size = " + bpBlock.getInventory().size());
            // This approach is required to avoid an array out of bounds error that shouldn't occur IMO
            for (int i = 0; i < ih.getSize(); i++) {
                ih.setItem(i, bpBlock.getInventory().get(i));
            }
            //bpBlock.getInventory().forEach(ih::setItem);
        }
        // Mob spawners
        else if (bs instanceof CreatureSpawner spawner) {
            // BentoBox.getInstance().logDebug("Spawner");
            setSpawner(spawner, bpBlock.getCreatureSpawner());
        }
        // Banners
        else if (bs instanceof Banner banner && bpBlock.getBannerPatterns() != null) {
            // BentoBox.getInstance().logDebug("Banner");
            bpBlock.getBannerPatterns().removeIf(Objects::isNull);
            banner.setPatterns(bpBlock.getBannerPatterns());
            banner.update(true, false);
        }
        // BentoBox.getInstance().logDebug("Block state complete");
    }

    /**
     * Set the spawner setting from the blueprint
     *
     * @param spawner - spawner
     * @param s       - blueprint spawner
     */
    public void setSpawner(CreatureSpawner spawner, BlueprintCreatureSpawner s) {
        // BentoBox.getInstance().logDebug("Setting spawner");
        spawner.setSpawnedType(s.getSpawnedType());
        spawner.setMaxNearbyEntities(s.getMaxNearbyEntities());
        spawner.setMaxSpawnDelay(s.getMaxSpawnDelay());
        spawner.setMinSpawnDelay(s.getMinSpawnDelay());
        spawner.setDelay(s.getDelay());
        spawner.setRequiredPlayerRange(s.getRequiredPlayerRange());
        spawner.setSpawnRange(s.getSpawnRange());
        // BentoBox.getInstance().logDebug("Now updating...");
        spawner.update(true, false);
        // BentoBox.getInstance().logDebug("Spawner updated");
    }

}