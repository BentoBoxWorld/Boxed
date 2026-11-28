package world.bentobox.boxed.reach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.boxed.reach.GrowthSimulator.Blocked;
import world.bentobox.boxed.reach.GrowthSimulator.Earned;
import world.bentobox.boxed.reach.GrowthSimulator.Result;
import world.bentobox.boxed.reach.LandSurvey.Kind;
import world.bentobox.boxed.reach.Requirements.Alternative;
import world.bentobox.boxed.reach.Requirements.Type;

/**
 * Tests the land survey, the requirements file and the growth simulation together.
 */
class GrowthSimulatorTest {

    private static final String YAML = """
            always:
              - zombie
            provides:
              - structure:village/*/villagers/* -> entity:villager
              - nether-biome:nether_wastes -> nether-entity:ghast
            sets:
              wood:
                - block:*_log
              iron:
                - block:iron_ore|deepslate_iron_ore*3
            advancements:
              story/mine_stone:
                - set:wood
                - block:stone
              story/smelt_iron:
                - after:story/mine_stone
                - set:iron
              story/enter_the_nether:
                - after:story/smelt_iron
                - block:lava || structure:ruined_portal/*
              nether/return_to_sender:
                - nether-entity:ghast
              adventure/trade:
                - entity:villager
              adventure/sniper_duel:
                - size:25
              story/mine_diamond:
                - block:diamond_ore
              end/kill_dragon:
                - never:there is no End
              end/dragon_egg:
                - after:end/kill_dragon
              adventure/kill_a_mob: []
            """;

    private LandSurvey survey;
    private Requirements requirements;
    private List<String> errors;

    @BeforeEach
    void setUp() throws Exception {
        errors = new ArrayList<>();
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(YAML);
        requirements = new Requirements(config, errors::add);
        survey = new LandSurvey(100);
        // A tree and stone at the start, iron a little further out, lava further still
        survey.add(Kind.BLOCK, "oak_log", 1, 4);
        survey.add(Kind.BLOCK, "stone", 1, 50);
        survey.add(Kind.BLOCK, "iron_ore", 3, 2);
        survey.add(Kind.BLOCK, "deepslate_iron_ore", 5, 1);
        survey.add(Kind.BLOCK, "lava", 12, 1);
        survey.add(Kind.NETHER_BIOME, "nether_wastes", 2, 1);
        survey.add(Kind.STRUCTURE, "village/plains/villagers/unemployed", 40, 1);
        // Diamonds are beyond the edge of the land, so they are not recorded
        survey.add(Kind.BLOCK, "diamond_ore", 150, 1);
        requirements.applyTo(survey);
    }

    @Test
    void testSizeFor() {
        // A box of size 1 covers blocks -1 and 0
        assertEquals(1, LandSurvey.sizeFor(0, 0));
        assertEquals(1, LandSurvey.sizeFor(-1, -1));
        assertEquals(2, LandSurvey.sizeFor(1, 0));
        assertEquals(2, LandSurvey.sizeFor(0, -2));
        assertEquals(10, LandSurvey.sizeFor(-3, 9));
    }

    @Test
    void testSurveyCountsAndWildcards() {
        assertEquals(2, survey.count(Kind.BLOCK, "iron_ore|deepslate_iron_ore", 4));
        assertEquals(3, survey.count(Kind.BLOCK, "*iron_ore", 5));
        assertEquals(5, survey.sizeNeeded(Kind.BLOCK, "iron_ore|deepslate_iron_ore", 3));
        assertEquals(-1, survey.sizeNeeded(Kind.BLOCK, "iron_ore", 3));
        assertEquals(-1, survey.sizeNeeded(Kind.BLOCK, "diamond_ore", 1));
        assertEquals(1, survey.firstSizes(Kind.BLOCK).get("oak_log"));
    }

