package dev.sablescale.scale.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Stops a scaled sub-level from going <b>black under Sable's dynamic shading</b>.
 *
 * <p>Sable's dynamic directional shading re-derives each face's brightness from its <em>world</em> normal, so a
 * rotated vehicle shades correctly. {@code SableDynamicDirectionalShadingPreProcessor} injects this into every
 * {@code rendertype_*} chunk vertex shader:</p>
 *
 * <pre>{@code vertexColor.rgb *= mix(vec3(1.0), vec3(block_brightness(
 *     inverse(NormalMat) * (ModelViewMat * vec4(Normal, 0.0)).xyz)), SableEnableNormalLighting);}</pre>
 *
 * <p>The normal is pushed through <b>{@code ModelViewMat}</b> &mdash; and that is precisely the matrix
 * {@code VanillaChunkedSubLevelRenderDataMixin} bakes the pose <b>scale</b> into ({@code modelView · R · S}).
 * Normals must never be scaled. For a uniform scale {@code s} the factor pulls straight out of the whole
 * expression (a scalar commutes through both matrices), so the shader hands {@code block_brightness} a normal of
 * length {@code s} instead of 1 &mdash; whatever {@code NormalMat} happens to be.</p>
 *
 * <p>Veil's {@code block_brightness} does <b>not</b> normalise, and it is non-linear:</p>
 * <pre>{@code pow(clamp(±worldNormal.y, 0.0, 1.0), 3)   // up/down faces
 * pow(clamp(±worldNormal.z, 0.0, 1.0), 2)   // side faces}</pre>
 *
 * <p>so the shading multiplier lands at <b>{@code s³} on top/bottom faces and {@code s²} on sides</b>. Half scale
 * is already 0.125&times;/0.25&times; brightness; by quarter scale it is ~0.015&times; &mdash; the vehicle reads as
 * solid black. (Growing saturates instead: the {@code clamp} pins each term at 1.0.) Turning dynamic shading off
 * drops the multiply entirely, which is why that "fixes" it.</p>
 *
 * <p><b>Fix:</b> wrap the argument in {@code normalize(...)} as the preprocessor parses it, restoring the unit
 * normal {@code block_brightness} expects. sable-scale only ever applies a <em>uniform</em> scale, which preserves
 * the normal's direction, so length is the whole error and normalising is exact &mdash; at every scale, shrunk or
 * grown. It is a no-op on un-scaled geometry (the normal is already unit), so the world and un-scaled sub-levels
 * render exactly as before. The argument is located by matching parentheses rather than by string equality, so a
 * future Sable tweak to the normal expression keeps working, and an already-normalised argument is left alone.</p>
 *
 * <p>The vector cannot be zero (its factors &mdash; a unit face normal, an invertible {@code ModelViewMat} and an
 * invertible {@code NormalMat} &mdash; are the same ones today's shading already depends on), so
 * {@code normalize} cannot introduce a NaN here.</p>
 */
@Mixin(targets = "dev.ryanhcode.sable.render.dynamic_shade.SableDynamicDirectionalShadingPreProcessor", remap = false)
public abstract class SableDynamicShadingNormalScaleMixin {

    @Unique
    private static final String SABLESCALE$CALL = "block_brightness(";

    @Unique
    private static final String SABLESCALE$NORMALIZE = "normalize(";

    /**
     * Rewrites the one injected statement that shades by normal; the preprocessor's other
     * {@code parseExpression} calls are plain {@code uniform} declarations and pass through untouched.
     */
    @ModifyArg(
        method = "modify",
        at = @At(
            value = "INVOKE",
            target = "Lio/github/ocelot/glslprocessor/api/GlslParser;parseExpression(Ljava/lang/String;)"
                + "Lio/github/ocelot/glslprocessor/api/node/GlslNode;"),
        index = 0)
    private String sablescale$normalizeShadingNormal(final String expression) {
        final int call = expression.indexOf(SABLESCALE$CALL);
        if (call < 0)
            return expression;

        final int argStart = call + SABLESCALE$CALL.length();
        if (expression.startsWith(SABLESCALE$NORMALIZE, argStart))
            return expression; // already normalised (a newer Sable) - don't wrap it twice

        int depth = 1;
        int i = argStart;
        for (; i < expression.length() && depth > 0; i++) {
            final char c = expression.charAt(i);
            if (c == '(')
                depth++;
            else if (c == ')')
                depth--;
        }
        if (depth != 0)
            return expression; // unbalanced - leave Sable's source alone rather than emit broken GLSL

        final int argEnd = i - 1; // index of the ')' closing block_brightness(
        return expression.substring(0, argStart)
            + SABLESCALE$NORMALIZE + expression.substring(argStart, argEnd) + ')'
            + expression.substring(argEnd);
    }
}
