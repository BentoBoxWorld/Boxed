package world.bentobox.boxed.commands;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.IntStream;

import org.bukkit.Location;
import org.bukkit.World.Environment;
import org.bukkit.configuration.file.YamlConfiguration;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.generators.biomes.BiomePatch;
import world.bentobox.boxed.reach.BiomeReport;
import world.bentobox.boxed.reach.Requirements;
import world.bentobox.boxed.workshop.Workshop;
import world.bentobox.boxed.workshop.WorkshopManager;

/**
 * Shows which biomes the box being worked on has and which it lacks, and puts patches of
 * biome on the land or takes them away.
 *
 * @author tastybento
 */
public class AdminAreasBiomeCommand extends CompositeCommand {

    private static final String PREFIX = "boxed.commands.boxadmin.areas.biome.";
    private static final String REQUIREMENTS_FILE = "requirements.yml";
    private static final String ADD = "add";
    private static final String REMOVE = "remove";
    private static final String BIOME = "[biome]";
    private static final String FAILED = "boxed.commands.boxadmin.areas.edit.failed";
    private static final int DEFAULT_RADIUS = 48;

    /**
     * @param parent the areas command
     */
    public AdminAreasBiomeCommand(CompositeCommand parent) {
        super(parent, "biome", "biomes");
    }

    @Override
    public void setup() {
        this.setPermission("admin.areas.biome");
        this.setOnlyPlayer(false);
        this.setParametersHelp(PREFIX + "parameters");
        this.setDescription(PREFIX + "description");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        WorkshopManager manager = this.<Boxed>getAddon().getWorkshop();
        Optional<Workshop> workshop = manager.get();
        if (workshop.isEmpty()) {
            user.sendMessage("boxed.commands.boxadmin.areas.edit.none", TextVariables.LABEL, getTopLabel());
            return false;
        }
        // A player in the workshop's Nether works on the Nether. Everyone else works on the overworld.
        Environment env = user.isPlayer() && manager.isWorkshopWorld(user.getWorld())
                && user.getWorld().getEnvironment() == Environment.NETHER ? Environment.NETHER : Environment.NORMAL;
        if (manager.getWorld(env) == null) {
            user.sendMessage(FAILED);
            return false;
        }
        if (args.isEmpty()) {
            return report(user, workshop.get(), env);
        }
        if (manager.isBusy()) {
            user.sendMessage(PREFIX + "busy");
            return false;
        }
        String action = args.get(0).toLowerCase(Locale.ENGLISH);
        if (ADD.equals(action) && args.size() >= 2 && args.size() <= 5 && args.size() != 4) {
            return add(user, env, args);
        }
        if (REMOVE.equals(action) && args.size() == 2 && Util.isInteger(args.get(1), true)) {
            return remove(user, workshop.get(), env, Integer.parseInt(args.get(1)));
        }
        this.showHelp(this, user);
        return false;
    }

    /**
     * Lists the patches, then looks at the land of the box and says which biomes it has and
     * which it lacks.
     */
    private boolean report(User user, Workshop workshop, Environment env) {
        Boxed addon = getAddon();
        List<BiomePatch> patches = workshop.getPatches(env);
        for (int i = 0; i < patches.size(); i++) {
            BiomePatch p = patches.get(i);
            user.sendMessage(PREFIX + "patch", TextVariables.NUMBER, String.valueOf(i + 1), BIOME, p.biome(),
                    "[radius]", String.valueOf(p.radius()), TextVariables.XYZ, p.x() + "," + p.z());
        }
        File folder = addon.getDataFolder();
        if (!new File(folder, REQUIREMENTS_FILE).exists()) {
            addon.saveResource(REQUIREMENTS_FILE, false);
        }
        Requirements requirements = new Requirements(
                YamlConfiguration.loadConfiguration(new File(folder, REQUIREMENTS_FILE)), addon::logWarning);
        user.sendMessage(PREFIX + "looking");
        addon.getWorkshop().surveyBiomes(env, percent -> {
            if (user.isPlayer() && user.isOnline()) {
                user.getPlayer().sendActionBar(user.getTranslationAsComponent(PREFIX + "progress",
                        TextVariables.NUMBER, String.valueOf(percent)));
            }
        }).whenComplete((survey, error) -> {
            if (error != null) {
                addon.logError("Could not look at the biomes: " + error);
                user.sendMessage(FAILED);
                return;
            }
            show(user, BiomeReport.of(survey, requirements, env == Environment.NETHER));
        });
        return true;
    }

