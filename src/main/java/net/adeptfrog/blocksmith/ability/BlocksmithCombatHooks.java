package net.adeptfrog.blocksmith.ability;

public class BlocksmithCombatHooks {
    public static final ThreadLocal<Boolean> CANCEL_KNOCKBACK = ThreadLocal.withInitial(() -> false);
}
