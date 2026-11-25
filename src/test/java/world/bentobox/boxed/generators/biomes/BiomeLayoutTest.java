package world.bentobox.boxed.generators.biomes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.block.Biome;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.boxed.CommonTestSetup;

/**
 * Tests the ways biomes can be laid out around the middle of an area.
 */
class BiomeLayoutTest extends CommonTestSetup {

    private static final int DIST = 320;
    private final List<String> errors = new ArrayList<>();
    private Map<String, Biome> known;

    @Override
    @BeforeEach
    public void setUp() {
        super.setUp();
        known = Map.of("DESERT", Biome.DESERT, "JUNGLE", Biome.JUNGLE, "FOREST", Biome.FOREST, "PLAINS",
                Biome.PLAINS);
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private BiomeLayout layout(String yaml, double blend) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return new BiomeLayout(config, blend, DIST, known::get, errors::add);
    }

    @Test
    void testQuarters() throws InvalidConfigurationException {
        BiomeLayout l = layout("""
                north-east:
                - 0.1:CUSTOM
                - 0.5:DESERT
                - 1.0:JUNGLE
                south-west:
                - 2.0:FOREST
                """, 0);
        // North is negative z
        assertNull(l.get(10, -10));
        assertEquals(Biome.DESERT, l.get(60, -60));
        assertEquals(Biome.JUNGLE, l.get(200, -200));
        // Beyond the last entry of a quarter
        assertNull(l.get(300, -300));
        assertEquals(Biome.FOREST, l.get(-300, 300));
        // Quarters with no list
        assertNull(l.get(-60, -60));
        assertNull(l.get(60, 60));
        // Nothing is chosen further out than twice the radius
        assertEquals(Biome.FOREST, l.get(0, 640));
        assertNull(l.get(0, 641));
        assertEquals(Set.of(Biome.DESERT, Biome.JUNGLE, Biome.FOREST), l.biomes());
        assertTrue(errors.isEmpty());
    }

    @Test
    void testRingsAreTheSameAllTheWayRound() throws InvalidConfigurationException {
        BiomeLayout l = layout("""
                rings:
                - 0.5:DESERT
                - 1.0:JUNGLE
                """, 0);
        for (int[] d : new int[][] { { 1, 1 }, { -1, 1 }, { 1, -1 }, { -1, -1 } }) {
            assertEquals(Biome.DESERT, l.get(50 * d[0], 50 * d[1]));
            assertEquals(Biome.JUNGLE, l.get(200 * d[0], 200 * d[1]));
        }
    }

    @Test
    void testPatches() throws InvalidConfigurationException {
        BiomeLayout l = layout("""
                patches:
                  size: 64
                  biomes: [CUSTOM, DESERT, JUNGLE, FOREST]
                """, 0);
        // The patch in the middle gets the first in the list, which here is the seed's own biome
        assertNull(l.get(0, 0));
        assertNull(l.get(31, -32));
        Set<Biome> seen = new java.util.HashSet<>();
        for (int x = -384; x <= 384; x += 64) {
            for (int z = -384; z <= 384; z += 64) {
                Biome b = l.get(x, z);
                seen.add(b);
                // A patch is one biome all over, and the same every time it is asked for
                assertEquals(b, l.get(x + 10, z - 10));
            }
        }
        // Every biome in the list is used somewhere
        assertEquals(4, seen.size());
    }

    @Test
    void testBlendMovesTheEdges() throws InvalidConfigurationException {
        String yaml = """
                rings:
                - 0.5:DESERT
                - 1.0:JUNGLE
                """;
        BiomeLayout straight = layout(yaml, 0);
        BiomeLayout blended = layout(yaml, 24);
        int different = 0;
        for (int x = -200; x <= 200; x++) {
            // The edge between the two rings is 160 blocks out
            if (straight.get(x, 160) != blended.get(x, 160)) {
                different++;
            }
            // Blending does not reach far from an edge
            assertEquals(Biome.DESERT, blended.get(x / 4, 0));
        }
        assertNotEquals(0, different);
    }

    @Test
    void testMistakesAreReported() throws InvalidConfigurationException {
        BiomeLayout l = layout("""
                rings:
                - DESERT
                - x:JUNGLE
                - 1.0:FOREST
                """, 0);
        assertEquals(2, errors.size());
        assertEquals(Biome.FOREST, l.get(5, 5));
    }

    @Test
    void testNoSection() {
        BiomeLayout l = new BiomeLayout(null, 0, DIST, known::get, errors::add);
        assertNull(l.get(0, 0));
        assertTrue(l.biomes().isEmpty());
    }
}
