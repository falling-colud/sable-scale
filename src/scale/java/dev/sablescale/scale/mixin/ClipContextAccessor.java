package dev.sablescale.scale.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Read access to {@link ClipContext}'s private modes, needed to rebuild projected contexts in the clip overwrite. */
@Mixin(ClipContext.class)
public interface ClipContextAccessor {

    @Accessor("block")
    ClipContext.Block sablescale$getBlock();

    @Accessor("fluid")
    ClipContext.Fluid sablescale$getFluid();

    @Accessor("collisionContext")
    CollisionContext sablescale$getCollisionContext();
}
