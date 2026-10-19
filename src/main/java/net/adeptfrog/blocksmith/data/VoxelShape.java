package net.adeptfrog.blocksmith.data;

/**
 * The shape of one voxel inside its cell (no Minecraft dependencies, shared by the item renderer and the editor).
 *
 * <p>A cell is a unit cube: x and y across the face, z through the layer (0 = back, 1 = front). Every shape is a
 * union of the eight half-size octants of the cell, so all of them share one geometry builder and the flip / turn /
 * mirror operations are plain permutations of those octants.
 * <ul>
 *   <li>{@link #FULL}: the whole cell.</li>
 *   <li>Half block ({@link #HALF_LEFT}, {@link #HALF_RIGHT}): half of the cell across x, full height and depth.</li>
 *   <li>Horizontal slab ({@link #SLAB_BOTTOM}, {@link #SLAB_TOP}): half of the cell across y.</li>
 *   <li>Vertical slab ({@link #SLAB_FRONT}, {@link #SLAB_BACK}): half of the cell through the layer (z).</li>
 *   <li>Stair ({@code STAIR_*}): an L profile in the face plane, full depth, named after the quarter that is cut away.</li>
 * </ul>
 * Ids 5 and 6 belonged to a shape that no longer exists; they are invalid and load as {@link #FULL}.
 */
public final class VoxelShape {
    private VoxelShape() {}

    public static final int FULL = 0, SLAB_BOTTOM = 1, SLAB_TOP = 2, HALF_LEFT = 3, HALF_RIGHT = 4,
            SLAB_FRONT = 7, SLAB_BACK = 8, STAIR_TR = 9, STAIR_TL = 10, STAIR_BL = 11, STAIR_BR = 12;
    public static final int COUNT = 13;

    /** Bits of the "hide" mask: faces that touch a neighbour which covers them and need not be drawn. */
    public static final int HIDE_BACK = 1, HIDE_FRONT = 2;

    /** Floats per quad in {@link #buildQuads}: four vertices (x, y, z) and the face normal. */
    public static final int QUAD_FLOATS = 15;
    /** Most quads one voxel can produce. */
    public static final int MAX_QUADS = 24;

    /** Octant bit of (x, y, z) with each coordinate 0 or 1: x + 2y + 4z. */
    private static int bit(int x, int y, int z) {
        return 1 << (x + 2 * y + 4 * z);
    }

    private static final int[] MASKS = new int[COUNT];
    static {
        for (int shape = 0; shape < COUNT; shape++) {
            int m = 0;
            for (int z = 0; z < 2; z++) {
                for (int y = 0; y < 2; y++) {
                    for (int x = 0; x < 2; x++) {
                        if (contains(shape, x, y, z)) m |= bit(x, y, z);
                    }
                }
            }
            MASKS[shape] = m;
        }
    }

    private static boolean contains(int shape, int x, int y, int z) {
        return switch (shape) {
            case SLAB_BOTTOM -> y == 0;
            case SLAB_TOP -> y == 1;
            case HALF_LEFT -> x == 0;
            case HALF_RIGHT -> x == 1;
            case SLAB_BACK -> z == 0;
            case SLAB_FRONT -> z == 1;
            case STAIR_TR -> !(x == 1 && y == 1);
            case STAIR_TL -> !(x == 0 && y == 1);
            case STAIR_BL -> !(x == 0 && y == 0);
            case STAIR_BR -> !(x == 1 && y == 0);
            default -> shape == FULL;
        };
    }

    public static boolean isValid(int shape) {
        return shape >= 0 && shape < COUNT && shape != 5 && shape != 6;
    }

    public static boolean isStair(int shape) {
        return shape >= STAIR_TR && shape <= STAIR_BR;
    }

    /** Whether the shape leaves part of the face outline empty (so the 2D editor must draw it piece by piece). */
    public static boolean isPartialInPlane(int shape) {
        return xyMask(shape) != 15;
    }

    /** The filled octants of the shape (bit x + 2y + 4z, z = 1 towards the front). */
    public static int octants(int shape) {
        return MASKS[isValid(shape) ? shape : FULL];
    }

