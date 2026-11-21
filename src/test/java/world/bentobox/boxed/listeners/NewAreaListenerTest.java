package world.bentobox.boxed.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;
import org.bukkit.util.BlockVector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import world.bentobox.bentobox.api.events.BentoBoxReadyEvent;
import world.bentobox.bentobox.api.events.island.IslandCreatedEvent;
import world.bentobox.bentobox.api.events.island.IslandDeleteEvent;
import world.bentobox.bentobox.database.AbstractDatabaseHandler;
import world.bentobox.bentobox.database.DatabaseSetup;
import world.bentobox.bentobox.database.DatabaseSetup.DatabaseType;
import world.bentobox.bentobox.util.Pair;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.CommonTestSetup;
import world.bentobox.boxed.Settings;
import world.bentobox.boxed.objects.ToBePlacedStructures;
import world.bentobox.boxed.objects.ToBePlacedStructures.StructureRecord;

/**
 * Tests for the structure queue in {@link NewAreaListener}.
 */
class NewAreaListenerTest extends CommonTestSetup {

    @Mock
    private Boxed addon;
    @Mock
    private world.bentobox.bentobox.Settings pluginSettings;
    @Mock
    private StructureManager sm;
    @Mock
    private Structure structure;
    @Mock
    private Block block;

    private Settings settings;
    private final CompletableFuture<Chunk> chunkFuture = new CompletableFuture<>();
    private File dataFolder;
    private MockedStatic<DatabaseSetup> mockedDatabaseSetup;
    private AbstractDatabaseHandler<Object> h;
    private NewAreaListener nal;
    /** Number of overworld structures in the default structures.yml */
    private int normalCount;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUpListener() throws Exception {
        // Database
        h = mock(AbstractDatabaseHandler.class);
        mockedDatabaseSetup = Mockito.mockStatic(DatabaseSetup.class);
        DatabaseSetup dbSetup = mock(DatabaseSetup.class);
        mockedDatabaseSetup.when(DatabaseSetup::getDatabase).thenReturn(dbSetup);
        when(dbSetup.getHandler(any())).thenReturn(h);
        when(h.saveObject(any())).thenReturn(CompletableFuture.completedFuture(true));
        when(h.saveObjectNow(any())).thenReturn(CompletableFuture.completedFuture(true));
        DatabaseType value = DatabaseType.JSON;
        when(plugin.getSettings()).thenReturn(pluginSettings);
        when(pluginSettings.getDatabaseType()).thenReturn(value);

        // Addon
        dataFolder = new File("dataFolder");
        dataFolder.mkdirs();
        Files.copy(Path.of("src/main/resources/structures.yml"), Path.of("dataFolder/structures.yml"));
        normalCount = org.bukkit.configuration.file.YamlConfiguration
                .loadConfiguration(new File(dataFolder, "structures.yml")).getConfigurationSection("normal")
                .getKeys(false).size();
        when(addon.getDataFolder()).thenReturn(dataFolder);
        when(addon.getPlugin()).thenReturn(plugin);
        settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        when(addon.inWorld(world)).thenReturn(true);
        when(addon.getOverWorld()).thenReturn(world);
        when(addon.getIslands()).thenReturn(im);

        // World
        when(world.getEnvironment()).thenReturn(Environment.NORMAL);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        when(block.getType()).thenReturn(Material.STONE);

        // Island
        when(island.getUniqueId()).thenReturn("island1");
        Location center = new Location(world, 0, 64, 0);
        when(island.getProtectionCenter()).thenReturn(center);
        when(island.getWorld()).thenReturn(world);
        when(im.getIslandAt(any())).thenReturn(Optional.of(island));

        // Structures
        mockedBukkit.when(Bukkit::getStructureManager).thenReturn(sm);
        when(sm.loadStructure(any(NamespacedKey.class))).thenReturn(structure);
        when(structure.getSize()).thenReturn(new BlockVector(1, 1, 1));

        // Run async tasks straight away so the listener becomes ready
        when(sch.runTaskAsynchronously(any(), any(Runnable.class))).thenAnswer(inv -> {
            inv.getArgument(1, Runnable.class).run();
            return null;
        });

        mockedBukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        when(world.getChunkAtAsync(anyInt(), anyInt())).thenReturn(chunkFuture);

        nal = new NewAreaListener(addon);
        nal.onBentoBoxReady(mock(BentoBoxReadyEvent.class));
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        mockedDatabaseSetup.closeOnDemand();
        deleteAll(dataFolder);
        super.tearDown();
    }

