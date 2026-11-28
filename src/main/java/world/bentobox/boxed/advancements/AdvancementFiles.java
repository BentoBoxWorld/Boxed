package world.bentobox.boxed.advancements;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Reads the advancement files that come with Boxed, and any that an admin has put in
 * Boxed's own folder to add to them or take their place.
 *
 * @author tastybento
 */
final class AdvancementFiles {

    /**
     * Where the files are, in the jar and in Boxed's folder
     */
    static final String FOLDER = "advancements";
    private static final String JSON = ".json";

    private AdvancementFiles() {
    }

    /**
     * Reads every file.
     * @param jar Boxed's jar
     * @param dataFolder Boxed's folder
     * @return the text of each file by its path below the advancements folder, with forward
     *         slashes, for example bentobox/ocean/root.json
     * @throws IOException if the jar cannot be read
     */
    static Map<String, String> read(File jar, File dataFolder) throws IOException {
        Map<String, String> files = new TreeMap<>();
        try (JarFile file = new JarFile(jar)) {
            Enumeration<JarEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!entry.isDirectory() && name.startsWith(FOLDER + "/") && name.endsWith(JSON)) {
                    try (InputStream in = file.getInputStream(entry)) {
                        files.put(name.substring(FOLDER.length() + 1),
                                new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            }
        }
        // A file in Boxed's folder is used instead of the one of the same name in the jar
        File folder = new File(dataFolder, FOLDER);
        if (folder.isDirectory()) {
            List<Path> own = new ArrayList<>();
            try (Stream<Path> walk = Files.walk(folder.toPath())) {
                walk.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(JSON)).forEach(own::add);
            }
            for (Path path : own) {
                files.put(folder.toPath().relativize(path).toString().replace(File.separatorChar, '/'),
                        Files.readString(path));
            }
        }
        return files;
    }
}
