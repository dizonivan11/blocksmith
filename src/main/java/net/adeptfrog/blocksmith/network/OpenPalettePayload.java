package net.adeptfrog.blocksmith.network;

import net.adeptfrog.blocksmith.Blocksmith;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/** Server to client: open the material / shade palette screen. */
public record OpenPalettePayload() implements CustomPacketPayload {
    public static final Type<OpenPalettePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Blocksmith.MOD_ID, "open_palette"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenPalettePayload> STREAM_CODEC = StreamCodec.unit(new OpenPalettePayload());

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
