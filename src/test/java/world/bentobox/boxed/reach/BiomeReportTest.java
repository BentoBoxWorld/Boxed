package world.bentobox.boxed.reach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.boxed.reach.LandSurvey.Kind;

/**
 * Tests the list of biomes a box has and lacks.
 */
class BiomeReportTest {

    private static final String RULES = """
            provides:
              - biome:swamp|mangrove_swamp -> entity:frog
              - biome:forest -> entity:wolf
              - biome:jungle* -> entity:parrot
              - nether-biome:basalt_deltas -> nether-entity:magma_cube
              - block:bee_nest -> entity:bee
            sets:
              all-biomes:
                - biome:forest
                - biome:desert
                - biome:badlands
            advancements:
              adventure/adventuring_time:
                - set:all-biomes
              adventure/play_jukebox_in_meadows:
                - biome:meadow
                - block:diamond_ore
              husbandry/plant_seed:
                - biome:plains || block:wheat
              nether/explore_nether:
                - nether-biome:nether_wastes
                - nether-biome:warped_forest
            """;

    private Requirements requirements;
    private LandSurvey survey;

    @BeforeEach
    void setUp() throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(RULES);
        List<String> errors = new ArrayList<>();
        requirements = new Requirements(config, errors::add);
        assertTrue(errors.isEmpty(), errors.toString());
        survey = new LandSurvey(320);
        survey.add(Kind.BIOME, "forest", 1, 500);
        survey.add(Kind.BIOME, "river", 40, 20);
        survey.add(Kind.BIOME, "beach", 40, 5);
        survey.add(Kind.NETHER_BIOME, "nether_wastes", 1, 100);
    }

    @Test
    void testPresentBiomesAreNearestFirst() {
        BiomeReport report = BiomeReport.of(survey, requirements, false);
        assertEquals(List.of("forest", "beach", "river"), List.copyOf(report.present().keySet()));
        assertEquals(40, report.present().get("river"));
    }

    @Test
    void testMissingBiomesSayWhatTheyAreFor() {
        List<BiomeReport.Missing> missing = BiomeReport.of(survey, requirements, false).missing();
        assertTrue(missing.contains(new BiomeReport.Missing(List.of("swamp or mangrove_swamp"), List.of("frog"))));
        assertTrue(missing.contains(new BiomeReport.Missing(List.of("jungle*"), List.of("parrot"))));
        assertTrue(missing.contains(
                new BiomeReport.Missing(List.of("meadow"), List.of("adventure/play_jukebox_in_meadows"))));
        // Biomes wanted for the same thing are listed together
        assertTrue(missing.contains(
                new BiomeReport.Missing(List.of("desert", "badlands"), List.of("adventure/adventuring_time"))));
        // Forest is there, a need that something else can meet is not a biome need, and the
        // Nether is not part of the overworld's list
        assertEquals(4, missing.size());
    }

    @Test
    void testNether() {
        BiomeReport report = BiomeReport.of(survey, requirements, true);
        assertEquals(List.of("nether_wastes"), List.copyOf(report.present().keySet()));
        assertEquals(List.of(new BiomeReport.Missing(List.of("basalt_deltas"), List.of("magma_cube")),
                new BiomeReport.Missing(List.of("warped_forest"), List.of("nether/explore_nether"))),
                report.missing());
    }

    @Test
    void testNothingMissing() {
        survey.add(Kind.NETHER_BIOME, "basalt_deltas", 10, 1);
        survey.add(Kind.NETHER_BIOME, "warped_forest", 10, 1);
        assertTrue(BiomeReport.of(survey, requirements, true).missing().isEmpty());
    }
}
