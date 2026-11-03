package net.adeptfrog.blocksmith.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Minecraft 26.x: sword behaviour comes from item properties (see McCompat.swordProperties), so this is a plain item. */
public abstract class SwordItemBase extends Item {
    protected SwordItemBase(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        serverInventoryTick(stack);
    }

    /** Called every tick on the server while the stack is in an inventory. */
    protected void serverInventoryTick(ItemStack stack) {}
}
