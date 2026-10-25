package net.adeptfrog.blocksmith.ability;

import net.adeptfrog.blocksmith.item.ModularSwordItem;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;

public class BlocksmithCombatHooks {
    public static final ThreadLocal<Boolean> CANCEL_KNOCKBACK = ThreadLocal.withInitial(() -> false);

    /**
     * Server-side damage to a living entity, called by the per-version LivingEntityMixin. Returns true when the
     * damage should be cancelled (a forged sword ability dodged a projectile).
     */
    public static boolean onDamaged(LivingEntity entity, DamageSource source) {
        if (entity.level().isClientSide()) return false;
        if (!(entity instanceof Player player)) return false;

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
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
