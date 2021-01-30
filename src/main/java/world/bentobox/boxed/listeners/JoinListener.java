package world.bentobox.boxed.listeners;

import java.util.Iterator;

import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.boxed.Boxed;

/**
 * @author tastybento
 *
 */
public class JoinListener implements Listener {

    private Boxed addon;

    public JoinListener(Boxed addon) {
        this.addon = addon;
    }

    @EventHandler
    public void onJoinEvent(PlayerJoinEvent e) {
        Iterator<Advancement> it = Bukkit.advancementIterator();
        while (it.hasNext()) {
            Advancement a = it.next();
            if (!a.getKey().getKey().startsWith("recipe")) {
                AdvancementProgress progress = e.getPlayer().getAdvancementProgress(a);
                BentoBox.getInstance().logDebug(a.getKey() + " " + progress.isDone());
                BentoBox.getInstance().logDebug("Awarded criteria");
                progress.getAwardedCriteria().forEach(s -> BentoBox.getInstance().logDebug(s + " " + progress.getDateAwarded(s)));

                BentoBox.getInstance().logDebug("Remaining criteria " + progress.getRemainingCriteria());
                progress.getAwardedCriteria().forEach(progress::revokeCriteria);
            }

        }
    }
}
