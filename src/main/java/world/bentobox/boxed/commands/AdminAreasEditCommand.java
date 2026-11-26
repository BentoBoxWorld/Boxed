package world.bentobox.boxed.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.World.Environment;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.areas.Area;
import world.bentobox.boxed.workshop.BiomeStyles;
import world.bentobox.boxed.workshop.Workshop;
import world.bentobox.boxed.workshop.WorkshopManager;

/**
 * Opens the area workshop: temporary worlds where an admin tries out a seed and a biome
 * style and shapes the land before it becomes an area. Players get dialog boxes; everything
 * can also be typed, which is what the console has to do.
 *
 * @author tastybento
 */
public class AdminAreasEditCommand extends CompositeCommand {

    private static final String PREFIX = "boxed.commands.boxadmin.areas.edit.";
    private static final String NONE = PREFIX + "none";
    private static final String FAILED = PREFIX + "failed";
    private static final String START = "start";
    private static final String GO = "go";
    private static final String DISCARD = "discard";
    private static final String CONFIRM = "confirm";
    private static final String RANDOM = "random";
    private static final String NETHER = "nether";
    private static final String SEED_VAR = "[seed]";
    /**
     * Stands for "the same as the area that is being edited"
     */
    static final String SAME = "same";
    /**
     * Stands for "not an existing area"
     */
    static final String NEW = "new";

    private final WorkshopDialogs dialogs = new WorkshopDialogs(this);

    /**
     * @param parent the areas command
     */
    public AdminAreasEditCommand(CompositeCommand parent) {
        super(parent, "edit");
    }

    @Override
    public void setup() {
        this.setPermission("admin.areas.edit");
        this.setOnlyPlayer(false);
        this.setParametersHelp(PREFIX + "parameters");
        this.setDescription(PREFIX + "description");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        WorkshopManager manager = this.<Boxed>getAddon().getWorkshop();
        if (args.isEmpty()) {
            return open(user, manager);
        }
        return switch (args.get(0).toLowerCase(Locale.ENGLISH)) {
        case START -> startTyped(user, args);
        case GO -> goTyped(user, args);
        case DISCARD -> {
            if (args.size() == 2 && CONFIRM.equalsIgnoreCase(args.get(1))) {
                yield discard(user);
            }
            user.sendMessage(PREFIX + "discard-confirm");
            yield false;
        }
        default -> help(user);
        };
    }

    private boolean startTyped(User user, List<String> args) {
        if (args.size() < 2 || args.size() > 4) {
            return help(user);
        }
        return start(user, args.get(1), args.size() > 2 ? args.get(2) : SAME, args.size() > 3 ? args.get(3) : NEW);
    }

    private boolean goTyped(User user, List<String> args) {
        if (args.size() == 1) {
            return go(user, Environment.NORMAL);
        }
        return args.size() == 2 && NETHER.equalsIgnoreCase(args.get(1)) ? go(user, Environment.NETHER) : help(user);
    }

    private boolean help(User user) {
        this.showHelp(this, user);
        return false;
    }

    /**
     * Shows where things stand: the dialog boxes for a player, a line of text for the console.
     */
    private boolean open(User user, WorkshopManager manager) {
        Optional<Workshop> workshop = manager.get();
        if (user.isPlayer()) {
            workshop.ifPresentOrElse(w -> dialogs.showStatus(user, w), () -> dialogs.showStart(user));
        } else if (workshop.isPresent()) {
            Workshop w = workshop.get();
            user.sendMessage(PREFIX + "status", SEED_VAR, String.valueOf(w.getSeed()), "[style]",
                    BiomeStyles.name(w.getBiomes()), "[area]", w.getArea() == null ? "-" : w.getArea());
        } else {
            user.sendMessage(NONE, TextVariables.LABEL, getTopLabel());
        }
        return true;
    }

