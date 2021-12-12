package world.bentobox.boxed;

import java.util.Collections;

import org.bukkit.Difficulty;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.commands.admin.DefaultAdminCommand;
import world.bentobox.bentobox.api.commands.island.DefaultPlayerCommand;
import world.bentobox.bentobox.api.configuration.Config;
import world.bentobox.bentobox.api.configuration.WorldSettings;
import world.bentobox.bentobox.api.flags.Flag;
import world.bentobox.bentobox.api.flags.Flag.Mode;
import world.bentobox.bentobox.api.flags.Flag.Type;
import world.bentobox.bentobox.managers.RanksManager;
import world.bentobox.boxed.generators.CopyGenerator;
import world.bentobox.boxed.listeners.AdvancementListener;
import world.bentobox.boxed.listeners.EnderPearlListener;

/**
 * Main Boxed class - provides an survival game inside a box
 * @author tastybento
 */
public class Boxed extends GameModeAddon {

    public static final Flag MOVE_BOX = new Flag.Builder("MOVE_BOX", Material.COMPOSTER)
            .mode(Mode.BASIC)
            .type(Type.PROTECTION)
            .defaultRank(RanksManager.OWNER_RANK)
            .build();
    public static final Flag ALLOW_MOVE_BOX = new Flag.Builder("ALLOW_MOVE_BOX", Material.COMPOSTER)
            .mode(Mode.BASIC)
            .type(Type.WORLD_SETTING)
            .defaultSetting(true)
            .build();

    private static final String NETHER = "_nether";
    private static final String THE_END = "_the_end";

    // Settings
    private Settings settings;
    private ChunkGenerator chunkGenerator;
    private final Config<Settings> configObject = new Config<>(this, Settings.class);
    private AdvancementsManager advManager;
    private ChunkGenerator netherChunkGenerator;
    private World seedWorld;

    @Override
    public void onLoad() {
        // Save the default config from config.yml
        saveDefaultConfig();
        // Load settings from config.yml. This will check if there are any issues with it too.
        loadSettings();

        // Register commands
        playerCommand = new DefaultPlayerCommand(this) {};

        adminCommand = new DefaultAdminCommand(this) {};

    }

    private boolean loadSettings() {
        // Load settings again to get worlds
        settings = configObject.loadConfigObject();
        if (settings == null) {
            // Disable
            logError("Boxed settings could not load! Addon disabled.");
            setState(State.DISABLED);
            return false;
        }
        return true;
    }

    @Override
    public void onEnable() {
        // Check for recommended addons
        if (this.getPlugin().getAddonsManager().getAddonByName("Border").isEmpty()) {
            this.logWarning("Boxed normally requires the Border addon.");
        }
        if (this.getPlugin().getAddonsManager().getAddonByName("InvSwitcher").isEmpty()) {
            this.logWarning("Boxed normally requires the InvSwitcher addon for per-world Advancements.");
        }
        // Advancements manager
        advManager = new AdvancementsManager(this);
        // Make flags only applicable to this game mode
        MOVE_BOX.setGameModes(Collections.singleton(this));
        ALLOW_MOVE_BOX.setGameModes(Collections.singleton(this));
        // Register protection flag with BentoBox
        getPlugin().getFlagsManager().registerFlag(this, ALLOW_MOVE_BOX);
        if (ALLOW_MOVE_BOX.isSetForWorld(getOverWorld())) {
            getPlugin().getFlagsManager().registerFlag(this, MOVE_BOX);
        } else {
            getPlugin().getFlagsManager().unregister(MOVE_BOX);
        }

        // Register listeners
        this.registerListener(new AdvancementListener(this));
        this.registerListener(new EnderPearlListener(this));

        // Register placeholders
        PlaceholdersManager phManager  = new PlaceholdersManager(this);
        getPlugin().getPlaceholdersManager().registerPlaceholder(this,"visited_island_advancements", phManager::getCountByLocation);
        getPlugin().getPlaceholdersManager().registerPlaceholder(this,"island_advancements", phManager::getCount);

    }

