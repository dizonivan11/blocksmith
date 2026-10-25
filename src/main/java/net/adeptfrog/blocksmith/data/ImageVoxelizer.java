package net.adeptfrog.blocksmith.data;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a picture into a voxel design: removes a plain background, crops to the subject, keeps its proportions,
 * averages the pixels down to the grid, matches every cell to the closest material shade by perceived colour
 * (optionally with dithering) and can extrude the result into several layers.
 */
public final class ImageVoxelizer {
    private ImageVoxelizer() {}

    /** Import settings. {@code thickness} is 1, 3, 5, 7 or 9 layers; {@code profile} is an {@link EditorGeometry} profile. */
    public record Options(int thickness, int profile, boolean dither, boolean stretch) {
        public static final Options DEFAULT = new Options(1, 0, false, false);

        public Options withThickness(int t) { return new Options(t, profile, dither, stretch); }
        public Options withProfile(int p) { return new Options(thickness, p, dither, stretch); }
        public Options withDither(boolean d) { return new Options(thickness, profile, d, stretch); }
        public Options withStretch(boolean s) { return new Options(thickness, profile, dither, s); }
    }

    /** One voxel cell of the result: grid position, layer and index into the palette that was passed in. */
    public record Cell(int x, int y, int z, int paletteIndex) {}

    private static final int ALPHA_CUTOFF = 40;
    private static final int BACKGROUND_TOLERANCE = 36;

    /**
     * @param palette RGB colours (0xRRGGBB) the result may use
     * @param maxCells upper bound for the total voxel count; outer layers are dropped first when it is exceeded
     */
    public static List<Cell> convert(BufferedImage src, int grid, Options opt, int[] palette, int maxCells) {
        int w = src.getWidth(), h = src.getHeight();
        int[] argb = src.getRGB(0, 0, w, h, null, 0, w);

        removePlainBackground(argb, w, h);

        // Crop to the visible subject
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((argb[x + y * w] >>> 24) >= ALPHA_CUTOFF) {
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) throw new IllegalArgumentException("No visible pixels found in image!");
        int cw = maxX - minX + 1, ch = maxY - minY + 1;

        // Fit into the grid, keeping proportions unless stretching was asked for
        int tw, th;
        if (opt.stretch()) {
            tw = grid;
            th = grid;
        } else {
            double scale = Math.min(grid / (double) cw, grid / (double) ch);
            tw = Math.clamp(Math.round(cw * scale), 1, grid);
            th = Math.clamp(Math.round(ch * scale), 1, grid);
        }
        int offX = (grid - tw) / 2, offY = (grid - th) / 2;

        // Alpha-weighted area average of the source pixels under each target cell
        float[] r = new float[tw * th], g = new float[tw * th], b = new float[tw * th];
        boolean[] solid = new boolean[tw * th];
        for (int ty = 0; ty < th; ty++) {
            int sy0 = minY + ty * ch / th;
            int sy1 = Math.max(sy0 + 1, minY + (int) Math.ceil((ty + 1) * ch / (double) th));
            for (int tx = 0; tx < tw; tx++) {
                int sx0 = minX + tx * cw / tw;
                int sx1 = Math.max(sx0 + 1, minX + (int) Math.ceil((tx + 1) * cw / (double) tw));
                double sa = 0, sr = 0, sg = 0, sb = 0;
                int n = 0;
                for (int y = sy0; y < Math.min(sy1, h); y++) {
                    for (int x = sx0; x < Math.min(sx1, w); x++) {
                        int p = argb[x + y * w];
                        int a = p >>> 24;
                        sa += a;
                        sr += a * ((p >> 16) & 0xFF);
                        sg += a * ((p >> 8) & 0xFF);
                        sb += a * (p & 0xFF);
                        n++;
                    }
                }
                int i = tx + ty * tw;
                if (n == 0 || sa <= 0 || sa / (n * 255.0) < 0.5) continue;
                solid[i] = true;
                r[i] = (float) (sr / sa);
                g[i] = (float) (sg / sa);
                b[i] = (float) (sb / sa);
            }
        }

        // Match to the palette by perceived colour (CIE Lab), optionally diffusing the error to neighbours
        float[][] lab = new float[palette.length][];
        for (int i = 0; i < palette.length; i++) lab[i] = toLab((palette[i] >> 16) & 0xFF, (palette[i] >> 8) & 0xFF, palette[i] & 0xFF);

        int[] chosen = new int[tw * th];
        java.util.Arrays.fill(chosen, -1);
        for (int ty = 0; ty < th; ty++) {
            for (int tx = 0; tx < tw; tx++) {
                int i = tx + ty * tw;
                if (!solid[i]) continue;
                float cr = Math.clamp(r[i], 0f, 255f), cg = Math.clamp(g[i], 0f, 255f), cb = Math.clamp(b[i], 0f, 255f);
                int best = nearest(lab, toLab(cr, cg, cb));
                chosen[i] = best;
                if (opt.dither()) {
                    float er = cr - ((palette[best] >> 16) & 0xFF);
                    float eg = cg - ((palette[best] >> 8) & 0xFF);
                    float eb = cb - (palette[best] & 0xFF);
                    spread(r, g, b, solid, tw, th, tx + 1, ty, er, eg, eb, 7 / 16f);
                    spread(r, g, b, solid, tw, th, tx - 1, ty + 1, er, eg, eb, 3 / 16f);
                    spread(r, g, b, solid, tw, th, tx, ty + 1, er, eg, eb, 5 / 16f);
                    spread(r, g, b, solid, tw, th, tx + 1, ty + 1, er, eg, eb, 1 / 16f);
                }
            }
        }

        // Base layer
        List<Cell> out = new ArrayList<>();
        for (int ty = 0; ty < th; ty++) {
            for (int tx = 0; tx < tw; tx++) {
                int i = tx + ty * tw;
                if (chosen[i] >= 0) out.add(new Cell(offX + tx, grid - 1 - (offY + ty), 0, chosen[i]));
            }
        }

        // Extrude onto the layers around it
        int half = (Math.max(1, opt.thickness()) - 1) / 2;
        if (half > 0) {
            final int fw = tw, fh = th;
            EditorGeometry.Cell occupied = (x, y) -> x >= 0 && y >= 0 && x < fw && y < fh && chosen[x + y * fw] >= 0;
            for (int d = 1; d <= half; d++) {
                List<Cell> layer = new ArrayList<>();
                for (int ty = 0; ty < th; ty++) {
                    for (int tx = 0; tx < tw; tx++) {
                        int i = tx + ty * tw;
                        if (chosen[i] < 0) continue;
                        if (!EditorGeometry.extrudes(opt.profile(), d, EditorGeometry.depth(occupied, tx, ty, EditorGeometry.MAX_DEPTH))) continue;
                        int vy = grid - 1 - (offY + ty);
                        layer.add(new Cell(offX + tx, vy, d, chosen[i]));
                        layer.add(new Cell(offX + tx, vy, -d, chosen[i]));
                    }
                }
                if (out.size() + layer.size() > maxCells) break; // keep whole layers only
                out.addAll(layer);
            }
        }
        return out;
    }

