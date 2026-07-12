package dev.sablescale.scale.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinhelpers.block_outline_render.SubLevelCamera;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;

/**
 * Fixes the block-break outline on <b>scaled</b> sub-levels rendering at the camera.
 *
 * <p>This is an upstream Sable transform-order bug: its outline mixin ({@code block_outline_render.LevelRendererMixin},
 * priority 2000) is the one Sable render path that <em>does</em> read {@code pose.scale()}, but it applies
 * {@code poseStack.scale(S)} <em>after</em> the camera-relative translate, i.e. it builds
 * {@code T(pos−cam)·R·T(plotCam−rotPoint)·S} instead of {@code T(pos−cam)·R·S·T(plotCam−rotPoint)}. The scale thereby
 * multiplies the plot-space camera offset too, displacing the outline by {@code R·(I−S)·(plotCam−rotPoint)}
 * &mdash; at &times;0.5 that is exactly {@code camera − vehicle}: the outline lands on the camera.</p>
 *
 * <p>This wrap targets the same {@code ClientHooks.onDrawHighlight} call at default priority (1000 &lt; 2000), so
 * MixinExtras chains it <em>inside</em> Sable's wrap: Sable pushes its (wrongly ordered) transform, then calls
 * through to us, and we append the compensation {@code T((I−S⁻¹)·d)} with {@code d = plotCam − rotationPoint},
 * which algebraically lands the stack on the correct matrix. No-op at scale 1 or for world-block outlines
 * (detected by Sable's plot-space {@link SubLevelCamera} standing in for the real camera). If Sable ever fixes the
 * ordering upstream, drop this mixin.</p>
 */
@Mixin(value = LevelRenderer.class, priority = 1000)
public abstract class LevelRendererOutlineScaleMixin {

    @WrapOperation(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/ClientHooks;onDrawHighlight(Lnet/minecraft/client/renderer/LevelRenderer;Lnet/minecraft/client/Camera;Lnet/minecraft/world/phys/HitResult;Lnet/minecraft/client/DeltaTracker;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)Z"))
    private boolean sablescale$fixScaledOutline(final LevelRenderer context, final Camera camera, final HitResult target,
                                                final DeltaTracker deltaTracker, final PoseStack poseStack,
                                                final MultiBufferSource bufferSource, final Operation<Boolean> original) {
        if (camera instanceof SubLevelCamera && target instanceof BlockHitResult blockTarget
            && Sable.HELPER.getContaining(Minecraft.getInstance().level, blockTarget.getBlockPos()) instanceof ClientSubLevel subLevel) {
            final Pose3dc pose = subLevel.renderPose();
            final Vector3dc scale = pose.scale();
            if (scale.x() != 1.0 || scale.y() != 1.0 || scale.z() != 1.0) {
                final Vec3 plotCam = camera.getPosition();
                final Vector3dc rotationPoint = pose.rotationPoint();
                poseStack.translate(
                    (float) ((plotCam.x - rotationPoint.x()) * (1.0 - 1.0 / scale.x())),
                    (float) ((plotCam.y - rotationPoint.y()) * (1.0 - 1.0 / scale.y())),
                    (float) ((plotCam.z - rotationPoint.z()) * (1.0 - 1.0 / scale.z())));
            }
        }

        return original.call(context, camera, target, deltaTracker, poseStack, bufferSource);
    }
}
