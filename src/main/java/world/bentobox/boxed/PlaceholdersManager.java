package world.bentobox.boxed;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;

/**
 * Handles place holders
 * @author tastybento
 *
 */
public class PlaceholdersManager {

    private final Boxed addon;

    public PlaceholdersManager(Boxed addon) {
        this.addon = addon;
    }

    /**
     * Get boxed advancement count
     * @param user owner or team member
     * @return string of advancement count
     */
    public String getCount(User user) {
        if (user == null || user.getUniqueId() == null) return "";
        Island i = addon.getIslands().getIsland(addon.getOverWorld(), user);
        return i == null ? "" : String.valueOf(addon.getAdvManager().getIsland(i).getAdvancements().size());
    }

    /**
     * Get the advancement count based on user's location
     * @param user user
     * @return string of advancement count
     */
    public String getCountByLocation(User user) {
        if (user == null || user.getUniqueId() == null || user.getLocation() == null) return "";
        return addon.getIslands().getIslandAt(user.getLocation())
                .map(i -> String.valueOf(addon.getAdvManager().getIsland(i).getAdvancements().size())).orElse("");
    }


}
