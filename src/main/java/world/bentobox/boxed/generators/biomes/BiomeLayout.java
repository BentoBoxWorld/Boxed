package world.bentobox.boxed.generators.biomes;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.block.Biome;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.noise.SimplexNoiseGenerator;
import org.eclipse.jdt.annotation.Nullable;

/**
 * How the chosen biomes are laid out around the middle of an area. A biomes file describes
 * one layout per dimension, in one of three ways:
 * <ul>
 * <li>{@code north-east}, {@code south-east}, {@code north-west} and {@code south-west}:
 * each quarter has its own list of biomes by distance from the middle.</li>
 * <li>{@code rings}: one list of biomes by distance, the same all the way round.</li>
 * <li>{@code patches}: square patches of a given {@code size}, each given one of a list of
 * {@code biomes} at random. The patch in the middle always gets the first in the list.</li>
 * </ul>
 * Distances are a fraction of the area's radius. {@code blend} is how many blocks the edges
 * between biomes wander, so that they are not straight lines.
 * <p>
 * Instances are immutable and can be used from any thread.
 *
 * @author tastybento
 */
public class BiomeLayout {

    /**
     * Config marker for "no chosen biome here, use the one the seed has"
     */
    public static final String CUSTOM_BIOME = "CUSTOM";
    /**
     * How far apart the bends in a blended edge are, in blocks
     */
    private static final double WAVELENGTH = 80;
    /**
     * Moves the second noise sample well away from the first so the two are unrelated
     */
    private static final double NOISE_OFFSET = 1000;

    private static final Map<BlockFace, String> QUARTERS = Map.of(BlockFace.NORTH_EAST, "north-east",
            BlockFace.SOUTH_EAST, "south-east", BlockFace.NORTH_WEST, "north-west", BlockFace.SOUTH_WEST, "south-west");

    private final int dist;
    private final double blend;
    private final Map<BlockFace, TreeMap<Double, Biome>> quadrants = new EnumMap<>(BlockFace.class);
    private final int patchSize;
    private final List<Biome> patches = new ArrayList<>();
    private final SimplexNoiseGenerator noise = new SimplexNoiseGenerator(20261125L);

    /**
     * @param section the part of a biomes file for one dimension, or null for no chosen biomes
     * @param blend how many blocks the edges between biomes wander. 0 for straight edges.
     * @param dist the radius of an area in blocks
     * @param parser turns a biome name into a biome, or null if there is no such biome
     * @param errorLog where to report mistakes in the file
     */
    public BiomeLayout(@Nullable ConfigurationSection section, double blend, int dist,
            Function<String, Biome> parser, Consumer<String> errorLog) {
        this.dist = dist;
        this.blend = Math.max(0, blend);
        if (section != null && section.contains("rings")) {
            TreeMap<Double, Biome> rings = loadRings(section, "rings", parser, errorLog);
            for (BlockFace quarter : QUARTERS.keySet()) {
                quadrants.put(quarter, rings);
            }
        } else {
            QUARTERS.forEach((quarter, key) -> quadrants.put(quarter, loadRings(section, key, parser, errorLog)));
        }
        ConfigurationSection p = section == null ? null : section.getConfigurationSection("patches");
        this.patchSize = p == null ? 0 : Math.max(16, p.getInt("size", 96));
        if (p != null) {
            p.getStringList("biomes").forEach(name -> patches.add(parse(name, parser)));
        }
    }

    private static Biome parse(String name, Function<String, Biome> parser) {
        return CUSTOM_BIOME.equalsIgnoreCase(name.trim()) ? null : parser.apply(name.trim());
    }

    /**
     * Reads a list of "distance:biome" entries.
     */
    private static TreeMap<Double, Biome> loadRings(@Nullable ConfigurationSection section, String key,
            Function<String, Biome> parser, Consumer<String> errorLog) {
        TreeMap<Double, Biome> result = new TreeMap<>();
        if (section == null) {
            return result;
        }
        for (String ring : section.getStringList(key)) {
            String[] split = ring.split(":", 2);
            if (split.length != 2) {
                errorLog.accept(ring + " must be in the format ratio:biome where ratio is a double.");
                continue;
            }
            try {
                // A biome of null means that no alternative biome should be applied
                result.put(Double.parseDouble(split[0]), parse(split[1], parser));
            } catch (NumberFormatException _) {
                errorLog.accept(key + ": " + split[0] + " does not seem to be a double. For integers add a .0 to the end");
            }
        }
        return result;
    }

    /**
     * Gets the chosen biome for a block.
     * @param dx block x relative to the middle of the area
     * @param dz block z relative to the middle of the area
     * @return the biome, or null if the seed's own biome should be used there
     */
    @Nullable
    public Biome get(int dx, int dz) {
        double x = dx;
        double z = dz;
        // Chosen biomes only go as far as twice the radius, measured before any blending
        if (Math.sqrt(x * x + z * z) > dist * 2D) {
            return null;
        }
        if (blend > 0) {
            x += blend * noise.noise(dx / WAVELENGTH, dz / WAVELENGTH);
            z += blend * noise.noise(dx / WAVELENGTH + NOISE_OFFSET, dz / WAVELENGTH + NOISE_OFFSET);
        }
        if (!patches.isEmpty()) {
            return patch(Math.floorDiv((int) Math.floor(x) + patchSize / 2, patchSize),
                    Math.floorDiv((int) Math.floor(z) + patchSize / 2, patchSize));
        }
        BlockFace quarter;
        if (x <= 0 && z <= 0) {
            quarter = BlockFace.NORTH_WEST;
        } else if (x > 0 && z <= 0) {
            quarter = BlockFace.NORTH_EAST;
        } else if (x <= 0) {
            quarter = BlockFace.SOUTH_WEST;
        } else {
            quarter = BlockFace.SOUTH_EAST;
        }
        Map.Entry<Double, Biome> en = quadrants.get(quarter).ceilingEntry(Math.sqrt(x * x + z * z) / dist);
        return en == null ? null : en.getValue();
    }

    /**
     * Picks the biome of a patch. It only depends on which patch it is, so every box of every
     * area that uses the same file gets the same pattern.
     */
    private Biome patch(int px, int pz) {
        if (px == 0 && pz == 0) {
            return patches.get(0);
        }
        long h = px * 341873128712L + pz * 132897987541L;
        h ^= h >>> 29;
        h *= 0x9E3779B97F4A7C15L;
        h ^= h >>> 32;
        return patches.get((int) Math.floorMod(h, (long) patches.size()));
    }

    /**
     * @return every biome this layout can choose
     */
    public Set<Biome> biomes() {
        Set<Biome> all = new LinkedHashSet<>();
        quadrants.values().forEach(q -> q.values().stream().filter(Objects::nonNull).forEach(all::add));
        patches.stream().filter(Objects::nonNull).forEach(all::add);
        return all;
    }
}
