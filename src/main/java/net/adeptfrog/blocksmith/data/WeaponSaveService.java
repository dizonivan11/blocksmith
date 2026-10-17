package net.adeptfrog.blocksmith.data;

import net.adeptfrog.blocksmith.component.ModDataComponents;
import net.adeptfrog.blocksmith.item.ModularBowItem;
import net.adeptfrog.blocksmith.item.ModularSwordItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.adeptfrog.blocksmith.Blocksmith.MAX_TOTAL_VOXELS;
import static net.adeptfrog.blocksmith.Blocksmith.MIN_VOXELS;

/**
 * Server-side validation and saving of a weapon design. Shared by the forging table screen and the Dimensional Editor
 * so both enforce exactly the same limits and material costs.
 */
public final class WeaponSaveService {
    private WeaponSaveService() {}

    public static boolean isModular(ItemStack stack) {
        return stack.getItem() instanceof ModularSwordItem || stack.getItem() instanceof ModularBowItem;
    }

    /** The forged weapon in the player's main hand, else the off hand, else null. */
    public static @Nullable ItemStack findModularStack(Player player) {
        ItemStack stack = player.getMainHandItem();
        if (isModular(stack)) return stack;
        stack = player.getOffhandItem();
        return isModular(stack) ? stack : null;
    }

    public static List<WeaponVoxel> getVoxels(ItemStack stack) {
        return stack.getItem() instanceof ModularSwordItem ? ModularSwordItem.getVoxels(stack) : ModularBowItem.getVoxels(stack);
    }

    /**
     * Validates {@code newVoxels} / {@code newOffset} and, if everything is in order, charges the material difference
     * and writes the design onto {@code stack}.
     *
     * @param freeMaterials true when no materials are charged (creative players)
     * @return true when the design was saved
     */
    public static boolean apply(Player player, ItemStack stack, List<WeaponVoxel> newVoxels, WeaponOffset newOffset, boolean freeMaterials) {
        int gridSize = VoxelMaterialRegistry.getGridSize();

        // Voxel count limit check (across all layers)
        if (newVoxels.size() < MIN_VOXELS || newVoxels.size() > MAX_TOTAL_VOXELS()) {
            return fail(player, "§cInvalid voxel count! (min " + MIN_VOXELS + ")");
        }

        // Layer range check
        for (WeaponVoxel v : newVoxels) {
            if (!WeaponVoxel.isValidLayer(v.z())) return fail(player, "§cInvalid voxel layer!");
        }

        // Grip offset limit check
        if (Math.abs(newOffset.x()) > gridSize || Math.abs(newOffset.y()) > gridSize) {
            return fail(player, "§cInvalid grip offset!");
        }

        Map<Item, Integer> itemDiffs = new HashMap<>();
        for (WeaponVoxel v : newVoxels) itemDiffs.merge(v.material().getIconItem(), 1, Integer::sum);
        for (WeaponVoxel v : getVoxels(stack)) itemDiffs.merge(v.material().getIconItem(), -1, Integer::sum);

        if (!freeMaterials) {
            for (Map.Entry<Item, Integer> entry : itemDiffs.entrySet()) {
                Item item = entry.getKey();
                int needed = entry.getValue();
                if (needed > 0) {
                    int has = countItem(player, item);
                    if (has < needed) {
                        return fail(player, "§cCannot save: Missing " + (needed - has) + "x " + item.getName(new ItemStack(item)).getString() + "!");
                    }
                }
            }
            for (Map.Entry<Item, Integer> entry : itemDiffs.entrySet()) {
                if (entry.getValue() > 0) consumeItem(player, entry.getKey(), entry.getValue());
            }
        }

        if (stack.getItem() instanceof ModularSwordItem) ModularSwordItem.saveVoxels(stack, newVoxels);
        else ModularBowItem.saveVoxels(stack, newVoxels);
        stack.set(ModDataComponents.WEAPON_OFFSET, newOffset);

        player.inventoryMenu.broadcastChanges();
        return true;
    }

    private static boolean fail(Player player, String message) {
        player.sendOverlayMessage(Component.literal(message));
        player.inventoryMenu.broadcastChanges();
        return false;
    }

    public static int countItem(Player player, Item item) {
        int count = 0;
        for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
            if (s.is(item)) count += s.getCount();
        }
        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(item)) count += offhand.getCount();
        return count;
    }

    public static void consumeItem(Player player, Item item, int amount) {
        int needed = amount;
        for (int i = 0; i < player.getInventory().getNonEquipmentItems().size(); i++) {
            ItemStack s = player.getInventory().getNonEquipmentItems().get(i);
            if (s.is(item)) {
                int take = Math.min(needed, s.getCount());
                s.shrink(take);
                needed -= take;
                if (s.isEmpty()) player.getInventory().getNonEquipmentItems().set(i, ItemStack.EMPTY);
                if (needed <= 0) return;
            }
        }
        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(item) && needed > 0) {
            offhand.shrink(Math.min(needed, offhand.getCount()));
        }
    }
}
