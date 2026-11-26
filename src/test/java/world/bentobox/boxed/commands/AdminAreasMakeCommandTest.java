package world.bentobox.boxed.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.CommonTestSetup;
import world.bentobox.boxed.areas.Area;
import world.bentobox.boxed.areas.AreaManager;
import world.bentobox.boxed.workshop.AreaMaker;
import world.bentobox.boxed.workshop.AreaMaker.Prepared;
import world.bentobox.boxed.workshop.Workshop;
import world.bentobox.boxed.workshop.WorkshopManager;

/**
 * Tests the typed form of making the workshop into an area.
 */
class AdminAreasMakeCommandTest extends CommonTestSetup {

    private static final String PREFIX = "boxed.commands.boxadmin.areas.make.";
    private static final String SUMMARY = "The box grows to fill its land.";

    private AdminAreasMakeCommand cmd;
    private AreaMaker maker;
    private AreaManager areas;
    private Workshop workshop;
    private User user;

    @BeforeEach
    void setUpCommand() {
        Boxed addon = mock(Boxed.class);
        WorkshopManager manager = mock(WorkshopManager.class);
        areas = mock(AreaManager.class);
        maker = mock(AreaMaker.class);
        workshop = new Workshop(5L, "biomes.yml", null);
        when(addon.getWorkshop()).thenReturn(manager);
        when(addon.getAreas()).thenReturn(areas);
        when(manager.get()).thenReturn(Optional.of(workshop));

        // The command is /boxadmin areas make
        CompositeCommand top = mock(CompositeCommand.class);
        CompositeCommand parent = mock(CompositeCommand.class);
        when(parent.getParent()).thenReturn(top);
        when(parent.getAddon()).thenReturn(addon);
        when(parent.getTopLabel()).thenReturn("boxadmin");
        when(parent.getPermissionPrefix()).thenReturn("");
        when(parent.getWorld()).thenReturn(world);
        when(parent.getSubCommands()).thenReturn(new HashMap<>());
        cmd = new AdminAreasMakeCommand(parent);
        cmd.setMaker(maker);

        // The console
        user = mock(User.class);
        when(user.isPlayer()).thenReturn(false);
    }

    private Prepared ready(String name) {
        Prepared prepared = new Prepared(name, Map.of(), SUMMARY, workshop, new int[] { 0, 0 }, new int[] { 0, 0 });
        when(maker.prepare(eq(name), any())).thenAnswer(i -> {
            // The copying reports how far it has got
            java.util.function.IntConsumer progress = i.getArgument(1);
            for (int percent : new int[] { 1, 30, 100 }) {
                progress.accept(percent);
            }
            return CompletableFuture.completedFuture(prepared);
        });
        return prepared;
    }

    @Test
    void testSetup() {
        assertEquals("make", cmd.getLabel());
        assertEquals("admin.areas.make", cmd.getPermission());
        assertFalse(cmd.isOnlyPlayer());
    }

    @Test
    void testNothingBeingWorkedOn() {
        Boxed addon = cmd.getAddon();
        when(addon.getWorkshop().get()).thenReturn(Optional.empty());
        assertFalse(cmd.execute(user, "make", List.of("meadow")));
        verify(user).sendMessage("boxed.commands.boxadmin.areas.edit.none", "[label]", "boxadmin");
    }

    @Test
    void testProblemsAreExplained() {
        when(maker.problem("meadow")).thenReturn(AreaMaker.Problem.NO_CENTRE);
        assertFalse(cmd.execute(user, "make", List.of("Meadow")));
        verify(user).sendMessage(PREFIX + "no-centre", "[name]", "meadow", "[label]", "boxadmin");
        when(maker.problem("meadow")).thenReturn(AreaMaker.Problem.HAS_BOXES);
        assertFalse(cmd.execute(user, "make", List.of("meadow")));
        verify(user).sendMessage(PREFIX + "has-boxes", "[name]", "meadow", "[label]", "boxadmin");
        verify(maker, never()).prepare(any(), any());
    }

    @Test
    void testCopyThenConfirm() throws IOException {
        Prepared prepared = ready("meadow");
        Area area = new Area("meadow", 5L, "biomes.yml", "s.yml", 1, true, null, 0, 0, 0, 0, 0);
        when(maker.finish(prepared)).thenReturn(area);

        assertTrue(cmd.execute(user, "make", List.of("meadow")));
        verify(user).sendMessage(PREFIX + "started", "[name]", "meadow");
        verify(user).sendMessage(PREFIX + "progress", "[number]", "1");
        verify(user).sendMessage(PREFIX + "progress", "[number]", "30");
        verify(user).sendMessage(PREFIX + "checking");
        verify(user).sendMessage(PREFIX + "result", "[description]", SUMMARY);
        verify(user).sendMessage(PREFIX + "confirm-how", "[name]", "meadow", "[label]", "boxadmin");
        // Nothing is made until it is confirmed
        verify(maker, never()).finish(any());

        assertTrue(cmd.execute(user, "make", List.of("meadow", "confirm")));
        verify(maker).finish(prepared);
        verify(user).sendMessage(PREFIX + "success", "[name]", "meadow");
        // It cannot be made twice
        assertFalse(cmd.execute(user, "make", List.of("meadow", "confirm")));
    }

    @Test
    void testReplacingAnAreaIsPointedOut() {
        ready("icelands");
        when(areas.getArea("icelands"))
                .thenReturn(new Area("icelands", 5L, "biomes.yml", "s.yml", 1, true, null, 0, 0, 0, 0, 0));
        assertTrue(cmd.execute(user, "make", List.of("icelands")));
        verify(user).sendMessage(PREFIX + "confirm-how-replace", "[name]", "icelands", "[label]", "boxadmin");
    }

    @Test
    void testConfirmNeedsTheLandCopiedForThatName() throws IOException {
        assertFalse(cmd.execute(user, "make", List.of("meadow", "confirm")));
        verify(user).sendMessage(PREFIX + "not-prepared", "[name]", "meadow", "[label]", "boxadmin");
        ready("meadow");
        cmd.execute(user, "make", List.of("meadow"));
        assertFalse(cmd.execute(user, "make", List.of("forest", "confirm")));
        verify(maker, never()).finish(any());
    }

    @Test
    void testCopyThatFails() {
        when(maker.prepare(eq("meadow"), any())).thenReturn(CompletableFuture.failedFuture(new IOException("disk full")));
        assertTrue(cmd.execute(user, "make", List.of("meadow")));
        verify(user).sendMessage(PREFIX + "failed");
    }

    @Test
    void testMakeThatFails() throws IOException {
        Prepared prepared = ready("meadow");
        doThrow(new IOException("moved")).when(maker).finish(prepared);
        cmd.execute(user, "make", List.of("meadow"));
        assertFalse(cmd.execute(user, "make", List.of("meadow", "confirm")));
        verify(user).sendMessage(PREFIX + "failed");
    }

    @Test
    void testWrongWords() {
        assertFalse(cmd.execute(user, "make", List.of()));
        assertFalse(cmd.execute(user, "make", List.of("meadow", "yes")));
        assertFalse(cmd.execute(user, "make", List.of("meadow", "confirm", "now")));
        verify(maker, never()).prepare(any(), any());
    }

    @Test
    void testTabComplete() {
        assertTrue(cmd.tabComplete(user, "make", List.of("areas", "make", "")).isEmpty());
        assertEquals(List.of("confirm"),
                cmd.tabComplete(user, "make", List.of("areas", "make", "meadow", "")).orElseThrow());
    }
}
