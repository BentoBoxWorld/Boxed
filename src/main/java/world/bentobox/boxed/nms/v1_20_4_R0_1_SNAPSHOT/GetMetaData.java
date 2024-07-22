package world.bentobox.boxed.nms.v1_20_4_R0_1_SNAPSHOT;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_20_R3.CraftWorld;

import net.minecraft.core.BlockPosition;
import net.minecraft.world.level.block.entity.TileEntity;
import world.bentobox.boxed.nms.AbstractMetaData;

public class GetMetaData extends AbstractMetaData {

    @Override
    public String nmsData(Block block) {
        Location w = block.getLocation();
        CraftWorld cw = (CraftWorld) w.getWorld(); // CraftWorld is NMS one
        TileEntity te = cw.getHandle().c_(new BlockPosition(w.getBlockX(), w.getBlockY(), w.getBlockZ()));

        return getData(te, "j", "c");
    }
}