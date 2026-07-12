package dev.sablescale.scale.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import dev.ryanhcode.sable.api.physics.mass.MergedMassTracker;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

/**
 * Fixes the brief, fast sideways jump of a <b>scaled</b> sub-level when a block is broken or placed on it.
 *
 * <p>When the centre of mass moves by {@code Δ} (plot space), {@code MergedMassTracker.uploadData} re-anchors the
 * pose's rotation point to the new CoM and compensates by teleporting the body by {@code R·Δ} &mdash; which holds
 * the world geometry still <em>only at scale 1</em>. At scale S the world transform is
 * {@code pos + R·S·(x − rotPoint)}, so the unscaled compensation shifts the whole vehicle by {@code R·(I−S)·Δ},
 * smeared over the client's pose interpolation: the "moves slightly, quite fast, for a brief instant" glitch
 * (the same class of bug the LittleTiles compat once had with its zero-mass CoM swings). Pre-scaling the movement
 * ({@code R·S·Δ}) makes the position/rotation-point change cancel exactly, on the server and in the client's
 * linear pose lerp alike. Verified single {@code Quaterniond.transform(Vector3d)} invoke in {@code uploadData}
 * via javap against the shipped jar.</p>
 */
@Mixin(value = MergedMassTracker.class, remap = false)
public abstract class MergedMassTrackerMixin {

    @Shadow
    @Final
    private ServerSubLevel subLevel;

    @Redirect(
        method = "uploadData",
        at = @At(
            value = "INVOKE",
            target = "Lorg/joml/Quaterniond;transform(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"))
    private Vector3d sablescale$scaleComMovement(final Quaterniond orientation, final Vector3d movement) {
        final Vector3dc scale = this.subLevel.logicalPose().scale();
        if (scale.x() != 1.0 || scale.y() != 1.0 || scale.z() != 1.0)
            movement.mul(scale.x(), scale.y(), scale.z());
        return orientation.transform(movement);
    }
}
