package world.bentobox.boxed.listeners;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Random;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.CommonTestSetup;
import world.bentobox.boxed.Settings;

/**
 * Tests the swapping of Nether mobs for fortress mobs.
 */
class FortressMobsListenerTest extends CommonTestSetup {

    @Mock
    private Boxed addon;
    @Mock
    private Random random;
    @Mock
    private CreatureSpawnEvent event;
    @Mock
    private Block feet;
    @Mock
    private Block floor;
    @Mock
    private Block above;

    private Settings settings;
    private FortressMobsListener listener;

    @Override
    @BeforeEach
    public void setUp() {
        super.setUp();
        settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        when(addon.getNetherWorld()).thenReturn(world);
        when(location.getWorld()).thenReturn(world);
        when(location.getBlock()).thenReturn(feet);
        when(feet.getRelative(BlockFace.DOWN)).thenReturn(floor);
        when(feet.getRelative(BlockFace.UP)).thenReturn(above);
        when(feet.getRelative(BlockFace.UP, 2)).thenReturn(above);
        when(above.isPassable()).thenReturn(true);
        when(floor.getType()).thenReturn(Material.NETHER_BRICKS);
        when(feet.getLightFromBlocks()).thenReturn((byte) 0);
        when(event.getLocation()).thenReturn(location);
        when(event.getSpawnReason()).thenReturn(SpawnReason.NATURAL);
        when(event.getEntityType()).thenReturn(EntityType.ZOMBIFIED_PIGLIN);
        listener = new FortressMobsListener(addon, random);
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private void roll(int value) {
        when(random.nextInt(28)).thenReturn(value);
    }

    private void verifySwappedFor(EntityType type) {
        verify(event).setCancelled(true);
        verify(world).spawnEntity(location, type, SpawnReason.NATURAL);
    }

    private void verifyLeftAlone() {
        verify(event, never()).setCancelled(true);
        verify(world, never()).spawnEntity(any(), any(EntityType.class), any(SpawnReason.class));
    }

    @Test
    void testWitherSkeleton() {
        roll(0);
        listener.onSpawn(event);
        verifySwappedFor(EntityType.WITHER_SKELETON);
    }

    @Test
    void testBlaze() {
        roll(8);
        // Blazes spawn in more light than skeletons
        when(feet.getLightFromBlocks()).thenReturn((byte) 11);
        listener.onSpawn(event);
        verifySwappedFor(EntityType.BLAZE);
    }

    @Test
    void testSkeleton() {
        roll(18);
        listener.onSpawn(event);
        verifySwappedFor(EntityType.SKELETON);
    }

    @Test
    void testMostSpawnsAreLeftAlone() {
        roll(20);
        listener.onSpawn(event);
        verifyLeftAlone();
    }

    @Test
    void testNotOnNetherBricks() {
        roll(0);
        when(floor.getType()).thenReturn(Material.NETHERRACK);
        listener.onSpawn(event);
        verifyLeftAlone();
    }

    @Test
    void testNotInTheLight() {
        roll(0);
        when(feet.getLightFromBlocks()).thenReturn((byte) 12);
        listener.onSpawn(event);
        verifyLeftAlone();
    }

    @Test
    void testNoRoomForAWitherSkeleton() {
        roll(0);
        when(above.isPassable()).thenReturn(false);
        listener.onSpawn(event);
        verifyLeftAlone();
    }

    @Test
    void testOtherWorldsAndSpawnReasons() {
        roll(0);
        when(addon.getNetherWorld()).thenReturn(mock(World.class));
        listener.onSpawn(event);
        when(addon.getNetherWorld()).thenReturn(world);
        when(event.getSpawnReason()).thenReturn(SpawnReason.SPAWNER);
        listener.onSpawn(event);
        verifyLeftAlone();
    }

    @Test
    void testSwitchedOff() {
        roll(0);
        settings.setNetherFortressMobs(false);
        listener.onSpawn(event);
        verifyLeftAlone();
    }

    @Test
    void testSameMobIsNotSwapped() {
        roll(18);
        when(event.getEntityType()).thenReturn(EntityType.SKELETON);
        listener.onSpawn(event);
        verifyLeftAlone();
    }
}
