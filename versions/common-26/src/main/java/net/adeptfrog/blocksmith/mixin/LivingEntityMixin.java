package net.adeptfrog.blocksmith.mixin;

import net.adeptfrog.blocksmith.ability.BlocksmithCombatHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Minecraft 26.x: server damage enters through hurtServer(ServerLevel, DamageSource, float). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void blocksmith$onDamaged(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		if (BlocksmithCombatHooks.onDamaged((LivingEntity) (Object) this, source)) {
			cir.setReturnValue(false);
		}
	}
}
