package world.bentobox.boxed.workshop;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.Material;
import org.bukkit.WorldBorder;
import org.bukkit.generator.BiomeProvider;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.boxed.reach.SpotFinder;
import world.bentobox.boxed.reach.SpotFinder.Candidate;
import world.bentobox.boxed.reach.SpotScorer;
import org.bukkit.HeightMap;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.areas.Area;
import world.bentobox.boxed.generators.biomes.AbstractSeedBiomeProvider;
import world.bentobox.boxed.generators.biomes.NetherSeedBiomeGenerator;
import world.bentobox.boxed.generators.biomes.SeedBiomeGenerator;
import world.bentobox.boxed.generators.chunks.BoxedSeedChunkGenerator;

/**
 * Runs the workshop: the worlds where an admin tries out a seed and a biome style, picks
 * the place that will be the middle of every box and shapes the land before it becomes an
 * area. There is one workshop at a time. It stays, with its worlds, until it is thrown away
 * or made into an area, including over restarts.
 *
 * @author tastybento
 */
public class WorkshopManager {

    /**
     * Name of the workshop's overworld inside the game world's folder
     */
    public static final String WORLD = "workshop";
    private static final String NETHER = "_nether";
    private static final String SPOTS_PREFIX = "boxed.commands.boxadmin.areas.spots.";
    /**
     * How far from 0,0 places to start are looked for, in blocks
     */
    private static final int SCAN_RADIUS = 5000;
    private static final int SCAN_STEP = 64;
    /**
     * How many places are looked at
     */
    private static final int SCAN_SPOTS = 12;
    private static final String FOUND = "found";
    private static final String TOTAL = "[total]";
    /**
     * How many blocks away from a place a player may be put to keep them out of a tree
     */
    private static final int STAND_SEARCH = 5;
    private static final int NETHER_FLOOR = 32;
    private static final int NETHER_ROOF = 110;

    private final Boxed addon;
    private final File areasFolder;
    private final File file;
    private final File displacedFile;
    private final Map<Environment, World> worlds = new EnumMap<>(Environment.class);
    private final Map<Environment, AbstractSeedBiomeProvider> biomes = new EnumMap<>(Environment.class);
    private final Map<UUID, GameMode> displaced = new HashMap<>();
    private Workshop workshop;
    private boolean closing;

    /**
     * @param addon Boxed
     */
    public WorkshopManager(Boxed addon) {
        this.addon = addon;
        this.areasFolder = new File(addon.getDataFolder(), "areas");
        this.file = new File(areasFolder, "workshop.yml");
        this.displacedFile = new File(areasFolder, "displaced.yml");
    }

