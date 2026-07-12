package dev.sablescale.core;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.LoadingModList;
import org.slf4j.Logger;

/**
 * Base {@link IMixinConfigPlugin} that applies a config's mixins only when every mod the patch bridges is present.
 * Same pattern as the sibling "Make it Compatible" mods: the bridged mods are declared <em>optional</em> in
 * {@code neoforge.mods.toml}, and this plugin decides at mixin-apply time whether the mixins (which reference
 * classes that only exist when those mods are loaded) should apply at all.
 *
 * <p>We query {@link LoadingModList} rather than {@code ModList}: mixins are applied during early class loading,
 * before {@code ModList} is populated, but the discovered mod files are already known then.</p>
 */
public abstract class RequiredModsMixinPlugin implements IMixinConfigPlugin {

    protected static final Logger LOGGER = LogUtils.getLogger();

    private final String patchName;
    private final String[] requiredMods;
    private boolean active;

    protected RequiredModsMixinPlugin(final String patchName, final String... requiredMods) {
        this.patchName = patchName;
        this.requiredMods = requiredMods;
    }

    /** @return {@code true} if every required mod was discovered (i.e. this patch's mixins should apply). */
    public static boolean allPresent(final String... modIds) {
        final LoadingModList mods = LoadingModList.get();
        if (mods == null)
            return false;
        for (final String id : modIds)
            if (mods.getModFileById(id) == null)
                return false;
        return true;
    }

    @Override
    public void onLoad(final String mixinPackage) {
        this.active = allPresent(this.requiredMods);
        if (!this.active)
            LOGGER.info("[Sable Scale] patch '{}' dormant - missing one of {}; skipping its mixins.",
                this.patchName, String.join(", ", this.requiredMods));
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        return this.active;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName, final IMixinInfo mixinInfo) {}

    @Override
    public void postApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName, final IMixinInfo mixinInfo) {}
}
