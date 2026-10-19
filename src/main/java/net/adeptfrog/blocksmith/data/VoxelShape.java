package net.adeptfrog.blocksmith.data;

/**
 * The shape of one voxel inside its cell (no Minecraft dependencies, shared by the item renderer and the editor).
 *
 * <p>A cell is a unit cube: x and y across the face, z through the layer (0 = back, 1 = front).
 * <ul>
 *   <li>{@link #FULL}: the whole cell.</li>
 *   <li>Slabs: half of the cell, split along x or y. The editor places {@link #SLAB_BOTTOM} (horizontal slab) and
 *       {@link #SLAB_LEFT} (vertical slab); flipping and rotating a design moves them to the other halves.</li>
 *   <li>Tapered: a full cell whose front and back faces slope down to a knife edge on every side that has no
 *       neighbouring voxel, so blades get a ground edge. A tapered voxel completely surrounded by neighbours is
 *       just a full cube. {@link #TAPERED_FULL} fades across the whole block, {@link #TAPERED_HALF} only across the
 *       outer half of it and keeps the inner half at full thickness.</li>
 * </ul>
 */
public final class VoxelShape {
    private VoxelShape() {}

    public static final int FULL = 0, SLAB_BOTTOM = 1, SLAB_TOP = 2, SLAB_LEFT = 3, SLAB_RIGHT = 4,
            TAPERED_FULL = 5, TAPERED_HALF = 6;
    public static final int COUNT = 7;

    /** Bits of the "free" mask: which neighbouring cells hold no voxel (-x, +x, -y, +y, back -z, front +z). */
    public static final int FREE_XM = 1, FREE_XP = 2, FREE_YM = 4, FREE_YP = 8, FREE_ZM = 16, FREE_ZP = 32;
    /** Bits of the "hide" mask: faces that touch an equally large neighbour and need not be drawn. */
    public static final int HIDE_BACK = 1, HIDE_FRONT = 2;

    /** Floats per quad in {@link #buildQuads}: four vertices (x, y, z) and the face normal. */
    public static final int QUAD_FLOATS = 15;
    /** Most quads one voxel can produce (a half-fade tapered voxel: 2x2 front and back patches plus 8 side strips). */
    public static final int MAX_QUADS = 16;

    public static boolean isValid(int shape) {
        return shape >= 0 && shape < COUNT;
    }

    public static boolean isTapered(int shape) {
        return shape == TAPERED_FULL || shape == TAPERED_HALF;
    }

    public static boolean isSlab(int shape) {
        return shape >= SLAB_BOTTOM && shape <= SLAB_RIGHT;
    }

    /** Footprint of the shape across the cell as {x0, y0, x1, y1}. */
    public static float[] footprint(int shape) {
        return switch (shape) {
            case SLAB_BOTTOM -> new float[]{0, 0, 1, 0.5f};
            case SLAB_TOP -> new float[]{0, 0.5f, 1, 1};
            case SLAB_LEFT -> new float[]{0, 0, 0.5f, 1};
            case SLAB_RIGHT -> new float[]{0.5f, 0, 1, 1};
            default -> new float[]{0, 0, 1, 1};
        };
    }

    /** Whether {@code neighbour}, sitting directly in front of or behind {@code self}, hides self's face towards it. */
    public static boolean covers(int neighbour, int self) {
        float[] n = footprint(neighbour), s = footprint(self);
        return n[0] <= s[0] && n[1] <= s[1] && n[2] >= s[2] && n[3] >= s[3];
    }

    // ----- orientation changes (keep slabs on the right half when a design is flipped, turned or mirrored) -----

    public static int flipX(int shape) {
        return switch (shape) {
            case SLAB_LEFT -> SLAB_RIGHT;
            case SLAB_RIGHT -> SLAB_LEFT;
            default -> shape;
        };
    }

    public static int flipY(int shape) {
        return switch (shape) {
            case SLAB_BOTTOM -> SLAB_TOP;
            case SLAB_TOP -> SLAB_BOTTOM;
            default -> shape;
        };
    }

    /** Quarter turn clockwise: top goes to the right, right to the bottom, bottom to the left, left to the top. */
    public static int rotateCw(int shape) {
        return switch (shape) {
            case SLAB_TOP -> SLAB_RIGHT;
            case SLAB_RIGHT -> SLAB_BOTTOM;
            case SLAB_BOTTOM -> SLAB_LEFT;
            case SLAB_LEFT -> SLAB_TOP;
            default -> shape;
        };
    }

    public static int rotateCcw(int shape) {
        return rotateCw(rotateCw(rotateCw(shape)));
    }

    /** Shape of the mirror image under an editor mirror mode (1 horizontal, 2 vertical, 3 diagonal /, 4 diagonal \). */
    public static int mirror(int shape, int mode) {
        return switch (mode) {
            case 1 -> flipX(shape);
            case 2 -> flipY(shape);
            case 3 -> switch (shape) { // swap x and y
                case SLAB_BOTTOM -> SLAB_LEFT;
                case SLAB_LEFT -> SLAB_BOTTOM;
                case SLAB_TOP -> SLAB_RIGHT;
                case SLAB_RIGHT -> SLAB_TOP;
                default -> shape;
            };
            case 4 -> switch (shape) { // (x, y) -> (n - y, n - x)
                case SLAB_BOTTOM -> SLAB_RIGHT;
                case SLAB_RIGHT -> SLAB_BOTTOM;
                case SLAB_TOP -> SLAB_LEFT;
                case SLAB_LEFT -> SLAB_TOP;
                default -> shape;
            };
            default -> shape;
        };
    }

    // ----- geometry -----

