package world.bentobox.boxed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.papermc.paper.advancement.AdvancementDisplay;
import world.bentobox.bentobox.database.AbstractDatabaseHandler;
import world.bentobox.bentobox.database.DatabaseSetup;
import world.bentobox.bentobox.database.DatabaseSetup.DatabaseType;
import world.bentobox.bentobox.managers.RanksManager;
import world.bentobox.boxed.objects.IslandAdvancements;

/**
 * @author tastybento
 *
 */
public class AdvancementsManagerTest extends CommonTestSetup {

    @Mock
    private world.bentobox.bentobox.Settings pluginSettings;
    @Mock
    private Boxed addon;
    private AdvancementsManager am;
    private File dataFolder;
    @Mock
    private Player player;
    @Mock
    private Advancement advancement;
    @Mock
    private AdvancementDisplay display;

    private MockedStatic<DatabaseSetup> mockedDatabaseSetup;
    private AbstractDatabaseHandler<Object> h;

    @SuppressWarnings("unchecked")
    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        // Database static mock (local — CommonTestSetup does not handle this one)
        h = mock(AbstractDatabaseHandler.class);
        mockedDatabaseSetup = Mockito.mockStatic(DatabaseSetup.class);
        DatabaseSetup dbSetup = mock(DatabaseSetup.class);
        mockedDatabaseSetup.when(DatabaseSetup::getDatabase).thenReturn(dbSetup);
        when(dbSetup.getHandler(any())).thenReturn(h);
        when(h.saveObject(any())).thenReturn(CompletableFuture.completedFuture(true));

        when(addon.getPlugin()).thenReturn(plugin);

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
        when(advancement.getDisplay()).thenReturn(display);

        // Bukkit
        mockedBukkit.when(() -> org.bukkit.Bukkit.getAdvancement(any(NamespacedKey.class))).thenReturn(advancement);

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
    @Override
    @AfterEach
    public void tearDown() throws Exception {
        mockedDatabaseSetup.closeOnDemand();
        deleteAll(dataFolder);
        super.tearDown();
    }

    /**
     * Test method for {@link world.bentobox.boxed.AdvancementsManager#AdvancementsManager(world.bentobox.boxed.Boxed)}.
     * @throws Exception
     */
    @Test
    public void testAdvancementsManagerNoFile() throws Exception {
        // Delete the advancements.yml file so the constructor logs an error. Do NOT tear
        // down the full mock infrastructure — we still need it for the second manager.
        deleteAll(dataFolder);
        am = new AdvancementsManager(addon);
        verify(addon).logError("advancements.yml cannot be found!");
    }

    /**
     * Test method for {@link world.bentobox.boxed.AdvancementsManager#AdvancementsManager(world.bentobox.boxed.Boxed)}.
     * @throws IOException
     */
    @Test
    public void testAdvancementsManager() {
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
    public void testSaveIslandNotInCache() {
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
    public void testSaveIslandInCache() {
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
    public void testSaveNothingToSave() {
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
    public void testSave() {
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
     * A null display means the advancement cannot be scored automatically.
     */
    @Test
    public void testAddAdvancementPlayerAdvancementZeroScore() {
        when(advancement.getDisplay()).thenReturn(null);
        assertEquals(0, am.addAdvancement(player, advancement));
        verify(island, never()).setProtectionRange(org.mockito.ArgumentMatchers.anyInt());
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

    /**
     * Test method for {@link world.bentobox.boxed.AdvancementsManager#getScore(org.bukkit.advancement.Advancement)}.
     * Root advancements fall back to settings.default-root-increase (0 in the shipped config).
     */
    @Test
    public void testGetScoreAdvancementRoot() {
        when(advancement.getKey()).thenReturn(NamespacedKey.fromString("story/root"));
        assertEquals(0, am.getScore(advancement));
    }

    /**
     * Test method for {@link world.bentobox.boxed.AdvancementsManager#getScore(org.bukkit.advancement.Advancement)}.
     * Recipe advancements always score settings.unknown-recipe-increase (0 in the shipped config).
     */
    @Test
    public void testGetScoreAdvancementRecipe() {
        when(advancement.getKey()).thenReturn(NamespacedKey.fromString("recipes/brewing/blaze_powder"));
        assertEquals(0, am.getScore(advancement));
    }

    /**
     * Test method for {@link world.bentobox.boxed.AdvancementsManager#addAdvancement(org.bukkit.entity.Player, org.bukkit.advancement.Advancement)}.
     * No island for this player means no expansion and a zero score.
     */
    @Test
    public void testAddAdvancementPlayerAdvancementNullIsland() {
        when(im.getIsland(world, player.getUniqueId())).thenReturn(null);
        assertEquals(0, am.addAdvancement(player, advancement));
        verify(island, never()).setProtectionRange(org.mockito.ArgumentMatchers.anyInt());
    }

    /**
     * Test method for {@link world.bentobox.boxed.AdvancementsManager#addAdvancement(org.bukkit.entity.Player, org.bukkit.advancement.Advancement)}.
     * Visitors (rank below MEMBER_RANK) cannot expand the island.
     */
    @Test
    public void testAddAdvancementPlayerAdvancementVisitorRank() {
        when(island.getRank(player.getUniqueId())).thenReturn(RanksManager.VISITOR_RANK);
        assertEquals(0, am.addAdvancement(player, advancement));
        verify(island, never()).setProtectionRange(org.mockito.ArgumentMatchers.anyInt());
    }

    /**
     * Test method for {@link world.bentobox.boxed.AdvancementsManager#addAdvancement(org.bukkit.entity.Player, org.bukkit.advancement.Advancement)}.
     * An advancement that's already been recorded on the island cannot grant a second expansion.
     */
    @Test
    public void testAddAdvancementPlayerAdvancementAlreadyHas() {
        // Seed the island with the exact same namespaced key the manager will try to record.
        am.addAdvancement(island, advancement.getKey().toString());
        assertEquals(0, am.addAdvancement(player, advancement));
    }

    /**
     * Test method for {@link world.bentobox.boxed.AdvancementsManager#checkIslandSize(world.bentobox.bentobox.database.objects.Island)}.
     * Positive diff case: one scoring advancement grows a size-1 island to size 10.
     */
    @Test
    public void testCheckIslandSizePositiveDiff() {
        when(island.getProtectionRange()).thenReturn(1);
        am.addAdvancement(island, "adventure/honey_block_slide");
        assertEquals(9, am.checkIslandSize(island));
        verify(island).setProtectionRange(10);
    }

}
