package world.bentobox.boxed.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.bukkit.World.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.CommonTestSetup;
import world.bentobox.boxed.areas.Area;
import world.bentobox.boxed.areas.AreaManager;
import world.bentobox.boxed.workshop.Workshop;
import world.bentobox.boxed.workshop.WorkshopManager;

/**
 * Tests the typed form of the area workshop command.
 */
class AdminAreasEditCommandTest extends CommonTestSetup {

    private static final String PREFIX = "boxed.commands.boxadmin.areas.edit.";

    @TempDir
    File folder;

    private AdminAreasEditCommand cmd;
    private Boxed addon;
    private WorkshopManager manager;
    private AreaManager areas;
    private User user;

    @BeforeEach
    void setUpCommand() throws IOException {
        addon = mock(Boxed.class);
        manager = mock(WorkshopManager.class);
        areas = mock(AreaManager.class);
        when(addon.getWorkshop()).thenReturn(manager);
        when(addon.getAreas()).thenReturn(areas);
        when(addon.getDataFolder()).thenReturn(folder);
        Files.createDirectories(new File(folder, "biomes").toPath());
        Files.writeString(new File(folder, "biomes/rings.yml").toPath(), "");
        when(manager.get()).thenReturn(Optional.empty());
        when(manager.hasDimension(any())).thenReturn(true);
        when(manager.getWorld(any())).thenReturn(world);
        when(areas.getArea("icelands")).thenReturn(
                new Area("icelands", 99L, "biomes-icelands.yml", "s.yml", 1, true, null, 0, 0, 0, 0, 0));

        // Once work has started, there is an area in progress
        when(manager.start(anyLong(), anyString(), any())).thenAnswer(i -> {
            inProgress();
            return manager.get().orElseThrow();
        });

        // The command is /boxadmin areas edit
        CompositeCommand top = mock(CompositeCommand.class);
        CompositeCommand parent = mock(CompositeCommand.class);
        when(parent.getParent()).thenReturn(top);
        when(parent.getAddon()).thenReturn(addon);
        when(parent.getTopLabel()).thenReturn("boxadmin");
        when(parent.getPermissionPrefix()).thenReturn("");
        when(parent.getWorld()).thenReturn(world);
        when(parent.getSubCommands()).thenReturn(new HashMap<>());
        cmd = new AdminAreasEditCommand(parent);

        // The console
        user = mock(User.class);
        when(user.isPlayer()).thenReturn(false);
    }

    private void inProgress() {
        when(manager.get()).thenReturn(Optional.of(new Workshop(5L, "biomes/rings.yml", null)));
    }

    @Test
    void testSetup() {
        assertEquals("edit", cmd.getLabel());
        assertEquals("admin.areas.edit", cmd.getPermission());
        assertFalse(cmd.isOnlyPlayer());
    }

    @Test
    void testConsoleIsToldThereIsNothing() {
        assertTrue(cmd.execute(user, "edit", List.of()));
        verify(user).sendMessage(PREFIX + "none", "[label]", "boxadmin");
    }

    @Test
    void testConsoleIsToldWhatIsInProgress() {
        inProgress();
        assertTrue(cmd.execute(user, "edit", List.of()));
        verify(user).sendMessage(PREFIX + "status", "[seed]", "5", "[style]", "rings", "[area]", "-");
    }

    @Test
    void testStartNewArea() throws IOException {
        assertTrue(cmd.execute(user, "edit", List.of("start", "1234", "rings")));
        verify(manager).start(1234L, "biomes/rings.yml", null);
        verify(user).sendMessage(PREFIX + "started", "[seed]", "1234", "[style]", "rings");
        // The console cannot go there, but the world is made
        verify(manager).getWorld(Environment.NORMAL);
        verify(user).sendMessage(PREFIX + "ready");
    }

    @Test
    void testStartWithRandomSeedAndOriginalStyle() throws IOException {
        assertTrue(cmd.execute(user, "edit", List.of("start", "random")));
        verify(manager).start(anyLong(), eq("biomes.yml"), isNull());
        when(manager.get()).thenReturn(Optional.empty());
        assertTrue(cmd.execute(user, "edit", List.of("start", "-1", "original")));
    }

    @Test
    void testStartFromAnAreaKeepsItsSeedAndBiomes() throws IOException {
        assertTrue(cmd.execute(user, "edit", List.of("start", "same", "same", "icelands")));
        verify(manager).start(99L, "biomes-icelands.yml", areas.getArea("icelands"));
    }

