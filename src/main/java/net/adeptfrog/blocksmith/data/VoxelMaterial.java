package net.adeptfrog.blocksmith.data;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public enum VoxelMaterial implements StringRepresentable {
    IRON("iron", 0.01f, 0.0f, 3, new int[]{
            0xFF707070, // 0: Darkest
            0xFF9E9E9E, // 1: Dark
            0xFFD8D8D8, // 2: Base
            0xFFEEEEEE, // 3: Light
            0xFFFFFFFF  // 4: Lightest
    }, Items.IRON_INGOT),

    EMERALD("emerald", 0.01f, 0.001f, 2, new int[]{
            0xFF0A371B, // 0: Darkest
            0xFF0E5B2C, // 1: Dark
            0xFF179048, // 2: Base
            0xFF39D375, // 3: Light
            0xFF82F2AE  // 4: Lightest
    }, Items.EMERALD),

    GOLD("gold", 0.02f, 0.005f, 1, new int[]{
            0xFF9E6F00, // 0: Darkest
            0xFFCCA010, // 1: Dark
            0xFFFFE135, // 2: Base
            0xFFFFF070, // 3: Light
            0xFFFFFFB8  // 4: Lightest
    }, Items.GOLD_INGOT),

    AMETHYST("amethyst", 0.03f, 0.0015f, 4, new int[]{
            0xFF2A1138, // 0: Darkest
            0xFF4D1C68, // 1: Dark
            0xFF8A38B0, // 2: Base
            0xFFB762D8, // 3: Light
            0xFFE3A6FF  // 4: Lightest
    }, Items.AMETHYST_SHARD),

    DIAMOND("diamond", 0.04f, 0.002f, 6, new int[]{
            0xFF1B7A72, // 0: Darkest
            0xFF2BB5AB, // 1: Dark
            0xFF4AEDD9, // 2: Base
            0xFF85FAF2, // 3: Light
            0xFFC7FFFF  // 4: Lightest
    }, Items.DIAMOND),

    NETHERITE("netherite", 0.06f, 0.001f, 10, new int[]{
            0xFF211A18, // 0: Darkest
            0xFF332825, // 1: Dark
            0xFF4A3C38, // 2: Base
            0xFF66534E, // 3: Light
            0xFF8A726C  // 4: Lightest
    }, Items.NETHERITE_INGOT);

    public static final Codec<VoxelMaterial> CODEC = StringRepresentable.fromEnum(VoxelMaterial::values);
    public static final StreamCodec<ByteBuf, VoxelMaterial> STREAM_CODEC = ByteBufCodecs.idMapper(
            i -> values()[i],
            VoxelMaterial::ordinal
    );

    private final String name;
    private final float bonusDamage;
    private final float bonusSpeed;
    private final int bonusDurability;
    private final int[] palette;
    private final Item iconItem;

    VoxelMaterial(String name, float bonusDamage, float bonusSpeed, int bonusDurability, int[] palette, Item iconItem) {
        this.name = name;
        this.bonusDamage = bonusDamage;
        this.bonusSpeed = bonusSpeed;
        this.bonusDurability = bonusDurability;
        this.palette = palette;
        this.iconItem = iconItem;
    }

    public float getBonusDamage() { return bonusDamage; }
    public float getBonusSpeed() { return bonusSpeed; }
    public int getBonusDurability() { return bonusDurability; }
    public int[] getPalette() { return palette; }

    public int getColorRgb(int shade) {
        int index = Math.max(0, Math.min(shade, palette.length - 1));
        return palette[index];
    }

    public Item getIconItem() { return iconItem; }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}