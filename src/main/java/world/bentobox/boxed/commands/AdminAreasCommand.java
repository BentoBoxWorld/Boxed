package world.bentobox.boxed.commands;

import java.util.Map;
import java.util.EnumMap;
import java.util.Comparator;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.blueprints.dataobjects.BlueprintBundle;
import world.bentobox.bentobox.util.Util;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.areas.Area;
import world.bentobox.boxed.areas.AreaLocationStrategy;
import world.bentobox.boxed.areas.AreaManager;

/**
 * Admin command that lists the areas. Its sub-commands visit an area, make a new one and
 * ask for an area's land to be rebuilt.
 *
 * @author tastybento
 */
public class AdminAreasCommand extends CompositeCommand {

    private static final String PREFIX = "boxed.commands.boxadmin.areas.";
    private static final String UNKNOWN_AREA = PREFIX + "unknown-area";

    public AdminAreasCommand(CompositeCommand parent) {
        super(parent, "areas");
    }

    @Override
    public void setup() {
        this.setPermission("admin.areas");
        this.setOnlyPlayer(false);
        this.setDescription(PREFIX + "description");
        new Tp(this);
        new Create(this);
        new Rebuild(this);
        new Clean(this);
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        if (!args.isEmpty()) {
            this.showHelp(this, user);
            return false;
        }
        Boxed addon = getAddon();
        user.sendMessage(PREFIX + "header");
        for (Area area : addon.getAreas().getAreas()) {
            long boxes = addon.getOverWorld() == null ? 0 : addon.getIslands().getIslands(addon.getOverWorld()).stream()
                    .filter(i -> i.getOwner() != null)
                    .filter(i -> addon.getAreaAt(i.getCenter().getBlockZ() >> 4).equals(area)).count();
            user.sendMessage(PREFIX + "entry", TextVariables.NAME, area.id(), "[row]", String.valueOf(area.row()),
                    "[seed]", String.valueOf(area.seed()), TextVariables.NUMBER, String.valueOf(boxes));
            if (!area.enabled()) {
                user.sendMessage(PREFIX + "entry-closed");
            }
            if (area.requires() != null) {
                Area required = addon.getAreas().getArea(area.requires());
                user.sendMessage(PREFIX + "entry-requires", TextVariables.NAME, area.requires(), TextVariables.NUMBER,
                        String.valueOf(required == null ? 0 : required.unlockSize()));
            }
        }
        return true;
    }

    private static Optional<List<String>> areaNames(Boxed addon, List<String> args) {
        if (args.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Util.tabLimit(addon.getAreas().getAreas().stream().map(Area::id).toList(),
                args.get(args.size() - 1)));
    }

    /**
     * Teleports the admin to the land of an area, at the spot where its next box will go, so
     * the land can be looked at and structures placed before any player has a box there.
     * Admins who cannot walk outside the border are put in spectator mode.
     */
    static class Tp extends CompositeCommand {

        Tp(CompositeCommand parent) {
            super(parent, "tp");
        }

        @Override
        public void setup() {
            this.setPermission("admin.areas.tp");
            this.setOnlyPlayer(true);
            this.setParametersHelp(PREFIX + "tp.parameters");
            this.setDescription(PREFIX + "tp.description");
        }

        @Override
        public boolean execute(User user, String label, List<String> args) {
            if (args.size() != 1) {
                this.showHelp(this, user);
                return false;
            }
            Boxed addon = getAddon();
            Area area = addon.getAreas().getArea(args.get(0).toLowerCase(Locale.ENGLISH));
            if (area == null) {
                user.sendMessage(UNKNOWN_AREA);
                return false;
            }
            World world = addon.getOverWorld();
            Location spot = new AreaLocationStrategy(addon, area).peekNextLocation(world);
            user.sendMessage(PREFIX + "tp.teleporting", TextVariables.NAME, area.id());
            // Load the chunk first so the highest block is known
            world.getChunkAtAsync(spot).thenRun(() -> {
                Location to = world.getHighestBlockAt(spot).getLocation().add(0.5, 1, 0.5);
                // Players are kept inside boxes by the Border addon, and there is no box here.
                // Those who can walk outside the border keep their game mode. Anyone else is
                // made a spectator, which Border leaves alone.
                Player player = user.getPlayer();
                boolean free = player.isOp() || player.hasPermission(getPermissionPrefix() + "border.bypass");
                if (!free && player.getGameMode() != GameMode.SPECTATOR) {
                    player.setGameMode(GameMode.SPECTATOR);
                    user.sendMessage(PREFIX + "tp.spectator");
                }
                Util.teleportAsync(user.getPlayer(), to, TeleportCause.COMMAND);
            });
            return true;
        }

