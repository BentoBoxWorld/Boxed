package world.bentobox.boxed;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.advancement.Advancement;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.bentobox.api.events.island.IslandEvent;
import world.bentobox.bentobox.database.Database;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.RanksManager;
import world.bentobox.bentobox.util.Util;
import world.bentobox.boxed.objects.IslandAdvancements;

/**
 * Manages Island advancements
 * @author tastybento
 *
 */
public class AdvancementsManager {

    private final Boxed addon;
    // Database handler for level data
    private final Database<IslandAdvancements> handler;
    // A cache of island levels.
    private final Map<String, IslandAdvancements> cache;
    private final YamlConfiguration advConfig;
    private int unknownAdvChange;
    private int unknownRecipeChange;

    /**
     * @param addon addon
     */
    public AdvancementsManager(Boxed addon) {
        this.addon = addon;
        // Get the BentoBox database
        // Set up the database handler to store and retrieve data
        // Note that these are saved by the BentoBox database
        handler = new Database<>(addon, IslandAdvancements.class);
        // Initialize the cache
        cache = new HashMap<>();
        // Advancement score sheet
        addon.saveResource("advancements.yml", false);
        advConfig = new YamlConfiguration();
        File advFile = new File(addon.getDataFolder(), "advancements.yml");
        if (!advFile.exists()) {
            addon.logError("advancements.yml cannot be found!");
        } else {
            try {
                advConfig.load(advFile);
                unknownAdvChange = advConfig.getInt("settings.unknown-advancement-increase", 0);
                unknownRecipeChange = advConfig.getInt("settings.unknown-recipe-increase", 0);
            } catch (IOException | InvalidConfigurationException e) {
                addon.logError("advancements.yml cannot be found! " + e.getLocalizedMessage());
            }
        }

    }

    /**
     * Get advancements for the island, loading from database if required
     * @param island - island
     * @return the island's advancement list object
     */
    @NonNull
    public IslandAdvancements getIsland(Island island) {
        return cache.computeIfAbsent(island.getUniqueId(), this::getFromDb);

    }

    @NonNull
    private IslandAdvancements getFromDb(String k) {
        if (!handler.objectExists(k)) {
            return new IslandAdvancements(k);
        }
        @Nullable
        IslandAdvancements ia = handler.loadObject(k);
        return ia == null ? new IslandAdvancements(k) : ia;
    }

    /**
     * Save the island
     * @param island - island
     * @return CompletableFuture true if saved successfully
     */
    protected CompletableFuture<Boolean> saveIsland(Island island) {
        return cache.containsKey(island.getUniqueId()) ? handler.saveObjectAsync(cache.get(island.getUniqueId())): CompletableFuture.completedFuture(true);
    }

    /**
     * Save all values in the cache
     */
    protected void save() {
        cache.values().forEach(handler::saveObjectAsync);
    }

    /**
     * Remove island from cache
     * @param island - island
     */
    protected void removeFromCache(Island island) {
        cache.remove(island.getUniqueId());
    }

    /**
     * Add advancement to island
     * @param island - island
     * @param advancement - advancement string
     * @return true if added, false if already added
     */
    public boolean addAdvancement(Island island, String advancement) {
        if (hasAdvancement(island, advancement)) {
            return false;
        }
        getIsland(island).getAdvancements().add(advancement);
        this.saveIsland(island);
        return true;
    }

    /**
     * Remove advancement from island
     * @param island - island
     * @param advancement - advancement string
     */
    public void removeAdvancement(Island island, String advancement) {
        getIsland(island).getAdvancements().remove(advancement);
        this.saveIsland(island);
    }

    /**
     * Check if island has advancement
     * @param island - island
     * @param advancement - advancement
     * @return true if island has advancement, false if not
     */
    public boolean hasAdvancement(Island island, String advancement) {
        return getIsland(island).getAdvancements().contains(advancement);
    }

    /**
     * Check and correct the island's protection size based on accumulated advancements
     * @param island - island to check
     * @return value of island size change. Negative values means the island range shrank.
     */
    public int checkIslandSize(Island island) {
        // Island is always a minimum of 1 for free.
        int shouldSize = getIsland(island).getAdvancements().stream().mapToInt(this::getScore).sum() + 1;
        if (shouldSize < 1) {
            // Boxes can never be less than 1 in protection size
            return 0;
        }
        int diff = shouldSize - island.getProtectionRange();
        if (diff != 0) {
            this.setProtectionSize(island, shouldSize, null);
        }
        return diff;
    }

    /**
     * Add advancement to island and adjusts the island's protection size accordingly
     * @param p - player who just advanced
     * @param advancement - advancement
     * @return score for advancement. 0 if the advancement was not added.
     */
    public int addAdvancement(Player p, Advancement advancement) {
        if (!addon.getOverWorld().equals(Util.getWorld(p.getWorld()))) {
            // Wrong world
            return 0;
        }
        int score = getScore(advancement.getKey().toString());
        if (score == 0) {
            return 0;
        }
        // Get island
        Island island = addon.getIslands().getIsland(addon.getOverWorld(), p.getUniqueId());
        if (island != null
                && island.getRank(p.getUniqueId()) >= RanksManager.MEMBER_RANK // Only island members expand island
                && addAdvancement(island, advancement.getKey().toString())) {
            int oldSize = island.getProtectionRange();
            int newSize = Math.max(1, oldSize + score);
            setProtectionSize(island, newSize, p.getUniqueId());
            return score;
        }
        return 0;

    }

    /**
     * Sets the island protection size and fires an event for it
     * @param island - island
     * @param newSize - new size of protected area
     * @param uuid - UUID of player making the change. null if the change is system-driven.
     */
    private void setProtectionSize(@NonNull Island island, int newSize, @Nullable UUID uuid) {
        island.setProtectionRange(newSize);
        // Call Protection Range Change event. Does not support canceling.
        IslandEvent.builder()
        .island(island)
        .location(island.getCenter())
        .reason(IslandEvent.Reason.RANGE_CHANGE)
        .involvedPlayer(uuid)
        .admin(true)
        .protectionRange(newSize, island.getProtectionRange())
        .build();

    }

    /**
     * Get the score for this advancement
     * @param string - advancement key as stored in the config file
     * @return score of advancement, or default values if the key is not in the file
     */
    public int getScore(String string) {
        String adv = "advancements." + string;
        // Check score of advancement
        if (advConfig.contains(adv)) {
            return advConfig.getInt(adv, this.unknownAdvChange);
        }
        // Unknowns
        if (adv.endsWith("/root")) {
            return advConfig.getInt("settings.default-root-increase");
        }
        if (adv.contains("minecraft:recipes")) {
            return this.unknownRecipeChange;
        }
        return this.unknownAdvChange;
    }

}
