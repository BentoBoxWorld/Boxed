package world.bentobox.boxed.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import world.bentobox.bentobox.api.events.island.IslandNewIslandEvent;
import world.bentobox.bentobox.api.events.team.TeamJoinedEvent;
import world.bentobox.bentobox.api.events.team.TeamLeaveEvent;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.boxed.AdvancementsManager;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.CommonTestSetup;
import world.bentobox.boxed.Settings;

/**
 * @author tastybento
 */
public class AdvancementListenerTest extends CommonTestSetup {

    @Mock
    private Boxed addon;
    @Mock
    private AdvancementsManager advManager;
    @Mock
    private Player player;
    @Mock
    private Advancement advancement;
    @Mock
    private AdvancementProgress progress;
    @Mock
    private User user;
    @Mock
    private Location playerLocation;

    private Settings settings;
    private UUID playerUuid;

    private MockedStatic<User> mockedUser;

    private AdvancementListener listener;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        // Local User mock (parent only cached mockPlayer, not our local player mock)
        mockedUser = Mockito.mockStatic(User.class, Mockito.CALLS_REAL_METHODS);
        mockedUser.when(() -> User.getInstance(any(Player.class))).thenReturn(user);
        mockedUser.when(() -> User.getInstance(any(UUID.class))).thenReturn(user);

        // Empty advancement iterator so the constructor's 5 lookups all return null
        mockedBukkit.when(Bukkit::advancementIterator).thenAnswer(inv -> Collections.emptyIterator());

        // Settings — real instance, tweak per test
        settings = new Settings();

        // Addon wiring
        when(addon.getPlugin()).thenReturn(plugin);
        when(addon.getSettings()).thenReturn(settings);
        when(addon.getIslands()).thenReturn(im);
        when(addon.getOverWorld()).thenReturn(world);
        when(addon.getAdvManager()).thenReturn(advManager);
        when(addon.inWorld(any(World.class))).thenReturn(true);
        when(addon.inWorld(any(Location.class))).thenReturn(true);

