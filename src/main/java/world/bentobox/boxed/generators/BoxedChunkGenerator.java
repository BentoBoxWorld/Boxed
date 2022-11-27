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
        chunks.putIfAbsent(new Pair<>(chunk.getX(), chunk.getZ()), chunk);
    }

    /**
     * @param x chunk x
     * @param z chunk z
     * @return chunk snapshot or null if there is none
     */
    public ChunkSnapshot getChunk(int x, int z) {
        int xx = Math.floorMod(x, size);
        int zz = Math.floorMod(z, size);
        return chunks.get(new Pair<>(xx, zz));
    }

    /**
     * @param chunk the chunk to set
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

        // Repeat islands
        int xx = Math.floorMod(chunkX, chunkX < 0 ? -size: size);
        int zz = Math.floorMod(chunkZ, chunkZ < 0 ? -size : size);

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
                        case WATER:
                        case LAVA:
                        case NETHERRACK:
                        case STONE:
                        case END_STONE:
                            cd.setBlock(x, y, z, m);
                            break;
                        default:
                            // Most other blocks
                            cd.setBlock(x, y, z, isGround(m) ? Material.STONE: Material.AIR);
                        }
                    }
                }
            }
        }
    }

    /**
     * @return the chunks
     */
    public Map<Pair<Integer, Integer>, ChunkSnapshot> getChunks() {
        return chunks;
    }

    private static boolean isInWater(Material m) {
        switch (m) {
        // Underwater plants
        case KELP:
        case KELP_PLANT:
        case SEAGRASS:
        case BUBBLE_COLUMN:
        case BUBBLE_CORAL:
        case BUBBLE_CORAL_BLOCK:
        case BUBBLE_CORAL_FAN:
        case BUBBLE_CORAL_WALL_FAN:
        case DEAD_BRAIN_CORAL:
        case DEAD_BRAIN_CORAL_BLOCK:
        case DEAD_BRAIN_CORAL_FAN:
        case DEAD_BRAIN_CORAL_WALL_FAN:
        case DEAD_BUBBLE_CORAL:
        case DEAD_BUBBLE_CORAL_BLOCK:
        case DEAD_BUBBLE_CORAL_FAN:
        case DEAD_BUBBLE_CORAL_WALL_FAN:
        case DEAD_BUSH:
        case DEAD_FIRE_CORAL:
        case DEAD_FIRE_CORAL_BLOCK:
        case DEAD_FIRE_CORAL_FAN:
        case DEAD_FIRE_CORAL_WALL_FAN:
        case DEAD_HORN_CORAL:
        case DEAD_HORN_CORAL_BLOCK:
        case DEAD_HORN_CORAL_FAN:
        case DEAD_HORN_CORAL_WALL_FAN:
        case DEAD_TUBE_CORAL:
        case DEAD_TUBE_CORAL_BLOCK:
        case DEAD_TUBE_CORAL_FAN:
        case DEAD_TUBE_CORAL_WALL_FAN:
        case FIRE_CORAL:
        case FIRE_CORAL_BLOCK:
        case FIRE_CORAL_FAN:
        case FIRE_CORAL_WALL_FAN:
        case HORN_CORAL:
        case HORN_CORAL_BLOCK:
        case HORN_CORAL_FAN:
        case HORN_CORAL_WALL_FAN:
        case TUBE_CORAL:
        case TUBE_CORAL_BLOCK:
        case TUBE_CORAL_FAN:
        case TUBE_CORAL_WALL_FAN:
        case TALL_SEAGRASS:
            return true;
        default:
            return false;
        }
    }


    private static boolean isGround(Material m) {
        if (m.isAir() || m.isBurnable() || !m.isSolid()) return false;
        switch (m) {
        case ANDESITE:
        case BEDROCK:
        case CALCITE:
        case CLAY:
        case COAL_ORE:
        case COARSE_DIRT:
        case COBBLESTONE:
        case COPPER_ORE:
        case DEEPSLATE:
        case DEEPSLATE_COAL_ORE:
        case DEEPSLATE_COPPER_ORE:
        case DEEPSLATE_DIAMOND_ORE:
        case DEEPSLATE_EMERALD_ORE:
        case DEEPSLATE_GOLD_ORE:
        case DEEPSLATE_IRON_ORE:
        case DEEPSLATE_LAPIS_ORE:
        case DEEPSLATE_REDSTONE_ORE:
        case DIAMOND_ORE:
        case DIORITE:
        case DIRT:
        case DIRT_PATH:
        case DRIPSTONE_BLOCK:
        case EMERALD_ORE:
        case END_STONE:
        case FARMLAND:
        case GLOWSTONE:
        case GOLD_ORE:
        case GRANITE:
        case GRASS_BLOCK:
        case IRON_ORE:
        case MAGMA_BLOCK:
        case MYCELIUM:
        case NETHERITE_BLOCK:
        case NETHERRACK:
        case RED_SAND:
        case RED_SANDSTONE:
        case ROOTED_DIRT:
        case SAND:
        case SANDSTONE:
        case SOUL_SAND:
        case SOUL_SOIL:
        case STONE:
        case TERRACOTTA:
        case AMETHYST_BLOCK:
        case AMETHYST_CLUSTER:
        case AMETHYST_SHARD:
        case BASALT:
        case BLACKSTONE:
        case BLACK_CONCRETE:
        case BLACK_GLAZED_TERRACOTTA:
        case BLACK_TERRACOTTA:
        case BLUE_CONCRETE:
        case BLUE_GLAZED_TERRACOTTA:
        case BLUE_TERRACOTTA:
        case BONE_BLOCK:
        case BROWN_CONCRETE:
        case BROWN_GLAZED_TERRACOTTA:
        case BROWN_TERRACOTTA:
        case BUDDING_AMETHYST:
        case CHISELED_DEEPSLATE:
        case CHISELED_NETHER_BRICKS:
        case CHISELED_POLISHED_BLACKSTONE:
        case CHISELED_QUARTZ_BLOCK:
        case CHISELED_RED_SANDSTONE:
        case CHISELED_SANDSTONE:
        case CHISELED_STONE_BRICKS:
        case COAL_BLOCK:
        case COBBLED_DEEPSLATE:
        case CRYING_OBSIDIAN:
        case CUT_RED_SANDSTONE:
        case CUT_RED_SANDSTONE_SLAB:
        case CUT_SANDSTONE:
        case CUT_SANDSTONE_SLAB:
        case CYAN_CONCRETE:
        case CYAN_GLAZED_TERRACOTTA:
        case CYAN_TERRACOTTA:
        case DEEPSLATE_BRICKS:
        case DIAMOND_BLOCK:
        case ECHO_SHARD:
        case EMERALD_BLOCK:
        case GOLD_BLOCK:
        case GRAVEL:
        case GRAY_CONCRETE:
        case GRAY_GLAZED_TERRACOTTA:
        case GRAY_TERRACOTTA:
        case GREEN_CONCRETE:
        case GREEN_GLAZED_TERRACOTTA:
        case GREEN_TERRACOTTA:
        case INFESTED_CHISELED_STONE_BRICKS:
        case INFESTED_COBBLESTONE:
        case INFESTED_CRACKED_STONE_BRICKS:
        case INFESTED_DEEPSLATE:
        case INFESTED_MOSSY_STONE_BRICKS:
        case INFESTED_STONE:
        case INFESTED_STONE_BRICKS:
        case LAPIS_ORE:
        case LARGE_AMETHYST_BUD:
        case LIGHT_BLUE_CONCRETE:
        case LIGHT_BLUE_GLAZED_TERRACOTTA:
        case LIGHT_BLUE_TERRACOTTA:
        case LIGHT_GRAY_CONCRETE:
        case LIGHT_GRAY_GLAZED_TERRACOTTA:
        case LIGHT_GRAY_TERRACOTTA:
        case LIME_CONCRETE:
        case LIME_GLAZED_TERRACOTTA:
        case LIME_TERRACOTTA:
        case MAGENTA_CONCRETE:
        case MAGENTA_GLAZED_TERRACOTTA:
        case MAGENTA_TERRACOTTA:
        case MOSSY_COBBLESTONE:
        case MUD:
        case NETHERITE_SCRAP:
        case NETHER_GOLD_ORE:
        case NETHER_QUARTZ_ORE:
        case OBSIDIAN:
        case ORANGE_CONCRETE:
        case ORANGE_GLAZED_TERRACOTTA:
        case ORANGE_TERRACOTTA:
        case PACKED_MUD:
        case PINK_CONCRETE:
        case PINK_GLAZED_TERRACOTTA:
        case PINK_TERRACOTTA:
        case PODZOL:
        case POLISHED_ANDESITE:
        case POLISHED_BASALT:
        case POLISHED_BLACKSTONE:
        case POLISHED_DEEPSLATE:
        case POLISHED_DIORITE:
        case POLISHED_GRANITE:
        case PURPLE_CONCRETE:
        case PURPLE_GLAZED_TERRACOTTA:
        case PURPLE_TERRACOTTA:
        case PURPUR_BLOCK:
        case QUARTZ_BLOCK:
        case RAW_COPPER_BLOCK:
        case RAW_GOLD_BLOCK:
        case RAW_IRON_BLOCK:
        case REDSTONE_BLOCK:
        case REDSTONE_ORE:
        case RED_CONCRETE:
        case RED_GLAZED_TERRACOTTA:
        case RED_TERRACOTTA:
        case SMOOTH_BASALT:
        case SMOOTH_QUARTZ:
        case SMOOTH_RED_SANDSTONE:
        case SMOOTH_SANDSTONE:
        case SMOOTH_STONE:
        case TUFF:
        case WARPED_HYPHAE:
        case WARPED_NYLIUM:
        case WHITE_CONCRETE:
        case WHITE_GLAZED_TERRACOTTA:
        case WHITE_TERRACOTTA:
        case YELLOW_CONCRETE:
        case YELLOW_GLAZED_TERRACOTTA:
        case YELLOW_TERRACOTTA:
            return true;
        default:
            return false;



        }
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

    public ChunkSnapshot getChunkFromXZ(int x, int z) {
        int chunkX = (int)((double)x/16);
        int chunkZ = (int)((double)z/16);
        return this.getChunk(chunkX, chunkZ);

    }




}
