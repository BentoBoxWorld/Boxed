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
public class BoxedChunkGenerator extends ChunkGenerator {

    private final Boxed addon;
    private final int size;
    private Map<Pair<Integer, Integer>, ChunkStore> chunks = new HashMap<>();
    public record ChunkStore(ChunkSnapshot snapshot, List<EntityData> bpEnts, List<ChestData> chests) {};
    public record EntityData(Vector relativeLoc, BlueprintEntity entity) {};
    public record ChestData(Vector relativeLoc, BlueprintBlock chest) {};

    //private final WorldRef wordRefNether;

    public BoxedChunkGenerator(Boxed addon) {
        this.addon = addon;
        this.size = (int)(addon.getSettings().getIslandDistance() / 16D); // Size is chunks
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

    /**
     * Save a chunk
     * @param z - chunk z coord
     * @param x - chunk x coord
     * @param chunk the chunk to set
     */
    public void setChunk(int x, int z, Chunk chunk) {
        List<LivingEntity> ents = Arrays.stream(chunk.getEntities())
                .filter(Objects::nonNull)
                .filter(e -> !(e instanceof Player))
                .filter(e -> e instanceof LivingEntity)
                .map(LivingEntity.class::cast)
                .toList();
        // Grab entities
        List<EntityData> bpEnts = this.setEntities(ents);
        // Grab tile entities
        List<ChestData> chests = Arrays.stream(chunk.getTileEntities()).map(t -> new ChestData(getLocInChunk(t.getLocation()), this.getBluePrintBlock(t.getBlock()))).toList();
        chunks.put(new Pair<>(x, z), new ChunkStore(chunk.getChunkSnapshot(false, true, false), bpEnts, chests));
    }

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
        int xx = repeatCalc(chunkX, size);
        int zz = repeatCalc(chunkZ, size);
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

    /*
    private void copyChunk(ChunkData cd, ChunkSnapshot chunk, int minY, int height) {
        for (int x = 0; x < 16; x ++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < height; y++) {
                    Material m = chunk.getBlockType(x, y, z);
                    // Handle blocks that occur naturally in water
                    if (isInWater(m)) {
                        cd.setBlock(x, y, z, Material.WATER);
                    } else {
                        // Handle liquids and default blocks
                        switch (m) {
                        case WATER, LAVA, NETHERRACK, STONE, END_STONE -> cd.setBlock(x, y, z, m);
                        default ->
                        // Most other blocks
                        cd.setBlock(x, y, z, isGround(m) ? Material.STONE : Material.AIR);
                        }
                    }
                }
            }
        }
    }
     */
    /**
     * Calculates the repeating value for a given size
     * @param chunkCoord chunk coord
     * @param s size
     * @return mapped chunk coord
     */
    public static int repeatCalc(int chunkCoord, int s) {
        int xx;
        if (chunkCoord > 0) {
            xx = Math.floorMod(chunkCoord + s, s*2) - s;
        } else {
            xx = Math.floorMod(chunkCoord - s, -s*2) + s;
        }
        return xx;
    }

    /**
     * @return the chunks
     */
    public Map<Pair<Integer, Integer>, ChunkStore> getChunks() {
        return chunks;
    }
    /*
    private static boolean isInWater(Material m) {
        return switch (m) {
        // Underwater plants
        case KELP, KELP_PLANT, SEAGRASS, BUBBLE_COLUMN, BUBBLE_CORAL, BUBBLE_CORAL_BLOCK, BUBBLE_CORAL_FAN,
        BUBBLE_CORAL_WALL_FAN, DEAD_BRAIN_CORAL, DEAD_BRAIN_CORAL_BLOCK, DEAD_BRAIN_CORAL_FAN,
        DEAD_BRAIN_CORAL_WALL_FAN, DEAD_BUBBLE_CORAL, DEAD_BUBBLE_CORAL_BLOCK, DEAD_BUBBLE_CORAL_FAN,
        DEAD_BUBBLE_CORAL_WALL_FAN, DEAD_BUSH, DEAD_FIRE_CORAL, DEAD_FIRE_CORAL_BLOCK, DEAD_FIRE_CORAL_FAN,
        DEAD_FIRE_CORAL_WALL_FAN, DEAD_HORN_CORAL, DEAD_HORN_CORAL_BLOCK, DEAD_HORN_CORAL_FAN,
        DEAD_HORN_CORAL_WALL_FAN, DEAD_TUBE_CORAL, DEAD_TUBE_CORAL_BLOCK, DEAD_TUBE_CORAL_FAN,
        DEAD_TUBE_CORAL_WALL_FAN, FIRE_CORAL, FIRE_CORAL_BLOCK, FIRE_CORAL_FAN, FIRE_CORAL_WALL_FAN,
        HORN_CORAL, HORN_CORAL_BLOCK, HORN_CORAL_FAN, HORN_CORAL_WALL_FAN, TUBE_CORAL, TUBE_CORAL_BLOCK,
        TUBE_CORAL_FAN, TUBE_CORAL_WALL_FAN, TALL_SEAGRASS -> true;
        default -> false;
        };
    }


    private static boolean isGround(Material m) {
        if (m.isAir() || m.isBurnable() || !m.isSolid()) return false;
        return switch (m) {
        case ANDESITE, BEDROCK, CALCITE, CLAY, COAL_ORE, COARSE_DIRT, COBBLESTONE, COPPER_ORE, DEEPSLATE,
        DEEPSLATE_COAL_ORE, DEEPSLATE_COPPER_ORE, DEEPSLATE_DIAMOND_ORE, DEEPSLATE_EMERALD_ORE,
        DEEPSLATE_GOLD_ORE, DEEPSLATE_IRON_ORE, DEEPSLATE_LAPIS_ORE, DEEPSLATE_REDSTONE_ORE, DIAMOND_ORE,
        DIORITE, DIRT, DIRT_PATH, DRIPSTONE_BLOCK, EMERALD_ORE, END_STONE, FARMLAND, GLOWSTONE, GOLD_ORE,
        GRANITE, GRASS_BLOCK, IRON_ORE, MAGMA_BLOCK, MYCELIUM, NETHERITE_BLOCK, NETHERRACK, RED_SAND,
        RED_SANDSTONE, ROOTED_DIRT, SAND, SANDSTONE, SOUL_SAND, SOUL_SOIL, STONE, TERRACOTTA, AMETHYST_BLOCK,
        AMETHYST_CLUSTER, AMETHYST_SHARD, BASALT, BLACKSTONE, BLACK_CONCRETE, BLACK_GLAZED_TERRACOTTA,
        BLACK_TERRACOTTA, BLUE_CONCRETE, BLUE_GLAZED_TERRACOTTA, BLUE_TERRACOTTA, BONE_BLOCK, BROWN_CONCRETE,
        BROWN_GLAZED_TERRACOTTA, BROWN_TERRACOTTA, BUDDING_AMETHYST, CHISELED_DEEPSLATE,
        CHISELED_NETHER_BRICKS, CHISELED_POLISHED_BLACKSTONE, CHISELED_QUARTZ_BLOCK, CHISELED_RED_SANDSTONE,
        CHISELED_SANDSTONE, CHISELED_STONE_BRICKS, COAL_BLOCK, COBBLED_DEEPSLATE, CRYING_OBSIDIAN,
        CUT_RED_SANDSTONE, CUT_RED_SANDSTONE_SLAB, CUT_SANDSTONE, CUT_SANDSTONE_SLAB, CYAN_CONCRETE,
        CYAN_GLAZED_TERRACOTTA, CYAN_TERRACOTTA, DEEPSLATE_BRICKS, DIAMOND_BLOCK, ECHO_SHARD, EMERALD_BLOCK,
        GOLD_BLOCK, GRAVEL, GRAY_CONCRETE, GRAY_GLAZED_TERRACOTTA, GRAY_TERRACOTTA, GREEN_CONCRETE,
        GREEN_GLAZED_TERRACOTTA, GREEN_TERRACOTTA, INFESTED_CHISELED_STONE_BRICKS, INFESTED_COBBLESTONE,
        INFESTED_CRACKED_STONE_BRICKS, INFESTED_DEEPSLATE, INFESTED_MOSSY_STONE_BRICKS, INFESTED_STONE,
        INFESTED_STONE_BRICKS, LAPIS_ORE, LARGE_AMETHYST_BUD, LIGHT_BLUE_CONCRETE,
        LIGHT_BLUE_GLAZED_TERRACOTTA, LIGHT_BLUE_TERRACOTTA, LIGHT_GRAY_CONCRETE,
        LIGHT_GRAY_GLAZED_TERRACOTTA, LIGHT_GRAY_TERRACOTTA, LIME_CONCRETE, LIME_GLAZED_TERRACOTTA,
        LIME_TERRACOTTA, MAGENTA_CONCRETE, MAGENTA_GLAZED_TERRACOTTA, MAGENTA_TERRACOTTA, MOSSY_COBBLESTONE,
        MUD, NETHERITE_SCRAP, NETHER_GOLD_ORE, NETHER_QUARTZ_ORE, OBSIDIAN, ORANGE_CONCRETE,
        ORANGE_GLAZED_TERRACOTTA, ORANGE_TERRACOTTA, PACKED_MUD, PINK_CONCRETE, PINK_GLAZED_TERRACOTTA,
        PINK_TERRACOTTA, PODZOL, POLISHED_ANDESITE, POLISHED_BASALT, POLISHED_BLACKSTONE,
        POLISHED_DEEPSLATE, POLISHED_DIORITE, POLISHED_GRANITE, PURPLE_CONCRETE, PURPLE_GLAZED_TERRACOTTA,
        PURPLE_TERRACOTTA, PURPUR_BLOCK, QUARTZ_BLOCK, RAW_COPPER_BLOCK, RAW_GOLD_BLOCK, RAW_IRON_BLOCK,
        REDSTONE_BLOCK, REDSTONE_ORE, RED_CONCRETE, RED_GLAZED_TERRACOTTA, RED_TERRACOTTA, SMOOTH_BASALT,
        SMOOTH_QUARTZ, SMOOTH_RED_SANDSTONE, SMOOTH_SANDSTONE, SMOOTH_STONE, TUFF, WARPED_HYPHAE,
        WARPED_NYLIUM, WHITE_CONCRETE, WHITE_GLAZED_TERRACOTTA, WHITE_TERRACOTTA, YELLOW_CONCRETE,
        YELLOW_GLAZED_TERRACOTTA, YELLOW_TERRACOTTA -> true;
        default -> false;
        };
    }
     */
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
