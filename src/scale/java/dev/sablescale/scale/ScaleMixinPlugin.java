package dev.sablescale.scale;

import dev.sablescale.core.ModIds;
import dev.sablescale.core.RequiredModsMixinPlugin;

/** Applies the pose-format mixins only when Sable is installed. */
public final class ScaleMixinPlugin extends RequiredModsMixinPlugin {

    public ScaleMixinPlugin() {
        super("Sub-level scaling", ModIds.SABLE);
    }
}
