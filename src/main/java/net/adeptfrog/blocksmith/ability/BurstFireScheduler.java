package net.adeptfrog.blocksmith.ability;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class BurstFireScheduler {
    private static final List<BurstTask> TASKS = new ArrayList<>();

    public static void scheduleBurst(ServerLevel level, Player player, ItemStack bow, float power) {
        // Fires 2 additional follow-up arrows
        TASKS.add(new BurstTask(level, player.getUUID(), bow.copy(), power, 2, 3));
    }

    public static void tick() {
        if (TASKS.isEmpty()) return;

        Iterator<BurstTask> it = TASKS.iterator();
        while (it.hasNext()) {
            BurstTask task = it.next();
            task.timer--;

            if (task.timer <= 0) {
                task.fireShot();
                task.shotsLeft--;
                task.timer = 5; // 5 ticks = 250ms interval

                if (task.shotsLeft <= 0) {
                    it.remove();
                }
            }
        }
    }

    private static class BurstTask {
        final ServerLevel level;
        final UUID playerUuid;
        final ItemStack bow;
        final float power;
        int shotsLeft;
        int timer;

        BurstTask(ServerLevel level, UUID playerUuid, ItemStack bow, float power, int shotsLeft, int timer) {
            this.level = level;
            this.playerUuid = playerUuid;
            this.bow = bow;
            this.power = power;
            this.shotsLeft = shotsLeft;
            this.timer = timer;
        }

        void fireShot() {
            Player player = level.getPlayerByUUID(playerUuid);
            if (player == null || !player.isAlive()) return;

            Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), bow);
            arrow.addTag("blocksmith_ability:burst");
            arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, power * 3.0F, 1.0F);
            arrow.setCritArrow(power >= 1.0F);

            level.addFreshEntity(arrow);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.2F);
        }
    }
}