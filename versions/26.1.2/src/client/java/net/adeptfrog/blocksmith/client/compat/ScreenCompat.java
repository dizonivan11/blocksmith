package net.adeptfrog.blocksmith.client.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.util.Util;

import java.io.File;
import java.net.URI;
import java.util.List;

/** Client calls whose signature differs between Minecraft versions. Minecraft 26.1 / 26.2 version. */
public final class ScreenCompat {
    private ScreenCompat() {
    }

    /** Draws a tooltip made of already-built components at the given position. */
    public static void tooltip(Gfx g, Font font, List<ClientTooltipComponent> lines, int x, int y) {
        g.raw().tooltip(font, lines, x, y, DefaultTooltipPositioner.INSTANCE, null);
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
