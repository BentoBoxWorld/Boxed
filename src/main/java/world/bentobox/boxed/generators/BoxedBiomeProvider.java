package world.bentobox.boxed.generators;

import java.util.Arrays;
import java.util.List;

import org.bukkit.ChunkSnapshot;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.boxed.Boxed;

/**
 * @author tastybento
 *
 */
public class BoxedBiomeProvider extends BiomeProvider {

    private final Boxed addon;

    public BoxedBiomeProvider(Boxed boxed) {
        addon = boxed;
    }

    @Override
    public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
        if (!worldInfo.getName().equals(addon.getSettings().getWorldName())) {
            BentoBox.getInstance().logDebug("Wrong world biome ask.");
            return Biome.OLD_GROWTH_SPRUCE_TAIGA;
        }
        int chunkX = (int)((double)x/16);
        int chunkZ = (int)((double)z/16);
        ChunkSnapshot c = addon.getChunkGenerator().getChunk(chunkX, chunkZ);
        if (c == null) {
            //BentoBox.getInstance().logDebug("No chunk snapshot for " + (int)((double)x/16) + "," + (int)((double)z/16));
            return Biome.OLD_GROWTH_SPRUCE_TAIGA;
        }
        int xx = Math.floorMod(x, 16);
        int zz = Math.floorMod(z, 16);
        int yy = Math.max(Math.min(y * 4, worldInfo.getMaxHeight()), worldInfo.getMinHeight()); // To handle bug in Spigot
        Biome b = c.getBiome(xx, yy, zz);
        if (b != Biome.CUSTOM) {
            /*
            if (chunkX == 0 && chunkZ == 0) {
                BentoBox.getInstance().logDebug("Chunk thinks its coords are x=" + c.getX() + " z=" + c.getZ());
                BentoBox.getInstance().logDebug("World min = " + worldInfo.getMinHeight() + " world max = " + worldInfo.getMaxHeight());
                BentoBox.getInstance().logDebug("Biome found and coord = " + b + " " + x + "," + y + "," + z);
                BentoBox.getInstance().logDebug("Chunk = " + chunkX + "," + chunkZ);
                BentoBox.getInstance().logDebug("Pos in chunk " + xx + "," + yy + "," + zz);
            }
             */
            return b;
        } else {
            return Biome.OLD_GROWTH_BIRCH_FOREST;
        }
    }

    @Override
    public List<Biome> getBiomes(WorldInfo worldInfo) {
        // Return all of them for now!
        return Arrays.stream(Biome.values()).filter(b -> !b.equals(Biome.CUSTOM)).toList();
        //return overWorld.getBiomeProvider().getBiomes(overWorld);
    }

}
