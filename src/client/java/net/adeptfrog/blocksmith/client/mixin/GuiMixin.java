package net.adeptfrog.blocksmith.client.mixin;

import net.adeptfrog.blocksmith.item.ModularBowItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void blocksmith$renderBowDrawHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player == null || !player.isUsingItem() || client.options.hideGui) return;

        ItemStack stack = player.getUseItem();
        if (stack.getItem() instanceof ModularBowItem) {
            int useTicks = player.getTicksUsingItem();
            float progress = Math.min(1.0F, (float) useTicks / ModularBowItem.getRequiredDrawTicks(stack));
            int centerX = client.getWindow().getGuiScaledWidth() / 2;
            int centerY = client.getWindow().getGuiScaledHeight() / 2;

            // Crosshair Charge Bar
            int barW = 32;
            int barH = 2;
            int barX = centerX - (barW / 2);
            int barY = centerY + 14;

            int fillW = (int) (barW * progress);

            // Bar Color: Gold/Green when fully charged (100%), White/Yellow while charging
            int fillColor = (progress >= 1.0F) ? 0xFF55FF55 : (progress > 0.65F ? 0xFFFFD700 : 0xFFFFFFFF);

            // Draw Background Track and Fill
            graphics.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xFF000000); // 1px black outline
            graphics.fill(barX, barY, barX + barW, barY + barH, 0x80222222); // Dark background track
            graphics.fill(barX, barY, barX + fillW, barY + barH, fillColor); // Active progress fill
        }
    }
}