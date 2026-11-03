package net.adeptfrog.blocksmith.client;

import net.adeptfrog.blocksmith.Blocksmith;
import net.adeptfrog.blocksmith.compat.EntityCompat;
import net.adeptfrog.blocksmith.data.VoxelMaterialRegistry;
import net.adeptfrog.blocksmith.item.ModularBowItem;
import net.adeptfrog.blocksmith.item.ModularSwordItem;
import net.adeptfrog.blocksmith.network.ServerSettingsPayload;
import net.adeptfrog.blocksmith.network.ServerSettingsPayload.ItemDefaults;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Uses the config of the dedicated server the client is connected to (see {@link ServerSettingsPayload}) and goes
 * back to the local config file on disconnect.
 */
public final class ServerSettingsClient {
    /** One item default the server and this client disagree on: stacks showing ours get the server's. */
    private record Fix<T>(Item item, DataComponentType<T> type, T clientDefault, T serverDefault) {
        void apply(ItemStack stack) {
            // The same instance means the stack has no value of its own and is showing the item default
            if (stack.getItem() == item && stack.get(type) == clientDefault) stack.set(type, serverDefault);
        }
    }

    private static List<Fix<?>> fixes = List.of();

    private ServerSettingsClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ServerSettingsPayload.TYPE, (payload, context) ->
                context.client().execute(() -> apply(context.client(), payload)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ServerSettingsClient::clear));
        ClientTickEvents.END_CLIENT_TICK.register(ServerSettingsClient::tick);
    }

    private static void apply(Minecraft client, ServerSettingsPayload payload) {
        // Singleplayer and LAN hosts share the registry with their own server, so its config is already ours
        if (client.hasSingleplayerServer()) return;
        VoxelMaterialRegistry.applyServerConfig(payload.config());

        List<Fix<?>> list = new ArrayList<>();
        addFixes(list, Blocksmith.MODULAR_SWORD, payload.sword());
        addFixes(list, Blocksmith.MODULAR_BOW, payload.bow());
        fixes = list;
    }

    private static void addFixes(List<Fix<?>> list, Item item, ItemDefaults server) {
        addFix(list, item, DataComponents.ATTRIBUTE_MODIFIERS, server.attributes());
        addFix(list, item, DataComponents.MAX_DAMAGE, server.maxDamage());
    }

    private static <T> void addFix(List<Fix<?>> list, Item item, DataComponentType<T> type, T serverDefault) {
        T clientDefault = item.components().get(type);
        if (clientDefault != null && !Objects.equals(clientDefault, serverDefault)) {
            list.add(new Fix<>(item, type, clientDefault, serverDefault));
        }
    }

    private static void clear() {
        fixes = List.of();
        VoxelMaterialRegistry.clearServerConfig();
    }

    private static void tick(Minecraft client) {
        List<Fix<?>> f = fixes;
        Player player = client.player;
        if (f.isEmpty() || player == null) return;

        for (ItemStack stack : EntityCompat.mainInventory(player)) fix(stack, f);
        fix(player.getOffhandItem(), f);
    }

    private static void fix(ItemStack stack, List<Fix<?>> f) {
        if (!(stack.getItem() instanceof ModularSwordItem) && !(stack.getItem() instanceof ModularBowItem)) return;
        for (Fix<?> fix : f) fix.apply(stack);
    }
}
