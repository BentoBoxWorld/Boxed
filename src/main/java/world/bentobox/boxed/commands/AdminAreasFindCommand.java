package world.bentobox.boxed.commands;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.generator.structure.Structure;
import org.bukkit.util.StructureSearchResult;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.generator.BiomeProvider;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.areas.Area;
import world.bentobox.boxed.generators.biomes.NetherSeedBiomeGenerator;
import world.bentobox.boxed.generators.biomes.SeedBiomeGenerator;
import world.bentobox.boxed.generators.chunks.BoxedSeedChunkGenerator;
import world.bentobox.boxed.reach.FortressSpot;
import world.bentobox.boxed.reach.SpotFinder;
import world.bentobox.boxed.reach.SpotScorer;
import world.bentobox.boxed.reach.SpotFinder.Candidate;
import world.bentobox.boxed.reach.SpotSurvey;

/**
 * Looks through a seed for good places to start a box, so that admins do not have to find
 * one by trial and error. The biomes of the seed are searched first, which is quick, and then
 * the land around the most promising places is generated in a throwaway world and scored.
 *
 * @author tastybento
 */
public class AdminAreasFindCommand extends CompositeCommand {

    private static final String PREFIX = "boxed.commands.boxadmin.areas.find.";
    private static final String RANDOM = "random";
    private static final String WORLD_PREFIX = "finder_";
    private static final int DEFAULT_RADIUS = 5000;
    private static final int MIN_RADIUS = 500;
    private static final int MAX_RADIUS = 20000;
    /**
     * Distance between the places whose biomes are looked at
     */
    private static final int STEP = 64;
    /**
     * How many places have their land generated and scored
     */
    private static final int SPOTS = 12;
    /**
     * How many of the best places are shown to whoever ran the command
     */
    private static final int SHOWN = 3;

    private final AtomicBoolean running = new AtomicBoolean();

    /**
     * A place and how good a start it gives
     */
    private record Scored(Candidate spot, SpotSurvey.Score score) {
    }

    /**
     * One search from start to finish
     */
    private final class Search {
        private final User user;
        private final Area area;
        private final long seed;
        private final int radius;
        private final SeedBiomeGenerator biomes;
        private final List<Scored> scored = new ArrayList<>();
        private final List<FortressSpot.Spot> netherSpots = new ArrayList<>();
        private World world;
        private World nether;
        private List<Candidate> spots = List.of();

        Search(User user, Area area, long seed, int radius) {
            this.user = user;
            this.area = area;
            this.seed = seed;
            this.radius = radius;
            this.biomes = new SeedBiomeGenerator(getAddon(), area.biomes());
        }

        /**
         * Makes the throwaway world and searches its biomes. Call on the main thread.
         */
        void start() {
            Boxed addon = getAddon();
            String name = addon.getSettings().getWorldName().toLowerCase(Locale.ENGLISH) + "/" + WORLD_PREFIX
                    + System.currentTimeMillis();
            world = WorldCreator.name(name).generator(new BoxedSeedChunkGenerator(addon, Environment.NORMAL, biomes))
                    .environment(Environment.NORMAL).seed(seed).createWorld();
            if (world == null) {
                finish(PREFIX + "failed");
                return;
            }
            world.setAutoSave(false);
            BiomeProvider vanilla = world.vanillaBiomeProvider();
            int seaLevel = world.getSeaLevel();
            int land = addon.getSettings().getIslandDistance();
            SpotFinder finder = new SpotFinder(
                    (x, z) -> vanilla.getBiome(world, x, seaLevel, z).getKey().getKey(), land);
            Bukkit.getScheduler().runTaskAsynchronously(getPlugin(), () -> {
                // Places are kept apart so that the custom biomes around each do not overlap
                List<Candidate> found = new ArrayList<>(finder.find(radius, STEP, SPOTS, land * 4));
                if (seed == area.seed()) {
                    // The place the area uses now is scored too, for comparison
                    found.removeIf(c -> c.x() == area.centreX() && c.z() == area.centreZ());
                    found.add(finder.at(area.centreX(), area.centreZ()));
                }
                Bukkit.getScheduler().runTask(getPlugin(), () -> {
                    spots = found;
                    if (found.isEmpty()) {
                        finish(PREFIX + "none");
                    } else {
                        biomes.setCentres(found.stream().map(c -> new int[] { c.x(), c.z() }).toList());
                        survey(0);
                    }
                });
            });
        }

