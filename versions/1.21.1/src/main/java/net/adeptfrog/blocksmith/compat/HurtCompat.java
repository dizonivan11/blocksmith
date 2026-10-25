package net.adeptfrog.blocksmith.compat;

import net.minecraft.world.entity.Entity;

/** Entity fields that changed between Minecraft versions. Minecraft 1.21.1 / 26.1 / 26.2 version. */
public final class HurtCompat {
    private HurtCompat() {
    }

    /** Clears damage immunity frames so the next hit registers. */
    public static void resetHurtCooldown(Entity entity) {
        entity.invulnerableTime = 0;
    }

    /** Sends the entity's changed velocity to clients this tick. */
    public static void syncVelocity(Entity entity) {
        entity.hurtMarked = true;
    }
}
