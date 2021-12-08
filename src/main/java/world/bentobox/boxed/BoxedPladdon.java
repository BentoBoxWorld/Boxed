package world.bentobox.boxed;

import org.bukkit.generator.BiomeProvider;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.addons.Pladdon;
import world.bentobox.boxed.generators.SimpleBiomeProvider;

public class BoxedPladdon extends Pladdon {

    @Override
    public Addon getAddon() {
        return new Boxed();
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(@NonNull String worldName, @Nullable String id) {
        return new SimpleBiomeProvider();
    }
}
