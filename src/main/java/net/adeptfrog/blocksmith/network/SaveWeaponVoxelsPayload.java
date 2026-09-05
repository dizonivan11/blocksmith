package net.adeptfrog.blocksmith.network;

import net.adeptfrog.blocksmith.Blocksmith;
import net.adeptfrog.blocksmith.data.WeaponVoxel;
import net.adeptfrog.blocksmith.item.ModularBowItem;
import net.adeptfrog.blocksmith.item.ModularSwordItem;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.adeptfrog.blocksmith.Blocksmith.MIN_VOXELS;
import static net.adeptfrog.blocksmith.Blocksmith.MAX_VOXELS;

public record SaveWeaponVoxelsPayload(List<WeaponVoxel> voxels) implements CustomPacketPayload {
    public static final Type<SaveWeaponVoxelsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Blocksmith.MOD_ID, "save_weapon_voxels"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveWeaponVoxelsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, WeaponVoxel.STREAM_CODEC), SaveWeaponVoxelsPayload::voxels,
            SaveWeaponVoxelsPayload::new
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> context.server().execute(() -> {
            Player player = context.player();
            ItemStack stack = player.getMainHandItem();
            boolean isModular = stack.getItem() instanceof ModularSwordItem || stack.getItem() instanceof ModularBowItem;

            if (!isModular) {
                stack = player.getOffhandItem();
                isModular = stack.getItem() instanceof ModularSwordItem || stack.getItem() instanceof ModularBowItem;
                if (!isModular) return;
            }

            List<WeaponVoxel> newVoxels = payload.voxels();

            // 1. Validate voxel limits
            if (newVoxels.size() < MIN_VOXELS || newVoxels.size() > MAX_VOXELS) {
                player.sendOverlayMessage(Component.literal("§cInvalid voxel count!"));
                player.inventoryMenu.broadcastChanges();
                return;
            }

            List<WeaponVoxel> originalVoxels = stack.getItem() instanceof ModularSwordItem
                    ? ModularSwordItem.getVoxels(stack)
                    : ModularBowItem.getVoxels(stack);

            // 2. Compute exact item diffs (Net additions & net removals)
            Map<Item, Integer> itemDiffs = new HashMap<>();

            for (WeaponVoxel v : newVoxels) {
                Item item = v.material().getIconItem();
                itemDiffs.merge(item, 1, Integer::sum);
            }

            for (WeaponVoxel v : originalVoxels) {
                Item item = v.material().getIconItem();
                itemDiffs.merge(item, -1, Integer::sum);
            }

            boolean isCreative = player.getAbilities().instabuild;

            if (!isCreative) {
                // 3. Strict Server-Side Verification: Check if player has enough of all items
                for (Map.Entry<Item, Integer> entry : itemDiffs.entrySet()) {
                    Item item = entry.getKey();
                    int needed = entry.getValue();

                    if (needed > 0) {
                        int has = countItem(player, item);
                        if (has < needed) {
                            int missing = needed - has;
                            player.sendOverlayMessage(
                                    Component.literal("§cCannot save: Missing " + missing + "x " + item.getName(new ItemStack(item)).getString() + "!")
                            );
                            // Resync inventory so client rolls back
                            player.inventoryMenu.broadcastChanges();
                            return;
                        }
                    }
                }

                // 4. Consume added items & refund removed items
                for (Map.Entry<Item, Integer> entry : itemDiffs.entrySet()) {
                    Item item = entry.getKey();
                    int diff = entry.getValue();

                    if (diff > 0) {
                        consumeItem(player, item, diff);
                    } else if (diff < 0) {
                        refundItem(player, item, -diff);
                    }
                }
            }

            // 5. Apply save to item and broadcast changes to client
            if (stack.getItem() instanceof ModularSwordItem) {
                ModularSwordItem.saveVoxels(stack, newVoxels);
            } else {
                ModularBowItem.saveVoxels(stack, newVoxels);
            }
            player.inventoryMenu.broadcastChanges();
        }));
    }

    private static int countItem(Player player, Item item) {
        int count = 0;

        // 1. Count items in main storage / hotbar
        for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
            if (s.is(item)) {
                count += s.getCount();
            }
        }

        // 2. Count item in offhand if matching
        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(item)) {
            count += offhand.getCount();
        }

        return count;
    }

    private static void consumeItem(Player player, Item item, int amount) {
        int needed = amount;

        // 1. Consume from main inventory storage
        for (int i = 0; i < player.getInventory().getNonEquipmentItems().size(); i++) {
            ItemStack s = player.getInventory().getNonEquipmentItems().get(i);
            if (s.is(item)) {
                int take = Math.min(needed, s.getCount());
                s.shrink(take);
                needed -= take;
                if (s.isEmpty()) {
                    player.getInventory().getNonEquipmentItems().set(i, ItemStack.EMPTY);
                }
                if (needed <= 0) return;
            }
        }

        // 2. Consume from offhand if still needed
        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(item) && needed > 0) {
            int take = Math.min(needed, offhand.getCount());
            offhand.shrink(take);
        }
    }

    private static void refundItem(Player player, Item item, int amount) {
        ItemStack refund = new ItemStack(item, amount);
        if (!player.getInventory().add(refund)) {
            player.drop(refund, false);
        }
    }
}