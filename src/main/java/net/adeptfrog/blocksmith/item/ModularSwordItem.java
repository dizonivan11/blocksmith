package net.adeptfrog.blocksmith.item;

import net.adeptfrog.blocksmith.ability.WeaponAbility;
import net.adeptfrog.blocksmith.ability.WeaponAbilityRegistry;
import net.adeptfrog.blocksmith.component.ModDataComponents;
import net.adeptfrog.blocksmith.data.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

import static net.adeptfrog.blocksmith.Blocksmith.BASE_DURABILITY;

public class ModularSwordItem extends Item {
    public static final Identifier BASE_ATTACK_DAMAGE_ID = Identifier.withDefaultNamespace("base_attack_damage");
    public static final Identifier BASE_ATTACK_SPEED_ID = Identifier.withDefaultNamespace("base_attack_speed");

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

    public static int calculateDefaultMaxDurability() {
        VoxelMaterialRegistry.initialize();
        List<WeaponVoxel> defaults = getDefaultVoxels();
        int gridSize = VoxelMaterialRegistry.getGridSize();
        double gridArea = gridSize * gridSize;

        double rawDurability = 0;
        for (WeaponVoxel voxel : defaults) {
            rawDurability += voxel.material().getBonusDurability();
        }
        return BASE_DURABILITY + (int) Math.round(StatScaling.scale(rawDurability, defaults.size()) / gridArea);
    }

    public static void recalculateAttributes(ItemStack stack) {
        List<WeaponVoxel> voxels = getVoxels(stack);
        int gridSize = VoxelMaterialRegistry.getGridSize();
        double gridArea = gridSize * gridSize;

        double rawDamage = 0;
        double rawSpeed = 0;
        double rawDurability = 0;

        for (WeaponVoxel voxel : voxels) {
            VoxelMaterial mat = voxel.material();
            rawDamage += mat.getBonusDamage();
            rawSpeed += mat.getBonusSpeed();
            rawDurability += mat.getBonusDurability();
        }

        int count = voxels.size();
        double bonusDamage = StatScaling.scale(rawDamage, count) / gridArea;
        double bonusSpeed = StatScaling.scale(rawSpeed, count) / gridArea;
        int bonusDurability = BASE_DURABILITY + (int) Math.round(StatScaling.scale(rawDurability, count) / gridArea);

        stack.set(DataComponents.MAX_DAMAGE, bonusDurability);

        ItemAttributeModifiers modifiers = ItemAttributeModifiers.builder()
                .add(
                        Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(
                                BASE_ATTACK_DAMAGE_ID,
                                6.0 + bonusDamage,
                                AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.MAINHAND
                )
                .add(
                        Attributes.ATTACK_SPEED,
                        new AttributeModifier(
                                BASE_ATTACK_SPEED_ID,
                                -2.4 + bonusSpeed,
                                AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.MAINHAND
                )
                .build();

        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);
    }

    public static ItemAttributeModifiers createDefaultAttributes() {
        List<WeaponVoxel> defaults = getDefaultVoxels();
        int gridSize = VoxelMaterialRegistry.getGridSize();
        double gridArea = gridSize * gridSize;

        double rawDamage = 0;
        double rawSpeed = 0;

        for (WeaponVoxel voxel : defaults) {
            VoxelMaterial mat = voxel.material();
            rawDamage += mat.getBonusDamage();
            rawSpeed += mat.getBonusSpeed();
        }

        double bonusDamage = StatScaling.scale(rawDamage, defaults.size()) / gridArea;
        double bonusSpeed = StatScaling.scale(rawSpeed, defaults.size()) / gridArea;

        return ItemAttributeModifiers.builder()
                .add(
                        Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(
                                BASE_ATTACK_DAMAGE_ID,
                                6.0 + bonusDamage,
                                AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.MAINHAND
                )
                .add(
                        Attributes.ATTACK_SPEED,
                        new AttributeModifier(
                                BASE_ATTACK_SPEED_ID,
                                -2.4 + bonusSpeed,
                                AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.MAINHAND
                )
                .build();
    }

    @Override
    public void postHurtEnemy(@NonNull ItemStack stack, @NonNull LivingEntity target, @NonNull LivingEntity attacker) {
        // Fire all unlocked WeaponAbility's onMeleeHit method
        if (attacker instanceof Player player && !player.level().isClientSide()) {
            if (!player.getCooldowns().isOnCooldown(stack)) {
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