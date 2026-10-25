package net.adeptfrog.blocksmith.client.render;

import net.adeptfrog.blocksmith.data.VoxelShape;

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
    /**
     * Layer thickness in cells at the reference 32x32 grid. The physical thickness is the same for every grid size
     * (matches the item renderer), so it is rescaled per grid in {@link #layerThickness}.
     */
    public static final double LAYER_THICKNESS = 1.25;
    private static final int REFERENCE_GRID = 32;

    /** Thickness of one layer measured in cells of this raycaster's grid. */
    private final double layerThickness;

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
    private final double pixelScale;   // render pixels per GUI pixel; widths below are tuned in GUI pixels
    private final int ss;              // supersampling per axis (only needed when rendering at low resolution)
    private final double lineHalfWidth; // half width of floor grid lines in render pixels
    private int[] cells;
    private byte[] shapes; // VoxelShape of every cell (0 = full cube)

    // Chunks: the grid is cut into small blocks. Empty ones are skipped by rays in one step, and a block that
    // changed is the only part of the picture that has to be drawn again after an edit.
    private static final int CHUNK_XY = 8, CHUNK_Z = 3;
    private final int chunksX, chunksY, chunksZ;
    private int[] chunkCount;          // non-empty cells per chunk
    private boolean[] chunkDirty;      // changed since the last render
    private boolean anyDirty = true;
    private boolean forceFull = true;  // next render must redraw everything

    // The last full-resolution render (before the outline pass) and the view it was drawn for
    private int[] raw;
    private int[] lastFg;
    private boolean haveFrame;
    private double sigYaw, sigPitch, sigScale, sigPanRight, sigPanUp;
    private int sigZi;

    private static final int[] EMPTY_RECT = {0, 0, -1, -1};

    // Screen tiles that the model can possibly cover (conservative). A ray through any other tile cannot hit a
    // voxel, so it skips tracing and only shades the floor; the outline pass also skips tiles far from the model.
    private static final int TILE_SHIFT = 3, TILE = 1 << TILE_SHIFT;
    private final int tilesX, tilesY;
    private boolean[] hitMask;      // tile may contain a voxel
    private boolean[] nearMask;     // tile or a neighbour may contain a voxel

    private double yaw;
    private double pitch;
    private double scale; // pixels per cell
    private int activeZi; // layer index the guide grid is drawn on and edits apply to
    private double panRight, panUp; // view pan in cells along the camera's right / up axes
    private final double defaultYaw = Math.toRadians(-35);
    private final double defaultPitch = Math.toRadians(25);
    private final double defaultScale;

    private boolean hlApplied; // set while shading when the hover highlight tinted the sample

    // Scratch values for the last trace
    private int hx, hy, hz, hnx, hny, hnz;
    private double hfx, hfy, hfz; // exact surface normal of the last hit (equal to hn* for flat cube faces)
    private boolean hShaped;      // the last hit was on a non-cube shape
    private final float[] quadScratch = new float[VoxelShape.MAX_QUADS * VoxelShape.QUAD_FLOATS];
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
        this(gridSize, layers, width, height, Math.max(1, pixelScale), Math.max(1, pixelScale) >= 2 ? 1 : 2);
    }

    /**
     * @param pixelScale  render pixels per GUI pixel, may be fractional (a camera preview between GUI and native size)
     * @param supersample samples per pixel along each axis (1 = none)
     */
    public VoxelRaycaster(int gridSize, int layers, int width, int height, double pixelScale, int supersample) {
        this.gridSize = gridSize;
        this.layerThickness = LAYER_THICKNESS * gridSize / REFERENCE_GRID;
        this.layers = layers;
        this.width = width;
        this.height = height;
        this.pixelScale = Math.max(0.5, pixelScale);
        this.ss = Math.max(1, supersample);
        this.lineHalfWidth = Math.max(0.6, 0.4 * this.pixelScale);
        this.cells = new int[gridSize * gridSize * layers];
        this.shapes = new byte[gridSize * gridSize * layers];
        this.chunksX = (gridSize + CHUNK_XY - 1) / CHUNK_XY;
        this.chunksY = this.chunksX;
        this.chunksZ = (layers + CHUNK_Z - 1) / CHUNK_Z;
        this.chunkCount = new int[chunksX * chunksY * chunksZ];
        this.chunkDirty = new boolean[chunkCount.length];
        this.raw = new int[width * height];
        this.tilesX = (width + TILE - 1) >> TILE_SHIFT;
        this.tilesY = (height + TILE - 1) >> TILE_SHIFT;
        this.hitMask = new boolean[tilesX * tilesY];
        this.nearMask = new boolean[tilesX * tilesY];
        this.activeZi = layers / 2;
        // Fit the model's bounding sphere so deep (many layer) models never clip at the corners
        double depth = layers * layerThickness;
        double radius = 0.5 * Math.sqrt(2.0 * gridSize * gridSize + depth * depth);
        this.defaultScale = Math.min(width, height) / 2.0 / radius * 1.02;
        resetView();
    }

    // ----- model -----

    public int gridSize() { return gridSize; }
    public int layers() { return layers; }

    /** Layer index (0..layers-1) that the guide grid sits on; voxels on other layers are drawn slightly faded. */
    public void setActiveLayer(int zi) { this.activeZi = Math.max(0, Math.min(layers - 1, zi)); }
    public int activeLayer() { return activeZi; }

    public void clear() {
        java.util.Arrays.fill(cells, 0);
        java.util.Arrays.fill(shapes, (byte) 0);
        java.util.Arrays.fill(chunkCount, 0);
        forceFull = true;
        anyDirty = true;
    }

    private int chunkIndex(int x, int y, int zi) {
        return ((zi / CHUNK_Z) * chunksY + y / CHUNK_XY) * chunksX + x / CHUNK_XY;
    }

    public boolean inBounds(int x, int y, int zi) {
        return x >= 0 && x < gridSize && y >= 0 && y < gridSize && zi >= 0 && zi < layers;
    }

    public void setCell(int x, int y, int zi, int argb) {
        setCell(x, y, zi, argb, VoxelShape.FULL);
    }

    public void setCell(int x, int y, int zi, int argb, int shape) {
        if (!inBounds(x, y, zi)) return;
        int i = index(x, y, zi);
        int old = cells[i];
        if (argb == 0) shape = VoxelShape.FULL;
        if (old == argb && shapes[i] == shape) return;
        cells[i] = argb;
        shapes[i] = (byte) shape;
        int ci = chunkIndex(x, y, zi);
        if (old == 0 && argb != 0) chunkCount[ci]++;
        else if (argb == 0 && old != 0) chunkCount[ci]--;
        chunkDirty[ci] = true;
        if ((old == 0) != (argb == 0)) {
            // A tapered neighbour reshapes itself when a voxel appears or disappears next to it
            int[][] nb = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            for (int[] d : nb) {
                if (inBounds(x + d[0], y + d[1], zi + d[2])) chunkDirty[chunkIndex(x + d[0], y + d[1], zi + d[2])] = true;
            }
        }
        anyDirty = true;
    }

    /**
     * Makes the model equal {@code next} (same layout as the internal grid: {@code (zi * gridSize + y) * gridSize + x}),
     * touching only the cells that differ so only their chunks are redrawn.
     */
    public void syncCells(int[] next) {
        syncCells(next, null);
    }

    /** As {@link #syncCells(int[])}, with the {@link VoxelShape} of every cell (null = all full cubes). */
    public void syncCells(int[] next, byte[] nextShapes) {
        if (next.length != cells.length) throw new IllegalArgumentException("wrong grid size");
        for (int i = 0; i < next.length; i++) {
            int sh = nextShapes == null ? 0 : nextShapes[i];
            if (cells[i] == next[i] && (next[i] == 0 || shapes[i] == sh)) continue;
            int x = i % gridSize, y = (i / gridSize) % gridSize, zi = i / (gridSize * gridSize);
            setCell(x, y, zi, next[i], sh);
        }
    }

    /** Number of cells in the model grid, for callers that build a grid for {@link #syncCells}. */
    public int cellCount() { return cells.length; }

    /** Index of a cell in the array passed to {@link #syncCells}. */
    public int cellIndex(int x, int y, int zi) { return index(x, y, zi); }

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
        targetX = targetY = targetZ = 0;
        syncPan();
    }

    // The point the camera looks at and orbits around, as an offset from the grid centre (world units). Panning moves
    // it; the screen-space pan values are derived from it so the pivot is always the middle of the picture.
    private double targetX, targetY, targetZ;

    private void syncPan() {
        updateBasis();
        panRight = -(targetX * rx + targetY * ry + targetZ * rz);
        panUp = -(targetX * ux + targetY * uy + targetZ * uz);
    }

    private void clampTarget() {
        double limit = gridSize * 1.5;
        double len = Math.sqrt(targetX * targetX + targetY * targetY + targetZ * targetZ);
        if (len > limit) { double k = limit / len; targetX *= k; targetY *= k; targetZ *= k; }
    }

    /**
     * Moves the pivot within the model's base plane (world z = 0 through the grid centre) so that a screen shift of
     * {@code (sr, su)} (right/up, in screen units) is produced. Keeping the pivot on that plane means it always stays
     * on the weapon itself however the view is turned; a free screen-space pan would drift it off into the air in
     * front of or behind the model, and the model would then swing around that empty point when orbiting.
     */
    private void movePivotOnPlane(double sr, double su) {
        // Solve  a*rx + b*ry = sr,  a*ux + b*uy = su  for the in-plane move (a, b, 0).
        double det = rx * uy - ry * ux;
        // Edge-on views (plane parallel to the view direction) cannot be panned exactly: bound the gain instead
        double d = Math.abs(det) < 0.2 ? Math.copySign(0.2, det == 0 ? 1 : det) : det;
        targetX += (sr * uy - ry * su) / d;
        targetY += (rx * su - ux * sr) / d;
    }

    /** Pans the view by a screen-space drag in render pixels (the model follows the cursor). */
    public void pan(double deltaPixelsX, double deltaPixelsY) {
        updateBasis();
        // The picture moves by (+dx, +dy) on screen, so the pivot moves by the opposite amount (screen y is down)
        movePivotOnPlane(-deltaPixelsX / scale, deltaPixelsY / scale);
        clampTarget();
        syncPan();
    }

    public double panRight() { return panRight; }
    public double panUp() { return panUp; }

    public void setPan(double panRight, double panUp) {
        updateBasis();
        targetX = targetY = targetZ = 0;
        movePivotOnPlane(-panRight, -panUp);
        clampTarget();
        syncPan();
    }

    /**
     * Turns the camera around the point at the centre of the current view (not always the middle of the grid): the
     * spot you panned to stays where it is on screen while everything rotates around it.
     */
    public void orbit(double deltaYaw, double deltaPitch) {
        yaw += deltaYaw;
        pitch = Math.max(Math.toRadians(-89), Math.min(Math.toRadians(89), pitch + deltaPitch));
        syncPan();
    }

    /** Unit vector from the scene towards the camera, in grid axes (x right, y up, z towards the front layers). */
    public double[] towardsCamera() {
        double cp = Math.cos(pitch);
        return new double[]{Math.sin(yaw) * cp, Math.sin(pitch), Math.cos(yaw) * cp};
    }

    public double yaw() { return yaw; }
    public double pitch() { return pitch; }
    /** Zoom relative to the default view (1.0 = default), independent of render resolution. */
    public double zoomLevel() { return scale / defaultScale; }

    public void setView(double yaw, double pitch, double zoomLevel) {
        this.yaw = yaw;
        this.pitch = Math.max(Math.toRadians(-89), Math.min(Math.toRadians(89), pitch));
        this.scale = Math.max(defaultScale * 0.5, Math.min(defaultScale * 4.0, defaultScale * zoomLevel));
        syncPan();
    }

    public void zoom(double factor) {
        scale = Math.max(defaultScale * 0.5, Math.min(defaultScale * 4.0, scale * factor));
    }

    // camera basis, recomputed per call
    private double rx, ry, rz, ux, uy, uz, fx, fy, fz;

    // Camera-following light: a light that sits up, to the left and in front of the viewer, so whatever faces the
    // camera is always lit as the model turns. lightDir* is the unit direction towards it; lightOff* its offset
    // from the model centre in grid-width units.
    private boolean cameraLight = true;
    private double lightDirX, lightDirY, lightDirZ, lightOffX, lightOffY, lightOffZ;

    // The base-layer guide grid and the silhouette outline; off when the picture is used as a plain scene backdrop
    private boolean decorations = true;

    public void setDecorations(boolean on) {
        if (decorations != on) { decorations = on; forceFull = true; }
    }

    /** When true (default) the lighting follows the camera; when false it is fixed in the world. */
    public void setCameraLight(boolean on) {
        if (cameraLight != on) { cameraLight = on; forceFull = true; }
    }
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

        // A headlight slightly above and to the left of the viewer (p = towards the camera, r = right, u = up), so
        // the faces turned towards the camera are always the best lit ones while still reading as a 3D shape
        double hx0 = px + 0.25 * ux - 0.2 * rx, hy0 = py + 0.25 * uy - 0.2 * ry, hz0 = pz + 0.25 * uz - 0.2 * rz;
        double ll = Math.sqrt(hx0 * hx0 + hy0 * hy0 + hz0 * hz0);
        lightOffX = 1.8 * hx0 / ll; lightOffY = 1.8 * hy0 / ll; lightOffZ = 1.8 * hz0 / ll;
        lightDirX = hx0 / ll; lightDirY = hy0 / ll; lightDirZ = hz0 / ll;

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

    // scratch arrays for trace(), per instance so rows can be traced on several threads without allocating per ray
    private final double[] traceO = new double[3], traceD = new double[3], traceSize = new double[3];

    // ray for a (continuous) pixel position in grid space: origin (o*) and direction (d*, shared by all pixels)
    private double ox, oy, oz, dx, dy, dz;

    private void setRay(double pixelX, double pixelY) {
        double sx = (pixelX - width / 2.0) / scale - panRight;
        double sy = (height / 2.0 - pixelY) / scale - panUp;
        double centreX = gridSize / 2.0, centreY = gridSize / 2.0, centreZ = layers / 2.0 * layerThickness;
        double wx = centreX + rx * sx + ux * sy - fx * BG_DISTANCE;
        double wy = centreY + ry * sx + uy * sy - fy * BG_DISTANCE;
        double wz = centreZ + rz * sx + uz * sy - fz * BG_DISTANCE;
        ox = wx; oy = wy; oz = wz / layerThickness;
        dx = fx; dy = fy; dz = fz / layerThickness;
    }

    // ----- tracing -----

    /** Walks the ray set by {@link #setRay}; on a hit fills the h* fields and returns true. */
    private boolean trace() {
        double tMin = Double.NEGATIVE_INFINITY, tMaxBox = Double.POSITIVE_INFINITY;
        int entryAxis = -1;
        final double[] o = traceO, d = traceD, size = traceSize;
        o[0] = ox; o[1] = oy; o[2] = oz;
        d[0] = dx; d[1] = dy; d[2] = dz;
        size[0] = gridSize; size[1] = gridSize; size[2] = layers;

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
            if (chunkCount[chunkIndex(cx, cy, cz)] == 0) {
                // Whole chunk is empty: jump straight to where the ray leaves it
                int x0 = (cx / CHUNK_XY) * CHUNK_XY, y0 = (cy / CHUNK_XY) * CHUNK_XY, z0 = (cz / CHUNK_Z) * CHUNK_Z;
                double ex = Math.abs(d[0]) < 1e-12 ? inf : ((stepX > 0 ? x0 + CHUNK_XY : x0) - o[0]) / d[0];
                double ey = Math.abs(d[1]) < 1e-12 ? inf : ((stepY > 0 ? y0 + CHUNK_XY : y0) - o[1]) / d[1];
                double ez = Math.abs(d[2]) < 1e-12 ? inf : ((stepZ > 0 ? z0 + CHUNK_Z : z0) - o[2]) / d[2];
                double tExit = Math.min(ex, Math.min(ey, ez));
                if (tExit == inf) return false;
                if (ex <= ey && ex <= ez) { nx = -stepX; ny = 0; nz = 0; }
                else if (ey <= ez) { nx = 0; ny = -stepY; nz = 0; }
                else { nx = 0; ny = 0; nz = -stepZ; }
                tCurrent = tExit;
                double tn = tExit + 1e-7;
                cx = (int) Math.floor(o[0] + d[0] * tn);
                cy = (int) Math.floor(o[1] + d[1] * tn);
                cz = (int) Math.floor(o[2] + d[2] * tn);
                if (cx < 0 || cx >= gridSize || cy < 0 || cy >= gridSize || cz < 0 || cz >= layers) return false;
                tMaxX = Math.abs(d[0]) < 1e-12 ? inf : ((stepX > 0 ? cx + 1 : cx) - o[0]) / d[0];
                tMaxY = Math.abs(d[1]) < 1e-12 ? inf : ((stepY > 0 ? cy + 1 : cy) - o[1]) / d[1];
                tMaxZ = Math.abs(d[2]) < 1e-12 ? inf : ((stepZ > 0 ? cz + 1 : cz) - o[2]) / d[2];
                continue;
            }
            int ci = index(cx, cy, cz);
            if (cells[ci] != 0) {
                int sh = shapes[ci];
                if (sh == VoxelShape.FULL) {
                    hx = cx; hy = cy; hz = cz; hnx = nx; hny = ny; hnz = nz; hitT = tCurrent;
                    hfx = nx; hfy = ny; hfz = nz; hShaped = false;
                    return true;
                }
                if (shapeHit(cx, cy, cz, sh, tCurrent)) return true;
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

    /**
     * Ray against the real geometry of a non-cube voxel (the ray is already set; cell space is a unit cube per cell).
     * On a hit it fills the h* fields and returns true.
     */
    private boolean shapeHit(int cx, int cy, int cz, int shape, double tFrom) {
        final float[] q = quadScratch;
        int count = VoxelShape.buildQuads(shape, 0, q);
        double lx = ox - cx, ly = oy - cy, lz = oz - cz;
        double best = Double.POSITIVE_INFINITY;
        int bestQ = -1;
        for (int i = 0; i < count; i++) {
            int o = i * VoxelShape.QUAD_FLOATS;
            // two triangles: (0,1,2) and (0,2,3)
            double t = triangleT(lx, ly, lz, q, o, o + 3, o + 6);
            double t2 = triangleT(lx, ly, lz, q, o, o + 6, o + 9);
            if (t2 < t) t = t2;
            if (t >= tFrom - 1e-9 && t < best) { best = t; bestQ = i; }
        }
        if (bestQ < 0) return false;
        int o = bestQ * VoxelShape.QUAD_FLOATS + 12;
        double qx = q[o], qy = q[o + 1], qz = q[o + 2];
        // The normal in physical space: cell z is squashed by the layer thickness, so slopes tilt with it
        double nzp = qz / layerThickness;
        double len = Math.sqrt(qx * qx + qy * qy + nzp * nzp);
        hfx = qx / len; hfy = qy / len; hfz = nzp / len;
        // Dominant axis, for placing and for the highlight
        double ax = Math.abs(qx), ay = Math.abs(qy), az = Math.abs(qz);
        hnx = hny = hnz = 0;
        if (ax >= ay && ax >= az) hnx = qx > 0 ? 1 : -1;
        else if (ay >= az) hny = qy > 0 ? 1 : -1;
        else hnz = qz > 0 ? 1 : -1;
        hx = cx; hy = cy; hz = cz; hitT = best; hShaped = true;
        return true;
    }

    /** Möller-Trumbore ray / triangle distance for the ray (o + t d), vertices read from {@code q}; infinity on a miss. */
    private double triangleT(double lx, double ly, double lz, float[] q, int ia, int ib, int ic) {
        double e1x = q[ib] - q[ia], e1y = q[ib + 1] - q[ia + 1], e1z = q[ib + 2] - q[ia + 2];
        double e2x = q[ic] - q[ia], e2y = q[ic + 1] - q[ia + 1], e2z = q[ic + 2] - q[ia + 2];
        double px = dy * e2z - dz * e2y, py = dz * e2x - dx * e2z, pz = dx * e2y - dy * e2x;
        double det = e1x * px + e1y * py + e1z * pz;
        if (Math.abs(det) < 1e-12) return Double.POSITIVE_INFINITY;
        double inv = 1.0 / det;
        double tx = lx - q[ia], ty = ly - q[ia + 1], tz = lz - q[ia + 2];
        double u = (tx * px + ty * py + tz * pz) * inv;
        if (u < -1e-9 || u > 1 + 1e-9) return Double.POSITIVE_INFINITY;
        double qx = ty * e1z - tz * e1y, qy = tz * e1x - tx * e1z, qz = tx * e1y - ty * e1x;
        double v = (dx * qx + dy * qy + dz * qz) * inv;
        if (v < -1e-9 || u + v > 1 + 1e-9) return Double.POSITIVE_INFINITY;
        return (e2x * qx + e2y * qy + e2z * qz) * inv;
    }

    private static int clamp(int v, int n) {
        return Math.max(0, Math.min(n - 1, v));
    }

    /** Where the current ray crosses the middle of the base layer, or null if it misses the base layer footprint. */
    private int[] floorCell() {
        if (Math.abs(dz) < 1e-12) return null;
        double planeZ = activeZi + 0.5;
        double t = (planeZ - oz) / dz;
        if (t < 0) return null;
        int x = (int) Math.floor(ox + dx * t);
        int y = (int) Math.floor(oy + dy * t);
        if (x < 0 || x >= gridSize || y < 0 || y >= gridSize) return null;
        return new int[]{x, y, activeZi};
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

    /**
     * Screen position, in this raycaster's pixels, of a point in grid space ({@code gz} is a layer-cell index and may
     * be fractional, e.g. {@code zi + 0.5} for the middle of a layer). Same projection the highlight uses.
     */
    public double[] project(double gx, double gy, double gz) {
        updateBasis();
        double relX = gx - gridSize / 2.0, relY = gy - gridSize / 2.0, relZ = gz * layerThickness - layers / 2.0 * layerThickness;
        double sx = (relX * rx + relY * ry + relZ * rz + panRight) * scale + width / 2.0;
        double sy = height / 2.0 - (relX * ux + relY * uy + relZ * uz + panUp) * scale;
        return new double[]{sx, sy};
    }

    /** Cell of the active layer under a pixel (where the ray crosses the middle of that layer), or null outside the grid. */
    public int[] pickCell(int pixelX, int pixelY) {
        if (pixelX < 0 || pixelY < 0 || pixelX >= width || pixelY >= height) return null;
        return pickOnPlane(pixelX, pixelY, 2, activeZi);
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
     * <p>
     * Rows are drawn on several threads. When only some chunks of the model changed since the last call and the
     * camera did not move, only the part of the picture those chunks cover is drawn again; {@code fg} must then
     * still hold the previous result.
     *
     * @return bounds {@code {x0, y0, x1, y1}} (inclusive) of the pixels that may have changed, an empty rectangle
     *         (x1 &lt; x0) if nothing changed, or null if the whole image may have changed
     */
    public int[] render(int[] fg, Hit highlight, int step) {
        updateBasis();
        buildHitMask();
        step = Math.max(1, step);

        boolean sameView = haveFrame && sigYaw == yaw && sigPitch == pitch && sigScale == scale
                && sigPanRight == panRight && sigPanUp == panUp && sigZi == activeZi;
        if (step == 1 && highlight == null && sameView && !forceFull && fg == lastFg) {
            if (!anyDirty) return EMPTY_RECT;
            int[] rect = dirtyRect();
            if (rect != null) {
                renderRect(rect[0], rect[1], rect[2], rect[3]);
                int radius = outlineRadius();
                int ox0 = Math.max(0, rect[0] - radius), oy0 = Math.max(0, rect[1] - radius);
                int ox1 = Math.min(width - 1, rect[2] + radius), oy1 = Math.min(height - 1, rect[3] + radius);
                bakeOutline(raw, fg, ox0, oy0, ox1, oy1);
                clearDirty();
                return new int[]{ox0, oy0, ox1, oy1};
            }
        }

        renderRows(highlight, step);
        bakeOutline(raw, fg, 0, 0, width - 1, height - 1);

        // Remember what this picture shows so later calls can update it in place
        boolean full = step == 1 && highlight == null;
        haveFrame = full;
        forceFull = !full;
        lastFg = fg;
        sigYaw = yaw; sigPitch = pitch; sigScale = scale; sigPanRight = panRight; sigPanUp = panUp; sigZi = activeZi;
        if (full) clearDirty();
        return null;
    }

    /** Marks the screen tiles covered by the projection of every non-empty chunk. Needs {@link #updateBasis} first. */
    private void buildHitMask() {
        java.util.Arrays.fill(hitMask, false);
        double cxm = gridSize / 2.0, cym = gridSize / 2.0, czm = layers / 2.0 * layerThickness;
        for (int cz = 0; cz < chunksZ; cz++) {
            for (int cy = 0; cy < chunksY; cy++) {
                for (int cx = 0; cx < chunksX; cx++) {
                    if (chunkCount[(cz * chunksY + cy) * chunksX + cx] == 0) continue;
                    double[] lo = {cx * CHUNK_XY, cy * CHUNK_XY, cz * CHUNK_Z};
                    double[] hi = {Math.min(gridSize, (cx + 1) * CHUNK_XY), Math.min(gridSize, (cy + 1) * CHUNK_XY),
                            Math.min(layers, (cz + 1) * CHUNK_Z)};
                    double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
                    for (int i = 0; i < 8; i++) {
                        double relX = ((i & 1) == 0 ? lo[0] : hi[0]) - cxm;
                        double relY = ((i & 2) == 0 ? lo[1] : hi[1]) - cym;
                        double relZ = ((i & 4) == 0 ? lo[2] : hi[2]) * layerThickness - czm;
                        double sx = (relX * rx + relY * ry + relZ * rz + panRight) * scale + width / 2.0;
                        double sy = height / 2.0 - (relX * ux + relY * uy + relZ * uz + panUp) * scale;
                        minX = Math.min(minX, sx); maxX = Math.max(maxX, sx);
                        minY = Math.min(minY, sy); maxY = Math.max(maxY, sy);
                    }
                    int tx0 = Math.max(0, ((int) Math.floor(minX) - 2) >> TILE_SHIFT), tx1 = Math.min(tilesX - 1, ((int) Math.ceil(maxX) + 2) >> TILE_SHIFT);
                    int ty0 = Math.max(0, ((int) Math.floor(minY) - 2) >> TILE_SHIFT), ty1 = Math.min(tilesY - 1, ((int) Math.ceil(maxY) + 2) >> TILE_SHIFT);
                    for (int ty = ty0; ty <= ty1; ty++) {
                        for (int tx = tx0; tx <= tx1; tx++) hitMask[ty * tilesX + tx] = true;
                    }
                }
            }
        }
        // The outline pass also rings the darkest spots of the floor grid (where border and major lines cross), so
        // the tiles under the guide grid count as "near" too
        double fMinX = Double.MAX_VALUE, fMaxX = -Double.MAX_VALUE, fMinY = Double.MAX_VALUE, fMaxY = -Double.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            double relX = ((i & 1) == 0 ? 0 : gridSize) - cxm;
            double relY = ((i & 2) == 0 ? 0 : gridSize) - cym;
            double relZ = (activeZi + 0.5) * layerThickness - czm;
            double sx = (relX * rx + relY * ry + relZ * rz + panRight) * scale + width / 2.0;
            double sy = height / 2.0 - (relX * ux + relY * uy + relZ * uz + panUp) * scale;
            fMinX = Math.min(fMinX, sx); fMaxX = Math.max(fMaxX, sx);
            fMinY = Math.min(fMinY, sy); fMaxY = Math.max(fMaxY, sy);
        }
        int fx0 = Math.max(0, ((int) Math.floor(fMinX) - 4) >> TILE_SHIFT), fx1 = Math.min(tilesX - 1, ((int) Math.ceil(fMaxX) + 4) >> TILE_SHIFT);
        int fy0 = Math.max(0, ((int) Math.floor(fMinY) - 4) >> TILE_SHIFT), fy1 = Math.min(tilesY - 1, ((int) Math.ceil(fMaxY) + 4) >> TILE_SHIFT);

        // One tile of margin for the outline, which reaches a couple of pixels past the model
        for (int ty = 0; ty < tilesY; ty++) {
            for (int tx = 0; tx < tilesX; tx++) {
                boolean near = false;
                for (int dy = -1; dy <= 1 && !near; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int x = tx + dx, y = ty + dy;
                        if (x >= 0 && y >= 0 && x < tilesX && y < tilesY && hitMask[y * tilesX + x]) { near = true; break; }
                    }
                }
                nearMask[ty * tilesX + tx] = near || (tx >= fx0 && tx <= fx1 && ty >= fy0 && ty <= fy1);
            }
        }
    }

    private void clearDirty() {
        java.util.Arrays.fill(chunkDirty, false);
        anyDirty = false;
        forceFull = false;
    }

    private int outlineRadius() {
        return Math.max(1, (int) Math.round(pixelScale * 0.7));
    }

    /** Screen rectangle covering every changed chunk (and its neighbouring cells), or null if that is most of the image. */
    private int[] dirtyRect() {
        double cxm = gridSize / 2.0, cym = gridSize / 2.0, czm = layers / 2.0 * layerThickness;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int cz = 0; cz < chunksZ; cz++) {
            for (int cy = 0; cy < chunksY; cy++) {
                for (int cx = 0; cx < chunksX; cx++) {
                    if (!chunkDirty[(cz * chunksY + cy) * chunksX + cx]) continue;
                    // The chunk plus one cell around it: a changed voxel also changes the edges of its neighbours
                    double[] lo = {cx * CHUNK_XY - 1, cy * CHUNK_XY - 1, cz * CHUNK_Z - 1};
                    double[] hi = {Math.min(gridSize, (cx + 1) * CHUNK_XY) + 1, Math.min(gridSize, (cy + 1) * CHUNK_XY) + 1,
                            Math.min(layers, (cz + 1) * CHUNK_Z) + 1};
                    for (int i = 0; i < 8; i++) {
                        double relX = ((i & 1) == 0 ? lo[0] : hi[0]) - cxm;
                        double relY = ((i & 2) == 0 ? lo[1] : hi[1]) - cym;
                        double relZ = ((i & 4) == 0 ? lo[2] : hi[2]) * layerThickness - czm;
                        double sx = (relX * rx + relY * ry + relZ * rz + panRight) * scale + width / 2.0;
                        double sy = height / 2.0 - (relX * ux + relY * uy + relZ * uz + panUp) * scale;
                        minX = Math.min(minX, sx); maxX = Math.max(maxX, sx);
                        minY = Math.min(minY, sy); maxY = Math.max(maxY, sy);
                    }
                }
            }
        }
        int margin = (int) Math.ceil(2 * pixelScale) + 2;
        int x0 = Math.max(0, (int) Math.floor(minX) - margin), x1 = Math.min(width - 1, (int) Math.ceil(maxX) + margin);
        int y0 = Math.max(0, (int) Math.floor(minY) - margin), y1 = Math.min(height - 1, (int) Math.ceil(maxY) + margin);
        if (x1 < x0 || y1 < y0) return EMPTY_RECT;
        // Redrawing most of the image anyway: do it in one go
        if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) > 0.6 * width * height) return null;
        return new int[]{x0, y0, x1, y1};
    }

    // ----- multi-threaded drawing -----

    private static final int BAND_ROWS = 8;
    private static final int WORKERS = Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors() - 1));
    private long stamp;                        // render this worker's camera copy belongs to
    private long renderStamp;                  // incremented per render on the main instance
    private final ThreadLocal<VoxelRaycaster> workers = ThreadLocal.withInitial(() -> new VoxelRaycaster(this));

    /** Worker copy: shares the model with {@code p} but has its own scratch fields, so rows can be drawn in parallel. */
    private VoxelRaycaster(VoxelRaycaster p) {
        this.gridSize = p.gridSize;
        this.layerThickness = p.layerThickness;
        this.layers = p.layers;
        this.width = p.width;
        this.height = p.height;
        this.pixelScale = p.pixelScale;
        this.ss = p.ss;
        this.lineHalfWidth = p.lineHalfWidth;
        this.cells = p.cells;
        this.shapes = p.shapes;
        this.chunksX = p.chunksX;
        this.chunksY = p.chunksY;
        this.chunksZ = p.chunksZ;
        this.chunkCount = p.chunkCount;
        this.chunkDirty = p.chunkDirty;
        this.defaultScale = p.defaultScale;
        this.raw = p.raw;
        this.tilesX = p.tilesX;
        this.tilesY = p.tilesY;
        this.hitMask = p.hitMask;
        this.nearMask = p.nearMask;
    }

    private void copyCameraFrom(VoxelRaycaster p) {
        yaw = p.yaw; pitch = p.pitch; scale = p.scale; activeZi = p.activeZi; panRight = p.panRight; panUp = p.panUp;
        rx = p.rx; ry = p.ry; rz = p.rz; ux = p.ux; uy = p.uy; uz = p.uz; fx = p.fx; fy = p.fy; fz = p.fz;
        floorFootX = p.floorFootX; floorFootY = p.floorFootY;
        cameraLight = p.cameraLight;
        decorations = p.decorations;
        lightDirX = p.lightDirX; lightDirY = p.lightDirY; lightDirZ = p.lightDirZ;
        lightOffX = p.lightOffX; lightOffY = p.lightOffY; lightOffZ = p.lightOffZ;
    }

    private VoxelRaycaster workerFor(long renderId) {
        VoxelRaycaster w = workers.get();
        if (w.stamp != renderId) {
            w.copyCameraFrom(this);
            w.stamp = renderId;
        }
        return w;
    }

    /** Draws the whole image (in blocks of {@code step} x {@code step} samples) into {@code raw}. */
    private void renderRows(Hit highlight, int step) {
        final int st = step;
        final long id = ++renderStamp;
        int bandRows = BAND_ROWS * st;
        int bands = (height + bandRows - 1) / bandRows;
        java.util.function.IntConsumer band = b -> {
            VoxelRaycaster w = workerFor(id);
            int y0 = b * bandRows, y1 = Math.min(height, y0 + bandRows);
            for (int by = y0; by < y1; by += st) {
                for (int bx = 0; bx < width; bx += st) {
                    int color = w.renderPixel(bx, by, highlight, st);
                    int xEnd = Math.min(width, bx + st), yEnd = Math.min(height, by + st);
                    for (int y = by; y < yEnd; y++) {
                        for (int x = bx; x < xEnd; x++) raw[y * width + x] = color;
                    }
                }
            }
        };
        runBands(bands, band);
    }

    /** Draws just a rectangle of pixels at full detail into {@code raw}. */
    private void renderRect(int x0, int y0, int x1, int y1) {
        final long id = ++renderStamp;
        int rows = y1 - y0 + 1;
        int bands = (rows + BAND_ROWS - 1) / BAND_ROWS;
        runBands(bands, b -> {
            VoxelRaycaster w = workerFor(id);
            int ya = y0 + b * BAND_ROWS, yb = Math.min(y1 + 1, ya + BAND_ROWS);
            for (int y = ya; y < yb; y++) {
                for (int x = x0; x <= x1; x++) raw[y * width + x] = w.renderPixel(x, y, null, 1);
            }
        });
    }

    /** Width of the image in render pixels. */
    public int width() { return width; }

    /** Number of floats written by {@link #exportView}. */
    public static final int VIEW_FLOATS = 32;

    /**
     * Writes everything the GPU renderer needs to draw exactly what {@link #render} would, in this order:
     * width, height, gridSize, layers, layerThickness, activeZi, scale, panRight, panUp, right (3), up (3),
     * forward (3), lightDir (3), lightOff (3), cameraLight, decorations, supersampling, outline radius,
     * floorFootX, floorFootY, pixelScale, lineHalfWidth.
     */
    public void exportView(float[] out) {
        updateBasis();
        double[] v = {width, height, gridSize, layers, layerThickness, activeZi, scale, panRight, panUp,
                rx, ry, rz, ux, uy, uz, fx, fy, fz, lightDirX, lightDirY, lightDirZ, lightOffX, lightOffY, lightOffZ,
                cameraLight ? 1 : 0, decorations ? 1 : 0, ss, outlineRadius(), floorFootX, floorFootY, pixelScale, lineHalfWidth};
        for (int i = 0; i < VIEW_FLOATS; i++) out[i] = (float) v[i];
    }

    /**
     * Scales a square straight-alpha ARGB image {@code src} (sw x sw) to {@code dst} (dw x dw) with bilinear filtering,
     * blending in premultiplied space so transparent pixels do not darken the edges. Used to show a camera preview
     * smoothly instead of as blocky pixels.
     */
    public static void upscale(int[] src, int sw, int[] dst, int dw) {
        if (sw == dw) {
            System.arraycopy(src, 0, dst, 0, dw * dw);
            return;
        }
        final double k = (double) sw / dw;
        int bands = (dw + BAND_ROWS - 1) / BAND_ROWS;
        runBands(bands, b -> {
            int ya = b * BAND_ROWS, yb = Math.min(dw, ya + BAND_ROWS);
            for (int y = ya; y < yb; y++) {
                double fy = Math.max(0.0, Math.min(sw - 1.0, (y + 0.5) * k - 0.5));
                int y0 = (int) fy, y1 = Math.min(sw - 1, y0 + 1);
                double ty = fy - y0;
                int r0 = y0 * sw, r1 = y1 * sw, out = y * dw;
                for (int x = 0; x < dw; x++) {
                    double fx = Math.max(0.0, Math.min(sw - 1.0, (x + 0.5) * k - 0.5));
                    int x0 = (int) fx, x1 = Math.min(sw - 1, x0 + 1);
                    double tx = fx - x0;
                    int c00 = src[r0 + x0], c10 = src[r0 + x1], c01 = src[r1 + x0], c11 = src[r1 + x1];
                    if ((c00 | c10 | c01 | c11) == 0) { dst[out + x] = 0; continue; }
                    if (c00 == c10 && c00 == c01 && c00 == c11) { dst[out + x] = c00; continue; }
                    double w00 = (1 - tx) * (1 - ty), w10 = tx * (1 - ty), w01 = (1 - tx) * ty, w11 = tx * ty;
                    double a00 = (c00 >>> 24) * w00, a10 = (c10 >>> 24) * w10, a01 = (c01 >>> 24) * w01, a11 = (c11 >>> 24) * w11;
                    double a = a00 + a10 + a01 + a11;
                    if (a <= 0) { dst[out + x] = 0; continue; }
                    double r = ((c00 >> 16) & 0xFF) * a00 + ((c10 >> 16) & 0xFF) * a10 + ((c01 >> 16) & 0xFF) * a01 + ((c11 >> 16) & 0xFF) * a11;
                    double g = ((c00 >> 8) & 0xFF) * a00 + ((c10 >> 8) & 0xFF) * a10 + ((c01 >> 8) & 0xFF) * a01 + ((c11 >> 8) & 0xFF) * a11;
                    double bl = (c00 & 0xFF) * a00 + (c10 & 0xFF) * a10 + (c01 & 0xFF) * a01 + (c11 & 0xFF) * a11;
                    dst[out + x] = (clamp255(a) << 24) | (clamp255(r / a) << 16) | (clamp255(g / a) << 8) | clamp255(bl / a);
                }
            }
        });
    }

    private static void runBands(int bands, java.util.function.IntConsumer body) {
        if (WORKERS <= 1 || bands <= 1) {
            for (int b = 0; b < bands; b++) body.accept(b);
        } else {
            java.util.stream.IntStream.range(0, bands).parallel().forEach(body);
        }
    }

    /**
     * Cheap hover update: copies a highlight-free {@code baseFg} (from {@link #render} with a null highlight) into
     * {@code fg} and re-traces only the screen area covered by the highlighted voxel.
     *
     * @return the re-traced bounds {@code {x0, y0, x1, y1}}, or null when there is no highlight
     */
    public int[] renderWithHighlight(int[] baseFg, int[] fg, Hit highlight) {
        System.arraycopy(baseFg, 0, fg, 0, baseFg.length);
        if (highlight == null) return null;
        updateBasis();
        buildHitMask();

        double cxm = gridSize / 2.0, cym = gridSize / 2.0, czm = layers / 2.0 * layerThickness;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double relX = highlight.x() + (i & 1) - cxm;
            double relY = highlight.y() + ((i >> 1) & 1) - cym;
            double relZ = (highlight.zi() + ((i >> 2) & 1)) * layerThickness - czm;
            double sx = (relX * rx + relY * ry + relZ * rz + panRight) * scale + width / 2.0;
            double sy = height / 2.0 - (relX * ux + relY * uy + relZ * uz + panUp) * scale;
            minX = Math.min(minX, sx); maxX = Math.max(maxX, sx);
            minY = Math.min(minY, sy); maxY = Math.max(maxY, sy);
        }
        int margin = (int) Math.ceil(2 * pixelScale);
        int x0 = Math.max(0, (int) Math.floor(minX) - margin), x1 = Math.min(width - 1, (int) Math.ceil(maxX) + margin);
        int y0 = Math.max(0, (int) Math.floor(minY) - margin), y1 = Math.min(height - 1, (int) Math.ceil(maxY) + margin);
        for (int py = y0; py <= y1; py++) {
            for (int px = x0; px <= x1; px++) {
                hlApplied = false;
                int c = renderPixel(px, py, highlight, 1);
                // Only pixels the highlight actually touched are replaced; everything else (outline, other floor
                // pixels) stays exactly as baked into the base
                if (hlApplied && (c >>> 24) != 0) fg[py * width + px] = c;
            }
        }
        return new int[]{x0, y0, x1, y1};
    }

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
    private void bakeOutline(int[] src, int[] dst, int rx0, int ry0, int rx1, int ry1) {
        if (!decorations) {
            for (int y = ry0; y <= ry1; y++) System.arraycopy(src, y * width + rx0, dst, y * width + rx0, rx1 - rx0 + 1);
            return;
        }
        final int radius = outlineRadius();
        final int w = width, h = height;
        runBands((ry1 - ry0 + 16) / 16, b -> {
            int ya = ry0 + b * 16, yb = Math.min(ry1 + 1, ya + 16);
            for (int y = ya; y < yb; y++) {
                for (int x = rx0; x <= rx1; x++) {
                    int idx = y * w + x;
                    int px = src[idx];
                    if ((px >>> 24) > 0xE6 || !nearMask[(y >> TILE_SHIFT) * tilesX + (x >> TILE_SHIFT)]) { dst[idx] = px; continue; }
                    // distance (in pixels, Chebyshev) to the nearest solid pixel within the radius
                    int nearest = 0;
                    search:
                    for (int d = 1; d <= radius; d++) {
                        int xa = Math.max(0, x - d), xb = Math.min(w - 1, x + d);
                        int yya = Math.max(0, y - d), yyb = Math.min(h - 1, y + d);
                        for (int yy = yya; yy <= yyb; yy++) {
                            boolean edgeRow = yy == y - d || yy == y + d;
                            for (int xx = xa; xx <= xb; xx++) {
                                if (!edgeRow && xx != x - d && xx != x + d) continue; // only the ring at distance d
                                if ((src[yy * w + xx] >>> 24) > 0xE6) { nearest = d; break search; }
                            }
                        }
                    }
                    if (nearest == 0) { dst[idx] = px; continue; }

                    double oa = 0.85 * (1.0 - (nearest - 1) / (radius + 0.5));
                    double da = ((px >>> 24) & 0xFF) / 255.0;
                    double na = oa + da * (1 - oa);
                    double dr = (px >> 16) & 0xFF, dg = (px >> 8) & 0xFF, db = px & 0xFF;
                    double r = (0x16 * oa + dr * da * (1 - oa)) / na;
                    double g = (0x1F * oa + dg * da * (1 - oa)) / na;
                    double bl = (0x33 * oa + db * da * (1 - oa)) / na;
                    dst[idx] = (clamp255(na * 255.0) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(bl);
                }
            }
        });
    }

    private void sampleAt(double pixelX, double pixelY, Hit highlight) {
        setRay(pixelX, pixelY);
        int tile = Math.min(tilesY - 1, (int) pixelY >> TILE_SHIFT) * tilesX + Math.min(tilesX - 1, (int) pixelX >> TILE_SHIFT);
        if (hitMask[tile] && trace()) {
            shadeHit(highlight);
            // The guide grid sits in the middle of the active layer: it shows over voxels behind that plane
            // (a face lying exactly on the plane, like the middle of a slab, covers it)
            if (decorations && floorT() < hitT - 1e-3) {
                double vr = sr, vg = sg, vb = sb;
                shadeFloor(highlight);
                double fa = sa;
                sr = sr * fa + vr * (1 - fa); sg = sg * fa + vg * (1 - fa); sb = sb * fa + vb * (1 - fa);
                sa = 1.0;
            }
        } else if (decorations) {
            shadeFloor(highlight);
        } else {
            sa = 0;
        }
    }

    private boolean solidAt(int x, int y, int zi) {
        return inBounds(x, y, zi) && cells[index(x, y, zi)] != 0;
    }

    /** Whether the half-size block at (sx, sy, sz) (cell coordinates doubled) is solid, honouring voxel shapes. */
    private boolean occupiedHalf(int sx, int sy, int sz) {
        int cx = sx >> 1, cy = sy >> 1, cz = sz >> 1;
        if (!inBounds(cx, cy, cz)) return false;
        int ci = index(cx, cy, cz);
        if (cells[ci] == 0) return false;
        int sh = shapes[ci];
        if (sh == VoxelShape.FULL) return true;
        return ((VoxelShape.octants(sh) >> ((sx & 1) + 2 * (sy & 1) + 4 * (sz & 1))) & 1) != 0;
    }

    /**
     * Distance (in cells) from the hit point to the nearest real edge of the surface it is on, or MAX_VALUE if no edge is
     * within {@code limit}. Works on half-size blocks, so slabs and stairs get edges along their own outline and a
     * neighbouring slab does not hide the edge of a full block. An edge is skipped when the next half-block along the
     * surface continues the same flat plane.
     */
    private double rimDistance(double px, double py, double pz, double limit) {
        double[] pt = {px, py, pz};
        int[] n = {hnx, hny, hnz};
        int a = hnx != 0 ? 0 : hny != 0 ? 1 : 2;
        int s = n[a];
        int[] sub = new int[3];
        for (int k = 0; k < 3; k++) sub[k] = (int) Math.floor(pt[k] * 2);
        sub[a] = s > 0 ? (int) Math.round(pt[a] * 2) - 1 : (int) Math.round(pt[a] * 2);
        if (!occupiedHalf(sub[0], sub[1], sub[2])) return Double.MAX_VALUE;
        double best = Double.MAX_VALUE;
        for (int i = 1; i <= 2; i++) {
            int b = (a + i) % 3;
            double t = pt[b] * 2 - sub[b]; // 0..1 across the half-block
            double dist = Math.min(t, 1 - t) / 2;
            if (dist >= limit || dist >= best) continue;
            int side = t < 0.5 ? -1 : 1;
            int[] nb = sub.clone();
            nb[b] += side;
            boolean real;
            if (!occupiedHalf(nb[0], nb[1], nb[2])) real = true;                  // the surface ends here (convex edge)
            else {
                nb[a] += s;
                real = occupiedHalf(nb[0], nb[1], nb[2]);                         // a block in front of the neighbour: concave corner
            }
            if (real) best = dist;
        }
        return best;
    }

    private void shadeHit(Hit highlight) {
        int base = cells[index(hx, hy, hz)];
        double light;
        if (cameraLight) {
            // Faces turned towards the camera-side light are bright, faces turned away fall to half brightness
            double facing = Math.max(0, hfx * lightDirX + hfy * lightDirY + hfz * lightDirZ);
            light = 0.55 + 0.45 * Math.sqrt(facing);
        } else if (hny > 0) light = 1.0;
        else if (hny < 0) light = 0.5;
        else if (hnz > 0) light = 0.85;
        else if (hnz < 0) light = 0.6;
        else light = hnx > 0 ? 0.75 : 0.65;

        // Borders are drawn only along the model's real edges. The line between two neighbouring voxels on the same
        // flat surface is skipped, so same-coloured voxels merge into one clean surface instead of a grid.
        double px = ox + dx * hitT, py = oy + dy * hitT, pz = oz + dz * hitT;
        double edge = Math.max(0.08, 0.6 * pixelScale / scale);
        double rim = rimDistance(px, py, pz, edge);
        if (rim < edge) {
            light *= 1.0 - 0.5 * smooth(1.0 - rim / edge);
        }

        // White "studio" light: a point light above and in front of the model gives every face a soft gradient,
        // brightens towards white where it shines, and adds a small specular glint.
        double wx = ox + dx * hitT, wy = oy + dy * hitT, wz = (oz + dz * hitT) * layerThickness;
        double g = gridSize;
        double lx, ly, lz;
        if (cameraLight) {
            lx = g * 0.5 + lightOffX * g - wx; ly = g * 0.5 + lightOffY * g - wy; lz = layers / 2.0 * layerThickness + lightOffZ * g - wz;
        } else {
            lx = g * 0.5 - 0.7 * g - wx; ly = g * 0.5 + 0.9 * g - wy; lz = layers / 2.0 * layerThickness + 1.4 * g - wz;
        }
        double ld = Math.sqrt(lx * lx + ly * ly + lz * lz);
        lx /= ld; ly /= ld; lz /= ld;
        double ndl = Math.max(0, hfx * lx + hfy * ly + hfz * lz);
        double atten = 1.0 / (1.0 + Math.pow(ld / (g * 2.2), 2));
        double shine = 0.80 + 0.75 * ndl * (0.55 + 0.45 * atten) - 0.05;
        light *= shine;
        // reflect the light about the normal and compare with the view direction (towards the camera = -f)
        double dot = hfx * lx + hfy * ly + hfz * lz;
        double rxv = 2 * dot * hfx - lx, ryv = 2 * dot * hfy - ly, rzv = 2 * dot * hfz - lz;
        double spec = Math.pow(Math.max(0, -(rxv * fx + ryv * fy + rzv * fz / layerThickness * layerThickness)), 24) * 0.35 * atten * (ndl > 0 ? 1 : 0);

        double rr = ((base >> 16) & 0xFF) * light, gg = ((base >> 8) & 0xFF) * light, bb = (base & 0xFF) * light;
        if (light > 1.0) { // over-exposed colour washes towards white instead of clipping to a hue
            double w = Math.min(0.5, (light - 1.0) * 0.8);
            rr += (255 - rr) * w; gg += (255 - gg) * w; bb += (255 - bb) * w;
        }
        rr += 255 * spec; gg += 255 * spec; bb += 255 * spec;

        if (highlight != null && !highlight.floor()
                && highlight.x() == hx && highlight.y() == hy && highlight.zi() == hz
                && ((highlight.nx() == 0 && highlight.ny() == 0 && highlight.nz() == 0)
                    || (highlight.nx() == hnx && highlight.ny() == hny && highlight.nz() == hnz))) {
            hlApplied = true;
            rr = rr * 0.55 + 255 * 0.45;
            gg = gg * 0.55 + 255 * 0.45;
            bb = bb * 0.55 + 255 * 0.45;
        }
        sr = rr; sg = gg; sb = bb; sa = 1.0;
    }

    /** Ray distance to the guide grid's plane (the middle of the active layer), infinity if the ray never reaches it. */
    private double floorT() {
        if (Math.abs(dz) < 1e-12) return Double.POSITIVE_INFINITY;
        double t = (activeZi + 0.5 - oz) / dz;
        return t < 0 ? Double.POSITIVE_INFINITY : t;
    }

    /** Anti-aliased base-layer guide: a faint slab with fine cell lines and stronger lines every few cells. */
    private void shadeFloor(Hit highlight) {
        sa = 0;
        if (Math.abs(dz) < 1e-12) return;
        double planeZ = activeZi + 0.5;
        double t = (planeZ - oz) / dz;
        if (t < 0) return;
        double fxc = ox + dx * t, fyc = oy + dy * t;
        int cellX = (int) Math.floor(fxc), cellY = (int) Math.floor(fyc);
        if (cellX < 0 || cellX >= gridSize || cellY < 0 || cellY >= gridSize) return;

        // The guide fades towards the edges of the grid so only the part near the model stays visible
        double rad = Math.sqrt((fxc - gridSize * 0.5) * (fxc - gridSize * 0.5) + (fyc - gridSize * 0.5) * (fyc - gridSize * 0.5)) / gridSize;
        double edgeFade = 1.0 - 0.85 * smooth(clamp01((rad - 0.28) / 0.44));

        // Slab tint (light on the dark studio backdrop)
        double a = 0.07 * edgeFade;
        double r = 255, g = 255, b = 255;

        // Fade fine lines out when they would be denser than ~1 per 2 pixels (grazing angles / far zoom-out)
        double foot = Math.max(floorFootX, floorFootY) * pixelScale; // cells per GUI pixel
        double fade = clamp01(1.0 - (foot - 0.35) / 0.5);

        // Every line sits on a whole-number coordinate, so a pixel farther than a line's reach from the nearest whole
        // number on both axes (most of them) touches no line at all and the coverage maths can be skipped
        double reach = lineHalfWidth + 0.5;
        boolean nearLine = Math.abs(fxc - Math.rint(fxc)) < reach * Math.max(floorFootX, 1e-6)
                || Math.abs(fyc - Math.rint(fyc)) < reach * Math.max(floorFootY, 1e-6);
        double minor = 0, major = 0, border = 0;
        if (nearLine) {
            minor = Math.max(lineCoverage(fxc, floorFootX, 1), lineCoverage(fyc, floorFootY, 1)) * fade;
            major = Math.max(lineCoverage(fxc, floorFootX, MAJOR_LINE_EVERY), lineCoverage(fyc, floorFootY, MAJOR_LINE_EVERY));
            border = Math.max(lineCoverage(fxc, floorFootX, gridSize), lineCoverage(fyc, floorFootY, gridSize));
        }

        // Pale blue-grey lines: faint every cell, clearer every few cells, strongest on the border
        double la = minor * 0.20 * edgeFade;
        if (la > 0) { double na = a + la * (1 - a); r = (r * a * (1 - la) + 0x9F * la) / na; g = (g * a * (1 - la) + 0xB1 * la) / na; b = (b * a * (1 - la) + 0xD2 * la) / na; a = na; }
        la = major * 0.32 * edgeFade;
        if (la > 0) { double na = a + la * (1 - a); r = (r * a * (1 - la) + 0xBE * la) / na; g = (g * a * (1 - la) + 0xCB * la) / na; b = (b * a * (1 - la) + 0xE6 * la) / na; a = na; }
        la = border * 0.55 * Math.max(edgeFade, 0.45);
        if (la > 0) { double na = a + la * (1 - a); r = (r * a * (1 - la) + 0xE2 * la) / na; g = (g * a * (1 - la) + 0xE9 * la) / na; b = (b * a * (1 - la) + 0xF6 * la) / na; a = na; }

        if (highlight != null && highlight.floor() && highlight.x() == cellX && highlight.y() == cellY) {
            hlApplied = true;
            double k = 0.55; // tint the hovered cell of the editing layer
            r = r * (1 - k) + 255 * k; g = g * (1 - k) + 255 * k; b = b * (1 - k) + 255 * k;
            a = Math.max(a, 0.7);
        }

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
