package world.bentobox.boxed.objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link IslandAdvancements} - the per-island list of earned advancements.
 */
class IslandAdvancementsTest {

    private IslandAdvancements ia;

    @BeforeEach
    void setUp() {
        ia = new IslandAdvancements("island-1");
    }

    @Test
    void testConstructorSetsUniqueId() {
        assertEquals("island-1", ia.getUniqueId());
    }

    @Test
    void testSetUniqueId() {
        ia.setUniqueId("island-2");
        assertEquals("island-2", ia.getUniqueId());
    }

    @Test
    void testAdvancementsStartEmpty() {
        assertTrue(ia.getAdvancements().isEmpty());
    }

    @Test
    void testSetAndGetAdvancements() {
        List<String> advs = List.of("minecraft:story/mine_stone", "minecraft:story/upgrade_tools");
        ia.setAdvancements(advs);
        assertEquals(2, ia.getAdvancements().size());
        assertTrue(ia.getAdvancements().contains("minecraft:story/mine_stone"));
    }
}
