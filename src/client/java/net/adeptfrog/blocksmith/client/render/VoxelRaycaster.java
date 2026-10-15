package net.adeptfrog.blocksmith.client.render;

/**
 * Minecraft-independent software ray-caster used by the forging screen's 3D editor.
 * <p>
 * The model lives in a grid of {@code gridSize x gridSize x layers} cells. Cell index {@code zi} is the layer
 * {@code z + layers/2}, so the base layer is the middle one. Cells hold ARGB colours (0 = empty).
 * <p>
 * The camera is orthographic and orbits the centre of the model. Rays walk the grid with a 3D DDA
 * (Amanatides &amp; Woo), which gives both the picture and, for the mouse, the exact voxel and face under a pixel.
 * Layers are physically {@link #LAYER_THICKNESS} cells thick, matching the in-game renderer.
 */
public final class VoxelRaycaster {
    /** Physical thickness of one layer relative to the width of one cell (matches the item renderer). */
    public static final double LAYER_THICKNESS = 1.25;

    /** A voxel face (or the empty base-layer floor) under a pixel. */
    public record Hit(int x, int y, int zi, int nx, int ny, int nz, boolean floor) {
        /** Cell a new voxel would be placed in when painting on this hit. */
        public int[] placeCell() {
            return floor ? new int[]{x, y, zi} : new int[]{x + nx, y + ny, zi + nz};
        }

        /** Axis (0=x, 1=y, 2=z) of the face normal. */
        public int normalAxis() {
            if (floor || nz != 0) return 2;
            return nx != 0 ? 0 : 1;
        }
    }

    private static final double BG_DISTANCE = 400.0;
    private static final int BACKGROUND = 0xFF141414;

    private final int gridSize;
    private final int layers;
    private final int width;
    private final int height;
    private final int[] cells;

    private double yaw;
    private double pitch;
    private double scale; // pixels per cell
    private final double defaultYaw = Math.toRadians(-35);
    private final double defaultPitch = Math.toRadians(25);
    private final double defaultScale;

    // Scratch values for the last trace
    private int hx, hy, hz, hnx, hny, hnz;
    private double hitT;

    public VoxelRaycaster(int gridSize, int layers, int width, int height) {
        this.gridSize = gridSize;
        this.layers = layers;
        this.width = width;
        this.height = height;
        this.cells = new int[gridSize * gridSize * layers];
        this.defaultScale = Math.min(width, height) / (gridSize * 1.45);
        resetView();
    }

    // ----- model -----

    public int gridSize() { return gridSize; }
    public int layers() { return layers; }

    public void clear() {
        java.util.Arrays.fill(cells, 0);
    }

    public boolean inBounds(int x, int y, int zi) {
        return x >= 0 && x < gridSize && y >= 0 && y < gridSize && zi >= 0 && zi < layers;
    }

    public void setCell(int x, int y, int zi, int argb) {
        if (inBounds(x, y, zi)) cells[index(x, y, zi)] = argb;
    }

    public int getCell(int x, int y, int zi) {
        return inBounds(x, y, zi) ? cells[index(x, y, zi)] : 0;
    }

    private int index(int x, int y, int zi) {
        return (zi * gridSize + y) * gridSize + x;
    }

    // ----- camera -----

    public void resetView() {
        yaw = defaultYaw;
        pitch = defaultPitch;
        scale = defaultScale;
    }

    public void orbit(double deltaYaw, double deltaPitch) {
        yaw += deltaYaw;
        pitch = Math.max(Math.toRadians(-89), Math.min(Math.toRadians(89), pitch + deltaPitch));
    }

    public void zoom(double factor) {
        scale = Math.max(defaultScale * 0.5, Math.min(defaultScale * 4.0, scale * factor));
    }

    // camera basis, recomputed per call
    private double rx, ry, rz, ux, uy, uz, fx, fy, fz;

    private void updateBasis() {
        double cp = Math.cos(pitch);
        double px = Math.sin(yaw) * cp, py = Math.sin(pitch), pz = Math.cos(yaw) * cp; // towards the camera
        fx = -px; fy = -py; fz = -pz;
        // r = normalize(cross(up, p)) with up = (0,1,0)  ->  (pz, 0, -px)
        double len = Math.sqrt(pz * pz + px * px);
        rx = pz / len; ry = 0; rz = -px / len;
        // u = cross(p, r)
        ux = py * rz - pz * ry;
        uy = pz * rx - px * rz;
        uz = px * ry - py * rx;
    }

    // ray for a pixel in grid space: origin (o*) and direction (d*, shared by all pixels)
    private double ox, oy, oz, dx, dy, dz;

