package world.bentobox.boxed.commands;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.bukkit.World.Environment;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.workshop.PlacedStructure;
import world.bentobox.boxed.workshop.WorkshopManager;

/**
 * Lists the structures placed on the land of the area being worked on, and takes them away.
 * They are placed with the place command.
 *
 * @author tastybento
 */
public class AdminAreasStructuresCommand extends CompositeCommand {

    private static final String PREFIX = "boxed.commands.boxadmin.areas.structures.";
    private static final String REMOVE = "remove";

    /**
     * @param parent the areas command
     */
    public AdminAreasStructuresCommand(CompositeCommand parent) {
        super(parent, "structures", "structure");
    }

    @Override
    public void setup() {
        this.setPermission("admin.areas.structures");
        this.setOnlyPlayer(false);
        this.setParametersHelp(PREFIX + "parameters");
        this.setDescription(PREFIX + "description");
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        WorkshopManager manager = this.<Boxed>getAddon().getWorkshop();
        if (manager.get().isEmpty()) {
            user.sendMessage("boxed.commands.boxadmin.areas.edit.none", TextVariables.LABEL, getTopLabel());
            return false;
        }
        Environment env = environment(user, manager);
        List<PlacedStructure> list = manager.getStructures(env);
        if (args.isEmpty()) {
            list(user, list);
            return true;
        }
        if (args.size() != 2 || !REMOVE.equalsIgnoreCase(args.get(0)) || !Util.isInteger(args.get(1), true)) {
            this.showHelp(this, user);
            return false;
        }
        int number = Integer.parseInt(args.get(1));
        if (number < 1 || number > list.size()) {
            user.sendMessage(PREFIX + "no-structure");
            return false;
        }
        if (manager.isBusy() || manager.getWorld(env) == null) {
            user.sendMessage("boxed.commands.boxadmin.areas.biome.busy");
            return false;
        }
        user.sendMessage(PREFIX + "removing", TextVariables.NUMBER, String.valueOf(number));
        manager.removeStructure(env, number - 1).whenComplete((changed, error) -> {
            if (error != null) {
                getAddon().logError("Could not make the land again: " + error);
                user.sendMessage("boxed.commands.boxadmin.areas.edit.failed");
            } else {
                user.sendMessage(PREFIX + "removed");
            }
        });
        return true;
    }

    /**
     * A player in the workshop's Nether works on the Nether. Everyone else works on the overworld.
     */
    private static Environment environment(User user, WorkshopManager manager) {
        return user.isPlayer() && manager.isWorkshopWorld(user.getWorld())
                && user.getWorld().getEnvironment() == Environment.NETHER ? Environment.NETHER : Environment.NORMAL;
    }

    private void list(User user, List<PlacedStructure> list) {
        if (list.isEmpty()) {
            user.sendMessage(PREFIX + "none", TextVariables.LABEL, getTopLabel());
            return;
        }
        user.sendMessage(PREFIX + "header");
        for (int i = 0; i < list.size(); i++) {
            PlacedStructure s = list.get(i);
            user.sendMessage(PREFIX + "entry", TextVariables.NUMBER, String.valueOf(i + 1), TextVariables.NAME,
                    s.name(), TextVariables.XYZ, s.x() + "," + s.y() + "," + s.z());
        }
    }

    @Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
        WorkshopManager manager = this.<Boxed>getAddon().getWorkshop();
        String last = args.isEmpty() ? "" : args.get(args.size() - 1);
        int parameter = AdminAreasCommand.parameter(this, args);
        if (parameter == 1) {
            return Optional.of(Util.tabLimit(List.of(REMOVE), last));
        }
        if (parameter == 2 && REMOVE.equalsIgnoreCase(args.get(args.size() - 2))) {
            int count = manager.getStructures(environment(user, manager)).size();
            return Optional.of(Util.tabLimit(IntStream.rangeClosed(1, count).mapToObj(String::valueOf).toList(), last));
        }
        return Optional.empty();
    }
}
