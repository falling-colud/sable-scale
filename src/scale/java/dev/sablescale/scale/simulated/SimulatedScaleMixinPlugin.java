package dev.sablescale.scale.simulated;

import dev.sablescale.core.ModIds;
import dev.sablescale.core.RequiredModsMixinPlugin;

/** Gates the Create Simulated scale patch on both {@code sable} and {@code simulated} being present. */
public final class SimulatedScaleMixinPlugin extends RequiredModsMixinPlugin {

    public SimulatedScaleMixinPlugin() {
        super("Create Simulated sub-level scaling", ModIds.SABLE, ModIds.SIMULATED);
    }
}
