package net.adeptfrog.blocksmith.network;

import net.adeptfrog.blocksmith.compat.EntityCompat;
import net.adeptfrog.blocksmith.compat.McCompat;
import net.adeptfrog.blocksmith.Blocksmith;
import net.adeptfrog.blocksmith.component.ModDataComponents;
import net.adeptfrog.blocksmith.data.VoxelMaterialRegistry;
import net.adeptfrog.blocksmith.data.WeaponOffset;
import net.adeptfrog.blocksmith.data.WeaponVoxel;
import net.adeptfrog.blocksmith.item.ModularBowItem;
import net.adeptfrog.blocksmith.item.ModularSwordItem;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.adeptfrog.blocksmith.Blocksmith.MIN_VOXELS;
import static net.adeptfrog.blocksmith.Blocksmith.MAX_TOTAL_VOXELS;

public record SaveWeaponVoxelsPayload(List<WeaponVoxel> voxels, WeaponOffset offset) implements CustomPacketPayload {
    public static final Type<SaveWeaponVoxelsPayload> TYPE = McCompat.payloadType("save_weapon_voxels");

    private static final int SAVE_INTERVAL_TICKS = 10;
    // Blocks from the player within which a Forging Table must be (reach, plus room to step back while editing)
    private static final int TABLE_RANGE = 8;
    private static final Map<UUID, Integer> LAST_SAVE = new HashMap<>();

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveWeaponVoxelsPayload> STREAM_CODEC = StreamCodec.composite(
            WeaponVoxel.LIST_STREAM_CODEC, SaveWeaponVoxelsPayload::voxels,
            WeaponOffset.STREAM_CODEC, SaveWeaponVoxelsPayload::offset,
            SaveWeaponVoxelsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register() {
        McCompat.registerServerbound(TYPE, STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> context.server().execute(() -> {
            Player player = context.player();
            ItemStack stack = player.getMainHandItem();
            boolean isModular = stack.getItem() instanceof ModularSwordItem || stack.getItem() instanceof ModularBowItem;

            if (!isModular) {
                stack = player.getOffhandItem();
                isModular = stack.getItem() instanceof ModularSwordItem || stack.getItem() instanceof ModularBowItem;
                if (!isModular) return;
            }

            // Each save walks the whole design: at most one per SAVE_INTERVAL_TICKS per player
            int now = context.server().getTickCount();
            Integer last = LAST_SAVE.put(player.getUUID(), now);
            if (last != null && now >= last && now - last < SAVE_INTERVAL_TICKS) return;

            // The editor only opens from a Forging Table, so saving needs one nearby
            if (!isNearWeaponTable(player)) {
                EntityCompat.actionBar(player, Component.literal("§cYou must be near a Forging Table to save!"));
                player.inventoryMenu.broadcastChanges();
                return;
            }

            int gridSize = VoxelMaterialRegistry.getGridSize();
            List<WeaponVoxel> newVoxels = validVoxels(payload.voxels(), gridSize);
            WeaponOffset newOffset = payload.offset();

            // Voxel count limit check (across all layers)
            if (newVoxels.size() < MIN_VOXELS || newVoxels.size() > MAX_TOTAL_VOXELS()) {
                EntityCompat.actionBar(player, Component.literal("§cInvalid voxel count!"));
                player.inventoryMenu.broadcastChanges();
                return;
            }

            // Grip offset limit check
            if (Math.abs(newOffset.x()) > gridSize || Math.abs(newOffset.y()) > gridSize) {
                EntityCompat.actionBar(player, Component.literal("§cInvalid grip offset!"));
                player.inventoryMenu.broadcastChanges();
                return;
            }

            List<WeaponVoxel> originalVoxels = stack.getItem() instanceof ModularSwordItem
                    ? ModularSwordItem.getVoxels(stack)
                    : ModularBowItem.getVoxels(stack);

            Map<Item, Integer> itemDiffs = new HashMap<>();

            for (WeaponVoxel v : newVoxels) {
                Item item = v.material().getIconItem();
                itemDiffs.merge(item, 1, Integer::sum);
            }

            for (WeaponVoxel v : originalVoxels) {
                Item item = v.material().getIconItem();
                itemDiffs.merge(item, -1, Integer::sum);
            }

            if (!VoxelMaterialRegistry.areMaterialsFree(player)) {
                for (Map.Entry<Item, Integer> entry : itemDiffs.entrySet()) {
                    Item item = entry.getKey();
                    int needed = entry.getValue();

                    if (needed > 0) {
                        int has = countItem(player, item);
                        if (has < needed) {
                            int missing = needed - has;
                            EntityCompat.actionBar(player, 
                                    Component.literal("§cCannot save: Missing " + missing + "x " + item.getName(new ItemStack(item)).getString() + "!")
                            );
                            player.inventoryMenu.broadcastChanges();
                            return;
                        }
                    }
                }

                for (Map.Entry<Item, Integer> entry : itemDiffs.entrySet()) {
                    Item item = entry.getKey();
                    int diff = entry.getValue();
                    if (diff > 0) consumeItem(player, item, diff);
                }
            }

            // Save data
            if (stack.getItem() instanceof ModularSwordItem) {
                ModularSwordItem.saveVoxels(stack, newVoxels);
            } else {
                ModularBowItem.saveVoxels(stack, newVoxels);
            }
            stack.set(ModDataComponents.WEAPON_OFFSET, newOffset);

            player.inventoryMenu.broadcastChanges();
        }));
    }

    /**
     * The voxels a design can really have: on a valid layer, inside the grid, of a known material, one per cell.
     * Others come from a modified client, or from a design made on a larger grid (cropped in the editor), and are
     * dropped. A material id is written once per voxel when the item is saved, so unknown ids must never be stored.
     */
    private static List<WeaponVoxel> validVoxels(List<WeaponVoxel> voxels, int gridSize) {
        Map<Long, WeaponVoxel> cells = new LinkedHashMap<>();
        for (WeaponVoxel v : voxels) {
            if (!WeaponVoxel.isValidLayer(v.z())) continue;
            if (v.x() < 0 || v.x() >= gridSize || v.y() < 0 || v.y() >= gridSize) continue;
            if (!VoxelMaterialRegistry.contains(v.materialId())) continue;
            long cell = ((long) (v.z() - Blocksmith.MIN_LAYER) << 32) | ((long) v.x() << 16) | v.y();
            cells.put(cell, v); // a later duplicate replaces an earlier one, as when the design was packed
        }
        return new ArrayList<>(cells.values());
    }

    private static boolean isNearWeaponTable(Player player) {
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-TABLE_RANGE, -TABLE_RANGE, -TABLE_RANGE),
                center.offset(TABLE_RANGE, TABLE_RANGE, TABLE_RANGE))) {
            if (player.level().getBlockState(pos).is(Blocksmith.WEAPON_TABLE)) return true;
        }
        return false;
    }

    private static int countItem(Player player, Item item) {
        int count = 0;

        for (ItemStack s : EntityCompat.mainInventory(player)) {
            if (s.is(item)) {
                count += s.getCount();
            }
        }

        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(item)) {
            count += offhand.getCount();
        }

        return count;
    }

    private static void consumeItem(Player player, Item item, int amount) {
        int needed = amount;

        for (int i = 0; i < EntityCompat.mainInventory(player).size(); i++) {
            ItemStack s = EntityCompat.mainInventory(player).get(i);
            if (s.is(item)) {
                int take = Math.min(needed, s.getCount());
                s.shrink(take);
                needed -= take;
                if (s.isEmpty()) {
                    EntityCompat.mainInventory(player).set(i, ItemStack.EMPTY);
                }
                if (needed <= 0) return;
            }
        }

        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(item) && needed > 0) {
            int take = Math.min(needed, offhand.getCount());
            offhand.shrink(take);
        }
    }
}