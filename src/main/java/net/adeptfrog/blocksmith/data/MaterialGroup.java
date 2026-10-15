package net.adeptfrog.blocksmith.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.NonNull;

public record MaterialGroup(
        String id,
        String materials,
        int color,
        Identifier iconItem
) {
    @Override
    public @NonNull String toString() { return id; }
    public String materials() { return materials; }
    public int color() { return color; }
    public Item icon() { return BuiltInRegistries.ITEM.getValue(iconItem); }
}
