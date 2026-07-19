package dev.sablescale;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

import dev.sablescale.core.ModIds;

/**
 * <b>Sable Scale</b> &mdash; scale <a href="https://modrinth.com/mod/sable">Sable</a> sub-levels (vehicles /
 * physics objects) up and down. Companion to the "Make it Compatible" family: same repo, same conventions, safe to
 * install with any subset of the bridged mods.
 *
 * <p>Sable's {@code Pose3d} already carries a per-axis scale that its whole transform stack honours &mdash; block
 * rendering ({@code SublevelRenderOffsetHelper}), client interpolation ({@code Pose3dc.lerp}), world&harr;plot
 * coordinate mapping ({@code transformPosition[Inverse]}) and entity collision all apply it. What Sable does
 * <em>not</em> do is transport that scale: the pose network format ({@code SableBufferUtils}) and the pose save
 * format ({@code SableNBTUtils}) both drop it, and nothing exposes it in-game. This mod fills exactly those gaps:</p>
 *
 * <ul>
 *   <li>two mixins append the scale to Sable's pose network + NBT formats (see {@code dev.sablescale.scale.mixin});</li>
 *   <li>a {@code /sablescale} command sets/queries the scale of sub-levels, including the one you are looking at.</li>
 * </ul>
 *
 * <p><b>Install on both sides.</b> The network mixin extends Sable's pose wire format, so server and client must
 * agree (a mismatch desyncs Sable's pose stream). Like Sable itself, this mod belongs on server <em>and</em> client.</p>
 *
 * <p>Physics follows the scale too: the Rapier terrain collider is resampled onto the scaled lattice
 * ({@code dev.sablescale.scale.ScaledColliders}) and the rigid body gets scale-corrected mass and inertia
 * ({@code dev.sablescale.scale.ScaledMass}: mass &times;S&sup3;, inertia &times;S&#8309; for uniform scale), so a
 * scaled vehicle collides, weighs and floats like the small/large thing it looks like. Water drag is corrected to
 * match ({@code dev.sablescale.scale.ScaledDrag}) &mdash; Sable's drag counts hull <em>surface cells</em>, so left
 * alone it outruns the &times;S&sup3; mass and eventually launches a shrunken vehicle out of the world.
 * Entity-vs-sub-level
 * collision is scale-aware out of the box (it goes through the pose transforms). Resizing keeps the vehicle
 * seated instead of burying it: the hull's lowest point is anchored across the change and the hull is lifted
 * clear of any terrain it would grow into ({@code dev.sablescale.scale.TerrainClearance}).</p>
 */
@Mod(SableScale.MOD_ID)
public final class SableScale {

    public static final String MOD_ID = "sablescale";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SableScale(final IEventBus modBus, final ModContainer container) {
        final boolean sablePresent = ModList.get() != null && ModList.get().isLoaded(ModIds.SABLE);
        LOGGER.info("[Sable Scale] loaded - sub-level scaling {}.",
            sablePresent ? "ACTIVE" : "dormant (Sable is not installed)");

        // Everything this mod does touches Sable classes, so only hook up the runtime (commands + the
        // scaled-collider flush) when Sable is present; the indirection through ScaleRuntime keeps Sable types out
        // of this class so it verifies cleanly without Sable. (The mixins gate themselves via ScaleMixinPlugin.)
        if (sablePresent)
            dev.sablescale.scale.ScaleRuntime.register();
    }
}
