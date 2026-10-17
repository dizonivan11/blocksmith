package net.adeptfrog.blocksmith.network;

import net.adeptfrog.blocksmith.Blocksmith;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/** Client to server: leave the Dimensional Editor, keeping ({@link #SAVE_AND_EXIT}) or dropping the changes. */
public record DimensionalActionPayload(int action) implements CustomPacketPayload {
    public static final int SAVE_AND_EXIT = 0;
    public static final int DISCARD_AND_EXIT = 1;

    public static final Type<DimensionalActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Blocksmith.MOD_ID, "dimensional_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DimensionalActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DimensionalActionPayload::action,
            DimensionalActionPayload::new
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
