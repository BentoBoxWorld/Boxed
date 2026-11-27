package world.bentobox.boxed.workshop;

import java.util.Locale;

import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.eclipse.jdt.annotation.Nullable;

import com.google.common.base.Enums;

/**
 * A structure the admin has put on the land of the workshop, to be placed in every box of
 * the area.
 * @param x block x in the workshop world
 * @param y block y
 * @param z block z in the workshop world
 * @param name name of the structure without a namespace, for example village/plains/houses/plains_small_house_1
 * @param rotation how it is turned
 * @param mirror how it is mirrored
 * @param noMobs true if the structure's mobs are left out
 *
 * @author tastybento
 */
public record PlacedStructure(int x, int y, int z, String name, StructureRotation rotation, Mirror mirror,
        boolean noMobs) {

    private static final String NO_MOBS = "NO_MOBS";

    /**
     * Makes sure the name is lower case and has no namespace
     */
    public PlacedStructure {
        name = name.toLowerCase(Locale.ENGLISH).replace("minecraft:", "").strip();
    }

    /**
     * @param centreX block x of the middle of the land
     * @param centreZ block z of the middle of the land
     * @return where the structure is as a structures file has it: x and z from the middle of the box
     */
    public String key(int centreX, int centreZ) {
        return (x - centreX) + "," + y + "," + (z - centreZ);
    }

    /**
     * @return what the structure is as a structures file has it: name, rotation and mirror
     */
    public String value() {
        return name + "," + rotation.name() + "," + mirror.name() + (noMobs ? "," + NO_MOBS : "");
    }

    /**
     * @return the structure in one line, for saving
     */
    public String text() {
        return x + "," + y + "," + z + "," + value();
    }

    /**
     * @param other another structure
     * @return true if both start at the same block
     */
    public boolean samePlace(PlacedStructure other) {
        return x == other.x && y == other.y && z == other.z;
    }

    /**
     * Reads a structure from a line made by {@link #text()}.
     * @param text the line
     * @return the structure, or null if the line is not one
     */
    @Nullable
    public static PlacedStructure parse(String text) {
        String[] parts = text.split(",", 4);
        return parts.length < 4 ? null : of(parts[0] + "," + parts[1] + "," + parts[2], parts[3], 0, 0);
    }

    /**
     * Reads a structure from an entry of a structures file.
     * @param key x,y,z with x and z from the middle of the box
     * @param value name,rotation,mirror and maybe NO_MOBS
     * @param centreX block x of the middle of the land
     * @param centreZ block z of the middle of the land
     * @return the structure, or null if the entry is not one
     */
    @Nullable
    public static PlacedStructure of(String key, String value, int centreX, int centreZ) {
        String[] xyz = key.split(",");
        // Old files have a space before NO_MOBS where there should be a comma
        String[] parts = value.replace(" " + NO_MOBS, "," + NO_MOBS).split(",");
        if (xyz.length != 3 || parts[0].isBlank()) {
            return null;
        }
        try {
            return new PlacedStructure(Integer.parseInt(xyz[0].strip()) + centreX, Integer.parseInt(xyz[1].strip()),
                    Integer.parseInt(xyz[2].strip()) + centreZ, parts[0],
                    parts.length > 1 ? Enums.getIfPresent(StructureRotation.class, upper(parts[1]))
                            .or(StructureRotation.NONE) : StructureRotation.NONE,
                    parts.length > 2 ? Enums.getIfPresent(Mirror.class, upper(parts[2])).or(Mirror.NONE) : Mirror.NONE,
                    parts.length > 3 && NO_MOBS.equalsIgnoreCase(parts[3].strip()));
        } catch (NumberFormatException _) {
            return null;
        }
    }

    private static String upper(String text) {
        return text.strip().toUpperCase(Locale.ENGLISH);
    }
}
