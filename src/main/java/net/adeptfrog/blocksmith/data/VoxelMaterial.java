package net.adeptfrog.blocksmith.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.NonNull;

public record VoxelMaterial(
        String id,
        float bonusDamage,
        float bonusSpeed,
        int bonusDurability,
        int[] palette,
        Identifier iconItem
) {
    public Item getIconItem() {
        return BuiltInRegistries.ITEM.getValue(iconItem);
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

    @Override
    public @NonNull String toString() {
        return this.id;
    }
}