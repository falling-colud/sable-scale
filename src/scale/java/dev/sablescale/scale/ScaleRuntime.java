package dev.sablescale.scale;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Registers the runtime hooks that touch Sable classes. Loaded (and thus class-initialised) only when Sable is
 * present, keeping the main mod class verifiable without it.
 */
public final class ScaleRuntime {

    private ScaleRuntime() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> SableScaleCommands.register(event.getDispatcher()));
        // Scaled-collider rebuilds are batched per tick; flush after all levels ticked so the next physics step
        // sees consistent geometry.
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> ScaledColliders.flushAll());
    }
}
