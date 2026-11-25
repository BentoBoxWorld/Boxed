package world.bentobox.boxed.areas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.World.Environment;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.bentobox.managers.IslandDeletionManager;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.CommonTestSetup;
import world.bentobox.boxed.Settings;
import world.bentobox.boxed.generators.chunks.AbstractBoxedChunkGenerator;

/**
 * Tests {@link AreaLocationStrategy}: boxes of an area go along the area's row.
 */
class AreaLocationStrategyTest extends CommonTestSetup {

    private Boxed addon;
    private AreaManager areas;
    private Area ice;

    @BeforeEach
    void setUpStrategy() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("areas:\n  ice: {}\n");
        File grid = File.createTempFile("grid", ".yml");
        grid.delete();
        grid.deleteOnExit();
        areas = new AreaManager(config, grid, 1L, s -> {});
        ice = areas.getAreaForBundle("ice");

        addon = mock(Boxed.class);
        when(addon.getAreas()).thenReturn(areas);
        Settings settings = mock(Settings.class);
        when(settings.getIslandHeight()).thenReturn(8);
        when(addon.getSettings()).thenReturn(settings);
        // No blueprints, so boxes go at the island height setting
        when(addon.getPlugin()).thenReturn(plugin);
        when(plugin.getBlueprintsManager()).thenReturn(mock(world.bentobox.bentobox.managers.BlueprintsManager.class));
        AbstractBoxedChunkGenerator gen = mock(AbstractBoxedChunkGenerator.class);
        // 20 chunks half-width = boxes 640 blocks apart
        when(gen.getSize()).thenReturn(20);
        when(addon.getChunkGenerator(Environment.NORMAL)).thenReturn(gen);

        // Nothing is there unless a test says so
        when(im.isIslandAt(any())).thenReturn(false);
        when(im.getIslandAt(any())).thenReturn(Optional.empty());
        IslandDeletionManager idm = mock(IslandDeletionManager.class);
        when(plugin.getIslandDeletionManager()).thenReturn(idm);
        when(iwm.getIslandDistance(any())).thenReturn(320);
    }

    @Test
    void testFirstBoxOfDefaultAreaIsAtTheOrigin() {
        Location loc = new AreaLocationStrategy(addon, areas.getDefaultArea()).getNextLocation(world);
        assertEquals(0, loc.getBlockX());
        assertEquals(8, loc.getBlockY());
        assertEquals(0, loc.getBlockZ());
    }

    @Test
    void testBoxesSpreadAlongTheAreasRow() {
        java.util.Set<Integer> taken = new java.util.HashSet<>();
        when(im.isIslandAt(any())).thenAnswer(inv -> taken.contains(((Location) inv.getArgument(0)).getBlockX()));
        AreaLocationStrategy strategy = new AreaLocationStrategy(addon, ice);
        int[] expectedX = { 0, 640, -640, 1280 };
        for (int x : expectedX) {
            Location loc = strategy.getNextLocation(world);
            assertEquals(x, loc.getBlockX());
            // The ice area is row 1
            assertEquals(640, loc.getBlockZ());
            taken.add(x);
        }
    }

    @Test
    void testTakenSpotsAreSkipped() {
        // The first two spots already have boxes
        when(im.isIslandAt(any())).thenAnswer(inv -> {
            Location l = inv.getArgument(0);
            return l.getBlockX() == 0 || l.getBlockX() == 640;
        });
        Location loc = new AreaLocationStrategy(addon, ice).getNextLocation(world);
        assertEquals(-640, loc.getBlockX());
        assertEquals(640, loc.getBlockZ());
    }

    @Test
    void testFreedSpotIsUsedAgain() {
        java.util.Set<Integer> taken = new java.util.HashSet<>(java.util.List.of(0, 640, -640));
        when(im.isIslandAt(any())).thenAnswer(inv -> taken.contains(((Location) inv.getArgument(0)).getBlockX()));
        AreaLocationStrategy strategy = new AreaLocationStrategy(addon, ice);
        assertEquals(1280, strategy.getNextLocation(world).getBlockX());
        // The box at 640 is deleted and cleaned up, so its record is gone
        taken.remove(640);
        assertEquals(640, strategy.getNextLocation(world).getBlockX());
    }

    @Test
    void testGivesUpWhenRowIsFull() {
        when(im.isIslandAt(any())).thenReturn(true);
        assertNull(new AreaLocationStrategy(addon, ice).getNextLocation(world));
    }

    @Test
    void testPeekIsTheNextSpot() {
        AreaLocationStrategy strategy = new AreaLocationStrategy(addon, ice);
        assertEquals(0, strategy.peekNextLocation(world).getBlockX());
        when(im.isIslandAt(any())).thenAnswer(inv -> ((Location) inv.getArgument(0)).getBlockX() == 0);
        assertEquals(640, strategy.peekNextLocation(world).getBlockX());
    }
}
