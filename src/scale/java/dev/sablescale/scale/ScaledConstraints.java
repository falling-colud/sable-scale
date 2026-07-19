package dev.sablescale.scale;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

/**
 * Makes rapier <b>constraints</b> (Create stickers &amp; super glue, Simulated merging glue / swivel bearings /
 * handles / ropes / docking connectors / the physics staff) line up with, and drive, a <b>scaled</b> sub-level.
 *
 * <p>Two independent corrections, both keyed off the same fact: {@link ScaledColliders} resamples a scaled body's
 * geometry onto {@code rotPoint + S·(blockPos − rotPoint)} because the native body transform carries no scale
 * ({@code world = pos + R·(local − centerOfMass)}).</p>
 *
 * <ul>
 *   <li><b>Anchors</b> ({@link #anchor}). Callers pass constraint anchors in <em>unscaled plot-local block</em>
 *       coordinates (Sable's {@code validateAnchors} enforces {@code plot.contains(pos)}), but in a scaled body the
 *       block that anchor means now physically sits at {@code rotPoint + S·(pos − rotPoint)}. Passing the raw
 *       anchor therefore pinned the joint to a phantom point offset by {@code (1−S)·(pos − rotPoint)} - a sticker
 *       on a shrunken vehicle grabbed thin air and yanked the hull. We map every anchor through the same transform
 *       the collider resampler uses, so the joint lands on the block you actually stuck.</li>
 *   <li><b>Motor gains</b> ({@link #motorFactor}). Motorised joints size their PD gains against the
 *       <em>unscaled</em> mass tracker (Simulated's swivel bearing: {@code kP = stiffness · unscaledInertia}) or
 *       against fixed constants (its handle: 240/30, cap 120 N). The body they actually drive has mass {@code k·m}
 *       and inertia {@code S⁵·I}, so at {@code S<1} a bearing tuned for {@code I} overpowers an {@code S⁵·I} body
 *       and oscillates - the rotary twin of the wheel bounce. Scaling gains and the force cap by {@code k} on
 *       linear axes and {@code S⁵} on angular ones restores the unscaled response, matching
 *       {@link dev.sablescale.scale.mixin.RapierActuatorForceScaleMixin}'s impulse rule.</li>
 * </ul>
 *
 * <p>Uniform scale only. Anchors are mapped with the rotation point <em>current at creation</em>; a later centre-of-
 * mass shift (block place/break) re-anchors {@code rotPoint} and rebuilds the collider, but cannot move an already
 * baked native joint frame, so a constrained scaled vehicle that is heavily rebuilt can drift by
 * {@code S·Δ(rotPoint)} until the joint is remade (stickers remake theirs on re-attach).</p>
 */
public final class ScaledConstraints {

    private ScaledConstraints() {}

    /** @return the sub-level whose live scale governs this joint: the scaled body, preferring {@code bodyA}. */
    @Nullable
    public static ServerSubLevel scaleSource(@Nullable final ServerSubLevel bodyA, @Nullable final ServerSubLevel bodyB) {
        if (bodyA != null && ScaledColliders.isManaged(bodyA))
            return bodyA;
        if (bodyB != null && ScaledColliders.isManaged(bodyB))
            return bodyB;
        return null;
    }

    /**
     * Maps a constraint anchor from unscaled plot-local space onto {@code body}'s resampled (scaled) geometry:
     * {@code rotPoint + S·(pos − rotPoint)}. Unscaled bodies, and anchors against the static world
     * ({@code body == null}, i.e. already world-space), pass through untouched.
     */
    public static Vector3dc anchor(final Vector3dc pos, @Nullable final PhysicsPipelineBody body) {
        if (!(body instanceof ServerSubLevel subLevel) || !ScaledColliders.isManaged(subLevel))
            return pos;
        final Pose3dc pose = subLevel.logicalPose();
        final Vector3dc rp = pose.rotationPoint(), s = pose.scale();
        return new Vector3d(
            rp.x() + s.x() * (pos.x() - rp.x()),
            rp.y() + s.y() * (pos.y() - rp.y()),
            rp.z() + s.z() * (pos.z() - rp.z()));
    }

    /**
     * Gain/force factor for a motor on {@code axis} ({@code ConstraintJointAxis.ordinal()}: 0-2 linear, 3-5
     * angular) driving {@code source}. {@code k} for linear (force vs. mass {@code k·m}), {@code S⁵} for angular
     * (torque vs. inertia {@code S⁵·I}); {@code 1} when nothing is scaled.
     */
    public static double motorFactor(@Nullable final ServerSubLevel source, final int axis) {
        if (source == null)
            return 1.0;
        final Vector3dc s = source.logicalPose().scale();
        final double k = s.x() * s.y() * s.z();
        return axis < 3 ? k : k * s.x() * s.x(); // uniform scale: S⁵ = k·S²
    }
}
