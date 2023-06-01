package world.bentobox.boxed.commands;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.structure.Structure;

import com.google.common.base.Enums;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;
import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.listeners.NewAreaListener;
import world.bentobox.boxed.listeners.NewAreaListener.Item;

/**
 * Enables admins to place templates in a Box and have them recorded for future boxes.
 * @author tastybento
 *
 */
public class AdminPlaceStructureCommand extends CompositeCommand {

    private static final String STRUCTURE_FILE = "structures.yml";

    /**
     * Integrity determines how damaged the building should look by randomly skipping blocks to place.
     * This value can range from 0 to 1. With 0 removing all blocks and 1 spawning the structure in pristine condition.
     */
    private static final float INTEGRITY = 1;

    /**
     * The palette index of the structure to use, starting at 0, or -1 to pick a random palette.
     */
    private static final int PALETTE = -1;

    private StructureRotation sr = StructureRotation.NONE;
    private Mirror mirror = Mirror.NONE;
    private boolean noMobs;

    public AdminPlaceStructureCommand(CompositeCommand parent) {
        super(parent, "place");
    }

    @Override
    public void setup() {
        this.setPermission("boxed.commands.boxadmin.place");
        this.setOnlyPlayer(false);
        this.setParametersHelp("boxed.commands.boxadmin.place.parameters");
        this.setDescription("boxed.commands.boxadmin.place.description");


    }

    @Override
    public boolean canExecute(User user, String label, List<String> args) {
        // Initialize
        sr = StructureRotation.NONE;
        mirror = Mirror.NONE;

        // Check world
        if (!((Boxed)getAddon()).inWorld(getWorld())) {
            user.sendMessage("boxed.commands.boxadmin.place.wrong-world");
            return false;
        }
        /*
         * Acceptable syntax with number of args:
         *   1. place <structure>
         *   4. place <structure> ~ ~ ~
         *   5. place <structure> ~ ~ ~ ROTATION
         *   6. place <structure> ~ ~ ~ ROTATION MIRROR
         *   7. place <structure> ~ ~ ~ ROTATION MIRROR NO_MOBS
         */
        // Format is place <structure> ~ ~ ~ or coords
        if (args.isEmpty() || args.size() == 2 || args.size() == 3 || args.size() > 6) {
            this.showHelp(this, user);
            return false;
        }
        // First arg must always be the structure name
        List<String> options = Bukkit.getStructureManager().getStructures().keySet().stream().map(k -> k.getKey()).toList();
        if (!options.contains(args.get(0).toLowerCase(Locale.ENGLISH))) {
            user.sendMessage("boxed.commands.boxadmin.place.unknown-structure");
            return false;
        }
        // If that is all we have, we're done
        if (args.size() == 1) {
            return true;
        }
        // Next come the coordinates - there must be at least 3 of them
        if ((!args.get(1).equals("~") && !Util.isInteger(args.get(1), true))
                || (!args.get(2).equals("~") && !Util.isInteger(args.get(2), true))
                || (!args.get(3).equals("~") && !Util.isInteger(args.get(3), true))) {
            user.sendMessage("boxed.commands.boxadmin.place.use-integers");
            return false;
        }
        // If that is all we have, we're done
        if (args.size() == 4) {
            return true;
        }
        // But there is more!
        sr = Enums.getIfPresent(StructureRotation.class, args.get(4).toUpperCase(Locale.ENGLISH)).orNull();
        if (sr == null) {
            user.sendMessage("boxed.commands.boxadmin.place.unknown-rotation");
            Arrays.stream(StructureRotation.values()).map(StructureRotation::name).forEach(user::sendRawMessage);
            return false;
        }
        if (args.size() == 5) {
            return true;
        }
        // But there is more!
        mirror = Enums.getIfPresent(Mirror.class, args.get(5).toUpperCase(Locale.ENGLISH)).orNull();
        if (mirror == null) {
            user.sendMessage("boxed.commands.boxadmin.place.unknown-mirror");
            Arrays.stream(Mirror.values()).map(Mirror::name).forEach(user::sendRawMessage);
            return false;
        }
        if (args.size() == 7) {
            if (args.get(6).toUpperCase(Locale.ENGLISH).equals("NO_MOBS")) {
                noMobs = true;
            } else {
                user.sendMessage("boxed.commands.boxadmin.place.unknown", TextVariables.LABEL, args.get(6).toUpperCase(Locale.ENGLISH));
                return false;
            }
        }
        // Syntax is okay
        return true;
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        NamespacedKey tag = NamespacedKey.fromString(args.get(0).toLowerCase(Locale.ENGLISH));
        Structure s = Bukkit.getStructureManager().loadStructure(tag);
        int x = args.size() == 1 || args.get(1).equals("~") ? user.getLocation().getBlockX() : Integer.valueOf(args.get(1).trim());
        int y = args.size() == 1 || args.get(2).equals("~") ? user.getLocation().getBlockY() : Integer.valueOf(args.get(2).trim());
        int z = args.size() == 1 || args.get(3).equals("~") ? user.getLocation().getBlockZ() : Integer.valueOf(args.get(3).trim());
        Location spot = new Location(user.getWorld(), x, y, z);
        s.place(spot, true, sr, mirror, PALETTE, INTEGRITY, new Random());
        NewAreaListener.removeJigsaw(new Item(tag.getKey(), s, spot, sr, mirror, noMobs));
        boolean result = saveStructure(spot, tag, user, sr, mirror);
        if (result) {
            user.sendMessage("boxed.commands.boxadmin.place.saved");
        } else {
            user.sendMessage("boxed.commands.boxadmin.place.failed");
        }
        return result;
    }

