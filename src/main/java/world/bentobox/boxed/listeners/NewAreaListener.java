package world.bentobox.boxed.listeners;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.structure.Structure;
import org.bukkit.util.BoundingBox;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.events.island.IslandCreatedEvent;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Pair;
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
    private record Item(World w, List<Pair<Integer, Integer>> cs, Structure structure, Location location) {};
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
        // Try to build something every 5 seconds
        Bukkit.getScheduler().runTaskTimer(addon.getPlugin(), () -> BuildItem(), 20, 100);
    }

    private void BuildItem() {
        // Only kick off a build if there is something to build and something isn't already being built
        if (!pasting && !itemsToBuild.isEmpty()) {
            // Build item
            Item item = itemsToBuild.poll();
            LoadChunksAsync(item);
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
                // Load Structure
                Structure s = Bukkit.getStructureManager().loadStructure(NamespacedKey.fromString("minecraft:" + structure));
                if (s == null) {
                    BentoBox.getInstance().logError("Could not load " + structure);
                    return;
                }
                Location l = new Location(world, x, y, z);
                itemsToBuild.add(new Item(world, cs, s, l));
            } else {
                addon.logError("Structure file syntax error: " + structure + " " + key);
            }
        }
    }


    private void LoadChunksAsync(Item item) {
        pasting = true;
        item.structure().place(item.location(), true, StructureRotation.NONE, Mirror.NONE, -1, 1, new Random());
        addon.log("Structure placed at " + item.location);
        // Find it
        removeJigsaw(item.location(), item.structure());
        pasting = false;
    }

    /**
     * Removes Jigsaw blocks from a placed structure
     * @param loc - location where the structure was placed
     * @param structure - structure that was placed
     */
    public static void removeJigsaw(Location loc, Structure structure) {
        BoundingBox bb = BoundingBox.of(loc, structure.getSize().getX(), structure.getSize().getY(), structure.getSize().getZ());
        for (int x = (int) bb.getMinX(); x < bb.getMaxX(); x++) {
            for (int y = (int) bb.getMinY(); y < bb.getMaxY(); y++) {
                for (int z = (int) bb.getMinZ(); z < bb.getMaxZ(); z++) {
                    Block b = loc.getWorld().getBlockAt(x, y, z);
                    if (b.getType().equals(Material.JIGSAW)) {
                        b.setType(Material.STRUCTURE_VOID);
                    } else if (b.getType().equals(Material.STRUCTURE_BLOCK)) {
                        // I would like to read the data from the block an do something with it!
                        b.setType(Material.STRUCTURE_VOID);
                    }
                }
            } 
        }
        
    }



}
