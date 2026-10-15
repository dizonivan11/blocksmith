package net.adeptfrog.blocksmith.item;

import net.adeptfrog.blocksmith.ability.BurstFireScheduler;
import net.adeptfrog.blocksmith.ability.WeaponAbility;
import net.adeptfrog.blocksmith.ability.WeaponAbilityRegistry;
import net.adeptfrog.blocksmith.component.ModDataComponents;
import net.adeptfrog.blocksmith.data.StatScaling;
import net.adeptfrog.blocksmith.data.VoxelDesignSerializer;
import net.adeptfrog.blocksmith.data.VoxelMaterialRegistry;
import net.adeptfrog.blocksmith.data.WeaponOffset;
import net.adeptfrog.blocksmith.data.WeaponVoxel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

import static net.adeptfrog.blocksmith.Blocksmith.BASE_DURABILITY;

public class ModularBowItem extends BowItem {
    public static final double BONUS_BASE_DAMAGE = 4.0F;

    public ModularBowItem(Properties properties) {
        super(properties);
    }

    public static List<WeaponVoxel> getVoxels(ItemStack stack) {
        List<WeaponVoxel> list = stack.get(ModDataComponents.WEAPON_VOXELS);
        return (list != null && !list.isEmpty()) ? list : getDefaultBowVoxels();
    }

    public static void saveVoxels(ItemStack stack, List<WeaponVoxel> voxels) {
        stack.set(ModDataComponents.WEAPON_VOXELS, new ArrayList<>(voxels));
        recalculateStats(stack);
    }

    public static int calculateDefaultMaxDurability() {
        VoxelMaterialRegistry.initialize();
        List<WeaponVoxel> defaults = getDefaultBowVoxels();
        int gridSize = VoxelMaterialRegistry.getGridSize();
        double gridArea = gridSize * gridSize;

        double rawDurability = 0;
        for (WeaponVoxel voxel : defaults) {
            rawDurability += voxel.material().getBonusDurability();
        }
        return BASE_DURABILITY + (int) Math.round(StatScaling.scale(rawDurability, defaults.size()) / gridArea);
    }

    public static void recalculateStats(ItemStack stack) {
        List<WeaponVoxel> voxels = getVoxels(stack);
        int gridSize = VoxelMaterialRegistry.getGridSize();
        double gridArea = gridSize * gridSize;

        double rawDurability = 0;
        for (WeaponVoxel voxel : voxels) {
            rawDurability += voxel.material().getBonusDurability();
        }

        int bonusDurability = BASE_DURABILITY + (int) Math.round(StatScaling.scale(rawDurability, voxels.size()) / gridArea);
        stack.set(DataComponents.MAX_DAMAGE, bonusDurability);
    }