        // Player
        playerUuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.getName()).thenReturn("tester");
        when(player.getWorld()).thenReturn(world);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getLocation()).thenReturn(playerLocation);
        when(player.getAdvancementProgress(any(Advancement.class))).thenReturn(progress);

        // User
        when(user.getPlayer()).thenReturn(player);
        when(user.getName()).thenReturn("tester");
        when(user.getUniqueId()).thenReturn(playerUuid);
        when(user.getWorld()).thenReturn(world);
        when(user.getLocation()).thenReturn(playerLocation);
        when(user.isOnline()).thenReturn(true);
        when(user.getTranslationOrNothing(anyString())).thenReturn("");

        // Advancement
        NamespacedKey key = NamespacedKey.fromString("minecraft:adventure/honey_block_slide");
        when(advancement.getKey()).thenReturn(key);
        when(advancement.getCriteria()).thenReturn(Set.of("crit1", "crit2"));
        when(progress.isDone()).thenReturn(false);
        when(progress.getAwardedCriteria()).thenReturn(Set.of("crit1"));

        // Islands
        when(im.getIsland(world, user)).thenReturn(island);
        when(im.getIslandAt(any(Location.class))).thenReturn(Optional.of(island));
        when(island.getMemberSet()).thenReturn(com.google.common.collect.ImmutableSet.of(playerUuid));
        when(advManager.getScore(anyString())).thenReturn(9);
        when(advManager.addAdvancement(any(Player.class), any(Advancement.class))).thenReturn(9);

        listener = new AdvancementListener(addon);
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        mockedUser.closeOnDemand();
        super.tearDown();
    }

    // ---------- constructor ----------

    @Test
    public void testConstructor() {
        assertNotNull(listener);
    }

    // ---------- onAdvancement ----------

    private PlayerAdvancementDoneEvent advancementDoneEvent() {
        PlayerAdvancementDoneEvent e = mock(PlayerAdvancementDoneEvent.class);
        when(e.getPlayer()).thenReturn(player);
        when(e.getAdvancement()).thenReturn(advancement);
        return e;
    }

    @Test
    public void testOnAdvancementNotSurvival() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        listener.onAdvancement(advancementDoneEvent());
        verify(advManager, never()).addAdvancement(any(Player.class), any(Advancement.class));
    }

    @Test
    public void testOnAdvancementIgnoreSetting() {
        settings.setIgnoreAdvancements(true);
        listener.onAdvancement(advancementDoneEvent());
        verify(advManager, never()).addAdvancement(any(Player.class), any(Advancement.class));
    }

    @Test
    public void testOnAdvancementNotInWorld() {
        when(addon.inWorld(world)).thenReturn(false);
        listener.onAdvancement(advancementDoneEvent());
        verify(advManager, never()).addAdvancement(any(Player.class), any(Advancement.class));
    }

    @Test
    public void testOnAdvancementVisitorDenied() {
        settings.setDenyVisitorAdvancements(true);
        // player is NOT in the member set
        when(island.getMemberSet()).thenReturn(com.google.common.collect.ImmutableSet.of(UUID.randomUUID()));
        listener.onAdvancement(advancementDoneEvent());
        // Criteria should be revoked
        verify(progress).revokeCriteria("crit1");
        verify(progress).revokeCriteria("crit2");
        // And advManager should NOT have awarded
        verify(advManager, never()).addAdvancement(any(Player.class), any(Advancement.class));
        // Notify fired (score > 0)
        verify(user).notify(eq("boxed.adv-disallowed"), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    public void testOnAdvancementMemberGrantsAndSchedulesTellTeam() {
        listener.onAdvancement(advancementDoneEvent());
        verify(advManager).addAdvancement(eq(player), eq(advancement));
        // tellTeam is scheduled one tick later
        verify(sch).runTask(eq(plugin), any(Runnable.class));
    }

    @Test
    public void testOnAdvancementZeroScoreDoesNotSchedule() {
        when(advManager.addAdvancement(any(Player.class), any(Advancement.class))).thenReturn(0);
        listener.onAdvancement(advancementDoneEvent());
        verify(sch, never()).runTask(eq(plugin), any(Runnable.class));
    }

    // ---------- onPortal ----------

    private PlayerPortalEvent portalEvent(TeleportCause cause) {
        PlayerPortalEvent e = mock(PlayerPortalEvent.class);
        when(e.getPlayer()).thenReturn(player);
        when(e.getCause()).thenReturn(cause);
        return e;
    }

    @Test
    public void testOnPortalNetherNoException() {
        // With null netherAdvancement fields, giveAdv short-circuits — we just assert no throw.
        listener.onPortal(portalEvent(TeleportCause.NETHER_PORTAL));
    }

    @Test
    public void testOnPortalEndNoException() {
        listener.onPortal(portalEvent(TeleportCause.END_PORTAL));
    }

    @Test
    public void testOnPortalNotSurvival() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        // should early return — no NPE even though we don't stub the cause
        listener.onPortal(portalEvent(TeleportCause.NETHER_PORTAL));
    }

    @Test
    public void testOnPortalNotInWorld() {
        when(addon.inWorld(world)).thenReturn(false);
        listener.onPortal(portalEvent(TeleportCause.NETHER_PORTAL));
    }

    // ---------- syncAdvancements ----------

    @Test
    public void testSyncAdvancementsIgnoreSetting() {
        settings.setIgnoreAdvancements(true);
        listener.syncAdvancements(user);
        verify(user, never()).sendMessage(anyString(), any());
    }

    @Test
    public void testSyncAdvancementsNoIsland() {
        when(im.getIsland(world, user)).thenReturn(null);
        listener.syncAdvancements(user);
        verify(user, never()).sendMessage(anyString(), any());
    }

    @Test
    public void testSyncAdvancementsSizeIncreased() {
        when(advManager.checkIslandSize(island)).thenReturn(3);
        // Return a non-null IslandAdvancements stub for grantAdv's iteration
        when(advManager.getIsland(island))
                .thenReturn(mock(world.bentobox.boxed.objects.IslandAdvancements.class));
        listener.syncAdvancements(user);
        verify(user).sendMessage(eq("boxed.size-changed"), anyString(), eq("3"));
        verify(player).playSound(eq(playerLocation), eq(Sound.ENTITY_PLAYER_LEVELUP), org.mockito.ArgumentMatchers.anyFloat(), org.mockito.ArgumentMatchers.anyFloat());
    }

    @Test
    public void testSyncAdvancementsSizeDecreased() {
        when(advManager.checkIslandSize(island)).thenReturn(-2);
        when(advManager.getIsland(island))
                .thenReturn(mock(world.bentobox.boxed.objects.IslandAdvancements.class));
        listener.syncAdvancements(user);
        verify(user).sendMessage(eq("boxed.size-decreased"), anyString(), eq("2"));
    }

    // ---------- onPlayerJoin / onPlayerEnterWorld ----------

    @Test
    public void testOnPlayerJoinNotInWorld() {
        when(addon.inWorld(world)).thenReturn(false);
        PlayerJoinEvent e = mock(PlayerJoinEvent.class);
        when(e.getPlayer()).thenReturn(player);
        listener.onPlayerJoin(e);
        // syncAdvancements not called → no island lookup
        verify(im, never()).getIsland(world, user);
    }

    @Test
    public void testOnPlayerEnterWorldDifferentWorld() {
        World otherWorld = mock(World.class);
        when(otherWorld.getName()).thenReturn("other_world");
        when(world.getName()).thenReturn("boxed_world");
        when(player.getWorld()).thenReturn(otherWorld);
        PlayerChangedWorldEvent e = mock(PlayerChangedWorldEvent.class);
        when(e.getPlayer()).thenReturn(player);
        // Util.getWorld(otherWorld) returns a mocked world (parent stubs Util.getWorld(any())
        // to return a fresh mock World), so it won't equal addon.getOverWorld()
        listener.onPlayerEnterWorld(e);
        verify(im, never()).getIsland(world, user);
    }

    // ---------- onTeamJoinTime / onTeamLeaveTime / onFirstTime ----------

    @Test
    public void testOnTeamJoinTimeSettingDisabled() {
        // isOnJoinResetAdvancements defaults to false
        TeamJoinedEvent e = mock(TeamJoinedEvent.class);
        when(e.getPlayerUUID()).thenReturn(playerUuid);
        listener.onTeamJoinTime(e);
        verify(im, never()).getIsland(world, user);
    }

    @Test
    public void testOnTeamLeaveTimeIgnoreAdvancements() {
        settings.setIgnoreAdvancements(true);
        TeamLeaveEvent e = mock(TeamLeaveEvent.class);
        listener.onTeamLeaveTime(e);
        // Short-circuit before User lookup
        verify(e, never()).getPlayerUUID();
    }

    @Test
    public void testOnFirstTimeIgnoreAdvancements() {
        settings.setIgnoreAdvancements(true);
        IslandNewIslandEvent e = mock(IslandNewIslandEvent.class);
        listener.onFirstTime(e);
        verify(e, never()).getPlayerUUID();
    }

    // ---------- static helpers ----------

    @Test
    public void testGetAdvancementNotFound() {
        assertNull(AdvancementListener.getAdvancement("minecraft:story/nonexistent"));
    }

    @Test
    public void testGetAdvancementFound() {
        Advancement a = mock(Advancement.class);
        when(a.getKey()).thenReturn(NamespacedKey.fromString("minecraft:story/root"));
        mockedBukkit.when(Bukkit::advancementIterator).thenAnswer(inv -> List.of(a).iterator());
        assertEquals(a, AdvancementListener.getAdvancement("minecraft:story/root"));
    }

    @Test
    public void testGiveAdvNullAdvancement() {
        // No throw, no interaction
        AdvancementListener.giveAdv(player, null);
        verify(player, never()).getAdvancementProgress(any(Advancement.class));
    }

    @Test
    public void testGiveAdvNotDoneAwardsCriteria() {
        AdvancementListener.giveAdv(player, advancement);
        verify(progress).awardCriteria("crit1");
        verify(progress).awardCriteria("crit2");
    }

    @Test
    public void testGiveAdvAlreadyDoneNoOp() {
        when(progress.isDone()).thenReturn(true);
        AdvancementListener.giveAdv(player, advancement);
        verify(progress, never()).awardCriteria(anyString());
    }
}
