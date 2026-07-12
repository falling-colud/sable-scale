package dev.sablescale.scale.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.compatibility.SableIrisCompat;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionContainer;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionRegion;
import foundry.veil.api.compat.IrisCompat;

/**
 * Makes the vanilla-sections block renderer (used by both {@code VanillaSubLevelRenderDispatcher} and the
 * Sodium-era {@code ReachAroundSubLevelRenderDispatcher}, including Iris's shadow-pass re-entry) honour the
 * pose <b>scale</b>.
 *
 * <p>Stock {@code renderChunkedSubLevel} composes the transform as {@code modelView · R} with the whole
 * translation smuggled through the per-section {@code CHUNK_OFFSET} uniform in <em>plot-local</em> space:
 * vertex path {@code MV·R·(vtx + chunkOffset)} with {@code chunkOffset = secOff + R⁻¹·(pos − R·p − cam)}
 * (where {@code p = rotationPoint − origin}). {@code renderPose().scale()} is never read &mdash; so scaled
 * sub-levels kept rendering at &times;1 while collision/picking (which go through the scale-aware pose
 * transforms) shrank, i.e. ghost blocks.</p>
 *
 * <p>The fix mirrors the same structure with the scale inserted where the logical transform has it
 * ({@code world = pos + R·S·(plotLocal − rotationPoint)}): the matrix becomes {@code modelView · R · S} and the
 * chunk offset moves to pre-scale space, {@code chunkOffset' = secOff − p + S⁻¹·R⁻¹·(pos − cam)}. At scale
 * (1,1,1) we leave stock code untouched.</p>
 */
@Mixin(value = VanillaChunkedSubLevelRenderData.class, remap = false)
public abstract class VanillaChunkedSubLevelRenderDataMixin {

    @Shadow
    @Final
    private Vector3d origin;

    @Shadow
    @Final
    private ClientSubLevel subLevel;

    @Shadow
    @Final
    private ObjectList<SectionRenderDispatcher.RenderSection> allRenderSections;

    @Inject(method = "renderChunkedSubLevel", at = @At("HEAD"), cancellable = true)
    private void sablescale$renderScaled(final RenderType layer, final ShaderInstance shader, final Matrix4f modelView,
                                         final double camX, final double camY, final double camZ, final CallbackInfo ci) {
        final Pose3dc renderPose = this.subLevel.renderPose();
        final Vector3dc scale = renderPose.scale();
        if (scale.x() == 1.0 && scale.y() == 1.0 && scale.z() == 1.0)
            return; // unscaled: stock path is correct, don't touch it

        ci.cancel();
        final Quaterniondc renderRot = renderPose.orientation();
        final Vector3dc pos = renderPose.position();

        // -- verbatim from stock: black fog while the camera is inside this sub-level's water volume --
        float[] oldFogColor = null;
        if (shader.FOG_COLOR != null) {
            final WaterOcclusionContainer<?> container = WaterOcclusionContainer.getContainer(this.subLevel.getLevel());
            final Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            final WaterOcclusionRegion occludingRegion = container.getOccludingRegion(camera.getPosition());
            if (occludingRegion != null
                && Sable.HELPER.getContaining(this.subLevel.getLevel(), occludingRegion.getVolume().getMinBlockPos()) == this.subLevel) {
                oldFogColor = RenderSystem.getShaderFogColor();
                shader.FOG_COLOR.set(0.0F, 0.0F, 0.0F, 0.0F);
                shader.FOG_COLOR.upload();
            }
        }

        // -- verbatim from stock: the sub-level's sky-light scale --
        final Uniform sableSkyLightScale = shader.getUniform("SableSkyLightScale");
        if (sableSkyLightScale != null) {
            sableSkyLightScale.set((float) this.subLevel.getLatestSkyLightScale() / 15.0F);
            sableSkyLightScale.upload();
        }

        // modelview carries rotation + scale; translation rides per-section in CHUNK_OFFSET (pre-scale space)
        final Matrix4f transform = new Matrix4f()
            .rotate(new Quaternionf(renderRot))
            .scale((float) scale.x(), (float) scale.y(), (float) scale.z());
        if (shader.MODEL_VIEW_MATRIX != null) {
            shader.MODEL_VIEW_MATRIX.set(modelView.mul(transform, new Matrix4f()));
            shader.MODEL_VIEW_MATRIX.upload();
            if (IrisCompat.isLoaded())
                SableIrisCompat.refreshModelMatrices(shader);
        }

        // base = −p + S⁻¹·R⁻¹·(pos − cam); per section: chunkOffset = (sectionOrigin − origin) + base
        final Vector3d base = renderRot
            .transformInverse(new Vector3d(pos).sub(camX, camY, camZ), new Vector3d())
            .div(scale.x(), scale.y(), scale.z())
            .sub(renderPose.rotationPoint())
            .add(this.origin);

        final Uniform chunkOffsetUniform = shader.CHUNK_OFFSET;
        for (final SectionRenderDispatcher.RenderSection renderSection : this.allRenderSections) {
            if (!renderSection.getCompiled().isEmpty(layer)) {
                if (chunkOffsetUniform != null) {
                    final BlockPos sectionOrigin = renderSection.getOrigin();
                    chunkOffsetUniform.set(
                        (float) ((double) sectionOrigin.getX() - this.origin.x() + base.x),
                        (float) ((double) sectionOrigin.getY() - this.origin.y() + base.y),
                        (float) ((double) sectionOrigin.getZ() - this.origin.z() + base.z));
                    chunkOffsetUniform.upload();
                }

                final VertexBuffer buffer = renderSection.getBuffer(layer);
                buffer.bind();
                buffer.draw();
            }
        }

        if (chunkOffsetUniform != null)
            chunkOffsetUniform.set(0.0F, 0.0F, 0.0F);
        if (oldFogColor != null)
            shader.FOG_COLOR.set(oldFogColor[0], oldFogColor[1], oldFogColor[2], oldFogColor[3]);
    }
}
