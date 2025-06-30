package world.bentobox.boxed.listeners;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
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
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.loot.LootTables;
import org.bukkit.structure.Structure;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import com.google.common.base.Enums;
import com.google.gson.Gson;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.events.BentoBoxReadyEvent;
import world.bentobox.bentobox.api.events.island.IslandCreatedEvent;
import world.bentobox.bentobox.api.events.island.IslandDeleteEvent;
import world.bentobox.bentobox.api.events.island.IslandResettedEvent;
import world.bentobox.bentobox.database.Database;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Pair;
import world.bentobox.bentobox.util.Util;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.nms.AbstractMetaData;
import world.bentobox.boxed.objects.BoxedJigsawBlock;
import world.bentobox.boxed.objects.BoxedStructureBlock;
import world.bentobox.boxed.objects.IslandStructures;
import world.bentobox.boxed.objects.ToBePlacedStructures;
import world.bentobox.boxed.objects.ToBePlacedStructures.StructureRecord;

/**
 * @author tastybento Place structures in areas after they are created
 */
public class NewAreaListener implements Listener {

    private static final Map<Integer, EntityType> BUTCHER_ANIMALS = Map.of(0, EntityType.COW, 1, EntityType.SHEEP, 2,
            EntityType.PIG);
    private static final List<BlockFace> CARDINALS = List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
            BlockFace.WEST);
    private static final List<String> JAR_STRUCTURES = List.of("bee", "pillager", "polar_bear", "axolotl", "allay",
            "parrot", "frog");
    private static final List<String> STRUCTURES = List.of("ancient_city", "bastion_remnant", "bastion",
            "buried_treasure", "desert_pyramid", "end_city", "fortress", "igloo", "jungle_pyramid", "mansion",
            "mineshaft", "mineshaft_mesa", "monument", "nether_fossil", "ocean_ruin_cold", "ocean_ruin_warm",
            "pillager_outpost", "ruined_portal_desert", "ruined_portal_jungle", "ruined_portal_mountain",
            "ruined_portal_nether", "ruined_portal_ocean", "ruined_portal_swamp", "ruined_portal", "shipwreck_beached",
            "shipwreck", "stronghold", "swamp_hut", "village_desert", "village_plains", "village_savanna",
            "village_snowy", "village_taiga");
    private final Boxed addon;
    private final File structureFile;
    /**
     * Queue for structures that have been determined to be built now
     */
    private final Queue<StructureRecord> itemsToBuild = new LinkedList<>();

    /**
     * Store for structures that are pending being built, e.g., waiting until the chunk they are is in loaded
     */
    private final Map<Pair<Integer, Integer>, List<StructureRecord>> pending;

    /**
     * A cache of all structures that have been placed. Used to determine if players have entered them
     */
    private final Map<String, IslandStructures> islandStructureCache = new HashMap<>();

    private static final Random rand = new Random();
    private boolean pasting = true;
    private static final Gson gson = new Gson();
    private static final String TODO = "ToDo";
    private static final String COULD_NOT_LOAD = "Could not load ";
    // Database handler for structure data
    private final Database<IslandStructures> handler;
    private final Database<ToBePlacedStructures> toPlace;

    private static String bukkitVersion = "v" + Bukkit.getBukkitVersion().replace('.', '_').replace('-', '_');
    private static String pluginPackageName;

    /**
     * Constructor for NewAreaListener.
     * Initializes structure files, databases, and starts the structure printer.
     * 
     * @param addon The Boxed addon instance.
     */
    public NewAreaListener(Boxed addon) {
        this.addon = addon;
        pluginPackageName = addon.getClass().getPackage().getName();
        // Save the default structures file from the jar
        addon.saveResource("structures.yml", false);
        // Load the config
        structureFile = new File(addon.getDataFolder(), "structures.yml");
        // Get database ready
        handler = new Database<>(addon, IslandStructures.class);
        // Load the pending structures
        toPlace = new Database<>(addon, ToBePlacedStructures.class);
        pending = this.loadToDos().getReadyToBuild();
        // Try to build something
        runStructurePrinter();
    }

    /**
     * Runs a recurring task to build structures in the queue and register Jar structures.
     */
    private void runStructurePrinter() {
        // Set up recurring task
        Bukkit.getScheduler().runTaskTimer(addon.getPlugin(), this::buildStructure, 100, 60);
        // Run through all the structures in the Jar and register them with the server
        for (String js : JAR_STRUCTURES) {
            addon.saveResource("structures/" + js + ".nbt", false);
            File structureFile = new File(addon.getDataFolder(), "structures/" + js + ".nbt");
            try {
                Structure s = Bukkit.getStructureManager().loadStructure(structureFile);
                Bukkit.getStructureManager().registerStructure(NamespacedKey.fromString("minecraft:boxed/" + js), s);
                addon.log("Loaded " + js + ".nbt");
            } catch (IOException e) {
                addon.logError("Error trying to load " + structureFile.getAbsolutePath());
                addon.getPlugin().logStacktrace(e);
            }
        }

    }

    /**
     * Builds a structure from the queue if not already pasting.
     * Only one structure is built at a time.
     */
    private void buildStructure() {
        // Only kick off a build if there is something to build and something isn't
        // already being built
        if (!pasting && !itemsToBuild.isEmpty()) {
            // Build item
            StructureRecord item = itemsToBuild.poll();
            placeStructure(item);
        }
    }

    /**
     * Places a structure at the specified location and updates the island structure cache.
     * 
     * @param item The structure record to place.
     */
    private void placeStructure(StructureRecord item) {
        // Set the semaphore - only paste one at a time
        pasting = true;
        // Place the structure - this cannot be done async
        Structure structure = Bukkit.getStructureManager().loadStructure(NamespacedKey.fromString(item.structure()));
        if (structure == null) {
            BentoBox.getInstance().logError(COULD_NOT_LOAD + item.structure());
            return;
        }
        structure.place(item.location(), true, item.rot(), item.mirror(), -1, 1, rand);
        addon.log(item.name() + " placed at " + item.location().getWorld().getName() + " "
                + Util.xyz(item.location().toVector()));
        // Remove any jigsaw artifacts
        BoundingBox bb = removeJigsaw(item);
        // Store it for future reference
        addon.getIslands().getIslandAt(item.location()).map(Island::getUniqueId).ifPresent(id -> {
            //.log("Saved " + item.name());
            if (item.location().getWorld().getEnvironment().equals(Environment.NETHER)) {
                getIslandStructData(id).addNetherStructure(bb, item.name());
            } else {
                getIslandStructData(id).addStructure(bb, item.name());
            }
            handler.saveObjectAsync(getIslandStructData(id));
        });
        // Clear the semaphore
        pasting = false;
    }

    /**
     * Loads known structures from the templates file and registers them with the server.
     * 
     * @param event BentoBoxReadyEvent
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBentoBoxReady(BentoBoxReadyEvent event) {
        Bukkit.getScheduler().runTaskAsynchronously(addon.getPlugin(), () -> {
            addon.saveResource("templates.yml", false);
            File templateFile = new File(addon.getDataFolder(), "templates.yml");
            if (templateFile.exists()) {
                YamlConfiguration loader = YamlConfiguration.loadConfiguration(templateFile);
                List<String> list = loader.getStringList("templates");
                for (String struct : list) {
                    if (!struct.endsWith("/")) {
                        Bukkit.getStructureManager().loadStructure(NamespacedKey.fromString(struct));
                    }
                }
            }
            pasting = false; // Allow pasting
        });
    }

    /**
     * Adds items to the build queue when their chunk is loaded.
     * 
     * @param e ChunkLoadEvent
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent e) {
        Chunk chunk = e.getChunk();
        // Check if this island is in this game
        if (!(addon.inWorld(chunk.getWorld()))) {
            return;
        }
        Pair<Integer, Integer> chunkCoords = new Pair<Integer, Integer>(chunk.getX(), chunk.getZ());
        if (pending.containsKey(chunkCoords)) {
            Iterator<StructureRecord> it = pending.get(chunkCoords).iterator();
            while (it.hasNext()) {
                StructureRecord item = it.next();
                if (item.location().getWorld().equals(e.getWorld())) {
                    // Placing structure in itemsToBuild
                    this.itemsToBuild.add(item);
                    it.remove();
                }
            }
            // Save to latest to the database
            ToBePlacedStructures tbd = new ToBePlacedStructures();
            tbd.setReadyToBuild(pending);
            toPlace.saveObjectAsync(tbd);
        }
    }


    /**
     * Track if a player has entered a structure.
     * 
     * @param e PlayerMoveEvent
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent e) {
        // Ignore head movements
        if (!addon.inWorld(e.getFrom().getWorld()) || e.getFrom().toVector().equals(e.getTo().toVector())) {
            return;
        }
        // Check where the player is
        addon.getIslands().getIslandAt(e.getTo()).ifPresent(island -> {
            // See if island is in cache
            final String islandId = island.getUniqueId();
            IslandStructures is = getIslandStructData(islandId);
            // Check if player is in any of the structures
            Map<BoundingBox, String> structures = e.getTo().getWorld().getEnvironment().equals(Environment.NETHER)
                    ? is.getNetherStructureBoundingBoxMap()
                    : is.getStructureBoundingBoxMap();
            for (Map.Entry<BoundingBox, String> en : structures.entrySet()) {
                if (en.getKey().contains(e.getTo().toVector())) {
                    for (String s : STRUCTURES) {
                        if (en.getValue().startsWith(s)) {
                            giveAdvFromCriteria(e.getPlayer(), s);
                        }
                    }
                    // STRUCTURES.stream().filter(en.getValue()::startsWith).forEach(s ->
                    // giveAdvFromCriteria(e.getPlayer(), s));
                }
            }
        });
    }

    /**
     * Gives a player all advancements that have the specified string as a named criteria.
     * 
     * @param player The player to award.
     * @param string The advancement criteria string.
     */
    private void giveAdvFromCriteria(Player player, String string) {
        // Give every advancement that requires a bastion
        Bukkit.advancementIterator().forEachRemaining(ad -> {
            if (!player.getAdvancementProgress(ad).isDone()) {
                for (String crit : ad.getCriteria()) {
                    if (crit.equals(string)) {
                        // Set the criteria (it may not complete the advancement completely
                        player.getAdvancementProgress(ad).awardCriteria(crit);
                        break;
                    }
                }
            }
        });

    }

    /**
     * Gets all known island structures for the specified island.
     * 
     * @param islandId The island ID.
     * @return IslandStructures for the island.
     */
    private IslandStructures getIslandStructData(String islandId) {
        // Return from cache if it exists
        if (islandStructureCache.containsKey(islandId)) {
            return islandStructureCache.get(islandId);
        }
        // Get from database
        IslandStructures struct = handler.objectExists(islandId) ? handler.loadObject(islandId)
                : new IslandStructures(islandId);
        this.islandStructureCache.put(islandId, struct);
        return struct;
    }

    /**
     * Handles island creation event and sets up structures for the new island.
     * 
     * @param event IslandCreatedEvent
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onIslandCreated(IslandCreatedEvent event) {
        setUpIsland(event.getIsland());
    }

    /**
     * Handles island reset event and sets up structures for the reset island.
     * 
     * @param event IslandResettedEvent
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onIslandReset(IslandResettedEvent event) {
        setUpIsland(event.getIsland());
    }

        /**
     * Handles island deletion event and removes all pending structures for the deleted island.
     *
     * @param event IslandDeletedEvent
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onIslandDeleted(IslandDeleteEvent event) {
        String deletedIslandId = event.getIsland().getUniqueId();

        // Remove from in-memory cache
        islandStructureCache.remove(deletedIslandId);

        // Remove from in-memory pending structures
        for (List<StructureRecord> records : pending.values()) {
            records.removeIf(record -> event.getIsland().inIslandSpace(record.location()));
        }
        pending.values().removeIf(list -> list.isEmpty());

        // Remove from pending structures in database
        Map<Pair<Integer, Integer>, List<StructureRecord>> readyToBuild = loadToDos().getReadyToBuild();
        boolean dbChanged = false;
        for (List<StructureRecord> records : readyToBuild.values()) {
            if (records.removeIf(record -> event.getIsland().inIslandSpace(record.location()))) {
                dbChanged = true;
            }
        }

        // Save updated pending structures if any were removed
        if (dbChanged) {
            ToBePlacedStructures tbd = new ToBePlacedStructures();
            tbd.setReadyToBuild(readyToBuild);
            toPlace.saveObjectAsync(tbd);
        }
    }

    /**
     * Sets up structures for the given island based on the configuration.
     * 
     * @param island The island to set up.
     */
    private void setUpIsland(Island island) {
        // Check if this island is in this game
        if (!(addon.inWorld(island.getWorld()))) {
            return;
        }
        // Load the latest config so that admins can change it on the fly without
        // reloading
        YamlConfiguration config = YamlConfiguration.loadConfiguration(structureFile);
        Location center = island.getProtectionCenter();
        for (String env : config.getKeys(false)) {
            Environment e = Enums.getIfPresent(Environment.class, env.toUpperCase(Locale.ENGLISH)).orNull();
            if (e == null) {
                addon.logError("Error in structures.yml - unknown environment " + env);
            } else {
                place(config.getConfigurationSection(env), center, e);
            }
        }

    }

    /**
     * Places structures defined in the configuration section at the specified center location.
     * 
     * @param section The configuration section.
     * @param center The center location.
     * @param env The world environment.
     */
    private void place(ConfigurationSection section, Location center, Environment env) {
        if (section == null) {
            return;
        }
        World world = env.equals(Environment.NORMAL) ? addon.getOverWorld() : addon.getNetherWorld();
        if (world == null) {
            return;
        }

        Map<Pair<Integer, Integer>, List<StructureRecord>> readyToBuild = new HashMap<>();

        for (String vector : section.getKeys(false)) {
            String[] nameParts = section.getString(vector).split(",");
            String name = nameParts[0].strip();
            StructureRotation rotation = nameParts.length > 1
                    ? Enums.getIfPresent(StructureRotation.class, nameParts[1].strip().toUpperCase(Locale.ENGLISH)).or(
                            StructureRotation.NONE)
                    : StructureRotation.NONE;
            Mirror mirror = nameParts.length > 2
                    ? Enums.getIfPresent(Mirror.class, nameParts[2].strip().toUpperCase(Locale.ENGLISH)).or(Mirror.NONE)
                    : Mirror.NONE;
            boolean noMobs = nameParts.length > 3 && "NO_MOBS".equalsIgnoreCase(nameParts[3].strip());

            // Check the structure exists
            Structure structure = Bukkit.getStructureManager()
                    .loadStructure(NamespacedKey.fromString("minecraft:" + name));
            if (structure == null) {
                BentoBox.getInstance().logError(COULD_NOT_LOAD + name);
                return;
            }

            String[] coords = vector.split(",");
            if (coords.length > 2) {
                int x = Integer.parseInt(coords[0].strip()) + center.getBlockX();
                int y = Integer.parseInt(coords[1].strip());
                int z = Integer.parseInt(coords[2].strip()) + center.getBlockZ();
                Location location = new Location(world, x, y, z);
                // Structure will be placed at location
                readyToBuild.computeIfAbsent(new Pair<>(x >> 4, z >> 4), k -> new ArrayList<>())
                        .add(new StructureRecord(name, "minecraft:" + name, location, rotation, mirror, noMobs,
                                Collections.emptyMap()));
                this.itemsToBuild
                        .add(new StructureRecord(name, "minecraft:" + name, location, rotation, mirror, noMobs,
                                Collections.emptyMap()));
            } else {
                addon.logError("Structure file syntax error: " + vector + ": " + Arrays.toString(coords));
            }
        }
        // Load any todo's and add the ones from this new island to the list
        ToBePlacedStructures tbd = this.loadToDos();
        Map<Pair<Integer, Integer>, List<StructureRecord>> mergedMap = tbd.getReadyToBuild();
        readyToBuild.forEach((key, value) -> mergedMap.merge(key, value, (list1, list2) -> {
            list1.addAll(list2);
            return list1;
        }));

        // Save the list
        tbd.setReadyToBuild(mergedMap);
        toPlace.saveObjectAsync(tbd);
    }

    /**
     * Removes Jigsaw blocks from a placed structure and fills underwater ruins with water.
     * 
     * @param item The structure record.
     * @return The resulting bounding box of the structure.
     */
    public static BoundingBox removeJigsaw(StructureRecord item) {
        Location loc = item.location();
        Structure structure = Bukkit.getStructureManager().loadStructure(NamespacedKey.fromString(item.structure()));
        if (structure == null) {
            BentoBox.getInstance().logError(COULD_NOT_LOAD + item.structure());
            return new BoundingBox();
        }
        StructureRotation structureRotation = item.rot();
        String key = item.name();

        Location otherCorner = switch (structureRotation) {

        case CLOCKWISE_180 -> loc.clone()
                .add(new Vector(-structure.getSize().getX(), structure.getSize().getY(), -structure.getSize().getZ()));

        case CLOCKWISE_90 -> loc.clone()
                .add(new Vector(-structure.getSize().getZ(), structure.getSize().getY(), structure.getSize().getX()));

        case COUNTERCLOCKWISE_90 -> loc.clone()
                .add(new Vector(structure.getSize().getZ(), structure.getSize().getY(), -structure.getSize().getX()));

        case NONE -> loc.clone()
                .add(new Vector(structure.getSize().getX(), structure.getSize().getY(), structure.getSize().getZ()));

        };

        BoundingBox bb = BoundingBox.of(loc, otherCorner);
        for (int x = (int) bb.getMinX(); x <= bb.getMaxX(); x++) {
            for (int y = (int) bb.getMinY(); y <= bb.getMaxY(); y++) {
                for (int z = (int) bb.getMinZ(); z <= bb.getMaxZ(); z++) {
                    Block b = loc.getWorld().getBlockAt(x, y, z);
                    if (b.getType().equals(Material.JIGSAW)) {
                        // I would like to read the data from the block and do something with it!
                        processJigsaw(b, structureRotation, !item.noMobs());
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
        return bb;
    }

    /**
     * Processes a structure block, possibly spawning entities or filling chests with loot.
     * 
     * @param b The structure block.
     */
    private static void processStructureBlock(Block b) {
        // I would like to read the data from the block and do something with it!
        String data = nmsData(b);
        if (data.isEmpty()) {
            return;
        }
        BoxedStructureBlock bsb = gson.fromJson(data, BoxedStructureBlock.class);
        b.setType(Material.STRUCTURE_VOID);
        Enums.getIfPresent(EntityType.class, bsb.getMetadata().toUpperCase(Locale.ENGLISH)).toJavaUtil()
                .ifPresent(type -> b.getWorld().spawnEntity(b.getRelative(BlockFace.UP).getLocation(), type));
        if (bsb.getMetadata().contains("chest")) {
            Block downBlock = b.getRelative(BlockFace.DOWN);
            if (downBlock.getType().equals(Material.CHEST)) {
                Chest chest = (Chest) downBlock.getState();
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

    /**
     * Processes a jigsaw block, setting its final state and spawning mobs if needed.
     * 
     * @param b The jigsaw block.
     * @param structureRotation The structure's rotation.
     * @param pasteMobs Whether to spawn mobs.
     */
    private static void processJigsaw(Block b, StructureRotation structureRotation, boolean pasteMobs) {
        try {
            String data = nmsData(b);
            if (data.isEmpty()) {
                return;
            }
            BoxedJigsawBlock bjb = gson.fromJson(data, BoxedJigsawBlock.class);
            String finalState = correctDirection(bjb.getFinal_state(), structureRotation);
            BlockData bd = Bukkit.createBlockData(finalState);
            b.setBlockData(bd);
            if (!bjb.getPool().equalsIgnoreCase("minecraft:empty") && pasteMobs) {
                spawnMob(b, bjb);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Spawns a mob at the specified block based on the jigsaw block's pool.
     * 
     * @param b The block.
     * @param bjb The BoxedJigsawBlock data.
     */
    private static void spawnMob(Block b, BoxedJigsawBlock bjb) {
        // bjb.getPool contains a lot more than just mobs, so we have to filter it to
        // see if any mobs are in there. This list may need to grow in the future
        EntityType type = switch (bjb.getPool()) {
        case "minecraft:bastion/mobs/piglin" -> EntityType.PIGLIN;
        case "minecraft:bastion/mobs/hoglin" -> EntityType.HOGLIN;
        case "minecraft:bastion/mobs/piglin_melee" -> EntityType.PIGLIN_BRUTE;
        case "minecraft:village/common/cats" -> EntityType.CAT;
        case "minecraft:village/common/horses" -> EntityType.HORSE;
        case "minecraft:village/common/sheep" -> EntityType.SHEEP;
        case "minecraft:village/common/pigs" -> EntityType.PIG;
        case "minecraft:village/common/cows" -> EntityType.COW;
        case "minecraft:village/common/iron_golem" -> EntityType.IRON_GOLEM;
        case "minecraft:village/common/butcher_animals", "minecraft:village/common/animals" ->
            BUTCHER_ANIMALS.get(rand.nextInt(3));
        default -> null;
        };
        // Boxed
        if (type == null && bjb.getPool().startsWith("minecraft:boxed/")) {
            String entString = bjb.getPool().toUpperCase(Locale.ENGLISH).substring(16, bjb.getPool().length());
            type = Enums.getIfPresent(EntityType.class, entString).orNull();
        }
        // Villagers
        if (bjb.getPool().contains("zombie/villagers")) {
            type = EntityType.ZOMBIE_VILLAGER;
        } else if (bjb.getPool().contains("villagers")) {
            type = EntityType.VILLAGER;
        }
        // Spawn it
        if (type != null) {
            Entity e = b.getWorld().spawnEntity(b.getRelative(BlockFace.UP).getLocation(), type);
            if (e != null) {
                e.setPersistent(true);
            }
        }
    }

    /**
     * Corrects the direction of a block based on the structure's rotation.
     * 
     * @param finalState The final block state.
     * @param sr The structure's rotation.
     * @return The corrected block state string.
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
        return finalState.replace(oldDirection.name().toLowerCase(Locale.ENGLISH),
                newDirection.name().toLowerCase(Locale.ENGLISH));

    }

    /**
     * Adjusts the direction based on the StructureRotation.
     * 
     * @param oldDirection The old direction.
     * @param sr The structure rotation.
     * @return The new direction, or SELF if not applicable.
     */
    private static BlockFace getNewDirection(BlockFace oldDirection, StructureRotation sr) {
        if (sr.equals(StructureRotation.CLOCKWISE_180)) {
            return oldDirection.getOppositeFace();
        } else if (sr.equals(StructureRotation.CLOCKWISE_90)) {
            return switch (oldDirection) {
            case EAST -> BlockFace.SOUTH;
            case NORTH -> BlockFace.EAST;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> BlockFace.SELF;
            };
        } else if (sr.equals(StructureRotation.COUNTERCLOCKWISE_90)) {
            return switch (oldDirection) {
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
     * Looks for north, south, east, or west in the blockstate string.
     * 
     * @param finalState The final block state string.
     * @return The detected direction, or SELF if not found.
     */
    private static BlockFace getDirection(String finalState) {
        return CARDINALS.stream().filter(bf -> finalState.contains(bf.name().toLowerCase(Locale.ENGLISH))).findFirst()
                .orElse(BlockFace.SELF);
    }

    /**
     * Gets NMS data from a block using the appropriate handler.
     * 
     * @param block The block.
     * @return The NMS data string.
     */
    private static String nmsData(Block block) {
        AbstractMetaData handler;
        try {
            Class<?> clazz = Class.forName(pluginPackageName + ".nms." + bukkitVersion + ".GetMetaData");
            if (AbstractMetaData.class.isAssignableFrom(clazz)) {
                handler = (AbstractMetaData) clazz.getConstructor().newInstance();
            } else {
                throw new IllegalStateException("Class " + clazz.getName() + " does not implement AbstractGetMetaData");
            }
        } catch (Exception e) {
            BentoBox.getInstance().logError("No metadata handler found for " + bukkitVersion + " in Boxed (yet).");
            handler = new world.bentobox.boxed.nms.fallback.GetMetaData();
        }
        return handler.nmsData(block);
    }

    /**
     * Loads the list of structures to be placed from the database.
     * 
     * @return ToBePlacedStructures instance.
     */
    private ToBePlacedStructures loadToDos() {
        if (!toPlace.objectExists(TODO)) {
            return new ToBePlacedStructures();
        }
        ToBePlacedStructures list = toPlace.loadObject(TODO);
        if (list == null) {
            return new ToBePlacedStructures();
        }
        if (!list.getReadyToBuild().isEmpty()) {
            addon.log("Loaded " + list.getReadyToBuild().size() + " structure todos.");
        }
        return list;
    }

}
