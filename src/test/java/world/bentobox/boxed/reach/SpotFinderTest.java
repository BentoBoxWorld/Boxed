package world.bentobox.boxed.reach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import world.bentobox.boxed.reach.SpotFinder.Candidate;

/**
 * Tests the search of a seed's biomes for places to start a box.
 */
class SpotFinderTest {

    /**
     * A plain with a forest from x = 0 to 255 and a river along its west edge
     */
    private static String strip(int x, int z) {
        if (x >= -16 && x < 0) {
            return "river";
        }
        return x >= 0 && x < 256 ? "forest" : "plains";
    }

    @Test
    void testBiomeKinds() {
        assertTrue(SpotFinder.isWooded("birch_forest"));
        assertTrue(SpotFinder.isWooded("old_growth_pine_taiga"));
        assertTrue(SpotFinder.isWooded("cherry_grove"));
        assertFalse(SpotFinder.isWooded("windswept_forest"));
        assertFalse(SpotFinder.isWooded("mangrove_swamp"));
        assertFalse(SpotFinder.isWooded("plains"));
        assertTrue(SpotFinder.isWater("frozen_river"));
        assertTrue(SpotFinder.isWater("deep_lukewarm_ocean"));
        assertFalse(SpotFinder.isWater("beach"));
    }

    @Test
    void testFindsWoodedLandNearWater() {
        List<Candidate> found = new SpotFinder(SpotFinderTest::strip, 320).find(1000, 16, 3, 100);
        assertEquals(3, found.size());
        for (Candidate c : found) {
            assertEquals("forest", c.biome());
            // The best places have the river 32 or 48 blocks away
            assertTrue(c.waterDistance() == 32 || c.waterDistance() == 48, c.toString());
            assertTrue(c.landPercent() > 80);
        }
        // Best first, and the nearest to the middle of the seed wins a tie
        assertEquals(0, found.get(0).z());
        assertTrue(found.get(0).rating() >= found.get(1).rating());
    }

    @Test
    void testPlacesAreKeptApart() {
        List<Candidate> found = new SpotFinder(SpotFinderTest::strip, 320).find(1000, 16, 50, 400);
        assertTrue(found.size() > 1);
        for (Candidate a : found) {
            for (Candidate b : found) {
                assertTrue(a == b || Math.abs(a.x() - b.x()) >= 400 || Math.abs(a.z() - b.z()) >= 400);
            }
        }
    }

    @Test
    void testPlacesAreOnChunkCorners() {
        List<Candidate> found = new SpotFinder(SpotFinderTest::strip, 320).find(500, 50, 10, 64);
        assertFalse(found.isEmpty());
        found.forEach(c -> assertEquals(0, (c.x() | c.z()) & 15));
    }

    @Test
    void testNoWaterNearbyRatesLower() {
        SpotFinder finder = new SpotFinder(SpotFinderTest::strip, 320);
        // 200 blocks from the river is further than the search for water goes
        Candidate far = finder.at(200, 0);
        Candidate near = finder.at(16, 0);
        assertEquals(-1, far.waterDistance());
        assertEquals(32, near.waterDistance());
        assertTrue(near.rating() > far.rating());
    }

    @Test
    void testNothingFoundInTheSea() {
        assertTrue(new SpotFinder((x, z) -> "ocean", 320).find(500, 64, 5, 100).isEmpty());
        // Wooded, but with water right beside it
        assertTrue(new SpotFinder((x, z) -> x == 0 && z == 0 ? "forest" : "river", 320).find(64, 64, 5, 100)
                .isEmpty());
    }
}
