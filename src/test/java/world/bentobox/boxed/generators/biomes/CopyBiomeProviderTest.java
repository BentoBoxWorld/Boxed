package world.bentobox.boxed.generators.biomes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.generator.WorldInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.CommonTestSetup;
import world.bentobox.boxed.Settings;
import world.bentobox.boxed.generators.chunks.AbstractBoxedChunkGenerator;
import world.bentobox.boxed.generators.template.Template;

/**
 * Tests {@link AbstractCopyBiomeProvider#getBiome(WorldInfo, int, int, int)} via the
 * concrete overworld {@link BoxedBiomeGenerator}. The provider looks up the biome stored in
 * the template for the (wrapped) chunk and the in-chunk position.
 */
class CopyBiomeProviderTest extends CommonTestSetup {

    private BoxedBiomeGenerator gen;
    private WorldInfo worldInfo;
    private Boxed addon;

    @BeforeEach
    void setUpProvider() {
        addon = mock(Boxed.class);
        Settings settings = mock(Settings.class);
        when(addon.getSettings()).thenReturn(settings);
        when(settings.getIslandDistance()).thenReturn(400);

        AbstractBoxedChunkGenerator chunkGen = mock(AbstractBoxedChunkGenerator.class);
        when(addon.getChunkGenerator(Environment.NORMAL)).thenReturn(chunkGen);

        worldInfo = mock(WorldInfo.class);
        when(worldInfo.getEnvironment()).thenReturn(Environment.NORMAL);

        // 400 / 16 = 25 chunks half-width.
        when(chunkGen.repeatCalc(anyInt()))
        .thenAnswer(inv -> AbstractBoxedChunkGenerator.repeatCalc(inv.getArgument(0), 25));

        gen = new BoxedBiomeGenerator(addon);
    }

    private Template template(int chunkX, int chunkZ) {
        Template template = mock(Template.class);
        when(template.hasChunk(chunkX, chunkZ)).thenReturn(true);
        when(addon.getTemplate(Environment.NORMAL, 0)).thenReturn(template);
        return template;
    }

    @Test
    void testReturnsStoredBiome() {
        // x=3,z=5 -> chunk 0,0 ; in-chunk position (3, y, 5)
        Template template = template(0, 0);
        when(template.getBiome(0, 0, 3, 64, 5)).thenReturn(Biome.PLAINS);

        assertEquals(Biome.PLAINS, gen.getBiome(worldInfo, 3, 64, 5));
    }

    @Test
    void testReturnsDefaultBiomeWhenPositionNotStored() {
        // Chunk exists but has no biome at the requested position -> default (OCEAN)
        template(0, 0);

        assertEquals(Biome.OCEAN, gen.getBiome(worldInfo, 3, 64, 5));
    }

    @Test
    void testReturnsDefaultBiomeAndWarnsWhenChunkMissing() {
        // There is a template, but not for this chunk
        template(5, 5);

        assertEquals(Biome.OCEAN, gen.getBiome(worldInfo, 3, 64, 5));
        // The missing chunk is logged as a warning
        verify(plugin).logWarning("Template chunk 0 0 is not stored");
    }

    @Test
    void testReturnsDefaultBiomeWhenThereIsNoTemplate() {
        assertEquals(Biome.OCEAN, gen.getBiome(worldInfo, 3, 64, 5));
    }

    @Test
    void testCoordinatesAreWrappedIntoSeedRegion() {
        // 403 >> 4 = 25 ; repeatCalc(25) with size 25 = floorMod(50,50)-25 = -25
        Template template = template(-25, 0);
        when(template.getBiome(-25, 0, 3, 64, 5)).thenReturn(Biome.DESERT);

        assertEquals(Biome.DESERT, gen.getBiome(worldInfo, 403, 64, 5));
    }
}
