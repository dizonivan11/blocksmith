package net.adeptfrog.blocksmith.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import net.adeptfrog.blocksmith.component.ModDataComponents;
import net.adeptfrog.blocksmith.data.VoxelMaterialRegistry;
import net.adeptfrog.blocksmith.data.WeaponOffset;
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
        float voxelDepth = 1.25f / 16.0f;
        float zOffset = 0.5f - (voxelDepth / 2.0f);

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

            for (WeaponVoxel voxel : state.voxels()) {
                Matrix4f voxelMatrix = new Matrix4f(baseMatrix)
                        // Translates voxels by the player's custom grip offset
                        .translate((voxel.x() + applyOffX) * voxelSize, (voxel.y() + applyOffY) * voxelSize, zOffset);

                renderSolidCube(voxelMatrix, consumer, voxelSize, voxelDepth, voxel.getColorRgb(), lightCoords, overlayCoords);
            }
        });
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(0.0f, 0.0f, 0.0f));
        output.accept(new Vector3f(1.0f, 1.0f, 1.0f));
    }

    private void renderSolidCube(Matrix4f matrix, VertexConsumer consumer, float size, float depth, int argb, int light, int overlay) {
        int a = (argb >> 24) & 0xFF;
        if (a == 0) a = 255;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        // DOWN (-Y)
        consumer.addVertex(matrix, 0, 0, 0).setColor(r, g, b, a).setUv(0, 0).setOverlay(overlay).setLight(light).setNormal(0, -1, 0);
        consumer.addVertex(matrix, size, 0, 0).setColor(r, g, b, a).setUv(1, 0).setOverlay(overlay).setLight(light).setNormal(0, -1, 0);
        consumer.addVertex(matrix, size, 0, depth).setColor(r, g, b, a).setUv(1, 1).setOverlay(overlay).setLight(light).setNormal(0, -1, 0);
        consumer.addVertex(matrix, 0, 0, depth).setColor(r, g, b, a).setUv(0, 1).setOverlay(overlay).setLight(light).setNormal(0, -1, 0);

        // UP (+Y)
        consumer.addVertex(matrix, 0, size, 0).setColor(r, g, b, a).setUv(0, 0).setOverlay(overlay).setLight(light).setNormal(0, 1, 0);
        consumer.addVertex(matrix, 0, size, depth).setColor(r, g, b, a).setUv(0, 1).setOverlay(overlay).setLight(light).setNormal(0, 1, 0);
        consumer.addVertex(matrix, size, size, depth).setColor(r, g, b, a).setUv(1, 1).setOverlay(overlay).setLight(light).setNormal(0, 1, 0);
        consumer.addVertex(matrix, size, size, 0).setColor(r, g, b, a).setUv(1, 0).setOverlay(overlay).setLight(light).setNormal(0, 1, 0);

        // NORTH (-Z)
        consumer.addVertex(matrix, 0, 0, 0).setColor(r, g, b, a).setUv(0, 0).setOverlay(overlay).setLight(light).setNormal(0, 0, -1);
        consumer.addVertex(matrix, 0, size, 0).setColor(r, g, b, a).setUv(0, 1).setOverlay(overlay).setLight(light).setNormal(0, 0, -1);
        consumer.addVertex(matrix, size, size, 0).setColor(r, g, b, a).setUv(1, 1).setOverlay(overlay).setLight(light).setNormal(0, 0, -1);
        consumer.addVertex(matrix, size, 0, 0).setColor(r, g, b, a).setUv(1, 0).setOverlay(overlay).setLight(light).setNormal(0, 0, -1);

        // SOUTH (+Z)
        consumer.addVertex(matrix, 0, 0, depth).setColor(r, g, b, a).setUv(0, 0).setOverlay(overlay).setLight(light).setNormal(0, 0, 1);
        consumer.addVertex(matrix, size, 0, depth).setColor(r, g, b, a).setUv(1, 0).setOverlay(overlay).setLight(light).setNormal(0, 0, 1);
        consumer.addVertex(matrix, size, size, depth).setColor(r, g, b, a).setUv(1, 1).setOverlay(overlay).setLight(light).setNormal(0, 0, 1);
        consumer.addVertex(matrix, 0, size, depth).setColor(r, g, b, a).setUv(0, 1).setOverlay(overlay).setLight(light).setNormal(0, 0, 1);

        // WEST (-X)
        consumer.addVertex(matrix, 0, 0, 0).setColor(r, g, b, a).setUv(0, 0).setOverlay(overlay).setLight(light).setNormal(-1, 0, 0);
        consumer.addVertex(matrix, 0, 0, depth).setColor(r, g, b, a).setUv(1, 0).setOverlay(overlay).setLight(light).setNormal(-1, 0, 0);
        consumer.addVertex(matrix, 0, size, depth).setColor(r, g, b, a).setUv(1, 1).setOverlay(overlay).setLight(light).setNormal(-1, 0, 0);
        consumer.addVertex(matrix, 0, size, 0).setColor(r, g, b, a).setUv(0, 1).setOverlay(overlay).setLight(light).setNormal(-1, 0, 0);

        // EAST (+X)
        consumer.addVertex(matrix, size, 0, 0).setColor(r, g, b, a).setUv(0, 0).setOverlay(overlay).setLight(light).setNormal(1, 0, 0);
        consumer.addVertex(matrix, size, 0, depth).setColor(r, g, b, a).setUv(0, 1).setOverlay(overlay).setLight(light).setNormal(1, 0, 0);
        consumer.addVertex(matrix, size, size, depth).setColor(r, g, b, a).setUv(1, 1).setOverlay(overlay).setLight(light).setNormal(1, 0, 0);
        consumer.addVertex(matrix, size, size, 0).setColor(r, g, b, a).setUv(1, 0).setOverlay(overlay).setLight(light).setNormal(1, 0, 0);
    }
}