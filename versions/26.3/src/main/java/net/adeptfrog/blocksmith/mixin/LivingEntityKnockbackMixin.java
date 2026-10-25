package net.adeptfrog.blocksmith.mixin;

import net.adeptfrog.blocksmith.ability.BlocksmithCombatHooks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels knockback when hurt by a Burst Fire arrow. Minecraft 26.2 and newer: knockback also takes the
 * damage source, the damage and whether it comes from an effect. Only applied on those versions, see
 * {@link BlocksmithMixinPlugin}.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityKnockbackMixin {
	@Inject(method = "knockback", at = @At("HEAD"), cancellable = true)
	private void blocksmith$cancelDamageKnockback(double power, double xd, double zd, DamageSource source, float damage, boolean comesFromEffect, CallbackInfo ci) {
		if (BlocksmithCombatHooks.CANCEL_KNOCKBACK.get()) {
			ci.cancel();
		}
	}
}
