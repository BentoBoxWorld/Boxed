package world.bentobox.boxed.objects;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * This replicates a jigsaw block.
 * @author tastybento
 *
 */
public class BoxedJigsawBlock {
    // final_state:"minecraft:polished_blackstone_bricks",joint:"aligned",name:"minecraft:empty",pool:"minecraft:bastion/bridge/legs",target:"minecraft:leg_connector"
    @Expose
    @SerializedName("final_state")
    private String finalState;
    @Expose
    private String joint;
    @Expose
    private String name;
    @Expose
    private String pool;
    @Expose
    private String target;
    /**
     * @return the finalState
     */
    public String getFinalState() {
        return finalState;
    }
    /**
     * @return the joint
     */
    public String getJoint() {
        return joint;
    }
    /**
     * @return the name
     */
    public String getName() {
        return name;
    }
    /**
     * @return the pool
     */
    public String getPool() {
        return pool;
    }
    /**
     * @return the target
     */
    public String getTarget() {
        return target;
    }
    @Override
    public String toString() {
        return "BoxedJigsawBlock [" + (finalState != null ? "finalState=" + finalState + ", " : "")
                + (joint != null ? "joint=" + joint + ", " : "") + (name != null ? "name=" + name + ", " : "")
                + (pool != null ? "pool=" + pool + ", " : "") + (target != null ? "target=" + target : "") + "]";
    }
}
