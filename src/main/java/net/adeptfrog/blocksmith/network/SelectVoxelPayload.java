package net.adeptfrog.blocksmith.network;

import net.adeptfrog.blocksmith.Blocksmith;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/** Client to server: the material and shade the Voxel Brush places next. */
public record SelectVoxelPayload(String materialId, int shade) implements CustomPacketPayload {
    public static final Type<SelectVoxelPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Blocksmith.MOD_ID, "select_voxel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectVoxelPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SelectVoxelPayload::materialId,
            ByteBufCodecs.VAR_INT, SelectVoxelPayload::shade,
            SelectVoxelPayload::new
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
