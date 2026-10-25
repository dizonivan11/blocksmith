package net.adeptfrog.blocksmith.data;

import net.adeptfrog.blocksmith.compat.McCompat;
import net.minecraft.world.item.Item;

public record MaterialGroup(
        String id,
        String materials,
        int color,
        String iconItem
) {
    @Override
    public String toString() { return id; }
    public String materials() { return materials; }
    public int color() { return color; }
    public Item icon() { return McCompat.item(iconItem); }
}