    @Override
    public void onUseTick(@NonNull Level level, @NonNull LivingEntity entity, @NonNull ItemStack stack, int remainingUseDuration) {
        if (entity instanceof Player player && !level.isClientSide()) {
            int useTicks = this.getUseDuration(stack, entity) - remainingUseDuration;
            int requiredTicks = getRequiredDrawTicks(stack);

            // Check if the bow has reached 100% full draw power
            if (useTicks >= requiredTicks) {
                if (WeaponAbilityRegistry.hasUnlockedAbilities(stack, "auto")) {
                    player.releaseUsingItem();
                    // Re-arm delay: arm lowers and reaches for the next arrow
                    player.getCooldowns().addCooldown(stack.getItem().getDefaultInstance(), 5);
                    return;
                }
            }

            // Immobilize user when charging an arrow with Sniper ability
            if (WeaponAbilityRegistry.hasUnlockedAbilities(stack, "sniper")) {
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 255, false, false, false));
                player.setDeltaMovement(0.0, Math.min(0.0, player.getDeltaMovement().y), 0.0);
            }
        }
        super.onUseTick(level, entity, stack, remainingUseDuration);
    }

    @Override
    public boolean releaseUsing(@NonNull ItemStack stack, @NonNull Level level, @NonNull LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return false;

        ItemStack projectileStack = player.getProjectile(stack);
        if (projectileStack.isEmpty()) return false;

        int actualTicksHeld = this.getUseDuration(stack, entity) - timeLeft;
        float power = Math.min(1.0F, (float) actualTicksHeld / getRequiredDrawTicks(stack));
        if ((double) power < 0.1) return false;

        List<ItemStack> list = draw(stack, projectileStack, player);
        if (level instanceof ServerLevel serverLevel && !list.isEmpty()) {
            float uncertainty = 1.0F;

            // If Sniper ability is active and arrow is charged at full power, arrow will be accurate
            if (WeaponAbilityRegistry.hasUnlockedAbilities(stack, "sniper")) {
                uncertainty = 0.0F;
            }

            // Shoot the arrow
            this.shoot(serverLevel, player, player.getUsedItemHand(), stack, list, power * 3.0F, uncertainty, power == 1.0F, null);

            // If Burst Fire ability is active and arrow is charged at full power, schedule additional arrows
            if (power >= 1.0F) {
                if (WeaponAbilityRegistry.hasUnlockedAbilities(stack, "burst")) {
                    BurstFireScheduler.scheduleBurst(serverLevel, player, stack, power);
                }
            }
        }

        level.playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARROW_SHOOT,
                SoundSource.PLAYERS,
                1.0F,
                1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + power * 0.5F
        );

        player.awardStat(Stats.ITEM_USED.get(this));
        return true;
    }

    @Override
    protected void shootProjectile(@NonNull LivingEntity shooter, @NonNull Projectile projectile, int index, float velocity, float inaccuracy, float angle, @Nullable LivingEntity target) {
        super.shootProjectile(shooter, projectile, index, velocity, inaccuracy, angle, target);

        // Apply bonus voxel damage to fired arrows
        if (projectile instanceof AbstractArrow arrow) {
            ItemStack bow = shooter.getMainHandItem().getItem() instanceof ModularBowItem
                    ? shooter.getMainHandItem()
                    : shooter.getOffhandItem();

            if (bow.getItem() instanceof ModularBowItem) {
                // Fire all unlocked WeaponAbility's onArrowFired method
                // Assign WeaponAbility tag to the arrow for onArrowHit method
                if (velocity >= 3.0F) {
                    for (WeaponAbility ability : WeaponAbilityRegistry.getUnlockedAbilities(bow)) {
                        if (ability.weaponType() == WeaponAbility.WeaponType.BOW || ability.weaponType() == WeaponAbility.WeaponType.ANY) {
                            ability.onArrowFired(shooter, projectile, index, velocity, inaccuracy, angle, target);
                            arrow.addTag("blocksmith_ability:" + ability.id());
                        }
                    }
                }
            }
        }
    }

    public static List<WeaponVoxel> getDefaultBowVoxels() {
        return VoxelDesignSerializer.getDefaultBowVoxels();
    }

    public static WeaponOffset getOffset(ItemStack stack) {
        WeaponOffset offset = stack.get(ModDataComponents.WEAPON_OFFSET);
        return offset != null ? offset : getDefaultBowOffset();
    }

    public static WeaponOffset getDefaultBowOffset() {
        return VoxelDesignSerializer.getDefaultBowOffset();
    }

    // Calculates draw speed from voxel materials and abilities
    public static float getDrawSpeedMultiplier(ItemStack stack) {
        List<WeaponVoxel> voxels = getVoxels(stack);
        int gridSize = VoxelMaterialRegistry.getGridSize();
        double gridArea = gridSize * gridSize;

        // Calculate voxel speed bonus
        double rawSpeed = 0;
        for (WeaponVoxel voxel : voxels) {
            rawSpeed += voxel.material().getBonusSpeed();
        }
        double bonusSpeed = StatScaling.scale(rawSpeed, voxels.size()) / gridArea;
        float speedMultiplier = 1.0F + (float) bonusSpeed;

        // Apply ability modifiers
        if (WeaponAbilityRegistry.hasUnlockedAbilities(stack, "sniper")) {
            // Sniper draws 10x slower
            speedMultiplier *= (1.0F / 10.0F);
        }
        if (WeaponAbilityRegistry.hasUnlockedAbilities(stack, "singularity")) {
            // Singularity draws 3x slower
            speedMultiplier *= (1.0F / 3.0F);
        }

        return Math.max(0.05F, speedMultiplier);
    }

    // Returns the exact integer ticks required to reach 100% full draw charge (Vanilla = 20 ticks)
    public static int getRequiredDrawTicks(ItemStack stack) {
        float multiplier = getDrawSpeedMultiplier(stack);
        return Math.max(1, Math.round(20.0F / multiplier));
    }
}