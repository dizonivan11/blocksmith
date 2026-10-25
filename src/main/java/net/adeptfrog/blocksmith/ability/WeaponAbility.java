package net.adeptfrog.blocksmith.ability;

import net.adeptfrog.blocksmith.data.MaterialGroup;
import net.adeptfrog.blocksmith.data.WeaponVoxel;
import net.adeptfrog.blocksmith.item.ModularBowItem;
import net.adeptfrog.blocksmith.item.ModularSwordItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.adeptfrog.blocksmith.Blocksmith.*;

public interface WeaponAbility {
    enum WeaponType { SWORD, BOW, ANY }

    String id();
    Component displayName();
    default Component description() { return Component.empty(); }
    WeaponType weaponType();
    Map<MaterialGroup, Double> requiredThresholds();

    /** Minimum number of voxels a weapon needs for abilities to unlock (flat count). */
    static int minVoxelCount() {
        return (MAX_VOXELS() * ABILITY_MIN_VOXELS) / 100;
    }

    default boolean isUnlocked(ItemStack stack) {
        boolean isBow = stack.getItem() instanceof ModularBowItem;
        boolean isSword = stack.getItem() instanceof ModularSwordItem;
        if (weaponType() == WeaponType.SWORD && !isSword) return false;
        if (weaponType() == WeaponType.BOW && !isBow) return false;

        List<WeaponVoxel> voxels = isBow ? ModularBowItem.getVoxels(stack) : ModularSwordItem.getVoxels(stack);
        return isUnlockedFor(isBow, voxels);
    }

    /** Evaluates unlock conditions for an arbitrary voxel layout (used by the forging UI for live preview). */
    default boolean isUnlockedFor(boolean isBow, List<WeaponVoxel> voxels) {
        if (weaponType() == WeaponType.SWORD && isBow) return false;
        if (weaponType() == WeaponType.BOW && !isBow) return false;
        if (voxels.isEmpty() || voxels.size() < WeaponAbility.minVoxelCount()) return false;

        Map<String, Integer> counts = new HashMap<>();
        for (WeaponVoxel v : voxels) {
            counts.merge(v.materialId().toLowerCase(), 1, Integer::sum);
        }

        double total = voxels.size();
        for (Map.Entry<MaterialGroup, Double> req : requiredThresholds().entrySet()) {
            // Split shared material keys (supports "/", ",", or "|", ex. "copper/redstone")
            String[] sharedGroup = req.getKey().materials().split("[,|/]");
            int combinedCount = 0;

            for (String matId : sharedGroup) {
                combinedCount += counts.getOrDefault(matId.trim().toLowerCase(), 0);
            }

            // Check if the combined percentage of the shared materials meets the threshold
            double combinedPct = (combinedCount * 100.0) / total;
            if (combinedPct < req.getValue()) {
                return false;
            }
        }
        return true;
    }

    // Ability Events
    default void onMeleeHit(ItemStack sword, Player player, LivingEntity target) {}
    default void onArrowFired(LivingEntity shooter, Projectile projectile, int index, float velocity, float inaccuracy, float angle, LivingEntity target) {}
    default void onArrowHit(Projectile arrow, HitResult hitResult, ServerLevel level) {}
    default void onArrowHitEntity(Projectile arrow, EntityHitResult hitResult, ServerLevel level) {}
    default void onArrowHitBlock(Projectile arrow, BlockHitResult hitResult, ServerLevel level) {}
    default void afterArrowHitEntity(Projectile arrow, LivingEntity mob, ServerLevel level) {}
    default boolean onDamagedByProjectile(ItemStack weapon, Player player, DamageSource source, Entity projectile, Entity attacker) { return true; /* Return false to negate the damage */ }
}