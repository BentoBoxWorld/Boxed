package world.bentobox.boxed.areas;

import java.util.List;
import java.io.IOException;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.blueprints.BundleAvailability;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.boxed.Boxed;

/**
 * Decides whether a player may make a box in an area. A player can have one box per area.
 * Areas can be closed to new boxes, and can be chained so that a player's box in one area
 * has to reach a size, set per area, before the next area is unlocked.
 *
 * @author tastybento
 */
public class AreaRules {

    static final String NOT_READY = "boxed.areas.errors.not-ready";
    static final String CLOSED = "boxed.areas.errors.closed";
    static final String LOCKED = "boxed.areas.errors.locked";
    static final String REQUIRES = "boxed.areas.errors.requires";
    static final String REQUIRES_BOX = "boxed.areas.errors.requires-box";
    static final String ALREADY_HAVE = "boxed.areas.errors.already-have";

    private final Boxed addon;

    /**
     * @param addon Boxed
     */
    public AreaRules(Boxed addon) {
        this.addon = addon;
    }

    /**
     * Why a player cannot make a box in an area.
     * @param reference locale reference of the reason
     * @param variables variables for the reason, in pairs
     * @param hidden true if the player should not even be shown the area
     */
    record Refusal(String reference, List<String> variables, boolean hidden) {
    }

    /**
     * Works out if there is a reason the player cannot make a box in the area of a bundle.
     * @param user the player the box is for
     * @param bundle unique id of the blueprint bundle, may be null
     * @param oldBox the box being replaced if this is a reset, otherwise null
     * @return the reason, or null if the player can make the box
     */
    @Nullable
    Refusal refusal(@NonNull User user, @Nullable String bundle, @Nullable Island oldBox) {
        AreaManager areas = addon.getAreas();
        if (areas.isPending(bundle)) {
            return new Refusal(NOT_READY, List.of(), true);
        }
        Area area = areas.getAreaForBundle(bundle);
        if (!area.enabled()) {
            return new Refusal(CLOSED, List.of(), true);
        }
        // One box per area. A box that is being reset is about to go, so it does not count.
        if (boxes(user, area).anyMatch(i -> oldBox == null || !i.getUniqueId().equals(oldBox.getUniqueId()))) {
            return new Refusal(ALREADY_HAVE, List.of(), false);
        }
        Area required = areas.getArea(area.requires());
        if (required != null && !hasUnlockedNext(user, required)) {
            return new Refusal(required.unlockSize() > 0 ? REQUIRES : REQUIRES_BOX, List.of(
                    TextVariables.NAME, required.id(), TextVariables.NUMBER, String.valueOf(required.unlockSize())),
                    false);
        }
        return null;
    }

    /**
     * Says whether a player can pick a blueprint bundle. Areas that are closed or not built
     * yet are hidden. Areas the player has not unlocked, or already has a box in, are shown
     * with the reason but cannot be picked.
     * @param user the player
     * @param bundle unique id of the blueprint bundle
     * @param oldBox the box being replaced if this is a reset, otherwise null
     * @return whether the bundle can be used
     */
    @NonNull
    public BundleAvailability availability(@NonNull User user, @Nullable String bundle, @Nullable Island oldBox) {
        Refusal refusal = refusal(user, bundle, oldBox);
        if (refusal == null) {
            return BundleAvailability.available();
        }
        return refusal.hidden() ? BundleAvailability.hidden()
                : BundleAvailability.locked(user.getTranslation(refusal.reference(), refusal.variables().toArray(String[]::new)));
    }

    /**
     * Works out which area a new box goes in and checks the player may make one there. This is
     * the last line of defence: players are normally stopped earlier by {@link #availability}.
     * @param user the player the box is for
     * @param bundle unique id of the blueprint bundle the player picked, may be null
     * @param oldBox the box being replaced if this is a reset, otherwise null
     * @return the area
     * @throws IOException if the box must not be made. The message is a locale reference.
     */
    @NonNull
    public Area check(@NonNull User user, @Nullable String bundle, @Nullable Island oldBox) throws IOException {
        Refusal refusal = refusal(user, bundle, oldBox);
        if (refusal != null) {
            if (!refusal.variables().isEmpty()) {
                // The reason has details, so tell the player here and refuse with the short version
                user.sendMessage(refusal.reference(), refusal.variables().toArray(String[]::new));
                throw new IOException(LOCKED);
            }
            throw new IOException(refusal.reference());
        }
        return addon.getAreas().getAreaForBundle(bundle);
    }

    /**
     * Checks if a player has unlocked the areas that require this one. They have if they own
     * or are a team member of a box in the area that has reached the area's unlock size.
     * @param user player
     * @param area area
     * @return true if the player has unlocked the areas that require this area
     */
    public boolean hasUnlockedNext(@NonNull User user, @NonNull Area area) {
        return boxes(user, area).anyMatch(i -> i.getProtectionRange() >= area.unlockSize());
    }

    /**
     * @return the boxes in an area that the player owns or is a team member of
     */
    private Stream<Island> boxes(User user, Area area) {
        return addon.getIslands().getIslands(addon.getOverWorld(), user).stream()
                .filter(i -> addon.getAreaAt(i.getCenter().getBlockZ() >> 4).equals(area));
    }
}
