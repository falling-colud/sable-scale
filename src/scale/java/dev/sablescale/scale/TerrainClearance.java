package dev.sablescale.scale;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

/**
 * Keeps a resized sub-level out of the terrain. Scaling is centered on the rotation point (&asymp; centre of
 * mass), so growing a grounded vehicle expands the hull in <em>every</em> direction - including down into the
 * floor it rests on, which the contact solver answers with a violent ejection (or a wedge). Two remedies, both
 * driven by {@link SubLevelScale#apply}:
 *
 * <ul>
 *   <li>{@link #lowestHullY} lets the caller <b>anchor the hull's lowest world point</b> across the scale change
 *       (measure before, measure after, teleport the difference): a grounded vehicle grows upward/outward instead
 *       of into the ground, and a shrinking one stays seated instead of dropping from mid-air.</li>
 *   <li>{@link #liftOutOfTerrain} then scans the scaled hull's collision boxes against the real terrain and
 *       returns the extra upward lift needed to clear what the anchor could not (terrain steps and slope rising
 *       under the mid-hull). Overlaps deeper than {@link #PAIR_CAP} are treated as sideways wall/cliff
 *       penetrations and skipped - popping a ship on top of a cliff it grew into would be worse than letting the
 *       solver push it back out.</li>
 * </ul>
 *
 * <p>Hull boxes are tested via their world-space AABBs, which is conservative for rotated vehicles (a resized
 * tilted hull may get a sub-block courtesy hop; the solver settles it back). Terrain is queried through vanilla
 * {@code getBlockCollisions}, so fluids never count as ground; shapes inside another sub-level's plot chunks
 * (which live in the same {@code ServerLevel}, far away) are explicitly skipped in case a vehicle strays near the
 * plot region.</p>
 */
public final class TerrainClearance {

    /** Per-overlap lift cap: anything needing more is a sideways wall overlap, not resolvable by lifting. */
    private static final double PAIR_CAP = 8.0;
    /** Cleared hulls get this much extra daylight so the solver doesn't start inside a contact. */
    private static final double EPSILON = 1.0E-3;
    /** Repeated scans catch terrain that only overlaps at the lifted position (overhangs); usually 1 pass. */
    private static final int MAX_PASSES = 4;
    /** Same budget as the collider resampler: skip the scan for absurd vehicle × scale combinations. */
    private static final long MAX_SCAN_CELLS = 2_000_000L;

    private TerrainClearance() {}

    /**
     * World Y of the lowest corner of the sub-level's scaled block bounds ({@code pos + R·S·(corner − rotPoint)}
     * minimised over the 8 corners), or {@code NaN} for an empty plot. Exact for unrotated vehicles, a lower
     * bound otherwise - consistent across two calls at different scales, which is all the anchor needs.
     */
    public static double lowestHullY(final ServerSubLevel subLevel) {
        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        if (bounds.maxX() < bounds.minX())
            return Double.NaN; // EMPTY sentinel (inverted min/max)
        final Pose3dc pose = subLevel.logicalPose();
        final Vector3dc rp = pose.rotationPoint(), s = pose.scale();
        final Vector3d corner = new Vector3d();
        double lowest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 8; i++) {
            corner.set(
                (((i & 1) == 0 ? bounds.minX() : bounds.maxX() + 1) - rp.x()) * s.x(),
                (((i & 2) == 0 ? bounds.minY() : bounds.maxY() + 1) - rp.y()) * s.y(),
                (((i & 4) == 0 ? bounds.minZ() : bounds.maxZ() + 1) - rp.z()) * s.z());
            pose.orientation().transform(corner);
            lowest = Math.min(lowest, corner.y + pose.position().y());
        }
        return lowest;
    }

    /**
     * Extra upward lift (&ge; 0) that frees the scaled hull from terrain penetration when the body is displaced
     * by {@code (0, baseOffset, 0)} from its current pose - i.e. call it with the pending anchor delta and add
     * the result to it before teleporting.
     */
    public static double liftOutOfTerrain(final ServerSubLevel subLevel, final double baseOffset) {
        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        if (bounds.maxX() < bounds.minX())
            return 0.0;
        final Vector3dc s = subLevel.logicalPose().scale();
        final long sourceCells = (long) (bounds.maxX() - bounds.minX() + 1) * (bounds.maxY() - bounds.minY() + 1) * (bounds.maxZ() - bounds.minZ() + 1);
        if (sourceCells * Math.max(1.0, s.x() * s.y() * s.z()) > MAX_SCAN_CELLS)
            return 0.0;

        double total = 0.0;
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            final double lift = scanOnce(subLevel, baseOffset + total);
            if (lift <= 0.0)
                break;
            total += lift;
        }
        return total;
    }

    /** One scan of every hull collision box vs terrain; returns the largest liftable penetration (0 when clear). */
    private static double scanOnce(final ServerSubLevel subLevel, final double yOffset) {
        final ServerLevel level = subLevel.getLevel();
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        final Pose3dc pose = subLevel.logicalPose();
        final Vector3dc rp = pose.rotationPoint(), s = pose.scale(), p = pose.position();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        final Vector3d corner = new Vector3d();
        double needed = 0.0;

        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                    cursor.set(x, y, z);
                    final var blockState = level.getBlockState(cursor);
                    if (blockState.isAir())
                        continue;
                    final VoxelShape shape = blockState.getCollisionShape(level, cursor);
                    if (shape.isEmpty())
                        continue;
                    for (final AABB box : shape.toAabbs()) {
                        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
                        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
                        for (int i = 0; i < 8; i++) {
                            corner.set(
                                (x + ((i & 1) == 0 ? box.minX : box.maxX) - rp.x()) * s.x(),
                                (y + ((i & 2) == 0 ? box.minY : box.maxY) - rp.y()) * s.y(),
                                (z + ((i & 4) == 0 ? box.minZ : box.maxZ) - rp.z()) * s.z());
                            pose.orientation().transform(corner);
                            minX = Math.min(minX, corner.x + p.x());
                            minY = Math.min(minY, corner.y + p.y());
                            minZ = Math.min(minZ, corner.z + p.z());
                            maxX = Math.max(maxX, corner.x + p.x());
                            maxY = Math.max(maxY, corner.y + p.y());
                            maxZ = Math.max(maxZ, corner.z + p.z());
                        }
                        final AABB worldBox = new AABB(minX, minY + yOffset, minZ, maxX, maxY + yOffset, maxZ);
                        for (final VoxelShape terrain : level.getBlockCollisions(null, worldBox)) {
                            final AABB terrainBox = terrain.bounds();
                            // Not terrain: another sub-level's plot blocks (same level, far-off plot region).
                            if (container != null && container.getPlot(
                                    SectionPos.blockToSectionCoord((int) Math.floor(terrainBox.minX)),
                                    SectionPos.blockToSectionCoord((int) Math.floor(terrainBox.minZ))) != null)
                                continue;
                            final double lift = terrainBox.maxY - worldBox.minY;
                            if (lift > 0.0 && lift <= PAIR_CAP)
                                needed = Math.max(needed, lift);
                        }
                    }
                }
            }
        }
        return needed > 0.0 ? needed + EPSILON : 0.0;
    }
}
