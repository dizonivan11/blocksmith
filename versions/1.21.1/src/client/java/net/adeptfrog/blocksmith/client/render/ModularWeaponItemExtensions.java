package net.adeptfrog.blocksmith.client.render;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** Minecraft 1.21.1: gives the forged weapons their voxel renderer (see the items' initializeClient). */
public final class ModularWeaponItemExtensions implements IClientItemExtensions {
    public static final ModularWeaponItemExtensions INSTANCE = new ModularWeaponItemExtensions();

    // Created on first use: the renderer needs the client's model sets, which are not ready while items register
    private ModularWeaponSpecialRenderer renderer;

    private ModularWeaponItemExtensions() {
    }

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        if (renderer == null) renderer = new ModularWeaponSpecialRenderer();
        return renderer;
    }
}
