package world.bentobox.boxed.reach;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import world.bentobox.boxed.reach.LandSurvey.Kind;
import world.bentobox.boxed.reach.Requirements.Alternative;
import world.bentobox.boxed.reach.Requirements.Need;
import world.bentobox.boxed.reach.Requirements.Provides;
import world.bentobox.boxed.reach.Requirements.Type;

/**
 * Which biomes a box has, and which biomes it lacks that the advancements want. It is made
 * for the admin who is deciding what biomes to add to an area.
 *
 * @param present the biomes in the box and the box size at which each is first reached, nearest first
 * @param missing the biomes that are wanted and not there
 * @author tastybento
 */
public record BiomeReport(Map<String, Integer> present, List<Missing> missing) {

    /**
     * Biomes that are wanted for the same things and are not in the box.
     * @param biomes the biomes. "a or b" means that either will do.
     * @param wantedFor the mobs they bring or the advancements that ask for them
     */
    public record Missing(List<String> biomes, List<String> wantedFor) {
    }

    /**
     * @param survey a survey that has the biomes of the land
     * @param requirements what the advancements need
     * @param nether true to report on the Nether, false for the overworld
     * @return the report
     */
    public static BiomeReport of(LandSurvey survey, Requirements requirements, boolean nether) {
        Kind kind = nether ? Kind.NETHER_BIOME : Kind.BIOME;
        Map<String, Integer> present = survey.firstSizes(kind).entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

        // What is wanted and not there, and the reasons it is wanted
        Map<String, List<String>> wanted = new LinkedHashMap<>();
        for (Provides rule : requirements.getProvides()) {
            if (rule.fromKind() == kind && survey.sizeNeeded(kind, rule.fromNames(), 1) < 0) {
                add(wanted, rule.fromNames(), rule.toName());
            }
        }
        for (String advancement : requirements.getAdvancements()) {
            for (Need need : requirements.getNeeds(advancement)) {
                String names = unmetBiomes(need, kind, survey);
                if (names != null) {
                    add(wanted, names, advancement.replace("minecraft:", ""));
                }
            }
        }
        // Biomes wanted for exactly the same reasons are listed together
        Map<List<String>, List<String>> byReason = new LinkedHashMap<>();
        wanted.forEach((names, reasons) -> byReason.computeIfAbsent(reasons, k -> new ArrayList<>())
                .add(names.replace("|", " or ")));
        List<Missing> missing = new ArrayList<>();
        byReason.forEach((reasons, names) -> missing.add(new Missing(names, reasons)));
        missing.sort(Comparator.comparingInt(m -> m.biomes().size()));
        return new BiomeReport(present, missing);
    }

    private static void add(Map<String, List<String>> wanted, String names, String reason) {
        List<String> reasons = wanted.computeIfAbsent(names, k -> new ArrayList<>());
        if (!reasons.contains(reason)) {
            reasons.add(reason);
        }
    }

    /**
     * @return the biomes a need asks for, if it can only be met by a biome and the land has
     *         none of them. Otherwise null.
     */
    private static String unmetBiomes(Need need, Kind kind, LandSurvey survey) {
        List<String> names = new ArrayList<>();
        for (Alternative a : need.alternatives()) {
            if (a.type() != Type.RESOURCE || a.kind() != kind || survey.sizeNeeded(kind, a.names(), a.amount()) > 0) {
                return null;
            }
            names.add(a.names());
        }
        return names.isEmpty() ? null : String.join("|", names);
    }
}
