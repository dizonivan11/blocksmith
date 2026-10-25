package net.adeptfrog.blocksmith.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.adeptfrog.blocksmith.component.ModDataComponents;
import net.adeptfrog.blocksmith.data.WeaponOffset;
import net.adeptfrog.blocksmith.data.WeaponVoxel;
import net.adeptfrog.blocksmith.item.ModularBowItem;
import net.adeptfrog.blocksmith.item.ModularSwordItem;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Minecraft 1.21.1: a Fabric builtin item renderer for items whose model has "parent": "builtin/entity". The model's
 * display transforms are applied first and the pose is moved by -0.5, so the weapon is drawn in the unit cube.
 * Geometry is in {@link VoxelMesh}.
 */
public class ModularWeaponSpecialRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {

    private static final RenderType VOXEL_RENDER_TYPE = RenderType.entityCutout(
            ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png")
    );

    @Override
    public void render(ItemStack stack, ItemDisplayContext mode, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        List<WeaponVoxel> voxels = null;
        if (stack.getItem() instanceof ModularSwordItem) {
            voxels = ModularSwordItem.getVoxels(stack);
        } else if (stack.getItem() instanceof ModularBowItem) {
            voxels = ModularBowItem.getVoxels(stack);
        }
        if (voxels == null || voxels.isEmpty()) return;

        WeaponOffset offset = stack.getOrDefault(ModDataComponents.WEAPON_OFFSET, WeaponOffset.ZERO);
        // Apply grip offset ONLY in-hand / world, keep 0 in inventory slots
        boolean isGui = mode == ItemDisplayContext.GUI;
        VoxelMesh.emit(poseStack.last().pose(), buffers.getBuffer(VOXEL_RENDER_TYPE), voxels,
                isGui ? 0 : offset.x(), isGui ? 0 : offset.y(), light, overlay);
    }
}
