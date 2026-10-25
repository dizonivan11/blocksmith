package net.adeptfrog.blocksmith.mixin;

import net.adeptfrog.blocksmith.ability.BlocksmithCombatHooks;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels knockback when hurt by a Burst Fire arrow. Minecraft 1.21.1 / 26.1: knockback takes only the power and direction.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityKnockbackMixin {
	@Inject(method = "knockback", at = @At("HEAD"), cancellable = true)
	private void blocksmith$cancelDamageKnockback(double power, double xd, double zd, CallbackInfo ci) {
		if (BlocksmithCombatHooks.CANCEL_KNOCKBACK.get()) {
			ci.cancel();
		}
	}
}
