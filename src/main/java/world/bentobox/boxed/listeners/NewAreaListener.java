package world.bentobox.boxed.listeners;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import world.bentobox.bentobox.api.events.island.IslandCreatedEvent;
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
    Pair<Integer, Integer> min = new Pair<Integer, Integer>(0,0);
    Pair<Integer, Integer> max = new Pair<Integer, Integer>(0,0);


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
            String key = section.getString(structure,"");
            // Extract coords
            String[] value = key.split(",");
            if (value.length == 4) {
                Environment env = Environment.valueOf(value[0].toUpperCase(Locale.ENGLISH).strip());
                World world = env.equals(Environment.NORMAL) ? addon.getOverWorld() : addon.getNetherWorld();
                int x = Integer.valueOf(value[1].strip()) + center.getBlockX();
                int y = Integer.valueOf(value[2].strip());
                int z = Integer.valueOf(value[3].strip()) + center.getBlockZ();
                List<Pair<Integer, Integer>> cs = new ArrayList<>();
                int size = 10;
                for (int cx = (x >> 4) - size; cx < (x >>4) + size; cx++) {
                    for (int cz = (z >> 4) - size; cz < (z >>4) + size; cz++) {
                        cs.add(new Pair<>(cx, cz));
                    }
                }
                // Make command
                String cmd = "execute in " + world.getName() + " run place "+ string + " minecraft:" + structure + " "
                        + x + " "
                        + y + " "
                        + z + " ";
                itemsToBuild.add(new Item(world, cs, cmd));
            } else {
                addon.logError("Structure file syntax error: " + structure + " " + key);
            }
        }
    }


    private void LoadChunksAsync(World w, List<Pair<Integer, Integer>> cs, int i, String cmd) {
        pasting = true;
        int total = cs.size();

        //addon.log("Loading chunk async " + i);
        if (i < total) {
            if (i == 0) {
                min = new Pair<>(cs.get(0).x, cs.get(0).z);
                max = new Pair<>(cs.get(0).x, cs.get(0).z);
            }
            if (cs.get(i).x < min.x || cs.get(i).z < min.z) {
                min = cs.get(i);
            }
            if (cs.get(i).x > min.x || cs.get(i).z > min.z) {
                max = cs.get(i);
            }
            Util.getChunkAtAsync(w, cs.get(i).x, cs.get(i).z, true).thenAccept(c -> {
                LoadChunksAsync(w, cs, i + 1, cmd);

            });
        } else {
            addon.log("Complete");
            addon.log("Loaded chunks in " + w.getName() + " min " + (min.x << 4) + " " + (min.z << 4) + " to " +
                    (max.x << 4) + " " + (max.z << 4));
            addon.log("run command " + cmd);
            Bukkit.getScheduler().runTaskLater(addon.getPlugin(), () -> {
                addon.log("Comand success = " + addon.getServer().dispatchCommand(Bukkit.getConsoleSender(), cmd));
                pasting = false;
            }, 20L);
        }
    }


}
