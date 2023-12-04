package world.bentobox.boxed.listeners;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import world.bentobox.bentobox.api.events.island.IslandNewIslandEvent;
import world.bentobox.bentobox.api.events.team.TeamJoinedEvent;
import world.bentobox.bentobox.api.events.team.TeamLeaveEvent;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;
import world.bentobox.boxed.Boxed;

/**
 * @author tastybento
 *
 */
public class AdvancementListener implements Listener {

    private final Boxed addon;
    private final Advancement netherAdvancement;
    private final Advancement netherFortressAdvancement;
    private final Advancement endAdvancement;
    private final Advancement netherRoot;
    private final Advancement endRoot;

    /**
     * @param addon addon
     */
    public AdvancementListener(Boxed addon) {
        this.addon = addon;
        this.netherAdvancement = getAdvancement("minecraft:story/enter_the_nether");
        this.endAdvancement = getAdvancement("minecraft:story/enter_the_end");
        this.netherFortressAdvancement = getAdvancement("minecraft:nether/find_fortress");
        this.netherRoot = getAdvancement("minecraft:nether/root");
        this.endRoot = getAdvancement("minecraft:end/root");
    }


    /**
     * Get Advancement given the namespaced key for it
     * @param key namespaced key name for Advancement
     * @return Advancement or null if none found
     */
    public static Advancement getAdvancement(String key) {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(Bukkit.advancementIterator(), Spliterator.ORDERED), false)
                .filter(a -> a.getKey().toString().equals(key))
                .findFirst().orElse(null);
    }


    /**
     * Awards a bigger box when an advancement is done. Removes advancements if they are not valid.
     * @param e PlayerAdvancementDoneEvent
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        // Ignore if player is not in survival or if advancements are being ignored
        if (!e.getPlayer().getGameMode().equals(GameMode.SURVIVAL) || addon.getSettings().isIgnoreAdvancements()) {
            return;
        }
        // Check if player is in the Boxed worlds
        if (addon.inWorld(e.getPlayer().getWorld())) {
            // Only allow members or higher to get advancements in a box
            if (addon.getSettings().isDenyVisitorAdvancements() && !addon.getIslands().getIslandAt(e.getPlayer().getLocation()).map(i -> i.getMemberSet().contains(e.getPlayer().getUniqueId())).orElse(false)) {
                // Remove advancement from player
                e.getAdvancement().getCriteria().forEach(c ->
                e.getPlayer().getAdvancementProgress(e.getAdvancement()).revokeCriteria(c));
                User u = User.getInstance(e.getPlayer());
                if (u != null && addon.getAdvManager().getScore(e.getAdvancement().getKey().getKey()) > 0) {
                    u.notify("boxed.adv-disallowed", TextVariables.NAME, e.getPlayer().getName(), TextVariables.DESCRIPTION, this.keyToString(u, e.getAdvancement().getKey()));
                }
                return;
            }
            // Add the advancement to the island
            int score = addon.getAdvManager().addAdvancement(e.getPlayer(), e.getAdvancement());
            // Tell other team players one tick after it occurs if it is something that has a score
            if (score != 0) {
                User user = User.getInstance(e.getPlayer());
                Bukkit.getScheduler().runTask(addon.getPlugin(), () -> tellTeam(user, e.getAdvancement().getKey(), score));
            }
        }
    }

    private void tellTeam(User user, NamespacedKey key, int score) {
        Island island = addon.getIslands().getIsland(addon.getOverWorld(), user);
        if (island == null) {
            // Something went wrong here
            return;
        }
        island.getMemberSet().stream().map(User::getInstance).filter(User::isOnline)
        .forEach(u -> {
            informPlayer(u, key, score);
            // Sync
            grantAdv(u, addon.getAdvManager().getIsland(island).getAdvancements());
        });
        // Broadcast
        if (addon.getSettings().isBroadcastAdvancements()) {
            Bukkit.getOnlinePlayers().stream().filter(p -> p.hasPermission(Server.BROADCAST_CHANNEL_USERS))
            .map(User::getInstance)
            .forEach(u -> u.sendMessage("boxed.user-completed", TextVariables.NAME, user.getName(), TextVariables.DESCRIPTION, this.keyToString(u, key)));
        }
    }

    /**
     * Synchronize the player's advancements to that of the box.
     * Player's advancements should be cleared before calling this otherwise they will get add the box ones as well.
     * @param user - user
     */
    public void syncAdvancements(User user) {
        if (addon.getSettings().isIgnoreAdvancements()) return;
        Island box = addon.getIslands().getIsland(addon.getOverWorld(), user);
        if (box != null) {
            grantAdv(user, addon.getAdvManager().getIsland(box).getAdvancements());
            int diff = addon.getAdvManager().checkIslandSize(box);
            if (diff > 0) {
                user.sendMessage("boxed.size-changed", TextVariables.NUMBER, String.valueOf(diff));
                user.getPlayer().playSound(Objects.requireNonNull(user.getLocation()), Sound.ENTITY_PLAYER_LEVELUP, 1F, 2F);
            } else if (diff < 0) {
                user.sendMessage("boxed.size-decreased", TextVariables.NUMBER, String.valueOf(Math.abs(diff)));
                user.getPlayer().playSound(Objects.requireNonNull(user.getLocation()), Sound.ENTITY_VILLAGER_DEATH, 1F, 2F);
            }
        }
    }

    private void informPlayer(User user, NamespacedKey key, int score) {
        user.getPlayer().playSound(Objects.requireNonNull(user.getLocation()), Sound.ENTITY_PLAYER_LEVELUP, 1F, 2F);
        user.sendMessage("boxed.completed", TextVariables.NAME,  keyToString(user, key));
        user.sendMessage("boxed.size-changed", TextVariables.NUMBER, String.valueOf(score));

    }

    private String keyToString(User user, NamespacedKey key) {
        String adv = user.getTranslationOrNothing("boxed.advancements." + key.toString());
        if (adv.isEmpty()) {
            adv = Util.prettifyText(key.getKey().substring(key.getKey().lastIndexOf("/") + 1));
        }
        return adv;
    }


    /**
     * Special case Advancement awarding
     * Awards the nether and end advancements when they use a portal for the first time.
     * @param e PlayerPortalEvent
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) {
        if (!addon.inWorld(e.getPlayer().getWorld()) || !e.getPlayer().getGameMode().equals(GameMode.SURVIVAL)
                || addon.getSettings().isIgnoreAdvancements()) {
            return;
        }
        if (e.getCause().equals(TeleportCause.NETHER_PORTAL)) {
            giveAdv(e.getPlayer(), netherAdvancement);
            giveAdv(e.getPlayer(), netherRoot);

        } else if (e.getCause().equals(TeleportCause.END_PORTAL)) {
            giveAdv(e.getPlayer(), endAdvancement);
            giveAdv(e.getPlayer(), endRoot);
        }
    }

    /**
     * Special case Advancement awarding
     * Looks for certain blocks, and if they are found then awards an advancement
     * @param e - PlayerMoveEvent
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!addon.getSettings().isNetherGenerate() || !Util.sameWorld(e.getPlayer().getWorld(), addon.getNetherWorld())
                || addon.getSettings().isIgnoreAdvancements()) {
            return;
        }
        // Nether fortress advancement
        if (e.getTo().getBlock().getRelative(BlockFace.DOWN).getType().equals(Material.NETHER_BRICKS)) {
            giveAdv(e.getPlayer(), netherFortressAdvancement);
        }
    }


    /**
     * Give player an advancement
     * @param player - player
     * @param adv - Advancement
     */
    public static void giveAdv(Player player, Advancement adv) {
        if (adv != null && !player.getAdvancementProgress(adv).isDone()) {
            adv.getCriteria().forEach(player.getAdvancementProgress(adv)::awardCriteria);
        }
    }

    /**
     * Sync advancements when player joins server if they are in the Boxed world
     * @param e PlayerJoinEvent
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent e) {
        User user = User.getInstance(e.getPlayer());
        if (addon.inWorld(e.getPlayer().getWorld())) {
            // Set advancements to same as island
            syncAdvancements(user);
        }
    }

    /**
     * Sync advancements when player enters the Boxed world
     * @param e PlayerChangedWorldEvent
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerEnterWorld(PlayerChangedWorldEvent e) {
        User user = User.getInstance(e.getPlayer());
        if (Util.sameWorld(addon.getOverWorld(), e.getPlayer().getWorld())) {
            // Set advancements to same as island
            syncAdvancements(user);
        }
    }

    /**
     * Clear and sync advancements for a player when they join a team if the settings require it
     * @param e TeamJoinedEvent
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTeamJoinTime(TeamJoinedEvent e) {
        User user = User.getInstance(e.getPlayerUUID());
        if (user != null && addon.getSettings().isOnJoinResetAdvancements() && user.isOnline()
                && addon.getOverWorld().equals(Util.getWorld(user.getWorld()))) {
            // Clear and set advancements
            clearAndSetAdv(user, addon.getSettings().isOnJoinResetAdvancements(), addon.getSettings().getOnJoinGrantAdvancements());
            // Set advancements to same as island
            syncAdvancements(user);
        }
    }

    /**
     * Clear player's advancements when they leave a team if the setting requires it
     * @param e TeamLeaveEvent
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTeamLeaveTime(TeamLeaveEvent e) {
        if (addon.getSettings().isIgnoreAdvancements()) return;
        User user = User.getInstance(e.getPlayerUUID());
        if (user != null && addon.getSettings().isOnJoinResetAdvancements() && user.isOnline()
                && addon.getOverWorld().equals(Util.getWorld(user.getWorld()))) {
            // Clear and set advancements
            clearAndSetAdv(user, addon.getSettings().isOnLeaveResetAdvancements(), addon.getSettings().getOnLeaveGrantAdvancements());

        }
    }

    /**
     * Clear player's advancements when they start an island for the first time.
     * @param e IslandNewIslandEvent
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFirstTime(IslandNewIslandEvent e) {
        if (addon.getSettings().isIgnoreAdvancements()) return;
        User user = User.getInstance(e.getPlayerUUID());
        if (user != null) {
            clearAndSetAdv(user, addon.getSettings().isOnJoinResetAdvancements(), addon.getSettings().getOnJoinGrantAdvancements());
        }
    }


    /**
     * Clear and set advancements for user. Will not do anything if the user is offline
     * @param user - user
     * @param clear - whether to clear advacements for this user or not
     * @param list - list of advacements (namespaced keys) to grant to user
     */
    private void clearAndSetAdv(User user, boolean clear, List<String> list) {
        if (!user.isOnline()) {
            return;
        }
        if (clear) {
            clearAdv(user);
        }
        grantAdv(user, list);

    }


    /**
     * Grant advancement to user
     * @param user - user
     * @param list - list of advancements to grant
     */
    private void grantAdv(User user, List<String> list) {
        Iterator<Advancement> it = Bukkit.advancementIterator();
        while (it.hasNext()) {
            Advancement a = it.next();
            AdvancementProgress progress = user.getPlayer().getAdvancementProgress(a);
            if (list.contains(a.getKey().toString()) && !progress.isDone()) {
                // Award
                a.getCriteria().forEach(progress::awardCriteria);
            }
        }
    }

    private void clearAdv(User user) {
        // Clear Statistics
        Arrays.stream(Statistic.values()).forEach(s -> resetStats(user, s));
        // Clear advancements
        Iterator<Advancement> it = Bukkit.advancementIterator();
        while (it.hasNext()) {
            Advancement a = it.next();
            AdvancementProgress p = user.getPlayer().getAdvancementProgress(a);
            p.getAwardedCriteria().forEach(p::revokeCriteria);
        }

    }

    private void resetStats(User user, Statistic s) {
        switch(s.getType()) {
        case BLOCK -> Arrays.stream(Material.values()).filter(Material::isBlock).forEach(m -> user.getPlayer().setStatistic(s, m, 0));
        case ITEM -> Arrays.stream(Material.values()).filter(Material::isItem).forEach(m -> user.getPlayer().setStatistic(s, m, 0));
        case ENTITY -> Arrays.stream(EntityType.values()).filter(EntityType::isAlive).forEach(m -> user.getPlayer().setStatistic(s, m, 0));
        case UNTYPED -> user.getPlayer().setStatistic(s, 0);
        }
    }

}