    /** Which quarters of the face are filled (bit x + 2y), seen from the front. */
    public static int xyMask(int shape) {
        int m = MASKS[isValid(shape) ? shape : FULL];
        return (m | (m >> 4)) & 15;
    }

    /** The shape made of exactly these octants, or {@link #FULL} if none is. */
    private static int fromMask(int mask) {
        for (int s = 0; s < COUNT; s++) if (isValid(s) && MASKS[s] == mask) return s;
        return FULL;
    }

    /** Octants of the face at the back (z = 0) or front (z = 1) as a 2x2 mask (bit x + 2y). */
    private static int layerMask(int shape, int z) {
        return (MASKS[isValid(shape) ? shape : FULL] >> (4 * z)) & 15;
    }

    /** Whether {@code neighbour}, directly behind {@code self}, hides self's back face. */
    public static boolean coversBack(int neighbour, int self) {
        return (layerMask(self, 0) & ~layerMask(neighbour, 1)) == 0;
    }

    /** Whether {@code neighbour}, directly in front of {@code self}, hides self's front face. */
    public static boolean coversFront(int neighbour, int self) {
        return (layerMask(self, 1) & ~layerMask(neighbour, 0)) == 0;
    }

    // ----- orientation changes (keep shapes on the right side when a design is flipped, turned or mirrored) -----

    private interface Map3 {
        int[] apply(int x, int y, int z);
    }

    private static int transform(int shape, Map3 f) {
        int m = MASKS[isValid(shape) ? shape : FULL], out = 0;
        for (int z = 0; z < 2; z++) {
            for (int y = 0; y < 2; y++) {
                for (int x = 0; x < 2; x++) {
                    if ((m & bit(x, y, z)) == 0) continue;
                    int[] p = f.apply(x, y, z);
                    out |= bit(p[0], p[1], p[2]);
                }
            }
        }
        return fromMask(out);
    }

    public static int flipX(int shape) {
        return transform(shape, (x, y, z) -> new int[]{1 - x, y, z});
    }

    public static int flipY(int shape) {
        return transform(shape, (x, y, z) -> new int[]{x, 1 - y, z});
    }

    /** Quarter turn clockwise in the face plane (matches the editor: new x = y, new y = n - x). */
    public static int rotateCw(int shape) {
        return transform(shape, (x, y, z) -> new int[]{y, 1 - x, z});
    }

    public static int rotateCcw(int shape) {
        return transform(shape, (x, y, z) -> new int[]{1 - y, x, z});
    }

    /** Shape of the mirror image under an editor mirror mode (1 horizontal, 2 vertical, 3 diagonal /, 4 diagonal \, 5 front-back). */
    public static int mirror(int shape, int mode) {
        return switch (mode) {
            case 1 -> flipX(shape);
            case 2 -> flipY(shape);
            case 3 -> transform(shape, (x, y, z) -> new int[]{y, x, z});
            case 4 -> transform(shape, (x, y, z) -> new int[]{1 - y, 1 - x, z});
            case 5 -> transform(shape, (x, y, z) -> new int[]{x, y, 1 - z});
            default -> shape;
        };
    }

    // ----- geometry -----

