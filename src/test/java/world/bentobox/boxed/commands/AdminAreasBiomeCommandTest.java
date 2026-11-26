package world.bentobox.boxed.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.CommonTestSetup;
import world.bentobox.boxed.generators.biomes.BiomePatch;
import world.bentobox.boxed.reach.LandSurvey;
import world.bentobox.boxed.reach.LandSurvey.Kind;
import world.bentobox.boxed.workshop.Workshop;
import world.bentobox.boxed.workshop.WorkshopManager;

/**
 * Tests looking at the biomes of the box being worked on and putting patches of biome on it.
 */
class AdminAreasBiomeCommandTest extends CommonTestSetup {

    private static final String PREFIX = "boxed.commands.boxadmin.areas.biome.";
    private static final String BIOME = "[biome]";
    private static final String NUMBER = "[number]";
    private static final String LABEL = "[label]";
    private static final String RADIUS = "[radius]";

    @TempDir
    File folder;

    private AdminAreasBiomeCommand cmd;
    private WorkshopManager manager;
    private Workshop workshop;
    private User user;
    private World workshopWorld;

    @BeforeEach
    void setUpCommand() {
        Boxed addon = mock(Boxed.class);
        manager = mock(WorkshopManager.class);
        when(addon.getWorkshop()).thenReturn(manager);
        when(addon.getDataFolder()).thenReturn(folder);
        workshop = new Workshop(5L, "biomes/none.yml", null);
        workshopWorld = mock(World.class);
        when(workshopWorld.getEnvironment()).thenReturn(Environment.NORMAL);
        when(manager.get()).thenReturn(Optional.of(workshop));
        when(manager.getWorld(any())).thenReturn(workshopWorld);
        when(manager.isWorkshopWorld(workshopWorld)).thenReturn(true);
        when(manager.getPatchBiomes(Environment.NORMAL)).thenReturn(List.of("desert", "swamp"));
        when(manager.getPatchBiomes(Environment.NETHER)).thenReturn(List.of("warped_forest"));

        // The command is /boxadmin areas biome
        CompositeCommand top = mock(CompositeCommand.class);
        CompositeCommand parent = mock(CompositeCommand.class);
        when(parent.getParent()).thenReturn(top);
        when(parent.getAddon()).thenReturn(addon);
        when(parent.getTopLabel()).thenReturn("boxadmin");
        when(parent.getPermissionPrefix()).thenReturn("");
        when(parent.getWorld()).thenReturn(world);
        when(parent.getSubCommands()).thenReturn(new HashMap<>());
        cmd = new AdminAreasBiomeCommand(parent);

        user = mock(User.class);
        when(user.getTranslationAsComponent(anyString(), any(String[].class)))
                .thenReturn(net.kyori.adventure.text.Component.text("progress"));
        when(user.isPlayer()).thenReturn(true);
        when(user.isOnline()).thenReturn(true);
        when(user.getPlayer()).thenReturn(mockPlayer);
        when(user.getWorld()).thenReturn(workshopWorld);
        when(user.getLocation()).thenReturn(new Location(workshopWorld, 100.7, 70, -200.2));
    }

    @Test
    void testSetup() {
        assertEquals("biome", cmd.getLabel());
        assertEquals("admin.areas.biome", cmd.getPermission());
        assertFalse(cmd.isOnlyPlayer());
    }

    @Test
    void testNoWorkshop() {
        when(manager.get()).thenReturn(Optional.empty());
        assertFalse(cmd.execute(user, "biome", List.of()));
        verify(user).sendMessage("boxed.commands.boxadmin.areas.edit.none", LABEL, "boxadmin");
    }

    @Test
    void testNoWorld() {
        when(manager.getWorld(any())).thenReturn(null);
        assertFalse(cmd.execute(user, "biome", List.of("add", "swamp")));
        verify(user).sendMessage("boxed.commands.boxadmin.areas.edit.failed");
    }

    @Test
    void testAddWhereThePlayerStands() {
        when(manager.addPatch(any(), any())).thenReturn(CompletableFuture.completedFuture(1234));
        assertTrue(cmd.execute(user, "biome", List.of("add", "SWAMP")));
        verify(manager).addPatch(Environment.NORMAL, new BiomePatch(100, -201, 48, "swamp"));
        verify(user).sendMessage(PREFIX + "adding", BIOME, "swamp", RADIUS, "48", "[xyz]", "100,-201");
        verify(user).sendMessage(PREFIX + "done", NUMBER, "1234", LABEL, "boxadmin");
    }

    @Test
    void testAddWithRadiusAndPlace() {
        when(user.isPlayer()).thenReturn(false);
        when(manager.addPatch(any(), any())).thenReturn(CompletableFuture.completedFuture(5));
        assertTrue(cmd.execute(user, "biome", List.of("add", "desert", "20", "-64", "320")));
        verify(manager).addPatch(Environment.NORMAL, new BiomePatch(-64, 320, 20, "desert"));
    }

