package net.adeptfrog.blocksmith.component;

import net.adeptfrog.blocksmith.compat.McCompat;
import com.mojang.serialization.Codec;
import net.adeptfrog.blocksmith.Blocksmith;
import net.adeptfrog.blocksmith.data.WeaponOffset;
import net.adeptfrog.blocksmith.data.WeaponVoxel;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

import java.util.List;

public class ModDataComponents {
    public static final DataComponentType<List<WeaponVoxel>> WEAPON_VOXELS = McCompat.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            "weapon_voxels",
            DataComponentType.<List<WeaponVoxel>>builder()
                    .persistent(Codec.list(WeaponVoxel.CODEC))
                    .networkSynchronized(WeaponVoxel.LIST_STREAM_CODEC)
                    .build()
    );

    public static final DataComponentType<WeaponOffset> WEAPON_OFFSET = McCompat.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            "weapon_offset",
            DataComponentType.<WeaponOffset>builder()
                    .persistent(WeaponOffset.CODEC)
                    .networkSynchronized(WeaponOffset.STREAM_CODEC)
                    .build()
    );

    public static void initialize() {}
}