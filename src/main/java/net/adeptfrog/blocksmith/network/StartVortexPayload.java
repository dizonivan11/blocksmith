package net.adeptfrog.blocksmith.network;

import net.adeptfrog.blocksmith.compat.McCompat;
import net.adeptfrog.blocksmith.Blocksmith;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record StartVortexPayload(double x, double y, double z, int duration) implements CustomPacketPayload {
    public static final Type<StartVortexPayload> TYPE = McCompat.payloadType("start_vortex");

    public static final StreamCodec<RegistryFriendlyByteBuf, StartVortexPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, StartVortexPayload::x,
            ByteBufCodecs.DOUBLE, StartVortexPayload::y,
            ByteBufCodecs.DOUBLE, StartVortexPayload::z,
            ByteBufCodecs.VAR_INT, StartVortexPayload::duration,
            StartVortexPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}