    /**
     * Builds the outward-facing quads of a voxel in cell space. Each quad is written as 15 floats: four vertices
     * (counter-clockwise seen from outside) followed by its unit normal. Degenerate faces (the knife edge of a
     * tapered voxel) are left out.
     *
     * @param freeMask which neighbours are empty ({@code FREE_*}); only a tapered voxel looks at it
     * @param hideMask faces not to produce ({@code HIDE_*})
     * @param out room for {@link #MAX_QUADS} quads
     * @return number of quads written
     */
    public static int buildQuads(int shape, int freeMask, int hideMask, float[] out) {
        float[] fp = footprint(shape);
        float x0 = fp[0], y0 = fp[1], x1 = fp[2], y1 = fp[3];

        // The front and back surfaces are height fields over an (m+1) x (m+1) grid of vertices across the footprint.
        // Vertices on an edge with no neighbour pull in to the mid plane (knife edge); a half-fade voxel adds a
        // middle line of vertices that stay at full thickness, so the slope only covers the outer half.
        int m = shape == TAPERED_HALF ? 2 : 1;
        int w = m + 1;
        float[] zf = new float[w * w], zb = new float[w * w];
        boolean tapered = isTapered(shape);
        boolean frontFree = (freeMask & FREE_ZP) != 0, backFree = (freeMask & FREE_ZM) != 0;
        for (int j = 0; j < w; j++) {
            for (int i = 0; i < w; i++) {
                int k = i + j * w;
                zf[k] = 1;
                zb[k] = 0;
                if (!tapered) continue;
                boolean sharp = (i == 0 && (freeMask & FREE_XM) != 0) || (i == m && (freeMask & FREE_XP) != 0)
                        || (j == 0 && (freeMask & FREE_YM) != 0) || (j == m && (freeMask & FREE_YP) != 0);
                if (sharp) {
                    if (frontFree) zf[k] = 0.5f;
                    if (backFree) zb[k] = 0.5f;
                }
            }
        }
        float[] xs = new float[w], ys = new float[w];
        for (int i = 0; i < w; i++) {
            xs[i] = x0 + (x1 - x0) * i / m;
            ys[i] = y0 + (y1 - y0) * i / m;
        }

        int n = 0;
        for (int j = 0; j < m; j++) {
            for (int i = 0; i < m; i++) {
                int a = i + j * w, b = a + 1, c = a + 1 + w, d = a + w; // (i,j) (i+1,j) (i+1,j+1) (i,j+1)
                if ((hideMask & HIDE_FRONT) == 0) {
                    n = quad(out, n, 0, 0, 1,
                            xs[i], ys[j], zf[a], xs[i + 1], ys[j], zf[b], xs[i + 1], ys[j + 1], zf[c], xs[i], ys[j + 1], zf[d]);
                }
                if ((hideMask & HIDE_BACK) == 0) {
                    n = quad(out, n, 0, 0, -1,
                            xs[i], ys[j], zb[a], xs[i], ys[j + 1], zb[d], xs[i + 1], ys[j + 1], zb[c], xs[i + 1], ys[j], zb[b]);
                }
            }
        }
        // Sides, one strip per grid segment
        for (int s = 0; s < m; s++) {
            int lo = s, hi = s + 1;
            // -X (i = 0) and +X (i = m): segments run along y
            int a = 0 + lo * w, b = 0 + hi * w;
            n = quad(out, n, -1, 0, 0, x0, ys[lo], zb[a], x0, ys[lo], zf[a], x0, ys[hi], zf[b], x0, ys[hi], zb[b]);
            a = m + lo * w; b = m + hi * w;
            n = quad(out, n, 1, 0, 0, x1, ys[lo], zb[a], x1, ys[hi], zb[b], x1, ys[hi], zf[b], x1, ys[lo], zf[a]);
            // -Y (j = 0) and +Y (j = m): segments run along x
            a = lo; b = hi;
            n = quad(out, n, 0, -1, 0, xs[lo], y0, zb[a], xs[hi], y0, zb[b], xs[hi], y0, zf[b], xs[lo], y0, zf[a]);
            a = lo + m * w; b = hi + m * w;
            n = quad(out, n, 0, 1, 0, xs[lo], y1, zb[a], xs[lo], y1, zf[a], xs[hi], y1, zf[b], xs[hi], y1, zb[b]);
        }
        return n;
    }

    /** Appends a quad whose vertices are reordered if needed so it faces {@code (ox, oy, oz)}; skips empty ones. */
    private static int quad(float[] out, int n, float ox, float oy, float oz,
                            float ax, float ay, float az, float bx, float by, float bz,
                            float cx, float cy, float cz, float dx, float dy, float dz) {
        // Normal from the diagonals, which stays meaningful when the four corners are not coplanar
        float ux = cx - ax, uy = cy - ay, uz = cz - az;
        float vx = dx - bx, vy = dy - by, vz = dz - bz;
        float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-5f) return n;
        nx /= len; ny /= len; nz /= len;
        // The diagonals' cross product points the way the vertices wind; flip the vertex order when it faces inwards
        boolean flip = nx * ox + ny * oy + nz * oz < 0;
        int o = n * QUAD_FLOATS;
        float[] v = {ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz};
        for (int i = 0; i < 4; i++) {
            int src = flip ? (4 - i) % 4 : i; // a, d, c, b when reversed
            out[o + i * 3] = v[src * 3];
            out[o + i * 3 + 1] = v[src * 3 + 1];
            out[o + i * 3 + 2] = v[src * 3 + 2];
        }
        if (flip) { nx = -nx; ny = -ny; nz = -nz; }
        out[o + 12] = nx; out[o + 13] = ny; out[o + 14] = nz;
        return n + 1;
    }
}
