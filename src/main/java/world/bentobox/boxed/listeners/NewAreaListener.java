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
import org.bukkit.craftbukkit.v1_19_R3.CraftWorld;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.loot.LootTables;
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
import world.bentobox.bentobox.api.events.island.IslandResettedEvent;
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

    private static final List<BlockFace> CARDINALS = List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST);
    private final Boxed addon;
    private File structureFile;
    private Queue<Item> itemsToBuild = new LinkedList<>();
    private boolean pasting;
    private static Gson gson = new Gson();
    private record Item(String name, Structure structure, Location location, StructureRotation rot, Mirror mirror) {};
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

    /**
     * Build a list of structures
     * @param event event
     */
    @EventHandler()
    public void onBentoBoxReady(BentoBoxReadyEvent event) {
        addon.saveResource("templates.yml", false);
        File templateFile = new File(addon.getDataFolder(), "templates.yml");
        if (templateFile.exists()) {
            YamlConfiguration loader = YamlConfiguration.loadConfiguration(templateFile);
            List<String> list = loader.getStringList("templates");
            for (String struct : list) {
                Structure s = Bukkit.getStructureManager().loadStructure(NamespacedKey.fromString(struct));
                if (s == null) {
                    //addon.log("Now loading group from: " + struct);
                } 
            }
        }

    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onIslandCreated(IslandCreatedEvent event) {
        setUpIsland(event.getIsland());
    }
    
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onIslandReset(IslandResettedEvent event) {
        setUpIsland(event.getIsland());
    }
    
    private void setUpIsland(Island island) {
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
                addon.logError("Structure file syntax error: " + vector + ": " + value);
            }
        }
    }

    private void LoadChunksAsync(Item item) {
        pasting = true;
        item.structure().place(item.location(), true, item.rot(), item.mirror(), -1, 1, new Random());
        addon.log(item.name() + " placed at " + item.location().getWorld().getName() + " " + Util.xyz(item.location().toVector()));
        // Find it
        removeJigsaw(item.location(), item.structure(), item.rot(), item.name());
        pasting = false;
    }

    /**
     * Removes Jigsaw blocks from a placed structure. Fills underwater ruins with water.
     * @param loc - location where the structure was placed
     * @param structure - structure that was placed
     * @param structureRotation - rotation of structure
     * @param key 
     */
    public static void removeJigsaw(Location loc, Structure structure, StructureRotation structureRotation, String key) {
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
                        processJigsaw(b, structureRotation);
                    } else if (b.getType().equals(Material.STRUCTURE_BLOCK)) {
                        processStructureBlock(b);
                    }
                    // Set water blocks for underwater ruins
                    if (key.contains("underwater_ruin") && b.getType().equals(Material.AIR)) {
                        b.setType(Material.WATER);
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
    private static void processJigsaw(Block b, StructureRotation structureRotation) {
        String data = nmsData(b);                      
        BoxedJigsawBlock bjb = gson.fromJson(data, BoxedJigsawBlock.class);
        //BentoBox.getInstance().logDebug("Jigsaw: " + bjb);
        //BentoBox.getInstance().logDebug("FinalState: " + bjb.getFinal_state());
        String finalState = correctDirection(bjb.getFinal_state(), structureRotation);
        //BentoBox.getInstance().logDebug("FinalState after rotation: " + finalState);
        BlockData bd = Bukkit.createBlockData(finalState);
        b.setBlockData(bd);
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
                case "minecraft:village/common/animals" -> BUTCHER_ANIMALS.get(new Random().nextInt(3));
                default -> null;
                };
                if (bjb.getPool().contains("zombie/villagers")) {
                    type = EntityType.ZOMBIE_VILLAGER;
                } else if (bjb.getPool().contains("villagers")) {
                    type = EntityType.VILLAGER;
                }
                // Spawn it
                if (type != null && b.getWorld().spawnEntity(b.getRelative(BlockFace.UP).getLocation(), type) != null) {            
                    //BentoBox.getInstance().logDebug("Spawned a " + type + " at " + b.getRelative(BlockFace.UP).getLocation()); 
                }

    }

    /**
     * Corrects the direction of a block based on the structure's rotation
     * @param finalState - the final block state of the block, which may include a facing: direction
     * @param sr - the structure's rotation
     * @return a rewritten blockstate with the updated direction, if required
     */
    private static String correctDirection(String finalState, StructureRotation sr) {
        if (sr.equals(StructureRotation.NONE)) {
            // No change
            return finalState;
        }
        BlockFace oldDirection = getDirection(finalState);
        BlockFace newDirection = getNewDirection(oldDirection, sr);
        if (newDirection.equals(BlockFace.SELF)) {
            // No change - shouldn't happen, but just in case
            return finalState;
        }
        return finalState.replace(oldDirection.name().toLowerCase(Locale.ENGLISH), newDirection.name().toLowerCase(Locale.ENGLISH));

    }

    /**
     * Adjusts the direction based on the StructureRotation
     * @param oldDirection the old direction to adjust
     * @param sr the structure rotation
     * @return the new direction, or SELF if something weird happens
     */
    private static BlockFace getNewDirection(BlockFace oldDirection, StructureRotation sr) {
        if (sr.equals(StructureRotation.CLOCKWISE_180)) {
            return oldDirection.getOppositeFace();
        } else if (sr.equals(StructureRotation.CLOCKWISE_90)) {
            return switch(oldDirection) {
            case EAST -> BlockFace.SOUTH;
            case NORTH -> BlockFace.EAST;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> BlockFace.SELF;            
            };
        } else if (sr.equals(StructureRotation.COUNTERCLOCKWISE_90)) {
            return switch(oldDirection) {
            case EAST -> BlockFace.NORTH;
            case NORTH -> BlockFace.WEST;
            case SOUTH -> BlockFace.EAST;
            case WEST -> BlockFace.SOUTH;
            default -> BlockFace.SELF;            
            };
        }   
        return BlockFace.SELF;
    }

    /**
     * Looks for north, south, east, west in the blockstate.
     * @param finalState - the final block state of the block
     * @return direction, if found, otherwise SELF
     */
    private static BlockFace getDirection(String finalState) {
        return CARDINALS.stream().filter(bf -> finalState.contains(bf.name().toLowerCase(Locale.ENGLISH))).findFirst().orElse(BlockFace.SELF);
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
