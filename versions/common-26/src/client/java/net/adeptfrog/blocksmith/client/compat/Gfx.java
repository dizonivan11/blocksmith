package net.adeptfrog.blocksmith.client.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * The drawing calls the screens use, so shared code never names the version's GUI graphics class.
 * Minecraft 26.x version: wraps GuiGraphicsExtractor; later draws always land on top of earlier ones.
 */
public final class Gfx {
    private final GuiGraphicsExtractor g;

    public Gfx(GuiGraphicsExtractor g) {
        this.g = g;
    }

    public GuiGraphicsExtractor raw() {
        return g;
    }

    public void fill(int x1, int y1, int x2, int y2, int argb) {
        g.fill(x1, y1, x2, y2, argb);
    }

    public void text(Font font, String text, int x, int y, int argb, boolean shadow) {
        g.text(font, text, x, y, argb, shadow);
    }

    public void text(Font font, Component text, int x, int y, int argb, boolean shadow) {
        g.text(font, text, x, y, argb, shadow);
    }

    public void text(Font font, FormattedCharSequence text, int x, int y, int argb, boolean shadow) {
        g.text(font, text, x, y, argb, shadow);
    }

    /** Draws a width x height part of a texWidth x texHeight texture, starting at (u, v), unscaled. */
    public void blit(TexId texture, int x, int y, float u, float v, int width, int height, int texWidth, int texHeight) {
        g.blit(RenderPipelines.GUI_TEXTURED, texture.raw(), x, y, u, v, width, height, texWidth, texHeight);
    }

    /** Draws a regionWidth x regionHeight part of the texture, starting at (u, v), stretched to width x height. */
    public void blit(TexId texture, int x, int y, float u, float v, int width, int height,
                     int regionWidth, int regionHeight, int texWidth, int texHeight) {
        g.blit(RenderPipelines.GUI_TEXTURED, texture.raw(), x, y, u, v, width, height, regionWidth, regionHeight, texWidth, texHeight);
    }

    /** Draws a GUI-atlas sprite. */
    public void sprite(TexId sprite, int x, int y, int width, int height) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite.raw(), x, y, width, height);
    }

    /** Draws an item icon (16x16) with no count or durability bar. */
    public void item(ItemStack stack, int x, int y) {
        g.fakeItem(stack, x, y);
    }

    public void pushPose() {
        g.pose().pushMatrix();
    }

    public void popPose() {
        g.pose().popMatrix();
    }

    public void translate(float x, float y) {
        g.pose().translate(x, y);
    }

    public void scale(float x, float y) {
        g.pose().scale(x, y);
    }

    /** Clips later drawing to the rectangle, in the current (translated / scaled) coordinates. */
    public void enableScissor(int x1, int y1, int x2, int y2) {
        g.enableScissor(x1, y1, x2, y2);
    }

    public void disableScissor() {
        g.disableScissor();
    }
}
