package world.bentobox.boxed.generators.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests capturing a chunk with {@link TemplateBuilder#toRawChunk} and copying it back out
 * with {@link TemplateChunk}.
 */
class TemplateChunkTest {

    private static final int MIN_Y = -16;
    private static final int MAX_Y = 32;

    private BlockData air;
    private BlockData stone;
    private BlockData dirt;
    private Biome plains;
    private Biome desert;
    private RawChunk raw;
    private TemplateChunk chunk;
    private RawBiomes rawBiomes;

    private static BlockData data(Material material, String string) {
        BlockData bd = mock(BlockData.class);
        when(bd.getMaterial()).thenReturn(material);
        when(bd.getAsString()).thenReturn(string);
        return bd;
    }

    private static Biome biome(String key) {
        Biome biome = mock(Biome.class);
        when(biome.getKey()).thenReturn(NamespacedKey.fromString(key));
        return biome;
    }

    @BeforeEach
    void setUp() {
        air = data(Material.AIR, "minecraft:air");
        stone = data(Material.STONE, "minecraft:stone");
        dirt = data(Material.DIRT, "minecraft:dirt");
        plains = biome("minecraft:plains");
        desert = biome("minecraft:desert");

        // Stone below y=0, one dirt block at 3,5,7 in otherwise empty air, air above y=16
        ChunkSnapshot snapshot = mock(ChunkSnapshot.class);
        when(snapshot.getBlockData(anyInt(), anyInt(), anyInt())).thenAnswer(inv -> {
            int x = inv.getArgument(0);
            int y = inv.getArgument(1);
            int z = inv.getArgument(2);
            if (y < 0) {
                return stone;
            }
            return x == 3 && y == 5 && z == 7 ? dirt : air;
        });
        // Desert underground, plains above
        when(snapshot.getBiome(anyInt(), anyInt(), anyInt()))
                .thenAnswer(inv -> (int) inv.getArgument(1) < 0 ? desert : plains);

        raw = TemplateBuilder.toRawChunk(snapshot, MIN_Y, MAX_Y, List.of(), List.of());
        BlockData[] palette = raw.blockPalette().stream()
                .map(s -> List.of(air, stone, dirt).stream().filter(b -> b.getAsString().equals(s)).findFirst().orElseThrow())
                .toArray(BlockData[]::new);
        chunk = new TemplateChunk(raw.minY(), palette, raw.sections(), raw.uniform(), List.of(), List.of());
        rawBiomes = TemplateBuilder.toRawBiomes(snapshot, MIN_Y, MAX_Y);
    }

    @Test
    void testUniformSectionsAreNotStoredAsArrays() {
        assertEquals(3, raw.sections().length);
        assertNull(raw.sections()[0]);
        assertEquals(RawChunk.SECTION_BLOCKS, raw.sections()[1].length);
        assertNull(raw.sections()[2]);
        assertEquals(3, raw.blockPalette().size());
    }

    @Test
    void testGetBlockData() {
        assertEquals(stone, chunk.getBlockData(0, -16, 0));
        assertEquals(stone, chunk.getBlockData(15, -1, 15));
        assertEquals(dirt, chunk.getBlockData(3, 5, 7));
        assertEquals(air, chunk.getBlockData(7, 5, 3));
        assertEquals(air, chunk.getBlockData(0, 31, 0));
        assertNull(chunk.getBlockData(0, 32, 0));
        assertNull(chunk.getBlockData(0, -17, 0));
    }

    @Test
    void testRawBiomes() {
        assertEquals(MIN_Y, rawBiomes.minY());
        assertEquals(List.of("minecraft:desert", "minecraft:plains"), rawBiomes.palette());
        // 48 blocks high = 12 layers of 16 cells
        assertEquals(12 * 16, rawBiomes.cells().length);
        // Desert below y=0, plains above
        assertEquals(0, rawBiomes.cells()[RawBiomes.index(0, 0, 0)]);
        assertEquals(0, rawBiomes.cells()[RawBiomes.index(12, 12, 12)]);
        assertEquals(1, rawBiomes.cells()[RawBiomes.index(0, 16, 0)]);
        assertEquals(1, rawBiomes.cells()[RawBiomes.index(15, 47, 15)]);
    }

    @Test
    void testCopyTo() {
        ChunkData cd = mock(ChunkData.class);
        when(cd.getMinHeight()).thenReturn(MIN_Y);
        when(cd.getMaxHeight()).thenReturn(MAX_Y);
        chunk.copyTo(cd);
        // The stone section is filled in one go
        verify(cd).setRegion(0, -16, 0, 16, 0, 16, stone);
        // The only block set individually is the dirt; air is never written
        verify(cd, times(1)).setBlock(anyInt(), anyInt(), anyInt(), any(BlockData.class));
        verify(cd).setBlock(3, 5, 7, dirt);
        verify(cd, never()).setRegion(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), org.mockito.ArgumentMatchers.eq(air));
    }

    @Test
    void testCopyToSkipsSectionsOutsideTheWorld() {
        ChunkData cd = mock(ChunkData.class);
        when(cd.getMinHeight()).thenReturn(0);
        when(cd.getMaxHeight()).thenReturn(MAX_Y);
        chunk.copyTo(cd);
        verify(cd, never()).setRegion(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any(BlockData.class));
        verify(cd).setBlock(3, 5, 7, dirt);
    }
}
