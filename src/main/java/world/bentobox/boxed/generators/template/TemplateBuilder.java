package world.bentobox.boxed.generators.template;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.bukkit.Bukkit;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Sign;
import org.bukkit.block.TrialSpawner;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Colorable;
import org.bukkit.plugin.Plugin;

import com.google.gson.Gson;

import world.bentobox.bentobox.blueprints.dataobjects.BlueprintBlock;
import world.bentobox.bentobox.blueprints.dataobjects.BlueprintCreatureSpawner;
import world.bentobox.bentobox.blueprints.dataobjects.BlueprintEntity;
import world.bentobox.bentobox.blueprints.dataobjects.BlueprintTrialSpawner;
import world.bentobox.boxed.generators.template.RawChunk.Placed;

/**
 * Builds a template file from a seed world.
 * <p>
 * The build blocks the calling (main) thread until it is done, but the seed chunks are
 * requested a column ahead so the server generates them on all of its worker threads, and the
 * captured chunks are compressed on a separate pool. Chunks are unloaded again once they are
 * captured so that the whole region is never held in memory at once.
 *
 * @author tastybento
 */
public class TemplateBuilder {

    private final World world;
    private final int centerX;
    private final int centerZ;
    private final int size;
    private final Gson gson;
    private final Consumer<String> log;

    /**
     * @param world seed world
     * @param centerX chunk x of the centre of the area in the seed world
     * @param centerZ chunk z of the centre of the area in the seed world
     * @param size half-width of the area in chunks
     * @param gson gson that can write blueprint blocks and entities
     * @param log where to report progress
     */
    public TemplateBuilder(World world, int centerX, int centerZ, int size, Gson gson, Consumer<String> log) {
        this.world = world;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.size = size;
        this.gson = gson;
        this.log = log;
    }