    @Test
    void testStartFromAnAreaWithAnotherStyle() throws IOException {
        assertTrue(cmd.start(user, "", "rings", "ICELANDS"));
        verify(manager).start(99L, "biomes/rings.yml", areas.getArea("icelands"));
    }

    @Test
    void testStartProblems() throws IOException {
        assertFalse(cmd.execute(user, "edit", List.of("start", "abc")));
        verify(user).sendMessage("boxed.commands.boxadmin.areas.create.invalid-seed");
        assertFalse(cmd.execute(user, "edit", List.of("start", "1", "nosuch")));
        verify(user).sendMessage(PREFIX + "unknown-style", "[styles]", "original, rings");
        assertFalse(cmd.execute(user, "edit", List.of("start", "1", "rings", "nowhere")));
        verify(user).sendMessage("boxed.commands.boxadmin.areas.unknown-area");
        verify(manager, never()).start(anyLong(), anyString(), any());
    }

    @Test
    void testStartWhenOneIsInProgress() throws IOException {
        inProgress();
        assertFalse(cmd.execute(user, "edit", List.of("start", "1")));
        verify(user).sendMessage(PREFIX + "exists");
        verify(manager, never()).start(anyLong(), anyString(), any());
    }

    @Test
    void testStartThatCannotBeSaved() throws IOException {
        doThrow(new IOException("disk full")).when(manager).start(anyLong(), anyString(), any());
        assertFalse(cmd.execute(user, "edit", List.of("start", "1")));
        verify(user).sendMessage(PREFIX + "failed");
    }

    @Test
    void testGo() {
        assertFalse(cmd.execute(user, "edit", List.of("go")));
        verify(user).sendMessage(PREFIX + "none", "[label]", "boxadmin");
        inProgress();
        assertTrue(cmd.execute(user, "edit", List.of("go", "nether")));
        verify(manager).getWorld(Environment.NETHER);
        when(manager.hasDimension(Environment.NETHER)).thenReturn(false);
        assertFalse(cmd.execute(user, "edit", List.of("go", "nether")));
        verify(user).sendMessage(PREFIX + "failed");
    }

    @Test
    void testPlayerIsTakenThere() {
        inProgress();
        when(user.isPlayer()).thenReturn(true);
        when(user.getPlayer()).thenReturn(mockPlayer);
        when(manager.enter(mockPlayer, Environment.NORMAL)).thenReturn(CompletableFuture.completedFuture(true));
        assertTrue(cmd.execute(user, "edit", List.of("go")));
        verify(user).sendMessage(PREFIX + "welcome", "[label]", "boxadmin");
    }

    @Test
    void testDiscardNeedsConfirming() {
        inProgress();
        assertFalse(cmd.execute(user, "edit", List.of("discard")));
        verify(user).sendMessage(PREFIX + "discard-confirm");
        verify(manager, never()).discard();
    }

    @Test
    void testDiscard() {
        inProgress();
        when(manager.discard()).thenReturn(CompletableFuture.completedFuture(true));
        assertTrue(cmd.execute(user, "edit", List.of("discard", "confirm")));
        verify(user).sendMessage(PREFIX + "discarded");
    }

    @Test
    void testDiscardNothing() {
        assertFalse(cmd.execute(user, "edit", List.of("discard", "confirm")));
        verify(user).sendMessage(PREFIX + "none", "[label]", "boxadmin");
    }

    @Test
    void testTabComplete() {
        Area icelands = areas.getArea("icelands");
        when(areas.getAreas()).thenReturn(List.of(icelands));
        assertEquals(List.of("start", "go", "discard"), cmd.tabComplete(user, "edit", List.of("areas", "edit", "")).orElseThrow());
        assertEquals(List.of("random", "same"), cmd.tabComplete(user, "edit", List.of("areas", "edit", "start", "")).orElseThrow());
        assertEquals(List.of("same", "original", "rings"),
                cmd.tabComplete(user, "edit", List.of("areas", "edit", "start", "1", "")).orElseThrow());
        assertEquals(List.of("icelands"),
                cmd.tabComplete(user, "edit", List.of("areas", "edit", "start", "1", "rings", "")).orElseThrow());
        assertEquals(List.of("nether"), cmd.tabComplete(user, "edit", List.of("areas", "edit", "go", "")).orElseThrow());
        assertEquals(List.of("confirm"), cmd.tabComplete(user, "edit", List.of("areas", "edit", "discard", "c")).orElseThrow());
    }
}
