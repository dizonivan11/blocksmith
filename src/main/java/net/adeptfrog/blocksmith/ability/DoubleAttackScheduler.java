package net.adeptfrog.blocksmith.ability;

import net.adeptfrog.blocksmith.compat.EntityCompat;
import net.adeptfrog.blocksmith.compat.HurtCompat;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class DoubleAttackScheduler {
    private static final List<AttackTask> TASKS = new ArrayList<>();

    public static void scheduleSecondStrike(ServerLevel level, Player player, LivingEntity target, ItemStack sword, int delayTicks) {
        TASKS.add(new AttackTask(level, player.getUUID(), target.getId(), sword.copy(), delayTicks));
    }

    public static void tick() {
        if (TASKS.isEmpty()) return;

        Iterator<AttackTask> it = TASKS.iterator();
        while (it.hasNext()) {
            AttackTask task = it.next();
            task.delay--;

            if (task.delay <= 0) {
                task.execute();
                it.remove();
            }
        }
    }

    private static class AttackTask {
        final ServerLevel level;
        final UUID playerUuid;
        final int targetId;
        final ItemStack sword;
        int delay;

        AttackTask(ServerLevel level, UUID playerUuid, int targetId, ItemStack sword, int delay) {
            this.level = level;
            this.playerUuid = playerUuid;
            this.targetId = targetId;
            this.sword = sword;
            this.delay = delay;
        }

        void execute() {
            Player player = level.getPlayerByUUID(playerUuid);
            Entity entity = level.getEntity(targetId);

            if (player == null || !player.isAlive() || !(entity instanceof LivingEntity target) || !target.isAlive()) {
                return;
            }

            // Ensure target is still within melee reach (5 blocks)
            if (player.distanceToSqr(target) > 25.0) return;

            // Reset i-frames so the second strike deals full damage
            HurtCompat.resetHurtCooldown(target);

            // Calculate sword damage and inflict second attack
            float damage = (float) player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            EntityCompat.hurt(level, target, player.damageSources().playerAttack(player), damage);

            // Visual and Audio effects
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 0.5, target.getZ(), 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + (target.getBbHeight() / 2.0), target.getZ(), 12, 0.2, 0.2, 0.2, 0.1);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 1.2F);
        }
    }
}