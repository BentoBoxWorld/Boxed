package world.bentobox.boxed.generators.chunks;

import java.util.Map;
import java.util.Objects;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Entity;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.util.Vector;

import world.bentobox.bentobox.blueprints.dataobjects.BlueprintBlock;
import world.bentobox.bentobox.blueprints.dataobjects.BlueprintCreatureSpawner;
import world.bentobox.bentobox.util.Pair;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.generators.chunks.AbstractBoxedChunkGenerator.ChestData;
import world.bentobox.boxed.generators.chunks.AbstractBoxedChunkGenerator.ChunkStore;

/**
 * @author tastybento
 *
 */
public class BoxedBlockPopulator extends BlockPopulator {

    private final Boxed addon;

    /**
     * @param addon Boxed
     */
    public BoxedBlockPopulator(Boxed addon) {
        this.addon = addon;

    }

    @Override
    public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion limitedRegion) {
        Map<Pair<Integer, Integer>, ChunkStore> chunks = addon.getChunkGenerator(worldInfo.getEnvironment()).getChunks();
        World world = Bukkit.getWorld(worldInfo.getUID());
        int xx = BoxedChunkGenerator.repeatCalc(chunkX);
        int zz = BoxedChunkGenerator.repeatCalc(chunkZ);
        Pair<Integer, Integer> coords = new Pair<>(xx, zz);
        if (chunks.containsKey(coords)) {
            ChunkStore data = chunks.get(coords);
            // Paste entities
            data.bpEnts().forEach(e -> {
                Location l = getLoc(world, e.relativeLoc().clone(), chunkX, chunkZ);
                if (limitedRegion.isInRegion(l)) {
                    Entity ent = limitedRegion.spawnEntity(l, e.entity().getType());
                    e.entity().configureEntity(ent);
                }
            });
            // Fill chests
            limitedRegion.getTileEntities().forEach(te -> {
                int teX = BoxedChunkGenerator.repeatCalc(te.getX() >> 4);
                int teZ = BoxedChunkGenerator.repeatCalc(te.getZ() >> 4);
                if (teX == xx && teZ == zz) { 
                    for (ChestData cd : data.chests()) {
                        Location chestLoc = getLoc(world, cd.relativeLoc().clone(), chunkX, chunkZ);
                        if (limitedRegion.isInRegion(chestLoc) && te.getLocation().equals(chestLoc)) {
                            this.setBlockState(te, cd.chest());
                        }
                    }
                }
            });
        }
    }

    private Location getLoc(World w, Vector v, int chunkX, int chunkZ) {
        v.add(new Vector(chunkX << 4, 0, chunkZ << 4));
        return v.toLocation(w);
    }

    /**
     * Handles signs, chests and mob spawner blocks
     *
     * @param bs   - block state
     * @param bpBlock - config
     */
    public void setBlockState(BlockState bs, BlueprintBlock bpBlock) {
        // Chests, in general
        if (bs instanceof InventoryHolder holder) {
            Inventory ih = holder.getInventory();
            // This approach is required to avoid an array out of bounds error that shouldn't occur IMO
            for (int i = 0; i < ih.getSize(); i++) {
                ih.setItem(i, bpBlock.getInventory().get(i));
            }
        }
        // Mob spawners
        else if (bs instanceof CreatureSpawner spawner) {
            setSpawner(spawner, bpBlock.getCreatureSpawner());
        }
        // Banners
        else if (bs instanceof Banner banner && bpBlock.getBannerPatterns() != null) {
            bpBlock.getBannerPatterns().removeIf(Objects::isNull);
            banner.setPatterns(bpBlock.getBannerPatterns());
            banner.update(true, false);
        }
    }

    /**
     * Set the spawner setting from the blueprint
     *
     * @param spawner - spawner
     * @param s       - blueprint spawner
     */
    public void setSpawner(CreatureSpawner spawner, BlueprintCreatureSpawner s) {
        spawner.setSpawnedType(s.getSpawnedType());
        spawner.setMaxNearbyEntities(s.getMaxNearbyEntities());
        int delay = Math.max(s.getMinSpawnDelay(), s.getMaxSpawnDelay());
        if (delay < 1) {
            delay = 120; // Set the default 2 minutes
        }
        spawner.setMaxSpawnDelay(delay);
        delay = Math.max(s.getMinSpawnDelay(), 1);
        spawner.setMinSpawnDelay(delay);
        spawner.setDelay(s.getDelay());
        int range = Math.max(s.getRequiredPlayerRange(), 0);
        spawner.setRequiredPlayerRange(range);
        spawner.setSpawnRange(s.getSpawnRange());
        spawner.update(true, false);
    }

}
