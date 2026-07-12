package dev.sablescale.scale.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.sablescale.scale.ScaledColliderPipeline;
import dev.sablescale.scale.ScaledColliders;
import dev.sablescale.scale.ScaledMass;

/**
 * Routes the voxel-collider uploads of <b>scaled</b> sub-levels through {@link ScaledColliders} (see its doc for
 * the resampling scheme). Stock chunk uploads/removals/block updates for a managed plot are cancelled and turned
 * into dirty marks; the manager rebuilds the body's native chunks once per server tick. Suppressed-section
 * bookkeeping keeps native add/remove calls exactly paired even across scale transitions and plot unloads.
 * {@code onStatsChanged}'s local-bounds refresh is redirected to the scaled bounds for managed bodies, and its
 * mass-properties upload to the scale-corrected values (see {@link ScaledMass}).
 */
@Mixin(value = RapierPhysicsPipeline.class, remap = false)
public abstract class RapierPhysicsPipelineMixin implements ScaledColliderPipeline {

    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    protected abstract long getSceneHandle();

    @Override
    public ServerLevel sablescale$level() {
        return this.level;
    }

    @Override
    public long sablescale$sceneHandle() {
        return this.getSceneHandle();
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void sablescale$register(final ServerLevel level, final CallbackInfo ci) {
        ScaledColliders.register(level, (RapierPhysicsPipeline) (Object) this);
    }

    @Inject(method = "handleChunkSectionAddition", at = @At("HEAD"), cancellable = true)
    private void sablescale$interceptSectionAdd(final LevelChunkSection section, final int x, final int y, final int z,
                                                final boolean uploadDataIfGlobal, final CallbackInfo ci) {
        final ServerSubLevel subLevel = this.sablescale$managedPlotSubLevel(x, z);
        if (subLevel != null) {
            ScaledColliders.onStockAddSuppressed(this.level, SectionPos.asLong(x, y, z));
            ScaledColliders.markDirty(subLevel);
            ci.cancel();
        }
    }

    @Inject(method = "handleChunkSectionRemoval", at = @At("HEAD"), cancellable = true)
    private void sablescale$interceptSectionRemove(final int x, final int y, final int z, final CallbackInfo ci) {
        // The section never made it into (or was already dropped from) the native scene - keep add/remove paired.
        if (ScaledColliders.consumeSuppressedStock(this.level, SectionPos.asLong(x, y, z))) {
            final ServerSubLevel subLevel = this.sablescale$plotSubLevel(x, z);
            if (subLevel != null)
                ScaledColliders.markDirty(subLevel);
            ci.cancel();
        }
    }

    @Inject(method = "handleBlockChange", at = @At("HEAD"), cancellable = true)
    private void sablescale$interceptBlockChange(final SectionPos sectionPos, final LevelChunkSection chunk,
                                                 final int x, final int y, final int z,
                                                 final BlockState oldState, final BlockState newState, final CallbackInfo ci) {
        final ServerSubLevel subLevel = this.sablescale$managedPlotSubLevel(sectionPos.x(), sectionPos.z());
        if (subLevel != null) {
            ScaledColliders.markDirty(subLevel);
            ci.cancel();
        }
    }

    @Inject(method = "add(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Ldev/ryanhcode/sable/companion/math/Pose3dc;)V", at = @At("TAIL"))
    private void sablescale$onBodyAdded(final ServerSubLevel subLevel, final dev.ryanhcode.sable.companion.math.Pose3dc pose, final CallbackInfo ci) {
        if (ScaledColliders.isManaged(subLevel))
            ScaledColliders.markDirty(subLevel); // sub-level loaded from disk already scaled
    }

    @Inject(method = "remove(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;)V", at = @At("HEAD"))
    private void sablescale$onBodyRemoved(final ServerSubLevel subLevel, final CallbackInfo ci) {
        ScaledColliders.onBodyRemoved(subLevel);
    }

    /**
     * All mass uploads funnel through this one call: body creation ({@code add} calls {@code onStatsChanged}),
     * block place/break ({@code MergedMassTracker.uploadData}) and the scale command ({@code SubLevelScale.apply}
     * calls {@code onStatsChanged} directly, since nothing else re-uploads on a scale-only change).
     * Verified single {@code setMassPropertiesFrom} invoke in {@code onStatsChanged} via javap against the
     * shipped rapier jar.
     */
    @Redirect(
        method = "onStatsChanged",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;setMassPropertiesFrom(JILdev/ryanhcode/sable/api/physics/mass/MassData;)V"))
    private void sablescale$scaledMassProperties(final long handle, final int bodyId, final MassData massData,
                                                 final ServerSubLevel subLevel) {
        if (ScaledColliders.isManaged(subLevel))
            ScaledMass.upload(handle, bodyId, massData, subLevel.logicalPose().scale());
        else
            Rapier3DAccessor.sablescale$setMassPropertiesFrom(handle, bodyId, massData);
    }

    @Redirect(
        method = "onStatsChanged",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;setLocalBounds(JIIIIIII)V"))
    private void sablescale$scaledLocalBounds(final long handle, final int bodyId,
                                              final int minX, final int minY, final int minZ,
                                              final int maxX, final int maxY, final int maxZ,
                                              final ServerSubLevel subLevel) {
        if (ScaledColliders.isManaged(subLevel)) {
            ScaledColliders.applyScaledLocalBounds(handle, bodyId, subLevel);
            // onStatsChanged runs right after a CoM change re-anchored the rotation point (and teleported the
            // body). The resampled lattice pivots on the rotation point, so rebuild it NOW - before the physics
            // step - or the offset collider gets a solver impulse (the block place/break "twitch").
            ScaledColliders.rebuildIfRotationPointMoved(subLevel);
        } else {
            Rapier3DAccessor.sablescale$setLocalBounds(handle, bodyId, minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    private ServerSubLevel sablescale$managedPlotSubLevel(final int chunkX, final int chunkZ) {
        final ServerSubLevel subLevel = this.sablescale$plotSubLevel(chunkX, chunkZ);
        return subLevel != null && ScaledColliders.isManaged(subLevel) ? subLevel : null;
    }

    private ServerSubLevel sablescale$plotSubLevel(final int chunkX, final int chunkZ) {
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);
        if (container == null)
            return null;
        final LevelPlot plot = container.getPlot(chunkX, chunkZ);
        return plot != null && plot.getSubLevel() instanceof ServerSubLevel subLevel && !subLevel.isRemoved() ? subLevel : null;
    }
}
