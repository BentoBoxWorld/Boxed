package world.bentobox.boxed.objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link IslandStructures} - the per-island record of placed structures.
 */
class IslandStructuresTest {

    private IslandStructures is;

    @BeforeEach
    void setUp() {
        is = new IslandStructures("island-123");
    }

    @Test
    void testConstructorSetsUniqueId() {
        assertEquals("island-123", is.getUniqueId());
    }

    @Test
    void testSetUniqueId() {
        is.setUniqueId("other");
        assertEquals("other", is.getUniqueId());
    }

    @Test
    void testMapsStartEmpty() {
        assertTrue(is.getStructureBoundingBoxMap().isEmpty());
        assertTrue(is.getNetherStructureBoundingBoxMap().isEmpty());
    }

    @Test
    void testAddStructure() {
        BoundingBox bb = new BoundingBox(0, 0, 0, 16, 16, 16);
        is.addStructure(bb, "minecraft:village");
        assertEquals(1, is.getStructureBoundingBoxMap().size());
        assertEquals("minecraft:village", is.getStructureBoundingBoxMap().get(bb));
        // The nether map is untouched
        assertTrue(is.getNetherStructureBoundingBoxMap().isEmpty());
    }

    @Test
    void testAddNetherStructure() {
        BoundingBox bb = new BoundingBox(0, 0, 0, 8, 8, 8);
        is.addNetherStructure(bb, "minecraft:fortress");
        assertEquals(1, is.getNetherStructureBoundingBoxMap().size());
        assertEquals("minecraft:fortress", is.getNetherStructureBoundingBoxMap().get(bb));
        assertTrue(is.getStructureBoundingBoxMap().isEmpty());
    }

    @Test
    void testGettersLazilyRecreateNullMaps() {
        is.setStructureBoundingBoxMap(null);
        is.setNetherStructureBoundingBoxMap(null);
        assertTrue(is.getStructureBoundingBoxMap().isEmpty());
        assertTrue(is.getNetherStructureBoundingBoxMap().isEmpty());
        // And adding after a null reset still works
        BoundingBox bb = new BoundingBox(0, 0, 0, 1, 1, 1);
        is.addStructure(bb, "minecraft:shipwreck");
        assertEquals("minecraft:shipwreck", is.getStructureBoundingBoxMap().get(bb));
    }
}
