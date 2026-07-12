package dev.sablescale.scale.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaSingleSubLevelRenderData;

/**
 * Makes single-block sub-levels (lone floating blocks) honour the pose <b>scale</b>.
 *
 * <p>Stock {@code renderSingleBlock} builds {@code T(pos − R·q − cam) · R} with pivot
 * {@code q = rotationPoint − blockPos} and no scale. Appending the sandwich {@code ·T(q)·S·T(−q)} right after
 * the rotate turns it into {@code T(pos−cam)·R·S·T(−q)}, matching the logical transform. The subsequent
 * {@code transform.normal(...)} call computes the normal matrix from the patched transform, so shading stays
 * correct (JOML's {@code normal()} is the inverse-transpose).</p>
 */
@Mixin(value = VanillaSingleSubLevelRenderData.class, remap = false)
public abstract class VanillaSingleSubLevelRenderDataMixin {

    @Shadow
    @Final
    private ClientSubLevel subLevel;

    @Shadow
    private BlockPos singleBlockPos;

    @Redirect(
        method = "renderSingleBlock",
        at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;rotate(Lorg/joml/Quaternionfc;)Lorg/joml/Matrix4f;"))
    private Matrix4f sablescale$scaleSingleBlock(final Matrix4f transform, final Quaternionfc rotation) {
        transform.rotate(rotation);
        final Pose3dc renderPose = this.subLevel.renderPose();
        final Vector3dc scale = renderPose.scale();
        if (scale.x() == 1.0 && scale.y() == 1.0 && scale.z() == 1.0)
            return transform;

        final Vector3d pivot = new Vector3d(renderPose.rotationPoint())
            .sub(this.singleBlockPos.getX(), this.singleBlockPos.getY(), this.singleBlockPos.getZ());
        return transform
            .translate((float) pivot.x, (float) pivot.y, (float) pivot.z)
            .scale((float) scale.x(), (float) scale.y(), (float) scale.z())
            .translate((float) -pivot.x, (float) -pivot.y, (float) -pivot.z);
    }
}
