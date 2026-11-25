package world.bentobox.boxed.areas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.World.Environment;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.bentobox.blueprints.Blueprint;
import world.bentobox.bentobox.blueprints.dataobjects.BlueprintBlock;
import world.bentobox.bentobox.blueprints.dataobjects.BlueprintBundle;
import world.bentobox.bentobox.managers.BlueprintsManager;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.CommonTestSetup;
import world.bentobox.boxed.Settings;
import world.bentobox.boxed.generators.template.Template;
import world.bentobox.boxed.generators.template.TemplateChunk;

/**
 * Tests {@link BoxHeight}: a new box is put at the height that lands the starter blueprint's
 * spawn sign on the area's ground.
 */
class BoxHeightTest extends CommonTestSetup {

    private Boxed addon;
    private AreaManager areas;
    private Area ice;
    private Blueprint bp;
    private Template template;

    private static BlockData data(Material material) {
        BlockData bd = mock(BlockData.class);
        when(bd.getMaterial()).thenReturn(material);
        return bd;
    }

    /**
     * A chunk from y=0 to y=95 where every column is stone up to 62, grass at 63 and an oak
     * tree (log at 64, leaves at 65) on top.
     */
    private static TemplateChunk chunk() {
        BlockData[] palette = { data(Material.AIR), data(Material.STONE), data(Material.GRASS_BLOCK),
                data(Material.OAK_LOG), data(Material.OAK_LEAVES) };
        short[][] sections = new short[6][];
        int[] uniform = new int[6];
        // Sections 0 to 2 are stone, 4 is air, 5 is air
        uniform[0] = uniform[1] = uniform[2] = 1;
        short[] top = new short[4096];
        for (int i = 0; i < top.length; i++) {
            int y = i >> 8;
            top[i] = (short) (y < 15 ? 1 : 2);
        }
        sections[3] = top;
        short[] tree = new short[4096];
        for (int i = 0; i < tree.length; i++) {
            int y = i >> 8;
            tree[i] = (short) (y == 0 ? 3 : y == 1 ? 4 : 0);
        }
        sections[4] = tree;
        return new TemplateChunk(0, palette, sections, uniform, List.of(), List.of());
    }

    @BeforeEach
    void setUpHeight() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("areas:\n  ice: {}\n");
        File grid = File.createTempFile("grid", ".yml");
        grid.delete();
        grid.deleteOnExit();
        areas = new AreaManager(config, grid, 1L, s -> {});
        ice = areas.getAreaForBundle("ice");

        addon = mock(Boxed.class);
        when(addon.getAreas()).thenReturn(areas);
        when(addon.getPlugin()).thenReturn(plugin);
        when(addon.getOverWorld()).thenReturn(world);
        when(world.getMinHeight()).thenReturn(-64);
        Settings settings = mock(Settings.class);
        when(settings.getIslandHeight()).thenReturn(8);
        when(addon.getSettings()).thenReturn(settings);

        // A blueprint with bedrock at 2,0,3 and the spawn sign 60 blocks above and one north of it
        bp = mock(Blueprint.class);
        when(bp.getBedrock()).thenReturn(new Vector(2, 0, 3));
        BlueprintBlock sign = mock(BlueprintBlock.class);
        when(sign.getSignLines(Side.FRONT)).thenReturn(List.of("[spawn_here]", "", "", ""));
        BlueprintBlock other = mock(BlueprintBlock.class);
        when(other.getSignLines(Side.FRONT)).thenReturn(List.of());
        Map<Vector, BlueprintBlock> attached = new LinkedHashMap<>();
        attached.put(new Vector(1, 1, 1), other);
        attached.put(new Vector(2, 60, 2), sign);
        when(bp.getAttached()).thenReturn(attached);
        when(bp.getBlocks()).thenReturn(Map.of());

        BlueprintsManager bpm = mock(BlueprintsManager.class);
        when(plugin.getBlueprintsManager()).thenReturn(bpm);
        BlueprintBundle bundle = mock(BlueprintBundle.class);
        when(bundle.getBlueprint(Environment.NORMAL)).thenReturn("island");
        when(bpm.getBlueprintBundles(addon)).thenReturn(Map.of("default-test", bundle));
        when(bpm.getBlueprints(addon)).thenReturn(Map.of("island", bp));

        template = mock(Template.class);
        TemplateChunk chunk = chunk();
        when(template.getChunk(anyInt(), anyInt())).thenReturn(chunk);
        areas.setTemplate(ice, Environment.NORMAL, template);
    }

    @Test
    void testGroundLevelIgnoresTrees() {
        assertEquals(63, chunk().getGroundLevel(4, 9));
    }

    @Test
    void testSpawnSignIsFound() {
        assertEquals(new Vector(2, 60, 2), BoxHeight.getSpawnSign(bp));
        when(bp.getAttached()).thenReturn(Map.of());
        assertNull(BoxHeight.getSpawnSign(bp));
    }

    /**
     * Ground at 63 means feet at 64, and the sign is 60 above the bedrock, so the centre is at 4.
     */
    @Test
    void testCentrePutsTheSignOnTheGround() {
        assertEquals(4, new BoxHeight(addon).get(ice));
        // The sign is one block north of the bedrock, which is in the chunk north of the centre
        verify(template).getChunk(0, -1);
    }

    @Test
    void testDefaultAreaLandMatchesTheOriginalHeight() {
        // The original land has its ground at 67, which gives the long-standing height of 8
        assertEquals(8, BoxHeight.height(67, 60));
    }

    @Test
    void testNoSignUsesTheSetting() {
        when(bp.getAttached()).thenReturn(Map.of());
        assertEquals(8, new BoxHeight(addon).get(ice));
    }

    @Test
    void testNoTemplateUsesTheSetting() {
        assertEquals(8, new BoxHeight(addon).get(areas.getDefaultArea()));
    }

    @Test
    void testCentreIsNotBelowTheWorld() {
        when(world.getMinHeight()).thenReturn(10);
        assertEquals(10, new BoxHeight(addon).get(ice));
    }
}
