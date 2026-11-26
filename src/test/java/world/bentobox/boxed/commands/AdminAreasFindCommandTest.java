package world.bentobox.boxed.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.CommonTestSetup;
import world.bentobox.boxed.areas.Area;
import world.bentobox.boxed.areas.AreaManager;

/**
 * Tests the tab completion of the find command, which is given everything typed after
 * /boxadmin: the "areas" and "find" labels and then the parameters.
 */
class AdminAreasFindCommandTest extends CommonTestSetup {

    private AdminAreasFindCommand cmd;
    private User user;

    @BeforeEach
    void setUpCommand() {
        Boxed addon = mock(Boxed.class);
        AreaManager areas = mock(AreaManager.class);
        when(addon.getAreas()).thenReturn(areas);
        when(areas.getAreas()).thenReturn(
                List.of(new Area("default", 1L, "biomes.yml", "s.yml", 0, true, null, 0, 0, 0, 0, 0),
                        new Area("icelands", 2L, "biomes.yml", "s.yml", 1, true, null, 0, 0, 0, 0, 0)));

        // The command is /boxadmin areas find
        CompositeCommand top = mock(CompositeCommand.class);
        CompositeCommand parent = mock(CompositeCommand.class);
        when(parent.getParent()).thenReturn(top);
        when(parent.getAddon()).thenReturn(addon);
        when(parent.getTopLabel()).thenReturn("boxadmin");
        when(parent.getPermissionPrefix()).thenReturn("");
        when(parent.getWorld()).thenReturn(world);
        when(parent.getSubCommands()).thenReturn(new HashMap<>());
        cmd = new AdminAreasFindCommand(parent);
        user = mock(User.class);
    }

    @Test
    void testAreasComeFirst() {
        assertEquals(List.of("default", "icelands"),
                cmd.tabComplete(user, "find", List.of("areas", "find", "")).orElseThrow());
        assertEquals(List.of("icelands"), cmd.tabComplete(user, "find", List.of("areas", "find", "ice")).orElseThrow());
    }

    @Test
    void testThenTheSeed() {
        assertEquals(List.of("random"),
                cmd.tabComplete(user, "find", List.of("areas", "find", "icelands", "")).orElseThrow());
    }

    @Test
    void testNothingForTheRadius() {
        assertTrue(cmd.tabComplete(user, "find", List.of("areas", "find", "icelands", "random", "")).isEmpty());
    }

    @Test
    void testParameterCountsFromTheCommandsOwnLabel() {
        assertEquals(1, AdminAreasCommand.parameter(cmd, List.of("areas", "find", "")));
        assertEquals(3, AdminAreasCommand.parameter(cmd, List.of("areas", "find", "a", "b", "")));
    }
}
