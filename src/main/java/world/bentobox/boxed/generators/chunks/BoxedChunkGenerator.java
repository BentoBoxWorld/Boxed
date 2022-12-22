package world.bentobox.boxed.generators.chunks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
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
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Colorable;
import org.bukkit.util.Vector;

import world.bentobox.bentobox.BentoBox;
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
public class BoxedChunkGenerator extends AbstractBoxedChunkGenerator {

    public BoxedChunkGenerator(Boxed addon) {
        super(addon);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return addon.getBoxedBiomeProvider();
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        world.getPopulators().add(addon.getBoxedBlockPopulator());
        return world.getPopulators();
    }

    @Override
    protected List<EntityData> getEnts(Chunk chunk) {
        return this.setEntities(Arrays.stream(chunk.getEntities())
                .filter(Objects::nonNull)
                .filter(e -> !(e instanceof Player))
                .filter(e -> e instanceof LivingEntity)
                .map(LivingEntity.class::cast)
                .toList());
    }

    @Override
    protected List<ChestData> getChests(Chunk chunk) {
        return Arrays.stream(chunk.getTileEntities()).map(t -> new ChestData(getLocInChunk(t.getLocation()), this.getBluePrintBlock(t.getBlock()))).toList();
    }


    private List<EntityData> setEntities(Collection<LivingEntity> entities) {
        List<EntityData> bpEnts = new ArrayList<>();
        for (LivingEntity entity: entities) {
            BlueprintEntity bpe = new BlueprintEntity();
            bpe.setType(entity.getType());
            bpe.setCustomName(entity.getCustomName());
            if (entity instanceof Villager villager) {
                setVillager(villager, bpe);
            }
            if (entity instanceof Colorable c) {
                if (c.getColor() != null) {
                    bpe.setColor(c.getColor());
                }
            }
            if (entity instanceof Tameable) {
                bpe.setTamed(((Tameable)entity).isTamed());
            }
            if (entity instanceof ChestedHorse) {
                bpe.setChest(((ChestedHorse)entity).isCarryingChest());
            }
            // Only set if child. Most animals are adults
            if (entity instanceof Ageable && !((Ageable)entity).isAdult()) {
                bpe.setAdult(false);
            }
            if (entity instanceof AbstractHorse horse) {
                bpe.setDomestication(horse.getDomestication());
                bpe.setInventory(new HashMap<>());
                for (int i = 0; i < horse.getInventory().getSize(); i++) {
                    ItemStack item = horse.getInventory().getItem(i);
                    if (item != null) {
                        bpe.getInventory().put(i, item);
                    }
                }
            }

            if (entity instanceof Horse horse) {
                bpe.setStyle(horse.getStyle());
            }
            bpEnts.add(new EntityData(getLocInChunk(entity.getLocation()), bpe));
        }
        return bpEnts;
    }

    private Vector getLocInChunk(Location l) {
        return new Vector(l.getBlockX() % 16, l.getBlockY(), l.getBlockZ() % 16);

    }

    /**
     * Set the villager stats
     * @param v - villager
     * @param bpe - Blueprint Entity
     */
    private void setVillager(Villager v, BlueprintEntity bpe) {
        bpe.setExperience(v.getVillagerExperience());
        bpe.setLevel(v.getVillagerLevel());
        bpe.setProfession(v.getProfession());
        bpe.setVillagerType(v.getVillagerType());
    }

    /**
     * Converts the block into a BluePrintBlock that can be pasted later
     * @param block - block to convert
     * @return Blueprint block
     */
    private BlueprintBlock getBluePrintBlock(Block block) {
        // Block state
        BlockState blockState = block.getState();
        BlueprintBlock b = new BlueprintBlock(block.getBlockData().getAsString());

        // Signs
        if (blockState instanceof Sign sign) {
            b.setSignLines(Arrays.asList(sign.getLines()));
            b.setGlowingText(sign.isGlowingText());
        }

        // Chests
        if (blockState instanceof InventoryHolder ih) {
            b.setInventory(new HashMap<>());
            for (int i = 0; i < ih.getInventory().getSize(); i++) {
                ItemStack item = ih.getInventory().getItem(i);
                if (item != null) {
                    b.getInventory().put(i, item);
                }
            }
        }
        // Spawner type
        if (blockState instanceof CreatureSpawner spawner) {
            b.setCreatureSpawner(getSpawner(spawner));
        }

        // Banners
        if (blockState instanceof Banner banner) {
            b.setBannerPatterns(banner.getPatterns());
        }

        return b;
    }

    private BlueprintCreatureSpawner getSpawner(CreatureSpawner spawner) {
        BlueprintCreatureSpawner cs = new BlueprintCreatureSpawner();
        cs.setSpawnedType(spawner.getSpawnedType());
        cs.setDelay(spawner.getDelay());
        cs.setMaxNearbyEntities(spawner.getMaxNearbyEntities());
        cs.setMaxSpawnDelay(spawner.getMaxSpawnDelay());
        cs.setMinSpawnDelay(spawner.getMinSpawnDelay());
        cs.setRequiredPlayerRange(spawner.getRequiredPlayerRange());
        cs.setSpawnRange(spawner.getSpawnRange());
        return cs;
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random r, int chunkX, int chunkZ, ChunkData cd) {
        int height = worldInfo.getMaxHeight();
        int minY = worldInfo.getMinHeight();
        int xx = repeatCalc(chunkX);
        int zz = repeatCalc(chunkZ);
        ChunkSnapshot chunk = this.getChunk(xx,zz);
        if (chunk == null) {
            // This should never be needed because islands should abut each other
            //cd.setRegion(0, minY, 0, 16, 0, 16, Material.WATER);
            BentoBox.getInstance().logDebug("No chunks found for " + xx + " " + zz);
            return;
        }
        // Copy the chunk
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

}
