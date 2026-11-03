package net.adeptfrog.blocksmith.network;

import net.adeptfrog.blocksmith.Blocksmith;
import net.adeptfrog.blocksmith.compat.McCompat;
import net.adeptfrog.blocksmith.data.VoxelMaterialRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * The server's whole Blocksmith config (settings, grid size and materials), sent to each player on join and again
 * when it changes, so the client's Forging Table, tooltips and charge bar match what the server enforces.
 * <p>
 * Also carries the server's default weapon stats (computed from its config at startup): a stack whose stats equal
 * them is sent without those components, and the client would otherwise fill in its own.
 */
public record ServerSettingsPayload(String config, ItemDefaults sword, ItemDefaults bow) implements CustomPacketPayload {
    public static final Type<ServerSettingsPayload> TYPE = McCompat.payloadType("server_settings");

    /** Default components of a modular weapon item. */
    public record ItemDefaults(ItemAttributeModifiers attributes, int maxDamage) {
        public static final StreamCodec<RegistryFriendlyByteBuf, ItemDefaults> STREAM_CODEC = StreamCodec.composite(
                ItemAttributeModifiers.STREAM_CODEC, ItemDefaults::attributes,
                ByteBufCodecs.VAR_INT, ItemDefaults::maxDamage,
                ItemDefaults::new
        );

        public static ItemDefaults of(Item item) {
            return new ItemDefaults(
                    item.components().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY),
                    item.components().getOrDefault(DataComponents.MAX_DAMAGE, 0)
            );
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerSettingsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(1 << 20), ServerSettingsPayload::config,
            ItemDefaults.STREAM_CODEC, ServerSettingsPayload::sword,
            ItemDefaults.STREAM_CODEC, ServerSettingsPayload::bow,
            ServerSettingsPayload::new
    );

    private static volatile MinecraftServer server;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ServerSettingsPayload current() {
        return new ServerSettingsPayload(
                VoxelMaterialRegistry.toJson(),
                ItemDefaults.of(Blocksmith.MODULAR_SWORD),
                ItemDefaults.of(Blocksmith.MODULAR_BOW)
        );
    }

    public static void register() {
        McCompat.registerClientbound(TYPE, STREAM_CODEC);

        ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> sender.sendPacket(current()));

        // A LAN host changing the config in game: tell the players already connected
        VoxelMaterialRegistry.onSettingsChanged(() -> {
            MinecraftServer s = server;
            if (s != null) s.execute(() -> {
                ServerSettingsPayload payload = current();
                for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                    ServerPlayNetworking.send(player, payload);
                }
            });
        });
    }
}
