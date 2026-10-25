package net.adeptfrog.blocksmith.client.mixin;

import net.adeptfrog.blocksmith.client.compat.Gfx;
import net.adeptfrog.blocksmith.client.render.BowChargeHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the shared {@link BowChargeHud} after the HUD. Minecraft 1.21.1: the HUD is drawn by Gui#render and hidden with options.hideGui. */
@Mixin(Gui.class)
public abstract class HudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void blocksmith$renderBowDrawHud(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        BowChargeHud.render(new Gfx(graphics), Minecraft.getInstance().options.hideGui);
    }
}
