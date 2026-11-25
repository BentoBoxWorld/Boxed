package world.bentobox.boxed.areas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.World.Environment;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import world.bentobox.boxed.generators.chunks.AbstractBoxedChunkGenerator;
import world.bentobox.boxed.generators.template.Template;

/**
 * Tests {@link AreaManager} and {@link Area}.
 */
class AreaManagerTest {

    private static final long SEED = 42L;

    @TempDir
    Path dir;

    private File gridFile;
    private final List<String> errors = new ArrayList<>();

    @BeforeEach
    void setUp() {
        gridFile = dir.resolve("areas/grid.yml").toFile();
    }

    private AreaManager manager(String yaml) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return new AreaManager(config, gridFile, SEED, errors::add);
    }

    @Test
    void testDefaultAreaAlwaysExists() throws Exception {
        AreaManager am = manager("");
        assertEquals(1, am.getAreas().size());
        Area area = am.getDefaultArea();
        assertEquals("default", area.id());
        assertTrue(area.isDefault());
        assertEquals(SEED, area.seed());
        assertEquals("biomes.yml", area.biomes());
        assertEquals("structures.yml", area.structures());
        assertEquals(0, area.row());
        assertTrue(errors.isEmpty());
    }

    @Test
    void testAreasGetRowsAndSettings() throws Exception {
        AreaManager am = manager("""
                areas:
                  icelands:
                    seed: 99
                    biomes: biomes-ice.yml
                    structures: structures-ice.yml
                  default:
                    biomes: biomes.yml
                  desert: {}
                """);
        assertEquals(List.of("default", "icelands", "desert"), am.getAreas().stream().map(Area::id).toList());
        // The default area is always row 0, whatever the order in the file
        assertEquals(0, am.getDefaultArea().row());
        Area ice = am.getAreaForBundle("icelands");
        assertEquals(1, ice.row());
        assertEquals(99L, ice.seed());
        assertEquals("biomes-ice.yml", ice.biomes());
        assertEquals("structures-ice.yml", ice.structures());
        Area desert = am.getAreaForBundle("desert");
        assertEquals(2, desert.row());
        assertEquals(SEED, desert.seed());
        assertSame(ice, am.getAreaAtRow(1));
        assertSame(desert, am.getAreaAtRow(2));
        // Rows nobody owns, and unknown bundles, are the default area
        assertSame(am.getDefaultArea(), am.getAreaAtRow(3));
        assertSame(am.getDefaultArea(), am.getAreaAtRow(-1));
        assertSame(am.getDefaultArea(), am.getAreaForBundle("nope"));
        assertSame(am.getDefaultArea(), am.getAreaForBundle(null));
    }

    @Test
    void testRulesAreRead() throws Exception {
        AreaManager am = manager("""
                areas:
                  default:
                    unlocks-next-at: 150
                  next:
                    requires: default
                  gone:
                    enabled: false
                  bad:
                    requires: nowhere
                  self:
                    requires: self
                """);
        assertEquals(150, am.getDefaultArea().unlockSize());
        assertTrue(am.getDefaultArea().enabled());
        assertNull(am.getDefaultArea().requires());
        assertEquals("default", am.getArea("next").requires());
        assertEquals(0, am.getArea("next").unlockSize());
        assertTrue(!am.getArea("gone").enabled());
        // Requirements that make no sense are dropped and reported
        assertNull(am.getArea("bad").requires());
        assertNull(am.getArea("self").requires());
        assertEquals(2, errors.size());
        assertNull(am.getArea("nope"));
        assertNull(am.getArea(null));
    }

    @Test
    void testPendingAndNames() throws Exception {
        AreaManager am = manager("");
        assertTrue(!am.isPending("fresh"));
        assertTrue(!am.isPending(null));
        am.addPending("fresh");
        assertTrue(am.isPending("fresh"));
        assertTrue(AreaManager.isValidName("ice_2"));
        assertTrue(!AreaManager.isValidName("Ice Lands"));
        assertTrue(!AreaManager.isValidName(""));
    }

    @Test
    void testRowsNeverChange() throws Exception {
        manager("areas:\n  a: {}\n  b: {}\n");
        // 'a' is removed and 'c' is added before 'b'. 'b' keeps its row and 'a's row is not reused.
        AreaManager am = manager("areas:\n  c: {}\n  b: {}\n");
        assertEquals(2, am.getAreaForBundle("b").row());
        assertEquals(3, am.getAreaForBundle("c").row());
        assertSame(am.getDefaultArea(), am.getAreaAtRow(1));
    }

    @Test
    void testInvalidNamesAreIgnored() throws Exception {
        AreaManager am = manager("areas:\n  'Bad Name': {}\n  good_1: {}\n");
        assertEquals(List.of("default", "good_1"), am.getAreas().stream().map(Area::id).toList());
        assertEquals(1, errors.size());
    }

    @Test
    void testColumnsSpreadFromTheMiddle() {
        assertEquals(0, AreaManager.column(0));
        assertEquals(1, AreaManager.column(1));
        assertEquals(-1, AreaManager.column(2));
        assertEquals(2, AreaManager.column(3));
        assertEquals(-2, AreaManager.column(4));
    }

    @Test
    void testTemplates() throws Exception {
        AreaManager am = manager("areas:\n  a: {}\n");
        Area a = am.getAreaForBundle("a");
        Template t1 = mock(Template.class);
        Template t2 = mock(Template.class);
        assertNull(am.getTemplate(a, Environment.NORMAL));
        assertNull(am.setTemplate(a, Environment.NORMAL, t1));
        assertSame(t1, am.getTemplate(a, Environment.NORMAL));
        assertNull(am.getTemplate(a, Environment.NETHER));
        assertNull(am.getTemplate(am.getDefaultArea(), Environment.NORMAL));
        assertSame(t1, am.setTemplate(a, Environment.NORMAL, t2));
        assertEquals(List.of(t2), am.removeTemplates());
        assertNull(am.getTemplate(a, Environment.NORMAL));
    }

    @Test
    void testSeedWorldNames() throws Exception {
        AreaManager am = manager("areas:\n  ice: {}\n");
        assertEquals("boxed_world/seed", am.getDefaultArea().seedWorldName("boxed_world", false));
        assertEquals("boxed_world/seed_nether", am.getDefaultArea().seedWorldName("boxed_world", true));
        assertEquals("boxed_world/seed_ice", am.getAreaForBundle("ice").seedWorldName("boxed_world", false));
        assertEquals("boxed_world/seed_ice_nether", am.getAreaForBundle("ice").seedWorldName("boxed_world", true));
    }

    @Test
    void testRowCalc() {
        // A box is 40 chunks wide, centred on chunk 0, so row 0 is chunks -20 to 19
        assertEquals(0, AbstractBoxedChunkGenerator.rowCalc(-20, 20));
        assertEquals(0, AbstractBoxedChunkGenerator.rowCalc(19, 20));
        assertEquals(1, AbstractBoxedChunkGenerator.rowCalc(20, 20));
        assertEquals(1, AbstractBoxedChunkGenerator.rowCalc(59, 20));
        assertEquals(-1, AbstractBoxedChunkGenerator.rowCalc(-21, 20));
    }
}