    /**
     * Builds the template file. Must be called on the main thread.
     * @param file file to make
     * @param seed seed to record in the file
     * @throws IOException if the file could not be written
     */
    public void build(File file, long seed) throws IOException {
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        // Closing the writer puts the finished file in place, unless the build was given up
        try (TemplateFile.Writer writer = new TemplateFile.Writer(file, size, seed);
                ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
                    Thread t = new Thread(r, "Boxed template builder");
                    t.setDaemon(true);
                    return t;
                })) {
            // Limits how many captured chunks wait to be compressed
            Semaphore permits = new Semaphore(threads * 4);
            AtomicReference<Exception> failure = new AtomicReference<>();
            try {
                captureAll(file, writer, pool, permits, failure);
                pool.shutdown();
                if (!pool.awaitTermination(10, TimeUnit.MINUTES)) {
                    failure.compareAndSet(null, new IOException("Timed out compressing chunks"));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failure.compareAndSet(null, e);
            } catch (RuntimeException e) {
                failure.compareAndSet(null, e);
            } finally {
                pool.shutdownNow();
            }
            if (failure.get() != null) {
                writer.abort();
                throw new IOException("Could not build " + file.getName(), failure.get());
            }
        }
    }

    /**
     * Builds the template file while the server keeps running. A column of chunks is
     * captured each time the server has them ready, so no tick is held up for long. The
     * chunks are left for the server to unload, which saves them.
     * @param file file to make
     * @param seed seed to record in the file
     * @param plugin plugin to schedule with
     * @param progress told the percentage done as it goes up, on the main thread
     * @return completes on the main thread when the file is in place, or with the reason it could not be made
     */
    public CompletableFuture<Void> buildAsync(File file, long seed, Plugin plugin, IntConsumer progress) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        TemplateFile.Writer writer;
        try {
            writer = new TemplateFile.Writer(file, size, seed);
        } catch (IOException e) {
            result.completeExceptionally(e);
            return result;
        }
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "Boxed template builder");
            t.setDaemon(true);
            return t;
        });
        AsyncBuild build = new AsyncBuild(file, writer, pool, new Semaphore(size * 4), new AtomicReference<>(), plugin,
                progress, result);
        captureColumn(build, -size);
        return result;
    }

    /**
     * What a build that runs alongside the server carries from one column to the next
     */
    private record AsyncBuild(File file, TemplateFile.Writer writer, ExecutorService pool, Semaphore permits,
            AtomicReference<Exception> failure, Plugin plugin, IntConsumer progress, CompletableFuture<Void> result) {
    }

    /**
     * Captures one column of chunks when the server has them, then moves on to the next.
     */
    private void captureColumn(AsyncBuild build, int x) {
        if (x >= size || build.failure().get() != null) {
            finishAsync(build);
            return;
        }
        List<CompletableFuture<Chunk>> chunks = new ArrayList<>();
        for (int z = -size; z < size; z++) {
            chunks.add(world.getChunkAtAsync(centerX + x, centerZ + z));
        }
        CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new)).whenComplete((v, error) ->
        // Chunks have to be read on the main thread
        Bukkit.getScheduler().runTask(build.plugin(), () -> {
            if (error != null) {
                build.failure().compareAndSet(null, new IOException(error));
            } else {
                try {
                    for (int z = -size; z < size; z++) {
                        capture(x, z, build.writer(), build.pool(), build.permits(), build.failure());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    build.failure().compareAndSet(null, e);
                } catch (RuntimeException e) {
                    build.failure().compareAndSet(null, e);
                }
                build.progress().accept((x + size + 1) * 100 / (size * 2));
            }
            captureColumn(build, x + 1);
        }));
    }

    /**
     * Waits for the last chunks to be compressed, off the main thread, and closes the file.
     */
    private void finishAsync(AsyncBuild build) {
        Bukkit.getScheduler().runTaskAsynchronously(build.plugin(), () -> {
            build.pool().shutdown();
            try {
                if (!build.pool().awaitTermination(10, TimeUnit.MINUTES)) {
                    build.failure().compareAndSet(null, new IOException("Timed out compressing chunks"));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                build.failure().compareAndSet(null, e);
            }
            build.pool().shutdownNow();
            try {
                if (build.failure().get() != null) {
                    build.writer().abort();
                } else {
                    build.writer().close();
                }
            } catch (IOException e) {
                build.failure().compareAndSet(null, e);
            }
            Exception failed = build.failure().get();
            Bukkit.getScheduler().runTask(build.plugin(), () -> {
                if (failed == null) {
                    build.result().complete(null);
                } else {
                    build.result().completeExceptionally(
                            new IOException("Could not build " + build.file().getName(), failed));
                }
            });
        });
    }

    private void captureAll(File file, TemplateFile.Writer writer, ExecutorService pool, Semaphore permits,
            AtomicReference<Exception> failure) throws InterruptedException {
        int total = size * 2 * size * 2;
        int done = 0;
        int lastPercent = 0;
        request(-size);
        for (int x = -size; x < size && failure.get() == null; x++) {
            // Get the server working on the next column while this one is captured
            request(x + 1);
            for (int z = -size; z < size; z++) {
                capture(x, z, writer, pool, permits, failure);
                done++;
            }
            unload(x - 2);
            int percent = done * 100 / total;
            if (percent / 10 != lastPercent / 10) {
                log.accept("Building " + file.getName() + " from " + world.getName() + ": " + percent + "% done");
            }
            lastPercent = percent;
        }
    }

    /**
     * Asks the server to start loading or generating a column of seed chunks.
     */
    private void request(int x) {
        if (x >= size) {
            return;
        }
        for (int z = -size; z < size; z++) {
            world.getChunkAtAsync(centerX + x, centerZ + z);
        }
    }

    /**
     * Unloads a column of seed chunks that has been captured.
     */
    private void unload(int x) {
        if (x < -size) {
            return;
        }
        for (int z = -size; z < size; z++) {
            world.unloadChunk(centerX + x, centerZ + z, true);
        }
    }

    private void capture(int x, int z, TemplateFile.Writer writer, ExecutorService pool, Semaphore permits,
            AtomicReference<Exception> failure) throws InterruptedException {
        Chunk chunk = world.getChunkAt(centerX + x, centerZ + z);
        ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, true, false);
        List<Placed> entities = getEntities(chunk);
        List<Placed> tiles = getTileEntities(chunk);
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        permits.acquire();
        pool.execute(() -> {
            try {
                writer.put(x, z, toRawChunk(snapshot, minY, maxY, entities, tiles).encode(),
                        toRawBiomes(snapshot, minY, maxY));
            } catch (IOException | RuntimeException e) {
                failure.compareAndSet(null, e);
            } finally {
                permits.release();
            }
        });
    }

    /**
     * Converts a chunk snapshot to the stored form. Can be called from any thread.
     * @param snapshot snapshot that includes biomes
     * @param minY lowest block y of the world
     * @param maxY highest block y of the world, exclusive
     * @param entities entities in the chunk
     * @param tiles tile entities in the chunk
     * @return stored form of the chunk
     */
    public static RawChunk toRawChunk(ChunkSnapshot snapshot, int minY, int maxY, List<Placed> entities,
            List<Placed> tiles) {
        int sectionCount = (maxY - minY) >> 4;
        Map<BlockData, Integer> blockIds = new HashMap<>();
        List<String> blockPalette = new ArrayList<>();
        short[][] sections = new short[sectionCount][];
        int[] uniform = new int[sectionCount];
        for (int section = 0; section < sectionCount; section++) {
            short[] blocks = new short[RawChunk.SECTION_BLOCKS];
            if (fillSection(snapshot, minY + (section << 4), blocks, blockIds, blockPalette)) {
                uniform[section] = blocks[0];
            } else {
                sections[section] = blocks;
            }
        }

        return new RawChunk(minY, blockPalette, sections, uniform, entities, tiles);
    }

    /**
     * Fills in the palette indices of one section, adding to the palette as needed.
     * @return true if every block in the section is the same
     */
    private static boolean fillSection(ChunkSnapshot snapshot, int baseY, short[] blocks,
            Map<BlockData, Integer> blockIds, List<String> blockPalette) {
        boolean same = true;
        int i = 0;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    blocks[i] = blockIds.computeIfAbsent(snapshot.getBlockData(x, baseY + y, z), data -> {
                        blockPalette.add(data.getAsString());
                        return blockPalette.size() - 1;
                    }).shortValue();
                    same &= blocks[i] == blocks[0];
                    i++;
                }
            }
        }
        return same;
    }

    /**
     * Gets the biomes of a chunk snapshot in their stored form. Can be called from any thread.
     * @param snapshot snapshot that includes biomes
     * @param minY lowest block y of the world
     * @param maxY highest block y of the world, exclusive
     * @return stored form of the biomes
     */
    public static RawBiomes toRawBiomes(ChunkSnapshot snapshot, int minY, int maxY) {
        Map<Biome, Integer> biomeIds = new HashMap<>();
        List<String> palette = new ArrayList<>();
        byte[] cells = new byte[((maxY - minY) >> 2) * 16];
        for (int y = 0; y < maxY - minY; y += 4) {
            for (int z = 0; z < 16; z += 4) {
                for (int x = 0; x < 16; x += 4) {
                    cells[RawBiomes.index(x, y, z)] = biomeIds.computeIfAbsent(snapshot.getBiome(x, minY + y, z), b -> {
                        palette.add(b.getKey().toString());
                        return palette.size() - 1;
                    }).byteValue();
                }
            }
        }
        return new RawBiomes(minY, palette, cells);
    }

    private List<Placed> getEntities(Chunk chunk) {
        return Arrays.stream(chunk.getEntities())
                .filter(e -> !(e instanceof Player))
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .map(e -> placed(e.getLocation(), gson.toJson(toBlueprintEntity(e), BlueprintEntity.class)))
                .toList();
    }

    private List<Placed> getTileEntities(Chunk chunk) {
        return Arrays.stream(chunk.getTileEntities())
                .map(t -> placed(t.getLocation(), gson.toJson(toBlueprintBlock(t.getBlock()), BlueprintBlock.class)))
                .toList();
    }

    private static Placed placed(Location l, String json) {
        return new Placed(Math.floorMod(l.getBlockX(), 16), l.getBlockY(), Math.floorMod(l.getBlockZ(), 16), json);
    }

    /**
     * Captures the entity's state as a blueprint entity
     * @param entity living entity
     * @return blueprint entity
     */
    private static BlueprintEntity toBlueprintEntity(LivingEntity entity) {
        BlueprintEntity bpe = new BlueprintEntity();
        bpe.setType(entity.getType());
        bpe.setCustomName(entity.getCustomName());
        if (entity instanceof Villager villager) {
            bpe.setExperience(villager.getVillagerExperience());
            bpe.setLevel(villager.getVillagerLevel());
            bpe.setProfession(villager.getProfession());
            bpe.setVillagerType(villager.getVillagerType());
        }
        if (entity instanceof Colorable c && c.getColor() != null) {
            bpe.setColor(c.getColor());
        }
        if (entity instanceof Tameable tameable) {
            bpe.setTamed(tameable.isTamed());
        }
        if (entity instanceof ChestedHorse chestedHorse) {
            bpe.setChest(chestedHorse.isCarryingChest());
        }
        // Only set if child. Most animals are adults
        if (entity instanceof Ageable ageable && !ageable.isAdult()) {
            bpe.setAdult(false);
        }
        if (entity instanceof AbstractHorse horse) {
            bpe.setDomestication(horse.getDomestication());
            bpe.setInventory(new HashMap<>());
            for (int i = 0; i < horse.getInventory().getSize(); i++) {
                ItemStack item = horse.getInventory().getItem(i);
                if (item != null) {
                    bpe.getInventory().put(i, item);
                }
            }
        }
        if (entity instanceof Horse horse) {
            bpe.setStyle(horse.getStyle());
        }
        return bpe;
    }

    /**
     * Converts the block into a blueprint block that can be pasted later
     * @param block - block to convert
     * @return Blueprint block
     */
    private static BlueprintBlock toBlueprintBlock(Block block) {
        BlockState blockState = block.getState();
        BlueprintBlock b = new BlueprintBlock(block.getBlockData().getAsString());

        // Signs
        if (blockState instanceof Sign sign) {
            b.setSignLines(Arrays.asList(sign.getLines()));
            b.setGlowingText(sign.isGlowingText());
        }

        // Chests
        if (blockState instanceof InventoryHolder ih) {
            b.setInventory(new HashMap<>());
            for (int i = 0; i < ih.getInventory().getSize(); i++) {
                ItemStack item = ih.getInventory().getItem(i);
                if (item != null) {
                    b.getInventory().put(i, item);
                }
            }
        }
        // Spawner type
        if (blockState instanceof CreatureSpawner spawner) {
            BlueprintCreatureSpawner cs = new BlueprintCreatureSpawner();
            cs.setSpawnedType(spawner.getSpawnedType());
            cs.setDelay(spawner.getDelay());
            cs.setMaxNearbyEntities(spawner.getMaxNearbyEntities());
            cs.setMaxSpawnDelay(spawner.getMaxSpawnDelay());
            cs.setMinSpawnDelay(spawner.getMinSpawnDelay());
            cs.setRequiredPlayerRange(spawner.getRequiredPlayerRange());
            cs.setSpawnRange(spawner.getSpawnRange());
            b.setCreatureSpawner(cs);
        }

        // Trial spawner
        if (blockState instanceof TrialSpawner trialSpawner) {
            b.setTrialSpawner(new BlueprintTrialSpawner(trialSpawner.isOminous(),
                    trialSpawner.isOminous() ? trialSpawner.getOminousConfiguration() : trialSpawner.getNormalConfiguration()));
        }

        // Banners
        if (blockState instanceof Banner banner) {
            b.setBannerPatterns(banner.getPatterns());
        }

        return b;
    }
}
