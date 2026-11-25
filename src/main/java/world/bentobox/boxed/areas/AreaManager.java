package world.bentobox.boxed.areas;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.bukkit.World.Environment;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.boxed.generators.template.Template;

/**
 * Knows the areas, where each one is in the world and what land it has.
 * <p>
 * Boxes are placed on a grid. Each area owns one row of that grid, which runs east-west
 * without end, so the area a chunk belongs to can be worked out from its z coordinate alone.
 * That is what lets the chunk generator pick the right land for any chunk. Which row an area
 * owns is saved in {@code areas/grid.yml} the first time the area is seen and never changes.
 * A row that no area owns has the default area's land.
 *
 * @author tastybento
 */
public class AreaManager {

    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_]+");
    private static final String ROWS = "rows";

    private final File gridFile;
    private final YamlConfiguration grid;
    private final Map<String, Area> areas = new LinkedHashMap<>();
    private final Map<Integer, Area> areaByRow = new ConcurrentHashMap<>();
    private final Map<Integer, Map<Environment, Template>> templates = new ConcurrentHashMap<>();
    private final Set<String> pending = ConcurrentHashMap.newKeySet();
    private final Area defaultArea;

    /**
     * @param config the contents of areas.yml
     * @param gridFile file that records which row each area owns
     * @param defaultSeed seed of the default area if it does not set one
     * @param errorLog where to report problems with the config
     */
    public AreaManager(YamlConfiguration config, File gridFile, long defaultSeed,
            java.util.function.Consumer<String> errorLog) {
        this.gridFile = gridFile;
        this.grid = YamlConfiguration.loadConfiguration(gridFile);
        ConfigurationSection section = config.getConfigurationSection("areas");
        List<String> ids = getIds(section, errorLog);
        boolean changed = false;
        for (String id : ids) {
            String path = ROWS + "." + id;
            if (!grid.contains(path)) {
                grid.set(path, nextFreeRow());
                changed = true;
            }
            ConfigurationSection s = section == null ? null : section.getConfigurationSection(id);
            if (s == null) {
                s = new YamlConfiguration();
            }
            String requires = getRequirement(s, id, ids, errorLog);
            int[] centre = parseCentre(s.getString("centre", s.getString("center", "")), id, errorLog);
            Area area = new Area(id, s.getLong("seed", defaultSeed), s.getString("biomes", "biomes.yml"),
                    s.getString("structures", "structures.yml"), grid.getInt(path), s.getBoolean("enabled", true),
                    requires, Math.max(0, s.getInt("unlocks-next-at", 0)), centre[0], centre[1]);
            areas.put(id, area);
            areaByRow.put(area.row(), area);
        }
        defaultArea = areas.get(Area.DEFAULT);
        if (changed) {
            save(errorLog);
        }
    }

    /**
     * Reads where in the seed world the middle of an area is. Land is copied in whole chunks,
     * so the spot is moved to the corner of its chunk.
     * @param text "x,z", or empty for the middle of the seed world
     * @return block x and z
     */
    static int[] parseCentre(String text, String id, java.util.function.Consumer<String> errorLog) {
        if (text.isBlank()) {
            return new int[] { 0, 0 };
        }
        String[] xz = text.split(",");
        try {
            if (xz.length == 2) {
                return new int[] { Integer.parseInt(xz[0].trim()) >> 4 << 4, Integer.parseInt(xz[1].trim()) >> 4 << 4 };
            }
        } catch (NumberFormatException _) {
            // Reported below
        }
        errorLog.accept("Area " + id + ": centre must be two whole numbers like 1200,-640. Using 0,0.");
        return new int[] { 0, 0 };
    }

    /**
     * Lists the names of the areas in the config. The default area always exists and always
     * comes first.
     */
    private static List<String> getIds(ConfigurationSection section, java.util.function.Consumer<String> errorLog) {
        List<String> ids = new ArrayList<>();
        ids.add(Area.DEFAULT);
        if (section == null) {
            return ids;
        }
        for (String id : section.getKeys(false)) {
            if (!VALID_ID.matcher(id).matches()) {
                errorLog.accept("Area name '" + id
                        + "' is not valid. Use lower case letters, numbers and underscores only. Ignoring it.");
            } else if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    /**
     * @return the area that an area requires, or null if it requires none or the one it names
     *         is not a different, known area
     */
    private static String getRequirement(ConfigurationSection s, String id, List<String> ids,
            java.util.function.Consumer<String> errorLog) {
        String requires = s.getString("requires");
        if (requires != null && (!ids.contains(requires) || requires.equals(id))) {
            errorLog.accept("Area '" + id + "' requires area '" + requires
                    + "', which is not a different, known area. Ignoring the requirement.");
            return null;
        }
        return requires;
    }

    /**
     * Rows are handed out in the order 0, 1, 2... Rows of areas that have since been removed
     * from the config stay taken, because their boxes are still in the world.
     */
    private int nextFreeRow() {
        ConfigurationSection s = grid.getConfigurationSection(ROWS);
        int row = 0;
        if (s != null) {
            for (String id : s.getKeys(false)) {
                row = Math.max(row, s.getInt(id) + 1);
            }
        }
        return row;
    }

    private void save(java.util.function.Consumer<String> errorLog) {
        try {
            File parent = gridFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            grid.save(gridFile);
        } catch (IOException e) {
            errorLog.accept("Could not save " + gridFile + ": " + e.getMessage());
        }
    }

    /**
     * @return all the areas, default first
     */
    public Collection<Area> getAreas() {
        return areas.values();
    }

    /**
     * @param id name of an area
     * @return the area, or null if there is no area with that name
     */
    @Nullable
    public Area getArea(@Nullable String id) {
        return id == null ? null : areas.get(id);
    }

    /**
     * Notes an area that has been added to the config but will not exist until the server
     * restarts. Boxes cannot be made in it until then.
     * @param id name of the new area
     */
    public void addPending(String id) {
        pending.add(id);
    }

    /**
     * @param id name of an area or blueprint bundle, may be null
     * @return true if this is a new area that is waiting for a restart
     */
    public boolean isPending(@Nullable String id) {
        return id != null && pending.contains(id);
    }

    /**
     * @param id name to check
     * @return true if the name can be used for an area
     */
    public static boolean isValidName(String id) {
        return VALID_ID.matcher(id).matches();
    }

    /**
     * @return the default area
     */
    @NonNull
    public Area getDefaultArea() {
        return defaultArea;
    }

    /**
     * Gets the area that boxes made with a blueprint bundle go in.
     * @param bundle unique id of the blueprint bundle, may be null
     * @return the area with the same name as the bundle, otherwise the default area
     */
    @NonNull
    public Area getAreaForBundle(@Nullable String bundle) {
        Area area = bundle == null ? null : areas.get(bundle);
        return area == null ? defaultArea : area;
    }

    /**
     * Gets the area that owns a row of the box grid. Safe to call from any thread.
     * @param row grid row
     * @return the area, which is the default area if no area owns the row
     */
    @NonNull
    public Area getAreaAtRow(int row) {
        Area area = areaByRow.get(row);
        return area == null ? defaultArea : area;
    }

    /**
     * Sets the land of an area.
     * @param area area
     * @param env environment
     * @param template the land
     * @return the land the area had before, or null
     */
    @Nullable
    public Template setTemplate(Area area, Environment env, Template template) {
        return templates.computeIfAbsent(area.row(), k -> new ConcurrentHashMap<>()).put(env, template);
    }

    /**
     * Gets the land of an area. Safe to call from any thread.
     * @param area area
     * @param env environment
     * @return the land, or null if the area has none in this environment
     */
    @Nullable
    public Template getTemplate(Area area, Environment env) {
        Map<Environment, Template> map = templates.get(area.row());
        return map == null ? null : map.get(env);
    }

    /**
     * Removes and returns the land of every area so that it can be closed.
     * @return all templates
     */
    public List<Template> removeTemplates() {
        List<Template> all = new ArrayList<>();
        templates.values().forEach(m -> all.addAll(new EnumMap<>(m).values()));
        templates.clear();
        return all;
    }

    /**
     * Converts a position number to a grid column. Positions spread out from the middle:
     * 0, 1, -1, 2, -2...
     * @param position position number, 0 or more
     * @return grid column
     */
    public static int column(int position) {
        return (position & 1) == 1 ? (position + 1) / 2 : -position / 2;
    }
}
