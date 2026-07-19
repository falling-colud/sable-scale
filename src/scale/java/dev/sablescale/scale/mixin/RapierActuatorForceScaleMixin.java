package dev.sablescale.scale.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import org.joml.Vector3dc;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.sablescale.scale.ScaledColliders;

/**
 * Gives every <b>actuator</b> force applied to a <b>scaled</b> sub-level the right effect on its scaled body.
 *
 * <p>Sable's block-entity actors (Offroad wheel suspension, Create Aeronautics propeller thrust / balloon lift /
 * recoil, Create Simulated springs / magnets / drag / plungers, Sable's own reaction wheels, floating blocks and
 * friction) all compute their force/impulse in <em>unscaled plot-local</em> units against the unscaled
 * {@code MassTracker}, then flush it here through {@code applyLinearAndAngularImpulse}
 * ({@code ForceTotal.applyForces}) or {@code applyImpulse} ({@code RigidBodyHandle.applyImpulseAtPoint}). But
 * {@link dev.sablescale.scale.ScaledMass} replaced the native body's mass with {@code k·m} ({@code k = sx·sy·sz})
 * and its inertia with the scaled tensor ({@code S⁵·I} for uniform scale). So the stock impulse produced
 * {@code Δv = impulse/(k·m)} &mdash; at {@code S<1} a suspension force calibrated for mass {@code m} over-accelerates
 * the {@code k·m} body by {@code 1/k}, and the wheel bounces (the reported symptom); thrust/lift under-drive an
 * upscaled body by the same factor.</p>
 *
 * <p>Gravity, buoyancy and contact are applied inside the native step against the scaled collider and scaled mass,
 * so they are already consistent and are <em>not</em> touched here &mdash; only the Java-side actuator impulses are.
 * The correction restores the exact <em>unscaled</em> plot-local rigid-body response, which Sable's render/collision
 * scale then maps to the {@code S}-sized world vehicle:</p>
 *
 * <ul>
 *   <li><b>linear</b> &times;{@code k}: {@code Δv = k·F/(k·m) = F/m} &mdash; the unscaled acceleration;</li>
 *   <li><b>angular</b> &times;{@code S⁵}: {@code Δω = S⁵·τ/(S⁵·I) = τ/I} &mdash; the unscaled angular acceleration.
 *       For the point-force path ({@code applyForce}, torque derived natively as {@code lever × force}) the same
 *       {@code S⁵} is reached by scaling the lever by {@code S²} on top of the force's {@code k}.</li>
 * </ul>
 *
 * <p>Uniform scale only (what {@code /sablescale} sets): the angular factor uses {@code S = sx}. At scale (1,1,1)
 * the redirects fall through untouched. Not corrected: rapier <em>constraint motors</em> (Simulated swivel
 * bearings / handles / physics staff / ropes) set PD gains through {@code setConstraintMotor}, which carries only a
 * joint handle &mdash; no body to read a scale from, and a joint may bridge two differently-scaled bodies.</p>
 */
@Mixin(value = RapierPhysicsPipeline.class, remap = false)
public abstract class RapierActuatorForceScaleMixin {

    @Redirect(
        method = "applyLinearAndAngularImpulse",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;applyForceAndTorque(JIDDDDDDZ)V"))
    private void sablescale$scaleForceAndTorque(final long handle, final int bodyId,
                                                double fx, double fy, double fz, double tx, double ty, double tz,
                                                final boolean nativeWake,
                                                final PhysicsPipelineBody body, final Vector3dc force,
                                                final Vector3dc torque, final boolean wakeUp) {
        if (body instanceof ServerSubLevel subLevel && ScaledColliders.isManaged(subLevel)) {
            final Vector3dc s = subLevel.logicalPose().scale();
            final double k = s.x() * s.y() * s.z();
            final double tf = k * s.x() * s.x(); // S⁵ for uniform scale (mass ×k, lever² ×S²)
            fx *= k; fy *= k; fz *= k;
            tx *= tf; ty *= tf; tz *= tf;
        }
        Rapier3DAccessor.sablescale$applyForceAndTorque(handle, bodyId, fx, fy, fz, tx, ty, tz, nativeWake);
    }

    @Redirect(
        method = "applyImpulse",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;applyForce(JIDDDDDDZ)V"))
    private void sablescale$scaleImpulse(final long handle, final int bodyId,
                                         double px, double py, double pz, double fx, double fy, double fz,
                                         final boolean nativeWake,
                                         final PhysicsPipelineBody body, final Vector3dc position, final Vector3dc force) {
        if (body instanceof ServerSubLevel subLevel && ScaledColliders.isManaged(subLevel)) {
            final Vector3dc s = subLevel.logicalPose().scale();
            final double k = s.x() * s.y() * s.z();
            final double leverFactor = s.x() * s.x(); // S²: with force ×k the derived torque scales S⁵
            px *= leverFactor; py *= leverFactor; pz *= leverFactor;
            fx *= k; fy *= k; fz *= k;
        }
        Rapier3DAccessor.sablescale$applyForce(handle, bodyId, px, py, pz, fx, fy, fz, nativeWake);
    }
}
