package net.adeptfrog.blocksmith.compat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Entity fields that changed between Minecraft versions. Minecraft 26.3 version: invulnerableTime is private
 * (living entities use damageCooldownTime for hurt immunity) and hurtMarked became syncVelocity.
 */
public final class HurtCompat {
    private HurtCompat() {
    }

    /** Clears damage immunity frames so the next hit registers. */
    public static void resetHurtCooldown(Entity entity) {
        entity.setInvulnerableTime(0);
        if (entity instanceof LivingEntity living) {
            living.damageCooldownTime = 0;
        }
    }

    /** Sends the entity's changed velocity to clients this tick. */
    public static void syncVelocity(Entity entity) {
        entity.syncVelocity = true;
    }
}
