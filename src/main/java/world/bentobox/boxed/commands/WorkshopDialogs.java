package world.bentobox.boxed.commands;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import org.bukkit.Bukkit;
import org.bukkit.World.Environment;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.areas.Area;
import world.bentobox.boxed.workshop.AreaMaker;
import world.bentobox.boxed.workshop.BiomeStyles;
import world.bentobox.boxed.workshop.Workshop;

/**
 * The dialog boxes of the area workshop. They only collect what the admin wants; the work is
 * done by {@link AdminAreasEditCommand}, which can also be driven by typing.
 *
 * @author tastybento
 */
final class WorkshopDialogs {

    private static final String PREFIX = "boxed.commands.boxadmin.areas.edit.dialog.";
    private static final String SPOTS = "boxed.commands.boxadmin.areas.spots.dialog.";
    private static final String MAKE = "boxed.commands.boxadmin.areas.make.dialog.";
    private static final String NAME = "name";
    private static final String BORDER = "border";
    private static final String[] NO_VARIABLES = {};
    private static final String SEED = "seed";
    private static final String STYLE = "style";
    private static final String AREA = "area";
    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder().uses(1)
            .lifetime(Duration.ofMinutes(30)).build();

    private final AdminAreasEditCommand command;

    WorkshopDialogs(AdminAreasEditCommand command) {
        this.command = command;
    }

    private static Component text(User user, String key, String... variables) {
        return user.getTranslationAsComponent(PREFIX + key, variables);
    }

    private ActionButton button(User user, String key, Consumer<DialogResponseView> action) {
        return button(command.getPlugin(), text(user, key), null, action);
    }

    private static ActionButton button(Plugin plugin, Component label, Component tooltip,
            Consumer<DialogResponseView> action) {
        // What a button does is run on the main thread, whichever thread the click arrives on
        ActionButton.Builder builder = ActionButton.builder(label).action(DialogAction.customClick(
                (view, audience) -> Bukkit.getScheduler().runTask(plugin, () -> action.accept(view)), ONCE));
        return tooltip == null ? builder.build() : builder.tooltip(tooltip).build();
    }

    /**
     * Shows the places found so far as numbered buttons. Clicking one makes it the middle of
     * every box and takes the player there.
     * @param plugin plugin to schedule with
     * @param user a player
     * @param workshop the area being worked on
     * @param select what to do with the number of the place that is clicked, counting from 1
     * @param border what to do when the border button is clicked
     */
    static void showSpots(Plugin plugin, User user, Workshop workshop, IntConsumer select, Runnable border) {
        List<Workshop.Spot> spots = workshop.getSpots();
        int[] centre = workshop.getCentre(Environment.NORMAL);
        List<ActionButton> buttons = new ArrayList<>();
        for (int i = 0; i < spots.size(); i++) {
            Workshop.Spot spot = spots.get(i);
            if (!spot.scored()) {
                continue;
            }
            int number = i + 1;
            boolean picked = workshop.hasCentre(Environment.NORMAL) && spot.x() == centre[0] && spot.z() == centre[1];
            String[] variables = { TextVariables.NUMBER, String.valueOf(number), TextVariables.XYZ,
                    spot.x() + "," + spot.z(), "[score]", String.valueOf(spot.score()), "[biome]", spot.biome(),
                    TextVariables.DESCRIPTION, spot.description() };
            buttons.add(button(plugin, user.getTranslationAsComponent(SPOTS + (picked ? "button-picked" : "button"), variables),
                    user.getTranslationAsComponent(SPOTS + "tooltip", variables), view -> select.accept(number)));
        }
        buttons.add(button(plugin, user.getTranslationAsComponent(SPOTS + BORDER, NO_VARIABLES), null, view -> border.run()));
        long waiting = spots.stream().filter(s -> !s.scored()).count();
        DialogBase base = DialogBase.builder(user.getTranslationAsComponent(SPOTS + "title", NO_VARIABLES)).canCloseWithEscape(true)
                .body(List.of(DialogBody.plainMessage(user.getTranslationAsComponent(
                        SPOTS + (waiting > 0 ? "body-looking" : "body"), TextVariables.NUMBER, String.valueOf(waiting)))))
                .build();
        user.getPlayer().showDialog(Dialog.create(b -> b.empty().base(base).type(DialogType.multiAction(buttons)
                .exitAction(ActionButton.builder(user.getTranslationAsComponent(SPOTS + "close", NO_VARIABLES)).build()).columns(2)
                .build())));
    }

    private static ActionButton close(User user, String key) {
        return ActionButton.builder(text(user, key)).build();
    }

