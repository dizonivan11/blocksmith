package net.adeptfrog.blocksmith.network;

import net.adeptfrog.blocksmith.Blocksmith;
import net.adeptfrog.blocksmith.data.VoxelMaterial;
import net.adeptfrog.blocksmith.data.WeaponVoxel;
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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record SaveWeaponVoxelsPayload(List<WeaponVoxel> voxels) implements CustomPacketPayload {
    public static final Type<SaveWeaponVoxelsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Blocksmith.MOD_ID, "save_weapon_voxels"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveWeaponVoxelsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, WeaponVoxel.STREAM_CODEC), SaveWeaponVoxelsPayload::voxels,
            SaveWeaponVoxelsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Player player = context.player();
                ItemStack stack = player.getMainHandItem();
                if (!(stack.getItem() instanceof ModularSwordItem)) {
                    stack = player.getOffhandItem();
                    if (!(stack.getItem() instanceof ModularSwordItem)) return;
                }

                List<WeaponVoxel> newVoxels = payload.voxels();

                // Reject payloads that do not meet voxel constraints
                if (newVoxels.size() < ModularSwordItem.MIN_VOXELS || newVoxels.size() > ModularSwordItem.MAX_VOXELS) {
                    return;
                }

                List<WeaponVoxel> originalVoxels = ModularSwordItem.getVoxels(stack);

                // Count original vs new voxels per material
                Map<VoxelMaterial, Integer> originalCounts = new EnumMap<>(VoxelMaterial.class);
                for (WeaponVoxel v : originalVoxels) {
                    originalCounts.merge(v.material(), 1, Integer::sum);
                }

                Map<VoxelMaterial, Integer> newCounts = new EnumMap<>(VoxelMaterial.class);
                for (WeaponVoxel v : newVoxels) {
                    newCounts.merge(v.material(), 1, Integer::sum);
                }

                boolean isCreative = player.getAbilities().instabuild;

                if (!isCreative) {
                    // 1. Verify player has enough of all required materials
                    for (VoxelMaterial mat : VoxelMaterial.values()) {
                        int origCount = originalCounts.getOrDefault(mat, 0);
                        int newCount = newCounts.getOrDefault(mat, 0);
                        int diff = newCount - origCount;

                        if (diff > 0) {
                            int available = countItem(player, mat.getIconItem());
                            if (available < diff) {
                                player.sendOverlayMessage(
                                        Component.literal("Not enough " + mat.getIconItem().getName(new ItemStack(mat.getIconItem())).getString() + "!")
                                );
                                return; // Reject save if insufficient materials
                            }
                        }
                    }

                    // 2. Consume required materials & refund removed materials
                    for (VoxelMaterial mat : VoxelMaterial.values()) {
                        int origCount = originalCounts.getOrDefault(mat, 0);
                        int newCount = newCounts.getOrDefault(mat, 0);
                        int diff = newCount - origCount;

                        if (diff > 0) {
                            consumeItem(player, mat.getIconItem(), diff);
                        } else if (diff < 0) {
                            refundItem(player, mat.getIconItem(), -diff);
                        }
                    }
                }

                // 3. Save to sword and sync inventory
                ModularSwordItem.saveVoxels(stack, newVoxels);
                player.inventoryMenu.broadcastChanges();
            });
        });
    }

    private static int countItem(Player player, Item item) {
        int count = 0;
        for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
            if (s.is(item)) {
                count += s.getCount();
            }
        }
        return count;
    }

    private static void consumeItem(Player player, Item item, int amount) {
        int needed = amount;
        for (int i = 0; i < player.getInventory().getNonEquipmentItems().size(); i++) {
            ItemStack s = player.getInventory().getNonEquipmentItems().get(i);
            if (s.is(item)) {
                int take = Math.min(needed, s.getCount());
                s.shrink(take);
                needed -= take;
                if (s.isEmpty()) {
                    player.getInventory().getNonEquipmentItems().set(i, ItemStack.EMPTY);
                }
                if (needed <= 0) break;
            }
        }
    }

    private static void refundItem(Player player, Item item, int amount) {
        ItemStack refund = new ItemStack(item, amount);
        if (!player.getInventory().add(refund)) {
            player.drop(refund, false); // Drop on ground if inventory is full
        }
    }
}
