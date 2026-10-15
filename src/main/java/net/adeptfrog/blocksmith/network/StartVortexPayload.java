package net.adeptfrog.blocksmith.network;

import net.adeptfrog.blocksmith.Blocksmith;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record StartVortexPayload(double x, double y, double z, int duration) implements CustomPacketPayload {
    public static final Type<StartVortexPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Blocksmith.MOD_ID, "start_vortex"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StartVortexPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, StartVortexPayload::x,
            ByteBufCodecs.DOUBLE, StartVortexPayload::y,
            ByteBufCodecs.DOUBLE, StartVortexPayload::z,
            ByteBufCodecs.VAR_INT, StartVortexPayload::duration,
            StartVortexPayload::new
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}