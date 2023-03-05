package world.bentobox.boxed;

import java.util.Collections;

import org.bukkit.Difficulty;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
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
import world.bentobox.boxed.commands.AdminPlaceStructureCommand;
import world.bentobox.boxed.generators.biomes.BoxedBiomeGenerator;
import world.bentobox.boxed.generators.biomes.NetherSeedBiomeGenerator;
import world.bentobox.boxed.generators.biomes.SeedBiomeGenerator;
import world.bentobox.boxed.generators.chunks.AbstractBoxedChunkGenerator;
import world.bentobox.boxed.generators.chunks.BoxedBlockPopulator;
import world.bentobox.boxed.generators.chunks.BoxedChunkGenerator;
import world.bentobox.boxed.generators.chunks.BoxedSeedChunkGenerator;
import world.bentobox.boxed.listeners.AdvancementListener;
import world.bentobox.boxed.listeners.EnderPearlListener;
import world.bentobox.boxed.listeners.NewAreaListener;

/**
 * Main Boxed class - provides a survival game inside a box
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
    private static final String SEED = "seed";
    private static final String NETHER = "_nether";
    private static final String THE_END = "_the_end";
    private static final String BASE = "_base";

    // Settings
    private Settings settings;
    private AbstractBoxedChunkGenerator chunkGenerator;
    private final Config<Settings> configObject = new Config<>(this, Settings.class);
    private AdvancementsManager advManager;
    private AbstractBoxedChunkGenerator netherChunkGenerator;
    private World baseWorld;
    private World baseWorldNether;
    private World seedWorld;
    private World seedWorldNether;
    //private World seedWorldEnd;
    private BiomeProvider boxedBiomeProvider;
    private BlockPopulator boxedBlockPopulator;

    @Override
    public void onLoad() {
        // Save the default config from config.yml
        saveDefaultConfig();
        // Load settings from config.yml. This will check if there are any issues with it too.
        loadSettings();

        // Register commands
        playerCommand = new DefaultPlayerCommand(this) {};

        adminCommand = new DefaultAdminCommand(this) {
            @Override
            public void setup()
            {
                super.setup();
                new AdminPlaceStructureCommand(this);
            }
        };
        

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
        // Initialize the Generator because createWorlds will be run after onLoad
        this.chunkGenerator = new BoxedChunkGenerator(this);
        this.netherChunkGenerator = new BoxedChunkGenerator(this);
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
        this.registerListener(new NewAreaListener(this));

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
        String worldName = settings.getWorldName().toLowerCase();
        // Create overworld
        createOverWorld(worldName);

        // Make the nether if it does not exist
        if (settings.isNetherGenerate()) {
            createNether(worldName);
        }
        /*
        // Make the end if it does not exist
        if (settings.isEndGenerate()) {
          //TODO
         */
    }

    private void createNether(String worldName) {
        // Create vanilla seed nether world
        log("Creating Boxed Seed Nether world ...");
        // This creates a vanilla base world with biomes
        AbstractBoxedChunkGenerator seedBaseGen = new BoxedSeedChunkGenerator(this, Environment.NETHER);
        baseWorldNether = WorldCreator
                .name(SEED+NETHER+BASE)
                .generator(seedBaseGen)
                .environment(Environment.NETHER)
                .seed(getSettings().getSeed())
                .createWorld();
        baseWorldNether.setDifficulty(Difficulty.PEACEFUL);
        baseWorldNether.setSpawnLocation(settings.getSeedX(), 64, settings.getSeedZ());
        copyChunks(baseWorldNether, seedBaseGen);
        // Create seed world
        // This copies a base world with custom biomes
        log("Creating Boxed Biomed Nether world ...");

        seedWorldNether = WorldCreator
                .name(SEED+NETHER)
                .generator(new BoxedSeedChunkGenerator(this, Environment.NETHER, new NetherSeedBiomeGenerator(this, seedBaseGen)))
                .environment(Environment.NETHER)
                .seed(getSettings().getSeed())
                .createWorld();
        seedWorldNether.setDifficulty(Difficulty.EASY);

        seedWorldNether.setSpawnLocation(settings.getNetherSeedX(), 64, settings.getNetherSeedZ());

        copyChunks(seedWorldNether, netherChunkGenerator);

        if (getServer().getWorld(worldName + NETHER) == null) {
            log("Creating Boxed's Nether...");
        }
        netherWorld = getWorld(worldName, World.Environment.NETHER);
    }

    private void createOverWorld(String worldName) {
        // Create vanilla seed world
        log("Creating Boxed Seed world ...");
        // This creates a vanilla base world with biomes
        AbstractBoxedChunkGenerator seedBaseGen = new BoxedSeedChunkGenerator(this, Environment.NORMAL);
        baseWorld = WorldCreator
                .name(SEED+BASE)
                .generator(seedBaseGen)
                .environment(Environment.NORMAL)
                .seed(getSettings().getSeed())
                .createWorld();
        baseWorld.setDifficulty(Difficulty.PEACEFUL);
        baseWorld.setSpawnLocation(settings.getSeedX(), 64, settings.getSeedZ());
        copyChunks(baseWorld, seedBaseGen);
        // Create seed world
        // This copies a base world with custom biomes
        log("Creating Boxed Biomed world ...");

        seedWorld = WorldCreator
                .name(SEED)
                .generator(new BoxedSeedChunkGenerator(this, Environment.NORMAL, new SeedBiomeGenerator(this, seedBaseGen)))
                .environment(Environment.NORMAL)
                .seed(getSettings().getSeed())
                .createWorld();
        seedWorld.setDifficulty(Difficulty.EASY);

        seedWorld.setSpawnLocation(settings.getSeedX(), 64, settings.getSeedZ());

        copyChunks(seedWorld, chunkGenerator);



        if (getServer().getWorld(worldName) == null) {
            log("Creating Boxed world ...");
        }

        // Create the world if it does not exist
        islandWorld = getWorld(worldName, World.Environment.NORMAL);

    }

    /**
     * Copies chunks from the seed world so they can be pasted in the game world
     * @param world - source world
     * @param gen - generator to store the chunks
     */
    private void copyChunks(World world, AbstractBoxedChunkGenerator gen) {
        int startX = 0;
        int startZ = 0;
        if (world.getEnvironment().equals(Environment.NORMAL)) {
            startX = this.settings.getSeedX() >> 4;
            startZ = this.settings.getSeedZ() >> 4;
        } else {
            startX = this.settings.getNetherSeedX() >> 4;
            startZ = this.settings.getNetherSeedZ() >> 4;
        }

        // Convert to chunks
        int size = (int)(this.getSettings().getIslandDistance() / 16D);
        double percent = size * 4D * size;
        int count = 0;
        int last = 0;
        for (int x = -size; x <= size; x ++) {
            for (int z = -size; z <= size; z++) {
                gen.setChunk(x, z, world.getChunkAt(startX + x, startZ + z));
                count++;
                int p = (int) (count / percent * 100);
                if (p % 10 == 0 && p != last) {
                    last = p;
                    this.log("Storing seed chunks for " + world.getEnvironment() + " " + p + "% done");
                }

            }
        }
    }

    /**
     * Get the chunk generator for a Boxed world
     * @param env - nether, normal, or end
     * @return the chunkGenerator for the environment
     */
    public AbstractBoxedChunkGenerator getChunkGenerator(Environment env) {
        if (env.equals(Environment.NORMAL)) {
            return chunkGenerator;
        }
        return netherChunkGenerator;
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
        boxedBiomeProvider = new BoxedBiomeGenerator(this);
        boxedBlockPopulator = new BoxedBlockPopulator(this);
        World w = WorldCreator
                .name(worldName2)
                .generator(env.equals(World.Environment.NETHER) ? netherChunkGenerator : chunkGenerator)
                .environment(env)
                .seed(seedWorld.getSeed()) // For development
                .createWorld();
        // Set spawn rates
        if (w != null) {
            setSpawnRates(w);
        }
        return w;

    }

    /**
     * @return the boxedBiomeProvider
     */
    public BiomeProvider getBoxedBiomeProvider() {
        return boxedBiomeProvider;
    }

    private void setSpawnRates(World w) {
        if (getSettings().getSpawnLimitMonsters() > 0) {
            w.setSpawnLimit(SpawnCategory.MONSTER, getSettings().getSpawnLimitMonsters());
        }
        if (getSettings().getSpawnLimitAmbient() > 0) {
            w.setSpawnLimit(SpawnCategory.AMBIENT, getSettings().getSpawnLimitAmbient());
        }
        if (getSettings().getSpawnLimitAnimals() > 0) {
            w.setSpawnLimit(SpawnCategory.ANIMAL, getSettings().getSpawnLimitAnimals());
        }
        if (getSettings().getSpawnLimitWaterAnimals() > 0) {
            w.setSpawnLimit(SpawnCategory.WATER_ANIMAL, getSettings().getSpawnLimitWaterAnimals());
        }
        if (getSettings().getTicksPerAnimalSpawns() > 0) {
            w.setTicksPerSpawns(SpawnCategory.ANIMAL, getSettings().getTicksPerAnimalSpawns());
        }
        if (getSettings().getTicksPerMonsterSpawns() > 0) {
            w.setTicksPerSpawns(SpawnCategory.MONSTER, getSettings().getTicksPerMonsterSpawns());
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

    /**
     * @return the boxedBlockPopulator
     */
    public BlockPopulator getBoxedBlockPopulator() {
        return boxedBlockPopulator;
    }

}
