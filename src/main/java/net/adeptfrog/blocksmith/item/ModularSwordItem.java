package net.adeptfrog.blocksmith.item;

import net.adeptfrog.blocksmith.component.ModDataComponents;
import net.adeptfrog.blocksmith.data.VoxelMaterial;
import net.adeptfrog.blocksmith.data.WeaponVoxel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.ArrayList;
import java.util.List;

public class ModularSwordItem extends Item {
    public static final int MIN_VOXELS = 8;   // Minimum threshold
    public static final int MAX_VOXELS = 192;
    public static final int BASE_DURABILITY = ToolMaterial.NETHERITE.durability();

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

    public static void recalculateAttributes(ItemStack stack) {
        List<WeaponVoxel> voxels = getVoxels(stack);

        double bonusDamage = 0;
        double bonusSpeed = 0;
        int bonusDurability = 0;

        for (WeaponVoxel voxel : voxels) {
            bonusDamage += voxel.material().getBonusDamage();
            bonusSpeed += voxel.material().getBonusSpeed();
            bonusDurability += voxel.material().getBonusDurability();
        }

        // 1. Update Dynamic Max Durability Component
        stack.set(DataComponents.MAX_DAMAGE, BASE_DURABILITY + bonusDurability);

        // 2. Update Attack Damage & Attack Speed Modifiers
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
        double bonusDamage = 0;
        double bonusSpeed = 0;

        for (WeaponVoxel voxel : defaults) {
            bonusDamage += voxel.material().getBonusDamage();
            bonusSpeed += voxel.material().getBonusSpeed();
        }

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

    public static List<WeaponVoxel> getDefaultVoxels() {
        List<WeaponVoxel> defaults = new ArrayList<>();

        // 1. Pommel
        defaults.add(new WeaponVoxel(2, 2, 0, VoxelMaterial.IRON, 1));
        defaults.add(new WeaponVoxel(2, 3, 0, VoxelMaterial.IRON, 2));
        defaults.add(new WeaponVoxel(3, 2, 0, VoxelMaterial.IRON, 0));

        // 2. Handle
        defaults.add(new WeaponVoxel(3, 3, 0, VoxelMaterial.NETHERITE, 1));
        defaults.add(new WeaponVoxel(4, 4, 0, VoxelMaterial.NETHERITE, 2));
        defaults.add(new WeaponVoxel(5, 5, 0, VoxelMaterial.NETHERITE, 2));

        // 3. Crossguard
        defaults.add(new WeaponVoxel(4, 7, 0, VoxelMaterial.IRON, 0));
        defaults.add(new WeaponVoxel(5, 7, 0, VoxelMaterial.IRON, 1));
        defaults.add(new WeaponVoxel(5, 6, 0, VoxelMaterial.IRON, 2));
        defaults.add(new WeaponVoxel(6, 6, 0, VoxelMaterial.IRON, 3));
        defaults.add(new WeaponVoxel(6, 7, 0, VoxelMaterial.IRON, 2));
        defaults.add(new WeaponVoxel(7, 6, 0, VoxelMaterial.IRON, 1));
        defaults.add(new WeaponVoxel(6, 5, 0, VoxelMaterial.IRON, 2));
        defaults.add(new WeaponVoxel(7, 5, 0, VoxelMaterial.IRON, 1));
        defaults.add(new WeaponVoxel(7, 4, 0, VoxelMaterial.IRON, 0));

        // 4. Blade
        for (int i = 8; i <= 18; i++) {
            defaults.add(new WeaponVoxel(i, i, 0, VoxelMaterial.IRON, 2));
            defaults.add(new WeaponVoxel(i - 1, i, 0, VoxelMaterial.IRON, 3));
            defaults.add(new WeaponVoxel(i, i - 1, 0, VoxelMaterial.IRON, 1));
        }

        // 5. Tip
        defaults.add(new WeaponVoxel(18, 19, 0, VoxelMaterial.IRON, 3));
        defaults.add(new WeaponVoxel(19, 18, 0, VoxelMaterial.IRON, 1));
        defaults.add(new WeaponVoxel(19, 19, 0, VoxelMaterial.IRON, 3));
        defaults.add(new WeaponVoxel(20, 20, 0, VoxelMaterial.IRON, 4));

        return defaults;
    }
}