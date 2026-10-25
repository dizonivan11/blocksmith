package net.adeptfrog.blocksmith.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/** Minecraft 1.21.1 / 26.1 / 26.2: blocks declare a codec. */
public class WeaponTableBlock extends AbstractWeaponTableBlock {
    public static final MapCodec<WeaponTableBlock> CODEC = simpleCodec(WeaponTableBlock::new);

    public WeaponTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }
}
