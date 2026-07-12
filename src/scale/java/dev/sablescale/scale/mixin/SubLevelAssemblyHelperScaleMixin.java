package dev.sablescale.scale.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;

/**
 * Makes pieces that split off a <b>scaled</b> sub-level inherit its scale.
 *
 * <p>{@code SubLevelAssemblyHelper.assembleBlocks} (the path both manual assembly and connectivity splits go
 * through) builds the new sub-level's pose from the containing sub-level's position/orientation but leaves the
 * scale at (1,1,1) &mdash; a chunk broken off a &times;0.5 vehicle popped back to full size. The position math is
 * already scale-aware ({@code transformPosition} on the containing pose), so copying the containing scale into the
 * pose before allocation is the whole fix; everything downstream (rendering, collider resampling, networking)
 * picks it up from there.</p>
 */
@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public abstract class SubLevelAssemblyHelperScaleMixin {

    @Redirect(
        method = "assembleBlocks",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/sublevel/ServerSubLevelContainer;allocateNewSubLevel(Ldev/ryanhcode/sable/companion/math/Pose3d;)Ldev/ryanhcode/sable/sublevel/SubLevel;"))
    private static SubLevel sablescale$inheritScale(final ServerSubLevelContainer container, final Pose3d pose,
                                                    final ServerLevel level, final BlockPos anchor,
                                                    final Iterable<BlockPos> blocks, final BoundingBox3ic bounds) {
        if (Sable.HELPER.getContaining(level, anchor) instanceof ServerSubLevel containing && !containing.isRemoved())
            pose.scale().set(containing.logicalPose().scale());
        return container.allocateNewSubLevel(pose);
    }
}
