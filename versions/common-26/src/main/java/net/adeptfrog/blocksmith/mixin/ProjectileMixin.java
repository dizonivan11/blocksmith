package net.adeptfrog.blocksmith.mixin;

import net.adeptfrog.blocksmith.ability.ArrowHooks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hooks the arrow class and forwards to the shared {@link ArrowHooks}. Minecraft 26.x: arrows live in projectile.arrow and damage goes through hurtOrSimulate. */
@Mixin(AbstractArrow.class)
public class ProjectileMixin {
    // Bypasses Punch enchantment knockback
    @Inject(method = "doKnockback", at = @At("HEAD"), cancellable = true)
    private void blocksmith$cancelPunchKnockback(LivingEntity mob, DamageSource damageSource, CallbackInfo ci) {
        if (ArrowHooks.cancelKnockback((Projectile) (Object) this)) {
            ci.cancel();
        }
    }

    @ModifyArg(
            method = "onHitEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
            ),
            index = 1 // float damageAmount
    )
    private float blocksmith$modifyFinalArrowDamage(float originalDamage) {
        return ArrowHooks.modifyDamage((Projectile) (Object) this, originalDamage);
    }

    @Inject(method = "onHitBlock", at = @At("HEAD"))
    private void blocksmith$onBlockHit(BlockHitResult hitResult, CallbackInfo ci) {
        ArrowHooks.onHitBlock((Projectile) (Object) this, hitResult);
    }

    @Inject(method = "onHitEntity", at = @At("HEAD"))
    private void blocksmith$onEntityHit(EntityHitResult hitResult, CallbackInfo ci) {
        ArrowHooks.onHitEntity((Projectile) (Object) this, hitResult);
    }

    @Inject(method = "doPostHurtEffects", at = @At("HEAD"))
    private void blocksmith$doPostHurtEffects(LivingEntity mob, CallbackInfo ci) {
        ArrowHooks.afterHurt((Projectile) (Object) this, mob);
    }
}
