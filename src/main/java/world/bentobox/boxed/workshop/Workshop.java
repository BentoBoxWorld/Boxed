package world.bentobox.boxed.workshop;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.World.Environment;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.eclipse.jdt.annotation.Nullable;

/**
 * An area that an admin is working on. It is saved every time it changes, so it is still
 * there after a restart.
 *
 * @author tastybento
 */
public class Workshop {

    private static final String PLAYERS = "players";
    private static final String CENTRES = "centres";

    private final long seed;
    private final String biomes;
    private final String area;
    private final Map<Environment, int[]> centres = new HashMap<>();
    private final Map<UUID, GameMode> players = new HashMap<>();

    /**
     * @param seed world seed of the land
     * @param biomes name of the biomes file, relative to the addon's folder
     * @param area name of the existing area this started from, or null if it is a new one
     */
    public Workshop(long seed, String biomes, @Nullable String area) {
        this.seed = seed;
        this.biomes = biomes;
        this.area = area;
    }

    /**
     * @return world seed of the land
     */
    public long getSeed() {
        return seed;
    }

    /**
     * @return name of the biomes file, relative to the addon's folder
     */
    public String getBiomes() {
        return biomes;
    }

    /**
     * @return name of the existing area this started from, or null if it is a new one
     */
    @Nullable
    public String getArea() {
        return area;
    }

    /**
     * @param env dimension
     * @return block x and z of the place that will be the middle of every box
     */
    public int[] getCentre(Environment env) {
        return centres.getOrDefault(env, new int[] { 0, 0 }).clone();
    }

    /**
     * @param env dimension
     * @param x block x of the place that will be the middle of every box
     * @param z block z
     */
    public void setCentre(Environment env, int x, int z) {
        centres.put(env, new int[] { x, z });
    }

    /**
     * Notes a player who has come in, and the game mode to give back when they leave.
     * @param uuid player
     * @param mode the game mode they had outside
     */
    public void addPlayer(UUID uuid, GameMode mode) {
        players.putIfAbsent(uuid, mode);
    }

    /**
     * @param uuid player who has left
     * @return the game mode they had outside, or null if they were not known to be inside
     */
    @Nullable
    public GameMode removePlayer(UUID uuid) {
        return players.remove(uuid);
    }

    /**
     * @return the players who are inside, online or not, and the game mode each had outside
     */
    public Map<UUID, GameMode> getPlayers() {
        return Map.copyOf(players);
    }

    /**
     * @param file file to save to
     * @throws IOException if the file cannot be written
     */
    public void save(File file) throws IOException {
        YamlConfiguration config = new YamlConfiguration();
        config.set("seed", seed);
        config.set("biomes", biomes);
        config.set("area", area);
        centres.forEach((env, c) -> config.set(CENTRES + "." + env.name().toLowerCase(Locale.ENGLISH), c[0] + "," + c[1]));
        players.forEach((uuid, mode) -> config.set(PLAYERS + "." + uuid, mode.name()));
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        config.save(file);
    }

    /**
     * @param file file written by {@link #save(File)}
     * @return the workshop, or null if the file does not exist or does not hold one
     */
    @Nullable
    public static Workshop load(File file) {
        if (!file.exists()) {
            return null;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (!config.contains("seed") || config.getString("biomes") == null) {
            return null;
        }
        Workshop workshop = new Workshop(config.getLong("seed"), config.getString("biomes"), config.getString("area"));
        ConfigurationSection c = config.getConfigurationSection(CENTRES);
        if (c != null) {
            for (String key : c.getKeys(false)) {
                String[] xz = c.getString(key, "").split(",");
                try {
                    workshop.setCentre(Environment.valueOf(key.toUpperCase(Locale.ENGLISH)),
                            Integer.parseInt(xz[0].trim()), Integer.parseInt(xz[1].trim()));
                } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException _) {
                    // A centre that cannot be read is left at 0,0
                }
            }
        }
        ConfigurationSection p = config.getConfigurationSection(PLAYERS);
        if (p != null) {
            for (String key : p.getKeys(false)) {
                try {
                    workshop.addPlayer(UUID.fromString(key), GameMode.valueOf(p.getString(key, "")));
                } catch (IllegalArgumentException _) {
                    // Not a player entry
                }
            }
        }
        return workshop;
    }
}
