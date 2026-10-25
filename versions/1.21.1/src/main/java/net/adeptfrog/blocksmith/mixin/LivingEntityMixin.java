package net.adeptfrog.blocksmith.mixin;

import net.adeptfrog.blocksmith.ability.BlocksmithCombatHooks;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Minecraft 1.21.1: damage enters through hurt(DamageSource, float) on both sides; the hook ignores the client. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
	private void blocksmith$onDamaged(DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		if (BlocksmithCombatHooks.onDamaged((LivingEntity) (Object) this, source)) {
			cir.setReturnValue(false);
		}
	}
}
