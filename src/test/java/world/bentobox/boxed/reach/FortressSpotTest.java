package world.bentobox.boxed.reach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import world.bentobox.boxed.reach.FortressSpot.Spot;

/**
 * Tests picking the middle of the Nether next to a fortress.
 */
class FortressSpotTest {

    private static final int[] MIN = { -128, -128 };
    private static final int[] MAX = { 127, 127 };

    @Test
    void testPicksOpenGroundJustClearOfTheSpawner() {
        List<int[]> spawners = List.of(new int[] { 0, 0 });
        List<int[]> bricks = List.of(new int[] { 0, 0 }, new int[] { 0, 10 });
        Optional<Spot> spot = FortressSpot.choose(MIN, MAX, spawners, bricks, (x, z) -> 1);
        assertTrue(spot.isPresent());
        // The nearest chunk corners that are far enough away are 32 blocks off
        assertEquals(FortressSpot.MIN_SPAWNER_SIZE, spot.get().spawnerSize());
        assertEquals(0, (spot.get().x() | spot.get().z()) & 15);
        assertTrue(spot.get().fortressSize() >= FortressSpot.MIN_FORTRESS_SIZE);
    }

    @Test
    void testSkipsGroundThatIsNotOpen() {
        List<int[]> spawners = List.of(new int[] { 0, 0 });
        // Only the east side is open
        Optional<Spot> spot = FortressSpot.choose(MIN, MAX, spawners, List.of(), (x, z) -> x >= 64 ? 1 : 0);
        assertTrue(spot.isPresent());
        assertEquals(64, spot.get().x());
        assertEquals(64, spot.get().spawnerSize());
    }

    @Test
    void testKeepsOutOfTheFortress() {
        List<int[]> spawners = List.of(new int[] { 0, 0 });
        // A corridor runs east from the spawner
        List<int[]> bricks = new java.util.ArrayList<>();
        for (int x = 0; x < 128; x++) {
            bricks.add(new int[] { x, 0 });
        }
        Spot spot = FortressSpot.choose(MIN, MAX, spawners, bricks, (x, z) -> x > 0 ? 1 : 0).orElseThrow();
        assertTrue(Math.abs(spot.z()) >= FortressSpot.MIN_FORTRESS_SIZE, spot.toString());
    }

    @Test
    void testNothingWithoutASpawnerOrOpenGround() {
        assertTrue(FortressSpot.choose(MIN, MAX, List.of(), List.of(), (x, z) -> 1).isEmpty());
        assertTrue(FortressSpot.choose(MIN, MAX, List.of(new int[] { 0, 0 }), List.of(), (x, z) -> 0).isEmpty());
    }

    @Test
    void testMeasuresAPlaceThatIsAlreadyPicked() {
        FortressSpot.Spot spot = FortressSpot.at(0, 0, List.of(new int[] { 40, -10 }), List.of(new int[] { 5, 12 }));
        assertEquals(LandSurvey.sizeFor(40, -10), spot.spawnerSize());
        assertEquals(LandSurvey.sizeFor(5, 12), spot.fortressSize());
        FortressSpot.Spot none = FortressSpot.at(16, 32, List.of(), List.of());
        assertEquals(16, none.x());
        assertEquals(Integer.MAX_VALUE, none.spawnerSize());
        assertEquals(Integer.MAX_VALUE, none.fortressSize());
    }
}
