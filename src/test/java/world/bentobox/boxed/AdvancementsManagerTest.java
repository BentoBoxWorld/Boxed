package world.bentobox.boxed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.beans.IntrospectionException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementDisplay;
import org.bukkit.entity.Player;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.Settings;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.AbstractDatabaseHandler;
import world.bentobox.bentobox.database.DatabaseSetup;
import world.bentobox.bentobox.database.DatabaseSetup.DatabaseType;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.IslandsManager;
import world.bentobox.bentobox.managers.RanksManager;
import world.bentobox.bentobox.util.Util;
import world.bentobox.boxed.objects.IslandAdvancements;

/**
 * @author tastybento
 *
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({Bukkit.class, BentoBox.class, DatabaseSetup.class, Util.class})
public class AdvancementsManagerTest {

    private static AbstractDatabaseHandler<Object> h;
    @Mock
    private BentoBox plugin;
    @Mock
    private Settings pluginSettings;


    @Mock
    private Boxed addon;
    private AdvancementsManager am;
    private File dataFolder;
    @Mock
    private Island island;
    @Mock
    private Player player;
    @Mock
    private Advancement advancement;
    @Mock
    private World world;
    @Mock
    private IslandsManager im;
    @Mock
    private AdvancementDisplay display;

    @SuppressWarnings("unchecked")
    @BeforeClass
    public static void beforeClass() throws IllegalAccessException, InvocationTargetException, IntrospectionException {
        // This has to be done beforeClass otherwise the tests will interfere with each other
        h = mock(AbstractDatabaseHandler.class);
        // Database
        PowerMockito.mockStatic(DatabaseSetup.class);
        DatabaseSetup dbSetup = mock(DatabaseSetup.class);
        when(DatabaseSetup.getDatabase()).thenReturn(dbSetup);
        when(dbSetup.getHandler(any())).thenReturn(h);
        when(h.saveObject(any())).thenReturn(CompletableFuture.completedFuture(true));
    }


    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        when(addon.getPlugin()).thenReturn(plugin);
        // Set up plugin
        Whitebox.setInternalState(BentoBox.class, "instance", plugin);

        // The database type has to be created one line before the thenReturn() to work!
        DatabaseType value = DatabaseType.JSON;
        when(plugin.getSettings()).thenReturn(pluginSettings);
        when(pluginSettings.getDatabaseType()).thenReturn(value);
        // Addon
        dataFolder = new File("dataFolder");
        dataFolder.mkdirs();
        when(addon.getDataFolder()).thenReturn(dataFolder);
        Files.copy(Path.of("src/main/resources/advancements.yml"), Path.of("dataFolder/advancements.yml"));
        when(addon.inWorld(world)).thenReturn(true);
        when(addon.getOverWorld()).thenReturn(world);

        // Island
        when(island.getUniqueId()).thenReturn("uniqueId");

        // Player
        when(player.getWorld()).thenReturn(world);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);


        NamespacedKey key = NamespacedKey.fromString("adventure/honey_block_slide");
        // Advancement
        when(advancement.getKey()).thenReturn(key);
        when(display.getX()).thenReturn(9F);
        when(display.getY()).thenReturn(0F);
        when(advancement.getDisplay()).thenReturn(display);

        // Bukkit
        PowerMockito.mockStatic(Bukkit.class, Mockito.RETURNS_MOCKS);
        when(Bukkit.getAdvancement(any(NamespacedKey.class))).thenReturn(advancement);

        // Island
        when(addon.getIslands()).thenReturn(im);
        when(im.getIsland(world, uuid)).thenReturn(island);
        when(island.getRank(uuid)).thenReturn(RanksManager.MEMBER_RANK);
        when(island.getProtectionRange()).thenReturn(5);


        am = new AdvancementsManager(addon);
    }

    /**
     * @throws java.lang.Exception - exception
     */
    @After
    public void tearDown() throws Exception {
        deleteAll(new File("database"));
        deleteAll(dataFolder);
        User.clearUsers();
        Mockito.framework().clearInlineMocks();
    }

    private static void deleteAll(File file) throws IOException {
        if (file.exists()) {
            Files.walk(file.toPath())
            .sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);
        }
    }

    /**
     * Test method for {@link world.bentobox.boxed.AdvancementsManager#AdvancementsManager(world.bentobox.boxed.Boxed)}.
     * @throws Exception
     */
    @Test
    public void testAdvancementsManagerNoFile() throws Exception {
        tearDown();
        am = new AdvancementsManager(addon);
        verify(addon).logError("advancements.yml cannot be found!");
    }

    /**
     * Test method for {@link world.bentobox.boxed.AdvancementsManager#AdvancementsManager(world.bentobox.boxed.Boxed)}.
     * @throws IOException
     */
    @Test
    public void testAdvancementsManager() throws IOException {
        verify(addon).saveResource("advancements.yml", false);
        verify(addon, never()).logError(anyString());
    }

    /**
     * Test method for {@link world.bentobox.boxed.AdvancementsManager#getIsland(world.bentobox.bentobox.database.objects.Island)}.
     */
    @Test
    public void testGetIsland() {
        @NonNull
        IslandAdvancements adv = am.getIsland(island);
        assertEquals("uniqueId", adv.getUniqueId());
        assertTrue(adv.getAdvancements().isEmpty());
    }

    /**
     * Test method for {@link world.bentobox.boxed.AdvancementsManager#saveIsland(world.bentobox.bentobox.database.objects.Island)}.
     * @throws IntrospectionException
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     */
    @Test
    public void testSaveIslandNotInCache() throws IllegalAccessException, InvocationTargetException, IntrospectionException {
        am.removeFromCache(island);
        am.saveIsland(island);
        verify(island, times(2)).getUniqueId(); // 2x
    }

    /**
     * Test method for {@link world.bentobox.boxed.AdvancementsManager#saveIsland(world.bentobox.bentobox.database.objects.Island)}.
     * @throws IntrospectionException
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     */
    @Test
    public void testSaveIslandInCache() throws IllegalAccessException, InvocationTargetException, IntrospectionException {
        testGetIsland();
        am.saveIsland(island);
        verify(island, times(3)).getUniqueId(); // 3x
    }

    /**
     * Test method for {@link world.bentobox.boxed.AdvancementsManager#save()}.
     * @throws IntrospectionException
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     */
    @Test
    public void testSaveNothingToSave() throws IllegalAccessException, InvocationTargetException, IntrospectionException {
        am.removeFromCache(island);
        am.save();
        verify(island).getUniqueId();
    }

    /**
     * Test method for {@link world.bentobox.boxed.AdvancementsManager#save()}.
     * @throws IntrospectionException
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     */
    @Test
    public void testSave() throws IllegalAccessException, InvocationTargetException, IntrospectionException {
        testGetIsland();
        am.save();
        verify(island).getUniqueId();
    }

    /**
     * Test method for {@link world.bentobox.boxed.AdvancementsManager#addAdvancement(world.bentobox.bentobox.database.objects.Island, java.lang.String)}.
     */
    @Test
    public void testAddAdvancementIslandString() {
        assertTrue(am.addAdvancement(island, "advancement"));
        assertFalse(am.addAdvancement(island, "advancement")); // Second time should fail
    }

    /**
     * Test method for {@link world.bentobox.boxed.AdvancementsManager#removeAdvancement(world.bentobox.bentobox.database.objects.Island, java.lang.String)}.
     */
    @Test
    public void testRemoveAdvancement() {
        assertTrue(am.addAdvancement(island, "advancement"));
        am.removeAdvancement(island, "advancement");
        assertTrue(am.addAdvancement(island, "advancement")); // Should work because it was removed
    }

    /**
     * Test method for {@link world.bentobox.boxed.AdvancementsManager#hasAdvancement(world.bentobox.bentobox.database.objects.Island, java.lang.String)}.
     */
    @Test
    public void testHasAdvancement() {
        assertFalse(am.hasAdvancement(island, "advancement"));
        am.addAdvancement(island, "advancement");
        assertTrue(am.hasAdvancement(island, "advancement"));
    }

    /**
     * Test method for {@link world.bentobox.boxed.AdvancementsManager#checkIslandSize(world.bentobox.bentobox.database.objects.Island)}.
     */
    @Test
    public void testCheckIslandSize() {
        // Island protection size is set to 5, but after checking, the size is reduced by 4
        assertEquals(-4, am.checkIslandSize(island));
    }

    /**
     * Test method for {@link world.bentobox.boxed.AdvancementsManager#addAdvancement(org.bukkit.entity.Player, org.bukkit.advancement.Advancement)}.
     */
    @Test
    public void testAddAdvancementPlayerAdvancementWrongWorld() {
        when(addon.inWorld(world)).thenReturn(false);
        assertEquals(0, am.addAdvancement(player, advancement));
    }

    /**
     * Test method for {@link world.bentobox.boxed.AdvancementsManager#addAdvancement(org.bukkit.entity.Player, org.bukkit.advancement.Advancement)}.
     */
    @Test
    public void testAddAdvancementPlayerAdvancement() {
        assertEquals(9, am.addAdvancement(player, advancement));
        verify(island).setProtectionRange(14); // (9 + 5)
    }

    /**
     * Test method for {@link world.bentobox.boxed.AdvancementsManager#addAdvancement(org.bukkit.entity.Player, org.bukkit.advancement.Advancement)}.
     */
    @Test
    public void testAddAdvancementPlayerAdvancementZeroScore() {
        when(display.getX()).thenReturn(0F);
        assertEquals(0, am.addAdvancement(player, advancement));
        verify(island, never()).setProtectionRange(anyInt());
    }

    /**
     * Test method for {@link world.bentobox.boxed.AdvancementsManager#getScore(java.lang.String)}.
     */
    @Test
    public void testGetScoreString() {
        assertEquals(9, am.getScore("adventure/lightning_rod_with_villager_no_fire"));
    }

    /**
     * Test method for {@link world.bentobox.boxed.AdvancementsManager#getScore(org.bukkit.advancement.Advancement)}.
     */
    @Test
    public void testGetScoreAdvancement() {
        assertEquals(9, am.getScore(advancement));
    }

}
