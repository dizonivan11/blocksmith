package net.adeptfrog.blocksmith.ability;

import net.adeptfrog.blocksmith.network.StartVortexPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class SingularityVortexScheduler {
    private static final List<VortexTask> TASKS = new ArrayList<>();

    public static void startVortex(ServerLevel level, Vec3 center, @Nullable Entity owner, int durationTicks) {
        TASKS.add(new VortexTask(level, center, owner != null ? owner.getUUID() : null, durationTicks));

        // Send single packet to nearby clients to trigger local particle rendering
        StartVortexPayload packet = new StartVortexPayload(center.x, center.y, center.z, durationTicks);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center) < 64 * 64) {
                ServerPlayNetworking.send(player, packet);
            }
        }
    }

    public static void tick() {
        if (TASKS.isEmpty()) return;

        Iterator<VortexTask> it = TASKS.iterator();
        while (it.hasNext()) {
            VortexTask task = it.next();
            task.duration--;
            task.tick();
            if (task.duration <= 0) {
                it.remove();
            }
        }
    }

    private static class VortexTask {
        final ServerLevel level;
        final Vec3 center;
        final UUID ownerUuid;
        int duration;

        VortexTask(ServerLevel level, Vec3 center, UUID ownerUuid, int duration) {
            this.level = level;
            this.center = center;
            this.ownerUuid = ownerUuid;
            this.duration = duration;
        }

        void tick() {
            AABB area = AABB.ofSize(center, 16, 16, 16);
            Player owner = ownerUuid != null ? level.getPlayerByUUID(ownerUuid) : null;

            // Pull non-elite living entities continuously each tick
            List<Entity> targets = level.getEntities(owner, area, e ->
                    e.isAlive()
                            && !e.isSpectator()
                            && !(e instanceof Projectile)
                            && !(e instanceof Player)
                            && !(e instanceof Mannequin)
                            && !(e instanceof WitherBoss)
                            && !(e instanceof EnderDragon)
                            && !(e instanceof Warden)
                            && !(e instanceof ElderGuardian)
            );

            for (Entity target : targets) {
                Vec3 pull = center.subtract(target.position()).normalize().scale(0.15);
                target.setDeltaMovement(target.getDeltaMovement().add(pull.x, pull.y, pull.z));
                target.hurtMarked = true;
            }

            // Continuous 5x5 Center Area Damage (2 damage every 1 second / 20 ticks)
            if (duration % 20 == 0) {
                AABB damageArea = AABB.ofSize(center, 5.0, 5.0, 5.0); // 5x5x5 block zone
                List<LivingEntity> damageTargets = level.getEntitiesOfClass(LivingEntity.class, damageArea, e ->
                        e.isAlive() && !e.isSpectator()
                );

                for (LivingEntity target : damageTargets) {
                    target.invulnerableTime = 0; // Reset i-frames so damage registers
                    target.hurtServer(level, level.damageSources().magic(), 2.0F); // 2.0 Damage (1 Heart)
                }
                // Ambient sound
                level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5F, 0.25F);
            }
        }
    }
}