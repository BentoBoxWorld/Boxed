package world.bentobox.boxed.generators.template;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.boxed.generators.chunks.AbstractBoxedChunkGenerator.ChestData;
import world.bentobox.boxed.generators.chunks.AbstractBoxedChunkGenerator.EntityData;

/**
 * One chunk of a template, ready to be copied into a world. Instances are immutable and can be
 * shared between threads.
 *
 * @author tastybento
 */
public class TemplateChunk {

    private final int minY;
    private final BlockData[] palette;
    private final short[][] sections;
    private final int[] uniform;
    private final List<EntityData> entities;
    private final List<ChestData> chests;

    /**
     * @param minY lowest block y in the chunk
     * @param palette block palette
     * @param sections palette indices per section, or null for a uniform section
     * @param uniform palette index of a uniform section
     * @param entities entities in the chunk
     * @param chests tile entities in the chunk
     */
    public TemplateChunk(int minY, BlockData[] palette, short[][] sections, int[] uniform,
            List<EntityData> entities, List<ChestData> chests) {
        this.minY = minY;
        this.palette = palette;
        this.sections = sections;
        this.uniform = uniform;
        this.entities = entities;
        this.chests = chests;
    }

    /**
     * @param x x in the chunk, 0 to 15
     * @param y world y
     * @param z z in the chunk, 0 to 15
     * @return the block, or null if y is outside the chunk
     */
    @Nullable
    public BlockData getBlockData(int x, int y, int z) {
        int relY = y - minY;
        int section = relY >> 4;
        if (relY < 0 || section >= sections.length) {
            return null;
        }
        short[] blocks = sections[section];
        return palette[blocks == null ? uniform[section] : blocks[RawChunk.blockIndex(x, relY & 15, z)]];
    }

    /**
     * @param y world y of any block in a 16-block-high section
     * @return true if the whole section is plain air, or is outside the chunk
     */
    public boolean isAirSection(int y) {
        int section = (y - minY) >> 4;
        if (y < minY || section >= sections.length) {
            return true;
        }
        return sections[section] == null && palette[uniform[section]].getMaterial() == Material.AIR;
    }

    /**
     * Finds the ground in a column: the highest block that can be stood on, ignoring trees.
     * @param x x in the chunk, 0 to 15
     * @param z z in the chunk, 0 to 15
     * @return y of the ground block, or null if the column has none
     */
    @Nullable
    public Integer getGroundLevel(int x, int z) {
        for (int section = sections.length - 1; section >= 0; section--) {
            int baseY = minY + (section << 4);
            // Empty sections are skipped whole
            for (int y = baseY + 15; y >= baseY && !isAirSection(baseY); y--) {
                Material m = getBlockData(x, y, z).getMaterial();
                if (m.isSolid() && !Tag.LEAVES.isTagged(m) && !Tag.LOGS.isTagged(m)) {
                    return y;
                }
            }
        }
        return null;
    }

    /**
     * Copies the blocks of this chunk into a chunk being generated. The chunk is expected to
     * be empty, so plain air is not written.
     * @param cd chunk being generated
     */
    public void copyTo(ChunkData cd) {
        for (int section = 0; section < sections.length; section++) {
            int baseY = minY + (section << 4);
            if (baseY + 16 <= cd.getMinHeight() || baseY >= cd.getMaxHeight()) {
                continue;
            }
            short[] blocks = sections[section];
            if (blocks == null) {
                BlockData data = palette[uniform[section]];
                if (data.getMaterial() != Material.AIR) {
                    cd.setRegion(0, baseY, 0, 16, baseY + 16, 16, data);
                }
            } else {
                copySection(cd, blocks, baseY);
            }
        }
    }

    private void copySection(ChunkData cd, short[] blocks, int baseY) {
        int i = 0;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockData data = palette[blocks[i++]];
                    if (data.getMaterial() != Material.AIR) {
                        cd.setBlock(x, baseY + y, z, data);
                    }
                }
            }
        }
    }

    /**
     * @return entities to spawn in the chunk
     */
    public List<EntityData> getEntities() {
        return entities;
    }

    /**
     * @return tile entities in the chunk
     */
    public List<ChestData> getChests() {
        return chests;
    }
}
