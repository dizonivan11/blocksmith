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
 * <p>
 * Rendering is split in two so the background can animate cheaply:
 * <ol>
 *   <li>{@link #render} ray-casts the model into a transparent <i>foreground</i> (model, floor grid and a soft
 *       outline). It can run at several render pixels per GUI pixel so the result is sharp on a scaled GUI, and is
 *       only needed when the model, camera or hovered face changes.</li>
 *   <li>{@link #renderBackground} fills a small bright animated background. It is cheap, runs every frame, and is
 *       drawn underneath the foreground.</li>
 * </ol>
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
    // Floor grid look
    private static final int MAJOR_LINE_EVERY = 8;

    private final int gridSize;
    private final int layers;
    private final int width;
    private final int height;
    private final int pixelScale;      // render pixels per GUI pixel; widths below are tuned in GUI pixels
    private final int ss;              // supersampling per axis (only needed when rendering at low resolution)
    private final double lineHalfWidth; // half width of floor grid lines in render pixels
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

    // Scratch values for the last shaded sample (straight, non-premultiplied colour + alpha, all 0..1 / 0..255)
    private double sr, sg, sb, sa;

    public VoxelRaycaster(int gridSize, int layers, int width, int height) {
        this(gridSize, layers, width, height, 1);
    }

    /**
     * @param pixelScale render pixels per GUI pixel (1 = low resolution, 2-3 = match a scaled GUI so the image is
     *                   sharp on screen). Line and edge widths scale with it so they look the same at any resolution.
     */
    public VoxelRaycaster(int gridSize, int layers, int width, int height, int pixelScale) {
        this.gridSize = gridSize;
        this.layers = layers;
        this.width = width;
        this.height = height;
        this.pixelScale = Math.max(1, pixelScale);
        this.ss = this.pixelScale >= 2 ? 1 : 2;
        this.lineHalfWidth = Math.max(0.6, 0.4 * this.pixelScale);
        this.cells = new int[gridSize * gridSize * layers];
        // Fit the model's bounding sphere so deep (many layer) models never clip at the corners
        double depth = layers * LAYER_THICKNESS;
        double radius = 0.5 * Math.sqrt(2.0 * gridSize * gridSize + depth * depth);
        this.defaultScale = Math.min(width, height) / 2.0 / radius * 1.02;
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

    public double yaw() { return yaw; }
    public double pitch() { return pitch; }
    /** Zoom relative to the default view (1.0 = default), independent of render resolution. */
    public double zoomLevel() { return scale / defaultScale; }

    public void setView(double yaw, double pitch, double zoomLevel) {
        this.yaw = yaw;
        this.pitch = Math.max(Math.toRadians(-89), Math.min(Math.toRadians(89), pitch));
        this.scale = Math.max(defaultScale * 0.5, Math.min(defaultScale * 4.0, defaultScale * zoomLevel));
    }

    public void zoom(double factor) {
        scale = Math.max(defaultScale * 0.5, Math.min(defaultScale * 4.0, scale * factor));
    }

    // camera basis, recomputed per call
    private double rx, ry, rz, ux, uy, uz, fx, fy, fz;
    // cells per pixel of the base-layer floor along x and y (constant across the image for an orthographic camera)
    private double floorFootX, floorFootY;

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

        // How many floor cells one screen pixel spans: d(floorXY)/d(screen) for the plane z = const
        if (Math.abs(fz) < 1e-3) {
            floorFootX = floorFootY = 1e6;
        } else {
            double pxSx = rx - fx * rz / fz, pxSy = ux - fx * uz / fz;
            double pySx = ry - fy * rz / fz, pySy = uy - fy * uz / fz;
            floorFootX = (Math.abs(pxSx) + Math.abs(pxSy)) / scale;
            floorFootY = (Math.abs(pySx) + Math.abs(pySy)) / scale;
        }
    }

    // ray for a (continuous) pixel position in grid space: origin (o*) and direction (d*, shared by all pixels)
    private double ox, oy, oz, dx, dy, dz;

    private void setRay(double pixelX, double pixelY) {
        double sx = (pixelX - width / 2.0) / scale;
        double sy = (height / 2.0 - pixelY) / scale;
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
        setRay(pixelX + 0.5, pixelY + 0.5);
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
        setRay(pixelX + 0.5, pixelY + 0.5);
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

    /** Full-quality render. See {@link #render(int[], Hit, int)}. */
    public void render(int[] fg, Hit highlight) {
        render(fg, highlight, 1);
    }

    /**
     * Ray-casts the model into {@code fg} (length width*height) as straight-alpha ARGB: fully transparent where
     * nothing is drawn, partially transparent for the floor grid and the soft outline around the model.
     * {@code highlight} may be null. {@code step > 1} renders one sample per step x step block, a fast low-detail
     * preview for use while the camera is being dragged.
     */
    public void render(int[] fg, Hit highlight, int step) {
        updateBasis();
        step = Math.max(1, step);
        for (int by = 0; by < height; by += step) {
            for (int bx = 0; bx < width; bx += step) {
                int color = renderPixel(bx, by, highlight, step);
                int xEnd = Math.min(width, bx + step), yEnd = Math.min(height, by + step);
                for (int y = by; y < yEnd; y++) {
                    for (int x = bx; x < xEnd; x++) fg[y * width + x] = color;
                }
            }
        }
        bakeOutline(fg);
    }

    /**
     * Cheap hover update: copies a highlight-free {@code baseFg} (from {@link #render} with a null highlight) into
     * {@code fg} and re-traces only the screen area covered by the highlighted voxel.
     */
    public void renderWithHighlight(int[] baseFg, int[] fg, Hit highlight) {
        System.arraycopy(baseFg, 0, fg, 0, baseFg.length);
        if (highlight == null || highlight.floor()) return;
        updateBasis();

        double cxm = gridSize / 2.0, cym = gridSize / 2.0, czm = layers / 2.0 * LAYER_THICKNESS;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double relX = highlight.x() + (i & 1) - cxm;
            double relY = highlight.y() + ((i >> 1) & 1) - cym;
            double relZ = (highlight.zi() + ((i >> 2) & 1)) * LAYER_THICKNESS - czm;
            double sx = (relX * rx + relY * ry + relZ * rz) * scale + width / 2.0;
            double sy = height / 2.0 - (relX * ux + relY * uy + relZ * uz) * scale;
            minX = Math.min(minX, sx); maxX = Math.max(maxX, sx);
            minY = Math.min(minY, sy); maxY = Math.max(maxY, sy);
        }
        int margin = 2 * pixelScale;
        int x0 = Math.max(0, (int) Math.floor(minX) - margin), x1 = Math.min(width - 1, (int) Math.ceil(maxX) + margin);
        int y0 = Math.max(0, (int) Math.floor(minY) - margin), y1 = Math.min(height - 1, (int) Math.ceil(maxY) + margin);
        for (int py = y0; py <= y1; py++) {
            for (int px = x0; px <= x1; px++) {
                int c = renderPixel(px, py, highlight, 1);
                // Only recolour model pixels; floor guide and outline pixels already baked into the base stay as they are
                if ((c >>> 24) == 0xFF) fg[py * width + px] = c;
            }
        }
    }

    /** One output pixel (or block of {@code span} pixels) as straight-alpha ARGB. Needs {@link #updateBasis} first. */
    private int renderPixel(int px, int py, Hit highlight, int span) {
        final double step = (double) span / ss;
        final int n = ss * ss;
        double accA = 0, accR = 0, accG = 0, accB = 0; // alpha-weighted sums
        for (int j = 0; j < ss; j++) {
            for (int i = 0; i < ss; i++) {
                sampleAt(px + (i + 0.5) * step, py + (j + 0.5) * step, highlight);
                if (sa > 0) {
                    accA += sa;
                    accR += sr * sa;
                    accG += sg * sa;
                    accB += sb * sa;
                }
            }
        }
        if (accA <= 0) return 0;
        int a = clamp255(accA / n * 255.0);
        return (a << 24) | (clamp255(accR / accA) << 16) | (clamp255(accG / accA) << 8) | clamp255(accB / accA);
    }

    /**
     * Adds a soft dark outline just outside the model's silhouette, baked into the foreground, so very light and
     * very dark voxels both stay readable on any background.
     */
    private void bakeOutline(int[] fg) {
        final int radius = Math.max(1, (int) Math.round(pixelScale * 0.7));
        final int w = width, h = height;
        boolean[] solid = new boolean[w * h];
        for (int i = 0; i < solid.length; i++) solid[i] = (fg[i] >>> 24) > 0xE6;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                if (solid[idx]) continue;
                // distance (in pixels, Chebyshev) to the nearest solid pixel within the radius
                int nearest = 0;
                search:
                for (int d = 1; d <= radius; d++) {
                    int xa = Math.max(0, x - d), xb = Math.min(w - 1, x + d);
                    int ya = Math.max(0, y - d), yb = Math.min(h - 1, y + d);
                    for (int yy = ya; yy <= yb; yy++) {
                        boolean edgeRow = yy == y - d || yy == y + d;
                        for (int xx = xa; xx <= xb; xx++) {
                            if (!edgeRow && xx != x - d && xx != x + d) continue; // only the ring at distance d
                            if (solid[yy * w + xx]) { nearest = d; break search; }
                        }
                    }
                }
                if (nearest == 0) continue;

                double oa = 0.85 * (1.0 - (nearest - 1) / (radius + 0.5));
                int dst = fg[idx];
                double da = ((dst >>> 24) & 0xFF) / 255.0;
                double na = oa + da * (1 - oa);
                double dr = (dst >> 16) & 0xFF, dg = (dst >> 8) & 0xFF, db = dst & 0xFF;
                double r = (0x16 * oa + dr * da * (1 - oa)) / na;
                double g = (0x1F * oa + dg * da * (1 - oa)) / na;
                double b = (0x33 * oa + db * da * (1 - oa)) / na;
                fg[idx] = (clamp255(na * 255.0) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
            }
        }
    }

    private void sampleAt(double pixelX, double pixelY, Hit highlight) {
        setRay(pixelX, pixelY);
        if (trace()) {
            shadeHit(highlight);
        } else {
            shadeFloor();
        }
    }

    private void shadeHit(Hit highlight) {
        int base = cells[index(hx, hy, hz)];
        double light;
        if (hny > 0) light = 1.0;
        else if (hny < 0) light = 0.5;
        else if (hnz > 0) light = 0.85;
        else if (hnz < 0) light = 0.6;
        else light = hnx > 0 ? 0.75 : 0.65;

        // Darken near the edges of the face so individual voxels read clearly. The rim keeps a minimum
        // on-screen width so it neither flickers nor vanishes when zoomed out.
        double px = ox + dx * hitT, py = oy + dy * hitT, pz = oz + dz * hitT;
        double u, v;
        if (hnx != 0) { u = frac(py); v = frac(pz); }
        else if (hny != 0) { u = frac(px); v = frac(pz); }
        else { u = frac(px); v = frac(py); }
        double edge = Math.max(0.08, 0.6 * pixelScale / scale);
        double du = Math.min(u, 1 - u), dv = Math.min(v, 1 - v);
        double rim = Math.min(du, dv);
        if (rim < edge) {
            double k = 1.0 - 0.22 * smooth(1.0 - rim / edge);
            light *= k;
        }

        double rr = ((base >> 16) & 0xFF) * light, gg = ((base >> 8) & 0xFF) * light, bb = (base & 0xFF) * light;

        if (highlight != null && !highlight.floor()
                && highlight.x() == hx && highlight.y() == hy && highlight.zi() == hz
                && highlight.nx() == hnx && highlight.ny() == hny && highlight.nz() == hnz) {
            rr = rr * 0.55 + 255 * 0.45;
            gg = gg * 0.55 + 255 * 0.45;
            bb = bb * 0.55 + 255 * 0.45;
        }
        sr = rr; sg = gg; sb = bb; sa = 1.0;
    }

    /** Anti-aliased base-layer guide: a faint slab with fine cell lines and stronger lines every few cells. */
    private void shadeFloor() {
        sa = 0;
        int[] f = floorCell();
        if (f == null) return;
        double planeZ = layers / 2 + 0.5;
        double t = (planeZ - oz) / dz;
        double fxc = ox + dx * t, fyc = oy + dy * t;

        // Slab tint
        double a = 0.20;
        double r = 255, g = 255, b = 255;

        // Fade fine lines out when they would be denser than ~1 per 2 pixels (grazing angles / far zoom-out)
        double foot = Math.max(floorFootX, floorFootY) * pixelScale; // cells per GUI pixel
        double fade = clamp01(1.0 - (foot - 0.35) / 0.5);

        double minor = Math.max(lineCoverage(fxc, floorFootX, 1), lineCoverage(fyc, floorFootY, 1)) * fade;
        double major = Math.max(lineCoverage(fxc, floorFootX, MAJOR_LINE_EVERY), lineCoverage(fyc, floorFootY, MAJOR_LINE_EVERY));
        double border = Math.max(lineCoverage(fxc, floorFootX, gridSize), lineCoverage(fyc, floorFootY, gridSize));

        double la = minor * 0.32;
        if (la > 0) { double na = a + la * (1 - a); r = (r * a * (1 - la) + 0x3B * la) / na; g = (g * a * (1 - la) + 0x5C * la) / na; b = (b * a * (1 - la) + 0x86 * la) / na; a = na; }
        la = major * 0.50;
        if (la > 0) { double na = a + la * (1 - a); r = (r * a * (1 - la) + 0x24 * la) / na; g = (g * a * (1 - la) + 0x3E * la) / na; b = (b * a * (1 - la) + 0x6A * la) / na; a = na; }
        la = border * 0.80;
        if (la > 0) { double na = a + la * (1 - a); r = (r * a * (1 - la) + 0x1B * la) / na; g = (g * a * (1 - la) + 0x2E * la) / na; b = (b * a * (1 - la) + 0x54 * la) / na; a = na; }

        sr = r; sg = g; sb = b; sa = a;
    }

    /**
     * Coverage (0..1) of grid lines spaced {@code spacing} cells apart at floor coordinate {@code coord}, where one
     * pixel spans {@code footprint} cells. Line width is fixed in pixels, so lines are always ~1px and anti-aliased.
     */
    private double lineCoverage(double coord, double footprint, int spacing) {
        double c = coord / spacing;
        double d = Math.min(frac(c), 1 - frac(c)) * spacing; // distance to nearest line, in cells
        double dPx = d / Math.max(footprint, 1e-6);
        return clamp01(0.5 + lineHalfWidth - dPx);
    }

    // ----- background -----

    private static final int BG_CELLS = 48; // background is evaluated on a coarse lattice and interpolated

    /**
     * Fills {@code out} (w*h, opaque ARGB) with a bright, softly animated background: slowly drifting sky blue,
     * lavender and mint with a couple of wandering light patches. Independent of the model, so it can be drawn as
     * its own small texture under the (sharper, higher resolution) foreground. {@code seconds} drives the animation.
     */
    public static void renderBackground(int[] out, int w, int h, double seconds) {
        double[] lattice = new double[(BG_CELLS + 1) * (BG_CELLS + 1) * 3];
        for (int j = 0; j <= BG_CELLS; j++) {
            double v = (double) j / BG_CELLS;
            for (int i = 0; i <= BG_CELLS; i++) {
                double u = (double) i / BG_CELLS;

                double k1 = 0.5 + 0.5 * Math.sin(seconds * 0.30 + u * 2.4 + v * 1.8);
                double k2 = 0.5 + 0.5 * Math.sin(seconds * 0.21 - u * 1.7 + v * 2.9 + 1.7);
                double r = 168 * (1 - k1) + 214 * k1;
                double g = 214 * (1 - k1) + 196 * k1;
                double b = 240 * (1 - k1) + 244 * k1;
                r = r * (1 - 0.45 * k2) + 190 * 0.45 * k2;
                g = g * (1 - 0.45 * k2) + 236 * 0.45 * k2;
                b = b * (1 - 0.45 * k2) + 222 * 0.45 * k2;

                double lift = 0;
                for (int blob = 0; blob < 2; blob++) {
                    double cx = 0.5 + 0.38 * Math.sin(seconds * (0.37 + 0.11 * blob) + blob * 2.1);
                    double cy = 0.5 + 0.38 * Math.cos(seconds * (0.29 + 0.07 * blob) + blob * 1.3);
                    double d2 = (u - cx) * (u - cx) + (v - cy) * (v - cy);
                    lift += 34 * Math.exp(-d2 / 0.07);
                }
                lift += 7 * (0.5 + 0.5 * Math.sin((u + v) * 14.0 - seconds * 0.8));

                int o = (j * (BG_CELLS + 1) + i) * 3;
                lattice[o] = r + lift;
                lattice[o + 1] = g + lift;
                lattice[o + 2] = b + lift;
            }
        }

        for (int py = 0; py < h; py++) {
            double gy = (double) py / Math.max(1, h - 1) * BG_CELLS;
            int y0 = Math.min((int) gy, BG_CELLS - 1);
            double ty = gy - y0;
            for (int px = 0; px < w; px++) {
                double gx = (double) px / Math.max(1, w - 1) * BG_CELLS;
                int x0 = Math.min((int) gx, BG_CELLS - 1);
                double tx = gx - x0;
                int i00 = (y0 * (BG_CELLS + 1) + x0) * 3, i10 = i00 + 3;
                int i01 = i00 + (BG_CELLS + 1) * 3, i11 = i01 + 3;
                double r = bilerp(lattice[i00], lattice[i10], lattice[i01], lattice[i11], tx, ty);
                double g = bilerp(lattice[i00 + 1], lattice[i10 + 1], lattice[i01 + 1], lattice[i11 + 1], tx, ty);
                double b = bilerp(lattice[i00 + 2], lattice[i10 + 2], lattice[i01 + 2], lattice[i11 + 2], tx, ty);
                out[py * w + px] = 0xFF000000 | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
            }
        }
    }

    private static double bilerp(double a, double b, double c, double d, double tx, double ty) {
        double top = a + (b - a) * tx;
        double bottom = c + (d - c) * tx;
        return top + (bottom - top) * ty;
    }

    // ----- small helpers -----

    private static double frac(double v) {
        return v - Math.floor(v);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : Math.min(1, v);
    }

    private static double smooth(double x) {
        x = clamp01(x);
        return x * x * (3 - 2 * x);
    }

    private static int clamp255(double v) {
        return v < 0 ? 0 : Math.min(255, (int) v);
    }
}
