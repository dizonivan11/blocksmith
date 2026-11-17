package net.adeptfrog.blocksmith.component;

import net.adeptfrog.blocksmith.compat.McCompat;
import net.adeptfrog.blocksmith.data.WeaponOffset;
import net.adeptfrog.blocksmith.data.WeaponVoxel;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

import java.util.List;

public class ModDataComponents {
    // Created when this class loads and registered later, in Forge's RegisterEvent (see register()).
    public static final DataComponentType<List<WeaponVoxel>> WEAPON_VOXELS = DataComponentType.<List<WeaponVoxel>>builder()
            .persistent(WeaponVoxel.LIST_CODEC)
            .networkSynchronized(WeaponVoxel.LIST_STREAM_CODEC)
            .build();

    public static final DataComponentType<WeaponOffset> WEAPON_OFFSET = DataComponentType.<WeaponOffset>builder()
            .persistent(WeaponOffset.CODEC)
            .networkSynchronized(WeaponOffset.STREAM_CODEC)
            .build();

    public static void register() {
        McCompat.register(BuiltInRegistries.DATA_COMPONENT_TYPE, "weapon_voxels", WEAPON_VOXELS);
        McCompat.register(BuiltInRegistries.DATA_COMPONENT_TYPE, "weapon_offset", WEAPON_OFFSET);
    }
}