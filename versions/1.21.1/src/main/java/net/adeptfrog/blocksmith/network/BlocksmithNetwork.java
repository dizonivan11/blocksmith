package net.adeptfrog.blocksmith.network;

import net.adeptfrog.blocksmith.Blocksmith;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Consumer;

/**
 * Blocksmith's network channel: every payload, its codec and its handler. Forge on Minecraft 1.21.1.
 * <p>
 * Packets the client receives are handed to the {@code on...} hooks, which the client sets at startup, so this
 * class never names a client-only class.
 */
public final class BlocksmithNetwork {
    private static final int PROTOCOL_VERSION = 1;

    /** Client handlers, set by the client at startup; they run on the client's main thread. */
    public static volatile Consumer<StartVortexPayload> onStartVortex = payload -> {};
    public static volatile Consumer<ServerSettingsPayload> onServerSettings = payload -> {};

    private static Channel<CustomPacketPayload> channel;

    private BlocksmithNetwork() {
    }

    public static void register() {
        channel = ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath(Blocksmith.MOD_ID, "main"))
                .networkProtocolVersion(PROTOCOL_VERSION)
                .payloadChannel()
                .play()
                .serverbound()
                .add(SaveWeaponVoxelsPayload.TYPE, SaveWeaponVoxelsPayload.STREAM_CODEC, BlocksmithNetwork::handleSaveWeapon)
                .clientbound()
                .add(StartVortexPayload.TYPE, StartVortexPayload.STREAM_CODEC,
                        (payload, context) -> handleOnClient(context, () -> onStartVortex.accept(payload)))
                .add(ServerSettingsPayload.TYPE, ServerSettingsPayload.STREAM_CODEC,
                        (payload, context) -> handleOnClient(context, () -> onServerSettings.accept(payload)))
                .build();
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        channel.send(payload, PacketDistributor.PLAYER.with(player));
    }

    public static void sendToServer(CustomPacketPayload payload) {
        channel.send(payload, PacketDistributor.SERVER.noArg());
    }

    private static void handleSaveWeapon(SaveWeaponVoxelsPayload payload, CustomPayloadEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player != null) context.enqueueWork(() -> SaveWeaponVoxelsPayload.handle(payload, player));
        context.setPacketHandled(true);
    }

    private static void handleOnClient(CustomPayloadEvent.Context context, Runnable work) {
        context.enqueueWork(work);
        context.setPacketHandled(true);
    }
}
