package world.bentobox.boxed.areas;

/**
 * A kind of land that boxes can be made in. Every box in an area has the same land.
 *
 * @param id unique name of the area. It matches the unique id of a blueprint bundle.
 * @param seed world seed the land is generated from
 * @param biomes name of the file that defines the biomes around the middle of the area
 * @param structures name of the file that lists the structures placed in each new box
 * @param row row of the box grid that this area's boxes are placed in
 * @param enabled false if new boxes can no longer be made in this area. Existing boxes stay.
 * @param requires name of the area that unlocks this one, or null if anyone can start here
 * @param unlockSize the size a player's box in this area must reach to unlock the areas that
 *                   require it. Each area sets its own, so some can be harder than others.
 *                   0 means that just having a box in the area is enough.
 * @author tastybento
 */
public record Area(String id, long seed, String biomes, String structures, int row, boolean enabled,
        String requires, int unlockSize) {

    /**
     * Name of the area that always exists
     */
    public static final String DEFAULT = "default";

    /**
     * @return true if this is the default area
     */
    public boolean isDefault() {
        return DEFAULT.equals(id);
    }

    /**
     * @param worldName name of the game overworld
     * @param nether true for the Nether seed world
     * @return name of the seed world this area's land is generated in
     */
    public String seedWorldName(String worldName, boolean nether) {
        return worldName + "/seed" + (isDefault() ? "" : "_" + id) + (nether ? "_nether" : "");
    }
}
