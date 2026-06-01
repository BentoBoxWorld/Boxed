package world.bentobox.boxed.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.CommonTestSetup;

/**
 * Tests the argument validation ({@code canExecute}) and tab completion of
 * {@link AdminPlaceStructureCommand}.
 */
class AdminPlaceStructureCommandTest extends CommonTestSetup {

    private AdminPlaceStructureCommand cmd;
    private Boxed addon;
    private User user;

    @BeforeEach
    public void setUpCommand() {
        addon = mock(Boxed.class);

        CompositeCommand parent = mock(CompositeCommand.class);
        when(parent.getAddon()).thenReturn(addon);
        when(parent.getTopLabel()).thenReturn("boxadmin");
        when(parent.getPermissionPrefix()).thenReturn("");
        when(parent.getWorld()).thenReturn(world);
        when(parent.getSubCommands()).thenReturn(new HashMap<>());

        cmd = new AdminPlaceStructureCommand(parent);

        // In the Boxed world by default
        when(addon.inWorld(world)).thenReturn(true);

        // One known structure called "igloo"
        StructureManager sm = Bukkit.getStructureManager();
        Map<NamespacedKey, Structure> structures = new HashMap<>();
        structures.put(NamespacedKey.minecraft("igloo"), mock(Structure.class));
        when(sm.getStructures()).thenReturn(structures);

        user = mock(User.class);
        when(user.getLocation()).thenReturn(location);
    }

    @Test
    void testSetup() {
        assertEquals("place", cmd.getLabel());
        assertEquals("boxed.commands.boxadmin.place", cmd.getPermission());
        assertFalse(cmd.isOnlyPlayer());
    }

    @Test
    void testUndoAlwaysAllowed() {
        assertTrue(cmd.canExecute(user, "place", List.of("undo")));
    }

    @Test
    void testWrongWorld() {
        when(addon.inWorld(world)).thenReturn(false);
        assertFalse(cmd.canExecute(user, "place", List.of("igloo")));
        verify(user).sendMessage("boxed.commands.boxadmin.place.wrong-world");
    }

    @Test
    void testUnknownStructure() {
        assertFalse(cmd.canExecute(user, "place", List.of("mansion")));
        verify(user).sendMessage("boxed.commands.boxadmin.place.unknown-structure");
    }

    @Test
    void testStructureNameOnly() {
        assertTrue(cmd.canExecute(user, "place", List.of("igloo")));
    }

    @Test
    void testNonIntegerCoordinates() {
        assertFalse(cmd.canExecute(user, "place", List.of("igloo", "x", "0", "0")));
        verify(user).sendMessage("boxed.commands.boxadmin.place.use-integers");
    }

    @Test
    void testTildeCoordinates() {
        assertTrue(cmd.canExecute(user, "place", List.of("igloo", "~", "~", "~")));
    }

    @Test
    void testIntegerCoordinates() {
        assertTrue(cmd.canExecute(user, "place", List.of("igloo", "10", "64", "-20")));
    }

    @Test
    void testUnknownRotation() {
        assertFalse(cmd.canExecute(user, "place", List.of("igloo", "~", "~", "~", "SPIN")));
        verify(user).sendMessage("boxed.commands.boxadmin.place.unknown-rotation");
    }

    @Test
    void testValidRotation() {
        assertTrue(cmd.canExecute(user, "place", List.of("igloo", "~", "~", "~", "CLOCKWISE_90")));
    }

    @Test
    void testUnknownMirror() {
        assertFalse(cmd.canExecute(user, "place", List.of("igloo", "~", "~", "~", "NONE", "FLIP")));
        verify(user).sendMessage("boxed.commands.boxadmin.place.unknown-mirror");
    }

    @Test
    void testValidMirror() {
        assertTrue(cmd.canExecute(user, "place", List.of("igloo", "~", "~", "~", "NONE", "LEFT_RIGHT")));
    }

    @Test
    void testTooManyArgsRejected() {
        // The size > 6 guard rejects a 7th argument (the NO_MOBS form is unreachable).
        assertFalse(cmd.canExecute(user, "place", List.of("igloo", "~", "~", "~", "NONE", "LEFT_RIGHT", "NO_MOBS")));
    }

    @Test
    void testTabCompleteFirstArgOffersUndo() {
        Optional<List<String>> opt = cmd.tabComplete(user, "place", List.of(""));
        assertTrue(opt.isPresent());
        assertTrue(opt.get().contains("undo"));
    }

    @Test
    void testTabCompleteSecondArgOffersStructures() {
        Optional<List<String>> opt = cmd.tabComplete(user, "place", List.of("place", ""));
        assertTrue(opt.isPresent());
        assertTrue(opt.get().contains("igloo"));
    }

    @Test
    void testTabCompleteRotation() {
        Optional<List<String>> opt = cmd.tabComplete(user, "place", List.of("place", "igloo", "~", "~", "~", ""));
        assertTrue(opt.isPresent());
        assertTrue(opt.get().contains("CLOCKWISE_90"));
    }

    @Test
    void testTabCompleteNoMobs() {
        Optional<List<String>> opt = cmd.tabComplete(user, "place",
                List.of("place", "igloo", "~", "~", "~", "NONE", "LEFT_RIGHT", ""));
        assertTrue(opt.isPresent());
        assertEquals(List.of("NO_MOBS"), opt.get());
    }

    @Test
    void testNeverMessagesWhenValid() {
        cmd.canExecute(user, "place", List.of("igloo"));
        verify(user, never()).sendMessage(anyString());
    }
}
