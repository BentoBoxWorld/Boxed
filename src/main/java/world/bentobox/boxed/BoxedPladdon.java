package world.bentobox.boxed;

import org.bukkit.plugin.java.annotation.plugin.ApiVersion;
import org.bukkit.plugin.java.annotation.plugin.Plugin;

import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.addons.Pladdon;

@Plugin(name="Boxed", version="1.0")
@ApiVersion(ApiVersion.Target.v1_17)
public class BoxedPladdon extends Pladdon {

    @Override
    public Addon getAddon() {
        return new Boxed();
    }

}
