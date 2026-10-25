package net.adeptfrog.blocksmith.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Minecraft 1.21.1: releaseUsing returns nothing. The shared bow implements {@link #release}. */
public abstract class BowItemBase extends BowItem {
    protected BowItemBase(Properties properties) {
        super(properties);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        release(stack, level, entity, timeLeft);
    }

    /** Called when the player lets go of the bow; returns true if an arrow was shot. */
    protected abstract boolean release(ItemStack stack, Level level, LivingEntity entity, int timeLeft);
}
