package net.adeptfrog.blocksmith.data;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compact binary form of a voxel list for packets and item syncing. Multi-layer designs can hold thousands of
 * voxels, far more than a serverbound packet (32 KB) fits when each voxel is written on its own. Voxels are sorted
 * into rows, neighbouring voxels of one material and shade become a single run, and each material/shade pair is
 * written once in a table. No Minecraft dependencies, so it can be tested on its own.
 */
public final class VoxelPacking {
    private VoxelPacking() {}

    public record Raw(int x, int y, int z, String material, int shade, int shape) {
        public Raw(int x, int y, int z, String material, int shade) { this(x, y, z, material, shade, 0); }
    }

    private static final int MAX_TABLE = 1 << 12;
    private static final int MAX_ROWS = 1 << 16;
    private static final int MAX_VOXELS = 1 << 18;

    public static byte[] pack(List<Raw> voxels) {
        // One voxel per cell; a later duplicate replaces an earlier one
        Map<String, Raw> unique = new LinkedHashMap<>();
        for (Raw v : voxels) unique.put(v.x() + "," + v.y() + "," + v.z(), v);
        List<Raw> sorted = new ArrayList<>(unique.values());
        sorted.sort(Comparator.comparingInt(Raw::z).thenComparingInt(Raw::y).thenComparingInt(Raw::x));

        Map<String, Integer> table = new LinkedHashMap<>();
        for (Raw v : sorted) table.putIfAbsent(tableKey(v), table.size());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeVar(out, table.size());
        for (String key : table.keySet()) {
            String[] parts = key.split("\u0000", -1); // material, shade, shape
            byte[] name = parts[0].getBytes(StandardCharsets.UTF_8);
            writeVar(out, name.length);
            out.write(name, 0, name.length);
            writeVar(out, zigzag(Integer.parseInt(parts[1])));
            writeVar(out, parts.length > 2 ? Integer.parseInt(parts[2]) : 0);
        }

        // Rows (same z and y), each a list of runs
        List<List<Raw>> rows = new ArrayList<>();
        for (Raw v : sorted) {
            List<Raw> last = rows.isEmpty() ? null : rows.getLast();
            if (last != null && last.getFirst().z() == v.z() && last.getFirst().y() == v.y()) last.add(v);
            else {
                List<Raw> row = new ArrayList<>();
                row.add(v);
                rows.add(row);
            }
        }
        writeVar(out, rows.size());
        for (List<Raw> row : rows) {
            // Runs: consecutive x with the same table entry
            List<int[]> runs = new ArrayList<>(); // {startX, length, tableIndex}
            for (Raw v : row) {
                int idx = table.get(tableKey(v));
                int[] last = runs.isEmpty() ? null : runs.getLast();
                if (last != null && last[2] == idx && last[0] + last[1] == v.x()) last[1]++;
                else runs.add(new int[]{v.x(), 1, idx});
            }
            writeVar(out, zigzag(row.getFirst().z()));
            writeVar(out, zigzag(row.getFirst().y()));
            writeVar(out, runs.size());
            int pos = 0;
            for (int[] r : runs) {
                writeVar(out, zigzag(r[0] - pos));
                writeVar(out, r[1]);
                writeVar(out, r[2]);
                pos = r[0] + r[1];
            }
        }
        return out.toByteArray();
    }

    public static List<Raw> unpack(byte[] data) {
        int[] p = {0};
        int tableSize = readVar(data, p);
        if (tableSize < 0 || tableSize > MAX_TABLE) throw new IllegalArgumentException("Bad voxel table size " + tableSize);
        String[] names = new String[tableSize];
        int[] shades = new int[tableSize];
        int[] shapes = new int[tableSize];
        for (int i = 0; i < tableSize; i++) {
            int len = readVar(data, p);
            if (len < 0 || p[0] + len > data.length) throw new IllegalArgumentException("Bad material name length");
            names[i] = new String(data, p[0], len, StandardCharsets.UTF_8);
            p[0] += len;
            shades[i] = unzigzag(readVar(data, p));
            shapes[i] = readVar(data, p);
        }

        int rowCount = readVar(data, p);
        if (rowCount < 0 || rowCount > MAX_ROWS) throw new IllegalArgumentException("Bad voxel row count " + rowCount);
        List<Raw> out = new ArrayList<>();
        for (int r = 0; r < rowCount; r++) {
            int z = unzigzag(readVar(data, p));
            int y = unzigzag(readVar(data, p));
            int runCount = readVar(data, p);
            if (runCount < 0 || runCount > MAX_VOXELS) throw new IllegalArgumentException("Bad run count");
            int pos = 0;
            for (int i = 0; i < runCount; i++) {
                int start = pos + unzigzag(readVar(data, p));
                int len = readVar(data, p);
                int idx = readVar(data, p);
                if (len < 1 || idx < 0 || idx >= tableSize || out.size() + (long) len > MAX_VOXELS) {
                    throw new IllegalArgumentException("Bad voxel run");
                }
                for (int k = 0; k < len; k++) out.add(new Raw(start + k, y, z, names[idx], shades[idx], shapes[idx]));
                pos = start + len;
            }
        }
        return out;
    }

    private static String tableKey(Raw v) {
        return v.material() + "\u0000" + v.shade() + "\u0000" + v.shape();
    }

    private static int zigzag(int v) { return (v << 1) ^ (v >> 31); }

    private static int unzigzag(int v) { return (v >>> 1) ^ -(v & 1); }

    private static void writeVar(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private static int readVar(byte[] data, int[] p) {
        int result = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            if (p[0] >= data.length) throw new IllegalArgumentException("Truncated voxel data");
            int b = data[p[0]++];
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return result;
        }
        throw new IllegalArgumentException("Voxel number too long");
    }
}
