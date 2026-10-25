package net.adeptfrog.blocksmith.item;

import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;

/** Minecraft 1.21.1: a netherite SwordItem, which gives cobweb mining, sweeping and the netherite enchantability. */
public abstract class SwordItemBase extends SwordItem {
    protected SwordItemBase(Properties properties) {
        super(Tiers.NETHERITE, properties);
    }
}
