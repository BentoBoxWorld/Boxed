package world.bentobox.boxed.generators.biomes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.bukkit.block.Biome;
import org.junit.jupiter.api.Test;

import world.bentobox.boxed.CommonTestSetup;

/**
 * Tests patches of biome that are put on the land by hand.
 */
class BiomePatchTest extends CommonTestSetup {

    @Test
    void testTextRoundTrip() {
        BiomePatch patch = new BiomePatch(-120, 64, 48, "swamp");
        assertEquals("-120,64,48,swamp", patch.text());
        assertEquals(patch, BiomePatch.parse(patch.text()));
        assertEquals(patch, BiomePatch.parse(" -120 , 64 , 48 , SWAMP "));
    }

    @Test
    void testNamesAreTidied() {
        assertEquals("lush_caves", new BiomePatch(0, 0, 20, "minecraft:LUSH_CAVES").biome());
    }

    @Test
    void testRadiusIsKeptInRange() {
        assertEquals(BiomePatch.MIN_RADIUS, new BiomePatch(0, 0, 1, "swamp").radius());
        assertEquals(BiomePatch.MAX_RADIUS, new BiomePatch(0, 0, 9999, "swamp").radius());
    }

    @Test
    void testBadTextIsNotAPatch() {
        assertNull(BiomePatch.parse(""));
        assertNull(BiomePatch.parse("1,2,3"));
        assertNull(BiomePatch.parse("1,2,three,swamp"));
        assertNull(BiomePatch.parse("1,2,3, "));
    }

    @Test
    void testContains() {
        BiomePatch patch = new BiomePatch(1000, -2000, 48, "swamp");
        assertTrue(patch.contains(1000, -2000));
        // Well inside the edge, whichever way it wanders
        assertTrue(patch.contains(1000 + 30, -2000));
        assertTrue(patch.contains(1000, -2000 - 30));
        // Beyond anywhere the edge can wander to
        assertEquals(60, patch.reach());
        assertFalse(patch.contains(1000 + 61, -2000));
        assertFalse(patch.contains(1000 + 50, -2000 + 50));
    }

    @Test
    void testTheEdgeWanders() {
        BiomePatch patch = new BiomePatch(0, 0, 48, "swamp");
        // Somewhere round the edge a block further out than the radius is in, or one nearer is out
        boolean wanders = false;
        for (int angle = 0; angle < 360 && !wanders; angle++) {
            double a = Math.toRadians(angle);
            boolean outer = patch.contains((int) Math.round(52 * Math.cos(a)), (int) Math.round(52 * Math.sin(a)));
            boolean inner = patch.contains((int) Math.round(44 * Math.cos(a)), (int) Math.round(44 * Math.sin(a)));
            wanders = outer || !inner;
        }
        assertTrue(wanders);
    }

    @Test
    void testSurfaceAndCavePatchesDoNotMix() {
        Map<String, Biome> known = Map.of("swamp", Biome.SWAMP, "lush_caves", Biome.LUSH_CAVES);
        BiomePatches patches = new BiomePatches(
                List.of(new BiomePatch(0, 0, 48, "swamp"), new BiomePatch(0, 0, 48, "lush_caves")), known::get);
        assertFalse(patches.isEmpty());
        assertSame(Biome.SWAMP, patches.get(0, 0, false));
        assertSame(Biome.LUSH_CAVES, patches.get(0, 0, true));
        assertNull(patches.get(500, 500, false));
        assertNull(patches.get(500, 500, true));
    }

    @Test
    void testTheNewestPatchWins() {
        Map<String, Biome> known = Map.of("swamp", Biome.SWAMP, "desert", Biome.DESERT);
        BiomePatches patches = new BiomePatches(
                List.of(new BiomePatch(0, 0, 48, "swamp"), new BiomePatch(20, 0, 48, "desert")), known::get);
        assertSame(Biome.DESERT, patches.get(10, 0, false));
        // Only the older one reaches here
        assertSame(Biome.SWAMP, patches.get(-35, 0, false));
    }

    @Test
    void testUnknownBiomesAreLeftOut() {
        BiomePatches patches = new BiomePatches(List.of(new BiomePatch(0, 0, 48, "nosuch")), name -> null);
        assertTrue(patches.isEmpty());
        assertNull(patches.get(0, 0, false));
        assertTrue(BiomePatches.NONE.isEmpty());
        assertTrue(BiomePatches.isCave("deep_dark"));
        assertFalse(BiomePatches.isCave("swamp"));
    }
}
