package world.bentobox.boxed.listeners;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import world.bentobox.bentobox.api.events.island.IslandCreatedEvent;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Pair;
import world.bentobox.bentobox.util.Util;
import world.bentobox.boxed.Boxed;

/**
 * @author tastybento
 * Place structures in areas after they are created
 */
public class NewAreaListener implements Listener {

    private final Boxed addon;
    private File structureFile;
    private Queue<Item> itemsToBuild = new LinkedList<>();
    private boolean pasting;
    private record Item(World w, List<Pair<Integer, Integer>> cs, String cmd) {};


    /**
     * @param addon addon
     */
    public NewAreaListener(Boxed addon) {
        this.addon = addon;
        // Load the config
        structureFile = new File(addon.getDataFolder(), "structures.yml");
        // Check if it exists and if not, save it from the jar
        if (!structureFile.exists()) {
            addon.saveResource("structures.yml", true);
        }
        // Try to build something every 10 seconds
        Bukkit.getScheduler().runTaskTimer(addon.getPlugin(), () -> BuildItem(), 20, 200);
    }

    private void BuildItem() {
        // Only kick off a build if there is something to build and something isn't already being built
        if (!pasting && !itemsToBuild.isEmpty()) {
            // Build item
            Item item = itemsToBuild.poll();
            LoadChunksAsync(item.w, item.cs, 0, item.cmd);
        }

    }


    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onIslandCreated(IslandCreatedEvent e) {
        // Load the latest config so that admins can change it on the fly without reloading
        YamlConfiguration config = YamlConfiguration.loadConfiguration(structureFile);
        //addon.log(e.getEventName());
        Island island = e.getIsland();
        addon.getPlugin().getIWM().getAddon(island.getWorld()).ifPresent(gma -> addon.log(gma.getDescription().getName()));
        // Check if this island is in this game

        Location center = island.getProtectionCenter();

        ConfigurationSection section = config.getConfigurationSection("structures.new");
        if (section == null) {
            addon.log("structures.new not found");
        } else {
            place("structure",section, center);
        }

    }

    private void place(String string, ConfigurationSection section, Location center) {
        // Loop through the structures in the file - there could be more than one
        for (String structure : section.getKeys(false)) {
            addon.log(structure);
            String value = section.getString(structure,"");
            // Extract coords
            String[] coords = value.split(",");
            if (coords.length == 3) {
                int x = Integer.valueOf(coords[0]) + center.getBlockX();
                int y = Integer.valueOf(coords[1]);
                int z = Integer.valueOf(coords[2]) + center.getBlockZ();
                List<Pair<Integer, Integer>> cs = new ArrayList<>();
                int size = 10;
                for (int cx = (x >> 4) - size; cx < (x >>4) + size; cx++) {
                    for (int cz = (z >> 4) - size; cz < (z >>4) + size; cz++) {
                        cs.add(new Pair<>(cx, cz));
                    }
                }
                // Make command
                String cmd = "execute in " + center.getWorld().getName() + " run place "+ string + " minecraft:" + structure + " "
                        + x + " "
                        + y + " "
                        + z + " ";
                itemsToBuild.add(new Item(center.getWorld(), cs, cmd));
            } else {
                addon.logError("Structure file syntax error: " + structure + " " + value);
            }
        }
    }


    private void LoadChunksAsync(World w, List<Pair<Integer, Integer>> cs, int i, String cmd) {
        pasting = true;
        int total = cs.size();
        //addon.log("Loading chunk async " + i);
        if (i < total) {
            Util.getChunkAtAsync(w, cs.get(i).x, cs.get(i).z, true).thenAccept(c -> {
                //addon.log("Loaded chunk " + c.getX() + " " + c.getZ());
                LoadChunksAsync(w, cs, i + 1, cmd);

            });
        } else {
            addon.log("Complete");
            addon.log("run command " + cmd);
            Bukkit.getScheduler().runTaskLater(addon.getPlugin(), () -> {
                addon.log("Comand success = " + addon.getServer().dispatchCommand(Bukkit.getConsoleSender(), cmd));
                pasting = false;
            }, 20L);
        }
    }


}
