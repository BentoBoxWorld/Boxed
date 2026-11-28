package world.bentobox.boxed.areas;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.sign.Side;
import org.bukkit.util.Vector;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.blueprints.Blueprint;
import world.bentobox.bentobox.blueprints.dataobjects.BlueprintBlock;
import world.bentobox.bentobox.blueprints.dataobjects.BlueprintBundle;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.reach.StarterBounty;
import world.bentobox.boxed.generators.template.Template;
import world.bentobox.boxed.generators.template.TemplateChunk;

/**
 * Works out the height of the centre of a new box. BentoBox pastes the starter blueprint with
 * its bedrock block at the centre of the box, so the centre decides how high the starter
 * blocks and the player's home are. The ground is at a different height in every area, so the
 * centre is set to put the blueprint's {@code [spawn_here]} sign on the area's ground.
 *
 * @author tastybento
 */
public class BoxHeight {

    private final Boxed addon;

    /**
     * @param addon Boxed
     */
    public BoxHeight(Boxed addon) {
        this.addon = addon;
    }

    /**
     * @param area an area
     * @return y of the centre of a new box in the area. This is the island height setting if
     *         the blueprint has no spawn sign or the area's land cannot be read.
     */
    public int get(Area area) {
        int fallback = addon.getSettings().getIslandHeight();
        Blueprint bp = getBlueprint(area);
        Vector sign = bp == null ? null : getSpawnSign(bp);
        Template template = addon.getAreas().getTemplate(area, Environment.NORMAL);
        if (sign == null || template == null) {
            return fallback;
        }
        Vector bedrock = bp.getBedrock() == null ? new Vector(0, 0, 0) : bp.getBedrock();
        // Where the sign goes, relative to the centre of the box
        int x = sign.getBlockX() - bedrock.getBlockX();
        int z = sign.getBlockZ() - bedrock.getBlockZ();
        TemplateChunk chunk = template.getChunk(x >> 4, z >> 4);
        Integer ground = chunk == null ? null : chunk.getGroundLevel(x & 15, z & 15);
        if (ground == null) {
            return fallback;
        }
        World world = addon.getOverWorld();
        int min = world == null ? Integer.MIN_VALUE : world.getMinHeight();
        return Math.max(min, height(ground, sign.getBlockY() - bedrock.getBlockY()));
    }

    /**
     * @param ground y of the ground block the player will stand on
     * @param signAboveBedrock how far the spawn sign is above the blueprint's bedrock
     * @return y of the centre of the box
     */
    static int height(int ground, int signAboveBedrock) {
        return ground + 1 - signAboveBedrock;
    }

    /**
     * Gets the overworld blueprint of an area: the one in the bundle named after the area, or
     * in the first bundle if the area has no bundle of its own.
     */
    @Nullable
    public Blueprint getBlueprint(Area area) {
        Map<String, BlueprintBundle> bundles = addon.getPlugin().getBlueprintsManager().getBlueprintBundles(addon);
        BlueprintBundle own = bundles.get(area.id());
        String name = Stream.concat(Stream.ofNullable(own),
                bundles.values().stream().sorted(Comparator.comparingInt(BlueprintBundle::getSlot)))
                .map(b -> b.getBlueprint(Environment.NORMAL)).filter(n -> n != null && !n.isBlank()).findFirst()
                .orElse(null);
        return name == null ? null : addon.getPlugin().getBlueprintsManager().getBlueprints(addon).get(name);
    }

    /**
     * Works out what the starter blueprint of an area gives a new box.
     * @param area an area
     * @param reach how far above and below the spawn sign blocks are counted:
     *              {@link StarterBounty#NEAR_SIGN} or {@link StarterBounty#WHOLE}
     * @return the blocks of the blueprint and the items in its chests
     */
    public StarterBounty getBounty(Area area, int reach) {
        Blueprint bp = getBlueprint(area);
        return StarterBounty.of(bp, bp == null ? null : getSpawnSign(bp), reach);
    }

    /**
     * @param bp blueprint
     * @return position of the blueprint's spawn sign, or null if it has none
     */
    @Nullable
    public static Vector getSpawnSign(Blueprint bp) {
        return Stream.of(bp.getAttached(), bp.getBlocks()).filter(m -> m != null).flatMap(m -> m.entrySet().stream())
                .filter(en -> isSpawnSign(en.getValue())).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    private static boolean isSpawnSign(BlueprintBlock block) {
        List<String> lines = block.getSignLines(Side.FRONT);
        return lines != null && !lines.isEmpty() && TextVariables.SPAWN_HERE.equalsIgnoreCase(lines.getFirst());
    }
}
