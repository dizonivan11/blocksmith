package net.adeptfrog.blocksmith.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Set;

/** Entity, player and projectile calls whose names differ between Minecraft versions. Minecraft 26.x version. */
public final class EntityCompat {
    private EntityCompat() {
    }

    /** Server-side damage. */
    public static boolean hurt(ServerLevel level, Entity target, DamageSource source, float amount) {
        return target.hurtServer(level, source, amount);
    }

    /** The entity's scoreboard tags (the forged bow stores arrow abilities there). */
    public static Set<String> tags(Entity entity) {
        return entity.entityTags();
    }

    /** Shows a message above the hotbar. */
    public static void actionBar(Player player, Component message) {
        player.sendOverlayMessage(message);
    }

    /** The 36 main inventory slots (hotbar included, armor and offhand excluded). */
    public static List<ItemStack> mainInventory(Player player) {
        return player.getInventory().getNonEquipmentItems();
    }

    public static boolean isOnCooldown(Player player, ItemStack stack) {
        return player.getCooldowns().isOnCooldown(stack);
    }

    public static void addCooldown(Player player, ItemStack stack, int ticks) {
        player.getCooldowns().addCooldown(stack.getItem().getDefaultInstance(), ticks);
    }

    public static boolean isArrow(Entity entity) {
        return entity instanceof AbstractArrow;
    }

    /** A plain arrow shot by the player from the given bow, ready to aim and spawn. */
    public static Projectile createArrow(ServerLevel level, Player player, ItemStack bow, boolean crit) {
        Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), bow);
        arrow.setCritArrow(crit);
        return arrow;
    }

    /** Decorative player-shaped entities that the Singularity vortex leaves alone. */
    public static boolean isMannequin(Entity entity) {
        return entity instanceof Mannequin;
    }
}
