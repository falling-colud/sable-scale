package dev.sablescale.core;

/** Mod ids of the mods this mod bridges. */
public final class ModIds {

    public static final String SABLE = "sable";
    /** Flywheel (engine_room) - Create's instanced-rendering backend; its embedding transform is what drops scale. */
    public static final String FLYWHEEL = "flywheel";
    /** Create Simulated - ships the swivel bearing, which allocates its own sub-level outside Sable's helper. */
    public static final String SIMULATED = "simulated";

    private ModIds() {}
}
