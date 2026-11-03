package net.adeptfrog.blocksmith.data;

import net.adeptfrog.blocksmith.compat.McCompat;
import net.minecraft.world.item.Item;

public record VoxelMaterial(
        String id,
        float bonusDamage,
        float bonusSpeed,
        int bonusDurability,
        int[] palette,
        String iconItem
) {
    public Item getIconItem() {
        return McCompat.item(iconItem);
    }

    public int getColorRgb(int shade) {
        if (palette == null || palette.length == 0) return 0xFFFFFFFF;
        int index = Math.clamp(shade, 0, palette.length - 1);
        return palette[index];
    }

    public float getBonusDamage() { return bonusDamage; }
    public float getBonusSpeed() { return bonusSpeed; }
    public int getBonusDurability() { return bonusDurability; }
    public int[] getPalette() { return palette; }

    // Contributions to weapon stats: zero while Dynamic Stats is off
    public float statDamage() { return VoxelMaterialRegistry.isDynamicStats() ? bonusDamage : 0f; }
    public float statSpeed() { return VoxelMaterialRegistry.isDynamicStats() ? bonusSpeed : 0f; }
    public int statDurability() { return VoxelMaterialRegistry.isDynamicStats() ? bonusDurability : 0; }

    @Override
    public String toString() {
        return this.id;
    }
}