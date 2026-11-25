package world.bentobox.boxed.reach;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;

import world.bentobox.boxed.generators.chunks.AbstractBoxedChunkGenerator.EntityData;
import world.bentobox.boxed.generators.template.Template;
import world.bentobox.boxed.generators.template.TemplateChunk;

/**
 * What the land of an area holds, and how big a box has to be to hold it. A box covers
 * the whole height of the world, so only the horizontal distance from the centre matters.
 * <p>
 * Everything is recorded under a kind and a lower-case name, for example {@code block} and
 * {@code iron_ore}. The kinds are in {@link Kind}.
 *
 * @author tastybento
 */
public class LandSurvey {

    /**
     * The kinds of thing a survey records. The nether has its own kinds because a resource
     * in the nether can only be used once the player can get there.
     */
    public enum Kind {
        BLOCK, BIOME, ENTITY, STRUCTURE, NETHER_BLOCK, NETHER_BIOME, NETHER_ENTITY, NETHER_STRUCTURE;

        /**
         * @return how the kind is written in the requirements file, e.g. nether-block
         */
        public String label() {
            return name().toLowerCase(Locale.ENGLISH).replace('_', '-');
        }

        /**
         * @return true if this kind is in the nether
         */
        public boolean isNether() {
            return name().startsWith("NETHER");
        }
    }

    private final int radius;
    private final Map<Kind, Map<String, int[]>> counts = new EnumMap<>(Kind.class);

    /**
     * @param radius the largest box size, which is the distance from the centre of the land to its edge
     */
    public LandSurvey(int radius) {
        this.radius = radius;
    }

    /**
     * @return the largest box size
     */
    public int getRadius() {
        return radius;
    }

    /**
     * The box size needed to take in a block.
     * @param x block x relative to the centre of the box
     * @param z block z relative to the centre of the box
     * @return the smallest box size that includes the block
     */
    public static int sizeFor(int x, int z) {
        // A box of size s covers -s to s - 1 on both axes
        return Math.max(x >= 0 ? x + 1 : -x, z >= 0 ? z + 1 : -z);
    }

    /**
     * Records one or more of something.
     * @param kind kind of thing
     * @param name its name
     * @param size the box size needed to take it in. Anything beyond the land is ignored.
     * @param amount how many
     */
    public void add(Kind kind, String name, int size, int amount) {
        if (size <= radius) {
            counter(kind, name)[Math.max(size, 1)] += amount;
        }
    }

    private int[] counter(Kind kind, String name) {
        return counts.computeIfAbsent(kind, k -> new HashMap<>()).computeIfAbsent(name.toLowerCase(Locale.ENGLISH),
                k -> new int[radius + 1]);
    }

    /**
     * Counts how many things of a kind are in a box.
     * @param kind kind of thing
     * @param names names separated by |, each of which may use * as a wildcard
     * @param size box size
     * @return how many there are in a box of that size
     */
    public long count(Kind kind, String names, int size) {
        Pattern pattern = pattern(names);
        long total = 0;
        for (Map.Entry<String, int[]> en : counts.getOrDefault(kind, Map.of()).entrySet()) {
            if (pattern.matcher(en.getKey()).matches()) {
                for (int i = 1; i <= Math.min(size, radius); i++) {
                    total += en.getValue()[i];
                }
            }
        }
        return total;
    }

    /**
     * Finds the box size at which there are enough things of a kind.
     * @param kind kind of thing
     * @param names names separated by |, each of which may use * as a wildcard
     * @param amount how many are needed
     * @return the smallest box size that holds that many, or -1 if the land never does
     */
    public int sizeNeeded(Kind kind, String names, int amount) {
        Pattern pattern = pattern(names);
        long[] bySize = new long[radius + 1];
        counts.getOrDefault(kind, Map.of()).forEach((name, c) -> {
            if (pattern.matcher(name).matches()) {
                for (int i = 1; i <= radius; i++) {
                    bySize[i] += c[i];
                }
            }
        });
        long total = 0;
        for (int i = 1; i <= radius; i++) {
            total += bySize[i];
            if (total >= amount) {
                return i;
            }
        }
        return -1;
    }

    /**
     * @param kind kind of thing
     * @return the names recorded for the kind and the smallest box size that holds one
     */
    public Map<String, Integer> firstSizes(Kind kind) {
        Map<String, Integer> result = new HashMap<>();
        counts.getOrDefault(kind, Map.of()).forEach((name, c) -> {
            for (int i = 1; i <= radius; i++) {
                if (c[i] > 0) {
                    result.put(name, i);
                    break;
                }
            }
        });
        return result;
    }

