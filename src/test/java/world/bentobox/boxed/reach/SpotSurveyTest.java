package world.bentobox.boxed.reach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import world.bentobox.boxed.CommonTestSetup;
import world.bentobox.boxed.reach.SpotSurvey.Score;

/**
 * Tests the scoring of the land around a place to start a box.
 */
class SpotSurveyTest extends CommonTestSetup {

    /**
     * Something that says which block is at a place in the land
     */
    @FunctionalInterface
    private interface Land {
        Material at(int x, int y, int z);
    }

    /**
     * Surveys the four chunks around the middle of some land that is 70 blocks high.
     */
    private static Score survey(Land land) {
        SpotSurvey survey = new SpotSurvey();
        for (int cx = -1; cx <= 0; cx++) {
            for (int cz = -1; cz <= 0; cz++) {
                int baseX = cx << 4;
                int baseZ = cz << 4;
                ChunkSnapshot snapshot = mock(ChunkSnapshot.class);
                when(snapshot.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(70);
                when(snapshot.getBlockType(anyInt(), anyInt(), anyInt())).thenAnswer(i -> land.at(
                        baseX + i.<Integer>getArgument(0), i.<Integer>getArgument(1), baseZ + i.<Integer>getArgument(2)));
                survey.add(snapshot, baseX, baseZ, 40);
            }
        }
        return survey.score();
    }

    /**
     * Grass over dirt over stone, with the surface at y = 64
     */
    private static Material plain(int y) {
        if (y > 64) {
            return Material.AIR;
        }
        if (y == 64) {
            return Material.GRASS_BLOCK;
        }
        return y > 60 ? Material.DIRT : Material.STONE;
    }

    @Test
    void testGoodStart() {
        Score score = survey((x, y, z) -> {
            if (x == 2 && z == 2 && y > 64 && y < 70) {
                return Material.OAK_LOG;
            }
            if (y == 70) {
                // A canopy over everything, which is not the ground
                return Material.OAK_LEAVES;
            }
            if (x == 10 && y == 64) {
                return Material.WATER;
            }
            if (x == -3 && y == 50) {
                return z == 0 ? Material.COAL_ORE : Material.IRON_ORE;
            }
            return plain(y);
        });
        assertTrue(score.dryCentre());
        assertEquals(100, score.dryPercent());
        assertEquals(3, score.wood());
        assertEquals(11, score.water());
        assertEquals(1, score.stone());
        assertEquals(3, score.iron());
        assertEquals(3, score.coal());
        assertEquals(100, score.score());
        assertTrue(score.describe().contains("wood at size 3"));
    }

    @Test
    void testBareLandScoresLow() {
        Score score = survey((x, y, z) -> plain(y));
        assertTrue(score.dryCentre());
        assertEquals(-1, score.wood());
        assertEquals(-1, score.water());
        assertEquals(-1, score.iron());
        assertEquals(-1, score.coal());
        // Dry land and stone are all it has
        assertEquals(40, score.score());
        assertTrue(score.describe().contains("wood at more than " + SpotSurvey.RADIUS));
    }

    @Test
    void testSea() {
        Score score = survey((x, y, z) -> {
            if (y > 64) {
                return Material.AIR;
            }
            return y > 50 ? Material.WATER : Material.STONE;
        });
        assertFalse(score.dryCentre());
        assertEquals(0, score.dryPercent());
        assertEquals(1, score.water());
        // The stone is too far under the water to count
        assertEquals(-1, score.stone());
        assertEquals(15, score.score());
        assertTrue(score.describe().contains("under water"));
    }

    @Test
    void testIceIsNotDryLand() {
        Score score = survey((x, y, z) -> y == 64 && x >= 0 ? Material.ICE : plain(y));
        assertFalse(score.dryCentre());
        assertTrue(score.dryPercent() > 40 && score.dryPercent() < 60);
    }

    @Test
    void testDeepWaterAndDeepStoneDoNotCount() {
        // Water in a cave and a thick layer of dirt over the stone
        Score score = survey((x, y, z) -> {
            if (y > 64) {
                return Material.AIR;
            }
            if (y == 45) {
                return Material.WATER;
            }
            return y > 50 ? Material.DIRT : Material.STONE;
        });
        assertEquals(-1, score.water());
        assertEquals(-1, score.stone());
    }
}
