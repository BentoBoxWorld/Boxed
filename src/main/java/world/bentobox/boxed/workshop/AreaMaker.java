package world.bentobox.boxed.workshop;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntConsumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.configuration.file.YamlConfiguration;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.areas.Area;
import world.bentobox.boxed.areas.AreaManager;
import world.bentobox.boxed.commands.AdminAreasCommand;
import world.bentobox.boxed.generators.template.Template;
import world.bentobox.boxed.generators.template.TemplateBuilder;
import world.bentobox.boxed.reach.AreaCheck;

/**
 * Turns the workshop into an area that players can use straight away. It is done in two
 * parts so that the admin can see what they are about to make: {@link #prepare} copies the
 * land and works out how far a box can grow in it, and {@link #finish} makes the area.
 *
 * @author tastybento
 */
public class AreaMaker {

    /**
     * Why an area cannot be made
     */
    public enum Problem {
        NO_WORKSHOP, BUSY, NO_CENTRE, INVALID_NAME, HAS_BOXES
    }

    /**
     * Land that has been copied and checked and is ready to become an area.
     * @param name name of the area
     * @param files the template file of each dimension
     * @param summary how far a box can grow in the land, in a sentence
     * @param workshop the workshop the land was copied from
     * @param centre block x and z of the middle of the overworld land
     * @param netherCentre block x and z of the middle of the Nether land
     */
    public record Prepared(String name, Map<Environment, File> files, String summary, Workshop workshop,
            Centre centre, Centre netherCentre) {
    }

    /**
     * The middle of the land in one dimension.
     * @param x block x
     * @param z block z
     */
    public record Centre(int x, int z) {
        static Centre of(int[] xz) {
            return new Centre(xz[0], xz[1]);
        }

        int[] xz() {
            return new int[] { x, z };
        }
    }

    private static final String STAGING = "workshop-build";
    private static final String AREAS = "areas.";
    /**
     * How many box positions each side of the middle of a row are put right when an area's
     * land is replaced
     */
    private static final int CLEAN_COLUMNS = 8;

    private final Boxed addon;
    private final WorkshopManager manager;

    /**
     * @param addon Boxed
     * @param manager the workshop
     */
    public AreaMaker(Boxed addon, WorkshopManager manager) {
        this.addon = addon;
        this.manager = manager;
    }

    /**
     * Checks whether the workshop can be made into an area with a name.
     * @param name name for the area
     * @return what is in the way, or null if nothing is
     */
    @Nullable
    public Problem problem(String name) {
        Workshop workshop = manager.get().orElse(null);
        if (workshop == null) {
            return Problem.NO_WORKSHOP;
        }
        if (manager.isBusy()) {
            return Problem.BUSY;
        }
        if (!workshop.hasCentre(Environment.NORMAL)) {
            return Problem.NO_CENTRE;
        }
        if (!AreaManager.isValidName(name)) {
            return Problem.INVALID_NAME;
        }
        Area existing = addon.getAreas().getArea(name);
        // The land of an area can only be replaced while nobody has a box in it
        return existing != null && countBoxes(existing) > 0 ? Problem.HAS_BOXES : null;
    }

    /**
     * @param area area
     * @return how many boxes players have in the area
     */
    public long countBoxes(Area area) {
        World overWorld = addon.getOverWorld();
        return overWorld == null ? 0
                : addon.getIslands().getIslands(overWorld).stream().filter(i -> i.getOwner() != null)
                        .filter(i -> addon.getAreaAt(i.getCenter().getBlockZ() >> 4).equals(area)).count();
    }

    /**
     * Copies the land of the workshop and works out how far a box can grow in it. This takes
     * a while, but the server keeps running. Nothing is changed that players can see.
     * @param name name for the area. Check it with {@link #problem(String)} first.
     * @param progress told the percentage done as it goes up
     * @return the land, ready to become an area
     */
    public CompletableFuture<Prepared> prepare(String name, IntConsumer progress) {
        Workshop workshop = manager.get().orElseThrow();
        manager.setBusy(true);
        File staging = new File(addon.getDataFolder(), "areas" + File.separator + STAGING);
        boolean nether = manager.hasDimension(Environment.NETHER);
        Map<Environment, File> files = new EnumMap<>(Environment.class);
        CompletableFuture<Prepared> result = build(workshop, Environment.NORMAL, staging, files,
                p -> progress.accept(nether ? p / 2 : p))
                        .thenCompose(v -> nether ? build(workshop, Environment.NETHER, staging, files,
                                p -> progress.accept(50 + p / 2)) : CompletableFuture.completedFuture(null))
                        .thenCompose(v -> check(name, files))
                        .thenApply(summary -> new Prepared(name, files, summary, workshop,
                                Centre.of(workshop.getCentre(Environment.NORMAL)),
                                Centre.of(workshop.getCentre(Environment.NETHER))));
        // Whatever happens, the workshop can be used again afterwards
        return result.whenComplete((p, error) -> manager.setBusy(false));
    }

