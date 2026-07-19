package dev.sablescale.scale.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import org.joml.Vector3d;
import org.joml.Vector3dc;

import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import dev.ryanhcode.sable.physics.impl.rapier.constraint.generic.RapierGenericConstraintHandle;
import dev.sablescale.scale.ScaledConstraint;
import dev.sablescale.scale.ScaledConstraints;

/**
 * Keeps a generic joint's anchors on the resampled geometry when they are <b>re-aimed after creation</b>.
 *
 * <p>{@code setFrame1}/{@code setFrame2} move a live joint's anchor (the physics staff drags its grab point this
 * way, and the docking connector smooths its pull toward one). They take the new anchor in the same unscaled
 * plot-local space {@code addConstraint} does, so they need the same mapping
 * {@link RapierConstraintScaleMixin} applies at creation - otherwise re-aiming a joint on a scaled vehicle would
 * silently undo the corrected anchor and snap it back to the phantom point. Frame 1 belongs to {@code bodyA},
 * frame 2 to {@code bodyB} (both stamped on the handle by {@link RapierConstraintScaleMixin}); the orientation
 * arguments are untouched, since a uniform scale preserves directions.</p>
 */
@Mixin(value = RapierGenericConstraintHandle.class, remap = false)
public abstract class RapierGenericConstraintFrameScaleMixin implements ScaledConstraint {

    @Redirect(
        method = "setFrame1",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;setConstraintFrame(JJIDDDDDDD)V"))
    private void sablescale$scaleFrame1(final long sceneHandle, final long handle, final int frame,
                                        final double px, final double py, final double pz,
                                        final double qx, final double qy, final double qz, final double qw) {
        final Vector3dc anchor = ScaledConstraints.anchor(new Vector3d(px, py, pz), this.sablescale$bodyA());
        Rapier3D.setConstraintFrame(sceneHandle, handle, frame, anchor.x(), anchor.y(), anchor.z(), qx, qy, qz, qw);
    }

    @Redirect(
        method = "setFrame2",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;setConstraintFrame(JJIDDDDDDD)V"))
    private void sablescale$scaleFrame2(final long sceneHandle, final long handle, final int frame,
                                        final double px, final double py, final double pz,
                                        final double qx, final double qy, final double qz, final double qw) {
        final Vector3dc anchor = ScaledConstraints.anchor(new Vector3d(px, py, pz), this.sablescale$bodyB());
        Rapier3D.setConstraintFrame(sceneHandle, handle, frame, anchor.x(), anchor.y(), anchor.z(), qx, qy, qz, qw);
    }
}
