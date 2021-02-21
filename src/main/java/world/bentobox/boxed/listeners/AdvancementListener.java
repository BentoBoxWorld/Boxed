package world.bentobox.boxed.listeners;

import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

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


    /**
     * @param addon
     */
    public AdvancementListener(Boxed addon) {
        this.addon = addon;
    }


    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        if (e.getPlayer().getWorld().equals(addon.getOverWorld())) {
            int score = addon.getAdvManager().addAvancement(e.getPlayer(), e.getAdvancement());
            if (score != 0) {
                User user = User.getInstance(e.getPlayer());
                e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1F, 2F);
                String adv = Util.prettifyText(e.getAdvancement().getKey().getKey().substring(e.getAdvancement().getKey().getKey().lastIndexOf("/") + 1, e.getAdvancement().getKey().getKey().length()));

                user.sendMessage("boxed.completed", TextVariables.NAME,  adv);
                user.sendMessage("boxed.size-changed", TextVariables.NUMBER, String.valueOf(score));
            }
        }
    }


}