        /**
         * Generates the land around a place and scores it, then moves on to the next.
         */
        private void survey(int index) {
            if (index >= spots.size()) {
                startNether();
                return;
            }
            Candidate spot = spots.get(index);
            SpotScorer.score(getPlugin(), world, spot.x(), spot.z(), true).whenComplete((score, error) -> {
                if (error != null) {
                    getAddon().logError("Could not generate the land at " + spot.x() + "," + spot.z() + ": " + error);
                } else {
                    scored.add(new Scored(spot, score));
                }
                survey(index + 1);
            });
        }

        /**
         * Looks for the fortresses of the seed's Nether. Call on the main thread.
         */
        private void startNether() {
            Boxed addon = getAddon();
            if (!addon.getSettings().isNetherGenerate()) {
                finish(null);
                return;
            }
            nether = WorldCreator.name(world.getName() + "_nether")
                    .generator(new BoxedSeedChunkGenerator(addon, Environment.NETHER,
                            new NetherSeedBiomeGenerator(addon, area.biomes())))
                    .environment(Environment.NETHER).seed(seed).createWorld();
            if (nether == null) {
                finish(null);
                return;
            }
            nether.setAutoSave(false);
            // Fortresses are looked for around the middle of the seed and a way off on each side
            List<int[]> fortresses = new ArrayList<>();
            for (int[] from : FORTRESS_SEARCHES) {
                StructureSearchResult result = nether.locateNearestStructure(
                        new Location(nether, from[0], 64, from[1]), Structure.FORTRESS, FORTRESS_SEARCH_CHUNKS, false);
                if (result != null && fortresses.size() < FORTRESSES) {
                    int[] at = { result.getLocation().getBlockX() >> 4, result.getLocation().getBlockZ() >> 4 };
                    if (fortresses.stream().noneMatch(f -> f[0] == at[0] && f[1] == at[1])) {
                        fortresses.add(at);
                    }
                }
            }
            surveyFortress(fortresses, 0);
        }

