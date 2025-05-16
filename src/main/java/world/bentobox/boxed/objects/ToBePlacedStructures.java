package world.bentobox.boxed.objects;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.util.Vector;

import com.google.gson.annotations.Expose;

import world.bentobox.bentobox.database.objects.DataObject;
import world.bentobox.bentobox.database.objects.Table;
import world.bentobox.bentobox.util.Pair;

/**
 * Stores all the structures to be placed in the world. This is a queue that is done over
 * time to avoid lag and if the server is stopped then the pending list is saved here
 * @author tastybento
 *
 */
@Table(name = "ToBePlacedStructures")
public class ToBePlacedStructures implements DataObject {

    /**
     * Structure record contains the name of the structure, the structure itself,
     * where it was placed and enums for rotation, mirror, and a flag to paste mobs
     * or not.
     * 
     * @param name      - name of structure
     * @param structure - Structure namespaced key
     * @param location  - location where it has been placed
     * @param rot       - rotation
     * @param mirror    - mirror setting
     * @param noMobs    - if false, mobs not pasted
     */
    public record StructureRecord(@Expose String name, @Expose String structure, @Expose Location location,
            @Expose StructureRotation rot, @Expose Mirror mirror, @Expose Boolean noMobs,
            Map<Vector, BlockData> removedBlocks) {
    }

    @Expose
    String uniqueId = "ToDo";
    @Expose
    private Map<Pair<Integer, Integer>, List<StructureRecord>> readyToBuild = new HashMap<>();

    /**
     * @return the uniqueId
     */
    public String getUniqueId() {
        return uniqueId;
    }

    /**
     * @param uniqueId the uniqueId to set
     */
    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }
    
    /**
     * @return the readyToBuild
     */
    public Map<Pair<Integer, Integer>, List<StructureRecord>> getReadyToBuild() {
        if (readyToBuild == null) {
            readyToBuild = new HashMap<>();
        }
        return readyToBuild;
    }
    
    /**
     * @param readyToBuild the readyToBuild to set
     */
    public void setReadyToBuild(Map<Pair<Integer, Integer>, List<StructureRecord>> readyToBuild) {
        this.readyToBuild = readyToBuild;
    }

}