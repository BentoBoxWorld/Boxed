package world.bentobox.boxed.workshop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Chunk;
import org.bukkit.WorldBorder;
import org.bukkit.block.data.BlockData;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpawnCategory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.CommonTestSetup;
import world.bentobox.boxed.Settings;
import world.bentobox.boxed.areas.Area;

/**
 * Tests the life of the workshop: starting, its worlds, players and throwing it away.
 */
class WorkshopManagerTest extends CommonTestSetup {

    @TempDir
    File folder;
    @Mock
    private Boxed addon;
    @Mock
    private World workshopWorld;
    @Mock
    private World mainWorld;

    private File worldFolder;
    private File workshopFile;
    private WorkshopManager manager;

    @Override
    @BeforeEach
    public void setUp() {
        super.setUp();
        File data = new File(folder, "data");
        worldFolder = new File(folder, "boxed_world");
        workshopFile = new File(data, "areas/workshop.yml");
        when(addon.getDataFolder()).thenReturn(data);
        when(addon.getSettings()).thenReturn(new Settings());
        when(addon.getPlugin()).thenReturn(plugin);
        when(addon.getOverWorld()).thenReturn(world);
        when(addon.getIslands()).thenReturn(im);
        when(world.getWorldFolder()).thenReturn(worldFolder);
        when(workshopWorld.getWorldFolder()).thenReturn(new File(worldFolder, "workshop"));
        when(workshopWorld.getEnvironment()).thenReturn(Environment.NORMAL);
        when(workshopWorld.getPlayers()).thenReturn(List.of());
        mockedBukkit.when(() -> Bukkit.createWorld(any(WorldCreator.class))).thenReturn(workshopWorld);
        mockedBukkit.when(() -> Bukkit.unloadWorld(any(World.class), any(Boolean.class))).thenReturn(true);
        mockedBukkit.when(Bukkit::getWorlds).thenReturn(List.of(mainWorld));
        mockedBukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
        when(mainWorld.getSpawnLocation()).thenReturn(location);
        manager = new WorkshopManager(addon);
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
    void testNothingToBeginWith() {
        manager.load();
        assertTrue(manager.get().isEmpty());
        assertNull(manager.getWorld(Environment.NORMAL));
        assertFalse(manager.isWorkshopWorld(world));
        assertFalse(manager.discard().join());
    }

    @Test
    void testStartSavesAndOnlyOneAtATime() throws IOException {
        Workshop w = manager.start(11L, "biomes.yml", null);
        assertSame(w, manager.get().orElseThrow());
        assertTrue(workshopFile.exists());
        assertThrows(IllegalStateException.class, () -> manager.start(12L, "biomes.yml", null));
    }

    @Test
    void testStartFromAnAreaUsesItsCentres() throws IOException {
        Area area = new Area("icelands", 99L, "biomes.yml", "s.yml", 1, true, null, 0, 160, -320, 48, 64);
        Workshop w = manager.start(5L, "biomes.yml", area);
        assertEquals("icelands", w.getArea());
        assertEquals(160, w.getCentre(Environment.NORMAL)[0]);
        assertEquals(64, w.getCentre(Environment.NETHER)[1]);
    }

    @Test
    void testStartFromAnUnchangedAreaCopiesItsLand() throws IOException {
        File seed = new File(worldFolder, "seed_icelands/region");
        Files.createDirectories(seed.toPath());
        Files.writeString(new File(seed, "r.0.0.mca").toPath(), "land");
        Files.writeString(new File(seed.getParentFile(), "session.lock").toPath(), "");
        mockedBukkit.when(() -> Bukkit.getWorld("boxed_world/seed_icelands")).thenReturn(null);
        mockedBukkit.when(() -> Bukkit.getWorld("boxed_world/seed_icelands_nether")).thenReturn(null);
        Area area = new Area("icelands", 99L, "biomes.yml", "s.yml", 1, true, null, 0, 0, 0, 0, 0);

        manager.start(99L, "biomes.yml", area);
        assertEquals("land", Files.readString(new File(worldFolder, "workshop/region/r.0.0.mca").toPath()));
        assertFalse(new File(worldFolder, "workshop/session.lock").exists());
    }

    @Test
    void testAChangedSeedDoesNotCopyTheLand() throws IOException {
        Files.createDirectories(new File(worldFolder, "seed_icelands/region").toPath());
        Area area = new Area("icelands", 99L, "biomes.yml", "s.yml", 1, true, null, 0, 0, 0, 0, 0);
        manager.start(100L, "biomes.yml", area);
        assertFalse(new File(worldFolder, "workshop").exists());
    }

    @Test
    void testWorldIsPeacefulWithNoSpawning() throws IOException {
        manager.start(11L, "biomes.yml", null);
        World w = manager.getWorld(Environment.NORMAL);
        assertSame(workshopWorld, w);
        assertTrue(manager.isWorkshopWorld(w));
        verify(workshopWorld).setDifficulty(Difficulty.PEACEFUL);
        verify(workshopWorld).setSpawnLimit(SpawnCategory.MONSTER, 0);
        verify(workshopWorld).setSpawnLimit(SpawnCategory.ANIMAL, 0);
        // Asking again does not make it again
        assertSame(w, manager.getWorld(Environment.NORMAL));
        mockedBukkit.verify(() -> Bukkit.createWorld(any(WorldCreator.class)));
        // There is no End yet
        assertNull(manager.getWorld(Environment.THE_END));
    }

    @Test
    void testLoadBringsTheWorldsBack() throws IOException {
        new Workshop(11L, "biomes.yml", null).save(workshopFile);
        Files.createDirectories(new File(worldFolder, "workshop").toPath());
        manager.load();
        assertNotNull(manager.get().orElseThrow());
        assertTrue(manager.isWorkshopWorld(workshopWorld));
        // The Nether was never visited, so it is not made
        mockedBukkit.verify(() -> Bukkit.createWorld(any(WorldCreator.class)));
    }

    @Test
    void testPlayersAreRemembered() throws IOException {
        manager.start(11L, "biomes.yml", null);
        when(mockPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
        manager.playerEntered(mockPlayer, GameMode.SURVIVAL);
        assertEquals(GameMode.SURVIVAL, Workshop.load(workshopFile).getPlayers().get(mockPlayer.getUniqueId()));
        assertEquals(GameMode.SURVIVAL, manager.playerLeft(mockPlayer));
        assertNull(manager.playerLeft(mockPlayer));
        assertTrue(Workshop.load(workshopFile).getPlayers().isEmpty());
    }

    @Test
    void testDiscardRemovesEverything() throws IOException {
        manager.start(11L, "biomes.yml", null);
        manager.getWorld(Environment.NORMAL);
        // Someone who is inside but not online
        UUID away = UUID.randomUUID();
        Player awayPlayer = mock(Player.class);
        when(awayPlayer.getUniqueId()).thenReturn(away);
        manager.playerEntered(awayPlayer, GameMode.ADVENTURE);
        // Someone who is inside and online, and has no box
        when(workshopWorld.getPlayers()).thenReturn(List.of(mockPlayer));
        when(mockPlayer.teleportAsync(any())).thenReturn(CompletableFuture.completedFuture(true));

        CompletableFuture<Boolean> done = manager.discard();
        // Nothing can be started while it is going
        assertTrue(manager.get().isEmpty());
        runScheduled();
        assertTrue(done.join());
        verify(mockPlayer).teleportAsync(location);
        mockedBukkit.verify(() -> Bukkit.unloadWorld(workshopWorld, false));
        assertFalse(workshopFile.exists());
        assertFalse(manager.isWorkshopWorld(workshopWorld));
        // The player who was away gets their game mode back when they return, once
        assertEquals(GameMode.ADVENTURE, manager.takeDisplaced(away));
        assertNull(manager.takeDisplaced(away));
        // And it is remembered over a restart until then
        manager.start(12L, "biomes.yml", null);
    }

    @Test
    void testDisplacedPlayersSurviveARestart() throws IOException {
        manager.start(11L, "biomes.yml", null);
        manager.getWorld(Environment.NORMAL);
        UUID away = UUID.randomUUID();
        Player awayPlayer = mock(Player.class);
        when(awayPlayer.getUniqueId()).thenReturn(away);
        manager.playerEntered(awayPlayer, GameMode.ADVENTURE);
        manager.discard();
        runScheduled();

        WorkshopManager restarted = new WorkshopManager(addon);
        restarted.load();
        assertEquals(GameMode.ADVENTURE, restarted.takeDisplaced(away));
    }

    @Test
    void testSendOutGoesToTheBoxIfThereIsOne() {
        UUID uuid = UUID.randomUUID();
        when(mockPlayer.getUniqueId()).thenReturn(uuid);
        when(im.hasIsland(world, uuid)).thenReturn(true);
        when(im.homeTeleportAsync(world, mockPlayer)).thenReturn(CompletableFuture.completedFuture(true));
        assertTrue(manager.sendOut(mockPlayer).join());
        verify(mockPlayer, never()).teleportAsync(any());
    }

    @Test
    void testSendOutGoesToTheGameSpawnNext() {
        Location spawn = mock(Location.class);
        when(mockPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(im.getSpawnPoint(world)).thenReturn(spawn);
        when(mockPlayer.teleportAsync(spawn)).thenReturn(CompletableFuture.completedFuture(true));
        assertTrue(manager.sendOut(mockPlayer).join());
    }

    private void twoSpots() throws IOException {
        Workshop w = new Workshop(11L, "biomes.yml", null);
        w.setSpots(List.of(new Workshop.Spot(0, 0, "plains", 90, "dry"), new Workshop.Spot(1280, -640, "forest", 95, "wood"),
                new Workshop.Spot(-2560, 0, "taiga", -1, "")));
        w.save(workshopFile);
        Files.createDirectories(new File(worldFolder, "workshop").toPath());
        // Land that is asked for never arrives, so the search waits
        when(workshopWorld.getChunkAtAsync(anyInt(), anyInt())).thenReturn(new CompletableFuture<>());
        manager.load();
        when(workshopWorld.getChunkAtAsync(80, -40)).thenReturn(CompletableFuture.completedFuture(mock(Chunk.class)));
        when(mockPlayer.teleportAsync(any())).thenReturn(CompletableFuture.completedFuture(true));
        when(mockPlayer.getWorld()).thenReturn(workshopWorld);
        // Ground at 70 with nothing on it
        when(workshopWorld.getHighestBlockYAt(1280, -640, HeightMap.OCEAN_FLOOR)).thenReturn(70);
        Block ground = mock(Block.class);
        when(ground.getType()).thenReturn(Material.GRASS_BLOCK);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(air.isPassable()).thenReturn(true);
        when(workshopWorld.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);
        when(workshopWorld.getBlockAt(1280, 70, -640)).thenReturn(ground);
    }

    @Test
    void testAnUnfinishedSearchCarriesOnAfterARestart() throws IOException {
        twoSpots();
        // The third place has not been looked at, so its land is asked for
        verify(workshopWorld, atLeastOnce()).getChunkAtAsync(-160, 0);
        verify(workshopWorld, never()).getChunkAtAsync(0, 0);
    }

    @Test
    void testSelectMakesThePlaceTheMiddleAndGoesThere() throws IOException {
        twoSpots();
        assertTrue(manager.select(mockPlayer, 1).join());
        Workshop saved = Workshop.load(workshopFile);
        assertEquals(1280, saved.getCentre(Environment.NORMAL)[0]);
        assertEquals(-640, saved.getCentre(Environment.NORMAL)[1]);
        ArgumentCaptor<Location> to = ArgumentCaptor.forClass(Location.class);
        verify(mockPlayer).teleportAsync(to.capture());
        assertEquals(1280.5, to.getValue().getX());
        assertEquals(71, to.getValue().getY());
        assertEquals(-639.5, to.getValue().getZ());
    }

    @Test
    void testSelectNeedsAPlaceThatHasBeenLookedAt() throws IOException {
        twoSpots();
        assertFalse(manager.select(mockPlayer, -1).join());
        assertFalse(manager.select(mockPlayer, 2).join());
        assertFalse(manager.select(mockPlayer, 3).join());
        verify(mockPlayer, never()).teleportAsync(any());
    }

    @Test
    void testCentreIsMarkedAndBordered() throws IOException {
        twoSpots();
        WorldBorder border = mock(WorldBorder.class);
        mockedBukkit.when(Bukkit::createWorldBorder).thenReturn(border);
        // Nothing is shown until a place has been picked
        manager.showCentre(mockPlayer, true);
        verify(mockPlayer).setWorldBorder(null);
        verify(mockPlayer, never()).sendBlockChange(any(Location.class), any(BlockData.class));

        manager.select(mockPlayer, 1).join();
        manager.showCentre(mockPlayer, true);
        ArgumentCaptor<Location> marker = ArgumentCaptor.forClass(Location.class);
        verify(mockPlayer).sendBlockChange(marker.capture(), any(BlockData.class));
        assertEquals(1280, marker.getValue().getX());
        assertEquals(70, marker.getValue().getY());
        verify(border).setCenter(1280, -640);
        // The border is as wide as the land of an area
        verify(border).setSize(new Settings().getIslandDistance() * 2D);
        verify(mockPlayer).setWorldBorder(border);
        // The marker stays when the border is hidden
        manager.showCentre(mockPlayer, false);
        verify(mockPlayer, times(2)).setWorldBorder(null);
        verify(mockPlayer, times(2)).sendBlockChange(any(Location.class), any(BlockData.class));
    }

    @Test
    void testStandingPlaceIsOnTheGroundUnderTheTrees() {
        // A tree stands on the place: leaves down to 74, trunk down to 71, soil at 70
        when(workshopWorld.getMinHeight()).thenReturn(-64);
        when(workshopWorld.getHighestBlockYAt(anyInt(), anyInt(), any(HeightMap.class))).thenReturn(70);
        when(workshopWorld.getHighestBlockYAt(16, 32, HeightMap.OCEAN_FLOOR)).thenReturn(76);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(air.isPassable()).thenReturn(true);
        Block soil = mock(Block.class);
        when(soil.getType()).thenReturn(Material.DIRT);
        Block log = mock(Block.class);
        when(log.getType()).thenReturn(Material.OAK_LOG);
        Block leaves = mock(Block.class);
        when(leaves.getType()).thenReturn(Material.OAK_LEAVES);
        when(workshopWorld.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);
        when(workshopWorld.getBlockAt(anyInt(), org.mockito.ArgumentMatchers.eq(70), anyInt())).thenReturn(soil);
        for (int y = 71; y <= 73; y++) {
            when(workshopWorld.getBlockAt(16, y, 32)).thenReturn(log);
        }
        for (int y = 74; y <= 76; y++) {
            when(workshopWorld.getBlockAt(16, y, 32)).thenReturn(leaves);
        }
        assertEquals(70, WorkshopManager.groundHeight(workshopWorld, 16, 32));
        // The trunk is in the way, so the player goes on the ground beside it
        Location stand = WorkshopManager.standingPlace(workshopWorld, 16, 32);
        assertEquals(71, stand.getY());
        assertEquals(1, Math.max(stand.getBlockX() - 16, stand.getBlockZ() - 32));
    }

    @Test
    void testStandingPlaceOverWaterIsTheSurface() {
        when(workshopWorld.getMinHeight()).thenReturn(-64);
        when(workshopWorld.getHighestBlockYAt(anyInt(), anyInt(), any(HeightMap.class))).thenReturn(40);
        when(workshopWorld.getHighestBlockYAt(0, 0)).thenReturn(62);
        Block sand = mock(Block.class);
        when(sand.getType()).thenReturn(Material.SAND);
        Block water = mock(Block.class);
        when(water.getType()).thenReturn(Material.WATER);
        when(water.isPassable()).thenReturn(true);
        when(water.isLiquid()).thenReturn(true);
        when(workshopWorld.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(water);
        when(workshopWorld.getBlockAt(anyInt(), org.mockito.ArgumentMatchers.eq(40), anyInt())).thenReturn(sand);
        assertEquals(63, WorkshopManager.standingPlace(workshopWorld, 0, 0).getY());
    }

    @Test
    void testNetherGroundIsUnderTheRoof() {
        World nether = mock(World.class);
        when(nether.getEnvironment()).thenReturn(Environment.NETHER);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        Block rock = mock(Block.class);
        when(rock.getType()).thenReturn(Material.NETHERRACK);
        when(nether.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(rock);
        when(nether.getBlockAt(3, 51, 4)).thenReturn(air);
        when(nether.getBlockAt(3, 52, 4)).thenReturn(air);
        assertEquals(50, WorkshopManager.groundHeight(nether, 3, 4));
    }
}
