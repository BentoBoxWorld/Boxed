package world.bentobox.boxed.commands;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.areas.Area;
import world.bentobox.boxed.workshop.AreaMaker;
import world.bentobox.boxed.workshop.AreaMaker.Prepared;
import world.bentobox.boxed.workshop.Workshop;

/**
 * Makes the workshop into an area. The land is copied and checked first and the admin is
 * shown how far a box can grow in it. Only when they confirm does the area come to exist,
 * and players can then pick it without a restart.
 *
 * @author tastybento
 */
public class AdminAreasMakeCommand extends CompositeCommand {

    private static final String PREFIX = "boxed.commands.boxadmin.areas.make.";
    private static final String CONFIRM = "confirm";
    private static final String FAILED = PREFIX + "failed";

    private AreaMaker maker;
    private Prepared prepared;

    /**
     * @param parent the areas command
     */
    public AdminAreasMakeCommand(CompositeCommand parent) {
        super(parent, "make");
    }

    @Override
    public void setup() {
        this.setPermission("admin.areas.make");
        this.setOnlyPlayer(false);
        this.setParametersHelp(PREFIX + "parameters");
        this.setDescription(PREFIX + "description");
    }

    private AreaMaker maker() {
        if (maker == null) {
            Boxed addon = getAddon();
            maker = new AreaMaker(addon, addon.getWorkshop());
        }
        return maker;
    }

    /**
     * For tests
     * @param maker the maker to use
     */
    void setMaker(AreaMaker maker) {
        this.maker = maker;
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        Optional<Workshop> workshop = this.<Boxed>getAddon().getWorkshop().get();
        if (workshop.isEmpty()) {
            user.sendMessage("boxed.commands.boxadmin.areas.edit.none", TextVariables.LABEL, getTopLabel());
            return false;
        }
        if (args.isEmpty() && user.isPlayer()) {
            WorkshopDialogs.showMakeName(getPlugin(), user, workshop.get(), name -> prepare(user, name));
            return true;
        }
        if (args.isEmpty() || args.size() > 2 || (args.size() == 2 && !CONFIRM.equalsIgnoreCase(args.get(1)))) {
            this.showHelp(this, user);
            return false;
        }
        return args.size() == 2 ? confirm(user, args.get(0)) : prepare(user, args.get(0));
    }

    /**
     * Copies and checks the land, then asks for the go-ahead.
     * @param user who asked
     * @param typedName name for the area
     * @return true if the copying started
     */
    boolean prepare(User user, String typedName) {
        String name = typedName == null ? "" : typedName.trim().toLowerCase(Locale.ENGLISH);
        AreaMaker.Problem problem = maker().problem(name);
        if (problem != null) {
            user.sendMessage(PREFIX + problem.name().toLowerCase(Locale.ENGLISH).replace('_', '-'), TextVariables.NAME,
                    name, TextVariables.LABEL, getTopLabel());
            return false;
        }
        prepared = null;
        user.sendMessage(PREFIX + "started", TextVariables.NAME, name);
        int[] last = { -1 };
        maker().prepare(name, percent -> progress(user, percent, last)).whenComplete((ready, error) -> {
            if (error != null) {
                getAddon().logError("Could not copy the land of the workshop: " + error);
                user.sendMessage(FAILED);
                return;
            }
            prepared = ready;
            Area existing = this.<Boxed>getAddon().getAreas().getArea(name);
            user.sendMessage(PREFIX + "result", TextVariables.DESCRIPTION, ready.summary());
            if (user.isPlayer()) {
                WorkshopDialogs.showMakeConfirm(getPlugin(), user, ready, existing != null, () -> confirm(user, name));
            } else {
                user.sendMessage(PREFIX + (existing == null ? "confirm-how" : "confirm-how-replace"),
                        TextVariables.NAME, name, TextVariables.LABEL, getTopLabel());
            }
        });
        return true;
    }

    /**
     * Players see the progress above the hotbar. The console is told every quarter.
     */
    private void progress(User user, int percent, int[] last) {
        if (user.isPlayer()) {
            user.getPlayer().sendActionBar(user.getTranslationAsComponent(PREFIX + "progress", TextVariables.NUMBER,
                    String.valueOf(percent)));
        } else if (percent / 25 != last[0] / 25 || last[0] < 0) {
            user.sendMessage(PREFIX + "progress", TextVariables.NUMBER, String.valueOf(percent));
        }
        if (percent >= 100 && last[0] < 100) {
            user.sendMessage(PREFIX + "checking");
        }
        last[0] = percent;
    }

    /**
     * Makes the area from the land that was copied.
     * @param user who asked
     * @param typedName name of the area, which must be the one the land was copied for
     * @return true if the area was made
     */
    boolean confirm(User user, String typedName) {
        String name = typedName.trim().toLowerCase(Locale.ENGLISH);
        Prepared ready = prepared;
        if (ready == null || !ready.name().equals(name)) {
            user.sendMessage(PREFIX + "not-prepared", TextVariables.NAME, name, TextVariables.LABEL, getTopLabel());
            return false;
        }
        prepared = null;
        try {
            Area area = maker().finish(ready);
            user.sendMessage(PREFIX + "success", TextVariables.NAME, area.id());
            return true;
        } catch (IOException e) {
            getAddon().logError("Could not make area " + name + ": " + e.getMessage());
            user.sendMessage(FAILED);
            return false;
        }
    }

    @Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
        if (AdminAreasCommand.parameter(this, args) == 2) {
            return Optional.of(Util.tabLimit(List.of(CONFIRM), args.get(args.size() - 1)));
        }
        return Optional.empty();
    }
}
