package dev.sablescale.scale.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;

/**
 * Keeps a scaled sub-level's <b>sky lighting</b> from blacking out.
 *
 * <p>Sable dims a whole sub-level's sky-light channel by a single per-sub-level factor,
 * {@code getLatestSkyLightScale() / 15} &mdash; it feeds the {@code SableSkyLightScale} shader uniform (terrain),
 * {@link ClientSubLevel#scaleLightColor(int)} (block entities) and the particle path alike. That factor comes from
 * {@link ClientSubLevel#computeSubLevelSkyLight(Pose3dc)}, which samples the <em>world's</em> sky brightness at a
 * few points taken from the sub-level's <b>global bounding box</b> and returns the max. The intent is "how much sky
 * reaches this vehicle" &mdash; a ship in the open stays bright, one in a cave goes dark.</p>
 *
 * <p>That bounding box is the pose-transformed plot, so it carries the pose <b>scale</b> (see
 * {@code SubLevel.updateBoundingBox} &rarr; {@code Pose3dc.bakeIntoMatrix}). Shrinking a vehicle therefore corrupts
 * the heuristic two ways, and neither happens at scale&nbsp;1:</p>
 * <ul>
 *   <li>once the shrunken box's {@code volume() < 9}, stock switches to a <em>single-column</em> sample at the pose
 *       position (&plusmn;1 block in Y). For a vehicle whose centre of mass sits under its own deck &mdash; or dips a
 *       block into water/terrain as it bobs &mdash; that column reads sky&nbsp;light&nbsp;0;</li>
 *   <li>even above the threshold, the four corner samples are pulled inward to the shrunken extent, so the "poke a
 *       corner out into open sky" robustness that keeps a full-size hull lit is lost.</li>
 * </ul>
 * <p>Either way a sample that <em>should</em> see sky returns 0, the factor collapses to 0, and every sky-lit face on
 * the sub-level renders fully black (block-light faces &mdash; torches &mdash; are unaffected, since only the sky
 * channel is scaled). It flickers as the vehicle moves: "sometimes things just turn fully dark".</p>
 *
 * <p><b>Fix:</b> a vehicle sits at the same world location and sees the same sky no matter how big it is drawn, so its
 * sky-light factor must be <b>scale-invariant</b>. For a scaled pose we recompute the factor exactly as stock would at
 * scale&nbsp;1 &mdash; rebuild the world footprint from the plot bounds under the pose with scale forced to
 * {@code (1,1,1)}, then run stock's own volume test and centre/corner sampling on it. A shrunk or grown vehicle is
 * then lit identically to an un-scaled copy of itself. At scale&nbsp;1 we return immediately and stock runs untouched.</p>
 */
@Mixin(value = ClientSubLevel.class, remap = false)
public abstract class ClientSubLevelSkyLightScaleMixin {

    @Inject(method = "computeSubLevelSkyLight", at = @At("HEAD"), cancellable = true)
    private void sablescale$scaleInvariantSkyLight(final Pose3dc pose, final CallbackInfoReturnable<Integer> cir) {
        final Vector3dc scale = pose.scale();
        if (scale.x() == 1.0 && scale.y() == 1.0 && scale.z() == 1.0)
            return; // un-scaled: stock heuristic is already correct, don't touch it

        final ClientSubLevel self = (ClientSubLevel) (Object) this;
        final ClientLevel level = self.getLevel();
        final BoundingBox3ic plotBounds = self.getPlot().getBoundingBox();

        // Empty plot (inverted min/max sentinel): nothing to sample a footprint from, fall back to the column at pos.
        if (plotBounds.maxX() < plotBounds.minX()) {
            cir.setReturnValue(sablescale$sampleColumn(level, pose.position()));
            return;
        }

        // The world AABB the vehicle would occupy at scale 1: plot bounds under the pose with scale neutralised.
        final Pose3d unscaledPose = new Pose3d(pose);
        unscaledPose.scale().set(1.0, 1.0, 1.0);
        final BoundingBox3d box = new BoundingBox3d(
            plotBounds.minX(), plotBounds.minY(), plotBounds.minZ(),
            plotBounds.maxX() + 1.0, plotBounds.maxY() + 1.0, plotBounds.maxZ() + 1.0);
        box.transform(unscaledPose);

        // -- from here, stock computeSubLevelSkyLight's own logic, but over the scale-1 box --
        final double volume = (box.maxX() - box.minX()) * (box.maxY() - box.minY()) * (box.maxZ() - box.minZ());
        if (volume < 9.0) {
            cir.setReturnValue(sablescale$sampleColumn(level, pose.position()));
            return;
        }

        final Vector3d center = box.center(new Vector3d());
        final double sampleY = center.y() + 0.1;
        final double xMin = box.minX(), xMax = box.maxX(), zMin = box.minZ(), zMax = box.maxZ();
        int maxLight = 0;
        maxLight = Math.max(maxLight, level.getBrightness(LightLayer.SKY, BlockPos.containing(center.x(), sampleY, center.z())));
        maxLight = Math.max(maxLight, level.getBrightness(LightLayer.SKY, BlockPos.containing(xMin, sampleY, zMin)));
        maxLight = Math.max(maxLight, level.getBrightness(LightLayer.SKY, BlockPos.containing(xMax, sampleY, zMin)));
        maxLight = Math.max(maxLight, level.getBrightness(LightLayer.SKY, BlockPos.containing(xMin, sampleY, zMax)));
        maxLight = Math.max(maxLight, level.getBrightness(LightLayer.SKY, BlockPos.containing(xMax, sampleY, zMax)));
        cir.setReturnValue(maxLight);
    }

    /** Stock's small-sub-level path: the sky-light column at {@code pos}, with a &plusmn;1-block Y fallback. */
    private static int sablescale$sampleColumn(final ClientLevel level, final Vector3dc pos) {
        int skyLight = level.getBrightness(LightLayer.SKY, BlockPos.containing(pos.x(), pos.y(), pos.z()));
        if (skyLight == 0)
            skyLight = level.getBrightness(LightLayer.SKY, BlockPos.containing(pos.x(), pos.y() + 1.0, pos.z()));
        if (skyLight == 0)
            skyLight = level.getBrightness(LightLayer.SKY, BlockPos.containing(pos.x(), pos.y() - 1.0, pos.z()));
        return skyLight;
    }
}
