package net.adeptfrog.blocksmith.client.mixin;

import net.adeptfrog.blocksmith.client.compat.Gfx;
import net.adeptfrog.blocksmith.client.render.BowChargeHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the shared {@link BowChargeHud} after the HUD. Minecraft 26.2+: the HUD moved to Hud (Gui now manages screens) and is hidden via Hud#isHidden. */
@Mixin(Hud.class)
public abstract class HudMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void blocksmith$renderBowDrawHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        BowChargeHud.render(new Gfx(graphics), Minecraft.getInstance().gui.hud.isHidden());
    }
}
