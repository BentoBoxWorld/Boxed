package world.bentobox.boxed.advancements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests reading the advancements that come with Boxed and getting them ready for the server.
 */
class BuiltInAdvancementsTest {

    private static final Path RESOURCES = Path.of("src/main/resources", AdvancementFiles.FOLDER);

    @TempDir
    File folder;

    private static NamespacedKey key(String id) {
        return NamespacedKey.fromString(id);
    }

    @Test
    void testParentsComeFirst() {
        Map<NamespacedKey, String> found = new HashMap<>();
        found.put(key("bentobox:ocean/deep"), "{\"parent\":\"bentobox:ocean/swim\"}");
        found.put(key("bentobox:ocean/swim"), "{\"parent\":\"bentobox:ocean/root\"}");
        found.put(key("bentobox:ocean/root"), "{}");
        found.put(key("minecraft:story/all_tools"), "{\"parent\":\"minecraft:story/iron_tools\"}");
        List<String> warnings = new ArrayList<>();
        Map<NamespacedKey, String> ready = BuiltInAdvancements.prepare(found,
                new AdvancementTags((kind, id) -> null, warnings::add), warnings::add);
        assertEquals(List.of("bentobox:ocean/root", "bentobox:ocean/swim", "minecraft:story/all_tools",
                "bentobox:ocean/deep"), ready.keySet().stream().map(NamespacedKey::toString).toList());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void testOneThatCannotBeReadIsLeftOut() {
        Map<NamespacedKey, String> found = new HashMap<>();
        found.put(key("bentobox:a"), "not json");
        found.put(key("bentobox:b"), "[]");
        found.put(key("bentobox:c"), "{}");
        List<String> warnings = new ArrayList<>();
        Map<NamespacedKey, String> ready = BuiltInAdvancements.prepare(found,
                new AdvancementTags((kind, id) -> null, warnings::add), warnings::add);
        assertEquals(Set.of(key("bentobox:c")), ready.keySet());
        assertEquals(2, warnings.size());
    }

    @Test
    void testFilesInTheFolderTakeTheJarsPlace() throws IOException {
        File jar = new File(folder, "addon.jar");
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar))) {
            put(out, "advancements/bentobox/ocean/root.json", "jar root");
            put(out, "advancements/bentobox/ocean/swim.json", "jar swim");
            put(out, "advancements/tags/item/boots.json", "jar boots");
            put(out, "advancements/readme.txt", "not an advancement");
            put(out, "config.yml", "not here");
        }
        File data = new File(folder, "data");
        Path own = data.toPath().resolve("advancements/bentobox/ocean");
        Files.createDirectories(own);
        Files.writeString(own.resolve("swim.json"), "my swim");
        Files.writeString(own.resolve("dive.json"), "my dive");
        Map<String, String> files = AdvancementFiles.read(jar, data);
        assertEquals(Map.of("bentobox/ocean/root.json", "jar root", "bentobox/ocean/swim.json", "my swim",
                "bentobox/ocean/dive.json", "my dive", "tags/item/boots.json", "jar boots"), files);
        // With no folder there is only the jar
        assertEquals(3, AdvancementFiles.read(jar, new File(folder, "nothing")).size());
    }

    private static void put(JarOutputStream out, String name, String text) throws IOException {
        out.putNextEntry(new JarEntry(name));
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    /**
     * The files that ship with Boxed must fit together: each is JSON, follows on from an
     * advancement that exists, and names only lists that exist.
     */
    @Test
    void testTheAdvancementsThatComeWithBoxedFitTogether() throws IOException {
        Map<String, JsonObject> advancements = new HashMap<>();
        Set<String> lists = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(RESOURCES)) {
            for (Path path : walk.filter(p -> p.toString().endsWith(".json")).toList()) {
                String name = RESOURCES.relativize(path).toString().replace(File.separatorChar, '/');
                name = name.substring(0, name.length() - 5);
                JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if (name.startsWith("tags/")) {
                    assertTrue(json.get("values").isJsonArray(), name);
                    lists.add(name.substring(5));
                } else {
                    String[] parts = name.split("/", 2);
                    assertNotNull(NamespacedKey.fromString(parts[0] + ":" + parts[1]), name);
                    advancements.put(parts[0] + ":" + parts[1], json);
                }
            }
        }
        assertTrue(advancements.size() > 400, "Only " + advancements.size() + " advancements");
        List<String> problems = new ArrayList<>();
        advancements.forEach((id, json) -> {
            JsonElement parent = json.get("parent");
            // The game's own advancements are not in Boxed's files
            if (parent != null && !advancements.containsKey(parent.getAsString())
                    && !parent.getAsString().startsWith("minecraft:")) {
                problems.add(id + " follows on from " + parent.getAsString() + ", which does not exist");
            }
            if (!json.has("criteria") || json.getAsJsonObject("criteria").isEmpty()) {
                problems.add(id + " has no criteria");
            }
            checkLists(id, json, lists, problems);
        });
        assertTrue(problems.isEmpty(), String.join("\n", problems));
        assertFalse(lists.isEmpty());
    }

    private static void checkLists(String id, JsonElement element, Set<String> lists, List<String> problems) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(e -> checkLists(id, e, lists, problems));
        } else if (element.isJsonObject()) {
            element.getAsJsonObject().asMap().forEach((key, value) -> {
                if (value.isJsonPrimitive() && value.getAsString().startsWith("#bentobox:")) {
                    String kind = switch (key) {
                    case "items" -> "item";
                    case "blocks" -> "block";
                    case "minecraft:entity_type" -> "entity_type";
                    default -> null;
                    };
                    String list = kind + "/" + value.getAsString().substring("#bentobox:".length());
                    if (kind == null) {
                        problems.add(id + " names a list in " + key + ", where the game will not take one");
                    } else if (!lists.contains(list)) {
                        problems.add(id + " names the list " + list + ", which does not exist");
                    }
                } else {
                    checkLists(id, value, lists, problems);
                }
            });
        }
    }
}
