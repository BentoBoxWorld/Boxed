package world.bentobox.boxed.listeners;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import world.bentobox.bentobox.api.events.island.IslandNewIslandEvent;
import world.bentobox.bentobox.api.events.team.TeamJoinedEvent;
import world.bentobox.bentobox.api.events.team.TeamLeaveEvent;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.RanksManager;
import world.bentobox.bentobox.util.Util;
import world.bentobox.boxed.Boxed;

/**
 * @author tastybento
 *
 */
public class AdvancementListener implements Listener {

    private Boxed addon;
    private final Advancement netherAdvancement;
    private final Advancement endAdvancement;
    private final Advancement netherRoot;
    private final Advancement endRoot;

    /**
     * @param addon
     */
    public AdvancementListener(Boxed addon) {
        this.addon = addon;
        this.netherAdvancement = getAdvancement("minecraft:story/enter_the_nether");
        this.endAdvancement = getAdvancement("minecraft:story/enter_the_end");
        this.netherRoot = getAdvancement("minecraft:nether/root");
        this.endRoot = getAdvancement("minecraft:end/root");
    }


    private Advancement getAdvancement(String string) {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(Bukkit.advancementIterator(), Spliterator.ORDERED), false)
                .filter(a -> a.getKey().toString().equals(string))
                .findFirst().orElse(null);
    }


    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        if (Util.sameWorld(e.getPlayer().getWorld(), addon.getOverWorld())) {
            // Only allow members or higher to get advancements in a box
            if (!addon.getIslands().getIslandAt(e.getPlayer().getLocation()).map(i -> i.getMemberSet().contains(e.getPlayer().getUniqueId())).orElse(false)) {
                // Remove advancement from player
                e.getAdvancement().getCriteria().forEach(c ->
                e.getPlayer().getAdvancementProgress(e.getAdvancement()).revokeCriteria(c));
                User u = User.getInstance(e.getPlayer());
                u.notify("boxed.adv-disallowed", TextVariables.NAME, e.getPlayer().getName(), TextVariables.DESCRIPTION, this.keyToString(u, e.getAdvancement().getKey()));
                return;
            }

            int score = addon.getAdvManager().addAdvancement(e.getPlayer(), e.getAdvancement());
            if (score != 0) {
                User user = User.getInstance(e.getPlayer());
                Bukkit.getScheduler().runTask(addon.getPlugin(), () -> tellTeam(user, e.getAdvancement().getKey(), score));
            }
        }
    }

    private void tellTeam(User user, NamespacedKey key, int score) {
        Island island = addon.getIslands().getIsland(addon.getOverWorld(), user);
        island.getMemberSet(RanksManager.MEMBER_RANK).stream()
        .map(User::getInstance)
        .filter(User::isOnline)
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
     * Synchronize the player's advancements to that of the island.
     * Player's advancements should be cleared before calling this othewise they will get add the island ones as well.
     * @param user - user
     */
    public void syncAdvancements(User user) {
        Island island = addon.getIslands().getIsland(addon.getOverWorld(), user);
        if (island != null) {
            grantAdv(user, addon.getAdvManager().getIsland(island).getAdvancements());
            int diff = addon.getAdvManager().checkIslandSize(island);
            if (diff > 0) {
                user.sendMessage("boxed.size-changed", TextVariables.NUMBER, String.valueOf(diff));
                user.getPlayer().playSound(user.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1F, 2F);
            } else if (diff < 0) {
                user.sendMessage("boxed.size-decreased", TextVariables.NUMBER, String.valueOf(Math.abs(diff)));
                user.getPlayer().playSound(user.getLocation(), Sound.ENTITY_VILLAGER_DEATH, 1F, 2F);
            }
        }
    }

    private void informPlayer(User user, NamespacedKey key, int score) {
        user.getPlayer().playSound(user.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1F, 2F);
        user.sendMessage("boxed.completed", TextVariables.NAME,  keyToString(user, key));
        user.sendMessage("boxed.size-changed", TextVariables.NUMBER, String.valueOf(score));

    }

    private String keyToString(User user, NamespacedKey key) {
        String adv = user.getTranslationOrNothing("boxed.advancements." + key.toString());
        if (adv.isEmpty()) {
            adv = Util.prettifyText(key.getKey().substring(key.getKey().lastIndexOf("/") + 1, key.getKey().length()));
        }
        return adv;
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) {
        if (!Util.sameWorld(e.getPlayer().getWorld(), addon.getOverWorld())) {
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


    private void giveAdv(Player player, Advancement adv) {
        if (adv != null && !player.getAdvancementProgress(adv).isDone()) {
            adv.getCriteria().forEach(player.getAdvancementProgress(adv)::awardCriteria);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent e) {
        User user = User.getInstance(e.getPlayer());
        if (Util.sameWorld(addon.getOverWorld(), e.getPlayer().getWorld())) {
            // Set advancements to same as island
            syncAdvancements(user);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerEnterWorld(PlayerChangedWorldEvent e) {
        User user = User.getInstance(e.getPlayer());
        if (Util.sameWorld(addon.getOverWorld(), e.getPlayer().getWorld())) {
            // Set advancements to same as island
            syncAdvancements(user);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTeamJoinTime(TeamJoinedEvent e) {
        User user = User.getInstance(e.getPlayerUUID());
        if (addon.getSettings().isOnJoinResetAdvancements() && user.isOnline()
                && addon.getOverWorld().equals(Util.getWorld(user.getWorld()))) {
            // Clear and set advancements
            clearAndSetAdv(user, addon.getSettings().isOnJoinResetAdvancements(), addon.getSettings().getOnJoinGrantAdvancements());
            // Set advancements to same as island
            syncAdvancements(user);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTeamLeaveTime(TeamLeaveEvent e) {
        User user = User.getInstance(e.getPlayerUUID());
        if (addon.getSettings().isOnJoinResetAdvancements() && user.isOnline()
                && addon.getOverWorld().equals(Util.getWorld(user.getWorld()))) {
            // Clear and set advancements
            clearAndSetAdv(user, addon.getSettings().isOnLeaveResetAdvancements(), addon.getSettings().getOnLeaveGrantAdvancements());

        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFirstTime(IslandNewIslandEvent e) {
        clearAndSetAdv(User.getInstance(e.getPlayerUUID()), addon.getSettings().isOnJoinResetAdvancements(), addon.getSettings().getOnJoinGrantAdvancements());
    }


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

    @SuppressWarnings("deprecation")
    private void clearAdv(User user) {
        // Clear stats
        // Statistics
        Arrays.stream(Statistic.values()).forEach(s -> {
            switch(s.getType()) {
            case BLOCK:
                for (Material m: Material.values()) {
                    if (m.isBlock() && !m.isLegacy()) {
                        user.getPlayer().setStatistic(s, m, 0);
                    }
                }
                break;
            case ITEM:
                for (Material m: Material.values()) {
                    if (m.isItem() && !m.isLegacy()) {
                        user.getPlayer().setStatistic(s, m, 0);
                    }
                }
                break;
            case ENTITY:
                for (EntityType en: EntityType.values()) {
                    if (en.isAlive()) {
                        user.getPlayer().setStatistic(s, en, 0);
                    }
                }
                break;
            case UNTYPED:
                user.getPlayer().setStatistic(s, 0);
                break;
            default:
                break;

            }

        });
        // Clear advancements
        Iterator<Advancement> it = Bukkit.advancementIterator();
        while (it.hasNext()) {
            Advancement a = it.next();
            AdvancementProgress p = user.getPlayer().getAdvancementProgress(a);
            p.getAwardedCriteria().forEach(p::revokeCriteria);
        }

    }

}
