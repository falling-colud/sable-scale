package dev.sablescale.scale.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;

/** Static invokers for the package-private chunk/bounds/mass natives the scaled-collider resampler uploads through. */
@Mixin(value = Rapier3D.class, remap = false)
public interface Rapier3DAccessor {

    @Invoker(value = "addChunk", remap = false)
    static void sablescale$addChunk(final long handle, final int x, final int y, final int z,
                                    final int[] data, final boolean global, final int bodyId) {
        throw new AssertionError();
    }

    @Invoker(value = "removeChunk", remap = false)
    static void sablescale$removeChunk(final long handle, final int x, final int y, final int z, final boolean global) {
        throw new AssertionError();
    }

    @Invoker(value = "setLocalBounds", remap = false)
    static void sablescale$setLocalBounds(final long handle, final int bodyId,
                                          final int minX, final int minY, final int minZ,
                                          final int maxX, final int maxY, final int maxZ) {
        throw new AssertionError();
    }

    /** The raw native behind {@code setMassPropertiesFrom}; inertia tensor is 9 doubles, row by row. */
    @Invoker(value = "setMassProperties", remap = false)
    static void sablescale$setMassProperties(final long handle, final int bodyId, final double mass,
                                             final double[] centerOfMass, final double[] inertiaTensor) {
        throw new AssertionError();
    }

    @Invoker(value = "setMassPropertiesFrom", remap = false)
    static void sablescale$setMassPropertiesFrom(final long handle, final int bodyId, final MassData massData) {
        throw new AssertionError();
    }

    /** Force + torque on a body (body frame); re-dispatched with scale-corrected magnitudes by the actuator mixin. */
    @Invoker(value = "applyForceAndTorque", remap = false)
    static void sablescale$applyForceAndTorque(final long handle, final int bodyId,
                                               final double fx, final double fy, final double fz,
                                               final double tx, final double ty, final double tz, final boolean wakeUp) {
        throw new AssertionError();
    }

    /** Force at a body-frame offset from the centre of mass (native derives the torque); scale-corrected re-dispatch. */
    @Invoker(value = "applyForce", remap = false)
    static void sablescale$applyForce(final long handle, final int bodyId,
                                      final double px, final double py, final double pz,
                                      final double fx, final double fy, final double fz, final boolean wakeUp) {
        throw new AssertionError();
    }
}
