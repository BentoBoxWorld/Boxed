package world.bentobox.boxed.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

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


    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        if (e.getPlayer().getWorld().equals(addon.getOverWorld())
                && e.getAdvancement().getKey().getNamespace().equals("minecraft")
                && !e.getAdvancement().getKey().getKey().startsWith("recipes")) {
            addon.getIslands().getIslandAt(e.getPlayer().getLocation()).ifPresent(i -> {
                i.setProtectionRange(i.getProtectionRange() + 1);
                i.getPlayersOnIsland().forEach(p -> p.sendRawMessage("Area expanded! " + e.getAdvancement().getKey() + " " + e.getAdvancement().getCriteria()));
            });
        }
    }


}
