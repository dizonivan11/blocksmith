package net.adeptfrog.blocksmith.mixin;

import net.adeptfrog.blocksmith.item.StatsCheckedStack;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Gives every stack the in-memory field behind {@link StatsCheckedStack}. Same class on every Minecraft version. */
@Mixin(ItemStack.class)
public abstract class ItemStackStatsMixin implements StatsCheckedStack {
	@Unique
	private int blocksmith$statsVersion;

	@Override
	public int blocksmith$statsVersion() {
		return this.blocksmith$statsVersion;
	}

	@Override
	public void blocksmith$setStatsVersion(int version) {
		this.blocksmith$statsVersion = version;
	}
}
