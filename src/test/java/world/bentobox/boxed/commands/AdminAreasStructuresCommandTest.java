package world.bentobox.boxed.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.CommonTestSetup;
import world.bentobox.boxed.workshop.PlacedStructure;
import world.bentobox.boxed.workshop.Workshop;
import world.bentobox.boxed.workshop.WorkshopManager;

/**
 * Tests listing the structures placed in the workshop and taking them away.
 */
class AdminAreasStructuresCommandTest extends CommonTestSetup {

    private static final String PREFIX = "boxed.commands.boxadmin.areas.structures.";
    private static final String NUMBER = "[number]";
    private static final PlacedStructure IGLOO = new PlacedStructure(100, 65, -40, "igloo/top",
            StructureRotation.NONE, Mirror.NONE, false);
    private static final PlacedStructure BASTION = new PlacedStructure(8, 40, 8, "bastion/units/center_pieces/center_0",
            StructureRotation.NONE, Mirror.NONE, false);

    private AdminAreasStructuresCommand cmd;
    private WorkshopManager manager;
    private User user;
    private World workshopWorld;

    @BeforeEach
    void setUpCommand() {
        Boxed addon = mock(Boxed.class);
        manager = mock(WorkshopManager.class);
        when(addon.getWorkshop()).thenReturn(manager);
        workshopWorld = mock(World.class);
        when(workshopWorld.getEnvironment()).thenReturn(Environment.NORMAL);
        when(manager.get()).thenReturn(Optional.of(new Workshop(5L, "biomes/none.yml", null)));
        when(manager.getWorld(any())).thenReturn(workshopWorld);
        when(manager.isWorkshopWorld(workshopWorld)).thenReturn(true);
        when(manager.getStructures(Environment.NORMAL)).thenReturn(List.of(IGLOO));
        when(manager.getStructures(Environment.NETHER)).thenReturn(List.of(BASTION));
        when(manager.removeStructure(any(), anyInt())).thenReturn(CompletableFuture.completedFuture(10));

        // The command is /boxadmin areas structures
        CompositeCommand top = mock(CompositeCommand.class);
        CompositeCommand parent = mock(CompositeCommand.class);
        when(parent.getParent()).thenReturn(top);
        when(parent.getAddon()).thenReturn(addon);
        when(parent.getTopLabel()).thenReturn("boxadmin");
        when(parent.getPermissionPrefix()).thenReturn("");
        when(parent.getWorld()).thenReturn(world);
        when(parent.getSubCommands()).thenReturn(new HashMap<>());
        cmd = new AdminAreasStructuresCommand(parent);

        user = mock(User.class);
        when(user.isPlayer()).thenReturn(true);
        when(user.getWorld()).thenReturn(workshopWorld);
    }

    @Test
    void testSetup() {
        assertEquals("structures", cmd.getLabel());
        assertEquals("admin.areas.structures", cmd.getPermission());
        assertFalse(cmd.isOnlyPlayer());
    }

    @Test
    void testNoWorkshop() {
        when(manager.get()).thenReturn(Optional.empty());
        assertFalse(cmd.execute(user, "structures", List.of()));
        verify(user).sendMessage("boxed.commands.boxadmin.areas.edit.none", "[label]", "boxadmin");
    }

    @Test
    void testList() {
        assertTrue(cmd.execute(user, "structures", List.of()));
        verify(user).sendMessage(PREFIX + "header");
        verify(user).sendMessage(PREFIX + "entry", NUMBER, "1", "[name]", "igloo/top", "[xyz]", "100,65,-40");
    }

    @Test
    void testListNothing() {
        when(manager.getStructures(Environment.NORMAL)).thenReturn(List.of());
        assertTrue(cmd.execute(user, "structures", List.of()));
        verify(user).sendMessage(PREFIX + "none", "[label]", "boxadmin");
    }

    @Test
    void testAPlayerInTheNetherSeesTheNethersStructures() {
        when(workshopWorld.getEnvironment()).thenReturn(Environment.NETHER);
        assertTrue(cmd.execute(user, "structures", List.of()));
        verify(user).sendMessage(PREFIX + "entry", NUMBER, "1", "[name]", "bastion/units/center_pieces/center_0",
                "[xyz]", "8,40,8");
    }

    @Test
    void testRemove() {
        assertTrue(cmd.execute(user, "structures", List.of("remove", "1")));
        verify(manager).removeStructure(Environment.NORMAL, 0);
        verify(user).sendMessage(PREFIX + "removing", NUMBER, "1");
        verify(user).sendMessage(PREFIX + "removed");
    }

    @Test
    void testRemoveThatFails() {
        when(manager.removeStructure(any(), anyInt()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("no")));
        assertTrue(cmd.execute(user, "structures", List.of("remove", "1")));
        verify(user).sendMessage("boxed.commands.boxadmin.areas.edit.failed");
    }

    @Test
    void testRemoveUnknownNumber() {
        assertFalse(cmd.execute(user, "structures", List.of("remove", "2")));
        assertFalse(cmd.execute(user, "structures", List.of("remove", "0")));
        verify(manager, never()).removeStructure(any(), anyInt());
    }

    @Test
    void testRemoveWhileBusy() {
        when(manager.isBusy()).thenReturn(true);
        assertFalse(cmd.execute(user, "structures", List.of("remove", "1")));
        verify(user).sendMessage("boxed.commands.boxadmin.areas.biome.busy");
        verify(manager, never()).removeStructure(any(), anyInt());
    }

    @Test
    void testBadParametersShowHelp() {
        assertFalse(cmd.execute(user, "structures", List.of("remove")));
        assertFalse(cmd.execute(user, "structures", List.of("add", "1")));
        assertFalse(cmd.execute(user, "structures", List.of("remove", "one")));
        verify(manager, never()).removeStructure(any(), anyInt());
    }

    @Test
    void testTabComplete() {
        assertEquals(List.of("remove"), cmd.tabComplete(user, "structures", List.of("areas", "structures", "")).orElseThrow());
        assertEquals(List.of("1"),
                cmd.tabComplete(user, "structures", List.of("areas", "structures", "remove", "")).orElseThrow());
    }
}