    private static void spread(float[] r, float[] g, float[] b, boolean[] solid, int w, int h, int x, int y,
                               float er, float eg, float eb, float k) {
        if (x < 0 || y < 0 || x >= w || y >= h || !solid[x + y * w]) return;
        int i = x + y * w;
        r[i] += er * k;
        g[i] += eg * k;
        b[i] += eb * k;
    }

    private static int nearest(float[][] lab, float[] c) {
        int best = 0;
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < lab.length; i++) {
            float dl = lab[i][0] - c[0], da = lab[i][1] - c[1], db = lab[i][2] - c[2];
            float d = dl * dl + da * da + db * db;
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }

    /** sRGB (0..255) to CIE L*a*b* (D65). */
    static float[] toLab(float r, float g, float b) {
        double lr = lin(r / 255.0), lg = lin(g / 255.0), lb = lin(b / 255.0);
        double x = (0.4124564 * lr + 0.3575761 * lg + 0.1804375 * lb) / 0.95047;
        double y = 0.2126729 * lr + 0.7151522 * lg + 0.0721750 * lb;
        double z = (0.0193339 * lr + 0.1191920 * lg + 0.9503041 * lb) / 1.08883;
        double fx = f(x), fy = f(y), fz = f(z);
        return new float[]{(float) (116 * fy - 16), (float) (500 * (fx - fy)), (float) (200 * (fy - fz))};
    }

    private static double lin(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double f(double t) {
        return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116;
    }

    /**
     * Pictures without transparency usually sit on a plain backdrop. When all four corners share one colour, every
     * pixel of that colour connected to the border is made transparent so only the subject becomes voxels.
     */
    static void removePlainBackground(int[] argb, int w, int h) {
        for (int p : argb) if ((p >>> 24) < 250) return; // already has transparency

        int[] corners = {argb[0], argb[w - 1], argb[(h - 1) * w], argb[w * h - 1]};
        for (int c : corners) if (colorDist(c, corners[0]) > BACKGROUND_TOLERANCE) return;
        int bg = corners[0];

        boolean[] seen = new boolean[w * h];
        int[] stack = new int[w * h];
        int sp = 0;
        for (int x = 0; x < w; x++) {
            sp = seed(argb, seen, stack, sp, x, bg, w);
            sp = seed(argb, seen, stack, sp, x + (h - 1) * w, bg, w);
        }
        for (int y = 0; y < h; y++) {
            sp = seed(argb, seen, stack, sp, y * w, bg, w);
            sp = seed(argb, seen, stack, sp, (w - 1) + y * w, bg, w);
        }
        while (sp > 0) {
            int i = stack[--sp];
            argb[i] = 0;
            int x = i % w, y = i / w;
            if (x > 0) sp = seed(argb, seen, stack, sp, i - 1, bg, w);
            if (x < w - 1) sp = seed(argb, seen, stack, sp, i + 1, bg, w);
            if (y > 0) sp = seed(argb, seen, stack, sp, i - w, bg, w);
            if (y < h - 1) sp = seed(argb, seen, stack, sp, i + w, bg, w);
        }
    }

    private static int seed(int[] argb, boolean[] seen, int[] stack, int sp, int i, int bg, int w) {
        if (seen[i] || argb[i] == 0 || colorDist(argb[i], bg) > BACKGROUND_TOLERANCE) return sp;
        seen[i] = true;
        stack[sp++] = i;
        return sp;
    }

    private static double colorDist(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
        int dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
        int db = (a & 0xFF) - (b & 0xFF);
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }
}
