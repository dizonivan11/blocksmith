package net.adeptfrog.blocksmith.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
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

public class ModularWeaponSpecialRenderer implements SpecialModelRenderer<List<WeaponVoxel>> {

    private static final RenderType VOXEL_RENDER_TYPE = RenderTypes.entityCutout(
            Identifier.withDefaultNamespace("textures/block/white_concrete.png")
    );

    public record Unbaked() implements SpecialModelRenderer.Unbaked<List<WeaponVoxel>> {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public @NonNull MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<List<WeaponVoxel>> bake(SpecialModelRenderer.@NonNull BakingContext context) {
            return new ModularWeaponSpecialRenderer();
        }
    }

    @Override
    public @Nullable List<WeaponVoxel> extractArgument(ItemStack stack) {
        List<WeaponVoxel> voxels = null;
        if (stack.getItem() instanceof ModularSwordItem) {
            voxels = ModularSwordItem.getVoxels(stack);
        } else if (stack.getItem() instanceof ModularBowItem) {
            voxels = ModularBowItem.getVoxels(stack);
        }
        return (voxels == null || voxels.isEmpty()) ? null : new ArrayList<>(voxels);
    }

    @Override
    public void submit(
            @Nullable List<WeaponVoxel> voxels,
            @NonNull PoseStack poseStack,
            @NonNull SubmitNodeCollector collector,
            int lightCoords,
            int overlayCoords,
            boolean hasFoil,
            int outlineColor
    ) {
        if (voxels == null || voxels.isEmpty()) return;

        float voxelSize = 1.0f / 24.0f;
        float voxelDepth = 1.25f / 24.0f;
        float zOffset = 0.5f - (voxelDepth / 2.0f);

        collector.submitCustomGeometry(poseStack, VOXEL_RENDER_TYPE, (pose, consumer) -> {
            Matrix4f baseMatrix = pose.pose();

            for (WeaponVoxel voxel : voxels) {
                Matrix4f voxelMatrix = new Matrix4f(baseMatrix)
                        .translate(voxel.x() * voxelSize, voxel.y() * voxelSize, zOffset);

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