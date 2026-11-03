package net.adeptfrog.blocksmith.item;

import net.adeptfrog.blocksmith.data.VoxelMaterialRegistry;
import net.minecraft.world.item.ItemStack;

/**
 * Added to every ItemStack (see ItemStackStatsMixin): the material config version a forged weapon's stats were last
 * checked against. It lives only in memory: a stack loaded from disk or copied starts unchecked.
 */
public interface StatsCheckedStack {
    int blocksmith$statsVersion();

    void blocksmith$setStatsVersion(int version);

    /**
     * True the first time it is asked for a stack under the current config, then false until the config changes.
     * Lets the per-tick stats refresh do its full recalculation once per weapon instead of every tick.
     */
    static boolean needsCheck(ItemStack stack) {
        StatsCheckedStack checked = (StatsCheckedStack) (Object) stack;
        int current = VoxelMaterialRegistry.version() + 1; // 0 is "never checked", which version 0 must not look like
        if (checked.blocksmith$statsVersion() == current) return false;
        checked.blocksmith$setStatsVersion(current);
        return true;
    }
}
