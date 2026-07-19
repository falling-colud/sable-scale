package dev.sablescale.scale.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.server.level.ServerLevel;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintConfiguration;
import dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline;
import dev.ryanhcode.sable.physics.impl.rapier.constraint.fixed.RapierFixedConstraintHandle;
import dev.ryanhcode.sable.physics.impl.rapier.constraint.free.RapierFreeConstraintHandle;
import dev.ryanhcode.sable.physics.impl.rapier.constraint.generic.RapierGenericConstraintHandle;
import dev.ryanhcode.sable.physics.impl.rapier.constraint.rotary.RapierRotaryConstraintHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.sablescale.scale.ScaledConstraint;
import dev.sablescale.scale.ScaledConstraints;

/**
 * Anchors constraints onto the <b>resampled geometry</b> of a scaled sub-level, and remembers which bodies each
 * joint spans.
 *
 * <p>Every joint Sable/Create builds - Create's stickers and super glue, Simulated's merging glue, swivel bearings,
 * handles, ropes, docking connectors and the physics staff - funnels through
 * {@code RapierPhysicsPipeline.addConstraint}, which validates the anchors and then dispatches to one of four
 * {@code create}s. The anchors arrive in unscaled plot-local block coordinates, but a scaled body's blocks live at
 * {@code rotPoint + S·(pos − rotPoint)} (see {@link dev.sablescale.scale.ScaledColliders}), so stock pinned the
 * joint to a phantom point and the hull got yanked toward it. We rebuild each configuration record with mapped
 * anchors ({@link ScaledConstraints#anchor}) - <em>after</em> Sable's validation, which requires the raw anchor to
 * lie inside the plot and would reject a grown one - and stamp the handle with its bodies so
 * {@link RapierConstraintHandleScaleMixin} can scale motor gains later.</p>
 *
 * <p>Normals and orientations are left alone: a uniform scale preserves directions. Anchors against the static
 * world ({@code body == null}) are already world-space and pass through.</p>
 */
@Mixin(value = RapierPhysicsPipeline.class, remap = false)
public abstract class RapierConstraintScaleMixin {

    @Redirect(
        method = "addConstraint",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ryanhcode/sable/physics/impl/rapier/constraint/rotary/RapierRotaryConstraintHandle;create(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/api/physics/PhysicsPipelineBody;Ldev/ryanhcode/sable/api/physics/PhysicsPipelineBody;Ldev/ryanhcode/sable/api/physics/constraint/RotaryConstraintConfiguration;)Ldev/ryanhcode/sable/physics/impl/rapier/constraint/rotary/RapierRotaryConstraintHandle;"))
    private RapierRotaryConstraintHandle sablescale$rotary(final ServerLevel level, final PhysicsPipelineBody bodyA,
                                                           final PhysicsPipelineBody bodyB, final RotaryConstraintConfiguration config) {
        return this.sablescale$stamp(RapierRotaryConstraintHandle.create(level, bodyA, bodyB,
            new RotaryConstraintConfiguration(
                ScaledConstraints.anchor(config.pos1(), bodyA), ScaledConstraints.anchor(config.pos2(), bodyB),
                config.normal1(), config.normal2())), bodyA, bodyB);
    }

    @Redirect(
        method = "addConstraint",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ryanhcode/sable/physics/impl/rapier/constraint/fixed/RapierFixedConstraintHandle;create(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/api/physics/PhysicsPipelineBody;Ldev/ryanhcode/sable/api/physics/PhysicsPipelineBody;Ldev/ryanhcode/sable/api/physics/constraint/FixedConstraintConfiguration;)Ldev/ryanhcode/sable/physics/impl/rapier/constraint/fixed/RapierFixedConstraintHandle;"))
    private RapierFixedConstraintHandle sablescale$fixed(final ServerLevel level, final PhysicsPipelineBody bodyA,
                                                         final PhysicsPipelineBody bodyB, final FixedConstraintConfiguration config) {
        return this.sablescale$stamp(RapierFixedConstraintHandle.create(level, bodyA, bodyB,
            new FixedConstraintConfiguration(
                ScaledConstraints.anchor(config.pos1(), bodyA), ScaledConstraints.anchor(config.pos2(), bodyB),
                config.orientation())), bodyA, bodyB);
    }

    @Redirect(
        method = "addConstraint",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ryanhcode/sable/physics/impl/rapier/constraint/free/RapierFreeConstraintHandle;create(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/api/physics/PhysicsPipelineBody;Ldev/ryanhcode/sable/api/physics/PhysicsPipelineBody;Ldev/ryanhcode/sable/api/physics/constraint/FreeConstraintConfiguration;)Ldev/ryanhcode/sable/physics/impl/rapier/constraint/free/RapierFreeConstraintHandle;"))
    private RapierFreeConstraintHandle sablescale$free(final ServerLevel level, final PhysicsPipelineBody bodyA,
                                                       final PhysicsPipelineBody bodyB, final FreeConstraintConfiguration config) {
        return this.sablescale$stamp(RapierFreeConstraintHandle.create(level, bodyA, bodyB,
            new FreeConstraintConfiguration(
                ScaledConstraints.anchor(config.pos1(), bodyA), ScaledConstraints.anchor(config.pos2(), bodyB),
                config.orientation())), bodyA, bodyB);
    }

    @Redirect(
        method = "addConstraint",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ryanhcode/sable/physics/impl/rapier/constraint/generic/RapierGenericConstraintHandle;create(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/api/physics/PhysicsPipelineBody;Ldev/ryanhcode/sable/api/physics/PhysicsPipelineBody;Ldev/ryanhcode/sable/api/physics/constraint/GenericConstraintConfiguration;)Ldev/ryanhcode/sable/physics/impl/rapier/constraint/generic/RapierGenericConstraintHandle;"))
    private RapierGenericConstraintHandle sablescale$generic(final ServerLevel level, final PhysicsPipelineBody bodyA,
                                                             final PhysicsPipelineBody bodyB, final GenericConstraintConfiguration config) {
        return this.sablescale$stamp(RapierGenericConstraintHandle.create(level, bodyA, bodyB,
            new GenericConstraintConfiguration(
                ScaledConstraints.anchor(config.pos1(), bodyA), ScaledConstraints.anchor(config.pos2(), bodyB),
                config.orientation1(), config.orientation2(), config.lockedAxes())), bodyA, bodyB);
    }

    /** Records the joint's bodies on the handle so later {@code setMotor}/{@code setFrame} calls can read their live scale. */
    @Unique
    private <T> T sablescale$stamp(final T handle, final PhysicsPipelineBody bodyA, final PhysicsPipelineBody bodyB) {
        if (handle instanceof ScaledConstraint constraint)
            constraint.sablescale$setBodies(
                bodyA instanceof ServerSubLevel subLevelA ? subLevelA : null,
                bodyB instanceof ServerSubLevel subLevelB ? subLevelB : null);
        return handle;
    }
}
