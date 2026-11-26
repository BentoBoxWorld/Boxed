package world.bentobox.boxed.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.bukkit.World.Environment;
import org.bukkit.WorldBorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.CommonTestSetup;
import world.bentobox.boxed.workshop.Workshop;
import world.bentobox.boxed.workshop.WorkshopManager;

/**
 * Tests listing and picking the places the workshop has found.
 */
class AdminAreasSpotsCommandTest extends CommonTestSetup {

    private static final String PREFIX = "boxed.commands.boxadmin.areas.spots.";

    private AdminAreasSpotsCommand cmd;
    private WorkshopManager manager;
    private Workshop workshop;
    private User user;

    @BeforeEach
    void setUpCommand() {
        Boxed addon = mock(Boxed.class);
        manager = mock(WorkshopManager.class);
        when(addon.getWorkshop()).thenReturn(manager);
        workshop = new Workshop(5L, "biomes.yml", null);
        workshop.setSpots(List.of(new Workshop.Spot(0, 0, "plains", 90, "dry land"),
                new Workshop.Spot(1280, -640, "forest", 98, "wood at size 2"),
                new Workshop.Spot(-2560, 64, "taiga", -1, "")));
        when(manager.get()).thenReturn(Optional.of(workshop));

        CompositeCommand parent = mock(CompositeCommand.class);
        when(parent.getAddon()).thenReturn(addon);
        when(parent.getTopLabel()).thenReturn("boxadmin");
        when(parent.getPermissionPrefix()).thenReturn("");
        when(parent.getWorld()).thenReturn(world);
        when(parent.getSubCommands()).thenReturn(new HashMap<>());
        cmd = new AdminAreasSpotsCommand(parent);

        user = mock(User.class);
        when(user.isPlayer()).thenReturn(true);
        when(user.getPlayer()).thenReturn(mockPlayer);
    }

    @Test
    void testSetup() {
        assertEquals("spots", cmd.getLabel());
        assertEquals("admin.areas.spots", cmd.getPermission());
        assertFalse(cmd.isOnlyPlayer());
    }

    @Test
    void testNoWorkshop() {
        when(manager.get()).thenReturn(Optional.empty());
        assertFalse(cmd.execute(user, "spots", List.of()));
        verify(user).sendMessage("boxed.commands.boxadmin.areas.edit.none", "[label]", "boxadmin");
    }

    @Test
    void testNothingFoundYet() {
        workshop.setSpots(List.of(new Workshop.Spot(0, 0, "plains", -1, "")));
        assertTrue(cmd.execute(user, "spots", List.of()));
        verify(user).sendMessage(PREFIX + "looking");
    }

    @Test
    void testConsoleGetsAList() {
        when(user.isPlayer()).thenReturn(false);
        workshop.setCentre(Environment.NORMAL, 1280, -640);
        assertTrue(cmd.execute(user, "spots", List.of()));
        verify(user).sendMessage(PREFIX + "entry", "[number]", "1", "[xyz]", "0,0", "[score]", "90", "[biome]",
                "plains", "[description]", "dry land");
        verify(user).sendMessage(PREFIX + "entry-picked", "[number]", "2", "[xyz]", "1280,-640", "[score]", "98",
                "[biome]", "forest", "[description]", "wood at size 2");
        verify(user).sendMessage(PREFIX + "more-coming", "[number]", "1");
    }

    @Test
    void testConsoleCannotPick() {
        when(user.isPlayer()).thenReturn(false);
        assertFalse(cmd.execute(user, "spots", List.of("1")));
        verify(user).sendMessage("general.errors.use-in-game");
        verify(manager, never()).select(any(), anyInt());
    }

    @Test
    void testPick() {
        when(manager.select(mockPlayer, 1)).thenReturn(CompletableFuture.completedFuture(true));
        assertTrue(cmd.execute(user, "spots", List.of("2")));
        verify(user).sendMessage(PREFIX + "selected", "[number]", "2", "[xyz]", "1280,-640", "[label]", "boxadmin");
    }

    @Test
    void testPickThatFails() {
        when(manager.select(mockPlayer, 0)).thenReturn(CompletableFuture.completedFuture(false));
        assertTrue(cmd.execute(user, "spots", List.of("1")));
        verify(user).sendMessage("boxed.commands.boxadmin.areas.edit.failed");
    }

    @Test
    void testPickUnknownOrUnfinished() {
        assertFalse(cmd.execute(user, "spots", List.of("0")));
        assertFalse(cmd.execute(user, "spots", List.of("4")));
        // The third place has not been looked at yet
        assertFalse(cmd.execute(user, "spots", List.of("3")));
        verify(user, times(3)).sendMessage(PREFIX + "unknown");
        verify(manager, never()).select(any(), anyInt());
    }

    @Test
    void testBorderOnAndOff() {
        // No border yet, so it is shown
        WorldBorder shown = mock(WorldBorder.class);
        doAnswer(i -> when(mockPlayer.getWorldBorder()).thenReturn(shown))
                .when(manager).showCentre(mockPlayer, true);
        assertTrue(cmd.execute(user, "spots", List.of("border")));
        verify(user).sendMessage(PREFIX + "border-on");
        // Now there is one, so it is hidden
        doAnswer(i -> when(mockPlayer.getWorldBorder()).thenReturn(null)).when(manager)
                .showCentre(mockPlayer, false);
        assertTrue(cmd.execute(user, "spots", List.of("BORDER")));
        verify(user).sendMessage(PREFIX + "border-off");
    }

    @Test
    void testBorderNeedsAPlace() {
        assertFalse(cmd.execute(user, "spots", List.of("border")));
        verify(manager).showCentre(mockPlayer, true);
        verify(user).sendMessage(PREFIX + "no-centre");
    }

    @Test
    void testTabComplete() {
        assertEquals(List.of("border", "1", "2"), cmd.tabComplete(user, "spots", List.of("spots", "")).orElseThrow());
        assertEquals(List.of("2"), cmd.tabComplete(user, "spots", List.of("spots", "2")).orElseThrow());
    }
}
