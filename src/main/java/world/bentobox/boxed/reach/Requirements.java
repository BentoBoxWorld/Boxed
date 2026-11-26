package world.bentobox.boxed.reach;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.configuration.ConfigurationSection;

import world.bentobox.boxed.reach.LandSurvey.Kind;

/**
 * What a player needs to have in their box to earn each advancement, read from
 * requirements.yml. This is a model written by hand: it says which raw resources an
 * advancement comes down to, not every step of crafting.
 *
 * @author tastybento
 */
public class Requirements {

    /**
     * Advancement that opens the nether if the file does not name one
     */
    public static final String DEFAULT_NETHER = "minecraft:story/enter_the_nether";

    /**
     * What an alternative asks for
     */
    public enum Type {
        /** Something in the land */
        RESOURCE,
        /** Another advancement earned first */
        AFTER,
        /** A box of at least a certain size */
        SIZE,
        /** Cannot be done in Boxed */
        NEVER
    }

    /**
     * One way of meeting a need.
     * @param type what is asked for
     * @param kind kind of resource, for a resource
     * @param names resource names, the advancement to earn first, or the reason it can never be done
     * @param amount how many of the resource, or the box size
     */
    public record Alternative(Type type, Kind kind, String names, int amount) {
    }

    /**
     * Something an advancement needs. It is met when any one of its alternatives is.
     * @param text the need as written in the file
     * @param alternatives ways of meeting it
     */
    public record Need(String text, List<Alternative> alternatives) {
    }

    /**
     * A rule that makes one thing in the land count as another as well, for example a village
     * house counting as a villager.
     * @param fromKind kind of the thing in the land
     * @param fromNames its names
     * @param toKind kind it also counts as
     * @param toName name it also counts as
     */
    public record Provides(Kind fromKind, String fromNames, Kind toKind, String toName) {
    }

    private final Map<String, List<Need>> needs = new LinkedHashMap<>();
    private final List<Provides> provides = new ArrayList<>();
    private final List<String> always = new ArrayList<>();
    private final String netherAdvancement;

    /**
     * @param config contents of requirements.yml
     * @param errorLog where to report lines that cannot be understood
     */
    public Requirements(ConfigurationSection config, Consumer<String> errorLog) {
        netherAdvancement = key(config.getString("nether-advancement", DEFAULT_NETHER));
        always.addAll(config.getStringList("always"));
        for (String rule : config.getStringList("provides")) {
            String[] sides = rule.split("->");
            Alternative from = sides.length == 2 ? alternative(sides[0], errorLog) : null;
            Alternative to = sides.length == 2 ? alternative(sides[1], errorLog) : null;
            if (from == null || to == null || from.type() != Type.RESOURCE || to.type() != Type.RESOURCE) {
                errorLog.accept("requirements.yml: cannot understand the provides rule '" + rule + "'");
            } else {
                provides.add(new Provides(from.kind(), from.names(), to.kind(), to.names()));
            }
        }
        ConfigurationSection sets = config.getConfigurationSection("sets");
        ConfigurationSection advancements = config.getConfigurationSection("advancements");
        if (advancements == null) {
            return;
        }
        for (String name : advancements.getKeys(false)) {
            List<Need> list = new ArrayList<>();
            for (String line : advancements.getStringList(name)) {
                addNeeds(line, sets, list, errorLog);
            }
            needs.put(key(name), list);
        }
    }

    private static String key(String name) {
        return name.contains(":") ? name : "minecraft:" + name;
    }

    private void addNeeds(String line, ConfigurationSection sets, List<Need> list, Consumer<String> errorLog) {
        String trimmed = line.trim();
        if (trimmed.startsWith("set:")) {
            String set = trimmed.substring(4).trim();
            if (sets == null || !sets.isList(set)) {
                errorLog.accept("requirements.yml: there is no set called '" + set + "'");
                return;
            }
            // Sets cannot hold other sets, which keeps them from going round in circles
            sets.getStringList(set).forEach(l -> addNeed(l, list, errorLog));
        } else {
            addNeed(trimmed, list, errorLog);
        }
    }

    private void addNeed(String line, List<Need> list, Consumer<String> errorLog) {
        List<Alternative> alternatives = new ArrayList<>();
        for (String part : line.split("\\|\\|")) {
            Alternative a = alternative(part, errorLog);
            if (a != null) {
                alternatives.add(a);
            }
        }
        if (!alternatives.isEmpty()) {
            list.add(new Need(line.trim(), alternatives));
        }
    }

    /**
     * Reads one alternative, such as "block:iron_ore|deepslate_iron_ore*3" or "after:story/smelt_iron".
     */
    static Alternative alternative(String text, Consumer<String> errorLog) {
        String[] parts = text.trim().split(":", 2);
        if (parts.length != 2 || parts[1].isBlank()) {
            errorLog.accept("requirements.yml: cannot understand '" + text.trim() + "'");
            return null;
        }
        String type = parts[0].trim().toLowerCase(Locale.ENGLISH);
        String value = parts[1].trim();
        try {
            switch (type) {
            case "after":
                return new Alternative(Type.AFTER, null, key(value), 0);
            case "size":
                return new Alternative(Type.SIZE, null, "", Integer.parseInt(value));
            case "never":
                return new Alternative(Type.NEVER, null, value, 0);
            default:
                break;
            }
            int amount = 1;
            int star = value.lastIndexOf('*');
            if (star > 0 && value.substring(star + 1).trim().matches("\\d+")) {
                amount = Integer.parseInt(value.substring(star + 1).trim());
                value = value.substring(0, star).trim();
            }
            for (Kind kind : Kind.values()) {
                if (kind.label().equals(type)) {
                    return new Alternative(Type.RESOURCE, kind, value, amount);
                }
            }
        } catch (NumberFormatException _) {
            // Reported below
        }
        errorLog.accept("requirements.yml: cannot understand '" + text.trim() + "'");
        return null;
    }

    /**
     * @param advancement advancement key
     * @return what it needs, or null if the file does not list it
     */
    public List<Need> getNeeds(String advancement) {
        return needs.get(advancement);
    }

    /**
     * @return rules that make things in the land count as other things
     */
    public List<Provides> getProvides() {
        return provides;
    }

    /**
     * @return mobs that turn up in any box, however small
     */
    public List<String> getAlways() {
        return always;
    }

    /**
     * @return the advancement that has to be earned before anything in the nether can be used
     */
    public String getNetherAdvancement() {
        return netherAdvancement;
    }

    /**
     * Adds what the rules say to a survey: the mobs that are always around, and the things
     * that count as other things.
     * @param survey survey of an area's land
     */
    public void applyTo(LandSurvey survey) {
        always.forEach(mob -> survey.add(Kind.ENTITY, mob, 1, 1));
        for (Provides rule : provides) {
            int size = survey.sizeNeeded(rule.fromKind(), rule.fromNames(), 1);
            if (size > 0) {
                survey.add(rule.toKind(), rule.toName(), size, 1);
            }
        }
    }
}
