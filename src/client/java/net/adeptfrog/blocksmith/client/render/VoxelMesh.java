package net.adeptfrog.blocksmith.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.adeptfrog.blocksmith.data.VoxelMaterialRegistry;
import net.adeptfrog.blocksmith.data.VoxelShape;
import net.adeptfrog.blocksmith.data.WeaponVoxel;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the geometry of a forged weapon from its voxels inside the unit cube. Shared by every Minecraft version;
 * the per-version ModularWeaponSpecialRenderer only supplies the matrix and vertex consumer.
 * <p>
 * The mesh is built once per design and cached: drawing a held weapon every frame only replays the stored quads.
 * Faces between two full voxels side by side on the same layer are left out, as are faces hidden between layers.
 */
public final class VoxelMesh {
    private VoxelMesh() {
    }

    /** A built mesh: per quad 4 vertex positions (12 floats), a normal (3 floats) and a colour. */
    private record Mesh(float[] positions, float[] normals, int[] colors, int quads, float voxelSize) {
    }

    // Most-recently-used meshes, keyed by design (and rebuilt when the grid size or material settings change)
    private static final net.adeptfrog.blocksmith.data.DesignCache<Mesh> CACHE = new net.adeptfrog.blocksmith.data.DesignCache<>(16);

    /**
     * Emits every voxel. {@code offsetX/Y} is the grip offset in voxels (pass 0 for inventory icons).
     */
    public static void emit(Matrix4f baseMatrix, VertexConsumer consumer, List<WeaponVoxel> voxels,
                            int offsetX, int offsetY, int light, int overlay) {
        Mesh mesh = meshFor(voxels);
        if (mesh.quads() == 0) return;
        Matrix4f m = (offsetX == 0 && offsetY == 0) ? baseMatrix
                : new Matrix4f(baseMatrix).translate(offsetX * mesh.voxelSize(), offsetY * mesh.voxelSize(), 0f);

        float[] p = mesh.positions(), nrm = mesh.normals();
        int[] col = mesh.colors();
        for (int q = 0; q < mesh.quads(); q++) {
            int argb = col[q];
            int a = (argb >>> 24), r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
            float nx = nrm[q * 3], ny = nrm[q * 3 + 1], nz = nrm[q * 3 + 2];
            int o = q * 12;
            for (int i = 0; i < 4; i++) {
                consumer.addVertex(m, p[o + i * 3], p[o + i * 3 + 1], p[o + i * 3 + 2]).setColor(r, g, b, a)
                        .setUv(i == 0 || i == 3 ? 0 : 1, i < 2 ? 0 : 1)
                        .setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
            }
        }
    }

    private static synchronized Mesh meshFor(List<WeaponVoxel> voxels) {
        int grid = VoxelMaterialRegistry.getGridSize(); // a grid size change bumps the material version, emptying the cache
        return CACHE.get(voxels, v -> build(v, grid));
    }

    private static Mesh build(List<WeaponVoxel> voxels, int grid) {
        float voxelSize = 1.0f / grid;
        // Each layer is half as thick as the old single slab; the base layer (z = 0) stays centered
        // and the other layers stack outward from it.
        float voxelDepth = 1.25f / 32.0f; // fixed for every grid size (the 32x32 thickness)
        float baseZOffset = 0.5f - (voxelDepth / 2.0f);

        // Shape lookup so faces hidden between touching voxels are not drawn and tapered voxels know their neighbours
        Map<Long, Integer> occupied = new HashMap<>();
        for (WeaponVoxel v : voxels) occupied.put(cellKey(v.x(), v.y(), v.z()), v.shape());

        int capacity = Math.max(1, voxels.size() * 6);
        float[] pos = new float[capacity * 12];
        float[] nrm = new float[capacity * 3];
        int[] col = new int[capacity];
        int count = 0;

        float[] scratch = new float[VoxelShape.MAX_QUADS * VoxelShape.QUAD_FLOATS];
        Map<Integer, float[]> templates = new HashMap<>();
        for (WeaponVoxel voxel : voxels) {
            float[] quads = scratch;
            int x = voxel.x(), y = voxel.y(), z = voxel.z();
            int shape = voxel.shape();
            int hide = 0;
            Integer back = occupied.get(cellKey(x, y, z - 1)), front = occupied.get(cellKey(x, y, z + 1));
            if (back != null && VoxelShape.coversBack(back, shape)) hide |= VoxelShape.HIDE_BACK;
            if (front != null && VoxelShape.coversFront(front, shape)) hide |= VoxelShape.HIDE_FRONT;

            int argb = voxel.getColorRgb();
            if ((argb >>> 24) == 0) argb |= 0xFF000000;
            float tx = x * voxelSize, ty = y * voxelSize, tz = baseZOffset + z * voxelDepth;
            boolean full = shape == VoxelShape.FULL;

            // Quads depend only on the shape and which faces are hidden: built once per combination, then reused
            int tk = shape * 4 + hide;
            float[] tpl = templates.get(tk);
            if (tpl == null) {
                int cnt = VoxelShape.buildQuads(shape, hide, quads);
                tpl = java.util.Arrays.copyOf(quads, cnt * VoxelShape.QUAD_FLOATS);
                templates.put(tk, tpl);
            }
            int n = tpl.length / VoxelShape.QUAD_FLOATS;
            quads = tpl;
            for (int q = 0; q < n; q++) {
                int o = q * VoxelShape.QUAD_FLOATS;
                float nx = quads[o + 12], ny = quads[o + 13], nz = quads[o + 14];
                // A full voxel's side face against a full neighbour on the same layer can never be seen
                if (full && nz == 0f && (nx != 0f || ny != 0f)) {
                    Integer side = occupied.get(cellKey(x + Math.round(nx), y + Math.round(ny), z));
                    if (side != null && side == VoxelShape.FULL) continue;
                }
                if (count == col.length) {
                    int grow = col.length * 2;
                    pos = java.util.Arrays.copyOf(pos, grow * 12);
                    nrm = java.util.Arrays.copyOf(nrm, grow * 3);
                    col = java.util.Arrays.copyOf(col, grow);
                }
                int po = count * 12;
                for (int i = 0; i < 4; i++) {
                    pos[po + i * 3] = tx + quads[o + i * 3] * voxelSize;
                    pos[po + i * 3 + 1] = ty + quads[o + i * 3 + 1] * voxelSize;
                    pos[po + i * 3 + 2] = tz + quads[o + i * 3 + 2] * voxelDepth;
                }
                nrm[count * 3] = nx;
                nrm[count * 3 + 1] = ny;
                nrm[count * 3 + 2] = nz;
                col[count] = argb;
                count++;
            }
        }
        return new Mesh(pos, nrm, col, count, voxelSize);
    }

    private static long cellKey(int x, int y, int z) {
        return ((long) (z + 8) << 48) | ((long) (x & 0xFFFFFF) << 24) | (y & 0xFFFFFFL);
    }
}