    static Pattern pattern(String names) {
        StringBuilder sb = new StringBuilder();
        for (String name : names.toLowerCase(Locale.ENGLISH).split("\\|")) {
            if (!sb.isEmpty()) {
                sb.append('|');
            }
            sb.append(Pattern.quote(name.trim()).replace("*", "\\E.*\\Q"));
        }
        return Pattern.compile(sb.toString());
    }

    /**
     * Records the blocks, biomes and mobs of a template.
     * @param template the land of an area in one dimension
     * @param nether true if the template is the nether
     */
    public void addTemplate(Template template, boolean nether) {
        // Block data and biome objects are shared within a template, so they can be looked up by identity
        Map<BlockData, int[]> blockCounters = new IdentityHashMap<>();
        Map<Biome, int[]> biomeCounters = new IdentityHashMap<>();
        int chunks = template.getSize();
        for (int chunkX = -chunks; chunkX < chunks; chunkX++) {
            for (int chunkZ = -chunks; chunkZ < chunks; chunkZ++) {
                TemplateChunk chunk = template.getChunk(chunkX, chunkZ);
                if (chunk != null) {
                    addBlocks(chunk, chunkX << 4, chunkZ << 4, nether, blockCounters);
                    addBiomes(template, chunkX, chunkZ, nether, biomeCounters);
                    addEntities(chunk, chunkX << 4, chunkZ << 4, nether);
                }
            }
        }
    }

    private void addBlocks(TemplateChunk chunk, int baseX, int baseZ, boolean nether, Map<BlockData, int[]> counters) {
        Kind kind = nether ? Kind.NETHER_BLOCK : Kind.BLOCK;
        chunk.forEachBlock((x, y, z, data) -> {
            int size = sizeFor(baseX + x, baseZ + z);
            if (size <= radius) {
                counters.computeIfAbsent(data, d -> counter(kind, d.getMaterial().getKey().getKey()))[size]++;
            }
        });
    }

    /**
     * Biomes are stored every four blocks. The one at sea level stands for the column, and a
     * few higher and lower pick up the cave and mountain biomes.
     */
    private void addBiomes(Template template, int chunkX, int chunkZ, boolean nether, Map<Biome, int[]> counters) {
        Kind kind = nether ? Kind.NETHER_BIOME : Kind.BIOME;
        for (int x = 0; x < 16; x += 4) {
            for (int z = 0; z < 16; z += 4) {
                int size = sizeFor((chunkX << 4) + x, (chunkZ << 4) + z);
                if (size <= radius) {
                    addBiomeColumn(template, chunkX, chunkZ, x, z, size, kind, counters);
                }
            }
        }
    }

    private void addBiomeColumn(Template template, int chunkX, int chunkZ, int x, int z, int size, Kind kind,
            Map<Biome, int[]> counters) {
        for (int y : BIOME_HEIGHTS) {
            Biome biome = template.getBiome(chunkX, chunkZ, x, y, z);
            if (biome != null) {
                counters.computeIfAbsent(biome, b -> counter(kind, b.getKey().getKey()))[size]++;
            }
        }
    }

    private void addEntities(TemplateChunk chunk, int baseX, int baseZ, boolean nether) {
        for (EntityData e : chunk.getEntities()) {
            add(nether ? Kind.NETHER_ENTITY : Kind.ENTITY, e.entity().getType().getKey().getKey(),
                    sizeFor(baseX + e.relativeLoc().getBlockX(), baseZ + e.relativeLoc().getBlockZ()), 1);
        }
    }

    private static final int[] BIOME_HEIGHTS = { 64, 32, 0, -32, -52, 100, 160 };

    /**
     * Records the blocks of the starter blueprint, which every new box is given.
     * @param blocks block data strings by position relative to the centre of the box
     */
    public void addBlueprint(Map<org.bukkit.util.Vector, String> blocks) {
        blocks.forEach((pos, data) -> {
            // "minecraft:oak_log[axis=y]" names the block oak_log
            String name = data.replaceFirst("^minecraft:", "").replaceFirst("\\[.*$", "");
            if (!name.equals("air")) {
                add(Kind.BLOCK, name, sizeFor(pos.getBlockX(), pos.getBlockZ()), 1);
            }
        });
    }

    /**
     * Records the structures that are placed in every new box of an area.
     * @param section a dimension's section of a structures file: "x,y,z" to "name,rotation,mirror"
     * @param nether true if the section is for the nether
     */
    public void addStructures(ConfigurationSection section, boolean nether) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String[] xyz = key.split(",");
            String value = section.getString(key, "");
            try {
                int size = sizeFor(Integer.parseInt(xyz[0].trim()), Integer.parseInt(xyz[2].trim()));
                add(nether ? Kind.NETHER_STRUCTURE : Kind.STRUCTURE, value.split(",")[0].trim(), size, 1);
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException _) {
                // Not a structure entry
            }
        }
    }
}
