package net.adeptfrog.blocksmith.client.compat;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * A texture the screens paint pixel by pixel (ARGB) and draw with {@link Gfx#blit}. Minecraft 1.21.1 version:
 * NativeImage stores ABGR, so pixels are converted on the way in.
 */
public final class DynTexture {
    private final NativeImage image;
    private final DynamicTexture texture;
    private final TexId id;

    /** Creates and registers a width x height texture named blocksmith:name. */
    public DynTexture(String name, int width, int height) {
        this.image = new NativeImage(width, height, true);
        this.texture = new DynamicTexture(this.image);
        this.id = TexId.mod(name);
        Minecraft.getInstance().getTextureManager().register(this.id.raw(), this.texture);
    }

    public void setPixel(int x, int y, int argb) {
        int abgr = (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
        this.image.setPixelRGBA(x, y, abgr);
    }

    /** Sends the painted pixels to the GPU. */
    public void upload() {
        this.texture.upload();
    }

    public TexId id() {
        return id;
    }

    /** OpenGL name of this texture. */
    public int glId() {
        return this.texture.getId();
    }

    /** Frees the texture; the object must not be used afterwards. */
    public void close() {
        this.texture.close();
        Minecraft.getInstance().getTextureManager().release(this.id.raw());
    }
}
