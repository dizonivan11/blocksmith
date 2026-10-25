package net.adeptfrog.blocksmith.client.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;

/**
 * The drawing calls the screens use, so shared code never names the version's GUI graphics class.
 * Minecraft 1.21.1 version: wraps GuiGraphics and evens out three differences from 26.x:
 * item icons are drawn with depth (cleared afterwards so later draws stay on top), blit needs blending switched
 * on for transparent pixels, and scissor rectangles ignore the pose (the translation / scale is applied here).
 */
public final class Gfx {
    private final GuiGraphics g;
    // Current 2D transform, tracked for scissor rectangles: screen = local * scale + translation
    private float tx, ty, sx = 1f, sy = 1f;
    private final ArrayDeque<float[]> stack = new ArrayDeque<>();

    public Gfx(GuiGraphics g) {
        this.g = g;
    }

    public GuiGraphics raw() {
        return g;
    }

    public void fill(int x1, int y1, int x2, int y2, int argb) {
        g.fill(x1, y1, x2, y2, argb);
    }

    public void text(Font font, String text, int x, int y, int argb, boolean shadow) {
        g.drawString(font, text, x, y, argb, shadow);
    }

    public void text(Font font, Component text, int x, int y, int argb, boolean shadow) {
        g.drawString(font, text, x, y, argb, shadow);
    }

    public void text(Font font, FormattedCharSequence text, int x, int y, int argb, boolean shadow) {
        g.drawString(font, text, x, y, argb, shadow);
    }

    /** Draws a width x height part of a texWidth x texHeight texture, starting at (u, v), unscaled. */
    public void blit(TexId texture, int x, int y, float u, float v, int width, int height, int texWidth, int texHeight) {
        RenderSystem.enableBlend();
        g.blit(texture.raw(), x, y, u, v, width, height, texWidth, texHeight);
        RenderSystem.disableBlend();
    }

    /** Draws a regionWidth x regionHeight part of the texture, starting at (u, v), stretched to width x height. */
    public void blit(TexId texture, int x, int y, float u, float v, int width, int height,
                     int regionWidth, int regionHeight, int texWidth, int texHeight) {
        RenderSystem.enableBlend();
        g.blit(texture.raw(), x, y, width, height, u, v, regionWidth, regionHeight, texWidth, texHeight);
        RenderSystem.disableBlend();
    }

    /** Draws a GUI-atlas sprite. */
    public void sprite(TexId sprite, int x, int y, int width, int height) {
        g.blitSprite(sprite.raw(), x, y, width, height);
    }

    /** Draws an item icon (16x16) with no count or durability bar. */
    public void item(ItemStack stack, int x, int y) {
        g.renderFakeItem(stack, x, y);
        g.flush();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    public void pushPose() {
        g.pose().pushPose();
        stack.push(new float[]{tx, ty, sx, sy});
    }

    public void popPose() {
        g.pose().popPose();
        float[] s = stack.pop();
        tx = s[0]; ty = s[1]; sx = s[2]; sy = s[3];
    }

    public void translate(float x, float y) {
        g.pose().translate(x, y, 0f);
        tx += x * sx;
        ty += y * sy;
    }

    public void scale(float x, float y) {
        g.pose().scale(x, y, 1f);
        sx *= x;
        sy *= y;
    }

    /** Clips later drawing to the rectangle, in the current (translated / scaled) coordinates. */
    public void enableScissor(int x1, int y1, int x2, int y2) {
        g.enableScissor((int) Math.floor(x1 * sx + tx), (int) Math.floor(y1 * sy + ty),
                (int) Math.ceil(x2 * sx + tx), (int) Math.ceil(y2 * sy + ty));
    }

    public void disableScissor() {
        g.disableScissor();
    }
}
