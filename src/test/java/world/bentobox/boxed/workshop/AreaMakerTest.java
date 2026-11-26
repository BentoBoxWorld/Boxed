package world.bentobox.boxed.workshop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.CommonTestSetup;
import world.bentobox.boxed.areas.Area;
import world.bentobox.boxed.areas.AreaManager;
import world.bentobox.boxed.workshop.AreaMaker.Problem;

/**
 * Tests the rules for making the workshop into an area.
 */
class AreaMakerTest extends CommonTestSetup {

    @Mock
    private Boxed addon;
    @Mock
    private WorkshopManager manager;
    @Mock
    private AreaManager areas;

    private final Area icelands = new Area("icelands", 9L, "biomes.yml", "s.yml", 1, true, null, 0, 0, 0, 0, 0);
    private Workshop workshop;
    private AreaMaker maker;

    @Override
    @BeforeEach
    public void setUp() {
        super.setUp();
        workshop = new Workshop(5L, "biomes.yml", null);
        workshop.setCentre(Environment.NORMAL, 160, 320);
        when(manager.get()).thenReturn(Optional.of(workshop));
        when(addon.getAreas()).thenReturn(areas);
        when(addon.getOverWorld()).thenReturn(world);
        when(addon.getIslands()).thenReturn(im);
        when(areas.getArea("icelands")).thenReturn(icelands);
        when(im.getIslands(world)).thenReturn(List.of());
        maker = new AreaMaker(addon, manager);
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private void aBoxIn(Area area) {
        Island box = mock(Island.class);
        Location centre = mock(Location.class);
        when(centre.getBlockZ()).thenReturn(640);
        when(box.getOwner()).thenReturn(UUID.randomUUID());
        when(box.getCenter()).thenReturn(centre);
        when(addon.getAreaAt(40)).thenReturn(area);
        when(im.getIslands(world)).thenReturn(List.of(box));
    }

    @Test
    void testANewNameIsFine() {
        assertNull(maker.problem("meadow"));
    }

    @Test
    void testAnAreaWithNoBoxesCanBeReplaced() {
        assertNull(maker.problem("icelands"));
        assertEquals(0, maker.countBoxes(icelands));
    }

    @Test
    void testAnAreaWithBoxesCannotBeReplaced() {
        aBoxIn(icelands);
        assertEquals(1, maker.countBoxes(icelands));
        assertEquals(Problem.HAS_BOXES, maker.problem("icelands"));
        // Boxes in other areas do not matter
        assertNull(maker.problem("meadow"));
    }

    @Test
    void testOtherProblems() {
        assertEquals(Problem.INVALID_NAME, maker.problem("My Area"));
        assertEquals(Problem.INVALID_NAME, maker.problem(""));
        when(manager.isBusy()).thenReturn(true);
        assertEquals(Problem.BUSY, maker.problem("meadow"));
        when(manager.isBusy()).thenReturn(false);
        when(manager.get()).thenReturn(Optional.of(new Workshop(5L, "biomes.yml", null)));
        assertEquals(Problem.NO_CENTRE, maker.problem("meadow"));
        when(manager.get()).thenReturn(Optional.empty());
        assertEquals(Problem.NO_WORKSHOP, maker.problem("meadow"));
    }

    @Test
    void testFinishRefusesLandFromAnotherWorkshop() {
        AreaMaker.Prepared prepared = new AreaMaker.Prepared("meadow", Map.of(), "", new Workshop(6L, "biomes.yml", null),
                new int[] { 160, 320 }, new int[] { 0, 0 });
        assertThrows(IOException.class, () -> maker.finish(prepared));
    }

    @Test
    void testFinishRefusesLandCopiedBeforeTheMiddleMoved() {
        AreaMaker.Prepared prepared = new AreaMaker.Prepared("meadow", Map.of(), "", workshop, new int[] { 0, 0 },
                new int[] { 0, 0 });
        assertThrows(IOException.class, () -> maker.finish(prepared));
    }

    @Test
    void testFinishRefusesAnAreaThatGotABoxMeanwhile() {
        AreaMaker.Prepared prepared = new AreaMaker.Prepared("icelands", Map.of(), "", workshop,
                new int[] { 160, 320 }, new int[] { 0, 0 });
        aBoxIn(icelands);
        assertThrows(IOException.class, () -> maker.finish(prepared));
    }
}
