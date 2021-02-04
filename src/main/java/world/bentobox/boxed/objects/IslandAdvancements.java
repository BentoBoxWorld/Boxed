package world.bentobox.boxed.objects;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.Expose;

import world.bentobox.bentobox.database.objects.DataObject;
import world.bentobox.bentobox.database.objects.Table;

/**
 * Stores the advancements for the island
 * @author tastybento
 *
 */
@Table(name = "IslandAdvancements")
public class IslandAdvancements implements DataObject {

    @Expose
    String uniqueId;
    @Expose
    List<String> advancements = new ArrayList<>();

    /**
     * @param uniqueId
     */
    public IslandAdvancements(String uniqueId) {
        this.uniqueId = uniqueId;
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
     * @return the advancements
     */
    public List<String> getAdvancements() {
        return advancements;
    }

    /**
     * @param advancements the advancements to set
     */
    public void setAdvancements(List<String> advancements) {
        this.advancements = advancements;
    }

}
