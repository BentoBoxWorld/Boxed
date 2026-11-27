package world.bentobox.boxed.workshop;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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

    private static final String PLAYERS_KEY = "players";
    private static final String BIOMES_KEY = "biomes";
    private static final String CENTRES_KEY = "centres";

    private static final String SPOTS_KEY = "spots";

    /**
     * A place that could be the middle of every box.
     * @param x block x
     * @param z block z
     * @param biome the biome the seed has there
     * @param score how good a start it gives out of 100, or -1 if its land has not been looked at yet
     * @param description what the score is made of
     */
    public record Spot(int x, int z, String biome, int score, String description) {
        /**
         * @return true if the land has been looked at
         */
        public boolean scored() {
            return score >= 0;
        }
    }

    private final long seed;
    private final String biomes;
    private final String area;
    private final Map<Environment, int[]> centres = new EnumMap<>(Environment.class);
    private final Map<UUID, GameMode> players = new HashMap<>();
    private final List<Spot> spots = new ArrayList<>();

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
     * @param env dimension
     * @return true if a place has been picked for the middle of every box
     */
    public boolean hasCentre(Environment env) {
        return centres.containsKey(env);
    }

    /**
     * @return the places found so far that could be the middle of every box. Their order
     *         never changes, so a place can be named by its position in the list.
     */
    public List<Spot> getSpots() {
        return List.copyOf(spots);
    }

    /**
     * @param found the places to choose from
     */
    public void setSpots(List<Spot> found) {
        spots.clear();
        spots.addAll(found);
    }

    /**
     * @param index position in the list
     * @param spot the place, now that its land has been looked at
     */
    public void setSpot(int index, Spot spot) {
        spots.set(index, spot);
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
        config.set(BIOMES_KEY, biomes);
        config.set("area", area);
        centres.forEach((env, c) -> config.set(CENTRES_KEY + "." + env.name().toLowerCase(Locale.ENGLISH), c[0] + "," + c[1]));
        players.forEach((uuid, mode) -> config.set(PLAYERS_KEY + "." + uuid, mode.name()));
        List<Map<String, Object>> list = new ArrayList<>();
        for (Spot spot : spots) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("x", spot.x());
            map.put("z", spot.z());
            map.put("biome", spot.biome());
            map.put("score", spot.score());
            map.put("description", spot.description());
            list.add(map);
        }
        config.set(SPOTS_KEY, list);
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
        if (!config.contains("seed") || config.getString(BIOMES_KEY) == null) {
            return null;
        }
        Workshop workshop = new Workshop(config.getLong("seed"), config.getString(BIOMES_KEY), config.getString("area"));
        workshop.loadCentres(config.getConfigurationSection(CENTRES_KEY));
        workshop.loadPlayers(config.getConfigurationSection(PLAYERS_KEY));
        List<Spot> found = new ArrayList<>();
        for (Map<?, ?> map : config.getMapList(SPOTS_KEY)) {
            if (map.get("x") instanceof Number x && map.get("z") instanceof Number z
                    && map.get("score") instanceof Number score) {
                found.add(new Spot(x.intValue(), z.intValue(), String.valueOf(map.get("biome")), score.intValue(),
                        String.valueOf(map.get("description"))));
            }
        }
        workshop.setSpots(found);
        return workshop;
    }

    private void loadCentres(@Nullable ConfigurationSection c) {
        if (c == null) {
            return;
        }
        for (String key : c.getKeys(false)) {
            String[] xz = c.getString(key, "").split(",");
            try {
                setCentre(Environment.valueOf(key.toUpperCase(Locale.ENGLISH)), Integer.parseInt(xz[0].trim()),
                        Integer.parseInt(xz[1].trim()));
            } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException _) {
                // A centre that cannot be read is left at 0,0
            }
        }
    }

    private void loadPlayers(@Nullable ConfigurationSection p) {
        if (p == null) {
            return;
        }
        for (String key : p.getKeys(false)) {
            try {
                addPlayer(UUID.fromString(key), GameMode.valueOf(p.getString(key, "")));
            } catch (IllegalArgumentException _) {
                // Not a player entry
            }
        }
    }
}