    /**
     * Brings back the workshop that was in progress when the server stopped, with its worlds.
     * Call once the game worlds exist.
     */
    public void load() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(displacedFile);
        for (String key : config.getKeys(false)) {
            try {
                displaced.put(UUID.fromString(key), GameMode.valueOf(config.getString(key, "")));
            } catch (IllegalArgumentException _) {
                // Not a player entry
            }
        }
        workshop = Workshop.load(file);
        if (workshop == null) {
            return;
        }
        addon.log("An area is being worked on (seed " + workshop.getSeed() + "). Loading its worlds...");
        for (Environment env : List.of(Environment.NORMAL, Environment.NETHER)) {
            File folder = worldFolder(env);
            if (folder != null && folder.isDirectory()) {
                getWorld(env);
            }
        }
    }

    /**
     * @return the area being worked on, if there is one
     */
    public Optional<Workshop> get() {
        return closing ? Optional.empty() : Optional.ofNullable(workshop);
    }

    /**
     * @param world a world
     * @return true if it is one of the workshop's worlds
     */
    public boolean isWorkshopWorld(@Nullable World world) {
        return world != null && worlds.containsValue(world);
    }

    /**
     * @param env dimension
     * @return true if the workshop can have this dimension
     */
    public boolean hasDimension(Environment env) {
        return env == Environment.NORMAL || (env == Environment.NETHER && addon.getSettings().isNetherGenerate());
    }

    private static String suffix(Environment env) {
        return env == Environment.NETHER ? NETHER : "";
    }

    private String worldName(Environment env) {
        return addon.getSettings().getWorldName().toLowerCase(Locale.ENGLISH) + "/" + WORLD + suffix(env);
    }

    /**
     * Worlds made by Boxed are kept in folders inside the game world's own folder.
     * @return where a workshop world is kept, or null if the game world is not loaded
     */
    @Nullable
    private File worldFolder(Environment env) {
        World overWorld = addon.getOverWorld();
        return overWorld == null ? null : new File(overWorld.getWorldFolder(), WORLD + suffix(env));
    }

    /**
     * Starts work on an area.
     * @param seed world seed of the land
     * @param biomesFile the biomes file to use, relative to the addon's folder
     * @param area an existing area to start from, or null for a new one. If the seed and
     *             biomes are the area's own, the workshop starts as a copy of the land the
     *             area was built from, so changes made to it by hand are kept.
     * @return the new workshop
     * @throws IOException if the workshop cannot be saved
     * @throws IllegalStateException if an area is already being worked on
     */
    public Workshop start(long seed, String biomesFile, @Nullable Area area) throws IOException {
        if (workshop != null) {
            throw new IllegalStateException("An area is already being worked on");
        }
        Workshop w = new Workshop(seed, biomesFile, area == null ? null : area.id());
        if (area != null) {
            w.setCentre(Environment.NORMAL, area.centreX(), area.centreZ());
            w.setCentre(Environment.NETHER, area.netherCentreX(), area.netherCentreZ());
            if (seed == area.seed() && biomesFile.equals(area.biomes())) {
                copySeedWorld(area, Environment.NORMAL);
                copySeedWorld(area, Environment.NETHER);
            }
        }
        w.save(file);
        workshop = w;
        return w;
    }

    private void copySeedWorld(Area area, Environment env) {
        File to = worldFolder(env);
        if (to == null || to.exists()) {
            return;
        }
        String name = area.seedWorldName("", env == Environment.NETHER).substring(1);
        File from = new File(to.getParentFile(), name);
        if (!from.isDirectory() || Bukkit.getWorld(area.seedWorldName(
                addon.getSettings().getWorldName().toLowerCase(Locale.ENGLISH), env == Environment.NETHER)) != null) {
            return;
        }
        try (Stream<Path> files = Files.walk(from.toPath())) {
            for (Path path : files.toList()) {
                Path target = to.toPath().resolve(from.toPath().relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else if (!path.getFileName().toString().equals("session.lock")) {
                    Files.copy(path, target);
                }
            }
        } catch (IOException e) {
            addon.logWarning("Could not copy the land of area " + area.id() + ", so it will be generated again: "
                    + e.getMessage());
        }
    }

    /**
     * Gets a world of the workshop, making or loading it if needed. This can take a few
     * seconds the first time. Call on the main thread.
     * @param env dimension
     * @return the world, or null if there is no workshop or the world could not be made
     */
    @Nullable
    public World getWorld(Environment env) {
        if (workshop == null || closing || !hasDimension(env)) {
            return null;
        }
        World world = worlds.get(env);
        if (world != null) {
            return world;
        }
        int[] centre = workshop.getCentre(env);
        AbstractSeedBiomeProvider provider = env == Environment.NETHER
                ? new NetherSeedBiomeGenerator(addon, workshop.getBiomes(), centre[0], centre[1])
                : new SeedBiomeGenerator(addon, workshop.getBiomes(), centre[0], centre[1]);
        if (env == Environment.NORMAL && !workshop.getSpots().isEmpty()) {
            // The biomes were laid out around every place that was found, and have to stay that way
            provider.setCentres(workshop.getSpots().stream().map(s -> new int[] { s.x(), s.z() }).toList());
        }
        world = WorldCreator.name(worldName(env)).generator(new BoxedSeedChunkGenerator(addon, env, provider))
                .environment(env).seed(workshop.getSeed()).createWorld();
        if (world == null) {
            addon.logError("Could not make the workshop world " + worldName(env));
            return null;
        }
        // Nothing should change the land except the admin
        world.setDifficulty(Difficulty.PEACEFUL);
        for (SpawnCategory category : SpawnCategory.values()) {
            if (category != SpawnCategory.MISC) {
                world.setSpawnLimit(category, 0);
            }
        }
        worlds.put(env, world);
        biomes.put(env, provider);
        if (env == Environment.NORMAL) {
            scan(workshop, world, provider);
        }
        return world;
    }

    /**
     * Takes a player to the middle of the workshop.
     * @param player player
     * @param env dimension
     * @return true once the player is there, false if they could not be taken
     */
    public CompletableFuture<Boolean> enter(Player player, Environment env) {
        World world = getWorld(env);
        if (world == null) {
            return CompletableFuture.completedFuture(false);
        }
        int[] centre = workshop.getCentre(env);
        return world.getChunkAtAsync(centre[0] >> 4, centre[1] >> 4)
                .thenCompose(chunk -> player.teleportAsync(standingPlace(world, centre[0], centre[1])))
                .thenApply(ok -> {
                    if (Boolean.TRUE.equals(ok)) {
                        // The marker is sent after the player has the chunk, or it would be overwritten
                        Bukkit.getScheduler().runTaskLater(addon.getPlugin(), () -> showCentre(player, true), 10L);
                    }
                    return ok;
                });
    }

    /**
     * Looks for places that could be the middle of every box. The seed's biomes are searched
     * first, which needs no land. The chosen biomes are then laid out around every place
     * found, and the land at each is generated and scored, one at a time. Players in the
     * workshop are told as each one is ready.
     */
    private void scan(Workshop w, World world, AbstractSeedBiomeProvider provider) {
        if (!w.getSpots().isEmpty()) {
            // Carry on where the server stopped
            scoreNext(w, world, false);
            return;
        }
        BiomeProvider vanilla = world.vanillaBiomeProvider();
        int seaLevel = world.getSeaLevel();
        int land = addon.getSettings().getIslandDistance();
        int[] start = w.getCentre(Environment.NORMAL);
        SpotFinder finder = new SpotFinder((x, z) -> vanilla.getBiome(world, x, seaLevel, z).getKey().getKey(), land);
        Bukkit.getScheduler().runTaskAsynchronously(addon.getPlugin(), () -> {
            // The place the workshop starts at is always the first choice. The others are kept
            // far enough from it, and from each other, that their biomes do not overlap.
            List<Workshop.Spot> found = new ArrayList<>();
            found.add(unscored(finder.at(start[0], start[1])));
            long apart = land * 4L;
            for (Candidate c : finder.find(SCAN_RADIUS, SCAN_STEP, SCAN_SPOTS, land * 4)) {
                long dx = (long) c.x() - start[0];
                long dz = (long) c.z() - start[1];
                if (dx * dx + dz * dz >= apart * apart) {
                    found.add(unscored(c));
                }
            }
            Bukkit.getScheduler().runTask(addon.getPlugin(), () -> {
                if (workshop != w || closing) {
                    return;
                }
                w.setSpots(found);
                save();
                provider.setCentres(found.stream().map(s -> new int[] { s.x(), s.z() }).toList());
                scoreNext(w, world, false);
            });
        });
    }

    private static Workshop.Spot unscored(Candidate c) {
        return new Workshop.Spot(c.x(), c.z(), c.biome(), -1, "");
    }

    /**
     * Scores the next place that has not been looked at yet.
     * @param told true if players have been told about a place since the scan last began
     */
    private void scoreNext(Workshop w, World world, boolean told) {
        List<Workshop.Spot> spots = w.getSpots();
        int index = 0;
        while (index < spots.size() && spots.get(index).scored()) {
            index++;
        }
        if (index == spots.size()) {
            if (told) {
                tell(SPOTS_PREFIX + "finished", spots.size(), spots.size());
            }
            return;
        }
        int i = index;
        Workshop.Spot spot = spots.get(i);
        SpotScorer.score(addon.getPlugin(), world, spot.x(), spot.z(), false).whenComplete((score, error) -> {
            if (workshop != w || closing) {
                return;
            }
            if (error != null) {
                addon.logError("Could not generate the land at " + spot.x() + "," + spot.z() + ": " + error);
            }
            w.setSpot(i, new Workshop.Spot(spot.x(), spot.z(), spot.biome(), error == null ? score.score() : 0,
                    error == null ? score.describe() : "?"));
            save();
            tell(SPOTS_PREFIX + FOUND, i + 1, spots.size());
            scoreNext(w, world, true);
        });
    }

    /**
     * Tells everyone in the workshop how the search is going. Chat is only used for the
     * first place and the last; the ones in between are shown above the hotbar.
     */
    private void tell(String message, int number, int total) {
        String label = addon.getAdminCommand().map(CompositeCommand::getTopLabel).orElse("boxadmin");
        for (World world : worlds.values()) {
            for (Player p : world.getPlayers()) {
                User user = User.getInstance(p);
                if (message.endsWith(FOUND) && number > 1) {
                    p.sendActionBar(user.getTranslationAsComponent(SPOTS_PREFIX + "progress", TextVariables.NUMBER,
                            String.valueOf(number), TOTAL, String.valueOf(total)));
                } else {
                    user.sendMessage(message, TextVariables.NUMBER, String.valueOf(number), TOTAL,
                            String.valueOf(total), TextVariables.LABEL, label);
                }
            }
        }
    }

    /**
     * Makes a place the middle of every box and takes the player there.
     * @param player player who picked it
     * @param index position of the place in the list of places found
     * @return true once the player is there, false if there is no such place
     */
    public CompletableFuture<Boolean> select(Player player, int index) {
        Workshop w = workshop;
        List<Workshop.Spot> spots = w == null ? List.of() : w.getSpots();
        if (w == null || closing || index < 0 || index >= spots.size() || !spots.get(index).scored()) {
            return CompletableFuture.completedFuture(false);
        }
        w.setCentre(Environment.NORMAL, spots.get(index).x(), spots.get(index).z());
        save();
        return enter(player, Environment.NORMAL);
    }

    /**
     * Shows a player where the middle of the box is, with a diamond block that only they can
     * see, and how far the box can reach, with a world border that only they can see.
     * Nothing is shown in a dimension where no place has been picked yet.
     * @param player player in a workshop world
     * @param border true to show the border, false to take it away
     */
    public void showCentre(Player player, boolean border) {
        World world = player.getWorld();
        if (workshop == null || !isWorkshopWorld(world) || !workshop.hasCentre(world.getEnvironment())) {
            player.setWorldBorder(null);
            return;
        }
        int[] centre = workshop.getCentre(world.getEnvironment());
        player.sendBlockChange(
                new Location(world, centre[0], groundHeight(world, centre[0], centre[1]), centre[1]),
                Material.DIAMOND_BLOCK.createBlockData());
        if (border) {
            WorldBorder limit = Bukkit.createWorldBorder();
            limit.setCenter(centre[0], centre[1]);
            limit.setSize(addon.getSettings().getIslandDistance() * 2D);
            limit.setWarningDistance(0);
            player.setWorldBorder(limit);
        } else {
            player.setWorldBorder(null);
        }
    }

    /**
     * Finds the ground at a place: the soil under any trees and the bed under any water.
     * @return y of the ground block
     */
    static int groundHeight(World world, int x, int z) {
        if (world.getEnvironment() == Environment.NETHER) {
            // The Nether has a roof, so look for a floor with room above it instead
            for (int y = NETHER_ROOF; y > NETHER_FLOOR; y--) {
                if (world.getBlockAt(x, y, z).getType().isSolid() && world.getBlockAt(x, y + 1, z).getType().isAir()
                        && world.getBlockAt(x, y + 2, z).getType().isAir()) {
                    return y;
                }
            }
            return 63;
        }
        int y = world.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR);
        // Go down through trees to the soil they grow in
        while (y > world.getMinHeight() && isTree(world.getBlockAt(x, y, z).getType())) {
            y--;
        }
        return y;
    }

    private static boolean isTree(Material m) {
        return Tag.LOGS.isTagged(m) || Tag.LEAVES.isTagged(m);
    }

    /**
     * Finds somewhere to stand at a place: on the ground, under the trees, and next to a
     * tree trunk if one is in the way. Over water it is the surface.
     * @return where a player can be put
     */
    static Location standingPlace(World world, int x, int z) {
        // The place is the corner of a chunk, so looking this way stays inside the chunk
        for (int reach = 0; reach <= STAND_SEARCH; reach++) {
            for (int dx = 0; dx <= reach; dx++) {
                for (int dz = 0; dz <= reach; dz++) {
                    Location at = Math.max(dx, dz) == reach ? openGround(world, x + dx, z + dz) : null;
                    if (at != null) {
                        return at;
                    }
                }
            }
        }
        return new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1D, z + 0.5);
    }

    /**
     * @return a place to stand on the ground of a column, or null if something is in the way
     */
    @Nullable
    private static Location openGround(World world, int x, int z) {
        int y = groundHeight(world, x, z);
        Block feet = world.getBlockAt(x, y + 1, z);
        Block head = world.getBlockAt(x, y + 2, z);
        return feet.isPassable() && head.isPassable() && !feet.isLiquid()
                ? new Location(world, x + 0.5, y + 1D, z + 0.5)
                : null;
    }

    /**
     * Notes that a player has come into the workshop.
     * @param player player
     * @param mode the game mode they had outside
     */
    public void playerEntered(Player player, GameMode mode) {
        if (workshop != null) {
            workshop.addPlayer(player.getUniqueId(), mode);
            save();
        }
    }

    /**
     * Notes that a player has left the workshop.
     * @param player player
     * @return the game mode they had outside, or null if it is not known
     */
    @Nullable
    public GameMode playerLeft(Player player) {
        GameMode mode = workshop == null ? null : workshop.removePlayer(player.getUniqueId());
        if (mode != null) {
            save();
        }
        return mode;
    }

    /**
     * Checks whether a player was in the workshop when it was removed while they were away.
     * @param uuid player
     * @return the game mode they had before they went in, or null if they were not
     */
    @Nullable
    public GameMode takeDisplaced(UUID uuid) {
        GameMode mode = displaced.remove(uuid);
        if (mode != null) {
            saveDisplaced();
        }
        return mode;
    }

    /**
     * Sends a player out of the workshop: to their box if they have one, otherwise to the
     * game's spawn, otherwise to the server's.
     * @param player player
     * @return completes when the player has been moved
     */
    public CompletableFuture<Boolean> sendOut(Player player) {
        World overWorld = addon.getOverWorld();
        if (overWorld != null && addon.getIslands().hasIsland(overWorld, player.getUniqueId())) {
            return addon.getIslands().homeTeleportAsync(overWorld, player);
        }
        Location spawn = overWorld == null ? null : addon.getIslands().getSpawnPoint(overWorld);
        return player.teleportAsync(spawn == null ? Bukkit.getWorlds().get(0).getSpawnLocation() : spawn);
    }

    /**
     * Throws the workshop away. Everyone in it is sent out first, and its worlds are deleted.
     * @return completes with true when it has gone, or false if there was nothing to remove
     */
    public CompletableFuture<Boolean> discard() {
        if (workshop == null || closing) {
            return CompletableFuture.completedFuture(false);
        }
        closing = true;
        List<CompletableFuture<Boolean>> moves = new ArrayList<>();
        for (World world : worlds.values()) {
            world.getPlayers().forEach(p -> moves.add(sendOut(p)));
        }
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        CompletableFuture.allOf(moves.toArray(CompletableFuture[]::new)).whenComplete(
                (v, error) -> Bukkit.getScheduler().runTask(addon.getPlugin(), () -> done.complete(remove())));
        return done;
    }

    /**
     * Unloads and deletes the worlds and forgets the workshop. Call on the main thread.
     */
    private boolean remove() {
        // Players who could not be sent anywhere better go to the server's spawn
        Location fallback = Bukkit.getWorlds().get(0).getSpawnLocation();
        List<File> folders = new ArrayList<>();
        boolean unloaded = true;
        for (World world : worlds.values()) {
            List.copyOf(world.getPlayers()).forEach(p -> p.teleport(fallback));
            File folder = world.getWorldFolder();
            if (Bukkit.unloadWorld(world, false)) {
                folders.add(folder);
            } else {
                addon.logWarning("Could not unload " + world.getName() + ". Delete its folder when the server is stopped.");
                unloaded = false;
            }
        }
        // Players who are away will find the world gone when they come back
        displaced.putAll(workshop.getPlayers());
        Bukkit.getOnlinePlayers().forEach(p -> displaced.remove(p.getUniqueId()));
        saveDisplaced();
        worlds.clear();
        biomes.clear();
        workshop = null;
        closing = false;
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            addon.logError("Could not delete " + file + ": " + e.getMessage());
        }
        Bukkit.getScheduler().runTaskAsynchronously(addon.getPlugin(), () -> folders.forEach(this::delete));
        return unloaded;
    }

    private void delete(File folder) {
        // Be very sure this is a workshop world before deleting anything
        if (!folder.getName().startsWith(WORLD) || !folder.isDirectory()) {
            return;
        }
        try (Stream<Path> files = Files.walk(folder.toPath())) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            addon.logWarning("Could not delete " + folder + ": " + e.getMessage());
        }
    }

    private void save() {
        try {
            workshop.save(file);
        } catch (IOException e) {
            addon.logError("Could not save " + file + ": " + e.getMessage());
        }
    }

    private void saveDisplaced() {
        YamlConfiguration config = new YamlConfiguration();
        displaced.forEach((uuid, mode) -> config.set(uuid.toString(), mode.name()));
        try {
            if (displaced.isEmpty()) {
                Files.deleteIfExists(displacedFile.toPath());
            } else {
                Files.createDirectories(areasFolder.toPath());
                config.save(displacedFile);
            }
        } catch (IOException e) {
            addon.logError("Could not save " + displacedFile + ": " + e.getMessage());
        }
    }
}
