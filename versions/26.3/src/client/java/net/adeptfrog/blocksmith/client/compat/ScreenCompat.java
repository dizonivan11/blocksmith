package net.adeptfrog.blocksmith.client.compat;

import com.mojang.blaze3d.Blaze3D;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;

import java.io.File;
import java.net.URI;
import java.util.List;

/**
 * Client calls whose signature differs between Minecraft versions. Minecraft 26.3 version: tooltip() takes an
 * extra "space after first line" flag, and Util.OS#openFile/openUri were replaced by Blaze3D.openPath and the
 * vanilla link confirmation screen.
 */
public final class ScreenCompat {
    private ScreenCompat() {
    }

    /** Draws a tooltip made of already-built components at the given position. */
    public static void tooltip(Gfx g, Font font, List<ClientTooltipComponent> lines, int x, int y) {
        g.raw().tooltip(font, lines, x, y, DefaultTooltipPositioner.INSTANCE, null, false);
    }

    /** Opens a folder in the system file manager. */
    public static void openFolder(File folder) {
        Blaze3D.openPath(folder.toPath());
    }

    /** Opens a web link in the system browser, after vanilla's "open this link?" confirmation. */
    public static void openLink(Screen current, URI uri) {
        ConfirmLinkScreen.confirmLinkNow(current, uri);
    }
}
