package world.bentobox.boxed.reach;

import java.util.List;
import java.util.Optional;
import java.util.function.IntBinaryOperator;

/**
 * Picks where the middle of an area's Nether should be so that its boxes get a fortress.
 * A fortress cannot be placed like other structures, so the only way to have one is to take
 * the land from a part of the seed that has one. The middle is put on open ground a short
 * way from a blaze spawner: close enough that the box soon grows to take it in, but not so
 * close that new arrivals land among the blazes.
 *
 * @author tastybento
 */
public final class FortressSpot {

    /**
     * The nearest a blaze spawner may be, as a box size
     */
    public static final int MIN_SPAWNER_SIZE = 32;
    /**
     * The nearest any part of the fortress may be, as a box size
     */
    public static final int MIN_FORTRESS_SIZE = 8;

    /**
     * A place for the middle of the Nether.
     * @param x block x
     * @param z block z
     * @param spawnerSize the box size that takes in a blaze spawner
     * @param fortressSize the box size that takes in the first of the fortress
     */
    public record Spot(int x, int z, int spawnerSize, int fortressSize) {
    }

    private FortressSpot() {
    }

    /**
     * Looks over the chunk corners of a piece of land for the best place.
     * @param min lowest block x and z of the land
     * @param max highest block x and z of the land
     * @param spawners block x and z of each blaze spawner
     * @param bricks block x and z of each column that has fortress in it
     * @param open says if a column is open ground: 1 if it is, 0 if not
     * @return the best place, or empty if the land has no spawner or no open ground near one
     */
    public static Optional<Spot> choose(int[] min, int[] max, List<int[]> spawners, List<int[]> bricks,
            IntBinaryOperator open) {
        Spot best = null;
        for (int x = (min[0] + 15) >> 4 << 4; x <= max[0]; x += 16) {
            for (int z = (min[1] + 15) >> 4 << 4; z <= max[1]; z += 16) {
                int spawner = nearest(spawners, x, z);
                int fortress = nearest(bricks, x, z);
                if (spawner >= MIN_SPAWNER_SIZE && spawner != Integer.MAX_VALUE && fortress >= MIN_FORTRESS_SIZE
                        && open.applyAsInt(x, z) == 1 && (best == null || spawner < best.spawnerSize()
                                || (spawner == best.spawnerSize() && fortress < best.fortressSize()))) {
                    best = new Spot(x, z, spawner, fortress);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Measures a place that has already been picked.
     * @param x block x
     * @param z block z
     * @param spawners block x and z of each blaze spawner
     * @param bricks block x and z of each column that has fortress in it
     * @return the place, with sizes of {@link Integer#MAX_VALUE} where there is nothing to reach
     */
    public static Spot at(int x, int z, List<int[]> spawners, List<int[]> bricks) {
        return new Spot(x, z, nearest(spawners, x, z), nearest(bricks, x, z));
    }

    /**
     * @return the smallest box around a place that takes in one of the columns
     */
    private static int nearest(List<int[]> columns, int x, int z) {
        int nearest = Integer.MAX_VALUE;
        for (int[] c : columns) {
            nearest = Math.min(nearest, LandSurvey.sizeFor(c[0] - x, c[1] - z));
        }
        return nearest;
    }
}
