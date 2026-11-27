package world.bentobox.boxed.workshop;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.eclipse.jdt.annotation.Nullable;

/**
 * The biome layouts an admin can pick from. A style is a biomes file: {@code biomes.yml} is
 * the style called "original" and every file in the {@code biomes} folder is a style named
 * after the file. Admins make their own by copying one of those files.
 *
 * @author tastybento
 */
public final class BiomeStyles {

    /**
     * Name of the style that Boxed has always had
     */
    public static final String ORIGINAL = "original";
    /**
     * Folder that holds the other styles
     */
    public static final String FOLDER = "biomes";
    private static final String ORIGINAL_FILE = "biomes.yml";
    private static final String YML = ".yml";

    private BiomeStyles() {
    }

    /**
     * @param dataFolder the addon's folder
     * @return the names of the styles, the original first
     */
    public static List<String> list(File dataFolder) {
        List<String> names = new ArrayList<>();
        names.add(ORIGINAL);
        String[] files = new File(dataFolder, FOLDER).list((dir, name) -> name.endsWith(YML));
        if (files != null) {
            Arrays.sort(files);
            for (String file : files) {
                names.add(file.substring(0, file.length() - YML.length()));
            }
        }
        return names;
    }

    /**
     * @param dataFolder the addon's folder
     * @param style name of a style, or the name of a biomes file in the addon's folder
     * @return the biomes file relative to the addon's folder, or null if there is no such style
     */
    @Nullable
    public static String file(File dataFolder, String style) {
        if (ORIGINAL.equalsIgnoreCase(style)) {
            return ORIGINAL_FILE;
        }
        // Names come from commands, so they must not be able to point outside the folder
        if (!style.matches("[A-Za-z0-9_.-]+") || style.contains("..")) {
            return null;
        }
        String inFolder = FOLDER + "/" + style.toLowerCase(Locale.ENGLISH) + YML;
        if (new File(dataFolder, inFolder).isFile()) {
            return inFolder;
        }
        return style.endsWith(YML) && new File(dataFolder, style).isFile() ? style : null;
    }

    /**
     * @param file a biomes file relative to the addon's folder
     * @return the name of the style it is
     */
    public static String name(String file) {
        if (ORIGINAL_FILE.equals(file)) {
            return ORIGINAL;
        }
        String prefix = FOLDER + "/";
        return file.startsWith(prefix) && file.endsWith(YML)
                ? file.substring(prefix.length(), file.length() - YML.length())
                : file;
    }
}
