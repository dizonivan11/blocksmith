package net.adeptfrog.blocksmith.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/** Maximum durability of a forged weapon, which its materials can raise or lower. */
final class WeaponDurability {
    private WeaponDurability() {}

    /** Max durability from the base value and the materials' bonus; never below 1, which an item must have. */
    static int of(int base, long bonus) {
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, base + bonus));
    }

    /**
     * Sets the stack's max durability. Wear the weapon already has is kept below the new maximum, so lowering it
     * (a new design, Dynamic Stats or the grid size) leaves the weapon at 1 durability instead of breaking it.
     */
    static void set(ItemStack stack, int maxDamage) {
        Integer current = stack.get(DataComponents.MAX_DAMAGE);
        if (current == null || current != maxDamage) stack.set(DataComponents.MAX_DAMAGE, maxDamage);
        if (stack.getDamageValue() >= maxDamage) stack.setDamageValue(maxDamage - 1);
    }
}
