package net.adeptfrog.blocksmith.client.utils;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Small adapters for client APIs that differ between Minecraft 26.1, 26.2 and 26.3, resolved by
 * reflection so the same code compiles and runs against all of them.
 *
 * - Opening a screen: {@code Minecraft#setScreen} (26.1) became {@code Minecraft#gui#setScreen} (26.2+).
 * - Held-key test: {@code InputConstants.isKeyDown(Window, int)} (26.1/26.2) became {@code isKeyDown(int)} (26.3).
 */
public final class InputCompat {
    private static final Method MC_SET_SCREEN = find(Minecraft.class, "setScreen");
    private static final Field MC_GUI = field(Minecraft.class, "gui");
    private static Method guiSetScreen;
    private static boolean guiResolved;

    private static final Method IS_KEY_DOWN = findIsKeyDown();

    private InputCompat() {
    }

    /** Opens a screen (or closes the current one with null). */
    public static void openScreen(Minecraft mc, Screen screen) {
        try {
            if (MC_SET_SCREEN != null) {
                MC_SET_SCREEN.invoke(mc, screen);
                return;
            }
            if (MC_GUI != null) {
                Object gui = MC_GUI.get(mc);
                if (!guiResolved) {
                    guiSetScreen = find(gui.getClass(), "setScreen");
                    guiResolved = true;
                }
                if (guiSetScreen != null) {
                    guiSetScreen.invoke(gui, screen);
                    return;
                }
            }
            throw new IllegalStateException("No setScreen method found on Minecraft or Gui");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not open screen", e);
        }
    }

    public static void openScreen(Screen screen) {
        openScreen(Minecraft.getInstance(), screen);
    }

    /** Whether a key is currently held. {@code key} must come from {@link Keys}. */
    public static boolean isKeyDown(int key) {
        if (IS_KEY_DOWN == null) {
            return false;
        }
        try {
            Class<?>[] p = IS_KEY_DOWN.getParameterTypes();
            if (p.length == 1) {
                return (boolean) IS_KEY_DOWN.invoke(null, key);
            }
            Object window = Minecraft.getInstance().getWindow();
            Object first = window;
            if (p[0] == long.class) {
                first = window.getClass().getMethod("handle").invoke(window);
            }
            return (boolean) IS_KEY_DOWN.invoke(null, first, key);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
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

    private static Method findIsKeyDown() {
        for (Method m : InputConstants.class.getMethods()) {
            if (m.getName().equals("isKeyDown") && m.getReturnType() == boolean.class
                    && m.getParameterCount() >= 1 && m.getParameterCount() <= 2
                    && m.getParameterTypes()[m.getParameterCount() - 1] == int.class) {
                return m;
            }
        }
        return null;
    }

    private static Method find(Class<?> type, String name) {
        for (Method m : type.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].isAssignableFrom(Screen.class)) {
                return m;
            }
        }
        return null;
    }

    private static Field field(Class<?> type, String name) {
        try {
            Field f = type.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
