package net.adeptfrog.blocksmith.data;

import static net.adeptfrog.blocksmith.Blocksmith.MAX_VOXELS;

/**
 * Applies diminishing returns to the total voxel bonus stats.
 * <p>
 * Bonuses grow linearly with the voxel count until a single layer is full ({@code MAX_VOXELS()}), which is the
 * peak of the normal curve. Beyond that point every extra voxel is worth only a small fraction of a normal one,
 * and that fraction keeps shrinking, so extra layers still add a little but can never snowball:
 * <pre>
 *   effective(n) = n                                        , n <= M
 *   effective(n) = M + TAIL * M * ln(1 + (n - M) / M)       , n >  M
 * </pre>
 * The raw bonus sum is scaled by {@code effective(n) / n}, so the material mix is preserved and designs at or
 * below {@code MAX_VOXELS()} are completely unchanged.
 */
public final class StatScaling {
    /** Marginal value of the first voxel past the peak (1.0 = full value). Falls off from there. */
    public static final double TAIL = 0.1;

    private StatScaling() {}

    public static double effectiveCount(int voxelCount) {
        double peak = MAX_VOXELS();
        if (voxelCount <= peak) return voxelCount;
        return peak + TAIL * peak * Math.log(1.0 + (voxelCount - peak) / peak);
    }

    /** Scales a raw (summed) bonus from {@code voxelCount} voxels by the diminishing-returns curve. */
    public static double scale(double rawBonus, int voxelCount) {
        if (voxelCount <= 0) return rawBonus;
        return rawBonus * (effectiveCount(voxelCount) / voxelCount);
    }
}
