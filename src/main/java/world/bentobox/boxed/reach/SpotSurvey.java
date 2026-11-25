package world.bentobox.boxed.reach;

import java.util.EnumMap;
import java.util.Map;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.Tag;

import world.bentobox.boxed.reach.LandSurvey.Kind;

/**
 * A close look at the land around a place where boxes could start. It counts the blocks a
 * new box would hold as it grows and works out how good a start the place gives: dry land
 * with trees in the middle, and water, stone, iron ore and coal soon after.
 *
 * @author tastybento
 */
public class SpotSurvey {

    /**
     * How far from the middle the land is looked at, in blocks
     */
    public static final int RADIUS = 48;
    /**
     * How far from the middle the land should be dry
     */
    private static final int DRY_RADIUS = 8;
    /**
     * How far below the ground something still counts as near the surface
     */
    private static final int SHALLOW = 8;

    private static final String WOOD = "*_log";
    private static final String WATER = "water";
    private static final String STONE = "stone|cobblestone|deepslate|cobbled_deepslate|blackstone";
    private static final String IRON = "iron_ore|deepslate_iron_ore";
    private static final String COAL = "coal_ore|deepslate_coal_ore";

    /**
     * How good a start a place gives. The sizes are the box sizes at which there is enough
     * of each thing, or -1 if there is not enough within {@link SpotSurvey#RADIUS} blocks.
     * @param dryCentre true if the very middle is dry land
     * @param dryPercent how much of the land around the middle is dry
     * @param wood size that takes in four logs
     * @param water size that takes in water at the surface
     * @param stone size that takes in three blocks of stone near the surface
     * @param iron size that takes in three iron ore
     * @param coal size that takes in coal ore
     * @param score marks out of 100
     */
    public record Score(boolean dryCentre, int dryPercent, int wood, int water, int stone, int iron, int coal,
            int score) {

        /**
         * @return the score in words
         */
        public String describe() {
            return (dryCentre ? "dry land" : "middle is under water") + " (" + dryPercent + "% dry), wood at "
                    + at(wood) + ", water at " + at(water) + ", stone at " + at(stone) + ", iron ore at " + at(iron)
                    + ", coal at " + at(coal);
        }

        private static String at(int size) {
            return size < 0 ? "more than " + RADIUS : "size " + size;
        }
    }

    private final Map<Material, int[]> all = new EnumMap<>(Material.class);
    private final Map<Material, int[]> shallow = new EnumMap<>(Material.class);
    private int dry;
    private int columns;
    private int dryCentre;

    /**
     * Counts the blocks of a chunk.
     * @param snapshot the chunk, with its highest blocks
     * @param baseX block x of the chunk's corner relative to the middle of the place
     * @param baseZ block z of the chunk's corner relative to the middle of the place
     * @param minY lowest block y of the world
     */
    public void add(ChunkSnapshot snapshot, int baseX, int baseZ, int minY) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int size = LandSurvey.sizeFor(baseX + x, baseZ + z);
                if (size <= RADIUS) {
                    addColumn(snapshot, x, z, minY, size);
                }
            }
        }
    }

    private void addColumn(ChunkSnapshot snapshot, int x, int z, int minY, int size) {
        int ground = Integer.MIN_VALUE;
        for (int y = snapshot.getHighestBlockYAt(x, z); y >= minY; y--) {
            Material m = snapshot.getBlockType(x, y, z);
            if (m.isAir()) {
                continue;
            }
            all.computeIfAbsent(m, k -> new int[RADIUS + 1])[size]++;
            if (ground == Integer.MIN_VALUE && isGround(m)) {
                ground = y;
                countDry(m, size);
            }
            if (ground != Integer.MIN_VALUE && ground - y < SHALLOW) {
                shallow.computeIfAbsent(m, k -> new int[RADIUS + 1])[size]++;
            }
        }
    }

    /**
     * @return true if the block is the ground or open water, not something growing or lying on it
     */
    private static boolean isGround(Material m) {
        return m == Material.WATER || m == Material.LAVA
                || (m.isSolid() && !Tag.LEAVES.isTagged(m) && !Tag.LOGS.isTagged(m));
    }

    private void countDry(Material ground, int size) {
        boolean wet = ground == Material.WATER || ground == Material.LAVA || Tag.ICE.isTagged(ground);
        if (size <= DRY_RADIUS) {
            columns++;
            dry += wet ? 0 : 1;
        }
        if (size == 1 && !wet) {
            dryCentre++;
        }
    }

    /**
     * @return how good a start the place gives
     */
    public Score score() {
        LandSurvey everything = toSurvey(all);
        LandSurvey near = toSurvey(shallow);
        // The middle is the four columns of a box of size 1
        boolean centre = dryCentre == 4;
        int dryPercent = columns == 0 ? 0 : dry * 100 / columns;
        int wood = everything.sizeNeeded(Kind.BLOCK, WOOD, 4);
        int water = near.sizeNeeded(Kind.BLOCK, WATER, 1);
        int stone = near.sizeNeeded(Kind.BLOCK, STONE, 3);
        int iron = everything.sizeNeeded(Kind.BLOCK, IRON, 3);
        int coal = everything.sizeNeeded(Kind.BLOCK, COAL, 1);
        double score = (centre ? 30.0 * dryPercent / 100 : 0) + marks(25, wood, 4) + marks(15, water, 16)
                + marks(10, stone, 4) + marks(10, iron, 4) + marks(10, coal, 4);
        return new Score(centre, dryPercent, wood, water, stone, iron, coal, (int) Math.round(score));
    }

    /**
     * @param most the marks for having the thing within the good size
     * @param size the box size that has enough of the thing, or -1
     * @param good the size that earns full marks
     * @return the marks, which fall to nothing at the edge of the land looked at
     */
    private static double marks(int most, int size, int good) {
        if (size < 0) {
            return 0;
        }
        return most * Math.clamp((double) (RADIUS - size) / (RADIUS - good), 0D, 1D);
    }

    private static LandSurvey toSurvey(Map<Material, int[]> counts) {
        LandSurvey survey = new LandSurvey(RADIUS);
        counts.forEach((m, bySize) -> {
            String name = m.getKey().getKey();
            for (int size = 1; size <= RADIUS; size++) {
                if (bySize[size] > 0) {
                    survey.add(Kind.BLOCK, name, size, bySize[size]);
                }
            }
        });
        return survey;
    }
}
