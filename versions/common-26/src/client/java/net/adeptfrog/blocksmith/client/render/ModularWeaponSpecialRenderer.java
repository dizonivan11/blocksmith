package net.adeptfrog.blocksmith.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.adeptfrog.blocksmith.component.ModDataComponents;
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
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;
import java.util.function.Consumer;

/** Minecraft 26.x: a special model renderer, referenced by assets/blocksmith/items/*.json. Geometry is in {@link VoxelMesh}. */
public class ModularWeaponSpecialRenderer implements SpecialModelRenderer<ModularWeaponSpecialRenderer.State> {

    private static final RenderType VOXEL_RENDER_TYPE = RenderTypes.entityCutout(
            Identifier.withDefaultNamespace("textures/block/white_concrete.png")
    );

    public record State(List<WeaponVoxel> voxels, int offsetX, int offsetY) {
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked<State> {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<State> bake(SpecialModelRenderer.BakingContext context) {
            return new ModularWeaponSpecialRenderer();
        }
    }

    @Override
    public State extractArgument(ItemStack stack) {
        List<WeaponVoxel> voxels = null;
        if (stack.getItem() instanceof ModularSwordItem) {
            voxels = ModularSwordItem.getVoxels(stack);
        } else if (stack.getItem() instanceof ModularBowItem) {
            voxels = ModularBowItem.getVoxels(stack);
        }
        if (voxels == null || voxels.isEmpty()) return null;

        WeaponOffset offset = stack.getOrDefault(ModDataComponents.WEAPON_OFFSET, WeaponOffset.ZERO);
        // The item's own list (not a copy): VoxelMesh caches the built mesh per list instance
        return new State(voxels, offset.x(), offset.y());
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
                       int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor) {
        if (state == null || state.voxels().isEmpty()) return;

        collector.submitCustomGeometry(poseStack, VOXEL_RENDER_TYPE, (pose, consumer) -> {
            Matrix4f baseMatrix = pose.pose();

            boolean isGui = Math.abs(baseMatrix.m01()) < 1e-4 &&
                    Math.abs(baseMatrix.m10()) < 1e-4 &&
                    Math.abs(baseMatrix.m02()) < 1e-4 &&
                    Math.abs(baseMatrix.m20()) < 1e-4 &&
                    baseMatrix.m00() > 0;

            // Apply grip offset ONLY in-hand / world, keep 0 in inventory slots
            VoxelMesh.emit(baseMatrix, consumer, state.voxels(),
                    isGui ? 0 : state.offsetX(), isGui ? 0 : state.offsetY(), lightCoords, overlayCoords);
        });
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(0.0f, 0.0f, 0.0f));
        output.accept(new Vector3f(1.0f, 1.0f, 1.0f));
    }
}
