package net.adeptfrog.blocksmith.client.compat;

import net.adeptfrog.blocksmith.client.mixin.GuiGraphicsAccessor;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;

import java.io.File;
import java.net.URI;
import java.util.List;

/** Tooltip, folder and link calls that differ between Minecraft versions. Minecraft 1.21.1 version. */
public final class ScreenCompat {
    private ScreenCompat() {
    }

    /** Draws a tooltip made of already-built components at the given position (the method is private in 1.21.1). */
    public static void tooltip(Gfx g, Font font, List<ClientTooltipComponent> lines, int x, int y) {
        ((GuiGraphicsAccessor) g.raw()).blocksmith$renderTooltipInternal(font, lines, x, y, DefaultTooltipPositioner.INSTANCE);
    }

    /** Opens a folder in the system file manager. */
    public static void openFolder(File folder) {
        Util.getPlatform().openFile(folder);
    }

    /** Opens a web link in the system browser. */
    public static void openLink(Screen current, URI uri) {
        Util.getPlatform().openUri(uri);
    }
}
