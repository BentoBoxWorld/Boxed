package world.bentobox.boxed.listeners;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import world.bentobox.bentobox.api.events.island.IslandCreatedEvent;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;
import world.bentobox.boxed.Boxed;

/**
 * @author tastybento
 * Place structures in areas after they are created
 */
public class NewAreaListener implements Listener {

    private final Boxed addon;
    private final YamlConfiguration config;

    /**
     * @param addon addon
     */
    public NewAreaListener(Boxed addon) {
        this.addon = addon;
        // Load the config
        File structureFile = new File(addon.getDataFolder(), "structures.yml");
        // Check if it exists and if not, save it from the jar
        if (!structureFile.exists()) {
            addon.saveResource("structures.yml", true);
        }
        config = YamlConfiguration.loadConfiguration(structureFile);

    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onIslandCreated(IslandCreatedEvent e) {
        addon.log(e.getEventName());
        Island island = e.getIsland();
        addon.getPlugin().getIWM().getAddon(island.getWorld()).ifPresent(gma -> addon.log(gma.getDescription().getName()));
        // Check if this island is in this game
        /*
        if (!addon.getPlugin().getIWM().getAddon(island.getWorld()).map(gma -> gma.equals(addon)).orElse(false)) {
            // Not correct addon
            return;
        }*/
        Location center = island.getProtectionCenter();
        ConfigurationSection section = config.getConfigurationSection("structures.new");
        if (section == null) {
            addon.logError("structures.new not found");
            return;
        }
        for (String structure : section.getKeys(false)) {
            addon.log(structure);
            String value = section.getString(structure,"");
            // Extract coords
            String[] coords = value.split(",");
            if (coords.length == 3) {
                int x = Integer.valueOf(coords[0]) + center.getBlockX();
                int y = Integer.valueOf(coords[1]) + center.getBlockY();
                int z = Integer.valueOf(coords[2]) + center.getBlockZ();
                Util.getChunkAtAsync(center.getWorld(), x >> 4, z >> 4, true).thenAccept(c -> {
                    // Run command
                    String cmd = "place structure minecraft:" + structure + " "
                            + x + " "
                            + y + " "
                            + z + " ";
                    Bukkit.getScheduler().runTaskLater(addon.getPlugin(), () -> {
                        addon.log("run command " + cmd);
                        addon.getServer().dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    }, 1000);
                });

            } else {
                addon.logError("Structure file syntax error: " + structure + " " + value);
            }

        }


    }



}
