package net.adeptfrog.blocksmith.client.utils;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Screen opening and held-key checks, kept behind one class so the rest of the code matches other versions. 1.21.1 version. */
public final class InputCompat {
    private InputCompat() {
    }

    /** Opens a screen (or closes the current one with null). */
    public static void openScreen(Minecraft mc, Screen screen) {
        mc.setScreen(screen);
    }

    public static void openScreen(Screen screen) {
        openScreen(Minecraft.getInstance(), screen);
    }

    /** Whether a key is currently held. {@code key} must come from {@link Keys}. */
    public static boolean isKeyDown(int key) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key);
    }

    public static boolean ctrlDown() {
        return isKeyDown(Keys.LEFT_CONTROL) || isKeyDown(Keys.RIGHT_CONTROL);
    }

    public static boolean shiftDown() {
        return isKeyDown(Keys.LEFT_SHIFT) || isKeyDown(Keys.RIGHT_SHIFT);
    }

    public static boolean altDown() {
        return isKeyDown(Keys.LEFT_ALT) || isKeyDown(Keys.RIGHT_ALT);
    }
}
