package net.adeptfrog.blocksmith.item;

import net.minecraft.world.item.Item;

/** Minecraft 26.x: sword behaviour comes from item properties (see McCompat.swordProperties), so this is a plain item. */
public abstract class SwordItemBase extends Item {
    protected SwordItemBase(Properties properties) {
        super(properties);
    }
}
