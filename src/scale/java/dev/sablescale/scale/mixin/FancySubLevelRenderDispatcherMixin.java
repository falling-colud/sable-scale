package dev.sablescale.scale.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.dispatcher.FancySubLevelRenderDispatcher;
import dev.ryanhcode.sable.sublevel.render.fancy.FancySubLevelRenderData;

/**
 * Makes Sable's "fancy" (Veil GPU-driven) block renderer honour the pose <b>scale</b>.
 *
 * <p>Stock {@code renderSectionLayer} feeds the {@code SableTransform} uniform
 * {@code T(pos − R·p − cam) · R} (with {@code p = rotationPoint − origin}), which equals
 * {@code T(pos − cam) · R · T(−p)} &mdash; no scale, so scaled sub-levels rendered at &times;1 while their
 * logical transforms (collision, picking) scaled. Since the whole transform lives in this one uniform, the fix
 * is a matrix sandwich about the plot-local pivot: {@code M' = M · T(p) · S · T(−p) = T(pos−cam) · R · S · T(−p)},
 * matching the logical {@code world = pos + R·S·(plotLocal − rotationPoint)}.</p>
 *
 * <p>The loop's sub-level is captured by wrapping its single {@code renderPose()} call (render thread only) rather
 * than MixinExtras {@code @Local}, whose by-type discrimination proved fragile against compiled locals.</p>
 */
@Mixin(value = FancySubLevelRenderDispatcher.class, remap = false)
public abstract class FancySubLevelRenderDispatcherMixin {

    @Unique
    private static ClientSubLevel sablescale$currentSubLevel;

    @Redirect(
        method = "renderSectionLayer",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ryanhcode/sable/sublevel/ClientSubLevel;renderPose()Ldev/ryanhcode/sable/companion/math/Pose3dc;"))
    private Pose3dc sablescale$captureSubLevel(final ClientSubLevel subLevel) {
        sablescale$currentSubLevel = subLevel;
        return subLevel.renderPose();
    }

    @ModifyArg(
        method = "renderSectionLayer",
        at = @At(
            value = "INVOKE",
            target = "Lfoundry/veil/api/client/render/shader/uniform/ShaderUniform;setMatrix(Lorg/joml/Matrix4fc;)V"),
        index = 0)
    private Matrix4fc sablescale$applyScale(final Matrix4fc transform) {
        final ClientSubLevel subLevel = sablescale$currentSubLevel;
        if (subLevel == null)
            return transform;

        final Pose3dc renderPose = subLevel.renderPose();
        final Vector3dc scale = renderPose.scale();
        if (scale.x() == 1.0 && scale.y() == 1.0 && scale.z() == 1.0)
            return transform;

        final FancySubLevelRenderData renderData = (FancySubLevelRenderData) subLevel.getRenderData();
        final Vector3d pivot = new Vector3d(renderPose.rotationPoint()).sub(renderData.getOrigin());
        return new Matrix4f(transform)
            .translate((float) pivot.x, (float) pivot.y, (float) pivot.z)
            .scale((float) scale.x(), (float) scale.y(), (float) scale.z())
            .translate((float) -pivot.x, (float) -pivot.y, (float) -pivot.z);
    }
}
