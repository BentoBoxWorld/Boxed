package world.bentobox.boxed.generators.biomes;

import java.util.Locale;

import org.bukkit.util.noise.SimplexNoiseGenerator;
import org.eclipse.jdt.annotation.Nullable;

/**
 * A round patch of one biome that an admin has put on the land of an area, on top of the
 * biomes it would have had. The edge wanders so that it does not look drawn with a compass.
 * Patches are placed in the seed world, so x and z are seed world blocks.
 *
 * @param x block x of the middle
 * @param z block z of the middle
 * @param radius how far the patch reaches from its middle, in blocks
 * @param biome name of the biome, e.g. swamp
 * @author tastybento
 */
public record BiomePatch(int x, int z, int radius, String biome) {

    /**
     * The smallest patch that can be made
     */
    public static final int MIN_RADIUS = 8;
    /**
     * The largest patch that can be made
     */
    public static final int MAX_RADIUS = 160;
    /**
     * How far apart the bends in the edge are, in blocks
     */
    private static final double WAVELENGTH = 40;
    private static final double NOISE_OFFSET = 1000;
    private static final SimplexNoiseGenerator NOISE = new SimplexNoiseGenerator(20261126L);

    /**
     * Makes a patch, keeping the radius within what is allowed and the name in lower case.
     */
    public BiomePatch {
        radius = Math.clamp(radius, MIN_RADIUS, MAX_RADIUS);
        biome = biome.toLowerCase(Locale.ENGLISH).replace("minecraft:", "");
    }

    /**
     * @return how many blocks the edge can wander in or out
     */
    public int wander() {
        return Math.min(12, radius / 4);
    }

    /**
     * @return the furthest from the middle that the patch can reach, in blocks
     */
    public int reach() {
        return radius + wander();
    }

    /**
     * @param bx block x
     * @param bz block z
     * @return true if the block is in the patch
     */
    public boolean contains(int bx, int bz) {
        double dx = (double) bx - x;
        double dz = (double) bz - z;
        int reach = reach();
        if (Math.abs(dx) > reach || Math.abs(dz) > reach) {
            return false;
        }
        int wander = wander();
        dx += wander * NOISE.noise(bx / WAVELENGTH, bz / WAVELENGTH);
        dz += wander * NOISE.noise(bx / WAVELENGTH + NOISE_OFFSET, bz / WAVELENGTH + NOISE_OFFSET);
        return dx * dx + dz * dz <= (double) radius * radius;
    }

    /**
     * @return the patch as it is written in a file: x,z,radius,biome
     */
    public String text() {
        return x + "," + z + "," + radius + "," + biome;
    }

    /**
     * @param text a patch as written by {@link #text()}
     * @return the patch, or null if the text is not one
     */
    @Nullable
    public static BiomePatch parse(String text) {
        String[] parts = text.split(",");
        if (parts.length != 4 || parts[3].isBlank()) {
            return null;
        }
        try {
            return new BiomePatch(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()), parts[3].trim());
        } catch (NumberFormatException _) {
            return null;
        }
    }
}