    private boolean saveStructure(Location spot, NamespacedKey tag, User user, StructureRotation sr2, Mirror mirror2) {
        return getAddon().getIslands().getIslandAt(spot).map(i -> {
            int xx = spot.getBlockX() - i.getCenter().getBlockX();
            int zz = spot.getBlockZ() - i.getCenter().getBlockZ();
            File structures = new File(getAddon().getDataFolder(), STRUCTURE_FILE);
            YamlConfiguration config = new YamlConfiguration();
            try {
                config.load(structures);
                StringBuilder v = new StringBuilder();
                v.append(tag.getKey() + "," + sr2.name() + "," + mirror2.name());
                if (noMobs) {
                    v.append(" NO_MOBS");
                }
                config.set(spot.getWorld().getEnvironment().name().toLowerCase(Locale.ENGLISH) + "." + xx + "," + spot.getBlockY() + "," + zz, v.toString());
                config.save(structures);
            } catch (IOException | InvalidConfigurationException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                return false;
            }
            return true;
        }).orElse(false);

    }

    @Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> args)
    {
        String lastArg = !args.isEmpty() ? args.get(args.size() - 1) : "";
        if (args.size() == 2) {
            return Optional.of(Util.tabLimit(Bukkit.getStructureManager().getStructures().keySet().stream().map(k -> k.getKey()).toList(), lastArg));
        } else if (args.size() == 3) {
            return Optional.of(List.of(String.valueOf(user.getLocation().getBlockX()), "~"));
        } else if (args.size() == 4) {
            return Optional.of(List.of(String.valueOf(user.getLocation().getBlockY()), "~"));
        } else if (args.size() == 5) {
            return Optional.of(List.of(String.valueOf(user.getLocation().getBlockZ()), "~"));
        } else if (args.size() == 6) {
            return Optional.of(Arrays.stream(StructureRotation.values()).map(StructureRotation::name).toList());
        } else if (args.size() == 7) {
            return Optional.of(Arrays.stream(Mirror.values()).map(Mirror::name).toList());
        }else if (args.size() == 8) {
            return Optional.of(List.of("NO_MOBS"));
        }
        return Optional.of(Collections.emptyList());
    }
}