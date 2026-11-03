package net.adeptfrog.blocksmith.item;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;

/** Minecraft 1.21.1: a netherite SwordItem, which gives cobweb mining, sweeping and the netherite enchantability. */
public abstract class SwordItemBase extends SwordItem {
    protected SwordItemBase(Properties properties) {
        super(Tiers.NETHERITE, properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!level.isClientSide()) serverInventoryTick(stack);
    }

    /** Called every tick on the server while the stack is in an inventory. */
    protected void serverInventoryTick(ItemStack stack) {}
}
