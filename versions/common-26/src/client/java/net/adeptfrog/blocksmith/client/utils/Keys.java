package net.adeptfrog.blocksmith.client.utils;

import com.mojang.blaze3d.platform.InputConstants;

import java.lang.reflect.Field;

/**
 * Key codes that work on every supported Minecraft version.
 *
 * 26.1 and 26.2 report GLFW key codes, 26.3 reports SDL scancodes. The constants in InputConstants
 * always match what {@code KeyEvent#key()} returns, but javac would inline them at compile time, so
 * they are read by reflection at runtime. If a field is missing the value falls back to a built-in
 * table for whichever scheme is active.
 */
public final class Keys {
    private static final boolean SDL;

    static {
        int a = read("KEY_A", -1);
        SDL = a == 4; // SDL scancode for A; GLFW uses 65
    }

    public static final int TAB = k(258, 43, "KEY_TAB");
    public static final int BACKSPACE = k(259, 42, "KEY_BACKSPACE");
    public static final int DELETE = k(261, 76, "KEY_DELETE");
    public static final int RIGHT = k(262, 79, "KEY_RIGHT");
    public static final int LEFT = k(263, 80, "KEY_LEFT");
    public static final int DOWN = k(264, 81, "KEY_DOWN");
    public static final int UP = k(265, 82, "KEY_UP");
    public static final int PAGE_UP = k(266, 75, "KEY_PAGEUP", "KEY_PAGE_UP");
    public static final int PAGE_DOWN = k(267, 78, "KEY_PAGEDOWN", "KEY_PAGE_DOWN");
    public static final int LEFT_SHIFT = k(340, 225, "KEY_LSHIFT", "KEY_LEFT_SHIFT");
    public static final int RIGHT_SHIFT = k(344, 229, "KEY_RSHIFT", "KEY_RIGHT_SHIFT");
    public static final int LEFT_CONTROL = k(341, 224, "KEY_LCONTROL", "KEY_LEFT_CONTROL");
    public static final int RIGHT_CONTROL = k(345, 228, "KEY_RCONTROL", "KEY_RIGHT_CONTROL");
    public static final int LEFT_ALT = k(342, 226, "KEY_LALT", "KEY_LEFT_ALT");
    public static final int RIGHT_ALT = k(346, 230, "KEY_RALT", "KEY_RIGHT_ALT");
    public static final int LEFT_BRACKET = k(91, 47, "KEY_LBRACKET", "KEY_LEFT_BRACKET");
    public static final int RIGHT_BRACKET = k(93, 48, "KEY_RBRACKET", "KEY_RIGHT_BRACKET");

    public static final int NUM_1 = k(49, 30, "KEY_1");
    public static final int NUM_5 = k(53, 34, "KEY_5");
    public static final int KP_1 = k(321, 89, "KEY_NUMPAD1", "KEY_KP_1");
    public static final int KP_5 = k(325, 93, "KEY_NUMPAD5", "KEY_KP_5");

    public static final int A = letter('A');
    public static final int B = letter('B');
    public static final int C = letter('C');
    public static final int D = letter('D');
    public static final int G = letter('G');
    public static final int H = letter('H');
    public static final int I = letter('I');
    public static final int K = letter('K');
    public static final int M = letter('M');
    public static final int O = letter('O');
    public static final int P = letter('P');
    public static final int R = letter('R');
    public static final int S = letter('S');
    public static final int V = letter('V');
    public static final int Y = letter('Y');
    public static final int Z = letter('Z');

    private Keys() {
    }

    private static int letter(char c) {
        return k(c, 4 + (c - 'A'), "KEY_" + c);
    }

    private static int k(int glfw, int sdl, String... names) {
        for (String name : names) {
            int v = read(name, Integer.MIN_VALUE);
            if (v != Integer.MIN_VALUE) {
                return v;
            }
        }
        return SDL ? sdl : glfw;
    }

    private static int read(String name, int fallback) {
        try {
            Field f = InputConstants.class.getField(name);
            return f.getInt(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return fallback;
        }
    }
}
