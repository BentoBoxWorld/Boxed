package world.bentobox.boxed.listeners;

import java.io.File;
import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import org.bukkit.block.data.BlockData;
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
import org.bukkit.loot.LootTables;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.structure.Structure;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import com.google.common.base.Enums;
import com.google.gson.Gson;

import net.minecraft.core.BlockPosition;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.protocol.game.PacketPlayOutTileEntityData;
import net.minecraft.world.level.block.entity.TileEntity;
import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.events.BentoBoxReadyEvent;
import world.bentobox.bentobox.api.events.island.IslandCreatedEvent;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Pair;
import world.bentobox.bentobox.util.Util;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.objects.BoxedJigsawBlock;
import world.bentobox.boxed.objects.BoxedStructureBlock;

/**
 * @author tastybento
 * Place structures in areas after they are created
 */
public class NewAreaListener implements Listener {

    private final Boxed addon;
    private File structureFile;
    private Queue<Item> itemsToBuild = new LinkedList<>();
    private boolean pasting;
    private static Gson gson = new Gson();
    private record Item(String name, Structure structure, Location location, StructureRotation rot, Mirror mirror) {};
    Pair<Integer, Integer> min = new Pair<Integer, Integer>(0,0);
    Pair<Integer, Integer> max = new Pair<Integer, Integer>(0,0);
    private BukkitTask task;
    private int i;


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

