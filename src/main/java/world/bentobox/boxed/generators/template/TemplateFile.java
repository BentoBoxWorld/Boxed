package world.bentobox.boxed.generators.template;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * A template file. It holds the compressed chunks of one area in one dimension, with an index
 * so that any chunk can be read without loading the rest.
 * <p>
 * Layout: magic, half-width in chunks, seed, offset and length of the biome table, then an
 * index entry (offset and length) for each chunk, then the chunk data, then the biome table.
 * Chunk coordinates run from -size to size - 1 on both axes.
 *
 * @author tastybento
 */
public class TemplateFile implements Closeable {

    private static final int MAGIC = 0x42585432; // BXT2
    private static final int HEADER_BYTES = Integer.BYTES * 3 + Long.BYTES * 2;
    private static final int ENTRY_BYTES = Long.BYTES + Integer.BYTES;

    private final FileChannel channel;
    private final int size;
    private final long seed;
    private final long biomeOffset;
    private final int biomeLength;
    private final long[] offsets;
    private final int[] lengths;

    /**
     * Opens a template file for reading.
     * @param file template file
     * @throws IOException if the file cannot be read or is not a template file
     */
    public TemplateFile(File file) throws IOException {
        channel = FileChannel.open(file.toPath(), StandardOpenOption.READ);
        try {
            ByteBuffer header = readFully(HEADER_BYTES, 0);
            if (header.getInt() != MAGIC) {
                throw new IOException(file + " is not a Boxed template file");
            }
            size = header.getInt();
            seed = header.getLong();
            biomeOffset = header.getLong();
            biomeLength = header.getInt();
            int count = count(size);
            if (size <= 0 || channel.size() < HEADER_BYTES + (long) count * ENTRY_BYTES) {
                throw new IOException(file + " is damaged");
            }
            offsets = new long[count];
            lengths = new int[count];
            ByteBuffer index = readFully(count * ENTRY_BYTES, HEADER_BYTES);
            for (int i = 0; i < count; i++) {
                offsets[i] = index.getLong();
                lengths[i] = index.getInt();
            }
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
    }

    private static int count(int size) {
        return size * 2 * size * 2;
    }

    private static int slot(int size, int x, int z) {
        return (x + size) * size * 2 + (z + size);
    }

    private ByteBuffer readFully(int length, long position) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        while (buffer.hasRemaining()) {
            if (channel.read(buffer, position + buffer.position()) < 0) {
                throw new IOException("Unexpected end of template file");
            }
        }
        return buffer.flip();
    }

    /**
     * @return half-width of the template in chunks
     */
    public int getSize() {
        return size;
    }

    /**
     * @return world seed the template was built with
     */
    public long getSeed() {
        return seed;
    }

    /**
     * Reads the compressed data of a chunk. Safe to call from any thread.
     * @param x chunk x, from -size to size - 1
     * @param z chunk z, from -size to size - 1
     * @return compressed chunk, or null if the template does not have this chunk
     * @throws IOException if the file cannot be read
     */
    public byte[] read(int x, int z) throws IOException {
        if (x < -size || x >= size || z < -size || z >= size) {
            return null;
        }
        int slot = slot(size, x, z);
        if (lengths[slot] <= 0) {
            return null;
        }
        return readFully(lengths[slot], offsets[slot]).array();
    }

    /**
     * Reads the biomes of every chunk.
     * @return biomes in slot order, see {@link #slot(int, int)}; null where a chunk is missing
     * @throws IOException if the file cannot be read
     */
    public RawBiomes[] readBiomes() throws IOException {
        RawBiomes[] biomes = new RawBiomes[offsets.length];
        byte[] data = readFully(biomeLength, biomeOffset).array();
        try (DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data)))) {
            for (int i = 0; i < biomes.length; i++) {
                if (in.readBoolean()) {
                    biomes[i] = RawBiomes.read(in);
                }
            }
        }
        return biomes;
    }

    /**
     * @param x chunk x, from -size to size - 1
     * @param z chunk z, from -size to size - 1
     * @return position of the chunk in {@link #readBiomes()}, or -1 if it is outside the template
     */
    public int slot(int x, int z) {
        return x < -size || x >= size || z < -size || z >= size ? -1 : slot(size, x, z);
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    /**
     * Writes a template file. The data goes to a temporary file that only replaces the real
     * file when {@link #close()} is called, so a build that is interrupted never leaves a
     * half-written template behind.
     */
    public static class Writer implements Closeable {

        private final File file;
        private final File temp;
        private final FileChannel channel;
        private final int size;
        private final long[] offsets;
        private final int[] lengths;
        private final RawBiomes[] biomes;
        private final long seed;
        private long position;

        /**
         * @param file template file to make
         * @param size half-width of the template in chunks
         * @param seed world seed the template is built with
         * @throws IOException if the file cannot be written
         */
        public Writer(File file, int size, long seed) throws IOException {
            this.file = file;
            this.size = size;
            this.temp = new File(file.getParentFile(), file.getName() + ".tmp");
            Files.createDirectories(file.getParentFile().toPath());
            channel = FileChannel.open(temp.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            int count = count(size);
            offsets = new long[count];
            lengths = new int[count];
            biomes = new RawBiomes[count];
            this.seed = seed;
            position = HEADER_BYTES + (long) count * ENTRY_BYTES;
        }

        private void write(ByteBuffer buffer, long at) throws IOException {
            while (buffer.hasRemaining()) {
                at += channel.write(buffer, at);
            }
        }

        /**
         * Adds a chunk. Safe to call from any thread.
         * @param x chunk x, from -size to size - 1
         * @param z chunk z, from -size to size - 1
         * @param data compressed chunk
         * @param chunkBiomes biomes of the chunk
         * @throws IOException if the file cannot be written
         */
        public synchronized void put(int x, int z, byte[] data, RawBiomes chunkBiomes) throws IOException {
            int slot = slot(size, x, z);
            biomes[slot] = chunkBiomes;
            write(ByteBuffer.wrap(data), position);
            offsets[slot] = position;
            lengths[slot] = data.length;
            position += data.length;
        }

        /**
         * Writes the biome table, header and index and moves the finished file into place.
         */
        @Override
        public synchronized void close() throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes))) {
                for (RawBiomes b : biomes) {
                    out.writeBoolean(b != null);
                    if (b != null) {
                        b.write(out);
                    }
                }
            }
            byte[] table = bytes.toByteArray();
            write(ByteBuffer.wrap(table), position);
            ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES);
            header.putInt(MAGIC).putInt(size).putLong(seed).putLong(position).putInt(table.length).flip();
            write(header, 0);
            ByteBuffer index = ByteBuffer.allocate(offsets.length * ENTRY_BYTES);
            for (int i = 0; i < offsets.length; i++) {
                index.putLong(offsets[i]).putInt(lengths[i]);
            }
            write(index.flip(), HEADER_BYTES);
            channel.force(true);
            channel.close();
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        /**
         * Gives up on the build and removes the temporary file.
         */
        public synchronized void abort() {
            try {
                channel.close();
                Files.deleteIfExists(temp.toPath());
            } catch (IOException e) {
                // Nothing more can be done
            }
        }
    }
}
