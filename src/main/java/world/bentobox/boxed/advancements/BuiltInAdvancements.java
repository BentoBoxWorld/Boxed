package world.bentobox.boxed.advancements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import net.kyori.adventure.key.Key;
import world.bentobox.boxed.Boxed;

/**
 * Gives the server the advancements that come with Boxed, so that no data pack has to be
 * installed. They are kept in memory only: they are registered when Boxed starts and again
 * whenever the server's data is reloaded, and nothing is written to the world.
 * <p>
 * The files are in the jar under {@value AdvancementFiles#FOLDER}, in a folder for each
 * namespace. A file of the same path in Boxed's own folder takes the place of the one in
 * the jar, and other files there are added. An advancement that the server already has,
 * from a data pack for instance, is left alone.
 *
 * @author tastybento
 */
public class BuiltInAdvancements implements Listener {

    private static final String TAGS = "tags/";
    private static final String CRITERION = "boxed";
    private static final String NAMESPACE = "bentobox";
    /**
     * Advancements that the game cannot give without a data pack, because they depend on the
     * kind of damage taken. Boxed gives them.
     */
    private static final NamespacedKey DRIPSTONE = new NamespacedKey(NAMESPACE, "caving/dripstone");
    private static final NamespacedKey IN_GRAVEL = new NamespacedKey(NAMESPACE, "caving/in_gravel");
    /**
     * Opening a chest in a bastion. The server cannot check which loot a chest holds for an
     * advancement that did not come from a data pack, so Boxed gives this one too.
     */
    private static final NamespacedKey LOOT_BASTION = new NamespacedKey(NAMESPACE, "nether_structures/loot_bastion");
    private static final String BASTION_LOOT = "chests/bastion_";

    private final Boxed addon;

    /**
     * @param addon Boxed
     */
    public BuiltInAdvancements(Boxed addon) {
        this.addon = addon;
    }

    /**
     * Registers every advancement the server does not have yet.
     * @return how many were registered
     */
    public int register() {
        Map<String, String> files;
        try {
            files = AdvancementFiles.read(addon.getFile(), addon.getDataFolder());
        } catch (IOException e) {
            addon.logError("Could not read the advancements that come with Boxed: " + e.getMessage());
            return 0;
        }
        AdvancementTags tags = new AdvancementTags(BuiltInAdvancements::gameTag, addon::logWarning);
        Map<NamespacedKey, String> found = new HashMap<>();
        files.forEach((path, text) -> {
            String[] parts = path.substring(0, path.length() - ".json".length()).split("/", 2);
            if (path.startsWith(TAGS) && parts[1].contains("/")) {
                String[] tag = parts[1].split("/", 2);
                tags.add(tag[0], tag[1], text);
            } else if (parts.length == 2) {
                NamespacedKey key = NamespacedKey.fromString(parts[0] + ":" + parts[1]);
                if (key == null) {
                    addon.logWarning("Advancement file " + path + " has a name the game cannot use");
                } else {
                    found.put(key, text);
                }
            }
        });
        return register(prepare(found, tags, addon::logWarning));
    }

    /**
     * Makes the advancements ready for the server: lists are written out in full, and they
     * are put in order, so that each comes after the one it follows on from.
     * @param found the text of each advancement
     * @param tags Boxed's lists
     * @param warn told about any that cannot be read
     * @return the advancements, parents first
     */
    static Map<NamespacedKey, String> prepare(Map<NamespacedKey, String> found, AdvancementTags tags,
            java.util.function.Consumer<String> warn) {
        Map<NamespacedKey, String> ready = new HashMap<>();
        Map<NamespacedKey, NamespacedKey> parents = new HashMap<>();
        found.forEach((key, text) -> {
            try {
                String json = tags.apply(text);
                JsonElement parent = JsonParser.parseString(json).getAsJsonObject().get("parent");
                ready.put(key, json);
                if (parent != null) {
                    parents.put(key, NamespacedKey.fromString(parent.getAsString()));
                }
            } catch (JsonSyntaxException | IllegalStateException e) {
                warn.accept("Advancement " + key + " cannot be read: " + e.getMessage());
            }
        });
        Map<NamespacedKey, String> ordered = new LinkedHashMap<>();
        ready.keySet().stream()
                .sorted(Comparator.<NamespacedKey>comparingInt(k -> depth(k, parents)).thenComparing(NamespacedKey::toString))
                .forEach(k -> ordered.put(k, ready.get(k)));
        return ordered;
    }

