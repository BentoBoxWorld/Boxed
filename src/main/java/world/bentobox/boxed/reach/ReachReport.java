package world.bentobox.boxed.reach;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import world.bentobox.boxed.reach.GrowthSimulator.Blocked;
import world.bentobox.boxed.reach.GrowthSimulator.Earned;
import world.bentobox.boxed.reach.GrowthSimulator.Result;
import world.bentobox.boxed.reach.LandSurvey.Kind;

/**
 * Writes up a growth simulation as plain text for an admin to read.
 *
 * @author tastybento
 */
public final class ReachReport {

    private ReachReport() {
    }

    /**
     * @param area name of the area
     * @param survey the land that was surveyed
     * @param result how the growth went
     * @return the report, one line per entry
     */
    public static List<String> lines(String area, LandSurvey survey, Result result) {
        List<String> out = new ArrayList<>();
        out.add("Reachability of area " + area);
        out.add("");
        out.add(summary(result));
        out.add("");
        out.add("== Growth, round by round ==");
        int round = 0;
        for (Earned e : result.earned()) {
            if (e.round() != round) {
                round = e.round();
                out.add("Round " + round + ", box size " + e.sizeBefore() + ":");
            }
            out.add("  +" + e.score() + "  " + e.advancement());
        }
        List<Blocked> later = result.blocked().stream().filter(b -> !b.impossible())
                .sorted(Comparator.comparing(Blocked::advancement)).toList();
        List<Blocked> never = result.blocked().stream().filter(Blocked::impossible)
                .sorted(Comparator.comparing(Blocked::advancement)).toList();
        addBlocked(out, "== Not earned: out of reach when the box stopped growing ==", later);
        addBlocked(out, "== Not earned: impossible in this area ==", never);
        if (!result.unlisted().isEmpty()) {
            out.add("");
            out.add("== Scored advancements that requirements.yml does not list ==");
            result.unlisted().forEach(a -> out.add("  " + a));
        }
        for (Kind kind : Kind.values()) {
            // Blocks would run to hundreds of lines and are better asked about by name
            if (kind != Kind.BLOCK && kind != Kind.NETHER_BLOCK) {
                addFirstSizes(out, kind, survey);
            }
        }
        return out;
    }

    /**
     * @param result how the growth went
     * @return one line that says how far the box got
     */
    public static String summary(Result result) {
        int earnedScore = result.earned().stream().mapToInt(Earned::score).sum();
        long impossible = result.blocked().stream().filter(Blocked::impossible).count();
        int impossibleScore = result.blocked().stream().filter(Blocked::impossible).mapToInt(Blocked::score).sum();
        long stuck = result.blocked().size() - impossible;
        return (result.filledLand() ? "The box grows to fill its land (size " + result.maxSize() + ")."
                : "The box stops growing at size " + result.finalSize() + " of " + result.maxSize() + ".") + " "
                + result.earned().size() + " advancements earned for " + earnedScore + " points, " + stuck
                + " out of reach, " + impossible + " impossible (" + impossibleScore + " points).";
    }

    private static void addBlocked(List<String> out, String title, List<Blocked> blocked) {
        if (blocked.isEmpty()) {
            return;
        }
        out.add("");
        out.add(title);
        for (Blocked b : blocked) {
            out.add("  +" + b.score() + "  " + b.advancement());
            b.missing().forEach(m -> out.add("        " + m));
        }
    }

    private static void addFirstSizes(List<String> out, Kind kind, LandSurvey survey) {
        Map<String, Integer> sizes = survey.firstSizes(kind);
        if (sizes.isEmpty()) {
            return;
        }
        out.add("");
        out.add("== " + kind.label() + ": box size at which each first appears ==");
        sizes.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry::getKey))
                .forEach(en -> out.add(String.format("  %4d  %s", en.getValue(), en.getKey())));
    }
}
