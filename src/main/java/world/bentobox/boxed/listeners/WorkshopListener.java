package world.bentobox.boxed.listeners;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.workshop.WorkshopManager;

/**
 * The rules for players in the workshop worlds. Everyone who comes in is put in creative
 * mode and gets their old game mode back when they leave. Anyone who dies there comes back
 * where they died. Players who were in a workshop that was removed while they were away
 * are sent to their box when they return.
 *
 * @author tastybento
 */
public class WorkshopListener implements Listener {

    private final Boxed addon;
    private final WorkshopManager manager;
    private final Map<UUID, Location> deaths = new HashMap<>();

    /**
     * @param addon Boxed
     * @param manager the workshop
     */
    public WorkshopListener(Boxed addon, WorkshopManager manager) {
        this.addon = addon;
        this.manager = manager;
    }

    // Other plugins may set the game mode when a player changes world, so this goes last
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent e) {
        Player player = e.getPlayer();
        boolean was = manager.isWorkshopWorld(e.getFrom());
        boolean is = manager.isWorkshopWorld(player.getWorld());
        if (is && !was) {
            manager.playerEntered(player, player.getGameMode());
            player.setGameMode(GameMode.CREATIVE);
        } else if (was && !is) {
            GameMode mode = manager.playerLeft(player);
            player.setGameMode(mode == null ? Bukkit.getDefaultGameMode() : mode);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        if (manager.isWorkshopWorld(player.getWorld())) {
            // They were here when they left, or the server was stopped
            manager.playerEntered(player, Bukkit.getDefaultGameMode());
            player.setGameMode(GameMode.CREATIVE);
            return;
        }
        GameMode mode = manager.takeDisplaced(player.getUniqueId());
        if (mode != null) {
            // The workshop they were in has gone
            player.setGameMode(mode);
            Bukkit.getScheduler().runTask(addon.getPlugin(), () -> manager.sendOut(player));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player player = e.getEntity();
        if (manager.isWorkshopWorld(player.getWorld())) {
            deaths.put(player.getUniqueId(), player.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        Location death = deaths.remove(e.getPlayer().getUniqueId());
        if (death != null && manager.isWorkshopWorld(death.getWorld())) {
            e.setRespawnLocation(death);
            // The game mode cannot be changed until the player is back in the world
            Bukkit.getScheduler().runTask(addon.getPlugin(), () -> e.getPlayer().setGameMode(GameMode.CREATIVE));
        }
    }
}
