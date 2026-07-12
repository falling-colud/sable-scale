package dev.sablescale.scale;

import net.minecraft.server.level.ServerLevel;

/** Implemented onto {@code RapierPhysicsPipeline} by {@code RapierPhysicsPipelineMixin}. */
public interface ScaledColliderPipeline {

    ServerLevel sablescale$level();

    long sablescale$sceneHandle();
}