    private void createIsland() {
        IslandCreatedEvent e = mock(IslandCreatedEvent.class);
        when(e.getIsland()).thenReturn(island);
        nal.onIslandCreated(e);
    }

    private void loadChunk(int x, int z) {
        Chunk chunk = mock(Chunk.class);
        when(chunk.getX()).thenReturn(x);
        when(chunk.getZ()).thenReturn(z);
        when(chunk.getWorld()).thenReturn(world);
        ChunkLoadEvent e = mock(ChunkLoadEvent.class);
        when(e.getChunk()).thenReturn(chunk);
        when(e.getWorld()).thenReturn(world);
        nal.onChunkLoad(e);
    }

    private void verifyPlaced(int times) {
        verify(structure, times(times)).place(any(Location.class), anyBoolean(), any(StructureRotation.class),
                any(Mirror.class), anyInt(), anyFloat(), any(Random.class));
    }

    /**
     * @return the last to-be-placed list written to the database
     */
    private int savedTodoCount() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        try {
            verify(h, atLeastOnce()).saveObject(captor.capture());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        List<Object> saved = new ArrayList<>(captor.getAllValues());
        Collections.reverse(saved);
        return saved.stream().filter(ToBePlacedStructures.class::isInstance).map(ToBePlacedStructures.class::cast)
                .findFirst().map(t -> t.getReadyToBuild().values().stream().mapToInt(List::size).sum())
                .orElseThrow();
    }

    @Test
    void testLoadedChunksPlacedWithinBudget() {
        settings.setStructureTimeBudget(10_000);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        createIsland();
        nal.buildStructures();
        verifyPlaced(normalCount);
        assertEquals(0, savedTodoCount());
    }

    @Test
    void testZeroBudgetPlacesOnePerTick() {
        settings.setStructureTimeBudget(0);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        createIsland();
        nal.buildStructures();
        verifyPlaced(1);
        nal.buildStructures();
        verifyPlaced(2);
        assertEquals(normalCount - 2, savedTodoCount());
    }

    @Test
    void testUnloadedChunkWaitsForChunkLoad() {
        settings.setStructureTimeBudget(10_000);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        createIsland();
        nal.buildStructures();
        verifyPlaced(0);
        assertEquals(normalCount, savedTodoCount());
        // The masons house is at 0,64,80 relative to the center, chunk 0,5
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        loadChunk(0, 5);
        nal.buildStructures();
        verifyPlaced(1);
        assertEquals(normalCount - 1, savedTodoCount());
    }

    @Test
    void testMissingStructureDoesNotStopQueue() {
        settings.setStructureTimeBudget(0);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        createIsland();
        when(sm.loadStructure(any(NamespacedKey.class))).thenReturn(null, null, structure); // chunk check and placement
        nal.buildStructures(); // Could not load
        verifyPlaced(0);
        nal.buildStructures();
        verifyPlaced(1);
    }

    @Test
    void testAlreadyPlacedNotPlacedAgain() {
        settings.setStructureTimeBudget(10_000);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        createIsland();
        nal.buildStructures();
        verifyPlaced(normalCount);
        // Same structures queued again, e.g., from an old to-do list
        createIsland();
        nal.buildStructures();
        verifyPlaced(normalCount);
        assertEquals(0, savedTodoCount());
    }

