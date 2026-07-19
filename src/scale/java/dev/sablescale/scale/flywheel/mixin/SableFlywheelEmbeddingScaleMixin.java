package dev.sablescale.scale.flywheel.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import org.joml.Matrix4f;
import org.joml.Vector3dc;

import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import dev.ryanhcode.sable.companion.math.Pose3dc;

/**
 * Makes Sable's <b>Flywheel</b> (instanced) block-entity rendering honour the pose <b>scale</b> &mdash; the fix for
 * "Create shafts don't scale". Sable's vanilla-BER path already scales (its {@code SubLevelRenderData.getTransformation}
 * builds {@code T(pos−cam)·R·S·T(blockPos−rotationPoint)}), but Create 6 renders kinetic blocks (shafts, cogs,
 * belts, Offroad wheels...) through Flywheel by default, and Sable routes those through a per-sub-level
 * {@code VisualEmbedding} whose transform is built in
 * {@code dev.ryanhcode.sable.neoforge.mixin.compatibility.flywheel.BlockEntityStorageMixin#sable$updateEmbeddingTransforms}
 * as {@code T(position−parentOrigin) · R · T(−(rotationPoint−localOrigin))} &mdash; with <b>no S</b>. So instanced
 * kinetic geometry stayed at scale 1 while the hull around it grew/shrank.
 *
 * <p>That transform is assembled entirely inside Sable's own mixin method, so the scale can only be inserted there,
 * via MixinSquared's {@code @TargetHandler}. We inject right after the method's single {@code Matrix4f.rotate(...)}
 * and before its {@code translate(−localOffset)}, appending {@code ·S} exactly where the BER path has it:
 * the embedding matrix becomes {@code T(position−parentOrigin) · R · S · T(−(rotationPoint−localOrigin))}, i.e.
 * {@code world = position + R·S·(worldBlockPos − rotationPoint)}, matching the hull. The embedding's normal matrix
 * is recomputed downstream from the patched matrix, so lighting stays correct (uniform scale keeps it a scalar
 * multiple of the rotation, which Flywheel's shaders renormalize).</p>
 *
 * <p>Uniform scale only (what {@code /sablescale} sets). At scale (1,1,1) this is a no-op.</p>
 */
@Mixin(targets = "dev.engine_room.flywheel.impl.visualization.storage.BlockEntityStorage", remap = false, priority = 1500)
public abstract class SableFlywheelEmbeddingScaleMixin {

    @TargetHandler(
        mixin = "dev.ryanhcode.sable.neoforge.mixin.compatibility.flywheel.BlockEntityStorageMixin",
        name = "sable$updateEmbeddingTransforms")
    @ModifyExpressionValue(
        method = "@MixinSquared:Handler",
        at = @At(
            value = "INVOKE",
            target = "Lorg/joml/Matrix4f;rotate(Lorg/joml/Quaternionfc;)Lorg/joml/Matrix4f;"))
    private Matrix4f sablescale$scaleEmbedding(final Matrix4f transformation, @Local(ordinal = 0) final Pose3dc renderPose) {
        // rotate() returns the transformation matrix itself; append the pose scale in place so the embedding matrix
        // becomes T(pos−parentOrigin)·R·S·T(−localOffset), then the method's own translate(−localOffset) follows.
        final Vector3dc scale = renderPose.scale();
        if (scale.x() != 1.0 || scale.y() != 1.0 || scale.z() != 1.0)
            transformation.scale((float) scale.x(), (float) scale.y(), (float) scale.z());
        return transformation;
    }
}
