package world.bentobox.boxed.objects;

import org.bukkit.block.data.type.StructureBlock.Mode;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;

import com.google.gson.annotations.Expose;

/**
 * @author tastybento
 *
 */
public class BoxedStructureBlock {
//{author:"LadyAgnes",ignoreEntities:1b,integrity:1.0f,metadata:"drowned",mirror:"NONE",mode:"DATA",name:"",posX:0,posY:1,posZ:0,powered:0b,rotation:"NONE",seed:0L,showair:0b
    //,showboundingbox:1b,sizeX:0,sizeY:0,sizeZ:0}
    @Expose
    private String author;
    @Expose
    private boolean ignoreEntities;
    @Expose
    private float integrity;
    @Expose
    private String metadata;
    @Expose
    private Mirror mirror;
    @Expose
    private Mode mode;
    @Expose
    private String name;
    @Expose
    private int posX;
    @Expose
    private int posY;
    @Expose
    private int posZ;
    @Expose
    private boolean powered;
    @Expose
    private StructureRotation rotation;
    @Expose
    private String seed;
    @Expose
    private boolean showair;
    @Expose
    private boolean showboundingbox;
    @Expose
    private int sizeX;
    @Expose
    private int sizeY;
    @Expose
    private int sizeZ;
    /**
     * @return the author
     */
    public String getAuthor() {
        return author;
    }
    /**
     * @return the ignoreEntities
     */
    public boolean isIgnoreEntities() {
        return ignoreEntities;
    }
    /**
     * @return the integrity
     */
    public float getIntegrity() {
        return integrity;
    }
    /**
     * @return the metadata
     */
    public String getMetadata() {
        return metadata;
    }
    /**
     * @return the mirror
     */
    public Mirror getMirror() {
        return mirror;
    }
    /**
     * @return the mode
     */
    public Mode getMode() {
        return mode;
    }
    /**
     * @return the name
     */
    public String getName() {
        return name;
    }
    /**
     * @return the posX
     */
    public int getPosX() {
        return posX;
    }
    /**
     * @return the posY
     */
    public int getPosY() {
        return posY;
    }
    /**
     * @return the posZ
     */
    public int getPosZ() {
        return posZ;
    }
    /**
     * @return the powered
     */
    public boolean isPowered() {
        return powered;
    }
    /**
     * @return the rotation
     */
    public StructureRotation getRotation() {
        return rotation;
    }
    /**
     * @return the seed
     */
    public String getSeed() {
        return seed;
    }
    /**
     * @return the showair
     */
    public boolean isShowair() {
        return showair;
    }
    /**
     * @return the showboundingbox
     */
    public boolean isShowboundingbox() {
        return showboundingbox;
    }
    /**
     * @return the sizeX
     */
    public int getSizeX() {
        return sizeX;
    }
    /**
     * @return the sizeY
     */
    public int getSizeY() {
        return sizeY;
    }
    /**
     * @return the sizeZ
     */
    public int getSizeZ() {
        return sizeZ;
    }
    @Override
    public String toString() {
        return "BoxedStructureBlock [" + (author != null ? "author=" + author + ", " : "") + "ignoreEntities="
                + ignoreEntities + ", integrity=" + integrity + ", "
                + (metadata != null ? "metadata=" + metadata + ", " : "")
                + (mirror != null ? "mirror=" + mirror + ", " : "") + (mode != null ? "mode=" + mode + ", " : "")
                + (name != null ? "name=" + name + ", " : "") + "posX=" + posX + ", posY=" + posY + ", posZ=" + posZ
                + ", powered=" + powered + ", " + (rotation != null ? "rotation=" + rotation + ", " : "") + "seed="
                + seed + ", showair=" + showair + ", showboundingbox=" + showboundingbox + ", sizeX=" + sizeX
                + ", sizeY=" + sizeY + ", sizeZ=" + sizeZ + "]";
    }
    
    
    
}
