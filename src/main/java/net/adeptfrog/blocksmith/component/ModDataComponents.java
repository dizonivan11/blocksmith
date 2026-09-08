package net.adeptfrog.blocksmith.component;

import com.mojang.serialization.Codec;
import net.adeptfrog.blocksmith.Blocksmith;
import net.adeptfrog.blocksmith.data.WeaponVoxel;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;

import java.util.List;

public class ModDataComponents {
    public static final DataComponentType<List<WeaponVoxel>> WEAPON_VOXELS = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(Blocksmith.MOD_ID, "weapon_voxels"),
            DataComponentType.<List<WeaponVoxel>>builder()
                    .persistent(Codec.list(WeaponVoxel.CODEC))
                    .networkSynchronized(ByteBufCodecs.collection(java.util.ArrayList::new, WeaponVoxel.STREAM_CODEC))
                    .build()
    );

    public static void initialize() {}
}