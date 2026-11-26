package world.bentobox.boxed.listeners;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.CommonTestSetup;
import world.bentobox.boxed.workshop.WorkshopManager;

/**
 * Tests the rules for players in the workshop worlds.
 */
class WorkshopListenerTest extends CommonTestSetup {

    @Mock
    private Boxed addon;
    @Mock
    private WorkshopManager manager;
    @Mock
    private World workshopWorld;

    private final UUID uuid = UUID.randomUUID();
    private WorkshopListener listener;

    @Override
    @BeforeEach
    public void setUp() {
        super.setUp();
        when(addon.getPlugin()).thenReturn(plugin);
        when(manager.isWorkshopWorld(workshopWorld)).thenReturn(true);
        when(mockPlayer.getUniqueId()).thenReturn(uuid);
        when(mockPlayer.getGameMode()).thenReturn(GameMode.SURVIVAL);
        mockedBukkit.when(Bukkit::getDefaultGameMode).thenReturn(GameMode.ADVENTURE);
        listener = new WorkshopListener(addon, manager);
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private void runScheduled() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(sch).runTask(any(), task.capture());
        task.getValue().run();
    }

    @Test
    void testComingInGivesCreative() {
        when(mockPlayer.getWorld()).thenReturn(workshopWorld);
        listener.onChangedWorld(new PlayerChangedWorldEvent(mockPlayer, world));
        verify(manager).playerEntered(mockPlayer, GameMode.SURVIVAL);
        verify(mockPlayer).setGameMode(GameMode.CREATIVE);
    }

    @Test
    void testLeavingGivesTheOldGameModeBack() {
        when(mockPlayer.getWorld()).thenReturn(world);
        when(manager.playerLeft(mockPlayer)).thenReturn(GameMode.SPECTATOR);
        listener.onChangedWorld(new PlayerChangedWorldEvent(mockPlayer, workshopWorld));
        verify(mockPlayer).setGameMode(GameMode.SPECTATOR);
        // The border that marks the box is taken away
        verify(mockPlayer).setWorldBorder(null);
    }

    @Test
    void testLeavingWithNoOldGameModeGivesTheServerDefault() {
        when(mockPlayer.getWorld()).thenReturn(world);
        listener.onChangedWorld(new PlayerChangedWorldEvent(mockPlayer, workshopWorld));
        verify(mockPlayer).setGameMode(GameMode.ADVENTURE);
    }

    @Test
    void testOtherWorldChangesAreLeftAlone() {
        when(mockPlayer.getWorld()).thenReturn(world);
        listener.onChangedWorld(new PlayerChangedWorldEvent(mockPlayer, mock(World.class)));
        // Moving between the workshop's own dimensions changes nothing either
        World nether = mock(World.class);
        when(manager.isWorkshopWorld(nether)).thenReturn(true);
        when(mockPlayer.getWorld()).thenReturn(nether);
        listener.onChangedWorld(new PlayerChangedWorldEvent(mockPlayer, workshopWorld));
        verify(mockPlayer, never()).setGameMode(any());
    }

    @Test
    void testJoiningInsideGivesCreative() {
        when(mockPlayer.getWorld()).thenReturn(workshopWorld);
        listener.onJoin(new PlayerJoinEvent(mockPlayer, net.kyori.adventure.text.Component.empty()));
        verify(mockPlayer).setGameMode(GameMode.CREATIVE);
        verify(manager, never()).takeDisplaced(any());
    }

    @Test
    void testJoiningAfterTheWorkshopWentSendsThePlayerOut() {
        when(mockPlayer.getWorld()).thenReturn(world);
        when(manager.takeDisplaced(uuid)).thenReturn(GameMode.SURVIVAL);
        listener.onJoin(new PlayerJoinEvent(mockPlayer, net.kyori.adventure.text.Component.empty()));
        verify(mockPlayer).setGameMode(GameMode.SURVIVAL);
        runScheduled();
        verify(manager).sendOut(mockPlayer);
    }

    @Test
    void testJoiningNormallyDoesNothing() {
        when(mockPlayer.getWorld()).thenReturn(world);
        listener.onJoin(new PlayerJoinEvent(mockPlayer, net.kyori.adventure.text.Component.empty()));
        verify(mockPlayer, never()).setGameMode(any());
        verify(manager, never()).sendOut(any());
    }

    @Test
    void testDyingInsideComesBackWhereTheyDied() {
        when(mockPlayer.getWorld()).thenReturn(workshopWorld);
        when(mockPlayer.getLocation()).thenReturn(location);
        when(location.getWorld()).thenReturn(workshopWorld);
        PlayerDeathEvent death = mock(PlayerDeathEvent.class);
        when(death.getEntity()).thenReturn(mockPlayer);
        listener.onDeath(death);

        PlayerRespawnEvent respawn = mock(PlayerRespawnEvent.class);
        when(respawn.getPlayer()).thenReturn(mockPlayer);
        listener.onRespawn(respawn);
        verify(respawn).setRespawnLocation(location);
        runScheduled();
        verify(mockPlayer).setGameMode(GameMode.CREATIVE);
    }

    @Test
    void testDyingElsewhereIsLeftAlone() {
        when(mockPlayer.getWorld()).thenReturn(world);
        PlayerDeathEvent death = mock(PlayerDeathEvent.class);
        when(death.getEntity()).thenReturn(mockPlayer);
        listener.onDeath(death);
        PlayerRespawnEvent respawn = mock(PlayerRespawnEvent.class);
        when(respawn.getPlayer()).thenReturn(mockPlayer);
        listener.onRespawn(respawn);
        verify(respawn, never()).setRespawnLocation(any());
    }
}
