package world.bentobox.boxed;

import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.generator.ChunkGenerator;
import org.eclipse.jdt.annotation.Nullable;

import nl.rutgerkok.worldgeneratorapi.WorldGeneratorApi;
import nl.rutgerkok.worldgeneratorapi.WorldRef;
import nl.rutgerkok.worldgeneratorapi.decoration.DecorationType;
import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.commands.admin.DefaultAdminCommand;
import world.bentobox.bentobox.api.commands.island.DefaultPlayerCommand;
import world.bentobox.bentobox.api.configuration.Config;
import world.bentobox.bentobox.api.configuration.WorldSettings;
import world.bentobox.boxed.generators.BasicWorldGenerator;
import world.bentobox.boxed.generators.DeleteGen;
import world.bentobox.boxed.listeners.AdvancementListener;
import world.bentobox.boxed.listeners.EnderPearlListener;

/**
 * Main BSkyBlock class - provides an island minigame in the sky
 * @author tastybento
 * @author Poslovitch
 */
public class Boxed extends GameModeAddon {

    private static final String NETHER = "_nether";
    private static final String THE_END = "_the_end";

    // Settings
    private Settings settings;
    private ChunkGenerator chunkGenerator;
    private Config<Settings> configObject = new Config<>(this, Settings.class);
    private AdvancementsManager advManager;
    private DeleteGen delChunks;

    @Override
    public void onLoad() {
        // Save the default config from config.yml
        saveDefaultConfig();
        // Load settings from config.yml. This will check if there are any issues with it too.
        loadSettings();
        // Chunk generator
        WorldRef wordRef = WorldRef.ofName(getSettings().getWorldName());
        chunkGenerator = WorldGeneratorApi
                .getInstance(getPlugin(), 0, 5)
                .createCustomGenerator(wordRef, generator -> {
                    // Set the noise generator
                    generator.setBaseNoiseGenerator(new BasicWorldGenerator(this, wordRef, getSettings().getSeed()));
                    generator.getWorldDecorator().withoutDefaultDecorations(DecorationType.SURFACE_STRUCTURES);
                    generator.getWorldDecorator().withoutDefaultDecorations(DecorationType.STRONGHOLDS);
                });
        // Register commands
        playerCommand = new DefaultPlayerCommand(this)

        {
            @Override
            public void setup()
            {
                super.setup();
            }
        };
        adminCommand = new DefaultAdminCommand(this) {};

        // Register listeners
        this.registerListener(new AdvancementListener(this));
        this.registerListener(new EnderPearlListener(this));
        //this.registerListener(new JoinListener(this));
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
    public void onEnable(){
        // Register this
        //registerListener(new JoinListener(this));
        // Advancements manager
        advManager = new AdvancementsManager(this);
        // Get delete chunk generator
        delChunks = new DeleteGen(this);
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
        String worldName = settings.getWorldName().toLowerCase();
        if (getServer().getWorld(worldName) == null) {
            log("Creating Boxed world ...");
        }

        // Create the world if it does not exist
        islandWorld = getWorld(worldName, World.Environment.NORMAL, chunkGenerator);
        // Make the nether if it does not exist
        if (settings.isNetherGenerate()) {
            if (getServer().getWorld(worldName + NETHER) == null) {
                log("Creating Boxed's Nether...");
            }
            netherWorld = settings.isNetherIslands() ? getWorld(worldName, World.Environment.NETHER, chunkGenerator) : getWorld(worldName, World.Environment.NETHER, null);
        }
        // Make the end if it does not exist
        if (settings.isEndGenerate()) {
            if (getServer().getWorld(worldName + THE_END) == null) {
                log("Creating Boxed's End World...");
            }
            endWorld = settings.isEndIslands() ? getWorld(worldName, World.Environment.THE_END, chunkGenerator) : getWorld(worldName, World.Environment.THE_END, null);
        }
    }

    /**
     * Gets a world or generates a new world if it does not exist
     * @param worldName2 - the overworld name
     * @param env - the environment
     * @param chunkGenerator2 - the chunk generator. If <tt>null</tt> then the generator will not be specified
     * @return world loaded or generated
     */
    private World getWorld(String worldName2, Environment env, ChunkGenerator chunkGenerator2) {
        // Set world name
        worldName2 = env.equals(World.Environment.NETHER) ? worldName2 + NETHER : worldName2;
        worldName2 = env.equals(World.Environment.THE_END) ? worldName2 + THE_END : worldName2;
        World w = WorldCreator.name(worldName2).type(WorldType.FLAT).environment(env).generator(chunkGenerator2).createWorld();
        // Backup world
        WorldCreator.name(worldName2 + "_bak").type(WorldType.FLAT).environment(env).generator(chunkGenerator2).createWorld();
        // Set spawn rates
        if (w != null) {
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
        return w;

    }

    @Override
    public WorldSettings getWorldSettings() {
        return getSettings();
    }

    @Override
    public @Nullable ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        if (id.equals("delete")) {
            return delChunks;
        }
        return chunkGenerator;
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
        // Reload settings and save them. This will occur after all addons have loaded
        this.loadSettings();
        this.saveWorldSettings();
    }

    /**
     * @return the advManager
     */
    public AdvancementsManager getAdvManager() {
        return advManager;
    }

}
