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

    // Melee abilities need a swing charged at least this much (vanilla's critical hit threshold).
    public static final float MIN_ABILITY_ATTACK_STRENGTH = 0.9F;

    private record Swing(Player player, int tick, float strength) {}

    private static final ThreadLocal<Swing> SWING = new ThreadLocal<>();

    /**
     * Called as a player starts an attack, before Player.attack resets the attack cooldown, which happens before the
     * weapon learns of the hit. Remembers how charged the swing was.
     */
    public static void beforeAttack(Player player) {
        SWING.set(new Swing(player, player.tickCount, player.getAttackStrengthScale(0.5F)));
    }

    /**
     * How charged the player's current swing was. Attacks that do not go through the attack event (from other mods,
     * for example) count as fully charged, as before.
     */
    public static float attackStrength(Player player) {
        Swing swing = SWING.get();
        if (swing == null || swing.player() != player || swing.tick() != player.tickCount) return 1.0F;
        return swing.strength();
    }

    /**
     * Server-side damage to a living entity, called by the per-version LivingEntityMixin. Returns true when the
     * damage should be cancelled (a forged sword ability dodged a projectile).
     */
    public static boolean onDamaged(LivingEntity entity, DamageSource source, float amount) {
        if (entity.level().isClientSide()) return false;
        if (!(entity instanceof Player player)) return false;

        Entity direct = source.getDirectEntity();
        Entity attacker = source.getEntity();

        // Triggered when struck by any projectile from an active attacker that actually deals damage (not snowballs)
        if (amount > 0 && direct instanceof Projectile && attacker != null && attacker != player && attacker.isAlive()) {
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