    /**
     * Starts work on an area and takes the admin there.
     * @param user who asked
     * @param seedText a seed, "random" or -1 for a random one, or blank or "same" for the seed
     *                 of the area being edited (random for a new area)
     * @param style name of a biome style, or "same" for the style of the area being edited
     *              (the original style for a new area)
     * @param areaId name of an existing area to start from, or "new"
     * @return true if work started
     */
    boolean start(User user, String seedText, String style, String areaId) {
        Boxed addon = getAddon();
        WorkshopManager manager = addon.getWorkshop();
        if (manager.get().isPresent()) {
            user.sendMessage(PREFIX + "exists");
            return false;
        }
        Area area = null;
        if (areaId != null && !NEW.equalsIgnoreCase(areaId)) {
            area = addon.getAreas().getArea(areaId.toLowerCase(Locale.ENGLISH));
            if (area == null) {
                user.sendMessage("boxed.commands.boxadmin.areas.unknown-area");
                return false;
            }
        }
        Long seed = parseSeed(seedText, area);
        if (seed == null) {
            user.sendMessage("boxed.commands.boxadmin.areas.create.invalid-seed");
            return false;
        }
        String biomes;
        if (style == null || SAME.equalsIgnoreCase(style)) {
            biomes = area == null ? BiomeStyles.file(addon.getDataFolder(), BiomeStyles.ORIGINAL) : area.biomes();
        } else {
            biomes = BiomeStyles.file(addon.getDataFolder(), style);
        }
        if (biomes == null) {
            user.sendMessage(PREFIX + "unknown-style", "[styles]",
                    String.join(", ", BiomeStyles.list(addon.getDataFolder())));
            return false;
        }
        try {
            manager.start(seed, biomes, area);
        } catch (IOException e) {
            addon.logError("Could not start work on an area: " + e.getMessage());
            user.sendMessage(FAILED);
            return false;
        }
        user.sendMessage(PREFIX + "started", SEED_VAR, String.valueOf(seed), "[style]", BiomeStyles.name(biomes));
        return go(user, Environment.NORMAL);
    }

    private static Long parseSeed(String text, Area area) {
        String seed = text == null ? "" : text.trim();
        if (seed.isEmpty() || SAME.equalsIgnoreCase(seed)) {
            return area == null ? ThreadLocalRandom.current().nextLong() : area.seed();
        }
        if (RANDOM.equalsIgnoreCase(seed) || seed.equals("-1")) {
            return ThreadLocalRandom.current().nextLong();
        }
        try {
            return Long.parseLong(seed);
        } catch (NumberFormatException _) {
            return null;
        }
    }

    /**
     * Takes the admin to the area being worked on. The world is made if it does not exist
     * yet, so the console can use this to make it too.
     * @param user who asked
     * @param env dimension to go to
     * @return true if there is somewhere to go
     */
    boolean go(User user, Environment env) {
        Boxed addon = getAddon();
        WorkshopManager manager = addon.getWorkshop();
        if (manager.get().isEmpty()) {
            user.sendMessage(NONE, TextVariables.LABEL, getTopLabel());
            return false;
        }
        if (!manager.hasDimension(env) || manager.getWorld(env) == null) {
            user.sendMessage(FAILED);
            return false;
        }
        if (!user.isPlayer()) {
            user.sendMessage(PREFIX + "ready");
            return true;
        }
        manager.enter(user.getPlayer(), env).whenComplete((ok, error) -> {
            if (error != null || !Boolean.TRUE.equals(ok)) {
                user.sendMessage(FAILED);
            } else {
                user.sendMessage(PREFIX + "welcome", TextVariables.LABEL, getTopLabel());
            }
        });
        return true;
    }

    /**
     * Throws away the area being worked on.
     * @param user who asked
     * @return true if there was one
     */
    boolean discard(User user) {
        WorkshopManager manager = this.<Boxed>getAddon().getWorkshop();
        if (manager.get().isEmpty()) {
            user.sendMessage(NONE, TextVariables.LABEL, getTopLabel());
            return false;
        }
        manager.discard().thenAccept(clean -> user.sendMessage(PREFIX + (Boolean.TRUE.equals(clean) ? "discarded" : "discard-failed")));
        return true;
    }

    @Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
        Boxed addon = getAddon();
        String last = args.isEmpty() ? "" : args.get(args.size() - 1);
        List<String> options = new ArrayList<>();
        int parameter = AdminAreasCommand.parameter(this, args);
        // The first parameter says what to do, and the rest depend on it
        String sub = parameter > 1 ? args.get(args.size() - parameter).toLowerCase(Locale.ENGLISH) : "";
        if (parameter <= 1) {
            options.addAll(List.of(START, GO, DISCARD));
        } else if (sub.equals(START) && parameter == 2) {
            options.addAll(List.of(RANDOM, SAME));
        } else if (sub.equals(START) && parameter == 3) {
            options.add(SAME);
            options.addAll(BiomeStyles.list(addon.getDataFolder()));
        } else if (sub.equals(START) && parameter == 4) {
            addon.getAreas().getAreas().forEach(a -> options.add(a.id()));
        } else if (sub.equals(GO) && parameter == 2) {
            options.add(NETHER);
        } else if (sub.equals(DISCARD) && parameter == 2) {
            options.add(CONFIRM);
        }
        return Optional.of(Util.tabLimit(options, last));
    }
}
