package net.adeptfrog.blocksmith.network;

import net.adeptfrog.blocksmith.Blocksmith;
import net.adeptfrog.blocksmith.data.WeaponOffset;
import net.adeptfrog.blocksmith.data.WeaponVoxel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/** Client to server: start a Dimensional Editor session seeded with the design currently shown in the forging screen. */
public record EnterDimensionalEditorPayload(List<WeaponVoxel> voxels, WeaponOffset offset) implements CustomPacketPayload {
    public static final Type<EnterDimensionalEditorPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Blocksmith.MOD_ID, "enter_dimensional_editor"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EnterDimensionalEditorPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, WeaponVoxel.STREAM_CODEC), EnterDimensionalEditorPayload::voxels,
            WeaponOffset.STREAM_CODEC, EnterDimensionalEditorPayload::offset,
            EnterDimensionalEditorPayload::new
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