    @Test
    void testIslandDeletedRemovesPending() {
        settings.setStructureTimeBudget(10_000);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        createIsland();
        when(island.inIslandSpace(any(Location.class))).thenReturn(true);
        IslandDeleteEvent e = mock(IslandDeleteEvent.class);
        when(e.getIsland()).thenReturn(island);
        nal.onIslandDeleted(e);
        assertEquals(0, savedTodoCount());
        loadChunk(0, 5);
        nal.buildStructures();
        verifyPlaced(0);
    }

    @Test
    void testPendingLoadedFromDatabase() throws Exception {
        Location loc = new Location(world, 5, 64, 5);
        StructureRecord sr = new StructureRecord("igloo/top", "minecraft:igloo/top", loc, StructureRotation.NONE,
                Mirror.NONE, false, Collections.emptyMap());
        ToBePlacedStructures tbd = new ToBePlacedStructures();
        tbd.setReadyToBuild(new java.util.HashMap<>(Map.of(new Pair<>(0, 0), new ArrayList<>(List.of(sr)))));
        when(h.objectExists("ToDo")).thenReturn(true);
        when(h.loadObject("ToDo")).thenReturn(tbd);
        nal = new NewAreaListener(addon);
        nal.onBentoBoxReady(mock(BentoBoxReadyEvent.class));

        nal.buildStructures();
        verifyPlaced(0);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        loadChunk(0, 0);
        nal.buildStructures();
        verify(structure).place(eq(loc), anyBoolean(), any(StructureRotation.class), any(Mirror.class), anyInt(),
                anyFloat(), any(Random.class));
        // Saved on shutdown with nothing left
        nal.saveNow();
        verify(h).saveObjectNow(any());
    }

    /**
     * Queues every structure, then reports their neighbouring chunks as unloaded.
     */
    private void queueWithUnloadedNeighbours() {
        settings.setStructureTimeBudget(10_000);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        createIsland();
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        nal.buildStructures();
        verifyPlaced(0);
        verify(world, atLeastOnce()).getChunkAtAsync(anyInt(), anyInt());
    }

    @Test
    void testUnloadedChunksLoadedInBackground() {
        queueWithUnloadedNeighbours();
        // Waiting structures are still saved
        assertEquals(normalCount, savedTodoCount());
        // Chunks load
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        chunkFuture.complete(mock(Chunk.class));
        verify(world, atLeastOnce()).addPluginChunkTicket(anyInt(), anyInt(), eq(plugin));
        nal.buildStructures();
        verifyPlaced(normalCount);
        verify(world, atLeastOnce()).removePluginChunkTicket(anyInt(), anyInt(), eq(plugin));
        assertEquals(0, savedTodoCount());
    }

    @Test
    void testDeletedWhileWaitingForChunks() {
        queueWithUnloadedNeighbours();
        when(island.inIslandSpace(any(Location.class))).thenReturn(true);
        IslandDeleteEvent e = mock(IslandDeleteEvent.class);
        when(e.getIsland()).thenReturn(island);
        nal.onIslandDeleted(e);
        assertEquals(0, savedTodoCount());
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        chunkFuture.complete(mock(Chunk.class));
        nal.buildStructures();
        verifyPlaced(0);
        verify(world, atLeastOnce()).removePluginChunkTicket(anyInt(), anyInt(), eq(plugin));
    }

    @Test
    void testChunkLoadFailurePlacesAnyway() {
        queueWithUnloadedNeighbours();
        chunkFuture.completeExceptionally(new IllegalStateException("bad chunk"));
        verify(addon, atLeastOnce()).logError(Mockito.contains("bad chunk"));
        nal.buildStructures();
        verifyPlaced(normalCount);
    }

    @Test
    void testNotReadyPlacesNothing() {
        nal = new NewAreaListener(addon); // not ready yet
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        createIsland();
        nal.buildStructures();
        verify(structure, never()).place(any(Location.class), anyBoolean(), any(StructureRotation.class),
                any(Mirror.class), anyInt(), anyFloat(), any(Random.class));
    }
}