    @Test
    void testAddInTheNether() {
        when(workshopWorld.getEnvironment()).thenReturn(Environment.NETHER);
        when(manager.addPatch(any(), any())).thenReturn(CompletableFuture.completedFuture(5));
        assertTrue(cmd.execute(user, "biome", List.of("add", "warped_forest", "30")));
        verify(manager).addPatch(Environment.NETHER, new BiomePatch(100, -201, 30, "warped_forest"));
        // Overworld biomes cannot go in the Nether
        assertFalse(cmd.execute(user, "biome", List.of("add", "swamp")));
        verify(user).sendMessage(PREFIX + "unknown-biome");
    }

    @Test
    void testAddNeedsAPlace() {
        when(user.isPlayer()).thenReturn(false);
        assertFalse(cmd.execute(user, "biome", List.of("add", "swamp")));
        verify(user).sendMessage(PREFIX + "where");
        // A player who is somewhere else has to say where as well
        when(user.isPlayer()).thenReturn(true);
        when(user.getWorld()).thenReturn(world);
        assertFalse(cmd.execute(user, "biome", List.of("add", "swamp", "30")));
        verify(manager, never()).addPatch(any(), any());
    }

    @Test
    void testBadParameters() {
        assertFalse(cmd.execute(user, "biome", List.of("add", "nosuch")));
        verify(user).sendMessage(PREFIX + "unknown-biome");
        assertFalse(cmd.execute(user, "biome", List.of("add", "swamp", "wide")));
        assertFalse(cmd.execute(user, "biome", List.of("add", "swamp", "20", "5")));
        assertFalse(cmd.execute(user, "biome", List.of("add", "swamp", "20", "x", "5")));
        assertFalse(cmd.execute(user, "biome", List.of("add")));
        assertFalse(cmd.execute(user, "biome", List.of("remove", "one")));
        assertFalse(cmd.execute(user, "biome", List.of("paint", "swamp")));
        verify(manager, never()).addPatch(any(), any());
        verify(manager, never()).removePatch(any(), anyInt());
    }

    @Test
    void testBusy() {
        when(manager.isBusy()).thenReturn(true);
        assertFalse(cmd.execute(user, "biome", List.of("add", "swamp")));
        verify(user).sendMessage(PREFIX + "busy");
        verify(manager, never()).addPatch(any(), any());
    }

    @Test
    void testRemove() {
        workshop.setPatches(Environment.NORMAL, List.of(new BiomePatch(0, 0, 48, "swamp")));
        when(manager.removePatch(Environment.NORMAL, 0))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("no")));
        assertFalse(cmd.execute(user, "biome", List.of("remove", "2")));
        verify(user).sendMessage(PREFIX + "no-patch");
        assertFalse(cmd.execute(user, "biome", List.of("remove", "0")));
        assertTrue(cmd.execute(user, "biome", List.of("remove", "1")));
        verify(user).sendMessage(PREFIX + "removing", NUMBER, "1");
        // What went wrong is in the console
        verify(user).sendMessage("boxed.commands.boxadmin.areas.edit.failed");
    }

    @Test
    void testReport() {
        workshop.setPatches(Environment.NORMAL, List.of(new BiomePatch(40, -40, 48, "swamp")));
        LandSurvey survey = new LandSurvey(320);
        survey.add(Kind.BIOME, "forest", 1, 10);
        survey.add(Kind.BIOME, "swamp", 30, 10);
        when(manager.surveyBiomes(eq(Environment.NORMAL), any())).thenAnswer(invocation -> {
            invocation.<java.util.function.IntConsumer>getArgument(1).accept(50);
            return CompletableFuture.completedFuture(survey);
        });
        assertTrue(cmd.execute(user, "biome", List.of()));
        verify(user).sendMessage(PREFIX + "patch", NUMBER, "1", BIOME, "swamp", RADIUS, "48", "[xyz]", "40,-40");
        verify(user).sendMessage(PREFIX + "looking");
        verify(mockPlayer).sendActionBar(any(net.kyori.adventure.text.Component.class));
        verify(user).sendMessage(PREFIX + "present-list", "[description]", "forest (1), swamp (30)");
        // The requirements file is not in a mocked addon, so nothing is asked for
        verify(user).sendMessage(PREFIX + "nothing-missing");
        verify(user, never()).sendMessage(eq(PREFIX + "missing-entry"), anyString(), anyString(), anyString(),
                anyString());
    }

    @Test
    void testReportFails() {
        when(manager.surveyBiomes(any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("no")));
        assertTrue(cmd.execute(user, "biome", List.of()));
        verify(user).sendMessage("boxed.commands.boxadmin.areas.edit.failed");
    }

    @Test
    void testTabComplete() {
        // The server sends the labels of the commands on the way down as well
        assertEquals(List.of("add", "remove"),
                cmd.tabComplete(user, "biome", List.of("areas", "biome", "")).orElseThrow());
        assertEquals(List.of("swamp"),
                cmd.tabComplete(user, "biome", List.of("areas", "biome", "add", "s")).orElseThrow());
        workshop.setPatches(Environment.NORMAL, List.of(new BiomePatch(0, 0, 48, "swamp"), new BiomePatch(9, 9, 48, "desert")));
        assertEquals(List.of("1", "2"),
                cmd.tabComplete(user, "biome", List.of("areas", "biome", "remove", "")).orElseThrow());
        assertTrue(cmd.tabComplete(user, "biome", List.of("areas", "biome", "add", "swamp", "")).isEmpty());
    }
}