    /**
     * Workaround for https://hub.spigotmc.org/jira/browse/SPIGOT-7288
     * @param event event
     */
    @EventHandler()
    public void onBentoBoxReady(BentoBoxReadyEvent event) {
        World seedBase = Bukkit.getWorld("seed_base");
        if (seedBase == null) {
            addon.logError("No seed base world!");
            return;
        }
        File templateFile = new File(addon.getDataFolder(), "templates.yml");
        if (templateFile.exists()) {
            YamlConfiguration loader = YamlConfiguration.loadConfiguration(templateFile);
            List<String> list = loader.getStringList("templates");
            task = Bukkit.getScheduler().runTaskTimer(addon.getPlugin(), () -> {
                if (i == list.size()) {
                    task.cancel();
                    return;
                }
                String struct = list.get(i++);
                Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "execute in " + seedBase.getName() + " run place template " + struct + " 10000 120 10000");



            }, 0, 10);
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
            Mirror mirror = Mirror.NONE;
            String name = section.getString(vector);
            // Check for rotation
            String[] split = name.split(",");
            if (split.length > 1) {
                // Rotation
                rot = Enums.getIfPresent(StructureRotation.class, split[1].strip().toUpperCase(Locale.ENGLISH)).or(StructureRotation.NONE); 
                name = split[0];
            }
            if (split.length == 3) {
                // Mirror
                mirror = Enums.getIfPresent(Mirror.class, split[1].strip().toUpperCase(Locale.ENGLISH)).or(Mirror.NONE); 
            }
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
                itemsToBuild.add(new Item(name, s, l, rot, mirror));
            } else {
                addon.logError("Structure file syntax error: " + name + " " + vector);
            }
        }
    }


    private void LoadChunksAsync(Item item) {
        pasting = true;
        item.structure().place(item.location(), true, item.rot(), item.mirror(), -1, 1, new Random());
        addon.log(item.name() + " placed at " + item.location().getWorld().getName() + " " + Util.xyz(item.location().toVector()));
        // Find it
        removeJigsaw(item.location(), item.structure(), item.rot());
        pasting = false;
    }

    /**
     * Removes Jigsaw blocks from a placed structure
     * @param loc - location where the structure was placed
     * @param structure - structure that was placed
     * @param structureRotation - rotation of structure
     */
    public static void removeJigsaw(Location loc, Structure structure, StructureRotation structureRotation) {
        Location otherCorner = switch (structureRotation) {

        case CLOCKWISE_180 -> loc.clone().add(new Vector(-structure.getSize().getX(), structure.getSize().getY(), -structure.getSize().getZ()));

        case CLOCKWISE_90 -> loc.clone().add(new Vector(-structure.getSize().getZ(), structure.getSize().getY(), structure.getSize().getX()));

        case COUNTERCLOCKWISE_90 -> loc.clone().add(new Vector(structure.getSize().getZ(), structure.getSize().getY(), -structure.getSize().getX()));

        case NONE -> loc.clone().add(new Vector(structure.getSize().getX(), structure.getSize().getY(), structure.getSize().getZ()));

        };

        BoundingBox bb = BoundingBox.of(loc, otherCorner);
        for (int x = (int) bb.getMinX(); x <= bb.getMaxX(); x++) {
            for (int y = (int) bb.getMinY(); y <= bb.getMaxY(); y++) {
                for (int z = (int) bb.getMinZ(); z <= bb.getMaxZ(); z++) {
                    Block b = loc.getWorld().getBlockAt(x, y, z);
                    if (b.getType().equals(Material.JIGSAW)) {
                        // I would like to read the data from the block and do something with it!
                        processJigsaw(b);
                    } else if (b.getType().equals(Material.STRUCTURE_BLOCK)) {
                        processStructureBlock(b);
                    }
                }
            } 
        }

    }


    private static void processStructureBlock(Block b) {
        // I would like to read the data from the block an do something with it!
        String data = nmsData(b);
        BoxedStructureBlock bsb = gson.fromJson(data, BoxedStructureBlock.class);
        b.setType(Material.STRUCTURE_VOID);
        Enums.getIfPresent(EntityType.class, bsb.getMetadata().toUpperCase(Locale.ENGLISH)).toJavaUtil()
        .ifPresent(type -> b.getWorld().spawnEntity(b.getRelative(BlockFace.UP).getLocation(), type));
        if (bsb.getMetadata().contains("chest")) {
            Block downBlock = b.getRelative(BlockFace.DOWN);
            if (downBlock.getType().equals(Material.CHEST)) {
                Chest chest = (Chest)downBlock.getState();
                // TODO: for now just give treasure
                chest.setLootTable(LootTables.BURIED_TREASURE.getLootTable());
                chest.update();
                if (chest.getBlockData() instanceof Waterlogged wl) {
                    if (wl.isWaterlogged()) {
                        b.setType(Material.WATER);
                    }
                }
            }
        }
    }

    private static final Map<Integer, EntityType> BUTCHER_ANIMALS = Map.of(0, EntityType.COW, 1, EntityType.SHEEP, 2, EntityType.PIG);
    private static void processJigsaw(Block b) {
        String data = nmsData(b);                      
        BoxedJigsawBlock bjb = gson.fromJson(data, BoxedJigsawBlock.class);
        BentoBox.getInstance().logDebug("Jigsaw: " + bjb);
        BlockData bd = Bukkit.createBlockData(bjb.getFinal_state());
        b.setType(bd.getMaterial());
        EntityType type = 
                switch (bjb.getPool()) {
                case "minecraft:bastion/mobs/piglin" -> EntityType.PIGLIN;
                case "minecraft:bastion/mobs/hoglin" -> EntityType.HOGLIN;
                case "minecraft:bastion/mobs/piglin_melee" -> EntityType.PIGLIN_BRUTE;
                case "minecraft:village/common/cats" -> EntityType.CAT;
                case "minecraft:village/common/horses" -> EntityType.HORSE;
                case "minecraft:village/common/sheep" -> EntityType.SHEEP;
                case "minecraft:village/common/pigs" -> EntityType.PIG;
                case "minecraft:village/common/cows" -> EntityType.COW;
                case "minecraft:village/common/iron_golem" -> EntityType.IRON_GOLEM;
                case "minecraft:village/common/butcher_animals" -> BUTCHER_ANIMALS.get(new Random().nextInt(3));
                default -> null;
                };
                if (bjb.getPool().contains("zombie/villagers")) {
                    type = EntityType.ZOMBIE_VILLAGER;
                } else if (bjb.getPool().contains("villagers")) {
                    type = EntityType.VILLAGER;
                }
                // Spawn it
                if (type != null && b.getWorld().spawnEntity(b.getRelative(BlockFace.UP).getLocation(), type) != null) {            
                    BentoBox.getInstance().logDebug("Spawned a " + type + " at " + b.getRelative(BlockFace.UP).getLocation()); 
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
