package world.bentobox.boxed.reach;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.bentobox.blueprints.Blueprint;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.areas.Area;
import world.bentobox.boxed.areas.BoxHeight;
import world.bentobox.boxed.generators.template.Template;

/**
 * Works out whether some land can be played to the end: what a box holds at each size, and
 * how far a box grows when every advancement in reach is earned. Everything the check needs
 * from the server is collected first, on the main thread, so the check itself can run off it.
 *
 * @author tastybento
 */
public class AreaCheck {

    private static final String REQUIREMENTS_FILE = "requirements.yml";

    /**
     * What a check found.
     * @param survey what the land holds at each box size
     * @param result how the box grew
     * @param summary the result in a sentence
     */
    public record Outcome(LandSurvey survey, GrowthSimulator.Result result, String summary) {
    }

    private final Template normal;
    private final Template nether;
    private final Requirements requirements;
    private final YamlConfiguration structures;
    private final Map<String, Integer> scores;
    private final Map<Vector, String> starter;
    private final int startSize;

    /**
     * Collects what a check needs. Call on the main thread.
     * @param addon Boxed
     * @param normal the overworld land
     * @param nether the Nether land, or null if there is none
     * @param structuresFile name of the file that lists the structures placed in each new box,
     *                       or null if there are none
     * @param blueprintArea the area whose starter blueprint new boxes will be given
     */
    public AreaCheck(Boxed addon, Template normal, @Nullable Template nether, @Nullable String structuresFile,
            Area blueprintArea) {
        this(addon, normal, nether, structuresFile == null ? new YamlConfiguration()
                : YamlConfiguration.loadConfiguration(new File(addon.getDataFolder(), structuresFile)), blueprintArea);
    }

    /**
     * Collects what a check needs. Call on the main thread.
     * @param addon Boxed
     * @param normal the overworld land
     * @param nether the Nether land, or null if there is none
     * @param structures the structures placed in each new box, as a structures file has them
     * @param blueprintArea the area whose starter blueprint new boxes will be given
     */
    public AreaCheck(Boxed addon, Template normal, @Nullable Template nether, YamlConfiguration structures,
            Area blueprintArea) {
        this.normal = normal;
        this.nether = nether;
        File folder = addon.getDataFolder();
        if (!new File(folder, REQUIREMENTS_FILE).exists()) {
            addon.saveResource(REQUIREMENTS_FILE, false);
        }
        this.requirements = new Requirements(YamlConfiguration.loadConfiguration(new File(folder, REQUIREMENTS_FILE)),
                addon::logWarning);
        this.structures = structures;
        this.scores = getScores(folder);
        this.starter = getStarterBlocks(addon, blueprintArea);
        this.startSize = Math.max(1, addon.getSettings().getIslandProtectionRange());
    }

    /**
     * Runs the check. Can be called from any thread.
     * @return what was found
     */
    public Outcome run() {
        LandSurvey survey = new LandSurvey(normal.getSize() * 16);
        survey.addTemplate(normal, false);
        if (nether != null) {
            survey.addTemplate(nether, true);
        }
        survey.addBlueprint(starter);
        survey.addStructures(structures.getConfigurationSection("normal"), false);
        survey.addStructures(structures.getConfigurationSection("nether"), true);
        requirements.applyTo(survey);
        GrowthSimulator.Result result = new GrowthSimulator(survey, requirements, scores, startSize).run();
        return new Outcome(survey, result, ReachReport.summary(result));
    }

    /**
     * @return how much each advancement grows the box, from advancements.yml
     */
    private static Map<String, Integer> getScores(File folder) {
        Map<String, Integer> scores = new HashMap<>();
        ConfigurationSection adv = YamlConfiguration.loadConfiguration(new File(folder, "advancements.yml"))
                .getConfigurationSection("advancements");
        if (adv != null) {
            adv.getKeys(false).forEach(k -> scores.put(k.contains(":") ? k : "minecraft:" + k, adv.getInt(k)));
        }
        return scores;
    }

    /**
     * @return the starter blocks of an area, by position relative to the centre of the box
     */
    private static Map<Vector, String> getStarterBlocks(Boxed addon, Area area) {
        Map<Vector, String> starter = new HashMap<>();
        Blueprint bp = new BoxHeight(addon).getBlueprint(area);
        if (bp != null) {
            Vector bedrock = bp.getBedrock() == null ? new Vector() : bp.getBedrock();
            Stream.of(bp.getBlocks(), bp.getAttached()).filter(Objects::nonNull).forEach(
                    m -> m.forEach((pos, block) -> starter.put(pos.clone().subtract(bedrock), block.getBlockData())));
        }
        return starter;
    }
}
