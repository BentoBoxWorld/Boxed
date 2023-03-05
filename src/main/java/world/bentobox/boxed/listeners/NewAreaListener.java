package world.bentobox.boxed.listeners;

import java.io.File;
import java.lang.reflect.Field;
import java.util.LinkedList;
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
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_19_R2.CraftWorld;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.structure.Structure;
import org.bukkit.util.BoundingBox;

import com.google.common.base.Enums;

import net.minecraft.core.BlockPosition;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.protocol.game.PacketPlayOutTileEntityData;
import net.minecraft.world.level.block.entity.TileEntity;
import world.bentobox.bentobox.BentoBox;
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
    private record Item(String name, Structure structure, Location location, StructureRotation rot) {};
    Pair<Integer, Integer> min = new Pair<Integer, Integer>(0,0);
    Pair<Integer, Integer> max = new Pair<Integer, Integer>(0,0);


    /**
     * @param addon addon
     */
    public NewAreaListener(Boxed addon) {
        this.addon = addon;
        addon.saveResource("structures.yml", false);
        // Load the config
        structureFile = new File(addon.getDataFolder(), "structures.yml");
        // Try to build something every second
        Bukkit.getScheduler().runTaskTimer(addon.getPlugin(), () -> BuildItem(), 20, 20);
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
    public void onIslandCreated(IslandCreatedEvent event) {
        Island island = event.getIsland();
     // Check if this island is in this game
        if (!(addon.inWorld(island.getWorld()))) {
            return;
        }
        // Load the latest config so that admins can change it on the fly without reloading
        YamlConfiguration config = YamlConfiguration.loadConfiguration(structureFile);
        Location center = island.getProtectionCenter();
        for (String env : config.getKeys(false)) {            
            Environment e = Enums.getIfPresent(Environment.class, env.toUpperCase(Locale.ENGLISH)).orNull();
            if (e == null) {
                addon.logError("Error in structures.yml - unknown environment " + env);
            } else {
                place("structure",config.getConfigurationSection(env), center, e);  
            }
        }
    }

    private void place(String string, ConfigurationSection section, Location center, Environment env) {
        World world = env.equals(Environment.NORMAL) ? addon.getOverWorld() : addon.getNetherWorld();
        // Loop through the structures in the file - there could be more than one
        for (String vector : section.getKeys(false)) {
            StructureRotation rot = StructureRotation.NONE;
            String name = section.getString(vector);
            // Load Structure
            Structure s = Bukkit.getStructureManager().loadStructure(NamespacedKey.fromString("minecraft:" + name));
            if (s == null) {
                BentoBox.getInstance().logError("Could not load " + name);
                return;
            }
            // Extract coords
            String[] value = vector.split(",");
            if (value.length > 2) {                
                int x = Integer.valueOf(value[0].strip()) + center.getBlockX();
                int y = Integer.valueOf(value[1].strip());
                int z = Integer.valueOf(value[2].strip()) + center.getBlockZ();                
                Location l = new Location(world, x, y, z);
                if (value.length > 3) {
                    // Rotation
                    rot = Enums.getIfPresent(StructureRotation.class, value[3].strip().toUpperCase(Locale.ENGLISH)).or(StructureRotation.NONE);                    
                }
                itemsToBuild.add(new Item(name, s, l, rot));
            } else {
                addon.logError("Structure file syntax error: " + name + " " + vector);
            }
        }
    }


    private void LoadChunksAsync(Item item) {
        pasting = true;
        item.structure().place(item.location(), true, item.rot(), Mirror.NONE, -1, 1, new Random());
        addon.log(item.name() + " placed at " + item.location().getWorld().getName() + " " + Util.xyz(item.location().toVector()));
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
                        String data = nmsData(b);
                        b.setType(Material.STRUCTURE_VOID);
                        BentoBox.getInstance().logDebug(data);
                        int index = data.indexOf("metadata:");
                        if (index > -1) {
                            data = data.substring(index + 10, data.length());
                            index = data.indexOf("\"");
                            data = data.substring(0, index);
                            BentoBox.getInstance().logDebug(data);
                            EntityType type = Enums.getIfPresent(EntityType.class, data.toUpperCase(Locale.ENGLISH)).orNull();                            
                            if (type != null) {
                                if (loc.getWorld().spawnEntity(loc, type) != null) {
                                    BentoBox.getInstance().logDebug("Spawned a " + type);
                                }
                            }
                            if (data.contains("chest")) {
                                Block downBlock = b.getRelative(BlockFace.DOWN);
                                if (downBlock.getType().equals(Material.CHEST)) {
                                   Chest chest = (Chest)downBlock.getState();
                                   chest.getInventory().addItem(new ItemStack(Material.GOLD_INGOT, 3));
                                   if (chest.getBlockData() instanceof Waterlogged wl) {
                                       if (wl.isWaterlogged()) {
                                           b.setType(Material.WATER);
                                       }
                                   }
                                }
                            }

                        }
                        
                    }
                }
            } 
        }

    }


    private static String nmsData(Block block) {
        Location w = block.getLocation();
        CraftWorld cw = (CraftWorld) w.getWorld(); // CraftWorld is NMS one
        // for 1.13+ (we have use WorldServer)
        TileEntity te = cw.getHandle().c_(new BlockPosition(w.getBlockX(), w.getBlockY(), w.getBlockZ()));
        try {
            PacketPlayOutTileEntityData packet = ((PacketPlayOutTileEntityData) te.h()); // get update packet from NMS object
            // here we should use reflection because "c" field isn't accessible
            Field f = packet.getClass().getDeclaredField("c"); // get field
            f.setAccessible(true); // make it available
            NBTTagCompound nbtTag = (NBTTagCompound) f.get(packet);
            return nbtTag.toString(); // this will show what you want
        } catch (Exception exc) {
            exc.printStackTrace();
        }
        return "Nothing";
    }

}
