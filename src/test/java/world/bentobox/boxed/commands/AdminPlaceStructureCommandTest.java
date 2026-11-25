package world.bentobox.boxed.commands;

import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.HeightMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    void setUpCommand() {
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
        assertEquals("admin.place", cmd.getPermission());
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
    void testGroundLevelHeights() {
        assertTrue(cmd.canExecute(user, "place", List.of("igloo", "~", "ground", "~")));
        assertTrue(cmd.canExecute(user, "place", List.of("igloo", "10", "surface", "20")));
        assertTrue(cmd.canExecute(user, "place", List.of("igloo", "~", "GROUND-1", "~")));
        assertTrue(cmd.canExecute(user, "place", List.of("igloo", "~", "ground+12", "~")));
        // Only the height can be ground level
        assertFalse(cmd.canExecute(user, "place", List.of("igloo", "ground", "~", "~")));
        assertFalse(cmd.canExecute(user, "place", List.of("igloo", "~", "ground-", "~")));
        assertFalse(cmd.canExecute(user, "place", List.of("igloo", "~", "floor", "~")));
    }

    @Test
    void testGroundLevelIgnoresWaterAndTrees() {
        World w = mock(World.class);
        when(w.getMinHeight()).thenReturn(-64);
        // Sea bed at 40 under water that reaches 62
        when(w.getHighestBlockYAt(5, 5, HeightMap.OCEAN_FLOOR)).thenReturn(40);
        when(w.getHighestBlockYAt(5, 5, HeightMap.MOTION_BLOCKING)).thenReturn(62);
        org.bukkit.block.Block bed = mock(org.bukkit.block.Block.class);
        when(bed.getType()).thenReturn(Material.SAND);
        when(w.getBlockAt(5, 40, 5)).thenReturn(bed);
        assertEquals(41, AdminPlaceStructureCommand.groundLevel(w, 5, 5, "ground"));
        assertEquals(40, AdminPlaceStructureCommand.groundLevel(w, 5, 5, "ground-1"));
        assertEquals(63, AdminPlaceStructureCommand.groundLevel(w, 5, 5, "surface"));
        assertEquals(66, AdminPlaceStructureCommand.groundLevel(w, 5, 5, "Surface+3"));

        // A tree: leaves at 72, trunk down to 70, soil at 69
        when(w.getHighestBlockYAt(9, 9, HeightMap.OCEAN_FLOOR)).thenReturn(72);
        org.bukkit.block.Block leaves = mock(org.bukkit.block.Block.class);
        when(leaves.getType()).thenReturn(Material.OAK_LEAVES);
        org.bukkit.block.Block log = mock(org.bukkit.block.Block.class);
        when(log.getType()).thenReturn(Material.OAK_LOG);
        org.bukkit.block.Block soil = mock(org.bukkit.block.Block.class);
        when(soil.getType()).thenReturn(Material.DIRT);
        when(w.getBlockAt(9, 72, 9)).thenReturn(leaves);
        when(w.getBlockAt(9, 71, 9)).thenReturn(log);
        when(w.getBlockAt(9, 70, 9)).thenReturn(log);
        when(w.getBlockAt(9, 69, 9)).thenReturn(soil);
        assertEquals(70, AdminPlaceStructureCommand.groundLevel(w, 9, 9, "ground"));
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
    void testValidNoMobs() {
        assertTrue(cmd.canExecute(user, "place", List.of("igloo", "~", "~", "~", "NONE", "LEFT_RIGHT", "NO_MOBS")));
    }

    @Test
    void testUnknownTrailingArg() {
        assertFalse(cmd.canExecute(user, "place", List.of("igloo", "~", "~", "~", "NONE", "LEFT_RIGHT", "MAYBE")));
        verify(user).sendMessage(eq("boxed.commands.boxadmin.place.unknown"), anyString(), anyString());
    }

    @Test
    void testTooManyArgsRejected() {
        // The size > 7 guard rejects an 8th argument.
        assertFalse(cmd.canExecute(user, "place",
                List.of("igloo", "~", "~", "~", "NONE", "LEFT_RIGHT", "NO_MOBS", "EXTRA")));
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
