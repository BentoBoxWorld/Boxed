package world.bentobox.boxed.reach;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import world.bentobox.boxed.reach.Requirements.Alternative;
import world.bentobox.boxed.reach.Requirements.Need;

/**
 * Plays out how a box grows in an area. Starting from the smallest box, every advancement
 * whose needs are inside the box is earned and the box grows by its score, over and over until
 * nothing more can be earned. This shows where a player gets stuck and which advancements can
 * never be earned in the area.
 *
 * @author tastybento
 */
public class GrowthSimulator {

    /**
     * An advancement that was earned.
     * @param advancement advancement key
     * @param score how much it grew the box
     * @param round the round it was earned in, starting at 1
     * @param sizeBefore box size when it was earned
     */
    public record Earned(String advancement, int score, int round, int sizeBefore) {
    }

    /**
     * An advancement that could not be earned.
     * @param advancement advancement key
     * @param score how much it would have grown the box
     * @param missing why not, one line per need that was not met
     * @param impossible true if no box size would be enough
     */
    public record Blocked(String advancement, int score, List<String> missing, boolean impossible) {
    }

    /**
     * How the growth went.
     * @param earned advancements in the order they were earned
     * @param blocked advancements that were not earned
     * @param unlisted scored advancements the requirements file does not list
     * @param finalSize the size the box reached, which is never more than the land allows
     * @param maxSize the largest size the land allows
     * @param sizeByRound box size at the end of each round
     */
    public record Result(List<Earned> earned, List<Blocked> blocked, List<String> unlisted, int finalSize, int maxSize,
            List<Integer> sizeByRound) {

        /**
         * @return true if the box grew to fill its land
         */
        public boolean filledLand() {
            return finalSize >= maxSize;
        }
    }

    private final LandSurvey survey;
    private final Requirements requirements;
    private final Map<String, Integer> scores;
    private final int startSize;

    /**
     * @param survey the land, with the requirements' rules already applied to it
     * @param requirements what each advancement needs
     * @param scores how much each advancement grows the box. Ones that score nothing are ignored.
     * @param startSize size of a new box
     */
    public GrowthSimulator(LandSurvey survey, Requirements requirements, Map<String, Integer> scores, int startSize) {
        this.survey = survey;
        this.requirements = requirements;
        this.scores = new TreeMap<>(scores);
        this.startSize = startSize;
    }

    /**
     * Plays out the growth of a box.
     * @return how it went
     */
    public Result run() {
        int max = survey.getRadius();
        int size = Math.min(startSize, max);
        Set<String> earned = new LinkedHashSet<>();
        List<Earned> order = new ArrayList<>();
        List<String> unlisted = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        Set<String> candidates = new LinkedHashSet<>();
        scores.forEach((advancement, score) -> {
            if (score > 0 && requirements.getNeeds(advancement) == null) {
                unlisted.add(advancement);
            } else if (score > 0) {
                candidates.add(advancement);
            }
        });
        boolean progress = true;
        for (int round = 1; progress; round++) {
            progress = false;
            int sizeNow = size;
            Set<String> before = Set.copyOf(earned);
            for (String advancement : List.copyOf(candidates)) {
                if (requirements.getNeeds(advancement).stream().allMatch(n -> isMet(n, sizeNow, before))) {
                    candidates.remove(advancement);
                    earned.add(advancement);
                    order.add(new Earned(advancement, scores.get(advancement), round, sizeNow));
                    size = Math.min(max, size + scores.get(advancement));
                    progress = true;
                }
            }
            if (progress) {
                sizes.add(size);
            }
        }
        int finalSize = size;
        List<Blocked> blocked = new ArrayList<>(candidates.stream().map(a -> blocked(a, finalSize, earned)).toList());
        markImpossibleChains(blocked);
        return new Result(order, blocked, unlisted, finalSize, max, sizes);
    }

    /**
     * An advancement is also impossible if every way of meeting one of its needs is another
     * advancement that is impossible. This is repeated until nothing changes, to follow chains.
     */
    private void markImpossibleChains(List<Blocked> blocked) {
        boolean changed = true;
        while (changed) {
            changed = false;
            Set<String> impossible = new LinkedHashSet<>();
            blocked.stream().filter(Blocked::impossible).forEach(b -> impossible.add(b.advancement()));
            for (int i = 0; i < blocked.size(); i++) {
                Blocked b = blocked.get(i);
                if (!b.impossible() && requirements.getNeeds(b.advancement()).stream()
                        .anyMatch(n -> n.alternatives().stream().allMatch(a -> a.type() == Requirements.Type.NEVER
                                || (a.type() == Requirements.Type.AFTER && impossible.contains(a.names()))))) {
                    blocked.set(i, new Blocked(b.advancement(), b.score(), b.missing(), true));
                    changed = true;
                }
            }
        }
    }

    private boolean isMet(Need need, int size, Set<String> earned) {
        return need.alternatives().stream().anyMatch(a -> isMet(a, size, earned));
    }

    private boolean isMet(Alternative a, int size, Set<String> earned) {
        return switch (a.type()) {
        case NEVER -> false;
        case SIZE -> size >= a.amount();
        case AFTER -> earned.contains(a.names());
        case RESOURCE -> (!a.kind().isNether() || earned.contains(requirements.getNetherAdvancement()))
                && survey.count(a.kind(), a.names(), size) >= a.amount();
        };
    }

    private Blocked blocked(String advancement, int size, Set<String> earned) {
        List<String> missing = new ArrayList<>();
        boolean impossible = false;
        for (Need need : requirements.getNeeds(advancement)) {
            if (isMet(need, size, earned)) {
                continue;
            }
            // The need could still be met later if any of its alternatives could be
            boolean possible = false;
            List<String> reasons = new ArrayList<>();
            for (Alternative a : need.alternatives()) {
                String reason = explain(a, size, earned);
                possible |= !reason.startsWith("!");
                reasons.add(reason.startsWith("!") ? reason.substring(1) : reason);
            }
            impossible |= !possible;
            missing.add(String.join(" or ", reasons));
        }
        return new Blocked(advancement, scores.get(advancement), missing, impossible);
    }

    /**
     * Says why an alternative is not met. The text starts with ! if it never can be.
     */
    private String explain(Alternative a, int size, Set<String> earned) {
        switch (a.type()) {
        case NEVER:
            return "!" + a.names();
        case SIZE:
            return (a.amount() > survey.getRadius() ? "!" : "") + "needs a box of size " + a.amount();
        case AFTER:
            return "needs " + a.names() + " first";
        default:
            break;
        }
        String what = (a.amount() > 1 ? a.amount() + " x " : "") + a.kind().label() + " " + a.names();
        int needed = survey.sizeNeeded(a.kind(), a.names(), a.amount());
        if (needed < 0) {
            long there = survey.count(a.kind(), a.names(), survey.getRadius());
            return "!" + what + (there > 0 ? " (the land only has " + there + ")" : " (not in the land)");
        }
        if (needed > size) {
            return what + " (first at size " + needed + ")";
        }
        return what + " (in the box, but the nether is not open)";
    }
}
