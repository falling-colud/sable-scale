package dev.sablescale.scale.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;

/**
 * Restores full interaction reach on <b>scaled</b> sub-levels.
 *
 * <p>Sable's {@code interaction_distance.PlayerMixin} grants {@code canInteractWithBlock} by transforming the eye
 * into <em>plot space</em> and comparing that distance against the world-space reach. Plot distances are
 * {@code 1/S} times world distances, so on a &times;0.5 vehicle the effective reach halves &mdash; "breaking /
 * placing from too far doesn't work even if it looks like it should". This hook adds the scale-corrected grant:
 * plot distance &times; scale = world distance. It only ever <em>grants</em> (like Sable's own hook), and only for
 * scale &ne; 1, so stock behaviour is untouched; with Sable's hook applied at higher priority its (conservative at
 * S&lt;1) grant runs first and this one catches what it wrongly rejects.</p>
 */
@Mixin(value = Player.class, priority = 900)
public abstract class PlayerReachScaleMixin extends LivingEntity {

    @Shadow
    public abstract double blockInteractionRange();

    protected PlayerReachScaleMixin(final EntityType<? extends LivingEntity> entityType, final Level level) {
        super(entityType, level);
    }

    @Inject(method = "canInteractWithBlock", at = @At("HEAD"), cancellable = true)
    private void sablescale$scaledReach(final BlockPos pos, final double slop, final CallbackInfoReturnable<Boolean> cir) {
        final SubLevel subLevel = Sable.HELPER.getContaining(this.level(), pos);
        if (subLevel == null)
            return;
        final Pose3dc pose = subLevel.logicalPose();
        final Vector3dc scale = pose.scale();
        if (scale.x() == 1.0 && scale.y() == 1.0 && scale.z() == 1.0)
            return; // Sable's own hook is correct at scale 1

        final double range = this.blockInteractionRange() + slop;
        final Vec3 plotEye = pose.transformPositionInverse(this.getEyePosition());
        // World distance = plot distance x scale; the max component keeps this conservative for non-uniform scale.
        final double s = Math.max(scale.x(), Math.max(scale.y(), scale.z()));
        if (new AABB(pos).distanceToSqr(plotEye) * s * s < range * range)
            cir.setReturnValue(true);
    }
}