    private CompletableFuture<Void> build(Workshop workshop, Environment env, File staging,
            Map<Environment, File> files, IntConsumer progress) {
        World world = manager.getWorld(env);
        if (world == null) {
            return CompletableFuture.failedFuture(new IOException("There is no " + env + " world to copy"));
        }
        File file = new File(staging, env.name().toLowerCase(Locale.ENGLISH) + ".bxt");
        files.put(env, file);
        int[] centre = workshop.getCentre(env);
        try {
            Files.createDirectories(staging.toPath());
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
        return new TemplateBuilder(world, centre[0] >> 4, centre[1] >> 4,
                addon.getChunkGenerator(Environment.NORMAL).getSize(), addon.templateGson(), addon::log)
                        .buildAsync(file, workshop.getSeed(), addon.getPlugin(), progress);
    }

    /**
     * Works out how far a box can grow in the copied land. Completes on the main thread.
     */
    private CompletableFuture<String> check(String name, Map<Environment, File> files) {
        CompletableFuture<String> result = new CompletableFuture<>();
        Template normal;
        Template nether;
        AreaCheck check;
        try {
            normal = addon.readTemplate(files.get(Environment.NORMAL));
            nether = files.containsKey(Environment.NETHER) ? addon.readTemplate(files.get(Environment.NETHER)) : null;
            Area existing = addon.getAreas().getArea(name);
            // A new area has no structures yet, and starts with the default area's blueprint. An
            // area that is being replaced keeps its own.
            check = new AreaCheck(addon, normal, nether, existing == null ? null : existing.structures(),
                    existing == null ? addon.getAreas().getDefaultArea() : existing);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
        Bukkit.getScheduler().runTaskAsynchronously(addon.getPlugin(), () -> {
            String summary;
            try {
                summary = check.run().summary();
            } catch (RuntimeException e) {
                summary = null;
                addon.logError("Could not check the land: " + e);
            }
            close(normal);
            close(nether);
            String found = summary;
            Bukkit.getScheduler().runTask(addon.getPlugin(), () -> result.complete(found == null ? "?" : found));
        });
        return result;
    }

    private void close(@Nullable Template template) {
        if (template != null) {
            try {
                template.close();
            } catch (IOException e) {
                addon.logError("Could not close a template: " + e.getMessage());
            }
        }
    }

    /**
     * Makes the area from land that has been prepared. Players can pick it as soon as this
     * returns. The workshop ends and its worlds become the area's seed worlds.
     * @param prepared the land
     * @return the area
     * @throws IOException if the area could not be made. Nothing players can see has changed.
     */
    public Area finish(Prepared prepared) throws IOException {
        if (manager.get().orElse(null) != prepared.workshop() || manager.isBusy()) {
            throw new IOException("The workshop has changed since the land was copied");
        }
        Workshop workshop = prepared.workshop();
        String name = prepared.name();
        Area old = addon.getAreas().getArea(name);
        if (old != null && countBoxes(old) > 0) {
            throw new IOException("Area " + name + " has boxes in it");
        }
        int[] centre = prepared.centre().xz();
        int[] netherCentre = prepared.netherCentre().xz();
        if (!prepared.centre().equals(Centre.of(workshop.getCentre(Environment.NORMAL)))
                || !prepared.netherCentre().equals(Centre.of(workshop.getCentre(Environment.NETHER)))) {
            throw new IOException("The middle of the box has moved since the land was copied");
        }
        File folder = addon.getDataFolder();
        String structures = old == null ? "structures-" + name + ".yml" : old.structures();
        File structuresFile = new File(folder, structures);
        if (!structuresFile.exists()) {
            Files.writeString(structuresFile.toPath(), "# This file is written by the /boxadmin place command\n");
        }
        // The settings are written first, so that a restart finds the land that matches them
        File areasFile = new File(folder, "areas.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(areasFile);
        config.set(AREAS + name + ".seed", workshop.getSeed());
        config.set(AREAS + name + ".centre", centre[0] + "," + centre[1]);
        config.set(AREAS + name + ".nether-centre", netherCentre[0] + "," + netherCentre[1]);
        config.set(AREAS + name + ".biomes", workshop.getBiomes());
        config.set(AREAS + name + ".structures", structures);
        config.save(areasFile);

        if (old != null) {
            addon.clearLand(old);
        }
        Area area = addon.getAreas().put(name, workshop.getSeed(), workshop.getBiomes(), structures, centre,
                netherCentre);
        Files.createDirectories(addon.getAreaFolder(area).toPath());
        for (Map.Entry<Environment, File> en : prepared.files().entrySet()) {
            File file = addon.getTemplateFile(area, en.getKey());
            Files.move(en.getValue().toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            addon.setLand(area, en.getKey(), file);
        }
        // The folder the land was copied into is empty now
        Files.deleteIfExists(new File(folder, "areas" + File.separator + STAGING).toPath());
        addon.registerSeedGenerators(area);
        AdminAreasCommand.createBundle(addon, name);
        if (old != null) {
            restoreRow(area);
        }
        manager.keepAs(area);
        return area;
    }

    /**
     * Land of the old kind that has already been generated in the game world is put right.
     * Nobody has a box in the area, so this is only where admins have been and around spawn.
     */
    private void restoreRow(Area area) {
        World overWorld = addon.getOverWorld();
        if (overWorld == null || addon.getBoxCleaner() == null) {
            return;
        }
        int pitch = addon.getChunkGenerator(Environment.NORMAL).getSize() * 2 * 16;
        for (int column = -CLEAN_COLUMNS; column <= CLEAN_COLUMNS; column++) {
            addon.getBoxCleaner().clean(new Location(overWorld, (double) column * pitch, 0, (double) area.row() * pitch));
        }
    }
}
