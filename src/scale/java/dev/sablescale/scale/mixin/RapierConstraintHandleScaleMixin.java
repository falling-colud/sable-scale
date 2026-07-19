package dev.sablescale.scale.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import dev.ryanhcode.sable.physics.impl.rapier.constraint.RapierConstraintHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.sablescale.scale.ScaledConstraint;
import dev.sablescale.scale.ScaledConstraints;

/**
 * Sizes a motorised joint's <b>PD gains</b> to the body it actually drives when that body is <b>scaled</b>, and
 * carries the joint's bodies (stamped by {@link RapierConstraintScaleMixin}) for this and the generic-frame mixin.
 *
 * <p>{@code setMotor} is the single implementation behind every motorised joint - Simulated's swivel bearing
 * ({@code kP = stiffness · unscaledInertia}), its handle and physics staff (fixed gains + a fixed force cap), its
 * ropes and docking connectors. All of them size their gains against the <em>unscaled</em> mass tracker or plain
 * constants, while the native body carries mass {@code k·m} and inertia {@code S⁵·I}. At {@code S<1} that left a
 * bearing tuned for {@code I} slamming an {@code S⁵·I} body around - the rotary twin of the wheel bounce - and at
 * {@code S>1} far too weak to move it. Scaling stiffness, damping and the force cap by {@code k} on the linear axes
 * and {@code S⁵} on the angular ones (see {@link ScaledConstraints#motorFactor}) restores the unscaled response.</p>
 *
 * <p>The motor <em>target</em> is deliberately not scaled: rotary targets are angles (scale-invariant) and every
 * linear motor here targets 0 (hold at the anchor).</p>
 */
@Mixin(value = RapierConstraintHandle.class, remap = false)
public abstract class RapierConstraintHandleScaleMixin implements ScaledConstraint {

    @Unique
    @Nullable
    private ServerSubLevel sablescale$bodyA;

    @Unique
    @Nullable
    private ServerSubLevel sablescale$bodyB;

    @Override
    public void sablescale$setBodies(@Nullable final ServerSubLevel bodyA, @Nullable final ServerSubLevel bodyB) {
        this.sablescale$bodyA = bodyA;
        this.sablescale$bodyB = bodyB;
    }

    @Override
    @Nullable
    public ServerSubLevel sablescale$bodyA() {
        return this.sablescale$bodyA;
    }

    @Override
    @Nullable
    public ServerSubLevel sablescale$bodyB() {
        return this.sablescale$bodyB;
    }

    @Redirect(
        method = "setMotor",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;setConstraintMotor(JJIDDDZD)V"))
    private void sablescale$scaleMotorGains(final long sceneHandle, final long handle, final int axis,
                                            final double target, final double stiffness, final double damping,
                                            final boolean hasForceLimit, final double maxForce) {
        final double factor = ScaledConstraints.motorFactor(
            ScaledConstraints.scaleSource(this.sablescale$bodyA, this.sablescale$bodyB), axis);
        Rapier3D.setConstraintMotor(sceneHandle, handle, axis, target,
            stiffness * factor, damping * factor, hasForceLimit, maxForce * factor);
    }
}
