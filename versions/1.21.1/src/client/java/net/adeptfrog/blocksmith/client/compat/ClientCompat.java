package net.adeptfrog.blocksmith.client.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Client setup and widget calls that differ between Minecraft versions. Minecraft 1.21.1 version. */
public final class ClientCompat {
    private ClientCompat() {
    }

    /**
     * Nothing to register on 1.21.1 Forge: the forged weapons' item models use "parent": "builtin/entity", and the
     * items hand Forge their renderer themselves (SwordItemBase / BowItemBase#initializeClient).
     */
    public static void registerWeaponRenderer() {
    }

    public static String playerName(Player player) {
        return player.getGameProfile().getName();
    }

    public static MultiLineEditBox multiLineEditBox(Font font, int x, int y, int width, int height, Component placeholder) {
        return new MultiLineEditBox(font, x, y, width, height, placeholder, placeholder);
    }
}