    @Override
    public void onDisable() {
        // Save the advancements cache
        getAdvManager().save();
    }

    @Override
    public void onReload() {
        if (loadSettings()) {
            log("Reloaded Boxed settings");
        }
    }

    /**
     * @return the settings
     */
    public Settings getSettings() {
        return settings;
    }

    @Override
    public void createWorlds() {
        // Create seed world
        String worldName = settings.getWorldName().toLowerCase();
        if (getServer().getWorld(worldName) == null) {
            log("Creating Boxed Seed world ...");
        }
        seedWorld = WorldCreator.name(worldName + "_bak").seed(settings.getSeed()).createWorld();
        seedWorld.setDifficulty(Difficulty.PEACEFUL); // No damage wanted in this world.

        if (getServer().getWorld(worldName) == null) {
            log("Creating Boxed world ...");
        }

        // Create the world if it does not exist
        islandWorld = getWorld(worldName, World.Environment.NORMAL);
        // Make the nether if it does not exist
        if (settings.isNetherGenerate()) {
            if (getServer().getWorld(worldName + NETHER) == null) {
                log("Creating Boxed's Nether...");
            }
            netherWorld = settings.isNetherIslands() ? getWorld(worldName, World.Environment.NETHER) : getWorld(worldName, World.Environment.NETHER);
        }
        // Make the end if it does not exist
        if (settings.isEndGenerate()) {
            if (getServer().getWorld(worldName + THE_END) == null) {
                log("Creating Boxed's End World...");
            }
            endWorld = settings.isEndIslands() ? getWorld(worldName, World.Environment.THE_END) : getWorld(worldName, World.Environment.THE_END);
        }
    }

    /**
     * Gets a world or generates a new world if it does not exist
     * @param worldName2 - the overworld name
     * @param env - the environment
     * @return world loaded or generated
     */
    private World getWorld(String worldName2, Environment env) {
        // Set world name
        worldName2 = env.equals(World.Environment.NETHER) ? worldName2 + NETHER : worldName2;
        worldName2 = env.equals(World.Environment.THE_END) ? worldName2 + THE_END : worldName2;
        World w = WorldCreator.name(worldName2).environment(env).seed(settings.getSeed()).createWorld();
        // Set spawn rates
        if (w != null) {
            setSpawnRates(w);
        }
        return w;

    }

    private void setSpawnRates(World w) {
        if (getSettings().getSpawnLimitMonsters() > 0) {
            w.setMonsterSpawnLimit(getSettings().getSpawnLimitMonsters());
        }
        if (getSettings().getSpawnLimitAmbient() > 0) {
            w.setAmbientSpawnLimit(getSettings().getSpawnLimitAmbient());
        }
        if (getSettings().getSpawnLimitAnimals() > 0) {
            w.setAnimalSpawnLimit(getSettings().getSpawnLimitAnimals());
        }
        if (getSettings().getSpawnLimitWaterAnimals() > 0) {
            w.setWaterAnimalSpawnLimit(getSettings().getSpawnLimitWaterAnimals());
        }
        if (getSettings().getTicksPerAnimalSpawns() > 0) {
            w.setTicksPerAnimalSpawns(getSettings().getTicksPerAnimalSpawns());
        }
        if (getSettings().getTicksPerMonsterSpawns() > 0) {
            w.setTicksPerMonsterSpawns(getSettings().getTicksPerMonsterSpawns());
        }
    }

    @Override
    public WorldSettings getWorldSettings() {
        return getSettings();
    }

    @Override
    public @Nullable ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return worldName.endsWith(NETHER) ? netherChunkGenerator : chunkGenerator;
    }

    @Override
    public void saveWorldSettings() {
        if (settings != null) {
            configObject.saveConfigObject(settings);
        }
    }

    /* (non-Javadoc)
     * @see world.bentobox.bentobox.api.addons.Addon#allLoaded()
     */
    @Override
    public void allLoaded() {
        // Save settings. This will occur after all addons have loaded
        this.saveWorldSettings();
    }

    /**
     * @return the advManager
     */
    public AdvancementsManager getAdvManager() {
        return advManager;
    }

}
