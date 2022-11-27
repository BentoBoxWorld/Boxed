package world.bentobox.boxed.generators;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import world.bentobox.bentobox.util.Pair;
import world.bentobox.boxed.Boxed;

/**
 * @author tastybento
 *
 */
public class BoxedChunkGenerator extends ChunkGenerator {

    private final Boxed addon;
    private final int size;
    private Map<Pair<Integer, Integer>, ChunkSnapshot> chunks = new HashMap<>();

    //private final WorldRef wordRefNether;

    public BoxedChunkGenerator(Boxed addon) {
        this.addon = addon;
        this.size = (int)(addon.getSettings().getIslandDistance() / 16D); // Size is chunks
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return addon.getBoxedBiomeProvider();
    }

    /**
     * @param chunk the chunk to set
     */
    public void setChunk(ChunkSnapshot chunk) {
        // Make the coords always positive
        chunks.putIfAbsent(new Pair<>(chunk.getX(), chunk.getZ()), chunk);
    }

    /**
     * @param x chunk x
     * @param z chunk z
     * @return chunk snapshot or null if there is none
     */
    public ChunkSnapshot getChunk(int x, int z) {
        return chunks.get(new Pair<>(x, z));
    }

    /**
     * @param chunks the chunks to set
     */
    public void setChunks(Map<Pair<Integer, Integer>, ChunkSnapshot> chunks) {
        this.chunks = chunks;
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
        ChunkSnapshot chunk = chunks.get(coords);
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
    public Map<Pair<Integer, Integer>, ChunkSnapshot> getChunks() {
        return chunks;
    }

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

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return this.addon.getSettings().isGenerateSurface();
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return this.addon.getSettings().isGenerateBedrock();
    }

    @Override
    public boolean shouldGenerateCaves() {
        return this.addon.getSettings().isGenerateCaves();
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return this.addon.getSettings().isGenerateDecorations();
    }

    @Override
    public boolean shouldGenerateMobs() {
        return this.addon.getSettings().isGenerateMobs();
    }

    @Override
    public boolean shouldGenerateStructures() {
        return this.addon.getSettings().isAllowStructures();
    }

}
