package net.adeptfrog.blocksmith.item;

import net.adeptfrog.blocksmith.dimension.DimensionalEditor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

/**
 * The tool used inside the Dimensional Editor. Right-click a block face to place the selected voxel against it,
 * right-click air (or sneak + right-click) to open the material / shade palette. Left-click breaks voxels.
 */
public class VoxelBrushItem extends Item {
    public VoxelBrushItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NonNull InteractionResult use(@NonNull Level level, @NonNull Player player, @NonNull InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            DimensionalEditor.openPalette(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @NonNull InteractionResult useOn(@NonNull UseOnContext context) {
        if (context.getLevel().isClientSide() || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            DimensionalEditor.openPalette(player);
        } else {
            DimensionalEditor.place(player, context.getClickedPos(), context.getClickedFace());
        }
        return InteractionResult.SUCCESS;
    }
}