    private void show(User user, BiomeReport report) {
        user.sendMessage(PREFIX + "present");
        StringBuilder line = new StringBuilder();
        report.present().forEach((name, size) -> line.append(line.isEmpty() ? "" : ", ").append(name).append(" (")
                .append(size).append(")"));
        user.sendMessage(PREFIX + "present-list", TextVariables.DESCRIPTION, line.toString());
        if (report.missing().isEmpty()) {
            user.sendMessage(PREFIX + "nothing-missing");
            return;
        }
        user.sendMessage(PREFIX + "missing");
        for (BiomeReport.Missing missing : report.missing()) {
            user.sendMessage(PREFIX + "missing-entry", BIOME, String.join(", ", missing.biomes()),
                    TextVariables.DESCRIPTION, String.join(", ", missing.wantedFor()));
        }
        user.sendMessage(PREFIX + "how", TextVariables.LABEL, getTopLabel());
    }

    /**
     * Puts a patch on the land: add &lt;biome&gt; [radius] [x z]
     */
    private boolean add(User user, Environment env, List<String> args) {
        WorkshopManager manager = this.<Boxed>getAddon().getWorkshop();
        String biome = args.get(1).toLowerCase(Locale.ENGLISH).replace("minecraft:", "");
        if (!manager.getPatchBiomes(env).contains(biome)) {
            user.sendMessage(PREFIX + "unknown-biome");
            return false;
        }
        int radius = DEFAULT_RADIUS;
        if (args.size() >= 3) {
            if (!Util.isInteger(args.get(2), true)) {
                this.showHelp(this, user);
                return false;
            }
            radius = Integer.parseInt(args.get(2));
        }
        int x;
        int z;
        if (args.size() == 5) {
            if (!Util.isInteger(args.get(3), true) || !Util.isInteger(args.get(4), true)) {
                this.showHelp(this, user);
                return false;
            }
            x = Integer.parseInt(args.get(3));
            z = Integer.parseInt(args.get(4));
        } else if (user.isPlayer() && manager.isWorkshopWorld(user.getWorld())) {
            Location at = user.getLocation();
            x = at.getBlockX();
            z = at.getBlockZ();
        } else {
            user.sendMessage(PREFIX + "where");
            return false;
        }
        BiomePatch patch = new BiomePatch(x, z, radius, biome);
        user.sendMessage(PREFIX + "adding", BIOME, patch.biome(), "[radius]", String.valueOf(patch.radius()),
                TextVariables.XYZ, x + "," + z);
        manager.addPatch(env, patch).whenComplete((changed, error) -> done(user, changed, error));
        return true;
    }

    private boolean remove(User user, Workshop workshop, Environment env, int number) {
        if (number < 1 || number > workshop.getPatches(env).size()) {
            user.sendMessage(PREFIX + "no-patch");
            return false;
        }
        user.sendMessage(PREFIX + "removing", TextVariables.NUMBER, String.valueOf(number));
        this.<Boxed>getAddon().getWorkshop().removePatch(env, number - 1)
                .whenComplete((changed, error) -> done(user, changed, error));
        return true;
    }

    private void done(User user, Integer changed, Throwable error) {
        if (error != null) {
            getAddon().logError("Could not make the land again: " + error);
            user.sendMessage(FAILED);
        } else {
            user.sendMessage(PREFIX + "done", TextVariables.NUMBER, String.valueOf(changed), TextVariables.LABEL,
                    getTopLabel());
        }
    }

    @Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
        WorkshopManager manager = this.<Boxed>getAddon().getWorkshop();
        String last = args.isEmpty() ? "" : args.get(args.size() - 1);
        int parameter = AdminAreasCommand.parameter(this, args);
        if (parameter == 1) {
            return Optional.of(Util.tabLimit(List.of(ADD, REMOVE), last));
        }
        // What was typed for the first parameter
        String action = args.get(args.size() - 2).toLowerCase(Locale.ENGLISH);
        if (parameter == 2 && ADD.equals(action)) {
            Environment env = user.isPlayer() && user.getWorld().getEnvironment() == Environment.NETHER
                    ? Environment.NETHER
                    : Environment.NORMAL;
            return Optional.of(Util.tabLimit(manager.getPatchBiomes(env), last));
        }
        if (parameter == 2 && REMOVE.equals(action)) {
            int patches = manager.get().map(w -> w.getPatches(Environment.NORMAL).size()).orElse(0);
            return Optional.of(Util.tabLimit(
                    IntStream.rangeClosed(1, patches).mapToObj(String::valueOf).toList(), last));
        }
        return Optional.empty();
    }
}
