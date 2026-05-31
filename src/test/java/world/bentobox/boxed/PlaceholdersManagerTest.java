package world.bentobox.boxed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.boxed.objects.IslandAdvancements;

/**
 * @author tastybento
 */
class PlaceholdersManagerTest extends CommonTestSetup {

    @Mock
    private Boxed addon;
    @Mock
    private AdvancementsManager advManager;
    @Mock
    private IslandAdvancements islandAdv;
    @Mock
    private User user;
    @Mock
    private Location userLocation;

    private PlaceholdersManager phm;
    private UUID uuid;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        uuid = UUID.randomUUID();
        when(user.getUniqueId()).thenReturn(uuid);
        when(user.getLocation()).thenReturn(userLocation);

        when(addon.getIslands()).thenReturn(im);
        when(addon.getOverWorld()).thenReturn(world);
        when(addon.getAdvManager()).thenReturn(advManager);

        when(advManager.getIsland(island)).thenReturn(islandAdv);
        when(islandAdv.getAdvancements()).thenReturn(List.of("a", "b", "c"));

        phm = new PlaceholdersManager(addon);
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    void testGetCountNullUser() {
        assertEquals("", phm.getCount(null));
    }

    @Test
    void testGetCountNullUuid() {
        when(user.getUniqueId()).thenReturn(null);
        assertEquals("", phm.getCount(user));
    }

    @Test
    void testGetCountNoIsland() {
        when(im.getIsland(world, user)).thenReturn(null);
        assertEquals("", phm.getCount(user));
    }

    @Test
    void testGetCountReturnsAdvancementCount() {
        when(im.getIsland(world, user)).thenReturn(island);
        assertEquals("3", phm.getCount(user));
    }

    @Test
    void testGetCountByLocationNullUser() {
        assertEquals("", phm.getCountByLocation(null));
    }

    @Test
    void testGetCountByLocationNullLocation() {
        when(user.getLocation()).thenReturn(null);
        assertEquals("", phm.getCountByLocation(user));
    }

    @Test
    void testGetCountByLocationNoIslandAtLocation() {
        when(im.getIslandAt(userLocation)).thenReturn(Optional.empty());
        assertEquals("", phm.getCountByLocation(user));
    }

    @Test
    void testGetCountByLocationReturnsAdvancementCount() {
        when(im.getIslandAt(userLocation)).thenReturn(Optional.of(island));
        assertEquals("3", phm.getCountByLocation(user));
    }
}