        /**
         * Generates a fortress and picks where the middle of the Nether should go to have it.
         */
        private void surveyFortress(List<int[]> fortresses, int index) {
            if (index >= fortresses.size()) {
                finish(null);
                return;
            }
            int[] fortress = fortresses.get(index);
            List<CompletableFuture<Chunk>> chunks = new ArrayList<>();
            for (int x = -FORTRESS_REACH; x <= FORTRESS_REACH; x++) {
                for (int z = -FORTRESS_REACH; z <= FORTRESS_REACH; z++) {
                    chunks.add(nether.getChunkAtAsync(fortress[0] + x, fortress[1] + z));
                }
            }
            CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new)).whenComplete((v, error) ->
            Bukkit.getScheduler().runTask(getPlugin(), () -> {
                if (error != null) {
                    getAddon().logError("Could not generate the fortress at chunk " + fortress[0] + "," + fortress[1]
                            + ": " + error);
                    surveyFortress(fortresses, index + 1);
                    return;
                }
                List<int[]> spawners = new ArrayList<>();
                Map<Long, ChunkSnapshot> snapshots = new HashMap<>();
                for (CompletableFuture<Chunk> chunk : chunks) {
                    Chunk c = chunk.join();
                    addBlazeSpawners(c, spawners);
                    snapshots.put(key(c.getX(), c.getZ()), c.getChunkSnapshot(false, false, false));
                    nether.unloadChunk(c.getX(), c.getZ(), false);
                }
                Bukkit.getScheduler().runTaskAsynchronously(getPlugin(), () -> {
                    Optional<FortressSpot.Spot> spot = chooseNetherSpot(fortress, spawners, snapshots);
                    Bukkit.getScheduler().runTask(getPlugin(), () -> {
                        spot.ifPresent(netherSpots::add);
                        surveyFortress(fortresses, index + 1);
                    });
                });
            }));
        }

        private Optional<FortressSpot.Spot> chooseNetherSpot(int[] fortress, List<int[]> spawners,
                Map<Long, ChunkSnapshot> snapshots) {
            List<int[]> bricks = new ArrayList<>();
            snapshots.values().forEach(s -> {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        if (hasBricks(s, x, z)) {
                            bricks.add(new int[] { (s.getX() << 4) + x, (s.getZ() << 4) + z });
                        }
                    }
                }
            });
            int[] min = { (fortress[0] - FORTRESS_REACH) << 4, (fortress[1] - FORTRESS_REACH) << 4 };
            int[] max = { ((fortress[0] + FORTRESS_REACH) << 4) + 15, ((fortress[1] + FORTRESS_REACH) << 4) + 15 };
            return FortressSpot.choose(min, max, spawners, bricks, (x, z) -> {
                ChunkSnapshot s = snapshots.get(key(x >> 4, z >> 4));
                return s != null && isOpen(s, x & 15, z & 15) ? 1 : 0;
            });
        }

        /**
         * Reports what was found and throws the world away. Call on the main thread.
         * @param problem message to send if the search did not work, or null if it did
         */
        private void finish(String problem) {
            File folder = world == null ? null : world.getWorldFolder();
            for (World w : new World[] { world, nether }) {
                if (w != null && !Bukkit.unloadWorld(w, false)) {
                    getAddon().logWarning("Could not unload " + w.getName());
                    folder = null;
                }
            }
            File toDelete = folder;
            File report = new File(AdminAreasFindCommand.this.<Boxed>getAddon().getAreaFolder(area), "spots.txt");
            scored.sort(Comparator.comparingInt((Scored s) -> s.score().score()).reversed());
            netherSpots.sort(Comparator.comparingInt(FortressSpot.Spot::spawnerSize));
            Bukkit.getScheduler().runTaskAsynchronously(getPlugin(), () -> {
                deleteWorlds(toDelete);
                boolean written = problem == null && write(report);
                Bukkit.getScheduler().runTask(getPlugin(), () -> {
                    running.set(false);
                    if (problem != null) {
                        user.sendMessage(problem);
                        return;
                    }
                    scored.stream().limit(SHOWN).forEach(s -> user.sendMessage(PREFIX + "result", TextVariables.XYZ,
                            s.spot().x() + "," + s.spot().z(), TextVariables.NUMBER,
                            String.valueOf(s.score().score()), TextVariables.DESCRIPTION, s.score().describe()));
                    netherSpots.stream().findFirst().ifPresent(n -> user.sendMessage(PREFIX + "nether",
                            TextVariables.XYZ, n.x() + "," + n.z(), TextVariables.NUMBER,
                            String.valueOf(n.spawnerSize())));
                    user.sendMessage(PREFIX + "report", "[file]", written ? report.getPath() : "-");
                    user.sendMessage(PREFIX + "how-to", TextVariables.NAME, area.id(), "[seed]", String.valueOf(seed));
                });
            });
        }

        /**
         * Deletes the world a search used, and any that were left behind by a search that the
         * server stopped in the middle of.
         * @param folder folder of the world that was just used, or null if it is still loaded
         */
        private void deleteWorlds(File folder) {
            File[] siblings = folder == null || folder.getParentFile() == null ? null
                    : folder.getParentFile().listFiles((dir, name) -> name.startsWith(WORLD_PREFIX));
            if (siblings == null) {
                return;
            }
            for (File old : siblings) {
                try (Stream<Path> files = Files.walk(old.toPath())) {
                    for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                        Files.delete(path);
                    }
                } catch (IOException e) {
                    getAddon().logWarning("Could not delete " + old + ": " + e.getMessage());
                }
            }
        }

        private boolean isCurrent(Candidate c) {
            return seed == area.seed() && c.x() == area.centreX() && c.z() == area.centreZ();
        }

        private boolean write(File report) {
            List<String> lines = new ArrayList<>();
            lines.add("Places to start a box in seed " + seed + ", searched " + radius + " blocks each way from 0,0");
            lines.add("Biomes file: " + area.biomes());
            lines.add("");
            lines.add("To use one, give the area this seed and centre in areas.yml, for example");
            lines.add("    seed: " + seed);
            lines.add("    centre: " + (scored.isEmpty() ? "0,0" : scored.get(0).spot().x() + "," + scored.get(0).spot().z()));
            lines.add("then run /boxadmin areas rebuild " + area.id() + " regenerate and restart the server.");
            lines.add("");
            lines.add("Sizes are the box size that takes in four logs, water at the surface, stone within");
            lines.add(SHALLOW_NOTE);
            lines.add("");
            for (Scored s : scored) {
                Candidate c = s.spot();
                lines.add(String.format("%3d/100  centre: %d,%d%s  (%s, river or sea about %s blocks away, %d%% land)",
                        s.score().score(), c.x(), c.z(), isCurrent(c) ? "  <- used by " + area.id() + " now" : "",
                        c.biome(),
                        c.waterDistance() < 0 ? "more than 128" : String.valueOf(c.waterDistance()), c.landPercent()));
                lines.add("         " + s.score().describe());
            }
            lines.add("");
            lines.add("Places for the middle of the Nether that give every box a fortress");
            lines.add("(set nether-centre for the area in areas.yml, then rebuild it as above):");
            if (netherSpots.isEmpty()) {
                lines.add("    none found");
            }
            for (FortressSpot.Spot n : netherSpots) {
                lines.add("    nether-centre: " + n.x() + "," + n.z() + "  (fortress at size " + n.fortressSize()
                        + ", blaze spawner at size " + n.spawnerSize() + ")");
            }
            try {
                Files.createDirectories(report.getParentFile().toPath());
                Files.write(report.toPath(), lines);
                return true;
            } catch (IOException e) {
                getAddon().logError("Could not write " + report + ": " + e.getMessage());
                return false;
            }
        }
    }

    private static void addBlazeSpawners(Chunk chunk, List<int[]> spawners) {
        for (BlockState tile : chunk.getTileEntities()) {
            if (tile instanceof CreatureSpawner spawner && spawner.getSpawnedType() == EntityType.BLAZE) {
                spawners.add(new int[] { tile.getX(), tile.getZ() });
            }
        }
    }

    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private static boolean isBrick(Material m) {
        return m == Material.NETHER_BRICKS || m == Material.NETHER_BRICK_FENCE || m == Material.NETHER_BRICK_STAIRS;
    }

    /**
     * @return true if a column of the Nether has part of a fortress in it
     */
    private static boolean hasBricks(ChunkSnapshot s, int x, int z) {
        for (int y = NETHER_FLOOR; y < NETHER_ROOF; y++) {
            if (isBrick(s.getBlockType(x, y, z))) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return true if a column of the Nether has solid ground with room to stand on it
     */
    private static boolean isOpen(ChunkSnapshot s, int x, int z) {
        for (int y = NETHER_FLOOR; y < NETHER_ROOF; y++) {
            Material floor = s.getBlockType(x, y, z);
            if (floor.isSolid() && !isBrick(floor) && s.getBlockType(x, y + 1, z).isAir()
                    && s.getBlockType(x, y + 2, z).isAir()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The lava sea is below this
     */
    private static final int NETHER_FLOOR = 32;
    private static final int NETHER_ROOF = 110;
    /**
     * How many fortresses are looked at
     */
    private static final int FORTRESSES = 3;
    /**
     * How far around a fortress the land is generated, in chunks
     */
    private static final int FORTRESS_REACH = 8;
    private static final int FORTRESS_SEARCH_CHUNKS = 100;
    private static final int[][] FORTRESS_SEARCHES = { { 0, 0 }, { 1500, 0 }, { -1500, 0 }, { 0, 1500 }, { 0, -1500 } };
    private static final String SHALLOW_NOTE = "eight blocks of the surface, three iron ore and one coal ore at any depth.";

    /**
     * @param parent the areas command
     */
    public AdminAreasFindCommand(CompositeCommand parent) {
        super(parent, "find");
    }

    @Override
    public void setup() {
        this.setPermission("admin.areas.find");
        this.setOnlyPlayer(false);
        this.setParametersHelp(PREFIX + "parameters");
        this.setDescription(PREFIX + "description");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        Boxed addon = getAddon();
        if (args.isEmpty() || args.size() > 3) {
            this.showHelp(this, user);
            return false;
        }
        Area area = addon.getAreas().getArea(args.get(0).toLowerCase(Locale.ENGLISH));
        if (area == null) {
            user.sendMessage("boxed.commands.boxadmin.areas.unknown-area");
            return false;
        }
        long seed = area.seed();
        if (args.size() > 1) {
            if (RANDOM.equalsIgnoreCase(args.get(1))) {
                seed = ThreadLocalRandom.current().nextLong();
            } else {
                try {
                    seed = Long.parseLong(args.get(1));
                } catch (NumberFormatException _) {
                    user.sendMessage("boxed.commands.boxadmin.areas.create.invalid-seed");
                    return false;
                }
            }
        }
        int radius = DEFAULT_RADIUS;
        if (args.size() > 2) {
            if (!Util.isInteger(args.get(2), true) || Integer.parseInt(args.get(2)) < MIN_RADIUS
                    || Integer.parseInt(args.get(2)) > MAX_RADIUS) {
                user.sendMessage(PREFIX + "invalid-radius", "[min]", String.valueOf(MIN_RADIUS), "[max]",
                        String.valueOf(MAX_RADIUS));
                return false;
            }
            radius = Integer.parseInt(args.get(2));
        }
        if (!running.compareAndSet(false, true)) {
            user.sendMessage(PREFIX + "running");
            return false;
        }
        user.sendMessage(PREFIX + "started", "[seed]", String.valueOf(seed));
        new Search(user, area, seed, radius).start();
        return true;
    }

    @Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
        if (args.size() == 3) {
            return Optional.of(Util.tabLimit(List.of(RANDOM), args.get(2)));
        }
        if (args.size() > 3) {
            return Optional.empty();
        }
        return Optional.of(Util.tabLimit(this.<Boxed>getAddon().getAreas().getAreas().stream().map(Area::id).toList(),
                args.isEmpty() ? "" : args.get(args.size() - 1)));
    }
}
