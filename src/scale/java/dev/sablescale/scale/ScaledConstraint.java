package dev.sablescale.scale;

import org.jetbrains.annotations.Nullable;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;

/**
 * The two bodies a rapier constraint was created between, stamped onto the handle so later mutations
 * ({@code setMotor}, {@code setFrame1/2}) can look up their live scale. Rapier's own handle keeps only the native
 * joint id, and the pose (scale <em>and</em> rotation point) moves over the body's lifetime, so we hold the
 * sub-levels rather than a snapshot of their scale.
 *
 * @see ScaledConstraints
 */
public interface ScaledConstraint {

    void sablescale$setBodies(@Nullable ServerSubLevel bodyA, @Nullable ServerSubLevel bodyB);

    @Nullable
    ServerSubLevel sablescale$bodyA();

    @Nullable
    ServerSubLevel sablescale$bodyB();
}
