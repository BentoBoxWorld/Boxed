package world.bentobox.boxed.generators.template;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.Gson;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import world.bentobox.bentobox.blueprints.dataobjects.BlueprintBlock;
import world.bentobox.bentobox.blueprints.dataobjects.BlueprintEntity;
import world.bentobox.boxed.generators.chunks.AbstractBoxedChunkGenerator.ChestData;
import world.bentobox.boxed.generators.chunks.AbstractBoxedChunkGenerator.EntityData;

/**
 * The land of one area in one dimension, read from a template file. Blocks are read from disk
 * when they are needed and only the most recently used chunks are kept in memory. Biomes are
 * small and asked for constantly, so all of them are kept in memory.
 *
 * @author tastybento
 */
public class Template implements Closeable {

    /**
     * Number of decoded chunks kept in memory per template
     */
    public static final int CACHE_SIZE = 256;

    private final TemplateFile file;
    private final Gson gson;
    private final Consumer<String> errorLog;
    private final Biome[][] biomePalettes;
    private final byte[][] biomeCells;
    private final int[] biomeMinY;
    private final Map<String, BlockData> blockDataCache = new ConcurrentHashMap<>();
    private final Map<Long, TemplateChunk> cache = new LinkedHashMap<>(CACHE_SIZE * 2, 0.75F, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, TemplateChunk> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    /**
     * @param file template file
     * @param gson gson that can read blueprint blocks and entities
     * @param errorLog where to report problems
     * @throws IOException if the file cannot be read
     */
    public Template(File file, Gson gson, Consumer<String> errorLog) throws IOException {
        this.file = new TemplateFile(file);
        this.gson = gson;
        this.errorLog = errorLog;
        try {
            RawBiomes[] raw = this.file.readBiomes();
            biomePalettes = new Biome[raw.length][];
            biomeCells = new byte[raw.length][];
            biomeMinY = new int[raw.length];
            Map<String, Biome> biomes = new HashMap<>();
            for (int i = 0; i < raw.length; i++) {
                if (raw[i] != null) {
                    biomePalettes[i] = raw[i].palette().stream().map(k -> biomes.computeIfAbsent(k, Template::biome))
                            .toArray(Biome[]::new);
                    biomeCells[i] = raw[i].cells();
                    biomeMinY[i] = raw[i].minY();
                }
            }
        } catch (IOException | RuntimeException e) {
            this.file.close();
            throw e;
        }
    }

    /**
     * Gets a biome of the template. Safe to call from any thread.
     * @param chunkX chunk x, from -size to size - 1
     * @param chunkZ chunk z, from -size to size - 1
     * @param x x in the chunk, 0 to 15
     * @param y world y
     * @param z z in the chunk, 0 to 15
     * @return the biome, or null if the template has no biome there or it is unknown on this server
     */
    @Nullable
    public Biome getBiome(int chunkX, int chunkZ, int x, int y, int z) {
        int slot = file.slot(chunkX, chunkZ);
        if (slot < 0 || biomeCells[slot] == null) {
            return null;
        }
        int relY = y - biomeMinY[slot];
        if (relY < 0) {
            return null;
        }
        int index = RawBiomes.index(x, relY, z);
        byte[] cells = biomeCells[slot];
        return index < cells.length ? biomePalettes[slot][cells[index] & 0xFF] : null;
    }

    /**
     * @param chunkX chunk x
     * @param chunkZ chunk z
     * @return true if the template has this chunk
     */
    public boolean hasChunk(int chunkX, int chunkZ) {
        int slot = file.slot(chunkX, chunkZ);
        return slot >= 0 && biomeCells[slot] != null;
    }

    /**
     * @return half-width of the template in chunks
     */
    public int getSize() {
        return file.getSize();
    }

    /**
     * @return world seed the template was built with
     */
    public long getSeed() {
        return file.getSeed();
    }

    /**
     * Gets a chunk of the template. Safe to call from any thread.
     * @param x chunk x, from -size to size - 1
     * @param z chunk z, from -size to size - 1
     * @return the chunk, or null if the template does not have it
     */
    @Nullable
    public TemplateChunk getChunk(int x, int z) {
        Long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
        synchronized (cache) {
            TemplateChunk chunk = cache.get(key);
            if (chunk != null) {
                return chunk;
            }
        }
        // Decode outside the lock. Two threads may decode the same chunk, which is harmless.
        TemplateChunk chunk = load(x, z);
        if (chunk != null) {
            synchronized (cache) {
                cache.put(key, chunk);
            }
        }
        return chunk;
    }

    private TemplateChunk load(int x, int z) {
        try {
            byte[] data = file.read(x, z);
            return data.length == 0 ? null : resolve(RawChunk.decode(data));
        } catch (IOException | RuntimeException e) {
            errorLog.accept("Could not read template chunk " + x + " " + z + ": " + e);
            return null;
        }
    }

    /**
     * Turns the stored form of a chunk into server objects.
     */
    private TemplateChunk resolve(RawChunk raw) {
        BlockData[] palette = raw.blockPalette().stream().map(this::blockData).toArray(BlockData[]::new);
        List<EntityData> entities = raw.entities().stream()
                .map(p -> new EntityData(new Vector(p.x(), p.y(), p.z()), gson.fromJson(p.json(), BlueprintEntity.class)))
                .toList();
        List<ChestData> chests = raw.tiles().stream()
                .map(p -> new ChestData(new Vector(p.x(), p.y(), p.z()), gson.fromJson(p.json(), BlueprintBlock.class)))
                .toList();
        return new TemplateChunk(raw.minY(), palette, raw.sections(), raw.uniform(), entities, chests);
    }

    private BlockData blockData(String string) {
        return blockDataCache.computeIfAbsent(string, s -> {
            try {
                return Bukkit.createBlockData(s);
            } catch (IllegalArgumentException _) {
                errorLog.accept("Template has a block this server does not know: " + s);
                return Material.AIR.createBlockData();
            }
        });
    }

    private static Biome biome(String key) {
        NamespacedKey nsk = NamespacedKey.fromString(key);
        return nsk == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).get(nsk);
    }

    @Override
    public void close() throws IOException {
        file.close();
    }
}
