package world.bentobox.boxed.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.World.Environment;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.workshop.Workshop;
import world.bentobox.boxed.workshop.WorkshopManager;

/**
 * Shows the places the workshop has found that could be the middle of every box, and makes
 * one of them the middle. Players get a dialog box of buttons; the places can also be
 * listed and picked by typing.
 *
 * @author tastybento
 */
public class AdminAreasSpotsCommand extends CompositeCommand {

    private static final String PREFIX = "boxed.commands.boxadmin.areas.spots.";
    private static final String BORDER = "border";
    private static final String NETHER = "nether";

    /**
     * @param parent the areas command
     */
    public AdminAreasSpotsCommand(CompositeCommand parent) {
        super(parent, "spots");
    }

    @Override
    public void setup() {
        this.setPermission("admin.areas.spots");
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
        // A player in the workshop's Nether works on the Nether, and so does anyone who says so
        boolean named = !args.isEmpty() && NETHER.equalsIgnoreCase(args.get(0));
        Environment env = named || (user.isPlayer() && manager.isWorkshopWorld(user.getWorld())
                && user.getWorld().getEnvironment() == Environment.NETHER) ? Environment.NETHER : Environment.NORMAL;
        List<String> rest = named ? args.subList(1, args.size()) : args;
        if (rest.size() > 1) {
            this.showHelp(this, user);
            return false;
        }
        if (env == Environment.NETHER && manager.getWorld(env) == null) {
            // The Nether is searched when its world is made
            user.sendMessage(PREFIX + "no-nether");
            return false;
        }
        if (rest.isEmpty()) {
            show(user, workshop.get(), env);
            return true;
        }
        if (BORDER.equalsIgnoreCase(rest.get(0))) {
            if (!user.isPlayer()) {
                user.sendMessage("general.errors.use-in-game");
                return false;
            }
            return border(user);
        }
        if (!Util.isInteger(rest.get(0), true)) {
            this.showHelp(this, user);
            return false;
        }
        return select(user, env, Integer.parseInt(rest.get(0)));
    }

    /**
     * Shows the places found so far: buttons for a player, a list for the console.
     */
    private void show(User user, Workshop workshop, Environment env) {
        List<Workshop.Spot> spots = workshop.getSpots(env);
        if (spots.stream().noneMatch(Workshop.Spot::scored)) {
            user.sendMessage(PREFIX + (env == Environment.NETHER ? "looking-nether" : "looking"));
            return;
        }
        if (user.isPlayer()) {
            WorkshopDialogs.showSpots(getPlugin(), user, workshop, env, number -> select(user, env, number),
                    () -> border(user));
            return;
        }
        int[] centre = workshop.getCentre(env);
        for (int i = 0; i < spots.size(); i++) {
            Workshop.Spot spot = spots.get(i);
            if (spot.scored()) {
                boolean picked = workshop.hasCentre(env) && spot.x() == centre[0] && spot.z() == centre[1];
                user.sendMessage(PREFIX + (picked ? "entry-picked" : "entry"), TextVariables.NUMBER,
                        String.valueOf(i + 1), TextVariables.XYZ, spot.x() + "," + spot.z(), "[score]",
                        String.valueOf(spot.score()), "[biome]", spot.biome(), TextVariables.DESCRIPTION,
                        spot.description());
            }
        }
        long waiting = spots.stream().filter(s -> !s.scored()).count();
        if (waiting > 0) {
            user.sendMessage(PREFIX + "more-coming", TextVariables.NUMBER, String.valueOf(waiting));
        }
    }

    /**
     * Makes a place the middle of every box and takes the player there.
     * @param user a player
     * @param env dimension
     * @param number the place's number, counting from 1
     * @return true if there is such a place
     */
    boolean select(User user, Environment env, int number) {
        WorkshopManager manager = this.<Boxed>getAddon().getWorkshop();
        List<Workshop.Spot> spots = manager.get().map(w -> w.getSpots(env)).orElse(List.of());
        if (number < 1 || number > spots.size() || !spots.get(number - 1).scored()) {
            user.sendMessage(PREFIX + "unknown");
            return false;
        }
        Workshop.Spot spot = spots.get(number - 1);
        manager.select(user.isPlayer() ? user.getPlayer() : null, env, number - 1).whenComplete((ok, error) -> {
            if (error != null || !Boolean.TRUE.equals(ok)) {
                user.sendMessage("boxed.commands.boxadmin.areas.edit.failed");
            } else {
                user.sendMessage(PREFIX + (env == Environment.NETHER ? "selected-nether" : "selected"), TextVariables.NUMBER, String.valueOf(number), TextVariables.XYZ,
                        spot.x() + "," + spot.z(), TextVariables.LABEL, getTopLabel());
            }
        });
        return true;
    }

    /**
     * Shows or hides the border that marks how far the box can reach.
     */
    boolean border(User user) {
        boolean show = user.getPlayer().getWorldBorder() == null;
        this.<Boxed>getAddon().getWorkshop().showCentre(user.getPlayer(), show);
        boolean shown = user.getPlayer().getWorldBorder() != null;
        if (show && !shown) {
            user.sendMessage(PREFIX + "no-centre");
            return false;
        }
        user.sendMessage(PREFIX + (shown ? "border-on" : "border-off"));
        return true;
    }

    @Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
        WorkshopManager manager = this.<Boxed>getAddon().getWorkshop();
        int parameter = AdminAreasCommand.parameter(this, args);
        boolean named = parameter == 2 && NETHER.equalsIgnoreCase(args.get(args.size() - 2));
        if (parameter > 1 && !named) {
            return Optional.empty();
        }
        Environment env = named || (user.isPlayer() && manager.isWorkshopWorld(user.getWorld())
                && user.getWorld().getEnvironment() == Environment.NETHER) ? Environment.NETHER : Environment.NORMAL;
        List<String> options = new ArrayList<>();
        if (!named) {
            options.add(BORDER);
            options.add(NETHER);
        }
        List<Workshop.Spot> spots = manager.get().map(w -> w.getSpots(env)).orElse(List.of());
        for (int i = 0; i < spots.size(); i++) {
            if (spots.get(i).scored()) {
                options.add(String.valueOf(i + 1));
            }
        }
        return Optional.of(Util.tabLimit(options, args.isEmpty() ? "" : args.get(args.size() - 1)));
    }
}
