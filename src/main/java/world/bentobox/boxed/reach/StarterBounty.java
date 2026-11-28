package world.bentobox.boxed.reach;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.bentobox.blueprints.Blueprint;
import world.bentobox.bentobox.blueprints.dataobjects.BlueprintBlock;
import world.bentobox.boxed.reach.LandSurvey.Kind;

/**
 * What a new box is given by its starter blueprint: the blocks of the blueprint and the
 * items in its chests. These are there whatever the land is like, so a place does not have
 * to supply them itself. A blueprint with a tree means the land needs no trees.
 *
 * @author tastybento
 */
public final class StarterBounty {

    /**
     * How far above and below the spawn sign the blueprint's blocks count as a start. Blocks
     * further down are part of the ground a player digs into later.
     */
    public static final int NEAR_SIGN = 20;
    /**
     * Counts every block of the blueprint, however far from the spawn sign
     */
    public static final int WHOLE = -1;
    /**
     * A blueprint that gives nothing, or no blueprint
     */
    public static final StarterBounty NONE = new StarterBounty(List.of());

    /**
     * Some of one thing.
     * @param name the block or item, for example oak_log
     * @param size the box size that takes it in
     * @param amount how many
     * @param inChest true if it is an item in a chest, not a block
     */
    public record Entry(String name, int size, int amount, boolean inChest) {
    }

    private final List<Entry> entries;

    StarterBounty(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    /**
     * Looks at a blueprint.
     * @param blueprint the starter blueprint, or null if there is none
     * @param spawnSign where the blueprint's spawn sign is, or null if it has none
     * @param reach how far above and below the spawn sign blocks are counted, or
     *              {@link #WHOLE} for all of them. Everything is counted if there is no sign.
     * @return what the blueprint gives
     */
    public static StarterBounty of(@Nullable Blueprint blueprint, @Nullable Vector spawnSign, int reach) {
        if (blueprint == null) {
            return NONE;
        }
        Vector bedrock = blueprint.getBedrock() == null ? new Vector() : blueprint.getBedrock();
        List<Entry> entries = new ArrayList<>();
        Stream.of(blueprint.getBlocks(), blueprint.getAttached()).filter(Objects::nonNull)
                .flatMap(m -> m.entrySet().stream()).forEach(en -> {
                    Vector pos = en.getKey();
                    if (reach != WHOLE && spawnSign != null && Math.abs(pos.getBlockY() - spawnSign.getBlockY()) > reach) {
                        return;
                    }
                    int size = LandSurvey.sizeFor(pos.getBlockX() - bedrock.getBlockX(),
                            pos.getBlockZ() - bedrock.getBlockZ());
                    add(entries, en.getValue(), size);
                });
        return new StarterBounty(entries);
    }

    private static void add(List<Entry> entries, BlueprintBlock block, int size) {
        // "minecraft:oak_log[axis=y]" names the block oak_log
        String name = block.getBlockData() == null ? "air"
                : block.getBlockData().replaceFirst("^minecraft:", "").replaceFirst("\\[.*$", "");
        if (!name.equals("air")) {
            entries.add(new Entry(name, size, 1, false));
        }
        Map<Integer, ItemStack> inventory = block.getInventory();
        if (inventory != null) {
            inventory.values().stream().filter(Objects::nonNull).forEach(item -> entries.add(
                    new Entry(item.getType().getKey().getKey().toLowerCase(Locale.ENGLISH), size, item.getAmount(), true)));
        }
    }

    /**
     * Adds everything to a survey of land, as if it were blocks in the box.
     * @param survey the survey
     */
    public void addTo(LandSurvey survey) {
        entries.forEach(e -> survey.add(Kind.BLOCK, e.name(), e.size(), e.amount()));
    }

    /**
     * @return how many of each thing there is, by name
     */
    public Map<String, Integer> totals() {
        Map<String, Integer> totals = new TreeMap<>();
        entries.forEach(e -> totals.merge(e.name(), e.amount(), Integer::sum));
        return totals;
    }

    /**
     * @return how many of each thing is in the blueprint's chests, by name
     */
    public Map<String, Integer> chest() {
        Map<String, Integer> totals = new TreeMap<>();
        entries.stream().filter(Entry::inChest).forEach(e -> totals.merge(e.name(), e.amount(), Integer::sum));
        return totals;
    }

    /**
     * @return true if the blueprint gives wood: a tree, or a sapling to grow one
     */
    public boolean hasWood() {
        return entries.stream().anyMatch(e -> e.name().endsWith("_log") || e.name().endsWith("_stem")
                || e.name().endsWith("_sapling") || e.name().equals("mangrove_propagule"));
    }

    /**
     * @return true if the blueprint gives nothing
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
