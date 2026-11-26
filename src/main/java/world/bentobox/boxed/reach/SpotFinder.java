package world.bentobox.boxed.reach;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Looks through the biomes of a seed for places where a box could start: wooded land with
 * water a short walk away. Looking up a biome is cheap, so a lot of ground can be covered.
 * The best places are then looked at properly with a {@link SpotSurvey}.
 *
 * @author tastybento
 */
public class SpotFinder {

    /**
     * Something that knows the biome at the surface of a seed.
     */
    @FunctionalInterface
    public interface BiomeLookup {
        /**
         * @param x block x
         * @param z block z
         * @return name of the biome, e.g. birch_forest
         */
        String biomeAt(int x, int z);
    }

    /**
     * A place that looks promising from its biomes.
     * @param x block x of the middle
     * @param z block z of the middle
     * @param biome biome at the middle
     * @param waterDistance how far away the nearest river or sea is, or -1 if there is none nearby
     * @param landPercent how much of the land a box can grow into is not sea or river
     * @param rating how promising the place is. Higher is better.
     */
    public record Candidate(int x, int z, String biome, int waterDistance, int landPercent, int rating) {
    }

    private static final int[] WATER_RINGS = { 32, 48, 64, 96, 128 };
    private static final int[][] DIRECTIONS = { { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 }, { -1, 0 }, { -1, -1 },
            { 0, -1 }, { 1, -1 } };
    /**
     * The best distance to water. Nearer and the box may start on a beach.
     */
    private static final int IDEAL_WATER = 40;
    /**
     * How many times more places have the rest of their land looked at than are returned
     */
    private static final int SHORTLIST = 5;
    private static final Comparator<Candidate> BEST = Comparator.comparingInt(Candidate::rating).reversed()
            .thenComparingLong(c -> (long) c.x() * c.x() + (long) c.z() * c.z()).thenComparingInt(Candidate::x)
            .thenComparingInt(Candidate::z);

    private final BiomeLookup lookup;
    private final int landRadius;

    /**
     * @param lookup the biomes of the seed
     * @param landRadius how far a box can grow from its middle
     */
    public SpotFinder(BiomeLookup lookup, int landRadius) {
        this.lookup = lookup;
        this.landRadius = landRadius;
    }

    /**
     * @param biome biome name
     * @return true if the biome is open water
     */
    public static boolean isWater(String biome) {
        return biome.contains("ocean") || biome.contains("river");
    }

    /**
     * @param biome biome name
     * @return true if the biome is fairly level dry land that grows plenty of trees
     */
    public static boolean isWooded(String biome) {
        if (biome.contains("windswept") || biome.contains("swamp") || biome.contains("badlands")) {
            return false;
        }
        return biome.contains("forest") || biome.contains("taiga") || biome.contains("jungle")
                || biome.equals("cherry_grove");
    }

    /**
     * Searches a square of the seed.
     * @param radius how far from the middle of the seed to look, in blocks
     * @param step distance between the places tried. It is rounded to whole chunks.
     * @param count how many places to return
     * @param separation the least distance between any two places returned
     * @return the most promising places, best first
     */
    public List<Candidate> find(int radius, int step, int count, int separation) {
        int grid = Math.max(16, step >> 4 << 4);
        int limit = radius / grid * grid;
        List<Candidate> found = new ArrayList<>();
        for (int x = -limit; x <= limit; x += grid) {
            for (int z = -limit; z <= limit; z += grid) {
                String biome = lookup.biomeAt(x, z);
                if (isWooded(biome) && isDryAround(x, z)) {
                    int water = waterDistance(x, z);
                    found.add(new Candidate(x, z, biome, water, 0, waterRating(water)));
                }
            }
        }
        // The land a box grows into is only looked at for the best of them, because it is slower
        List<Candidate> shortlist = apart(found.stream().sorted(BEST).toList(), count * SHORTLIST, separation)
                .stream().map(c -> at(c.x(), c.z())).sorted(BEST).toList();
        return apart(shortlist, count, separation);
    }

    /**
     * Describes a place, whether or not it is a good one.
     * @param x block x of the middle
     * @param z block z of the middle
     * @return the place
     */
    public Candidate at(int x, int z) {
        int water = waterDistance(x, z);
        int land = landPercent(x, z);
        return new Candidate(x, z, lookup.biomeAt(x, z), water, land, waterRating(water) + land / 2);
    }

    private static int waterRating(int water) {
        return water < 0 ? 0 : 100 - Math.abs(water - IDEAL_WATER);
    }

    /**
     * Takes the first places from a list, skipping any that are too close to one already taken.
     */
    private static List<Candidate> apart(List<Candidate> sorted, int count, int separation) {
        List<Candidate> chosen = new ArrayList<>();
        for (Candidate c : sorted) {
            if (chosen.size() < count && chosen.stream().noneMatch(o -> Math.abs(o.x() - c.x()) < separation
                    && Math.abs(o.z() - c.z()) < separation)) {
                chosen.add(c);
            }
        }
        return chosen;
    }

    /**
     * @return true if there is no open water right next to the place
     */
    private boolean isDryAround(int x, int z) {
        for (int dx = -12; dx <= 12; dx += 24) {
            for (int dz = -12; dz <= 12; dz += 24) {
                if (isWater(lookup.biomeAt(x + dx, z + dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    private int waterDistance(int x, int z) {
        for (int ring : WATER_RINGS) {
            for (int[] d : DIRECTIONS) {
                if (isWater(lookup.biomeAt(x + d[0] * ring, z + d[1] * ring))) {
                    return ring;
                }
            }
        }
        return -1;
    }

    private int landPercent(int x, int z) {
        int land = 0;
        int total = 0;
        int gap = Math.max(16, landRadius / 4);
        for (int dx = -landRadius; dx <= landRadius; dx += gap) {
            for (int dz = -landRadius; dz <= landRadius; dz += gap) {
                total++;
                if (!isWater(lookup.biomeAt(x + dx, z + dz))) {
                    land++;
                }
            }
        }
        return land * 100 / total;
    }
}
