package world.bentobox.boxed;


import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.addons.Pladdon;


public class BoxedPladdon extends Pladdon {

    private Boxed addon;

    @Override
    public Addon getAddon() {
        if (addon == null) {
            addon = new Boxed();
        }
        return addon;
    }

}
