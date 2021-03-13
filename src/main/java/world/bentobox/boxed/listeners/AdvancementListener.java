package world.bentobox.boxed.listeners;

import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import world.bentobox.bentobox.api.events.island.IslandNewIslandEvent;
import world.bentobox.bentobox.api.events.team.TeamJoinedEvent;
import world.bentobox.bentobox.api.events.team.TeamLeaveEvent;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
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
            int score = addon.getAdvManager().addAvancement(e.getPlayer(), e.getAdvancement());
            if (score != 0) {
                User user = User.getInstance(e.getPlayer());
                Bukkit.getScheduler().runTask(addon.getPlugin(), () -> {
                    e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1F, 2F);
                    String adv = user.getTranslation("boxed.advancements." + e.getAdvancement().getKey().toString());
                    if (adv.isEmpty()) {
                        adv = Util.prettifyText(e.getAdvancement().getKey().getKey().substring(e.getAdvancement().getKey().getKey().lastIndexOf("/") + 1, e.getAdvancement().getKey().getKey().length()));
                    }
                    user.sendMessage("boxed.completed", TextVariables.NAME,  adv);
                    user.sendMessage("boxed.size-changed", TextVariables.NUMBER, String.valueOf(score));
                });
            }
        }
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
    public void onTeamJoinTime(TeamJoinedEvent e) {
        User user = User.getInstance(e.getPlayerUUID());
        if (addon.getSettings().isOnJoinResetAdvancements() && user.isOnline()
                && addon.getOverWorld().equals(Util.getWorld(user.getWorld()))) {
            // Clear and set advancements
            clearAndSetAdv(user, addon.getSettings().isOnJoinResetAdvancements(), addon.getSettings().getOnJoinGrantAdvancements());
            
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
            // Clear advancements
            Iterator<Advancement> it = Bukkit.advancementIterator();
            while (it.hasNext()) {
                Advancement a = it.next();
                AdvancementProgress p = user.getPlayer().getAdvancementProgress(a);
                p.getAwardedCriteria().forEach(p::revokeCriteria);
            }
        }
        // Grant advancements
        list.forEach(k -> {
            Iterator<Advancement> it = Bukkit.advancementIterator();
            while (it.hasNext()) {
                Advancement a = it.next();                
                if (a.getKey().toString().equals(k)) {
                    // Award                    
                    a.getCriteria().forEach(user.getPlayer().getAdvancementProgress(a)::awardCriteria);
                }
            }
        });
        
    } 
    
}
