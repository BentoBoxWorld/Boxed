package world.bentobox.boxed.workshop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.junit.jupiter.api.Test;

/**
 * Tests how a structure placed in the workshop is written down and read back.
 */
class PlacedStructureTest {

    private static final PlacedStructure IGLOO = new PlacedStructure(100, 65, -40, "igloo/top",
            StructureRotation.CLOCKWISE_90, Mirror.NONE, true);

    @Test
    void testNameIsTidied() {
        assertEquals("igloo/top", new PlacedStructure(0, 0, 0, " minecraft:Igloo/Top", StructureRotation.NONE,
                Mirror.NONE, false).name());
    }

    @Test
    void testStructuresFileEntryIsMeasuredFromTheMiddle() {
        assertEquals("20,65,-8", IGLOO.key(80, -32));
        assertEquals("igloo/top,CLOCKWISE_90,NONE,NO_MOBS", IGLOO.value());
        assertEquals(IGLOO, PlacedStructure.of("20,65,-8", "igloo/top,CLOCKWISE_90,NONE,NO_MOBS", 80, -32));
    }

    @Test
    void testTextRoundTrip() {
        assertEquals("100,65,-40,igloo/top,CLOCKWISE_90,NONE,NO_MOBS", IGLOO.text());
        assertEquals(IGLOO, PlacedStructure.parse(IGLOO.text()));
        PlacedStructure house = new PlacedStructure(1, 2, 3, "village/plains/houses/plains_small_house_1",
                StructureRotation.NONE, Mirror.FRONT_BACK, false);
        assertEquals(house, PlacedStructure.parse(house.text()));
    }

    @Test
    void testOldFilesAreRead() {
        // Only a name
        PlacedStructure s = PlacedStructure.of("1, 2, 3", "igloo/top", 0, 0);
        assertEquals(new PlacedStructure(1, 2, 3, "igloo/top", StructureRotation.NONE, Mirror.NONE, false), s);
        // NO_MOBS used to be written after a space
        assertTrue(PlacedStructure.of("1,2,3", "igloo/top,NONE,NONE NO_MOBS", 0, 0).noMobs());
        // Unknown rotations and mirrors are taken as none
        assertEquals(StructureRotation.NONE, PlacedStructure.of("1,2,3", "igloo/top,SIDEWAYS,UP", 0, 0).rotation());
    }

    @Test
    void testBadEntries() {
        assertNull(PlacedStructure.parse("1,2,3"));
        assertNull(PlacedStructure.parse("a,2,3,igloo/top"));
        assertNull(PlacedStructure.of("1,2", "igloo/top", 0, 0));
        assertNull(PlacedStructure.of("1,2,3", " ", 0, 0));
    }

    @Test
    void testSamePlace() {
        assertTrue(IGLOO.samePlace(new PlacedStructure(100, 65, -40, "other", StructureRotation.NONE, Mirror.NONE, false)));
        assertFalse(IGLOO.samePlace(new PlacedStructure(100, 66, -40, "igloo/top", StructureRotation.CLOCKWISE_90,
                Mirror.NONE, true)));
    }
}
