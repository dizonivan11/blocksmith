package net.adeptfrog.blocksmith.item;

import net.adeptfrog.blocksmith.component.ModDataComponents;
import net.adeptfrog.blocksmith.data.VoxelDesignSerializer;
import net.adeptfrog.blocksmith.data.WeaponVoxel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
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
        List<WeaponVoxel> defaults = getDefaultBowVoxels();
        int bonusDurability = 0;
        for (WeaponVoxel voxel : defaults) {
            bonusDurability += voxel.material().getBonusDurability();
        }
        return BASE_DURABILITY + bonusDurability;
    }

    public static void recalculateStats(ItemStack stack) {
        List<WeaponVoxel> voxels = getVoxels(stack);
        int bonusDurability = 0;

        for (WeaponVoxel voxel : voxels) {
            bonusDurability += voxel.material().getBonusDurability();
        }

        stack.set(DataComponents.MAX_DAMAGE, BASE_DURABILITY + bonusDurability);
    }

    @Override
    public boolean releaseUsing(@NonNull ItemStack stack, @NonNull Level level, @NonNull LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) {
            return false;
        }

        ItemStack projectileStack = player.getProjectile(stack);
        if (projectileStack.isEmpty()) {
            return false;
        }

        List<WeaponVoxel> voxels = getVoxels(stack);
        double bonusSpeed = 0;

        for (WeaponVoxel voxel : voxels) {
            bonusSpeed += voxel.material().getBonusSpeed();
        }

        // Draw speed multiplier (draws bow faster with speed voxels)
        float speedMultiplier = 1.0f + (float) (bonusSpeed * 5.0f);
        int useDuration = (int) ((this.getUseDuration(stack, entity) - timeLeft) * speedMultiplier);

        float power = getPowerForTime(useDuration);
        if ((double) power < 0.1) {
            return false;
        }

        List<ItemStack> list = draw(stack, projectileStack, player);
        if (level instanceof ServerLevel serverLevel && !list.isEmpty()) {
            this.shoot(serverLevel, player, player.getUsedItemHand(), stack, list, power * 3.0F, 1.0F, power == 1.0F, null);
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
                double bonusDamage = 0;
                for (WeaponVoxel voxel : getVoxels(bow)) {
                    bonusDamage += voxel.material().getBonusDamage();
                }
                // Default vanilla arrow base damage is 2.0
                arrow.setBaseDamage(2.0 + bonusDamage);
            }
        }
    }

    public static List<WeaponVoxel> getDefaultBowVoxels() {
        return VoxelDesignSerializer.getDefaultBowVoxels();
    }
}