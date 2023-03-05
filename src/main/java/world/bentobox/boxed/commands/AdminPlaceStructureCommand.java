package world.bentobox.boxed.commands;

import java.io.File;
import java.io.IOException;
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

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;
import world.bentobox.boxed.listeners.NewAreaListener;
import world.bentobox.boxed.Boxed;

/**
 * @author tastybento
 *
 */
public class AdminPlaceStructureCommand extends CompositeCommand {

    public AdminPlaceStructureCommand(CompositeCommand parent) {
        super(parent, "place");
    }

    @Override
    public void setup() {
        this.setPermission("boxed.admin.place");
        this.setOnlyPlayer(false);
        this.setParametersHelp("boxed.admin.place.parameters");
        this.setDescription("boxed.admin.place.description");


    }

    @Override
    public boolean canExecute(User user, String label, List<String> args) {
        // Check world
        if (!((Boxed)getAddon()).inWorld(getWorld())) {
            user.sendMessage("boxed.admin.place.wrong-world");
            return false; 
        }
        // Format is place <structure> ~ ~ ~ or coords
        if (args.size() != 1 && args.size() != 4) {
            this.showHelp(this, user);
            return false;
        }
        List<String> options = Bukkit.getStructureManager().getStructures().keySet().stream().map(k -> k.getKey()).toList();
        if (!options.contains(args.get(0).toLowerCase(Locale.ENGLISH))) {
            user.sendMessage("boxed.admin.place.unknown-structure");
            return false;
        }
        if (args.size() == 1) {
            return true;
        }

        if ((!args.get(1).equals("~") && !Util.isInteger(args.get(1), true))
                || (!args.get(2).equals("~") && !Util.isInteger(args.get(2), true)) 
                || (!args.get(3).equals("~") && !Util.isInteger(args.get(3), true))) {
            user.sendMessage("boxed.admin.place.use-integers");
            return false;  
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
        s.place(spot, true, StructureRotation.NONE, Mirror.NONE, -1, 1, new Random());
        NewAreaListener.removeJigsaw(spot, s);
        getAddon().getIslands().getIslandAt(spot).ifPresent(i -> {
            int xx = x - i.getCenter().getBlockX();
            int zz = z - i.getCenter().getBlockZ();
            File structures = new File(getAddon().getDataFolder(), "structures.yml");
            YamlConfiguration config = new YamlConfiguration();
            try {
                config.load(structures);
                config.set("structures.new." + tag.getKey(), user.getWorld().getEnvironment().name().toLowerCase() + ", " + xx + ", " + y + ", " + zz);
                config.save(structures);
            } catch (IOException | InvalidConfigurationException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        });
        
        return true;
    }

    @Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> args)
    {
        String lastArg = !args.isEmpty() ? args.get(args.size() - 1) : "";
        if (args.size() == 2) {
            return Optional.of(Util.tabLimit(Bukkit.getStructureManager().getStructures().keySet().stream().map(k -> k.getKey()).toList(), lastArg));
        } else if (args.size() == 3) {
            return Optional.of(List.of("~", String.valueOf(user.getLocation().getBlockX())));
        } else if (args.size() == 4) {
            return Optional.of(List.of("~", String.valueOf(user.getLocation().getBlockY())));
        } else if (args.size() == 5) {
            return Optional.of(List.of("~", String.valueOf(user.getLocation().getBlockZ())));
        }
        return Optional.of(Collections.emptyList());
    }
}