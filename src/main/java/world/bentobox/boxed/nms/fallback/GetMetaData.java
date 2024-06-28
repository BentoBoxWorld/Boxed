package world.bentobox.boxed.nms.fallback;

import org.bukkit.block.Block;

import world.bentobox.boxed.nms.AbstractMetaData;

/**
 * Fallback
 */
public class GetMetaData extends AbstractMetaData {

    @Override
    public String nmsData(Block block) {
        return "Nothing"; // We cannot read it if we have no NMS
    }

}
