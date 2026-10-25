package net.adeptfrog.blocksmith.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Minecraft 26.x: releaseUsing reports whether the item was used. The shared bow implements {@link #release}. */
public abstract class BowItemBase extends BowItem {
    protected BowItemBase(Properties properties) {
        super(properties);
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        return release(stack, level, entity, timeLeft);
    }

    /** Called when the player lets go of the bow; returns true if an arrow was shot. */
    protected abstract boolean release(ItemStack stack, Level level, LivingEntity entity, int timeLeft);
}
