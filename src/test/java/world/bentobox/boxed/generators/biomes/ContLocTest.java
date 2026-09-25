package world.bentobox.boxed.generators.biomes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import world.bentobox.boxed.generators.biomes.AbstractSeedBiomeProvider.ContLoc;

/**
 * Tests {@link ContLoc#getCont(double)} band lookup, including values outside the nominal
 * continentalness range that vanilla noise can produce.
 */
class ContLocTest {

    @Test
    void testBelowNominalRangeIsMushroomFields() {
        // Value seen in the wild that used to throw "contloc out of spec"
        assertEquals(ContLoc.MUSHROOM_FIELDS, ContLoc.getCont(-1.2687000036239624));
        assertEquals(ContLoc.MUSHROOM_FIELDS, ContLoc.getCont(-100D));
    }

    @Test
    void testAboveNominalRangeIsFarInland() {
        assertEquals(ContLoc.FAR_INLAND, ContLoc.getCont(10D));
        assertEquals(ContLoc.FAR_INLAND, ContLoc.getCont(100D));
    }

    @Test
    void testBandBoundaries() {
        assertEquals(ContLoc.MUSHROOM_FIELDS, ContLoc.getCont(-1.2));
        assertEquals(ContLoc.DEEP_OCEAN, ContLoc.getCont(-1.05));
        assertEquals(ContLoc.OCEAN, ContLoc.getCont(-0.455));
        assertEquals(ContLoc.COAST, ContLoc.getCont(-0.19));
        assertEquals(ContLoc.NEAR_INLAND, ContLoc.getCont(-0.11));
        assertEquals(ContLoc.MID_INLAND, ContLoc.getCont(0.03));
        assertEquals(ContLoc.FAR_INLAND, ContLoc.getCont(0.3));
    }
}
