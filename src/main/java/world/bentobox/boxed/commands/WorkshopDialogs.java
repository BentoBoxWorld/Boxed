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
