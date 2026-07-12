package dev.sablescale.scale.mixin;

import java.util.function.Predicate;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;

/**
 * Replaces Sable's {@code BlockGetter.clip} overwrite (its {@code clip_overwrite.BlockGetterMixin}, priority 1100)
 * with a scale-correct copy. Two defects at scale &ne; 1:
 *
 * <ol>
 *   <li><b>Mismatched distance metric.</b> Stock Sable compares the sub-level hit's distance measured in
 *       <em>plot space</em> against the main-level hit's distance measured in <em>world space</em>. At &times;0.5
 *       plot distances are 2&times; world distances, so terrain <em>behind</em> a shrunken vehicle out-competed the
 *       vehicle you were aiming at (and at &times;2 the vehicle would win through walls). Fixed by projecting the
 *       sub-level hit back to world space before comparing.</li>
 *   <li><b>Candidate lookup via the physics broadphase.</b> {@code getAllIntersecting} answers from the rapier
 *       broadphase, whose colliders are always unscaled (native rapier has no shape-scale API), so an
 *       <em>up</em>scaled sub-level's outer blocks could not be targeted at all. Fixed by filtering the container's
 *       sub-levels against their logical (scale-aware) bounding boxes instead.</li>
 * </ol>
 *
 * <p>At scale (1,1,1) the behaviour is exactly Sable's. Mixin priority 2100 &gt; 1100 makes this overwrite win;
 * revisit when updating Sable.</p>
 */
@Mixin(value = BlockGetter.class, priority = 2100)
public interface BlockGetterClipScaleMixin {

    /**
     * @author sablescale (after dev.ryanhcode.sable.mixin.clip_overwrite.BlockGetterMixin)
     * @reason Sable's overwrite compares plot-space vs world-space hit distances and queries the unscaled physics
     * broadphase, both of which break targeting on scaled sub-levels; see the class doc.
     */
    @Overwrite
    default BlockHitResult clip(final ClipContext originalContext) {
        final BlockGetter self = (BlockGetter) this;
        if (!(this instanceof Level level))
            return sablescale$originalClip(self, originalContext);
        if (originalContext instanceof ClipContextExtension extension && extension.sable$doNotProject())
            return sablescale$originalClip(self, originalContext);

        final SubLevel ignoredSubLevel = originalContext instanceof ClipContextExtension extension
            ? extension.sable$getIgnoredSubLevel() : null;
        final Predicate<SubLevel> subLevelIgnoring = originalContext instanceof ClipContextExtension extension
            ? extension.sable$getSubLevelIgnoring() : null;
        final ClipContext.Block blockMode = ((ClipContextAccessor) (Object) originalContext).sablescale$getBlock();
        final ClipContext.Fluid fluidMode = ((ClipContextAccessor) (Object) originalContext).sablescale$getFluid();
        final CollisionContext collisionContext = ((ClipContextAccessor) (Object) originalContext).sablescale$getCollisionContext();
        final ActiveSableCompanion helper = Sable.HELPER;

        // Project a start/end that sits inside a sub-level out to world space (same as stock).
        ClipContext clipContext = originalContext;
        final SubLevel fromSubLevel = helper.getContaining(level, clipContext.getFrom());
        if (fromSubLevel != null) {
            final Vec3 from = sablescale$pose(level, fromSubLevel).transformPosition(clipContext.getFrom());
            clipContext = new ClipContext(from, clipContext.getTo(), blockMode, fluidMode, collisionContext);
        }

        final SubLevel toSubLevel = helper.getContaining(level, clipContext.getTo());
        if (toSubLevel != null) {
            final Vec3 to = sablescale$pose(level, toSubLevel).transformPosition(clipContext.getTo());
            clipContext = new ClipContext(clipContext.getFrom(), to, blockMode, fluidMode, collisionContext);
        }

        BlockHitResult minResult;
        double minDistance = Double.MAX_VALUE;
        if (clipContext instanceof ClipContextExtension extension && extension.sable$isIgnoreMainLevel()) {
            final Vec3 diff = clipContext.getFrom().subtract(clipContext.getTo());
            minResult = BlockHitResult.miss(clipContext.getTo(), Direction.getNearest(diff.x, diff.y, diff.z),
                BlockPos.containing(clipContext.getTo()));
        } else {
            minResult = sablescale$originalClip(self, clipContext);
            minDistance = minResult.getLocation().distanceTo(clipContext.getFrom());
        }

        // Candidates from the logical (scale-aware) bounds, not the unscaled physics broadphase.
        final Vec3 rayFrom = clipContext.getFrom();
        final Vec3 rayTo = clipContext.getTo();
        final BoundingBox3d rayBounds = new BoundingBox3d(
            Math.min(rayFrom.x, rayTo.x), Math.min(rayFrom.y, rayTo.y), Math.min(rayFrom.z, rayTo.z),
            Math.max(rayFrom.x, rayTo.x), Math.max(rayFrom.y, rayTo.y), Math.max(rayFrom.z, rayTo.z));
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null) {
            for (final SubLevel subLevel : container.getAllSubLevels()) {
                if (subLevel.isRemoved() || subLevel == ignoredSubLevel
                    || subLevelIgnoring != null && subLevelIgnoring.test(subLevel)
                    || !subLevel.boundingBox().intersects(rayBounds))
                    continue;

                final Pose3dc pose = sablescale$pose(level, subLevel);
                final Vec3 from = pose.transformPositionInverse(clipContext.getFrom());
                final Vec3 to = pose.transformPositionInverse(clipContext.getTo());
                if (helper.getContaining(level, from) != subLevel)
                    continue;

                final ClipContext subClipContext = new ClipContext(from, to, blockMode, fluidMode, collisionContext);
                final BlockHitResult subResult = sablescale$originalClip(subLevel.getLevel(), subClipContext);
                if (subResult.getType() != HitResult.Type.MISS) {
                    // The fix: compare in world space (stock measured this in plot space, off by the scale factor).
                    final double distance = pose.transformPosition(subResult.getLocation()).distanceTo(clipContext.getFrom());
                    if (distance < minDistance || minResult.getType() == HitResult.Type.MISS) {
                        minResult = subResult;
                        minDistance = distance;
                    }
                }
            }
        }

