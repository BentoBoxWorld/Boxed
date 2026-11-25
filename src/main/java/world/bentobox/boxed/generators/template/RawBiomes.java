package world.bentobox.boxed.generators.template;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * The biomes of one chunk of a template in their stored form. Biomes are stored per 4x4x4
 * cell as palette indices in y, z, x order.
 * <p>
 * Biomes are kept apart from the blocks because the server asks for them far more often and
 * over much wider areas, for example when locating a biome. They are small, so a template
 * keeps all of them in memory.
 *
 * @param minY lowest block y in the chunk
 * @param palette biome keys
 * @param cells biome palette index per 4x4x4 cell
 * @author tastybento
 */
public record RawBiomes(int minY, List<String> palette, byte[] cells) {

    /**
     * @param x x in the chunk, 0 to 15
     * @param y y above the chunk's lowest y
     * @param z z in the chunk, 0 to 15
     * @return index into the cells array
     */
    public static int index(int x, int y, int z) {
        return ((y >> 2) << 4) | ((z >> 2) << 2) | (x >> 2);
    }

    void write(DataOutputStream out) throws IOException {
        out.writeInt(minY);
        RawChunk.writeStrings(out, palette);
        out.writeInt(cells.length);
        out.write(cells);
    }

    static RawBiomes read(DataInputStream in) throws IOException {
        int minY = in.readInt();
        List<String> palette = RawChunk.readStrings(in);
        byte[] cells = new byte[in.readInt()];
        in.readFully(cells);
        return new RawBiomes(minY, palette, cells);
    }
}
