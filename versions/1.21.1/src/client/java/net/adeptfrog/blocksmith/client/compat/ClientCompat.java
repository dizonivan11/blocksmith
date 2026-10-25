package net.adeptfrog.blocksmith.client.compat;

import net.adeptfrog.blocksmith.Blocksmith;
import net.adeptfrog.blocksmith.client.render.ModularWeaponSpecialRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Client setup and widget calls that differ between Minecraft versions. Minecraft 1.21.1 version. */
public final class ClientCompat {
    private ClientCompat() {
    }

    /** Registers the voxel renderer for the forged weapons, whose item models use "parent": "builtin/entity". */
    public static void registerWeaponRenderer() {
        ModularWeaponSpecialRenderer renderer = new ModularWeaponSpecialRenderer();
        BuiltinItemRendererRegistry.INSTANCE.register(Blocksmith.MODULAR_SWORD, renderer);
        BuiltinItemRendererRegistry.INSTANCE.register(Blocksmith.MODULAR_BOW, renderer);
    }

    public static String playerName(Player player) {
        return player.getGameProfile().getName();
    }

    public static MultiLineEditBox multiLineEditBox(Font font, int x, int y, int width, int height, Component placeholder) {
        return new MultiLineEditBox(font, x, y, width, height, placeholder, placeholder);
    }
}
