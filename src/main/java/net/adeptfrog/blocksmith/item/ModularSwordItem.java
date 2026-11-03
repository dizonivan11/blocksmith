package net.adeptfrog.blocksmith.item;

import net.adeptfrog.blocksmith.compat.EntityCompat;
import net.adeptfrog.blocksmith.compat.McCompat;
import net.adeptfrog.blocksmith.ability.BlocksmithCombatHooks;
import net.adeptfrog.blocksmith.ability.WeaponAbility;
import net.adeptfrog.blocksmith.ability.WeaponAbilityRegistry;
import net.adeptfrog.blocksmith.component.ModDataComponents;
import net.adeptfrog.blocksmith.data.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.ArrayList;
import java.util.List;

import static net.adeptfrog.blocksmith.Blocksmith.BASE_DURABILITY;

public class ModularSwordItem extends SwordItemBase {
    /** Attack damage before material bonuses, on top of the player's own 1 (so the tooltip shows 10). */
    public static final double BASE_ATTACK_DAMAGE = 9.0;

    public ModularSwordItem(Properties properties) {
        super(properties);
    }

    public static List<WeaponVoxel> getVoxels(ItemStack stack) {
        List<WeaponVoxel> list = stack.get(ModDataComponents.WEAPON_VOXELS);
        return (list != null && !list.isEmpty()) ? list : getDefaultVoxels();
    }

    public static void saveVoxels(ItemStack stack, List<WeaponVoxel> voxels) {
        stack.set(ModDataComponents.WEAPON_VOXELS, new ArrayList<>(voxels));
        recalculateAttributes(stack);
    }

    /** Durability and attribute modifiers a design gives under the current material settings. */
    private record Stats(int maxDamage, ItemAttributeModifiers modifiers) {}

    private static final DesignCache<Stats> STATS_CACHE = new DesignCache<>(32);

    private static Stats computeStats(List<WeaponVoxel> voxels) {
        int gridSize = VoxelMaterialRegistry.getGridSize();
        double gridArea = gridSize * gridSize;

        double rawDamage = 0;
        double rawSpeed = 0;
        double rawDurability = 0;

        for (WeaponVoxel voxel : voxels) {
            VoxelMaterial mat = voxel.material();
            rawDamage += mat.statDamage();
            rawSpeed += mat.statSpeed();
            rawDurability += mat.statDurability();
        }

        int count = voxels.size();
        double bonusDamage = StatScaling.scale(rawDamage, count) / gridArea;
        double bonusSpeed = StatScaling.scale(rawSpeed, count) / gridArea;
        int maxDamage = WeaponDurability.of(BASE_DURABILITY, Math.round(StatScaling.scale(rawDurability, count) / gridArea));

        ItemAttributeModifiers modifiers = ItemAttributeModifiers.builder()
                .add(
                        Attributes.ATTACK_DAMAGE,
                        McCompat.vanillaModifier("base_attack_damage", BASE_ATTACK_DAMAGE + bonusDamage),
                        EquipmentSlotGroup.MAINHAND
                )
                .add(
                        Attributes.ATTACK_SPEED,
                        McCompat.vanillaModifier("base_attack_speed", -2.4 + bonusSpeed),
                        EquipmentSlotGroup.MAINHAND
                )
                .build();

        return new Stats(maxDamage, modifiers);
    }

    public static int calculateDefaultMaxDurability() {
        VoxelMaterialRegistry.initialize();
        return computeStats(getDefaultVoxels()).maxDamage();
    }

    public static ItemAttributeModifiers createDefaultAttributes() {
        return computeStats(getDefaultVoxels()).modifiers();
    }

    public static void recalculateAttributes(ItemStack stack) {
        Stats stats = computeStats(getVoxels(stack));
        WeaponDurability.set(stack, stats.maxDamage());
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, stats.modifiers());
    }

    /**
     * Brings the stack's stats in line with the current material settings. Item defaults are computed once at
     * startup and forged stats when saved, so toggling Dynamic Stats (or editing materials) would leave them stale.
     */
    public static void refreshStats(ItemStack stack) {
        Stats stats = STATS_CACHE.get(getVoxels(stack), ModularSwordItem::computeStats);
        WeaponDurability.set(stack, stats.maxDamage());
        if (!stats.modifiers().equals(stack.get(DataComponents.ATTRIBUTE_MODIFIERS))) {
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, stats.modifiers());
        }
    }

    @Override
    protected void serverInventoryTick(ItemStack stack) {
        // Stats only go stale when the config changes: check each stack once per config version, not every tick
        if (StatsCheckedStack.needsCheck(stack)) refreshStats(stack);
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // Fire all unlocked WeaponAbility's onMeleeHit method, on charged swings only (spam-clicking gets none)
        if (attacker instanceof Player player && !player.level().isClientSide()) {
            if (!EntityCompat.isOnCooldown(player, stack)
                    && BlocksmithCombatHooks.attackStrength(player) >= BlocksmithCombatHooks.MIN_ABILITY_ATTACK_STRENGTH) {
                for (WeaponAbility ability : WeaponAbilityRegistry.getUnlockedAbilities(stack)) {
                    if (ability.weaponType() == WeaponAbility.WeaponType.SWORD || ability.weaponType() == WeaponAbility.WeaponType.ANY) {
                        ability.onMeleeHit(stack, player, target);
                    }
                }
            }
        }
        super.postHurtEnemy(stack, target, attacker);
    }

    public static List<WeaponVoxel> getDefaultVoxels() {
        return VoxelDesignSerializer.getDefaultVoxels();
    }

    public static WeaponOffset getOffset(ItemStack stack) {
        WeaponOffset offset = stack.get(ModDataComponents.WEAPON_OFFSET);
        return offset != null ? offset : getDefaultOffset();
    }

    public static WeaponOffset getDefaultOffset() {
        return VoxelDesignSerializer.getDefaultOffset();
    }
}