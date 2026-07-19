package dev.sablescale.scale.simulated.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.world.level.block.entity.BlockEntity;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;

/**
 * Makes a swivel bearing's rotating half inherit the <b>scale</b> of the vehicle it is mounted on.
 *
 * <p>Sable's {@code SubLevelAssemblyHelper.assembleBlocks} is the one place new sub-levels normally come from, and
 * {@link dev.sablescale.scale.mixin.SubLevelAssemblyHelperScaleMixin} already copies the containing scale there -
 * which covers the bearing's usual path, since {@code SimAssemblyHelper.assembleFromSingleBlock} delegates to it.
 * But when the block in front of the bearing is <em>air</em> that helper returns {@code null}, and
 * {@code SwivelBearingBlockEntity.assemble} falls back to allocating the plate's sub-level itself from a fresh
 * {@code Pose3d} - whose scale is (1,1,1). On a scaled vehicle that produced a full-size plate hanging off a
 * shrunken (or dwarfed by a grown) hull, with the rotary constraint then spanning two differently-scaled bodies.
 * Copying the containing sub-level's scale into the pose before allocation fixes it, exactly as the assembly-helper
 * mixin does; everything downstream (collider resampling, constraint anchors, networking) follows from the pose.</p>
 */
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity", remap = false)
public abstract class SwivelBearingAssemblyScaleMixin {

    @Redirect(
        method = "assemble",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/sublevel/ServerSubLevelContainer;allocateNewSubLevel(Ldev/ryanhcode/sable/companion/math/Pose3d;)Ldev/ryanhcode/sable/sublevel/SubLevel;"))
    private SubLevel sablescale$inheritScale(final ServerSubLevelContainer container, final Pose3d pose) {
        final BlockEntity self = (BlockEntity) (Object) this;
        if (self.getLevel() != null
            && Sable.HELPER.getContaining(self.getLevel(), self.getBlockPos()) instanceof ServerSubLevel containing
            && !containing.isRemoved())
            pose.scale().set(containing.logicalPose().scale());
        return container.allocateNewSubLevel(pose);
    }
}
