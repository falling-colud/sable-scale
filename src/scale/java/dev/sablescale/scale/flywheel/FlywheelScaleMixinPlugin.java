package dev.sablescale.scale.flywheel;

import dev.sablescale.core.ModIds;
import dev.sablescale.core.RequiredModsMixinPlugin;

/**
 * Gates the Flywheel sub-level-scale patch on both {@code sable} and {@code flywheel} being present, and - only
 * when they are - bootstraps MixinSquared so its {@code @MixinSquared:Handler} selector is registered before the
 * patch's one mixin ({@code SableFlywheelEmbeddingScaleMixin}) applies.
 *
 * <p>{@code MixinSquaredBootstrap.init()} is idempotent and is the library's documented manual entry point; we call
 * it here rather than relying on MixinSquared's own manifest bootstrap, which may never run depending on how the
 * library jar was located (JarJar'd game library in production vs. classpath entry in dev) - exactly the reasoning
 * the sibling Immersive Portals x Sable patch uses. With either mod missing, neither MixinSquared nor the mixin
 * touches the game, so each mod alone behaves as stock.</p>
 */
public final class FlywheelScaleMixinPlugin extends RequiredModsMixinPlugin {

    private static final String PATCH_NAME = "Flywheel sub-level scaling";

    public FlywheelScaleMixinPlugin() {
        super(PATCH_NAME, ModIds.SABLE, ModIds.FLYWHEEL);
    }

    @Override
    public void onLoad(final String mixinPackage) {
        super.onLoad(mixinPackage);
        if (allPresent(ModIds.SABLE, ModIds.FLYWHEEL)) {
            com.bawnorton.mixinsquared.MixinSquaredBootstrap.init();
            LOGGER.info("[Sable Scale] patch '{}' active - bootstrapped MixinSquared.", PATCH_NAME);
        }
    }
}