        @Override
        public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
            return areaNames(getAddon(), args);
        }
    }

    /**
     * Makes a new area: adds it to areas.yml, gives it a biomes file and a structures file and
     * makes a blueprint bundle for it. The land is built when the server next starts.
     */
    static class Create extends CompositeCommand {

        Create(CompositeCommand parent) {
            super(parent, "create");
        }

        @Override
        public void setup() {
            this.setPermission("admin.areas.create");
            this.setOnlyPlayer(false);
            this.setParametersHelp(PREFIX + "create.parameters");
            this.setDescription(PREFIX + "create.description");
        }

        @Override
        public boolean execute(User user, String label, List<String> args) {
            if (args.isEmpty() || args.size() > 2) {
                this.showHelp(this, user);
                return false;
            }
            Boxed addon = getAddon();
            String name = args.get(0).toLowerCase(Locale.ENGLISH);
            if (!AreaManager.isValidName(name)) {
                user.sendMessage(PREFIX + "create.invalid-name");
                return false;
            }
            if (addon.getAreas().getArea(name) != null || addon.getAreas().isPending(name)
                    || getPlugin().getBlueprintsManager().getBlueprintBundles(addon).containsKey(name)) {
                user.sendMessage(PREFIX + "create.exists");
                return false;
            }
            long seed;
            try {
                seed = args.size() == 2 ? Long.parseLong(args.get(1)) : ThreadLocalRandom.current().nextLong();
            } catch (NumberFormatException e) {
                user.sendMessage(PREFIX + "create.invalid-seed");
                return false;
            }
            try {
                createArea(addon, name, seed);
            } catch (IOException e) {
                addon.logError("Could not create area " + name + ": " + e.getMessage());
                user.sendMessage(PREFIX + "create.failed");
                return false;
            }
            user.sendMessage(PREFIX + "create.success", TextVariables.NAME, name, "[seed]", String.valueOf(seed));
            return true;
        }

        private void createArea(Boxed addon, String name, long seed) throws IOException {
            File folder = addon.getDataFolder();
            String biomes = "biomes-" + name + ".yml";
            String structures = "structures-" + name + ".yml";
            // Start from the default area's biomes; an admin will want to change them
            File biomesFile = new File(folder, biomes);
            File defaultBiomes = new File(folder, "biomes.yml");
            if (!biomesFile.exists() && defaultBiomes.exists()) {
                Files.copy(defaultBiomes.toPath(), biomesFile.toPath());
            }
            // No structures to begin with
            File structuresFile = new File(folder, structures);
            if (!structuresFile.exists()) {
                Files.writeString(structuresFile.toPath(), "# This file is written by the /boxadmin place command\n");
            }
            File areasFile = new File(folder, "areas.yml");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(areasFile);
            config.set("areas." + name + ".seed", seed);
            config.set("areas." + name + ".biomes", biomes);
            config.set("areas." + name + ".structures", structures);
            config.save(areasFile);

            // A bundle with the same name is how players choose the area
            // Start with the same blueprints as an existing bundle. The default bundle is not
            // always called "default", so use the first bundle that has an overworld blueprint.
            BlueprintBundle source = getPlugin().getBlueprintsManager().getBlueprintBundles(addon).values().stream()
                    .filter(b -> b.getBlueprint(World.Environment.NORMAL) != null)
                    .min(Comparator.comparingInt(BlueprintBundle::getSlot)).orElse(null);
            BlueprintBundle bundle = new BlueprintBundle();
            bundle.setUniqueId(name);
            bundle.setDisplayName(Util.prettifyText(name));
            bundle.setDescription(List.of("A box in the " + Util.prettifyText(name) + " area"));
            Map<World.Environment, String> blueprints = new EnumMap<>(World.Environment.class);
            if (source != null) {
                bundle.setIcon(source.getIcon());
                for (World.Environment env : World.Environment.values()) {
                    if (source.getBlueprint(env) != null) {
                        blueprints.put(env, source.getBlueprint(env));
                    }
                }
            } else {
                // The blueprints that come with Boxed
                blueprints.put(World.Environment.NORMAL, "island");
                blueprints.put(World.Environment.NETHER, "nether");
            }
            bundle.setBlueprints(blueprints);
            bundle.setSlot(getPlugin().getBlueprintsManager().getBlueprintBundles(addon).size());
            getPlugin().getBlueprintsManager().addBlueprintBundle(addon, bundle);
            getPlugin().getBlueprintsManager().saveBlueprintBundle(addon, bundle);

            // Boxes cannot be made in it until the land is built on the next start
            addon.getAreas().addPending(name);
        }
    }

    /**
     * Asks for the land of an area to be built again when the server next starts.
     */
    static class Rebuild extends CompositeCommand {

        private static final String REGENERATE = "regenerate";

        Rebuild(CompositeCommand parent) {
            super(parent, "rebuild");
        }

        @Override
        public void setup() {
            this.setPermission("admin.areas.rebuild");
            this.setOnlyPlayer(false);
            this.setParametersHelp(PREFIX + "rebuild.parameters");
            this.setDescription(PREFIX + "rebuild.description");
        }

        @Override
        public boolean execute(User user, String label, List<String> args) {
            if (args.isEmpty() || args.size() > 2 || (args.size() == 2 && !REGENERATE.equalsIgnoreCase(args.get(1)))) {
                this.showHelp(this, user);
                return false;
            }
            Boxed addon = getAddon();
            Area area = addon.getAreas().getArea(args.get(0).toLowerCase(Locale.ENGLISH));
            if (area == null) {
                user.sendMessage(UNKNOWN_AREA);
                return false;
            }
            boolean regenerate = args.size() == 2;
            if (!addon.requestRebuild(area, regenerate)) {
                user.sendMessage(PREFIX + "rebuild.failed");
                return false;
            }
            user.sendMessage(PREFIX + (regenerate ? "rebuild.success-regenerate" : "rebuild.success"),
                    TextVariables.NAME, area.id());
            return true;
        }

        @Override
        public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
            if (args.size() == 2) {
                return Optional.of(Util.tabLimit(List.of(REGENERATE), args.get(1)));
            }
            return areaNames(getAddon(), args);
        }
    }

    /**
     * Puts the land of a box position back the way the area's template has it. Useful after
     * trying out structures in a position nobody has a box in.
     */
    static class Clean extends CompositeCommand {

        Clean(CompositeCommand parent) {
            super(parent, "clean");
        }

        @Override
        public void setup() {
            this.setPermission("admin.areas.clean");
            this.setOnlyPlayer(false);
            this.setParametersHelp(PREFIX + "clean.parameters");
            this.setDescription(PREFIX + "clean.description");
        }

        @Override
        public boolean execute(User user, String label, List<String> args) {
            Boxed addon = getAddon();
            Location location;
            if (args.isEmpty() && user.isPlayer() && addon.inWorld(user.getLocation())) {
                location = user.getLocation();
            } else if (args.size() == 2 && Util.isInteger(args.get(0), true) && Util.isInteger(args.get(1), true)) {
                location = new Location(addon.getOverWorld(), Integer.parseInt(args.get(0)), 0,
                        Integer.parseInt(args.get(1)));
            } else {
                this.showHelp(this, user);
                return false;
            }
            Location centre = addon.getBoxCentre(location);
            if (!addon.getBoxCleaner().clean(location)) {
                user.sendMessage(PREFIX + "clean.in-use");
                return false;
            }
            user.sendMessage(PREFIX + "clean.success", TextVariables.XYZ, centre.getBlockX() + "," + centre.getBlockZ());
            return true;
        }
    }
}
