package net.adeptfrog.blocksmith.mixin;

import net.adeptfrog.blocksmith.ability.WeaponAbility;
import net.adeptfrog.blocksmith.ability.WeaponAbilityRegistry;
import net.adeptfrog.blocksmith.data.StatScaling;
import net.adeptfrog.blocksmith.data.VoxelMaterialRegistry;
import net.adeptfrog.blocksmith.data.WeaponVoxel;
import net.adeptfrog.blocksmith.item.ModularBowItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

import static net.adeptfrog.blocksmith.item.ModularBowItem.BONUS_BASE_DAMAGE;

@Mixin(AbstractArrow.class)
public class ProjectileMixin {
    // Bypasses Punch enchantment knockback
    @Inject(method = "doKnockback", at = @At("HEAD"), cancellable = true)
    private void blocksmith$cancelPunchKnockback(LivingEntity mob, DamageSource damageSource, CallbackInfo ci) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        if (arrow.entityTags().contains("blocksmith_ability:burst")) {
            ci.cancel();
        }
    }

    @ModifyArg(
            method = "onHitEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
            ),
            index = 1 // Index 1 is the 3rd parameter: float damageAmount
    )
    private float blocksmith$modifyFinalArrowDamage(float originalDamage) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;

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

    // OnArrowHit ability events
    @Inject(method = "onHitBlock", at = @At("HEAD"))
    private void blocksmith$onBlockHit(BlockHitResult hitResult, CallbackInfo ci) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        if (arrow.level() instanceof ServerLevel serverLevel) {
            for (String tag : arrow.entityTags()) {
                if (tag.startsWith("blocksmith_ability:")) {
                    String abilityId = tag.substring("blocksmith_ability:".length());
                    WeaponAbility ability = WeaponAbilityRegistry.get(abilityId);
                    if (ability != null) {
                        ability.onArrowHit(arrow, hitResult, serverLevel);
                        ability.onArrowHitBlock(arrow, hitResult, serverLevel);
                    }
                }
            }
        }
    }

    @Inject(method = "onHitEntity", at = @At("HEAD"))
    private void blocksmith$onEntityHit(EntityHitResult hitResult, CallbackInfo ci) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        if (arrow.level() instanceof ServerLevel serverLevel) {
            for (String tag : arrow.entityTags()) {
                if (tag.startsWith("blocksmith_ability:")) {
                    String abilityId = tag.substring("blocksmith_ability:".length());
                    WeaponAbility ability = WeaponAbilityRegistry.get(abilityId);
                    if (ability != null) {
                        ability.onArrowHit(arrow, hitResult, serverLevel);
                        ability.onArrowHitEntity(arrow, hitResult, serverLevel);
                    }
                }
            }
        }
    }

    // afterArrowHitEntity ability events
    @Inject(method = "doPostHurtEffects", at = @At("HEAD"))
    private void blocksmith$doPostHurtEffects(LivingEntity mob, CallbackInfo ci) {
        Projectile projectile = (Projectile) (Object) this;
        if (projectile instanceof AbstractArrow arrow && projectile.level() instanceof ServerLevel serverLevel) {
            for (String tag : projectile.entityTags()) {
                if (tag.startsWith("blocksmith_ability:")) {
                    String abilityId = tag.substring("blocksmith_ability:".length());
                    WeaponAbility ability = WeaponAbilityRegistry.get(abilityId);
                    if (ability != null) {
                        ability.afterArrowHitEntity(arrow, mob, serverLevel);
                    }
                }
            }
        }
    }
}