        return minResult;
    }

    @Unique
    private static Pose3dc sablescale$pose(final Level level, final SubLevel subLevel) {
        return level instanceof LevelPoseProviderExtension extension ? extension.sable$getPose(subLevel) : subLevel.logicalPose();
    }

    /** Vanilla's original {@code BlockGetter.clip} body (same copy Sable keeps for the per-space traversals). */
    @Unique
    @NotNull
    private static BlockHitResult sablescale$originalClip(final BlockGetter level, final ClipContext clipContext) {
        return BlockGetter.traverseBlocks(clipContext.getFrom(), clipContext.getTo(), clipContext, (ctx, blockPos) -> {
            final BlockState blockState = level.getBlockState(blockPos);
            final FluidState fluidState = level.getFluidState(blockPos);
            final Vec3 from = ctx.getFrom();
            final Vec3 to = ctx.getTo();
            final VoxelShape blockShape = ctx.getBlockShape(blockState, level, blockPos);
            final BlockHitResult blockHit = level.clipWithInteractionOverride(from, to, blockPos, blockShape, blockState);
            final VoxelShape fluidShape = ctx.getFluidShape(fluidState, level, blockPos);
            final BlockHitResult fluidHit = fluidShape.clip(from, to, blockPos);
            final double blockDistance = blockHit == null ? Double.MAX_VALUE : ctx.getFrom().distanceToSqr(blockHit.getLocation());
            final double fluidDistance = fluidHit == null ? Double.MAX_VALUE : ctx.getFrom().distanceToSqr(fluidHit.getLocation());
            return blockDistance <= fluidDistance ? blockHit : fluidHit;
        }, ctx -> {
            final Vec3 diff = ctx.getFrom().subtract(ctx.getTo());
            return BlockHitResult.miss(ctx.getTo(), Direction.getNearest(diff.x, diff.y, diff.z), BlockPos.containing(ctx.getTo()));
        });
    }
}
