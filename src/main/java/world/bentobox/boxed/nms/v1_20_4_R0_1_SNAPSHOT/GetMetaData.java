package world.bentobox.boxed.nms.v1_20_4_R0_1_SNAPSHOT;

import java.lang.reflect.Field;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_20_R3.CraftWorld;

import net.minecraft.core.BlockPosition;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.protocol.game.PacketPlayOutTileEntityData;
import net.minecraft.world.level.block.entity.TileEntity;
import world.bentobox.boxed.nms.AbstractMetaData;

public class GetMetaData extends AbstractMetaData {

    @Override
    public String nmsData(Block block) {
        Location w = block.getLocation();
        CraftWorld cw = (CraftWorld) w.getWorld(); // CraftWorld is NMS one
        // for 1.13+ (we have use WorldServer)
        TileEntity te = cw.getHandle().c_(new BlockPosition(w.getBlockX(), w.getBlockY(), w.getBlockZ()));
        try {
            PacketPlayOutTileEntityData packet = ((PacketPlayOutTileEntityData) te.j()); // get update packet from NMS
            // object
            // here we should use reflection because "c" field isn't accessible
            Field f = packet.getClass().getDeclaredField("c"); // get field
            f.setAccessible(true); // make it available
            NBTTagCompound nbtTag = (NBTTagCompound) f.get(packet);
            return nbtTag.toString(); // this will show what you want
        } catch (Exception exc) {
            exc.printStackTrace();
        }
        return "Nothing";
    }

}