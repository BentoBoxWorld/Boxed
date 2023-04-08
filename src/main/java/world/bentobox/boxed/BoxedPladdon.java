package world.bentobox.boxed;


import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.addons.Pladdon;


public class BoxedPladdon extends Pladdon {

    @Override
    public Addon getAddon() {
        return new Boxed();
    }

}
