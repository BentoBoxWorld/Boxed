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
        return world.getChunkAtAsync(centre[0] >> 4, centre[1] >> 4).thenCompose(chunk -> player
                .teleportAsync(new Location(world, centre[0] + 0.5, standingHeight(world, centre[0], centre[1]),
                        centre[1] + 0.5)));
    }

    /**
     * @return the height to put a player at so that they are standing on the ground
     */
    static int standingHeight(World world, int x, int z) {
        if (world.getEnvironment() != Environment.NETHER) {
            return world.getHighestBlockYAt(x, z) + 1;
        }
        // The Nether has a roof, so look for a floor with room above it instead
        for (int y = NETHER_ROOF; y > NETHER_FLOOR; y--) {
            if (world.getBlockAt(x, y, z).getType().isSolid() && world.getBlockAt(x, y + 1, z).getType().isAir()
                    && world.getBlockAt(x, y + 2, z).getType().isAir()) {
                return y + 1;
            }
        }
        return 64;
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
