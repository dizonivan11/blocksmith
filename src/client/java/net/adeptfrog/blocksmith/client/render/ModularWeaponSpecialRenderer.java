package net.adeptfrog.blocksmith.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import net.adeptfrog.blocksmith.component.ModDataComponents;
import net.adeptfrog.blocksmith.data.VoxelMaterialRegistry;
import net.adeptfrog.blocksmith.data.WeaponOffset;
import net.adeptfrog.blocksmith.data.VoxelShape;
import net.adeptfrog.blocksmith.data.WeaponVoxel;
import net.adeptfrog.blocksmith.item.ModularBowItem;
import net.adeptfrog.blocksmith.item.ModularSwordItem;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ModularWeaponSpecialRenderer implements SpecialModelRenderer<WeaponRenderState> {

    private static final RenderType VOXEL_RENDER_TYPE = RenderTypes.entityCutout(
            Identifier.withDefaultNamespace("textures/block/white_concrete.png")
    );

    public record Unbaked() implements SpecialModelRenderer.Unbaked<WeaponRenderState> {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public @NonNull MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<WeaponRenderState> bake(SpecialModelRenderer.@NonNull BakingContext context) {
            return new ModularWeaponSpecialRenderer();
        }
    }

    @Override
    public @Nullable WeaponRenderState extractArgument(ItemStack stack) {
        List<WeaponVoxel> voxels = null;
        if (stack.getItem() instanceof ModularSwordItem) {
            voxels = ModularSwordItem.getVoxels(stack);
        } else if (stack.getItem() instanceof ModularBowItem) {
            voxels = ModularBowItem.getVoxels(stack);
        }
        if (voxels == null || voxels.isEmpty()) return null;

        WeaponOffset offset = stack.getOrDefault(ModDataComponents.WEAPON_OFFSET, WeaponOffset.ZERO);
        return new WeaponRenderState(new ArrayList<>(voxels), offset.x(), offset.y());
    }

    @Override
    public void submit(
            @Nullable WeaponRenderState state,
            @NonNull PoseStack poseStack,
            @NonNull SubmitNodeCollector collector,
            int lightCoords,
            int overlayCoords,
            boolean hasFoil,
            int outlineColor
    ) {
        if (state == null || state.voxels().isEmpty()) return;

        float resolution = (float) VoxelMaterialRegistry.getGridSize();
        float voxelSize = 1.0f / resolution;
        // Each layer is half as thick as the old single slab; the base layer (z = 0) stays centered
        // and the other layers stack outward from it.
        float voxelDepth = 1.25f / 32.0f; // fixed for every grid size (the 32x32 thickness)
        float baseZOffset = 0.5f - (voxelDepth / 2.0f);

        // Shape lookup so faces hidden between touching layers are not drawn and tapered voxels know their neighbours
        java.util.Map<Long, Integer> occupied = new java.util.HashMap<>();
        for (WeaponVoxel v : state.voxels()) occupied.put(cellKey(v.x(), v.y(), v.z()), v.shape());

        collector.submitCustomGeometry(poseStack, VOXEL_RENDER_TYPE, (pose, consumer) -> {
            Matrix4f baseMatrix = pose.pose();

            boolean isGui = Math.abs(baseMatrix.m01()) < 1e-4 &&
                    Math.abs(baseMatrix.m10()) < 1e-4 &&
                    Math.abs(baseMatrix.m02()) < 1e-4 &&
                    Math.abs(baseMatrix.m20()) < 1e-4 &&
                    baseMatrix.m00() > 0;

            // Apply grip offset ONLY in-hand / world, keep 0 in inventory slots
            int applyOffX = isGui ? 0 : state.offsetX();
            int applyOffY = isGui ? 0 : state.offsetY();

            float[] quads = new float[VoxelShape.MAX_QUADS * VoxelShape.QUAD_FLOATS];
            for (WeaponVoxel voxel : state.voxels()) {
                Matrix4f voxelMatrix = new Matrix4f(baseMatrix)
                        // Translates voxels by the player's custom grip offset
                        .translate((voxel.x() + applyOffX) * voxelSize, (voxel.y() + applyOffY) * voxelSize,
                                baseZOffset + voxel.z() * voxelDepth);

                int x = voxel.x(), y = voxel.y(), z = voxel.z();
                int free = 0, hide = 0;
                Integer back = occupied.get(cellKey(x, y, z - 1)), front = occupied.get(cellKey(x, y, z + 1));
                if (back != null && VoxelShape.covers(back, voxel.shape())) hide |= VoxelShape.HIDE_BACK;
                if (front != null && VoxelShape.covers(front, voxel.shape())) hide |= VoxelShape.HIDE_FRONT;
                if (VoxelShape.isTapered(voxel.shape())) {
                    if (!occupied.containsKey(cellKey(x - 1, y, z))) free |= VoxelShape.FREE_XM;
                    if (!occupied.containsKey(cellKey(x + 1, y, z))) free |= VoxelShape.FREE_XP;
                    if (!occupied.containsKey(cellKey(x, y - 1, z))) free |= VoxelShape.FREE_YM;
                    if (!occupied.containsKey(cellKey(x, y + 1, z))) free |= VoxelShape.FREE_YP;
                    if (back == null) free |= VoxelShape.FREE_ZM;
                    if (front == null) free |= VoxelShape.FREE_ZP;
                }

                int count = VoxelShape.buildQuads(voxel.shape(), free, hide, quads);
                renderShape(voxelMatrix, consumer, quads, count, voxelSize, voxelDepth, voxel.getColorRgb(), lightCoords, overlayCoords);
            }
        });
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(0.0f, 0.0f, 0.0f));
        output.accept(new Vector3f(1.0f, 1.0f, 1.0f));
    }

    private static long cellKey(int x, int y, int z) {
        return ((long) (z + 8) << 48) | ((long) (x & 0xFFFFFF) << 24) | (y & 0xFFFFFFL);
    }

    /** Emits the quads of one voxel (cell space, see {@link VoxelShape}) scaled to the voxel's real size. */
    private void renderShape(Matrix4f matrix, VertexConsumer consumer, float[] quads, int count, float size, float depth,
                             int argb, int light, int overlay) {
        int a = (argb >> 24) & 0xFF;
        if (a == 0) a = 255;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        for (int q = 0; q < count; q++) {
            int o = q * VoxelShape.QUAD_FLOATS;
            float nx = quads[o + 12], ny = quads[o + 13], nz = quads[o + 14];
            for (int i = 0; i < 4; i++) {
                float vx = quads[o + i * 3] * size, vy = quads[o + i * 3 + 1] * size, vz = quads[o + i * 3 + 2] * depth;
                consumer.addVertex(matrix, vx, vy, vz).setColor(r, g, b, a).setUv(i == 0 || i == 3 ? 0 : 1, i < 2 ? 0 : 1)
                        .setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
            }
        }
    }
}