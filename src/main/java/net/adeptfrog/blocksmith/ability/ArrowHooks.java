package net.adeptfrog.blocksmith.ability;

import net.adeptfrog.blocksmith.compat.EntityCompat;
import net.adeptfrog.blocksmith.data.StatScaling;
import net.adeptfrog.blocksmith.data.VoxelMaterialRegistry;
import net.adeptfrog.blocksmith.data.WeaponVoxel;
import net.adeptfrog.blocksmith.item.ModularBowItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

import java.util.List;

import static net.adeptfrog.blocksmith.item.ModularBowItem.BONUS_BASE_DAMAGE;

/**
 * Arrow behaviour of the forged bow, shared by every Minecraft version. The per-version ProjectileMixin
 * (versions/.../mixin) only hooks the arrow class and forwards here.
 */
public final class ArrowHooks {
    private ArrowHooks() {
    }

    /** True when the arrow's Punch knockback should be skipped (Burst Fire arrows). */
    public static boolean cancelKnockback(Projectile arrow) {
        return WeaponAbilityRegistry.arrowHasAbility(arrow, "burst");
    }

    /** Final arrow damage: adds the forged bow's voxel bonus (and Burst/Sniper adjustments) to the vanilla damage. */
    public static float modifyDamage(Projectile arrow, float originalDamage) {

        // Retrieve the ModularBow ItemStack that shot this arrow
        ItemStack bow = arrow.getWeaponItem();
        if (bow == null && arrow.getOwner() instanceof LivingEntity shooter) {
            if (shooter.getMainHandItem().getItem() instanceof ModularBowItem) {
                bow = shooter.getMainHandItem();
            } else if (shooter.getOffhandItem().getItem() instanceof ModularBowItem) {
                bow = shooter.getOffhandItem();
            }
        }

        if (bow != null && bow.getItem() instanceof ModularBowItem) {
            // If shot by Burst Fire, divide the original damage (including Power & Crits) by 3 before adding voxel damage bonus
            if (WeaponAbilityRegistry.arrowHasAbility(arrow, "burst")) {
                originalDamage = Math.max(2.0F, originalDamage / 3.0F);
            }

            // Calculate normalized voxel damage bonus
            int gridSize = VoxelMaterialRegistry.getGridSize();
            double gridArea = gridSize * gridSize;
            double rawDamage = 0;
            List<WeaponVoxel> bowVoxels = ModularBowItem.getVoxels(bow);
            for (WeaponVoxel voxel : bowVoxels) {
                rawDamage += voxel.material().getBonusDamage();
            }
            double voxelDamageBonus = StatScaling.scale(rawDamage, bowVoxels.size()) / gridArea;

            // Add flat voxel bonus damage to vanilla calculated damage with Power & Crits
            originalDamage = (float) (originalDamage + voxelDamageBonus + BONUS_BASE_DAMAGE);

            // If shot by Sniper, divide the final damage by 2 to offset the velocity damage increase
            if (WeaponAbilityRegistry.arrowHasAbility(arrow, "sniper")) {
                originalDamage /= 2.0F;
            }
        }

        return originalDamage;
    }

    /** OnArrowHit / OnArrowHitBlock ability events. */
    public static void onHitBlock(Projectile arrow, BlockHitResult hitResult) {
        if (arrow.level() instanceof ServerLevel serverLevel) {
            for (WeaponAbility ability : abilities(arrow)) {
                ability.onArrowHit(arrow, hitResult, serverLevel);
                ability.onArrowHitBlock(arrow, hitResult, serverLevel);
            }
        }
    }

    /** OnArrowHit / OnArrowHitEntity ability events. */
    public static void onHitEntity(Projectile arrow, EntityHitResult hitResult) {
        if (arrow.level() instanceof ServerLevel serverLevel) {
            for (WeaponAbility ability : abilities(arrow)) {
                ability.onArrowHit(arrow, hitResult, serverLevel);
                ability.onArrowHitEntity(arrow, hitResult, serverLevel);
            }
        }
    }

    /** afterArrowHitEntity ability events. */
    public static void afterHurt(Projectile arrow, LivingEntity mob) {
        if (arrow.level() instanceof ServerLevel serverLevel) {
            for (WeaponAbility ability : abilities(arrow)) {
                ability.afterArrowHitEntity(arrow, mob, serverLevel);
            }
        }
    }

    private static List<WeaponAbility> abilities(Projectile arrow) {
        List<WeaponAbility> list = new java.util.ArrayList<>();
        if (VoxelMaterialRegistry.areAbilitiesDisabled()) return list;
        // Copy first: abilities may remove their own tag while running
        for (String tag : List.copyOf(EntityCompat.tags(arrow))) {
            if (tag.startsWith("blocksmith_ability:")) {
                WeaponAbility ability = WeaponAbilityRegistry.get(tag.substring("blocksmith_ability:".length()));
                if (ability != null) list.add(ability);
            }
        }
        return list;
    }
}
