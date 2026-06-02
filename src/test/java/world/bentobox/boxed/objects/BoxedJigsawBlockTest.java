package world.bentobox.boxed.objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

/**
 * Tests {@link BoxedJigsawBlock} GSON (de)serialization.
 *
 * <p>The {@code finalState} field is mapped to the Minecraft jigsaw JSON key
 * {@code final_state} via {@link com.google.gson.annotations.SerializedName}.
 * These tests guard that mapping so the field can keep a Java-friendly name
 * without breaking the structure data that {@code NewAreaListener} reads with a
 * plain {@code new Gson()}.</p>
 */
class BoxedJigsawBlockTest {

    // Production (NewAreaListener) deserialises with a plain Gson instance.
    private final Gson gson = new Gson();

    private static final String JSON = "{\"final_state\":\"minecraft:polished_blackstone_bricks\","
            + "\"joint\":\"aligned\",\"name\":\"minecraft:empty\","
            + "\"pool\":\"minecraft:bastion/bridge/legs\",\"target\":\"minecraft:leg_connector\"}";

    /**
     * The key in the source data is {@code final_state}; it must still populate the
     * renamed {@code finalState} field.
     */
    @Test
    void testDeserialiseFinalStateKey() {
        BoxedJigsawBlock bjb = gson.fromJson(JSON, BoxedJigsawBlock.class);
        assertEquals("minecraft:polished_blackstone_bricks", bjb.getFinalState());
        assertEquals("aligned", bjb.getJoint());
        assertEquals("minecraft:empty", bjb.getName());
        assertEquals("minecraft:bastion/bridge/legs", bjb.getPool());
        assertEquals("minecraft:leg_connector", bjb.getTarget());
    }

    /**
     * Serialisation must emit the {@code final_state} key, not {@code finalState}.
     */
    @Test
    void testSerialiseUsesFinalStateKey() {
        BoxedJigsawBlock bjb = gson.fromJson(JSON, BoxedJigsawBlock.class);
        String json = gson.toJson(bjb);
        assertTrue(json.contains("\"final_state\""), "Expected final_state key in: " + json);
        assertTrue(json.contains("minecraft:polished_blackstone_bricks"));
    }

    @Test
    void testToStringUsesFinalState() {
        BoxedJigsawBlock bjb = gson.fromJson(JSON, BoxedJigsawBlock.class);
        assertTrue(bjb.toString().contains("finalState=minecraft:polished_blackstone_bricks"));
    }
}