    @Test
    void testRulesAreApplied() {
        // Always around, and brought by a structure or a biome
        assertEquals(1, survey.sizeNeeded(Kind.ENTITY, "zombie", 1));
        assertEquals(40, survey.sizeNeeded(Kind.ENTITY, "villager", 1));
        assertEquals(2, survey.sizeNeeded(Kind.NETHER_ENTITY, "ghast", 1));
    }

    @Test
    void testBlueprintBlocksAreCounted() {
        LandSurvey s = new LandSurvey(10);
        s.addBlueprint(Map.of(new Vector(0, 60, -1), "minecraft:oak_log[axis=y]", new Vector(1, 61, 0), "minecraft:air"));
        assertEquals(1, s.sizeNeeded(Kind.BLOCK, "oak_log", 1));
        assertEquals(1, s.firstSizes(Kind.BLOCK).size());
    }

    @Test
    void testStructuresFile() throws Exception {
        YamlConfiguration structures = new YamlConfiguration();
        structures.loadFromString("normal:\n  -38,63,20: ruined_portal/portal_5\n  0,64,80: village/x,CLOCKWISE_90\n");
        LandSurvey s = new LandSurvey(100);
        s.addStructures(structures.getConfigurationSection("normal"), false);
        s.addStructures(null, true);
        assertEquals(38, s.sizeNeeded(Kind.STRUCTURE, "ruined_portal/*", 1));
        assertEquals(81, s.sizeNeeded(Kind.STRUCTURE, "village/x", 1));
    }

    @Test
    void testRequirementsAreRead() {
        assertTrue(errors.isEmpty(), errors.toString());
        assertEquals(2, requirements.getNeeds("minecraft:story/mine_stone").size());
        assertEquals(new Alternative(Type.RESOURCE, Kind.BLOCK, "iron_ore|deepslate_iron_ore", 3),
                requirements.getNeeds("minecraft:story/smelt_iron").get(1).alternatives().get(0));
        assertEquals(2, requirements.getNeeds("minecraft:story/enter_the_nether").get(1).alternatives().size());
        assertTrue(requirements.getNeeds("minecraft:adventure/kill_a_mob").isEmpty());
        assertNull(requirements.getNeeds("minecraft:story/unknown"));
    }

    @Test
    void testBadLinesAreReported() {
        assertNull(Requirements.alternative("nonsense", errors::add));
        assertNull(Requirements.alternative("rock:stone", errors::add));
        assertNull(Requirements.alternative("size:big", errors::add));
        assertEquals(3, errors.size());
    }

    @Test
    void testGrowth() {
        Map<String, Integer> scores = Map.of("minecraft:story/mine_stone", 2, "minecraft:story/smelt_iron", 3,
                "minecraft:story/enter_the_nether", 6, "minecraft:nether/return_to_sender", 1,
                "minecraft:adventure/trade", 8, "minecraft:adventure/sniper_duel", 5,
                "minecraft:story/mine_diamond", 6, "minecraft:end/kill_dragon", 2, "minecraft:end/dragon_egg", 2,
                "minecraft:story/not_in_file", 4);
        // kill_a_mob scores nothing here, so it is left out
        Result result = new GrowthSimulator(survey, requirements, scores, 1).run();

        // 1 -> mine_stone -> 3: only two iron in reach, so the box stops there
        assertEquals(List.of("minecraft:story/mine_stone"), result.earned().stream().map(Earned::advancement).toList());
        assertEquals(3, result.finalSize());
        assertFalse(result.filledLand());
        assertEquals(List.of("minecraft:story/not_in_file"), result.unlisted());

        Map<String, Blocked> blocked = new java.util.HashMap<>();
        result.blocked().forEach(b -> blocked.put(b.advancement(), b));
        // Iron is there, just further out
        assertFalse(blocked.get("minecraft:story/smelt_iron").impossible());
        assertTrue(blocked.get("minecraft:story/smelt_iron").missing().get(0).contains("first at size 5"));
        assertFalse(blocked.get("minecraft:adventure/sniper_duel").impossible());
        // Diamonds are not in the land, and the End does not exist, nor does anything after it
        assertTrue(blocked.get("minecraft:story/mine_diamond").impossible());
        assertTrue(blocked.get("minecraft:end/kill_dragon").impossible());
        assertTrue(blocked.get("minecraft:end/dragon_egg").impossible());
        assertTrue(ReachReport.summary(result).startsWith("The box stops growing at size 3 of 100."));
    }

