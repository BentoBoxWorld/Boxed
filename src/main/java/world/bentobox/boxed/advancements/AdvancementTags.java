package world.bentobox.boxed.advancements;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * Boxed's own lists of items, blocks and entities, such as "every kind of boot". In a data
 * pack these would be tags, but a tag can only come from a data pack. So where an
 * advancement names one, written {@code #bentobox:boots}, the list is written out in full
 * before the advancement is given to the server. A list can name other lists, and the
 * game's own tags, which are looked up on the server so that they are always up to date.
 *
 * @author tastybento
 */
final class AdvancementTags {

    private static final String OWN = "#bentobox:";
    /**
     * The places in an advancement where a list can be named, and the kind of list each takes
     */
    private static final Map<String, String> FIELDS = Map.of("items", "item", "blocks", "block",
            "minecraft:entity_type", "entity_type");

    private final Map<String, List<String>> lists = new HashMap<>();
    private final BiFunction<String, String, Set<String>> gameTags;
    private final Consumer<String> warn;

    /**
     * @param gameTags looks up one of the game's tags: given the kind (item, block or
     *                 entity_type) and the tag's id, returns the ids in it, or null if there
     *                 is no such tag
     * @param warn told about anything that cannot be worked out
     */
    AdvancementTags(BiFunction<String, String, Set<String>> gameTags, Consumer<String> warn) {
        this.gameTags = gameTags;
        this.warn = warn;
    }

    /**
     * Adds a list from a file.
     * @param kind item, block or entity_type
     * @param name name of the list, for example boots
     * @param json the file: an object with an array called values
     */
    void add(String kind, String name, String json) {
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            List<String> values = object.getAsJsonArray("values").asList().stream()
                    .filter(JsonElement::isJsonPrimitive).map(JsonElement::getAsString).toList();
            lists.put(kind + "/" + name, values);
        } catch (JsonSyntaxException | IllegalStateException | NullPointerException e) {
            warn.accept("The list " + kind + "/" + name + " cannot be read: " + e.getMessage());
        }
    }

    /**
     * Works out everything in a list.
     * @param kind item, block or entity_type
     * @param name name of the list
     * @return the ids in the list, in order. Empty if there is no such list.
     */
    Set<String> expand(String kind, String name) {
        Set<String> ids = new LinkedHashSet<>();
        expand(kind, name, ids, new LinkedHashSet<>());
        return ids;
    }

    private void expand(String kind, String name, Set<String> ids, Set<String> seen) {
        List<String> values = lists.get(kind + "/" + name);
        if (values == null) {
            warn.accept("There is no list called " + kind + "/" + name);
            return;
        }
        if (!seen.add(name)) {
            // A list that names itself, perhaps by way of another
            return;
        }
        for (String value : values) {
            if (value.startsWith(OWN)) {
                expand(kind, value.substring(OWN.length()), ids, seen);
            } else if (value.startsWith("#")) {
                Set<String> found = gameTags.apply(kind, value.substring(1));
                if (found == null) {
                    warn.accept("The game has no " + kind + " tag called " + value.substring(1));
                } else {
                    ids.addAll(found);
                }
            } else {
                ids.add(value);
            }
        }
    }

    /**
     * Writes out in full every list that an advancement names.
     * @param json an advancement
     * @return the advancement with no lists of Boxed's left in it
     * @throws JsonSyntaxException if the advancement is not JSON
     */
    String apply(String json) {
        if (!json.contains(OWN)) {
            return json;
        }
        JsonElement root = JsonParser.parseString(json);
        apply(root);
        return root.toString();
    }

    private void apply(JsonElement element) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(this::apply);
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (String key : List.copyOf(object.keySet())) {
                JsonElement value = object.get(key);
                String kind = FIELDS.get(key);
                if (kind != null && value.isJsonPrimitive() && value.getAsString().startsWith(OWN)) {
                    JsonArray ids = new JsonArray();
                    expand(kind, value.getAsString().substring(OWN.length())).forEach(ids::add);
                    object.add(key, ids);
                } else {
                    apply(value);
                }
            }
        }
    }
}