    /**
     * Builds the outward-facing quads of a voxel in cell space. Each quad is written as 15 floats: four vertices
     * (counter-clockwise seen from outside) followed by its unit normal. Coplanar neighbouring faces are merged.
     *
     * @param hideMask faces not to produce ({@code HIDE_*})
     * @param out room for {@link #MAX_QUADS} quads
     * @return number of quads written
     */
    public static int buildQuads(int shape, int hideMask, float[] out) {
        int mask = MASKS[isValid(shape) ? shape : FULL];
        int n = 0;
        for (int axis = 0; axis < 3; axis++) {
            for (int dir = -1; dir <= 1; dir += 2) {
                for (int plane = 0; plane <= 2; plane++) {
                    if (axis == 2 && dir < 0 && plane == 0 && (hideMask & HIDE_BACK) != 0) continue;
                    if (axis == 2 && dir > 0 && plane == 2 && (hideMask & HIDE_FRONT) != 0) continue;
                    // The octant on the inside of this plane must be filled and the one on the outside empty
                    int inside = dir > 0 ? plane - 1 : plane, outside = dir > 0 ? plane : plane - 1;
                    boolean[] face = new boolean[4];
                    boolean any = false;
                    for (int v = 0; v < 2; v++) {
                        for (int u = 0; u < 2; u++) {
                            boolean in = inside >= 0 && inside <= 1 && (mask & octant(axis, inside, u, v)) != 0;
                            boolean out2 = outside >= 0 && outside <= 1 && (mask & octant(axis, outside, u, v)) != 0;
                            face[u + 2 * v] = in && !out2;
                            any |= face[u + 2 * v];
                        }
                    }
                    if (!any) continue;
                    boolean[] used = new boolean[4];
                    for (int v0 = 0; v0 < 2; v0++) {
                        for (int u0 = 0; u0 < 2; u0++) {
                            if (!face[u0 + 2 * v0] || used[u0 + 2 * v0]) continue;
                            int u1 = u0 + 1;
                            while (u1 < 2 && face[u1 + 2 * v0] && !used[u1 + 2 * v0]) u1++;
                            int v1 = v0 + 1;
                            while (v1 < 2 && rowFree(face, used, u0, u1, v1)) v1++;
                            for (int v = v0; v < v1; v++) for (int u = u0; u < u1; u++) used[u + 2 * v] = true;
                            n = emit(out, n, axis, dir, plane, u0, v0, u1, v1);
                        }
                    }
                }
            }
        }
        return n;
    }

    private static boolean rowFree(boolean[] face, boolean[] used, int u0, int u1, int v) {
        for (int u = u0; u < u1; u++) if (!face[u + 2 * v] || used[u + 2 * v]) return false;
        return true;
    }

    /** Octant bit for the cell at layer {@code a} along {@code axis} and (u, v) along the next two axes. */
    private static int octant(int axis, int a, int u, int v) {
        int[] c = new int[3];
        c[axis] = a;
        c[(axis + 1) % 3] = u;
        c[(axis + 2) % 3] = v;
        return bit(c[0], c[1], c[2]);
    }

    private static int emit(float[] out, int n, int axis, int dir, int plane, int u0, int v0, int u1, int v1) {
        float[][] p = new float[4][3];
        int[][] uv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
        for (int i = 0; i < 4; i++) {
            p[i][axis] = plane * 0.5f;
            p[i][(axis + 1) % 3] = uv[i][0] * 0.5f;
            p[i][(axis + 2) % 3] = uv[i][1] * 0.5f;
        }
        float ox = axis == 0 ? dir : 0, oy = axis == 1 ? dir : 0, oz = axis == 2 ? dir : 0;
        return quad(out, n, ox, oy, oz, p[0][0], p[0][1], p[0][2], p[1][0], p[1][1], p[1][2],
                p[2][0], p[2][1], p[2][2], p[3][0], p[3][1], p[3][2]);
    }

    /** Appends a quad whose vertices are reordered if needed so it faces {@code (ox, oy, oz)}; skips empty ones. */
    private static int quad(float[] out, int n, float ox, float oy, float oz,
                            float ax, float ay, float az, float bx, float by, float bz,
                            float cx, float cy, float cz, float dx, float dy, float dz) {
        float ux = cx - ax, uy = cy - ay, uz = cz - az;
        float vx = dx - bx, vy = dy - by, vz = dz - bz;
        float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-5f) return n;
        nx /= len; ny /= len; nz /= len;
        boolean flip = nx * ox + ny * oy + nz * oz < 0;
        int o = n * QUAD_FLOATS;
        float[] v = {ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz};
        for (int i = 0; i < 4; i++) {
            int src = flip ? (4 - i) % 4 : i;
            out[o + i * 3] = v[src * 3];
            out[o + i * 3 + 1] = v[src * 3 + 1];
            out[o + i * 3 + 2] = v[src * 3 + 2];
        }
        if (flip) { nx = -nx; ny = -ny; nz = -nz; }
        out[o + 12] = nx; out[o + 13] = ny; out[o + 14] = nz;
        return n + 1;
    }
}
