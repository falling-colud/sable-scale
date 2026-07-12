package dev.sablescale.scale.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import org.joml.Vector3d;

import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinhelpers.CanFallAtleastHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;

/**
 * Third un-scaled SAT site: {@code CanFallAtleastHelper.canFallAtleastWithSubLevels} backs the player's
 * <b>pose/headroom</b> checks (Sable's {@code player_standup} hook into {@code canPlayerFitWithinBlocksAndEntitiesWhen})
 * and the edge back-off. Like the entity-collision loop it transforms each block box's <em>center</em> through the
 * scale-aware pose but keeps the raw plot-space <em>dimensions</em> &mdash; on a &times;0.5 vehicle the ceiling
 * blocks reach a full block down as phantoms, the standing-pose test fails, and the player is forced into crouch
 * ("I go into crouch mode way too easily"). Same capture-then-scale treatment as
 * {@link SubLevelEntityCollisionMixin}; both wrapped calls verified unique in the shipped jar via javap.
 */
@Mixin(value = CanFallAtleastHelper.class, remap = false)
public abstract class CanFallAtleastHelperScaleMixin {

    @Unique
    private static final ThreadLocal<Vector3d> sablescale$currentScale = ThreadLocal.withInitial(() -> new Vector3d(1.0));

    @Redirect(
        method = "canFallAtleastWithSubLevels",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ryanhcode/sable/sublevel/SubLevel;lastPose()Ldev/ryanhcode/sable/companion/math/Pose3dc;"))
    private static Pose3dc sablescale$captureScale(final SubLevel subLevel) {
        final Pose3dc pose = subLevel.lastPose();
        sablescale$currentScale.get().set(pose.scale());
        return pose;
    }

    @Redirect(
        method = "canFallAtleastWithSubLevels",
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
