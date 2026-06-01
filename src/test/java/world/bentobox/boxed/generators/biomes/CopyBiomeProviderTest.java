package world.bentobox.boxed.generators.biomes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.CommonTestSetup;
import world.bentobox.boxed.Settings;
import world.bentobox.boxed.WhiteBox;
import world.bentobox.boxed.generators.chunks.AbstractBoxedChunkGenerator;
import world.bentobox.boxed.generators.chunks.AbstractBoxedChunkGenerator.ChunkStore;

/**
 * Tests {@link AbstractCopyBiomeProvider#getBiome(WorldInfo, int, int, int)} via the
 * concrete overworld {@link BoxedBiomeGenerator}. The provider looks up the biome that
 * was captured from the seed world for the (wrapped) chunk and the in-chunk position.
 */
class CopyBiomeProviderTest extends CommonTestSetup {

    private BoxedBiomeGenerator gen;
    private AbstractBoxedChunkGenerator chunkGen;
    private WorldInfo worldInfo;
    private Boxed addon;

    @BeforeEach
    public void setUpProvider() {
        addon = mock(Boxed.class);
        Settings settings = mock(Settings.class);
        when(addon.getSettings()).thenReturn(settings);
        when(settings.getIslandDistance()).thenReturn(400);

        chunkGen = mock(AbstractBoxedChunkGenerator.class);
        when(addon.getChunkGenerator(Environment.NORMAL)).thenReturn(chunkGen);

        worldInfo = mock(WorldInfo.class);
        when(worldInfo.getEnvironment()).thenReturn(Environment.NORMAL);

        // 400 / 16 = 25 chunks half-width. repeatCalc needs size > 0.
        WhiteBox.setInternalState(AbstractBoxedChunkGenerator.class, "size", 25);

        gen = new BoxedBiomeGenerator(addon);
    }

    @Test
    void testReturnsStoredBiome() {
        // x=3,z=5 -> chunk 0,0 ; in-chunk position (3, y, 5)
        Map<Vector, Biome> biomes = new HashMap<>();
        biomes.put(new Vector(3, 64, 5), Biome.PLAINS);
        when(chunkGen.getChunk(0, 0)).thenReturn(new ChunkStore(null, null, null, biomes));

        assertEquals(Biome.PLAINS, gen.getBiome(worldInfo, 3, 64, 5));
    }

    @Test
    void testReturnsDefaultBiomeWhenPositionNotStored() {
        // Chunk exists but the requested position is not in the map -> default (OCEAN)
        when(chunkGen.getChunk(0, 0)).thenReturn(new ChunkStore(null, null, null, new HashMap<>()));

        assertEquals(Biome.OCEAN, gen.getBiome(worldInfo, 3, 64, 5));
    }

    @Test
    void testReturnsDefaultBiomeAndWarnsWhenChunkMissing() {
        when(chunkGen.getChunk(0, 0)).thenReturn(null);

        assertEquals(Biome.OCEAN, gen.getBiome(worldInfo, 3, 64, 5));
        // The missing snapshot is logged as a warning
        verify(plugin).logWarning(eq("Snapshot at 0 0 is not stored"));
    }

    @Test
    void testCoordinatesAreWrappedIntoSeedRegion() {
        // x in a far chunk still resolves back into the stored region.
        // x = 3 + 16*25 = 403 -> chunkX 25 -> repeatCalc(25)= -25 ... but getChunk is
        // stubbed on the wrapped coord, so verify the wrapped lookup is used.
        Map<Vector, Biome> biomes = new HashMap<>();
        biomes.put(new Vector(3, 64, 5), Biome.DESERT);
        // 403 >> 4 = 25 ; repeatCalc(25) with size 25 = floorMod(50,50)-25 = -25
        when(chunkGen.getChunk(-25, 0)).thenReturn(new ChunkStore(null, null, null, biomes));

        assertEquals(Biome.DESERT, gen.getBiome(worldInfo, 403, 64, 5));
    }
}