    /**
     * Asks for the name of the area the workshop is to become.
     * @param plugin plugin to schedule with
     * @param user a player
     * @param workshop the area being worked on
     * @param go what to do with the name
     */
    static void showMakeName(Plugin plugin, User user, Workshop workshop, Consumer<String> go) {
        DialogBase base = DialogBase.builder(user.getTranslationAsComponent(MAKE + "name-title", NO_VARIABLES))
                .canCloseWithEscape(true)
                .body(List.of(DialogBody.plainMessage(user.getTranslationAsComponent(MAKE + "name-body", NO_VARIABLES))))
                .inputs(List.of(DialogInput.text(NAME, user.getTranslationAsComponent(MAKE + NAME, NO_VARIABLES))
                        .initial(workshop.getArea() == null ? "" : workshop.getArea()).maxLength(32).build()))
                .build();
        user.getPlayer().showDialog(Dialog.create(b -> b.empty().base(base).type(DialogType.confirmation(
                button(plugin, user.getTranslationAsComponent(MAKE + "check", NO_VARIABLES), null,
                        view -> go.accept(view.getText(NAME))),
                ActionButton.builder(user.getTranslationAsComponent(MAKE + "cancel", NO_VARIABLES)).build()))));
    }

    /**
     * Shows how far a box can grow in the land that was copied and asks for the go-ahead.
     * @param plugin plugin to schedule with
     * @param user a player
     * @param prepared the land
     * @param replace true if an area of that name exists and its land will be replaced
     * @param make what to do if the player says yes
     */
    static void showMakeConfirm(Plugin plugin, User user, AreaMaker.Prepared prepared, boolean replace,
            Runnable make) {
        DialogBase base = DialogBase
                .builder(user.getTranslationAsComponent(MAKE + "confirm-title", TextVariables.NAME, prepared.name()))
                .canCloseWithEscape(true)
                .body(List.of(DialogBody.plainMessage(user.getTranslationAsComponent(
                        MAKE + (replace ? "confirm-body-replace" : "confirm-body"), TextVariables.DESCRIPTION,
                        prepared.summary(), TextVariables.NAME, prepared.name()))))
                .build();
        user.getPlayer().showDialog(Dialog.create(b -> b.empty().base(base).type(DialogType.confirmation(
                button(plugin, user.getTranslationAsComponent(MAKE + "make", NO_VARIABLES), null, view -> make.run()),
                ActionButton.builder(user.getTranslationAsComponent(MAKE + "not-yet", NO_VARIABLES)).build()))));
    }

    /**
     * Asks what to start work on: a new area or an existing one, the seed and the biome style.
     */
    void showStart(User user) {
        Boxed addon = command.getAddon();
        List<OptionEntry> areas = new ArrayList<>();
        areas.add(OptionEntry.create(AdminAreasEditCommand.NEW, text(user, "new-area"), true));
        for (Area area : addon.getAreas().getAreas()) {
            areas.add(OptionEntry.create(area.id(), Component.text(area.id()), false));
        }
        List<OptionEntry> styles = new ArrayList<>();
        styles.add(OptionEntry.create(AdminAreasEditCommand.SAME, text(user, "same-style"), true));
        for (String style : BiomeStyles.list(addon.getDataFolder())) {
            styles.add(OptionEntry.create(style, Component.text(style), false));
        }
        DialogBase base = DialogBase.builder(text(user, "start-title")).canCloseWithEscape(true)
                .body(List.of(DialogBody.plainMessage(text(user, "start-body"))))
                .inputs(List.of(DialogInput.singleOption(AREA, text(user, AREA), areas).build(),
                        DialogInput.text(SEED, text(user, SEED)).initial("").maxLength(24).build(),
                        DialogInput.singleOption(STYLE, text(user, STYLE), styles).build()))
                .build();
        user.getPlayer().showDialog(Dialog.create(b -> b.empty().base(base).type(DialogType.confirmation(
                button(user, "go", view -> command.start(user, view.getText(SEED), view.getText(STYLE),
                        view.getText(AREA))),
                close(user, "cancel")))));
    }

    /**
     * Shows the area being worked on, with buttons to go to it or throw it away.
     */
    void showStatus(User user, Workshop workshop) {
        Boxed addon = command.getAddon();
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(user, "go-overworld", view -> command.go(user, Environment.NORMAL)));
        if (addon.getWorkshop().hasDimension(Environment.NETHER)) {
            buttons.add(button(user, "go-nether", view -> command.go(user, Environment.NETHER)));
        }
        buttons.add(button(user, "discard", view -> showDiscard(user)));
        DialogBase base = DialogBase.builder(text(user, "status-title")).canCloseWithEscape(true)
                .body(List.of(DialogBody.plainMessage(text(user, "status-body", "[seed]",
                        String.valueOf(workshop.getSeed()), "[style]", BiomeStyles.name(workshop.getBiomes()),
                        "[area]", workshop.getArea() == null ? "-" : workshop.getArea()))))
                .build();
        user.getPlayer().showDialog(Dialog.create(b -> b.empty().base(base)
                .type(DialogType.multiAction(buttons).exitAction(close(user, "save-for-later")).columns(1).build())));
    }

    private void showDiscard(User user) {
        DialogBase base = DialogBase.builder(text(user, "discard-title")).canCloseWithEscape(true)
                .body(List.of(DialogBody.plainMessage(text(user, "discard-body")))).build();
        user.getPlayer().showDialog(Dialog.create(b -> b.empty().base(base).type(DialogType.confirmation(
                button(user, "discard", view -> command.discard(user)), close(user, "cancel")))));
    }
}
