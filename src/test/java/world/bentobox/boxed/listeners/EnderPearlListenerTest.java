package world.bentobox.boxed.listeners;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import com.google.common.collect.ImmutableSet;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.IslandWorldManager;
import world.bentobox.bentobox.managers.IslandsManager;
import world.bentobox.bentobox.util.Util;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.Settings;

/**
 * @author tastybento
 *
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({Bukkit.class, BentoBox.class, User.class, Util.class })
public class EnderPearlListenerTest {

    @Mock
    private BentoBox plugin;
    @Mock
    private Boxed addon;
    @Mock
    private Player player;
    @Mock
    private Location from;
    @Mock
    private Location to;
    @Mock
    private World world;
    @Mock
    private IslandsManager im;
    @Mock
    private Island island;
    @Mock
    private Island anotherIsland;
    @Mock
    private Island spawn;
    @Mock
    private User user;
    @Mock
    private EnderPearl projectile;
    @Mock
    private Block hitBlock;
    
    private Settings settings;
    private EnderPearlListener epl;
    @Mock
    private IslandWorldManager iwm;


    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        
        // Set up plugin
        plugin = mock(BentoBox.class);
        Whitebox.setInternalState(BentoBox.class, "instance", plugin);
        
        when(plugin.getIWM()).thenReturn(iwm);
        

        
        PowerMockito.mockStatic(Bukkit.class, Mockito.RETURNS_MOCKS);
        
        PowerMockito.mockStatic(User.class, Mockito.RETURNS_MOCKS);
        when(User.getInstance(any(Player.class))).thenReturn(user);
        // Settings
        settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        when(iwm.getWorldSettings(world)).thenReturn(settings);
        when(iwm.inWorld(world)).thenReturn(true);
        
        // Locations
        when(to.getWorld()).thenReturn(world);
        when(from.getWorld()).thenReturn(world);
        when(from.toVector()).thenReturn(new Vector(1,2,3));
        when(to.toVector()).thenReturn(new Vector(6,7,8));
        when(world.getEnvironment()).thenReturn(Environment.NORMAL);
        
        // In game world
        when(addon.inWorld(any(World.class))).thenReturn(true);
        when(addon.inWorld(any(Location.class))).thenReturn(true);
        
        // User
        when(user.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getWorld()).thenReturn(world);
        when(user.getMetaData(anyString())).thenReturn(Optional.empty()); // No meta data
        when(player.getLocation()).thenReturn(from);
        when(user.getLocation()).thenReturn(from);
        
        // Islands
        when(island.onIsland(any())).thenReturn(true); // Default on island
        when(im.getIsland(world, user)).thenReturn(island);
        when(im.getIslandAt(any())).thenReturn(Optional.of(island));
        when(addon.getIslands()).thenReturn(im);
        when(im.getProtectedIslandAt(any())).thenReturn(Optional.of(island));
        when(island.getUniqueId()).thenReturn("uniqueID");
        when(island.getProtectionCenter()).thenReturn(from);
        when(island.getProtectionBoundingBox()).thenReturn(BoundingBox.of(new Vector(0,0,0), new Vector(50,50,50)));
        when(island.getRange()).thenReturn(3);
        when(im.isSafeLocation(any())).thenReturn(true); // safe for now
        when(island.getPlayersOnIsland()).thenReturn(List.of(player));
        when(im.getSpawn(any())).thenReturn(Optional.of(spawn));
        when(island.onIsland(from)).thenReturn(true);
        when(island.onIsland(to)).thenReturn(false);
        when(island.getMemberSet()).thenReturn(ImmutableSet.of(UUID.randomUUID()));
        // Another island
        when(anotherIsland.getUniqueId()).thenReturn("another_uniqueID");
        
        // Projectiles
        when(projectile.getType()).thenReturn(EntityType.ENDER_PEARL);
        when(projectile.getShooter()).thenReturn(player);
        when(hitBlock.getLocation()).thenReturn(to);
        when(hitBlock.getWorld()).thenReturn(world);
        when(hitBlock.getRelative(BlockFace.UP)).thenReturn(hitBlock);
        Boxed.ALLOW_MOVE_BOX.setSetting(world, true);
        
        epl = new EnderPearlListener(addon);
    }

    /**
     * Test method for {@link world.bentobox.boxed.listeners.EnderPearlListener#EnderPearlListener(world.bentobox.boxed.Boxed)}.
     */
    @Test
    public void testEnderPearlListener() {
        assertNotNull(epl);
    }

    /**
     * Test method for {@link world.bentobox.boxed.listeners.EnderPearlListener#onPlayerTeleport(org.bukkit.event.player.PlayerTeleportEvent)}.
     */
    @Test
    public void testOnPlayerTeleportNotAllowed() {
        PlayerTeleportEvent e = new PlayerTeleportEvent(player, from, to, TeleportCause.CHORUS_FRUIT);
        epl.onPlayerTeleport(e);
        assertTrue(e.isCancelled());
        verify(user).sendMessage("boxed.general.errors.no-teleport-outside");
    }
    
    /**
     * Test method for {@link world.bentobox.boxed.listeners.EnderPearlListener#onPlayerTeleport(org.bukkit.event.player.PlayerTeleportEvent)}.
     */
    @Test
    public void testOnPlayerTeleportNotSurvival() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        PlayerTeleportEvent e = new PlayerTeleportEvent(player, from, to, TeleportCause.CHORUS_FRUIT);
        epl.onPlayerTeleport(e);
        assertFalse(e.isCancelled());
        verify(user, never()).sendMessage("boxed.general.errors.no-teleport-outside");
    }
    
    /**
     * Test method for {@link world.bentobox.boxed.listeners.EnderPearlListener#onPlayerTeleport(org.bukkit.event.player.PlayerTeleportEvent)}.
     */
    @Test
    public void testOnPlayerTeleportNullTo() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        PlayerTeleportEvent e = new PlayerTeleportEvent(player, from, null, TeleportCause.CHORUS_FRUIT);
        epl.onPlayerTeleport(e);
        assertFalse(e.isCancelled());
        verify(user, never()).sendMessage("boxed.general.errors.no-teleport-outside");
    }
    
    /**
     * Test method for {@link world.bentobox.boxed.listeners.EnderPearlListener#onPlayerTeleport(org.bukkit.event.player.PlayerTeleportEvent)}.
     */
    @Test
    public void testOnPlayerTeleportToSpawn() {
        when(spawn.onIsland(any())).thenReturn(true);
        PlayerTeleportEvent e = new PlayerTeleportEvent(player, from, to, TeleportCause.CHORUS_FRUIT);
        epl.onPlayerTeleport(e);
        assertFalse(e.isCancelled());
        verify(user, never()).sendMessage("boxed.general.errors.no-teleport-outside");
    }
    
    /**
     * Test method for {@link world.bentobox.boxed.listeners.EnderPearlListener#onPlayerTeleport(org.bukkit.event.player.PlayerTeleportEvent)}.
     */
    @Test
    public void testOnPlayerTeleportNotInWorldAllowed() {
        when(addon.inWorld(any(World.class))).thenReturn(false);
        when(addon.inWorld(any(Location.class))).thenReturn(false);
        PlayerTeleportEvent e = new PlayerTeleportEvent(player, from, to, TeleportCause.CHORUS_FRUIT);
        epl.onPlayerTeleport(e);
        assertFalse(e.isCancelled());
        verify(user, never()).sendMessage("boxed.general.errors.no-teleport-outside");
    }

    /**
     * Test method for {@link world.bentobox.boxed.listeners.EnderPearlListener#onEnderPearlLand(org.bukkit.event.entity.ProjectileHitEvent)}.
     * @throws IOException 
     */
    @Test
    public void testOnEnderPearlLandNotEnderPearl() throws IOException {
        when(projectile.getType()).thenReturn(EntityType.ARROW);
        ProjectileHitEvent e = new ProjectileHitEvent(projectile, null, hitBlock, BlockFace.UP);
        epl.onEnderPearlLand(e);
        assertFalse(e.isCancelled());
        verify(user, never()).sendMessage("boxed.general.errors.no-teleport-outside");
        verifyFailure();
    }
    
    /**
     * Test method for {@link world.bentobox.boxed.listeners.EnderPearlListener#onEnderPearlLand(org.bukkit.event.entity.ProjectileHitEvent)}.
     * @throws IOException 
     */
    @Test
    public void testOnEnderPearlLandNullHitBlock() throws IOException {
        ProjectileHitEvent e = new ProjectileHitEvent(projectile, null, null, BlockFace.UP);
        epl.onEnderPearlLand(e);
        assertFalse(e.isCancelled());
        verify(user, never()).sendMessage("boxed.general.errors.no-teleport-outside");
        verifyFailure();
    }
    
    /**
     * Test method for {@link world.bentobox.boxed.listeners.EnderPearlListener#onEnderPearlLand(org.bukkit.event.entity.ProjectileHitEvent)}.
     * @throws IOException 
     */
    @Test
    public void testOnEnderPearlLandNotInWorld() throws IOException {
        when(addon.inWorld(to)).thenReturn(false);
        ProjectileHitEvent e = new ProjectileHitEvent(projectile, null, hitBlock, BlockFace.UP);
        epl.onEnderPearlLand(e);
        assertFalse(e.isCancelled());
        verify(user, never()).sendMessage("boxed.general.errors.no-teleport-outside");
        verifyFailure();
    }

    /**
     * Test method for {@link world.bentobox.boxed.listeners.EnderPearlListener#onEnderPearlLand(org.bukkit.event.entity.ProjectileHitEvent)}.
     * @throws IOException 
     */
    @Test
    public void testOnEnderPearlLandNotMovingBox() throws IOException {
        Boxed.ALLOW_MOVE_BOX.setSetting(world, false);
        ProjectileHitEvent e = new ProjectileHitEvent(projectile, null, hitBlock, BlockFace.UP);
        epl.onEnderPearlLand(e);
        assertFalse(e.isCancelled());
        verify(user, never()).sendMessage("boxed.general.errors.no-teleport-outside");
        verifyFailure();
    }
    
    /**
     * Test method for {@link world.bentobox.boxed.listeners.EnderPearlListener#onEnderPearlLand(org.bukkit.event.entity.ProjectileHitEvent)}.
     * @throws IOException 
     */
    @Test
    public void testOnEnderPearlLandNonHuman() throws IOException {
        Creeper creeper = mock(Creeper.class);
        when(projectile.getShooter()).thenReturn(creeper);
        ProjectileHitEvent e = new ProjectileHitEvent(projectile, null, hitBlock, BlockFace.UP);
        epl.onEnderPearlLand(e);
        assertFalse(e.isCancelled());
        verify(user, never()).sendMessage("boxed.general.errors.no-teleport-outside");
        verifyFailure();
    }
    
    /**
     * Test method for {@link world.bentobox.boxed.listeners.EnderPearlListener#onEnderPearlLand(org.bukkit.event.entity.ProjectileHitEvent)}.
     * @throws IOException 
     */
    @Test
    public void testOnEnderPearlLandUserHasNoIsland() throws IOException {
        when(im.getIsland(world, user)).thenReturn(null);
        ProjectileHitEvent e = new ProjectileHitEvent(projectile, null, hitBlock, BlockFace.UP);
        epl.onEnderPearlLand(e);
        assertFalse(e.isCancelled());
        verify(user, never()).sendMessage("boxed.general.errors.no-teleport-outside");
        verifyFailure();
    }
    
    /**
     * Test method for {@link world.bentobox.boxed.listeners.EnderPearlListener#onEnderPearlLand(org.bukkit.event.entity.ProjectileHitEvent)}.
     * @throws IOException 
     */
    @Test
    public void testOnEnderPearlNotOnIslandWhenThrowing() throws IOException {
        when(im.getIslandAt(any())).thenReturn(Optional.empty());
        ProjectileHitEvent e = new ProjectileHitEvent(projectile, null, hitBlock, BlockFace.UP);
        epl.onEnderPearlLand(e);
        assertFalse(e.isCancelled());
        verifyFailure();
    }
    
    private void verifyFailure() throws IOException {
        verify(user, never()).sendMessage("boxed.general.errors.no-teleport-outside");
        verify(im, never()).setHomeLocation(any(UUID.class), any());
        verify(island, never()).setProtectionCenter(any());
        verify(island, never()).setSpawnPoint(any(), any());
        verify(player, never()).playSound(any(Location.class), any(Sound.class), anyFloat(), anyFloat());
    }
    
    /**
     * Test method for {@link world.bentobox.boxed.listeners.EnderPearlListener#onEnderPearlLand(org.bukkit.event.entity.ProjectileHitEvent)}.
     * @throws IOException 
     */
    @Test
    public void testOnEnderPearlLandHuman() throws IOException {
        ProjectileHitEvent e = new ProjectileHitEvent(projectile, null, hitBlock, BlockFace.UP);
        epl.onEnderPearlLand(e);
        assertFalse(e.isCancelled());
        verify(user, never()).sendMessage("boxed.general.errors.no-teleport-outside");
        verify(im).setHomeLocation(any(UUID.class), eq(to));
        verify(island).setProtectionCenter(to);
        verify(island).setSpawnPoint(Environment.NORMAL, to);
        verify(player).playSound(to, Sound.ENTITY_GENERIC_EXPLODE, 2F, 2F);
    }
    
    /**
     * Test method for {@link world.bentobox.boxed.listeners.EnderPearlListener#onEnderPearlLand(org.bukkit.event.entity.ProjectileHitEvent)}.
     * @throws IOException 
     */
    @Test
    public void testOnEnderPearlThrewToDifferentIsland() throws IOException {
        when(im.getIslandAt(eq(to))).thenReturn(Optional.of(anotherIsland));
        ProjectileHitEvent e = new ProjectileHitEvent(projectile, null, hitBlock, BlockFace.UP);
        epl.onEnderPearlLand(e);
        assertTrue(e.isCancelled());
        verify(user).sendMessage("boxed.general.errors.no-teleport-outside");
    }
    
    /**
     * Test method for {@link world.bentobox.boxed.listeners.EnderPearlListener#onEnderPearlLand(org.bukkit.event.entity.ProjectileHitEvent)}.
     * @throws IOException 
     */
    @Test
    public void testOnEnderPearlThrewToNonIsland() throws IOException {
        when(im.getIslandAt(eq(to))).thenReturn(Optional.empty());
        ProjectileHitEvent e = new ProjectileHitEvent(projectile, null, hitBlock, BlockFace.UP);
        epl.onEnderPearlLand(e);
        assertTrue(e.isCancelled());
        verify(user).sendMessage("boxed.general.errors.no-teleport-outside");
    }
    
    /**
     * Test method for {@link world.bentobox.boxed.listeners.EnderPearlListener#onEnderPearlLand(org.bukkit.event.entity.ProjectileHitEvent)}.
     * @throws IOException 
     */
    @Test
    public void testOnEnderPearlCannotSetProtectionCenter() throws IOException {
        doThrow(IOException.class).when(island).setProtectionCenter(to);
        ProjectileHitEvent e = new ProjectileHitEvent(projectile, null, hitBlock, BlockFace.UP);
        epl.onEnderPearlLand(e);
        assertFalse(e.isCancelled());
        verify(addon).logError("Could not move box null");
    }
}
