package net.adeptfrog.blocksmith.item;

import net.adeptfrog.blocksmith.component.ModDataComponents;
import net.adeptfrog.blocksmith.data.VoxelDesignSerializer;
import net.adeptfrog.blocksmith.data.VoxelMaterial;
import net.adeptfrog.blocksmith.data.WeaponVoxel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

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
        List<WeaponVoxel> defaults = getDefaultVoxels();
        int bonusDurability = 0;
        for (WeaponVoxel voxel : defaults) {
            bonusDurability += voxel.material().getBonusDurability();
        }
        return BASE_DURABILITY + bonusDurability;
    }

    public static void recalculateAttributes(ItemStack stack) {
        List<WeaponVoxel> voxels = getVoxels(stack);

        double bonusDamage = 0;
        double bonusSpeed = 0;
        int bonusDurability = 0;

        for (WeaponVoxel voxel : voxels) {
            VoxelMaterial mat = voxel.material();
            bonusDamage += mat.getBonusDamage();
            bonusSpeed += mat.getBonusSpeed();
            bonusDurability += mat.getBonusDurability();
        }

        stack.set(DataComponents.MAX_DAMAGE, BASE_DURABILITY + bonusDurability);

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
            VoxelMaterial mat = voxel.material();
            bonusDamage += mat.getBonusDamage();
            bonusSpeed += mat.getBonusSpeed();
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
        return VoxelDesignSerializer.getDefaultVoxels();
    }
}