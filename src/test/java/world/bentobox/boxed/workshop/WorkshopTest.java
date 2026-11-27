package world.bentobox.boxed.workshop;

import world.bentobox.boxed.generators.biomes.BiomePatch;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.World.Environment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests that an area being worked on is saved and read back, and the names of biome styles.
 */
class WorkshopTest {

    @TempDir
    File folder;

    @Test
    void testPatchesAreSaved() throws IOException {
        Workshop w = new Workshop(1L, "biomes/none.yml", null);
        assertTrue(w.getPatches(Environment.NORMAL).isEmpty());
        w.setPatches(Environment.NORMAL, List.of(new BiomePatch(40, -40, 48, "swamp"), new BiomePatch(0, 9, 20, "desert")));
        w.setPatches(Environment.NETHER, List.of(new BiomePatch(8, 8, 30, "warped_forest")));
        File file = new File(folder, "areas/workshop.yml");
        w.save(file);

        Workshop loaded = Workshop.load(file);
        assertNotNull(loaded);
        assertEquals(List.of(new BiomePatch(40, -40, 48, "swamp"), new BiomePatch(0, 9, 20, "desert")),
                loaded.getPatches(Environment.NORMAL));
        assertEquals(List.of(new BiomePatch(8, 8, 30, "warped_forest")), loaded.getPatches(Environment.NETHER));
    }

    @Test
    void testStructuresAreSaved() throws IOException {
        Workshop w = new Workshop(5L, "biomes/none.yml", "meadow");
        assertTrue(w.getStructures(Environment.NORMAL).isEmpty());
        assertFalse(w.isToShow(Environment.NORMAL));
        PlacedStructure igloo = new PlacedStructure(100, 65, -40, "igloo/top",
                org.bukkit.block.structure.StructureRotation.CLOCKWISE_90, org.bukkit.block.structure.Mirror.NONE, true);
        w.setStructures(Environment.NORMAL, List.of(igloo));
        w.setToShow(Environment.NORMAL, true);
        w.setToShow(Environment.NETHER, true);
        w.setToShow(Environment.NETHER, false);
        File file = new File(folder, "structures.yml");
        w.save(file);
        Workshop loaded = Workshop.load(file);
        assertEquals(List.of(igloo), loaded.getStructures(Environment.NORMAL));
        assertTrue(loaded.getStructures(Environment.NETHER).isEmpty());
        assertTrue(loaded.isToShow(Environment.NORMAL));
        assertFalse(loaded.isToShow(Environment.NETHER));
    }

    @Test
    void testSaveAndLoad() throws IOException {
        UUID uuid = UUID.randomUUID();
        Workshop w = new Workshop(-42L, "biomes/natural.yml", "icelands");
        w.setCentre(Environment.NORMAL, 1664, -1536);
        w.addPlayer(uuid, GameMode.SURVIVAL);
        // The first game mode is the one from outside, so it is kept
        w.addPlayer(uuid, GameMode.CREATIVE);
        File file = new File(folder, "areas/workshop.yml");
        w.save(file);

        Workshop loaded = Workshop.load(file);
        assertNotNull(loaded);
        assertEquals(-42L, loaded.getSeed());
        assertEquals("biomes/natural.yml", loaded.getBiomes());
        assertEquals("icelands", loaded.getArea());
        assertArrayEquals(new int[] { 1664, -1536 }, loaded.getCentre(Environment.NORMAL));
        assertArrayEquals(new int[] { 0, 0 }, loaded.getCentre(Environment.NETHER));
        assertEquals(GameMode.SURVIVAL, loaded.getPlayers().get(uuid));
        assertEquals(GameMode.SURVIVAL, loaded.removePlayer(uuid));
        assertNull(loaded.removePlayer(uuid));
    }

    @Test
    void testSpotsAreSavedInOrder() throws IOException {
        Workshop w = new Workshop(1L, "biomes.yml", null);
        assertFalse(w.hasCentre(Environment.NORMAL));
        w.setSpots(List.of(new Workshop.Spot(0, 0, "plains", 90, "dry land, wood at size 3"),
                new Workshop.Spot(-1280, 640, "forest", -1, "")));
        w.setCentre(Environment.NORMAL, 0, 0);
        File file = new File(folder, "workshop.yml");
        w.save(file);
        Workshop loaded = Workshop.load(file);
        assertNotNull(loaded);
        assertTrue(loaded.hasCentre(Environment.NORMAL));
        assertEquals(w.getSpots(), loaded.getSpots());
        assertTrue(loaded.getSpots().get(0).scored());
        assertFalse(loaded.getSpots().get(1).scored());
        // A place is filled in when its land has been looked at
        loaded.setSpot(1, new Workshop.Spot(-1280, 640, "forest", 77, "wet"));
        assertEquals(77, loaded.getSpots().get(1).score());
    }

    @Test
    void testNewAreaHasNoName() throws IOException {
        File file = new File(folder, "workshop.yml");
        new Workshop(7L, "biomes.yml", null).save(file);
        Workshop loaded = Workshop.load(file);
        assertNotNull(loaded);
        assertNull(loaded.getArea());
        assertTrue(loaded.getPlayers().isEmpty());
    }

    @Test
    void testLoadNothing() throws IOException {
        assertNull(Workshop.load(new File(folder, "missing.yml")));
        File junk = new File(folder, "junk.yml");
        Files.writeString(junk.toPath(), "hello: world\n");
        assertNull(Workshop.load(junk));
    }

    @Test
    void testBadEntriesAreIgnored() throws IOException {
        File file = new File(folder, "workshop.yml");
        Files.writeString(file.toPath(), """
                seed: 5
                biomes: biomes.yml
                centres:
                  normal: here
                  moon: 1,2
                players:
                  nobody: SURVIVAL
                """);
        Workshop loaded = Workshop.load(file);
        assertNotNull(loaded);
        assertArrayEquals(new int[] { 0, 0 }, loaded.getCentre(Environment.NORMAL));
        assertTrue(loaded.getPlayers().isEmpty());
    }

    @Test
    void testBiomeStyles() throws IOException {
        Files.createDirectories(new File(folder, "biomes").toPath());
        Files.writeString(new File(folder, "biomes/rings.yml").toPath(), "");
        Files.writeString(new File(folder, "biomes/natural.yml").toPath(), "");
        Files.writeString(new File(folder, "biomes/notes.txt").toPath(), "");
        Files.writeString(new File(folder, "biomes-icelands.yml").toPath(), "");

        assertEquals(List.of("original", "natural", "rings"), BiomeStyles.list(folder));
        assertEquals("biomes.yml", BiomeStyles.file(folder, "Original"));
        assertEquals("biomes/rings.yml", BiomeStyles.file(folder, "RINGS"));
        // A biomes file that an older area uses can be named in full
        assertEquals("biomes-icelands.yml", BiomeStyles.file(folder, "biomes-icelands.yml"));
        assertNull(BiomeStyles.file(folder, "nosuch"));
        assertNull(BiomeStyles.file(folder, "../config.yml"));
        assertNull(BiomeStyles.file(folder, "biomes/rings"));

        assertEquals("original", BiomeStyles.name("biomes.yml"));
        assertEquals("rings", BiomeStyles.name("biomes/rings.yml"));
        assertEquals("biomes-icelands.yml", BiomeStyles.name("biomes-icelands.yml"));
    }
}
