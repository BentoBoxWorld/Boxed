package world.bentobox.boxed.objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.block.data.type.StructureBlock.Mode;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

/**
 * Tests {@link BoxedStructureBlock} GSON deserialization (this is how the class
 * is populated from captured structure-block data) and its {@code toString()}.
 */
class BoxedStructureBlockTest {

    // NewAreaListener reads structure data with a plain Gson instance.
    private final Gson gson = new Gson();

    private static final String JSON = "{\"author\":\"LadyAgnes\",\"ignoreEntities\":true,\"integrity\":1.0,"
            + "\"metadata\":\"drowned\",\"mirror\":\"NONE\",\"mode\":\"DATA\",\"name\":\"house\","
            + "\"posX\":3,\"posY\":1,\"posZ\":-7,\"powered\":false,\"rotation\":\"CLOCKWISE_90\","
            + "\"seed\":\"0\",\"showair\":false,\"showboundingbox\":true,"
            + "\"sizeX\":5,\"sizeY\":6,\"sizeZ\":7}";

    private BoxedStructureBlock deserialise() {
        return gson.fromJson(JSON, BoxedStructureBlock.class);
    }

    @Test
    void testStringFields() {
        BoxedStructureBlock b = deserialise();
        assertEquals("LadyAgnes", b.getAuthor());
        assertEquals("drowned", b.getMetadata());
        assertEquals("house", b.getName());
        assertEquals("0", b.getSeed());
    }

    @Test
    void testEnumFields() {
        BoxedStructureBlock b = deserialise();
        assertEquals(Mirror.NONE, b.getMirror());
        assertEquals(Mode.DATA, b.getMode());
        assertEquals(StructureRotation.CLOCKWISE_90, b.getRotation());
    }

    @Test
    void testNumericFields() {
        BoxedStructureBlock b = deserialise();
        assertEquals(1.0f, b.getIntegrity());
        assertEquals(3, b.getPosX());
        assertEquals(1, b.getPosY());
        assertEquals(-7, b.getPosZ());
        assertEquals(5, b.getSizeX());
        assertEquals(6, b.getSizeY());
        assertEquals(7, b.getSizeZ());
    }

    @Test
    void testBooleanFields() {
        BoxedStructureBlock b = deserialise();
        assertTrue(b.isIgnoreEntities());
        assertFalse(b.isPowered());
        assertFalse(b.isShowair());
        assertTrue(b.isShowboundingbox());
    }

    @Test
    void testToString() {
        String s = deserialise().toString();
        assertTrue(s.startsWith("BoxedStructureBlock ["));
        assertTrue(s.contains("author=LadyAgnes"));
        assertTrue(s.contains("mode=DATA"));
        assertTrue(s.contains("sizeZ=7"));
    }
}
