package world.bentobox.boxed.objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.bentobox.util.Pair;
import world.bentobox.boxed.objects.ToBePlacedStructures.StructureRecord;

/**
 * Tests {@link ToBePlacedStructures} and its {@link StructureRecord} component.
 */
class ToBePlacedStructuresTest {

    private ToBePlacedStructures tbps;

    @BeforeEach
    void setUp() {
        tbps = new ToBePlacedStructures();
    }

    @Test
    void testDefaultUniqueId() {
        assertEquals("ToDo", tbps.getUniqueId());
    }

    @Test
    void testSetUniqueId() {
        tbps.setUniqueId("changed");
        assertEquals("changed", tbps.getUniqueId());
    }

    @Test
    void testReadyToBuildStartsEmpty() {
        assertTrue(tbps.getReadyToBuild().isEmpty());
    }

    @Test
    void testGetterLazilyRecreatesNullMap() {
        tbps.setReadyToBuild(null);
        assertTrue(tbps.getReadyToBuild().isEmpty());
    }

    @Test
    void testSetAndGetReadyToBuild() {
        Map<Pair<Integer, Integer>, List<StructureRecord>> map = new HashMap<>();
        StructureRecord rec = new StructureRecord("village", "minecraft:village",
                new Location(null, 1, 2, 3), StructureRotation.NONE, Mirror.NONE, false, new HashMap<>());
        map.put(new Pair<>(0, 0), new ArrayList<>(List.of(rec)));
        tbps.setReadyToBuild(map);
        assertSame(map, tbps.getReadyToBuild());
        assertEquals(1, tbps.getReadyToBuild().get(new Pair<>(0, 0)).size());
    }

    @Test
    void testStructureRecordAccessors() {
        Location loc = new Location(null, 10, 64, -20);
        Map<org.bukkit.util.Vector, org.bukkit.block.data.BlockData> removed = new HashMap<>();
        StructureRecord rec = new StructureRecord("name", "minecraft:igloo", loc,
                StructureRotation.CLOCKWISE_90, Mirror.LEFT_RIGHT, true, removed);
        assertEquals("name", rec.name());
        assertEquals("minecraft:igloo", rec.structure());
        assertSame(loc, rec.location());
        assertEquals(StructureRotation.CLOCKWISE_90, rec.rot());
        assertEquals(Mirror.LEFT_RIGHT, rec.mirror());
        assertTrue(rec.noMobs());
        assertSame(removed, rec.removedBlocks());
    }

    @Test
    void testStructureRecordEqualityAndToString() {
        Location loc = new Location(null, 0, 0, 0);
        Map<org.bukkit.util.Vector, org.bukkit.block.data.BlockData> removed = new HashMap<>();
        StructureRecord a = new StructureRecord("n", "s", loc, StructureRotation.NONE, Mirror.NONE, false, removed);
        StructureRecord b = new StructureRecord("n", "s", loc, StructureRotation.NONE, Mirror.NONE, false, removed);
        StructureRecord different = new StructureRecord("other", "s", loc, StructureRotation.NONE, Mirror.NONE, false,
                removed);
        // Records get value-based equals/hashCode for free
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, different);
        assertTrue(a.toString().contains("name=n"));
    }
}
