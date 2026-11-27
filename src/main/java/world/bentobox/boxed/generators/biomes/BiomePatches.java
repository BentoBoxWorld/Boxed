package world.bentobox.boxed.generators.biomes;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.bukkit.block.Biome;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The patches of biome on one dimension of an area, ready to be asked "which biome is
 * here". A patch of a cave biome only changes the caves, and a patch of any other biome only
 * changes the surface, so a swamp can be laid over lush caves without losing them. Where
 * patches overlap, the one that was added last wins.
 * <p>
 * Instances are immutable and can be used from any thread.
 *
 * @author tastybento
 */
public class BiomePatches {

    /**
     * Biomes that are only found underground
     */
    private static final Set<String> CAVES = Set.of("lush_caves", "dripstone_caves", "deep_dark");

    /**
     * No patches
     */
    public static final BiomePatches NONE = new BiomePatches(List.of(), name -> null);

    private record Entry(BiomePatch patch, Biome biome, boolean cave) {
    }

    private final List<Entry> entries = new ArrayList<>();

    /**
     * @param patches the patches, oldest first
     * @param parser turns a biome name into a biome, or null if there is no such biome.
     *               Patches of unknown biomes are left out.
     */
    public BiomePatches(List<BiomePatch> patches, Function<String, Biome> parser) {
        for (BiomePatch patch : patches) {
            Biome biome = parser.apply(patch.biome());
            if (biome != null) {
                // Newest first, so the search can stop at the first hit
                entries.add(0, new Entry(patch, biome, isCave(patch.biome())));
            }
        }
    }

    /**
     * @param biome name of a biome
     * @return true if the biome is only found underground
     */
    public static boolean isCave(String biome) {
        return CAVES.contains(biome);
    }

    /**
     * @param x block x
     * @param z block z
     * @param underground true if the block is deep enough to be in a cave biome
     * @return the biome a patch gives the block, or null if no patch covers it
     */
    @Nullable
    public Biome get(int x, int z, boolean underground) {
        for (Entry entry : entries) {
            if (entry.cave() == underground && entry.patch().contains(x, z)) {
                return entry.biome();
            }
        }
        return null;
    }

    /**
     * @return true if there are no patches
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
