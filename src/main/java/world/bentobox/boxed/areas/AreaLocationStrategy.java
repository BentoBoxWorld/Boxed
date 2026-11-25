package world.bentobox.boxed.areas;

import org.bukkit.Location;
import org.bukkit.World;

import world.bentobox.bentobox.managers.island.DefaultNewIslandLocationStrategy;
import world.bentobox.boxed.Boxed;

/**
 * Finds the place for a new box in an area. Boxes of an area go along the area's row of the
 * box grid, spreading out east and west from the middle.
 *
 * @author tastybento
 */
public class AreaLocationStrategy extends DefaultNewIslandLocationStrategy {

    /**
     * How many taken positions in a row to pass before giving up
     */
    static final int MAX_TRIES = 10000;

    private final Boxed addon;
    private final Area area;
    private final int height;

    /**
     * @param addon Boxed
     * @param area the area the box will be in
     */
    public AreaLocationStrategy(Boxed addon, Area area) {
        this.addon = addon;
        this.area = area;
        this.height = new BoxHeight(addon).get(area);
    }

    @Override
    public Location getNextLocation(World world) {
        return find(world);
    }

    /**
     * Finds where the next box of the area will go.
     * @param world the game overworld
     * @return location of the next box, or the middle of the area's row if the row is full
     */
    public Location peekNextLocation(World world) {
        Location loc = find(world);
        return loc == null ? location(world, 0) : loc;
    }

    private Location location(World world, int position) {
        // Distance between box centres. The generator repeats the land on whole chunks.
        int pitch = addon.getChunkGenerator(World.Environment.NORMAL).getSize() * 2 * 16;
        return new Location(world, (double) AreaManager.column(position) * pitch, height,
                (double) area.row() * pitch);
    }

    /**
     * Finds the first position in the area's row that nothing is at. Positions are searched
     * from the middle every time, so a position is used again once the box that was there
     * has been deleted and its data cleaned up. Until then the old box is still on record,
     * which keeps the position taken, so a new box never gets another player's leftovers.
     */
    private Location find(World world) {
        for (int position = 0; position < MAX_TRIES; position++) {
            Location loc = location(world, position);
            if (isIsland(loc) == Result.FREE) {
                return loc;
            }
        }
        addon.logError("Could not find a free spot for a box in area " + area.id());
        return null;
    }
}
