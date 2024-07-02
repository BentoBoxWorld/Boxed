package world.bentobox.boxed.nms;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.bukkit.block.Block;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.protocol.game.PacketPlayOutTileEntityData;
import net.minecraft.world.level.block.entity.TileEntity;

/**
 * 
 */
public abstract class AbstractMetaData {

    public abstract String nmsData(Block block);

    protected String getData(TileEntity te, String method, String field) {
        try {
            // Check if the method 'j' exists
            Method updatePacketMethod = te.getClass().getDeclaredMethod(method);
            if (updatePacketMethod != null) {
                // Invoke the method to get the PacketPlayOutTileEntityData object
                updatePacketMethod.setAccessible(true);
                PacketPlayOutTileEntityData packet = (PacketPlayOutTileEntityData) updatePacketMethod.invoke(te);

                // Access the private field for the NBTTagCompound getter in PacketPlayOutTileEntityData
                Field fieldC = packet.getClass().getDeclaredField(field);
                fieldC.setAccessible(true);
                NBTTagCompound nbtTag = (NBTTagCompound) fieldC.get(packet);

                return nbtTag.toString(); // This will show what you want
            }
        } catch (NoSuchMethodException e) {
            System.out.println("The method '" + method + "' does not exist in the TileEntity class.");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";

    }
}
