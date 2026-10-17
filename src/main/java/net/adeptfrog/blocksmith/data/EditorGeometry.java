package net.adeptfrog.blocksmith.data;

import java.util.ArrayList;
import java.util.List;

/** Pure 2D helpers for the layer editor's drawing tools and the image importer (no Minecraft dependencies). */
public final class EditorGeometry {
    private EditorGeometry() {}

    /** Answers a yes/no question about a cell of the grid. */
    public interface Cell {
        boolean test(int x, int y);
    }

    public static final String[] PROFILE_NAMES = {"Flat", "Tapered", "Thick centre"};
    /** Deepest erosion depth any profile asks for (thick centre, two layers out). */
    public static final int MAX_DEPTH = 5;

    /** Cells on the straight line between two cells, both included (Bresenham). */
    public static List<int[]> line(int x0, int y0, int x1, int y1) {
        List<int[]> out = new ArrayList<>();
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int x = x0, y = y0;
        while (true) {
            out.add(new int[]{x, y});
            if (x == x1 && y == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x += sx; }
            if (e2 <= dx) { err += dx; y += sy; }
        }
        return out;
    }

    /** Cells of the rectangle spanned by two corner cells, filled or only its border. */
    public static List<int[]> rect(int x0, int y0, int x1, int y1, boolean filled) {
        int minX = Math.min(x0, x1), maxX = Math.max(x0, x1);
        int minY = Math.min(y0, y1), maxY = Math.max(y0, y1);
        List<int[]> out = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (filled || x == minX || x == maxX || y == minY || y == maxY) out.add(new int[]{x, y});
            }
        }
        return out;
    }

    /** 4-connected flood fill from a start cell over cells for which {@code same} holds, inside an n x n grid. */
    public static List<int[]> flood(int n, int sx, int sy, Cell same) {
        List<int[]> out = new ArrayList<>();
        if (sx < 0 || sy < 0 || sx >= n || sy >= n || !same.test(sx, sy)) return out;
        boolean[] seen = new boolean[n * n];
        int[] stack = new int[n * n];
        int sp = 0;
        stack[sp++] = sx + sy * n;
        seen[sx + sy * n] = true;
        while (sp > 0) {
            int c = stack[--sp];
            int x = c % n, y = c / n;
            out.add(new int[]{x, y});
            int[][] nb = {{x + 1, y}, {x - 1, y}, {x, y + 1}, {x, y - 1}};
            for (int[] p : nb) {
                if (p[0] < 0 || p[1] < 0 || p[0] >= n || p[1] >= n) continue;
                int idx = p[0] + p[1] * n;
                if (seen[idx] || !same.test(p[0], p[1])) continue;
                seen[idx] = true;
                stack[sp++] = idx;
            }
        }
        return out;
    }

    /**
     * How far inside a shape a cell is: 1 for a cell on the edge (a neighbour, even diagonally, is empty),
     * 2 one cell further in, and so on up to {@code cap}. 0 when the cell itself is empty.
     */
    public static int depth(Cell occupied, int x, int y, int cap) {
        if (!occupied.test(x, y)) return 0;
        int k = 1;
        while (k < cap) {
            int r = k; // check the full square of radius k around the cell
            for (int yy = y - r; yy <= y + r; yy++) {
                for (int xx = x - r; xx <= x + r; xx++) {
                    if (!occupied.test(xx, yy)) return k;
                }
            }
            k++;
        }
        return cap;
    }

    /** Whether a cell of the given depth is extruded onto the layer {@code d} steps (d >= 1) from the drawn one. */
    public static boolean extrudes(int profile, int d, int depth) {
        return switch (profile) {
            case 1 -> depth >= d + 1;       // tapered: each layer out is one cell smaller all round
            case 2 -> depth >= 2 * d + 1;   // thick centre: thin edges, fat middle
            default -> depth >= 1;          // flat: same footprint on every layer
        };
    }
}