    @Test
    void testGrowthWithEnoughIron() {
        survey.add(Kind.BLOCK, "iron_ore", 2, 1);
        Map<String, Integer> scores = Map.of("minecraft:story/mine_stone", 2, "minecraft:story/smelt_iron", 10,
                "minecraft:story/enter_the_nether", 30, "minecraft:nether/return_to_sender", 1,
                "minecraft:adventure/trade", 60, "minecraft:adventure/sniper_duel", 5);
        Result result = new GrowthSimulator(survey, requirements, scores, 1).run();
        // 1 -> 3 -> 13 (lava at 12) -> 43 (ghast, villager at 40, size 25) -> past the edge of the land
        assertEquals(6, result.earned().size());
        assertEquals(100, result.finalSize());
        assertTrue(result.filledLand());
        assertTrue(result.blocked().isEmpty());
        // The nether resource only counted once the nether was open
        assertEquals(4, result.earned().stream().filter(e -> e.advancement().endsWith("return_to_sender")).findFirst()
                .orElseThrow().round());
        List<String> report = ReachReport.lines("test", survey, result);
        assertTrue(report.contains("Round 4, box size 43:"));
        assertTrue(report.stream().anyMatch(l -> l.contains("entity: box size at which each first appears")));
    }

    /**
     * The requirements file that ships with Boxed can be read without complaints.
     */
    @Test
    void testShippedRequirementsFile() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new java.io.File("src/main/resources/requirements.yml"));
        List<String> problems = new ArrayList<>();
        Requirements shipped = new Requirements(config, problems::add);
        assertTrue(problems.isEmpty(), problems.toString());
        // Every advancement that scores has an entry
        YamlConfiguration adv = YamlConfiguration.loadConfiguration(new java.io.File("src/main/resources/advancements.yml"));
        for (String key : adv.getConfigurationSection("advancements").getKeys(false)) {
            if (adv.getInt("advancements." + key) > 0) {
                assertNotNull(shipped.getNeeds("minecraft:" + key), key + " is missing from requirements.yml");
            }
        }
    }

    @Test
    void testMobsNeedRoom() {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString("""
                    always:
                      zombie: 16
                      bat: 0
                    advancements:
                      adventure/kill_a_mob:
                        - entity:zombie
                    """);
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            throw new IllegalStateException(e);
        }
        Requirements requirements = new Requirements(config, s -> {});
        assertEquals(java.util.Map.of("zombie", 16, "bat", 1), requirements.getAlways());
        LandSurvey survey = new LandSurvey(48);
        requirements.applyTo(survey);
        // No zombie until the box has room for one
        assertEquals(16, survey.sizeNeeded(Kind.ENTITY, "zombie", 1));
        assertEquals(1, survey.sizeNeeded(Kind.ENTITY, "bat", 1));
        // A box of 1 that can earn nothing else never gets there
        GrowthSimulator.Result stuck = new GrowthSimulator(survey, requirements,
                java.util.Map.of("minecraft:adventure/kill_a_mob", 5), 1).run();
        assertTrue(stuck.earned().isEmpty());
        // One that starts big enough does
        GrowthSimulator.Result roomy = new GrowthSimulator(survey, requirements,
                java.util.Map.of("minecraft:adventure/kill_a_mob", 5), 16).run();
        assertEquals(1, roomy.earned().size());
    }
}
