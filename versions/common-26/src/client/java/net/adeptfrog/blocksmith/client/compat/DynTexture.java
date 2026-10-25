package net.adeptfrog.blocksmith.client.compat;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** A texture the screens paint pixel by pixel (ARGB) and draw with {@link Gfx#blit}. Minecraft 26.x version. */
public final class DynTexture {
    private final NativeImage image;
    private final DynamicTexture texture;
    private final TexId id;

    /** Creates and registers a width x height texture named blocksmith:name. */
    public DynTexture(String name, int width, int height) {
        this.image = new NativeImage(width, height, true);
        this.texture = new DynamicTexture(() -> "blocksmith_" + name, this.image);
        this.id = TexId.mod(name);
        Minecraft.getInstance().getTextureManager().register(this.id.raw(), this.texture);
    }

    public void setPixel(int x, int y, int argb) {
        this.image.setPixel(x, y, argb);
    }

    /** Sends the painted pixels to the GPU. */
    public void upload() {
        this.texture.upload();
    }

    public TexId id() {
        return id;
    }

    private static java.lang.reflect.Method getTexture, glId;
    private static boolean notGl; // the GPU textures have no GL name (another backend): stop looking

    /**
     * OpenGL name of this texture, or -1 when Minecraft is not drawing with OpenGL (26.3 can also use Vulkan). Looked
     * up reflectively so the same code works with 26.1's and 26.3's GL texture classes.
     */
    public int glId() {
        if (notGl) return -1;
        try {
            if (getTexture == null) {
                getTexture = this.texture.getClass().getMethod("getTexture");
                getTexture.setAccessible(true);
            }
            Object gpu = getTexture.invoke(this.texture);
            if (gpu == null) return -1;
            if (glId == null || !glId.getDeclaringClass().isInstance(gpu)) {
                glId = gpu.getClass().getMethod("glId");
                glId.setAccessible(true);
            }
            return (Integer) glId.invoke(gpu);
        } catch (Throwable e) {
            notGl = true;
            return -1;
        }
    }

    /** Frees the texture; the object must not be used afterwards. */
    public void close() {
        this.texture.close();
        Minecraft.getInstance().getTextureManager().release(this.id.raw());
    }
}
