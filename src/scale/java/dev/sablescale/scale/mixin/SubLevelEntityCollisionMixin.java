package dev.sablescale.scale.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import org.joml.Vector3d;

import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;

/**
 * Makes entity-vs-sub-level collision honour the pose <b>scale</b>.
 *
 * <p>Stock {@code SubLevelEntityCollision} builds one world-space OBB per collision box: the box <em>center</em>
 * goes through {@code subLevelPose.transformPosition} (scale-aware), but the box <em>dimensions</em> are copied
 * raw from the plot-space voxel shape ({@code box.size(cubeOBB.getDimensions())}) &mdash; so at &times;0.5 every
 * block still pushed entities as a full-size cube centred on the shrunken position, with neighbouring phantom
 * boxes overlapping ("entities are pushed weirdly"). Both SAT call sites (the main {@code collide} loop and the
 * step-up probe in {@code hasCollision}) get the cube dimensions multiplied by the scale before the test. The
 * dimensions vector is refilled from {@code box.size(...)} on every iteration, so mutating it here is safe.</p>
 *
 * <p>The current sub-level's scale is captured by wrapping the loop's single {@code subLevel.logicalPose()} call
 * and stashed in a thread-local &mdash; NOT via MixinExtras {@code @Local}, which cannot discriminate here (the
 * compiled {@code collide} carries two {@code SubLevel} locals at the SAT instruction; discrimination failed and
 * took the whole injector down, crashing the server). {@code hasCollision} only runs inside that loop (via
 * {@code tryStepUp}), so the captured scale is always the right one.</p>
 */
@Mixin(value = SubLevelEntityCollision.class, remap = false)
public abstract class SubLevelEntityCollisionMixin {

    @Unique
    private static final ThreadLocal<Vector3d> sablescale$currentScale = ThreadLocal.withInitial(() -> new Vector3d(1.0));

    @Redirect(
        method = "collide",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ryanhcode/sable/sublevel/SubLevel;logicalPose()Ldev/ryanhcode/sable/companion/math/Pose3d;"))
    private static Pose3d sablescale$captureScale(final SubLevel subLevel) {
        final Pose3d pose = subLevel.logicalPose();
        sablescale$currentScale.get().set(pose.scale());
        return pose;
    }

    @Redirect(
        method = {"collide", "hasCollision"},
        at = @At(
            value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/math/OrientedBoundingBox3d;sat(Ldev/ryanhcode/sable/api/math/OrientedBoundingBox3d;Ldev/ryanhcode/sable/api/math/OrientedBoundingBox3d;Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"))
    private static Vector3d sablescale$scaledSat(final OrientedBoundingBox3d entityOBB, final OrientedBoundingBox3d cubeOBB, final Vector3d mtv) {
        final Vector3d scale = sablescale$currentScale.get();
        if (scale.x != 1.0 || scale.y != 1.0 || scale.z != 1.0)
            cubeOBB.getDimensions().mul(scale.x, scale.y, scale.z);
        return OrientedBoundingBox3d.sat(entityOBB, cubeOBB, mtv);
    }
}
