package world.bentobox.boxed.objects;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.util.BoundingBox;

import com.google.gson.annotations.Expose;

import world.bentobox.bentobox.database.objects.DataObject;
import world.bentobox.bentobox.database.objects.Table;

/**
 * Stores all the structures placed in the box when it is made.
 * These are used later to identify when a player enters such a structure and
 * trigger an Advancement
 * @author tastybento
 *
 */
@Table(name = "IslandStructures")
public class IslandStructures implements DataObject {

    @Expose
    String uniqueId;
    @Expose
    Map<BoundingBox, String> structureBoundingBoxMap = new HashMap<>();
    @Expose
    Map<BoundingBox, String> netherStructureBoundingBoxMap = new HashMap<>();

    public IslandStructures(String islandId) {
        this.uniqueId = islandId;
    }

    @Override
    public String getUniqueId() {
        return uniqueId;
    }

    @Override
    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    /**
     * Add a structure for this island
     * @param bb - bounding box of the structure
     * @param key - structure namespace key
     */
    public void addStructure(BoundingBox bb, String key) {
        getStructureBoundingBoxMap().put(bb, key);
    }

    public Map<BoundingBox, String> getStructureBoundingBoxMap() {
        if (structureBoundingBoxMap == null) {
            structureBoundingBoxMap = new HashMap<>();
        }
        return structureBoundingBoxMap;
    }

    public void setStructureBoundingBoxMap(Map<BoundingBox, String> structureBoundingBoxMap) {
        this.structureBoundingBoxMap = structureBoundingBoxMap;
    }

    /**
     * Add a structure for this island
     * @param bb - bounding box of the structure
     * @param key - structure namespace key
     */
    public void addNetherStructure(BoundingBox bb, String key) {
        getNetherStructureBoundingBoxMap().put(bb, key);
    }

    public Map<BoundingBox, String> getNetherStructureBoundingBoxMap() {
        if (netherStructureBoundingBoxMap == null) {
            netherStructureBoundingBoxMap = new HashMap<>();
        }
        return netherStructureBoundingBoxMap;
    }

    public void setNetherStructureBoundingBoxMap(Map<BoundingBox, String> netherStructureBoundingBoxMap) {
        this.netherStructureBoundingBoxMap = netherStructureBoundingBoxMap;
    }
}
