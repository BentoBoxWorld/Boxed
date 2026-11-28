package world.bentobox.boxed.reach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.bentobox.blueprints.Blueprint;
import world.bentobox.bentobox.blueprints.dataobjects.BlueprintBlock;
import world.bentobox.boxed.reach.LandSurvey.Kind;

/**
 * Tests working out what a starter blueprint gives a new box.
 */
class StarterBountyTest {

    private Blueprint blueprint;
    private final Vector sign = new Vector(0, 60, 1);

    private static ItemStack item(String name, int amount) {
        ItemStack item = mock(ItemStack.class);
        Material type = mock(Material.class);
        when(type.getKey()).thenReturn(NamespacedKey.minecraft(name));
        when(item.getType()).thenReturn(type);
        when(item.getAmount()).thenReturn(amount);
        return item;
    }

    @BeforeEach
    void setUp() {
        blueprint = new Blueprint();
        blueprint.setBedrock(new Vector(0, 0, 0));
        Map<Vector, BlueprintBlock> blocks = new HashMap<>();
        // A tree by the sign, ore a long way down, and air that does not count
        blocks.put(new Vector(0, 61, 0), new BlueprintBlock("minecraft:jungle_log[axis=y]"));
        blocks.put(new Vector(0, 62, 0), new BlueprintBlock("minecraft:jungle_log[axis=y]"));
        blocks.put(new Vector(3, 63, -2), new BlueprintBlock("minecraft:jungle_leaves"));
        blocks.put(new Vector(0, 10, 0), new BlueprintBlock("minecraft:iron_ore"));
        blocks.put(new Vector(0, 40, 0), new BlueprintBlock("minecraft:stone"));
        blocks.put(new Vector(1, 61, 0), new BlueprintBlock("minecraft:air"));
        BlueprintBlock chest = new BlueprintBlock("minecraft:chest[facing=north]");
        chest.setInventory(Map.of(0, item("torch", 12), 1, item("coal", 2), 2, item("torch", 2)));
        blocks.put(new Vector(1, 60, 1), chest);
        blueprint.setBlocks(blocks);
        blueprint.setAttached(Map.of(sign, new BlueprintBlock("minecraft:oak_sign[rotation=0]")));
    }

    @Test
    void testNearTheSignLeavesOutWhatIsDeepDown() {
        StarterBounty bounty = StarterBounty.of(blueprint, sign, StarterBounty.NEAR_SIGN);
        Map<String, Integer> totals = bounty.totals();
        assertEquals(2, totals.get("jungle_log"));
        // Forty blocks up is exactly twenty below the sign
        assertEquals(1, totals.get("stone"));
        assertFalse(totals.containsKey("iron_ore"));
        assertFalse(totals.containsKey("air"));
        assertEquals(14, totals.get("torch"));
        assertEquals(Map.of("torch", 14, "coal", 2), bounty.chest());
        assertTrue(bounty.hasWood());
    }

    @Test
    void testTheWholeBlueprintCountsEverything() {
        StarterBounty bounty = StarterBounty.of(blueprint, sign, StarterBounty.WHOLE);
        assertEquals(1, bounty.totals().get("iron_ore"));
        // With no sign there is nothing to measure from, so everything counts
        assertEquals(1, StarterBounty.of(blueprint, null, StarterBounty.NEAR_SIGN).totals().get("iron_ore"));
    }

    @Test
    void testThingsAreInTheBoxThatReachesThem() {
        LandSurvey survey = new LandSurvey(48);
        StarterBounty.of(blueprint, sign, StarterBounty.NEAR_SIGN).addTo(survey);
        assertEquals(1, survey.sizeNeeded(Kind.BLOCK, "jungle_log", 2));
        // The leaves are three blocks out
        assertEquals(LandSurvey.sizeFor(3, -2), survey.sizeNeeded(Kind.BLOCK, "jungle_leaves", 1));
        // What is in the chest counts by its name, as many as there are
        assertEquals(LandSurvey.sizeFor(1, 1), survey.sizeNeeded(Kind.BLOCK, "torch", 14));
        assertEquals(-1, survey.sizeNeeded(Kind.BLOCK, "torch", 15));
    }

    @Test
    void testNoBlueprintGivesNothing() {
        assertTrue(StarterBounty.of(null, null, StarterBounty.WHOLE).isEmpty());
        assertTrue(StarterBounty.NONE.totals().isEmpty());
        assertFalse(StarterBounty.NONE.hasWood());
    }

    @Test
    void testASaplingIsWood() {
        Blueprint bare = new Blueprint();
        BlueprintBlock chest = new BlueprintBlock("minecraft:chest");
        chest.setInventory(Map.of(0, item("oak_sapling", 1)));
        bare.setBlocks(Map.of(new Vector(0, 0, 0), chest));
        assertTrue(StarterBounty.of(bare, null, StarterBounty.WHOLE).hasWood());
        chest.setInventory(Map.of(0, item("stick", 1)));
        assertFalse(StarterBounty.of(bare, null, StarterBounty.WHOLE).hasWood());
    }

    @Test
    void testAPlaceNeedsNoTreesWhenTheBlueprintHasOne() {
        // Bare land: nothing was counted at all
        SpotSurvey land = new SpotSurvey();
        assertEquals(-1, land.score().wood());
        SpotSurvey.Score score = land.score(StarterBounty.of(blueprint, sign, StarterBounty.NEAR_SIGN));
        // Two logs are not the four a start needs
        assertEquals(-1, score.wood());
        Map<Vector, BlueprintBlock> more = new HashMap<>(blueprint.getBlocks());
        more.put(new Vector(0, 63, 0), new BlueprintBlock("minecraft:jungle_log"));
        more.put(new Vector(0, 64, 0), new BlueprintBlock("minecraft:jungle_log"));
        blueprint.setBlocks(more);
        score = land.score(StarterBounty.of(blueprint, sign, StarterBounty.NEAR_SIGN));
        assertEquals(1, score.wood());
        assertTrue(score.score() > land.score().score());
    }
}
