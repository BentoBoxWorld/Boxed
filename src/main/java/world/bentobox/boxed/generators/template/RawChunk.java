package world.bentobox.boxed.generators.template;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * One chunk of a template in its stored form. It only holds strings and numbers, so it can be
 * encoded and decoded without a server.
 * <p>
 * Blocks are stored per 16-block-high section. A section is either uniform, in which case
 * {@code sections[i]} is null and {@code uniform[i]} is the palette index of its only block, or
 * it is an array of 4096 palette indices in y, z, x order. Biomes are stored separately, see
 * {@link RawBiomes}.
 *
 * @param minY lowest block y in the chunk
 * @param blockPalette block data strings
 * @param sections palette indices per section, or null for a uniform section
 * @param uniform palette index of a uniform section
 * @param entities entities in the chunk
 * @param tiles tile entities in the chunk
 * @author tastybento
 */
public record RawChunk(int minY, List<String> blockPalette, short[][] sections, int[] uniform,
        List<Placed> entities, List<Placed> tiles) {

    /**
     * Something stored at a position in the chunk, described by a JSON string.
     * @param x x in the chunk, 0 to 16
     * @param y world y
     * @param z z in the chunk, 0 to 16
     * @param json serialized entity or tile entity
     */
    public record Placed(double x, double y, double z, String json) {
    }

    /**
     * Number of blocks in a section
     */
    public static final int SECTION_BLOCKS = 4096;

    /**
     * @param x x in the chunk, 0 to 15
     * @param y y in the section, 0 to 15
     * @param z z in the chunk, 0 to 15
     * @return index into a section array
     */
    public static int blockIndex(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }

    /**
     * Encodes this chunk into compressed bytes.
     * @return compressed chunk
     */
    public byte[] encode() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(16384);
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes))) {
            out.writeInt(minY);
            writeStrings(out, blockPalette);
            out.writeInt(sections.length);
            for (int i = 0; i < sections.length; i++) {
                short[] section = sections[i];
                out.writeBoolean(section != null);
                if (section == null) {
                    out.writeInt(uniform[i]);
                } else {
                    for (short s : section) {
                        out.writeShort(s);
                    }
                }
            }
            writePlaced(out, entities);
            writePlaced(out, tiles);
        } catch (IOException e) {
            // Cannot happen when writing to memory
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    /**
     * Decodes a chunk made by {@link #encode()}.
     * @param data compressed chunk
     * @return chunk
     * @throws IOException if the data is damaged
     */
    public static RawChunk decode(byte[] data) throws IOException {
        try (DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data)))) {
            int minY = in.readInt();
            List<String> blockPalette = readStrings(in);
            int count = in.readInt();
            short[][] sections = new short[count][];
            int[] uniform = new int[count];
            for (int i = 0; i < count; i++) {
                if (in.readBoolean()) {
                    short[] section = new short[SECTION_BLOCKS];
                    for (int j = 0; j < SECTION_BLOCKS; j++) {
                        section[j] = in.readShort();
                    }
                    sections[i] = section;
                } else {
                    uniform[i] = in.readInt();
                }
            }
            return new RawChunk(minY, blockPalette, sections, uniform, readPlaced(in), readPlaced(in));
        }
    }

    static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        out.writeInt(b.length);
        out.write(b);
    }

    static String readString(DataInputStream in) throws IOException {
        byte[] b = new byte[in.readInt()];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    static void writeStrings(DataOutputStream out, List<String> list) throws IOException {
        out.writeInt(list.size());
        for (String s : list) {
            writeString(out, s);
        }
    }

    static List<String> readStrings(DataInputStream in) throws IOException {
        int count = in.readInt();
        List<String> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(readString(in));
        }
        return list;
    }

    private static void writePlaced(DataOutputStream out, List<Placed> list) throws IOException {
        out.writeInt(list.size());
        for (Placed p : list) {
            out.writeDouble(p.x());
            out.writeDouble(p.y());
            out.writeDouble(p.z());
            writeString(out, p.json());
        }
    }

    private static List<Placed> readPlaced(DataInputStream in) throws IOException {
        int count = in.readInt();
        List<Placed> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new Placed(in.readDouble(), in.readDouble(), in.readDouble(), readString(in)));
        }
        return list;
    }
}
