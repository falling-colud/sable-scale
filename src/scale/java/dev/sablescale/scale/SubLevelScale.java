package dev.sablescale.scale;

import org.joml.Vector3d;
import org.joml.Vector3dc;

import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

/** Applies a scale to a server sub-level and makes sure it actually reaches clients and disk. */
public final class SubLevelScale {

    /** Bounds for the command argument; also what "sane" means for a sub-level scale. */
    public static final double MIN_SCALE = 0.01;
    public static final double MAX_SCALE = 100.0;

    private SubLevelScale() {}

    /**
     * Sets a uniform scale on the sub-level's logical pose. The scale is centered on the pose's rotation point
     * (see {@code Pose3dc.bakeIntoMatrix}: translate &times; rotate &times; <b>scale</b> &times; -rotationPoint),
     * i.e. the sub-level grows/shrinks around its center of mass rather than drifting &mdash; and the body is
     * then re-seated vertically so the resize never buries it in the terrain (see {@link TerrainClearance}).
     *
     * <p>Physics keeps the scale intact: {@code SubLevelPhysicsSystem}'s per-tick pose writeback only touches
     * position and orientation. Persistence and networking are covered by this mod's two mixins.</p>
     */
    public static void apply(final ServerSubLevel subLevel, final double scale) {
        // Measure the hull bottom BEFORE the change: scaling is centered on the rotation point (≈ CoM), so a
        // grounded vehicle would otherwise grow straight into the floor (solver ejection / wedging) or shrink
        // into a mid-air drop. Anchoring the lowest point keeps it seated - and because position and scale lerp
        // linearly in the same client snapshot, bottom(t) = pos_y(t) + s(t)·m stays constant through the whole
        // grow animation, not just at its endpoints (Δpos cancels Δscale·m exactly, like the CoM compensation).
        final double bottomBefore = TerrainClearance.lowestHullY(subLevel);

        subLevel.logicalPose().scale().set(scale, scale, scale);

        double offset = 0.0;
        if (!Double.isNaN(bottomBefore)) {
            offset = bottomBefore - TerrainClearance.lowestHullY(subLevel);
            // The anchor holds the lowest point, but terrain rising under the mid-hull (slopes, ledges) can
            // still poke into the grown hull - lift out of whatever remains (sideways cliff overlaps excluded;
            // see TerrainClearance.PAIR_CAP).
            offset += TerrainClearance.liftOutOfTerrain(subLevel, offset);
        }

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(subLevel.getLevel());
        final PhysicsPipeline pipeline = container != null && !subLevel.isRemoved()
            ? container.physicsSystem().getPipeline() : null;
        if (pipeline != null && offset != 0.0)
            pipeline.teleport(subLevel,
                new Vector3d(subLevel.logicalPose().position()).add(0.0, offset, 0.0),
                subLevel.logicalPose().orientation());

        subLevel.updateBoundingBox();

        // Sable only networks a pose snapshot when the pose left a position/orientation tolerance window
        // (Pose3dc.withinTolerance ignores scale), so a scale-only change on a resting sub-level would never be
        // sent. Knocking the last-networked position far out of tolerance forces one fresh snapshot on the next
        // tracking tick - which carries the full pose, scale included, thanks to SableBufferUtilsMixin. The
        // tracking system immediately overwrites lastNetworkedPose with the real pose when it sends.
        subLevel.lastNetworkedPose().position().add(0.0, 1.0E7, 0.0);

        // Rebuild the rapier terrain collider on the scaled lattice (no-op on non-rapier pipelines).
        ScaledColliders.onScaleChanged(subLevel);

        // Nothing re-uploads mass properties on a scale-only change (MergedMassTracker only uploads when the
        // unscaled mass/CoM/inertia moved), so push a stats refresh ourselves: RapierPhysicsPipelineMixin scales
        // the uploaded mass ×(sx·sy·sz) and the inertia tensor to match (see ScaledMass), and refreshes the local
        // bounds. Then wake the body so a sleeping vehicle re-settles under its new weight.
        if (pipeline != null) {
            pipeline.onStatsChanged(subLevel);
            pipeline.wakeUp(subLevel);
        }
    }

    public static boolean isUniform(final Vector3dc scale) {
        return scale.x() == scale.y() && scale.y() == scale.z();
    }
}
