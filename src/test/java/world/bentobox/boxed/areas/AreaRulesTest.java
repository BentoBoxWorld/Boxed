package world.bentobox.boxed.areas;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.blueprints.BundleAvailability;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.CommonTestSetup;
import world.bentobox.boxed.generators.chunks.AbstractBoxedChunkGenerator;

/**
 * Tests {@link AreaRules}: closed areas, chained areas and areas waiting for a restart.
 */
class AreaRulesTest extends CommonTestSetup {

    private Boxed addon;
    private AreaManager areas;
    private AreaRules rules;
    private User user;
    private final List<Island> boxes = new ArrayList<>();

    @BeforeEach
    void setUpRules() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                areas:
                  default:
                    unlocks-next-at: 100
                  ice:
                    requires: default
                  old:
                    enabled: false
                """);
        File grid = File.createTempFile("grid", ".yml");
        grid.delete();
        grid.deleteOnExit();
        areas = new AreaManager(config, grid, 1L, s -> {});

        addon = mock(Boxed.class);
        when(addon.getAreas()).thenReturn(areas);
        when(addon.getIslands()).thenReturn(im);
        when(addon.getOverWorld()).thenReturn(world);
        // Boxes are 40 chunks apart
        when(addon.getAreaAt(anyInt()))
                .thenAnswer(inv -> areas.getAreaAtRow(AbstractBoxedChunkGenerator.rowCalc(inv.getArgument(0), 20)));
        user = mock(User.class);
        when(im.getIslands(world, user)).thenReturn(boxes);
        rules = new AreaRules(addon);
    }

    private Island addBox(int row, int size) {
        Island box = mock(Island.class);
        Location centre = mock(Location.class);
        when(centre.getBlockZ()).thenReturn(row * 640);
        when(box.getCenter()).thenReturn(centre);
        when(box.getProtectionRange()).thenReturn(size);
        when(box.getUniqueId()).thenReturn("box" + boxes.size());
        boxes.add(box);
        return box;
    }

    @Test
    void testAnyoneCanStartInAnOpenArea() throws IOException {
        assertEquals("default", rules.check(user, "default", null).id());
        // Bundles that are not areas, and no bundle at all, use the default area
        assertEquals("default", rules.check(user, "starter", null).id());
        assertEquals("default", rules.check(user, null, null).id());
        verify(user, never()).sendMessage(anyString(), any(String[].class));
    }

    @Test
    void testClosedArea() {
        IOException e = assertThrows(IOException.class, () -> rules.check(user, "old", null));
        assertEquals(AreaRules.CLOSED, e.getMessage());
    }

    @Test
    void testChainedAreaIsLockedWithoutABox() {
        IOException e = assertThrows(IOException.class, () -> rules.check(user, "ice", null));
        assertEquals(AreaRules.LOCKED, e.getMessage());
        // The player is told what to do
        verify(user).sendMessage(AreaRules.REQUIRES, "[name]", "default", "[number]", "100");
    }

    @Test
    void testChainedAreaIsLockedUntilCompleted() {
        addBox(0, 99);
        assertThrows(IOException.class, () -> rules.check(user, "ice", null));
    }

    @Test
    void testChainedAreaUnlocksWhenCompleted() throws IOException {
        addBox(0, 100);
        assertEquals("ice", rules.check(user, "ice", null).id());
    }

    @Test
    void testABigBoxInAnotherAreaDoesNotCount() {
        // Row 2 is the 'old' area, not the required default area
        addBox(2, 300);
        assertThrows(IOException.class, () -> rules.check(user, "ice", null));
    }

    @Test
    void testOneBoxPerArea() {
        addBox(0, 5);
        IOException e = assertThrows(IOException.class, () -> rules.check(user, "default", null));
        assertEquals(AreaRules.ALREADY_HAVE, e.getMessage());
    }

    @Test
    void testResetInTheSameAreaIsAllowed() throws IOException {
        Island old = addBox(0, 5);
        assertEquals("default", rules.check(user, "default", old).id());
    }

    @Test
    void testResetDoesNotAllowASecondBox() {
        // The player has boxes in the default and ice areas and resets the ice one into default
        addBox(0, 100);
        Island ice = addBox(1, 5);
        IOException e = assertThrows(IOException.class, () -> rules.check(user, "default", ice));
        assertEquals(AreaRules.ALREADY_HAVE, e.getMessage());
    }

    @Test
    void testUnlockSizeOfZeroOnlyNeedsABox() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("areas:\n  second:\n    requires: default\n");
        File grid = File.createTempFile("grid", ".yml");
        grid.delete();
        grid.deleteOnExit();
        areas = new AreaManager(config, grid, 1L, s -> {});
        when(addon.getAreas()).thenReturn(areas);
        // No box in the default area yet
        assertThrows(IOException.class, () -> rules.check(user, "second", null));
        verify(user).sendMessage(AreaRules.REQUIRES_BOX, "[name]", "default", "[number]", "0");
        // Any box there will do
        addBox(0, 1);
        assertEquals("second", rules.check(user, "second", null).id());
    }

    @Test
    void testAvailabilityForThePanel() {
        when(user.getTranslation(anyString(), any(String[].class))).thenAnswer(inv -> "translated " + inv.getArgument(0));
        // Open area
        assertTrue(rules.availability(user, "default", null).isAvailable());
        // Not unlocked: shown with the reason, but locked
        BundleAvailability ice = rules.availability(user, "ice", null);
        assertEquals(BundleAvailability.State.LOCKED, ice.state());
        assertEquals("translated " + AreaRules.REQUIRES, ice.message());
        verify(user).getTranslation(AreaRules.REQUIRES, "[name]", "default", "[number]", "100");
        // Closed and not-yet-built areas are not shown at all
        assertEquals(BundleAvailability.State.HIDDEN, rules.availability(user, "old", null).state());
        areas.addPending("fresh");
        assertEquals(BundleAvailability.State.HIDDEN, rules.availability(user, "fresh", null).state());
        // Already have a box there: locked with that reason
        Island box = addBox(0, 100);
        BundleAvailability mine = rules.availability(user, "default", null);
        assertEquals(BundleAvailability.State.LOCKED, mine.state());
        assertEquals("translated " + AreaRules.ALREADY_HAVE, mine.message());
        // ...unless that box is the one being reset
        assertTrue(rules.availability(user, "default", box).isAvailable());
        // And the next area is now unlocked
        assertTrue(rules.availability(user, "ice", null).isAvailable());
        // Asking does not message the player
        verify(user, never()).sendMessage(anyString(), any(String[].class));
    }

    @Test
    void testNewAreaWaitsForRestart() {
        areas.addPending("fresh");
        IOException e = assertThrows(IOException.class, () -> rules.check(user, "fresh", null));
        assertEquals(AreaRules.NOT_READY, e.getMessage());
    }
}
