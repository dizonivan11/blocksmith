package net.adeptfrog.blocksmith.mixin;

import net.adeptfrog.blocksmith.ability.BlocksmithCombatHooks;
import net.adeptfrog.blocksmith.ability.WeaponAbility;
import net.adeptfrog.blocksmith.ability.WeaponAbilityRegistry;
import net.adeptfrog.blocksmith.item.ModularSwordItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	// Cancels knockback when hurt by a Burst Fire arrows
	@Inject(method = "knockback", at = @At("HEAD"), cancellable = true)
	private void blocksmith$cancelDamageKnockback(double power, double xd, double zd, CallbackInfo ci) {
		if (BlocksmithCombatHooks.CANCEL_KNOCKBACK.get()) {
			ci.cancel();
		}
	}

	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void blocksmith$onPlayerDamaged(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity entity = (LivingEntity) (Object) this;

		if (entity instanceof Player player) {
			Entity direct = source.getDirectEntity();
			Entity attacker = source.getEntity();

			// Triggered when struck by any projectile from an active attacker
			if (direct instanceof Projectile && attacker != null && attacker != player && attacker.isAlive()) {
				ItemStack mainHand = player.getMainHandItem();
				ItemStack offHand = player.getOffhandItem();
				ItemStack sword = mainHand.getItem() instanceof ModularSwordItem ? mainHand
						: (offHand.getItem() instanceof ModularSwordItem ? offHand : null);

				if (sword != null) {
					for (WeaponAbility ability : WeaponAbilityRegistry.getUnlockedAbilities(sword)) {
						if (!ability.onDamagedByProjectile(sword, player, source, direct, attacker)) {
							cir.setReturnValue(false);
							return;
						}
					}
				}
			}
		}
	}
}