package world.bentobox.boxed.advancements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests the lists that stand in for a data pack's tags.
 */
class AdvancementTagsTest {

    private final List<String> warnings = new ArrayList<>();
    private AdvancementTags tags;

    @BeforeEach
    void setUp() {
        // The game has one tag: the raiders
        tags = new AdvancementTags((kind, id) -> kind.equals("entity_type") && id.equals("minecraft:raiders")
                ? new LinkedHashSet<>(List.of("minecraft:pillager", "minecraft:witch"))
                : null, warnings::add);
        tags.add("item", "boots", "{\"values\":[\"minecraft:leather_boots\",\"minecraft:iron_boots\"]}");
        tags.add("item", "armor", "{\"values\":[\"#bentobox:boots\",\"minecraft:shield\",\"minecraft:iron_boots\"]}");
        tags.add("entity_type", "hostile", "{\"values\":[\"minecraft:zombie\",\"#minecraft:raiders\"]}");
    }

    @Test
    void testAListCanNameOtherLists() {
        assertEquals(List.of("minecraft:leather_boots", "minecraft:iron_boots", "minecraft:shield"),
                List.copyOf(tags.expand("item", "armor")));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void testTheGamesOwnTagsAreLookedUp() {
        assertEquals(Set.of("minecraft:zombie", "minecraft:pillager", "minecraft:witch"),
                tags.expand("entity_type", "hostile"));
    }

    @Test
    void testUnknownListsAndTagsAreReported() {
        assertTrue(tags.expand("item", "hats").isEmpty());
        // A list is only known for its own kind
        assertTrue(tags.expand("block", "boots").isEmpty());
        tags.add("item", "odd", "{\"values\":[\"#minecraft:nothing\",\"minecraft:stick\"]}");
        assertEquals(Set.of("minecraft:stick"), tags.expand("item", "odd"));
        assertEquals(3, warnings.size());
    }

    @Test
    void testListsThatNameEachOtherDoNotLoop() {
        tags.add("item", "a", "{\"values\":[\"minecraft:stick\",\"#bentobox:b\"]}");
        tags.add("item", "b", "{\"values\":[\"#bentobox:a\",\"minecraft:stone\"]}");
        assertEquals(Set.of("minecraft:stick", "minecraft:stone"), tags.expand("item", "a"));
    }

    @Test
    void testAFileThatIsNotAListIsReported() {
        tags.add("item", "broken", "not json");
        tags.add("item", "empty", "{}");
        assertEquals(2, warnings.size());
    }

    @Test
    void testListsAreWrittenOutInAnAdvancement() {
        String json = """
                {"criteria":{"a":{"trigger":"minecraft:inventory_changed","conditions":{"items":[
                  {"items":"#bentobox:boots"},{"items":"#minecraft:planks"},{"items":"minecraft:stick"}]}},
                 "b":{"trigger":"minecraft:player_killed_entity","conditions":{"entity":{
                  "type":"minecraft:entity_properties","predicate":{"minecraft:entity_type":"#bentobox:hostile"}}}}},
                 "display":{"description":"#bentobox:boots"}}""";
        JsonObject out = JsonParser.parseString(tags.apply(json)).getAsJsonObject();
        var items = out.getAsJsonObject("criteria").getAsJsonObject("a").getAsJsonObject("conditions")
                .getAsJsonArray("items");
        assertEquals("[\"minecraft:leather_boots\",\"minecraft:iron_boots\"]",
                items.get(0).getAsJsonObject().get("items").toString());
        // The game's own tags are left to the game
        assertEquals("#minecraft:planks", items.get(1).getAsJsonObject().get("items").getAsString());
        assertEquals("minecraft:stick", items.get(2).getAsJsonObject().get("items").getAsString());
        assertEquals(3, out.getAsJsonObject("criteria").getAsJsonObject("b").getAsJsonObject("conditions")
                .getAsJsonObject("entity").getAsJsonObject("predicate").getAsJsonArray("minecraft:entity_type").size());
        // Only where a list can go
        assertEquals("#bentobox:boots", out.getAsJsonObject("display").get("description").getAsString());
        assertFalse(out.toString().contains("\"items\":\"#bentobox"));
    }

    @Test
    void testAnAdvancementWithNoListsIsNotTouched() {
        String json = "{ \"criteria\": {} }";
        assertSame(json, tags.apply(json));
    }
}