    private static int depth(NamespacedKey key, Map<NamespacedKey, NamespacedKey> parents) {
        int depth = 0;
        NamespacedKey at = parents.get(key);
        // The limit is for advancements that name each other as parents
        while (at != null && depth < 100) {
            depth++;
            at = parents.get(at);
        }
        return depth;
    }

    private int register(Map<NamespacedKey, String> ordered) {
        Map<Key, String> wanted = new LinkedHashMap<>();
        ordered.forEach((key, json) -> {
            if (Bukkit.getAdvancement(key) == null) {
                wanted.put(key, json);
            }
        });
        if (wanted.isEmpty()) {
            return 0;
        }
        try {
            // false: they are not written into the world as a data pack
            return Bukkit.getUnsafe().loadAdvancements(wanted, false).size();
        } catch (RuntimeException e) {
            addon.logWarning("Some of Boxed's advancements could not be registered together (" + e.getMessage()
                    + "). Trying them one at a time.");
        }
        // One that is wrong must not stop the rest
        List<String> failed = new ArrayList<>();
        int count = 0;
        for (Map.Entry<Key, String> en : wanted.entrySet()) {
            try {
                count += Bukkit.getUnsafe().loadAdvancements(Map.of(en.getKey(), en.getValue()), false).size();
            } catch (RuntimeException _) {
                failed.add(en.getKey().asString());
            }
        }
        if (!failed.isEmpty()) {
            addon.logError("These advancements could not be registered: " + String.join(", ", failed));
        }
        return count;
    }

    /**
     * Looks up one of the game's tags.
     * @param kind item, block or entity_type
     * @param id the tag's id, for example minecraft:raiders
     * @return the ids in the tag, or null if there is no such tag
     */
    @Nullable
    private static Set<String> gameTag(String kind, String id) {
        NamespacedKey key = NamespacedKey.fromString(id);
        if (key == null) {
            return null;
        }
        Tag<? extends Keyed> tag = switch (kind) {
        case "item" -> Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
        case "block" -> Bukkit.getTag(Tag.REGISTRY_BLOCKS, key, Material.class);
        case "entity_type" -> Bukkit.getTag(Tag.REGISTRY_ENTITY_TYPES, key, EntityType.class);
        default -> null;
        };
        return tag == null ? null
                : tag.getValues().stream().map(v -> v.getKey().toString()).sorted().collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /**
     * A reload of the server's data throws away everything that did not come from a data pack.
     */
    @EventHandler
    public void onReload(ServerResourcesReloadedEvent e) {
        int count = register();
        if (count > 0) {
            addon.log("The server's data was reloaded. Registered " + count + " advancements again.");
        }
    }

    /**
     * Gives the advancements that depend on the kind of damage taken: being hurt by a
     * stalagmite, and being smothered in gravel.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player player) || !addon.inWorld(player.getWorld())) {
            return;
        }
        DamageType type = e.getDamageSource().getDamageType();
        if (type == DamageType.STALAGMITE) {
            award(player, DRIPSTONE);
        } else if (type == DamageType.IN_WALL && player.getEyeLocation().getBlock().getType() == Material.GRAVEL) {
            award(player, IN_GRAVEL);
        }
    }

    /**
     * Gives the advancement for opening a bastion's chest for the first time.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLoot(LootGenerateEvent e) {
        if (e.getEntity() instanceof Player player && addon.inWorld(player.getWorld())
                && e.getLootTable().getKey().getKey().startsWith(BASTION_LOOT)) {
            award(player, LOOT_BASTION);
        }
    }

    private static void award(Player player, NamespacedKey key) {
        Advancement advancement = Bukkit.getAdvancement(key);
        if (advancement != null) {
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            if (!progress.isDone()) {
                progress.awardCriteria(CRITERION);
            }
        }
    }
}
