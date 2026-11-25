package world.bentobox.boxed.generators.template;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import world.bentobox.boxed.generators.template.RawChunk.Placed;

/**
 * Tests the stored form of templates: {@link RawChunk} and {@link TemplateFile}.
 */
class TemplateFileTest {

    @TempDir
    Path dir;

    private static RawChunk sampleChunk() {
        short[][] sections = new short[3][];
        sections[1] = new short[RawChunk.SECTION_BLOCKS];
        sections[1][RawChunk.blockIndex(3, 4, 5)] = 1;
        return new RawChunk(-16, List.of("minecraft:air", "minecraft:stone", "minecraft:deepslate"), sections,
                new int[] { 2, 0, 0 },
                List.of(new Placed(1, 70, 2, "{\"type\":\"COW\"}")), List.of(new Placed(3, 64, 4, "{}")));
    }

    private static RawBiomes sampleBiomes() {
        byte[] cells = new byte[12 * 16];
        cells[RawBiomes.index(4, 20, 8)] = 1;
        return new RawBiomes(-16, List.of("minecraft:plains", "minecraft:desert"), cells);
    }

    @Test
    void testRawChunkRoundTrip() throws IOException {
        RawChunk in = sampleChunk();
        RawChunk out = RawChunk.decode(in.encode());
        assertEquals(in.minY(), out.minY());
        assertEquals(in.blockPalette(), out.blockPalette());
        assertNull(out.sections()[0]);
        assertArrayEquals(in.sections()[1], out.sections()[1]);
        assertArrayEquals(in.uniform(), out.uniform());
        assertEquals(in.entities(), out.entities());
        assertEquals(in.tiles(), out.tiles());
    }

    @Test
    void testFileRoundTrip() throws IOException {
        File file = dir.resolve("area/normal.bxt").toFile();
        byte[] a = sampleChunk().encode();
        byte[] b = { 1, 2, 3 };
        TemplateFile.Writer writer = new TemplateFile.Writer(file, 2, 1234L);
        writer.put(-2, -2, a, sampleBiomes());
        writer.put(1, 1, b, new RawBiomes(0, List.of("minecraft:ocean"), new byte[16]));
        // Nothing is visible until the build is finished
        assertFalse(file.exists());
        writer.close();
        assertTrue(file.exists());

        try (TemplateFile template = new TemplateFile(file)) {
            assertEquals(2, template.getSize());
            assertEquals(1234L, template.getSeed());
            assertArrayEquals(a, template.read(-2, -2));
            assertArrayEquals(b, template.read(1, 1));
            // Not written
            assertNull(template.read(0, 0));
            // Outside the template
            assertNull(template.read(2, 0));
            assertNull(template.read(0, -3));
            assertEquals(-1, template.slot(2, 0));

            RawBiomes[] biomes = template.readBiomes();
            assertEquals(16, biomes.length);
            RawBiomes first = biomes[template.slot(-2, -2)];
            assertEquals(-16, first.minY());
            assertEquals(sampleBiomes().palette(), first.palette());
            assertArrayEquals(sampleBiomes().cells(), first.cells());
            assertEquals(List.of("minecraft:ocean"), biomes[template.slot(1, 1)].palette());
            assertNull(biomes[template.slot(0, 0)]);
        }
    }

    @Test
    void testAbortLeavesNoFile() throws IOException {
        File file = dir.resolve("normal.bxt").toFile();
        TemplateFile.Writer writer = new TemplateFile.Writer(file, 1, 0L);
        writer.put(0, 0, new byte[] { 1 }, sampleBiomes());
        writer.abort();
        assertFalse(file.exists());
        assertEquals(0, dir.toFile().list().length);
    }

    @Test
    void testRejectsOtherFiles() throws IOException {
        File file = dir.resolve("junk.bxt").toFile();
        Files.write(file.toPath(), new byte[64]);
        assertThrows(IOException.class, () -> new TemplateFile(file));
    }
}
