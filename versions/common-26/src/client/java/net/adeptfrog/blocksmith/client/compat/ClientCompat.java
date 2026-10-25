package net.adeptfrog.blocksmith.client.compat;

import net.adeptfrog.blocksmith.Blocksmith;
import net.adeptfrog.blocksmith.client.render.ModularWeaponSpecialRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/** Client setup and widget calls that differ between Minecraft versions. Minecraft 26.x version. */
public final class ClientCompat {
    private ClientCompat() {
    }

    /** Registers the voxel renderer that the forged weapons' item models (assets/blocksmith/items) point at. */
    public static void registerWeaponRenderer() {
        SpecialModelRenderers.ID_MAPPER.put(
                Identifier.fromNamespaceAndPath(Blocksmith.MOD_ID, "modular_weapon"),
                ModularWeaponSpecialRenderer.Unbaked.MAP_CODEC
        );
    }

    public static String playerName(Player player) {
        return player.getGameProfile().name();
    }

    public static MultiLineEditBox multiLineEditBox(Font font, int x, int y, int width, int height, Component placeholder) {
        MultiLineEditBox box = new MultiLineEditBox.Builder().build(font, width, height, placeholder);
        box.setPosition(x, y);
        return box;
    }
}