    private void setRay(double pixelX, double pixelY) {
        double sx = (pixelX + 0.5 - width / 2.0) / scale;
        double sy = (height / 2.0 - (pixelY + 0.5)) / scale;
        double centreX = gridSize / 2.0, centreY = gridSize / 2.0, centreZ = layers / 2.0 * LAYER_THICKNESS;
        double wx = centreX + rx * sx + ux * sy - fx * BG_DISTANCE;
        double wy = centreY + ry * sx + uy * sy - fy * BG_DISTANCE;
        double wz = centreZ + rz * sx + uz * sy - fz * BG_DISTANCE;
        ox = wx; oy = wy; oz = wz / LAYER_THICKNESS;
        dx = fx; dy = fy; dz = fz / LAYER_THICKNESS;
    }

    // ----- tracing -----

    /** Walks the ray set by {@link #setRay}; on a hit fills the h* fields and returns true. */
    private boolean trace() {
        double tMin = Double.NEGATIVE_INFINITY, tMaxBox = Double.POSITIVE_INFINITY;
        int entryAxis = -1;
        double[] o = {ox, oy, oz};
        double[] d = {dx, dy, dz};
        double[] size = {gridSize, gridSize, layers};

        for (int a = 0; a < 3; a++) {
            if (Math.abs(d[a]) < 1e-12) {
                if (o[a] < 0 || o[a] > size[a]) return false;
                continue;
            }
            double t1 = (0 - o[a]) / d[a];
            double t2 = (size[a] - o[a]) / d[a];
            double tNear = Math.min(t1, t2), tFar = Math.max(t1, t2);
            if (tNear > tMin) { tMin = tNear; entryAxis = a; }
            tMaxBox = Math.min(tMaxBox, tFar);
        }
        if (tMin > tMaxBox || tMaxBox < 0) return false;
        if (tMin < 0) { tMin = 0; entryAxis = -1; }

        double t = tMin + 1e-7;
        int cx = clamp((int) Math.floor(o[0] + d[0] * t), gridSize);
        int cy = clamp((int) Math.floor(o[1] + d[1] * t), gridSize);
        int cz = clamp((int) Math.floor(o[2] + d[2] * t), layers);

        int nx = 0, ny = 0, nz = 0;
        if (entryAxis == 0) nx = d[0] > 0 ? -1 : 1;
        else if (entryAxis == 1) ny = d[1] > 0 ? -1 : 1;
        else if (entryAxis == 2) nz = d[2] > 0 ? -1 : 1;

        int stepX = d[0] > 0 ? 1 : -1, stepY = d[1] > 0 ? 1 : -1, stepZ = d[2] > 0 ? 1 : -1;
        double inf = Double.POSITIVE_INFINITY;
        double tDeltaX = Math.abs(d[0]) < 1e-12 ? inf : Math.abs(1.0 / d[0]);
        double tDeltaY = Math.abs(d[1]) < 1e-12 ? inf : Math.abs(1.0 / d[1]);
        double tDeltaZ = Math.abs(d[2]) < 1e-12 ? inf : Math.abs(1.0 / d[2]);
        double tMaxX = Math.abs(d[0]) < 1e-12 ? inf : ((stepX > 0 ? cx + 1 : cx) - o[0]) / d[0];
        double tMaxY = Math.abs(d[1]) < 1e-12 ? inf : ((stepY > 0 ? cy + 1 : cy) - o[1]) / d[1];
        double tMaxZ = Math.abs(d[2]) < 1e-12 ? inf : ((stepZ > 0 ? cz + 1 : cz) - o[2]) / d[2];

        double tCurrent = tMin;
        int limit = gridSize * 2 + layers + 4;
        for (int i = 0; i < limit; i++) {
            if (cells[index(cx, cy, cz)] != 0) {
                hx = cx; hy = cy; hz = cz; hnx = nx; hny = ny; hnz = nz; hitT = tCurrent;
                return true;
            }
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                cx += stepX; tCurrent = tMaxX; tMaxX += tDeltaX; nx = -stepX; ny = 0; nz = 0;
                if (cx < 0 || cx >= gridSize) return false;
            } else if (tMaxY <= tMaxZ) {
                cy += stepY; tCurrent = tMaxY; tMaxY += tDeltaY; nx = 0; ny = -stepY; nz = 0;
                if (cy < 0 || cy >= gridSize) return false;
            } else {
                cz += stepZ; tCurrent = tMaxZ; tMaxZ += tDeltaZ; nx = 0; ny = 0; nz = -stepZ;
                if (cz < 0 || cz >= layers) return false;
            }
        }
        return false;
    }

    private static int clamp(int v, int n) {
        return Math.max(0, Math.min(n - 1, v));
    }

    /** Where the current ray crosses the middle of the base layer, or null if it misses the base layer footprint. */
    private int[] floorCell() {
        if (Math.abs(dz) < 1e-12) return null;
        double planeZ = layers / 2 + 0.5;
        double t = (planeZ - oz) / dz;
        if (t < 0) return null;
        int x = (int) Math.floor(ox + dx * t);
        int y = (int) Math.floor(oy + dy * t);
        if (x < 0 || x >= gridSize || y < 0 || y >= gridSize) return null;
        return new int[]{x, y, layers / 2};
    }

    // ----- picking -----

    /** The voxel face (or empty base-layer cell) under a pixel, or null if the pixel shows nothing editable. */
    public Hit pick(int pixelX, int pixelY) {
        if (pixelX < 0 || pixelY < 0 || pixelX >= width || pixelY >= height) return null;
        updateBasis();
        setRay(pixelX, pixelY);
        if (trace()) return new Hit(hx, hy, hz, hnx, hny, hnz, false);
        int[] f = floorCell();
        return f == null ? null : new Hit(f[0], f[1], f[2], 0, 0, 1, true);
    }

    /**
     * Cell where the ray under a pixel crosses the plane of cells {@code index} along {@code axis}.
     * Used to keep a paint stroke on one plane while dragging. Returns null when outside the grid.
     */
    public int[] pickOnPlane(int pixelX, int pixelY, int axis, int index) {
        updateBasis();
        setRay(pixelX, pixelY);
        double[] o = {ox, oy, oz};
        double[] d = {dx, dy, dz};
        if (Math.abs(d[axis]) < 1e-12) return null;
        double t = (index + 0.5 - o[axis]) / d[axis];
        int[] c = new int[3];
        for (int a = 0; a < 3; a++) {
            c[a] = a == axis ? index : (int) Math.floor(o[a] + d[a] * t);
        }
        return inBounds(c[0], c[1], c[2]) ? c : null;
    }

    // ----- rendering -----

    /** Renders the whole view as ARGB into {@code out} (length width*height). {@code highlight} may be null. */
    public void render(int[] out, Hit highlight) {
        updateBasis();
        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                setRay(px, py);
                int color;
                if (trace()) {
                    color = shadeHit(highlight);
                } else {
                    color = shadeFloor();
                }
                out[py * width + px] = color;
            }
        }
    }

    private int shadeHit(Hit highlight) {
        int base = cells[index(hx, hy, hz)];
        double light;
        if (hny > 0) light = 1.0;
        else if (hny < 0) light = 0.5;
        else if (hnz > 0) light = 0.85;
        else if (hnz < 0) light = 0.6;
        else light = hnx > 0 ? 0.75 : 0.65;

        // Darken near the edges of the face so individual voxels read clearly
        double px = ox + dx * hitT, py = oy + dy * hitT, pz = oz + dz * hitT;
        double u, v;
        if (hnx != 0) { u = frac(py); v = frac(pz); }
        else if (hny != 0) { u = frac(px); v = frac(pz); }
        else { u = frac(px); v = frac(py); }
        double edge = 0.07;
        if (u < edge || u > 1 - edge || v < edge || v > 1 - edge) light *= 0.8;

        int r = (base >> 16) & 0xFF, g = (base >> 8) & 0xFF, b = base & 0xFF;
        double rr = r * light, gg = g * light, bb = b * light;

        if (highlight != null && !highlight.floor()
                && highlight.x() == hx && highlight.y() == hy && highlight.zi() == hz
                && highlight.nx() == hnx && highlight.ny() == hny && highlight.nz() == hnz) {
            rr = rr * 0.6 + 255 * 0.4;
            gg = gg * 0.6 + 255 * 0.4;
            bb = bb * 0.6 + 255 * 0.4;
        }
        return 0xFF000000 | (clamp255(rr) << 16) | (clamp255(gg) << 8) | clamp255(bb);
    }

    private int shadeFloor() {
        int[] f = floorCell();
        if (f == null) return BACKGROUND;
        double planeZ = layers / 2 + 0.5;
        double t = (planeZ - oz) / dz;
        double fx2 = frac(ox + dx * t), fy2 = frac(oy + dy * t);
        if (fx2 < 0.06 || fy2 < 0.06) return 0xFF3E3E3E;
        return ((f[0] + f[1]) & 1) == 0 ? 0xFF242424 : 0xFF2A2A2A;
    }

    private static double frac(double v) {
        return v - Math.floor(v);
    }

    private static int clamp255(double v) {
        return v < 0 ? 0 : Math.min(255, (int) v);
    }
}
