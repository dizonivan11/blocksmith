package net.adeptfrog.blocksmith.ability;

import net.adeptfrog.blocksmith.data.MaterialGroup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

import java.util.*;

import static net.adeptfrog.blocksmith.Blocksmith.LOGGER;

public class WeaponAbilityRegistry {
    private static final Map<String, WeaponAbility> ABILITIES = new LinkedHashMap<>();
    public static Collection<WeaponAbility> getAll() {
        return Collections.unmodifiableCollection(ABILITIES.values());
    }

    // Material Groups
    private static final Map<String, MaterialGroup> GROUPS =  new LinkedHashMap<>();
    public static Collection<MaterialGroup> getAllGroups() {
        return Collections.unmodifiableCollection(GROUPS.values());
    }

    static {
        // ╔═════════════════════════╗
        // ║     MATERIAL GROUPS     ║
        // ╚═════════════════════════╝

        registerGroup(new MaterialGroup(
                "neutral",
                "netherite/flint/lazuli/quartz/iron/clay/gunpowder/coal",
                0xffdddddd,
                Identifier.fromNamespaceAndPath("minecraft", "iron_ingot")));
        registerGroup(new MaterialGroup(
                "earth",
                "moss/emerald/prismarine/slime/kelp/leaf/litter",
                0xff88cc00,
                Identifier.fromNamespaceAndPath("minecraft", "moss_block")));
        registerGroup(new MaterialGroup(
                "nether",
                "charge/copper/glowstone/blaze/bone/gold/redstone",
                0xffff8844,
                Identifier.fromNamespaceAndPath("minecraft", "blaze_rod")));
        registerGroup(new MaterialGroup(
                "tundra",
                "obsidian/glow/glacier/ice/diamond/snow",
                0xff4488ff,
                Identifier.fromNamespaceAndPath("minecraft", "ice")));
        registerGroup(new MaterialGroup(
                "end",
                "chorus/ender/mushroom/amethyst",
                0xffff55ff,
                Identifier.fromNamespaceAndPath("minecraft", "ender_pearl")));

        // ╔═════════════════════════╗
        // ║     SWORD ABILITIES     ║
        // ╚═════════════════════════╝

        register(new WeaponAbility() {
            @Override public String id() { return "valor"; }
            @Override public Component displayName() { return Component.literal("§7Valor"); }
            @Override public Component description() {
                return Component.literal(
                        "§7Increases damage for each nearby enemy.");
            }
            @Override public WeaponType weaponType() { return WeaponType.SWORD; }
            @Override public Map<MaterialGroup, Double> requiredThresholds() {
                return Map.of(GROUPS.get("neutral"), 50.0);
            }
            @Override
            public void onMeleeHit(ItemStack sword, Player player, LivingEntity target) {
                if (player.level() instanceof ServerLevel serverLevel) {
                    // Scan for nearby living enemies within 8 blocks (16x16x16 area)
                    AABB area = AABB.ofSize(player.position(), 16, 16, 16);
                    List<LivingEntity> nearbyEnemies = serverLevel.getEntitiesOfClass(LivingEntity.class, area, e ->
                            e != player && e != target && e.isAlive() && !e.isSpectator() && !player.isAlliedTo(e)
                    );
                    int enemyCount = Math.min(32, nearbyEnemies.size()); // Max +64.0 damage bonus cap

                    // Inflict +2.0 bonus damage per nearby enemy
                    if (enemyCount > 0) {
                        target.invulnerableTime = 0; // Reset i-frames so bonus damage registers
                        target.hurtServer(serverLevel, player.damageSources().playerAttack(player), enemyCount * 2.0F);
                    }
                }
            }
        });
        register(new WeaponAbility() {
            @Override public String id() { return "life"; }
            @Override public Component displayName() { return Component.literal("§2Life"); }
            @Override public Component description() {
                return Component.literal(
                        "§7Restores 10% of attack damage as health on hit.");
            }
            @Override public WeaponType weaponType() { return WeaponType.SWORD; }
            @Override public Map<MaterialGroup, Double> requiredThresholds() {
                return Map.of(GROUPS.get("earth"), 50.0);
            }
            @Override
            public void onMeleeHit(ItemStack sword, Player player, LivingEntity target) {
                if (player.getHealth() < player.getMaxHealth() && player.level() instanceof ServerLevel serverLevel) {
                    float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
                    float healAmount = damage * 0.10F; // 10% of damage dealt
                    player.heal(healAmount);

                    // Visual Heart & Emerald heal particles + audio feedback
                    serverLevel.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.25, 0.25, 0.25, 0.05);
                    serverLevel.sendParticles(ParticleTypes.CRIMSON_SPORE, target.getX(), target.getY() + (target.getBbHeight() / 2.0), target.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
                    serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7F, 1.4F);
                }
            }
        });
        register(new WeaponAbility() {
            @Override public String id() { return "teleport"; }
            @Override public Component displayName() { return Component.literal("§dTeleport"); }
            @Override public Component description() {
                return Component.literal(
                        "§7When hit by any projectile, "+
                                "instantly teleports near the attacker "+
                                "and dodges the damage instance.");
            }
            @Override public WeaponType weaponType() { return WeaponType.SWORD; }
            @Override public Map<MaterialGroup, Double> requiredThresholds() {
                return Map.of(GROUPS.get("end"), 50.0);
            }
            @Override
            public boolean onDamagedByProjectile(ItemStack sword, Player player, DamageSource source, Entity projectile, Entity attacker) {
                if (player.level() instanceof ServerLevel serverLevel) {
                    // Disabled if on cooldown
                    if (player.getCooldowns().isOnCooldown(sword)) return true;

                    RandomSource random = player.getRandom();
                    Vec3 oldPos = player.position();

                    // Tests up to 16 candidate blocks around the attacker
                    for (int i = 0; i < 16; i++) {
                        double targetX = attacker.getX() + (random.nextDouble() - 0.5) * 8.0;
                        double targetY = attacker.getY() + (random.nextInt(7) - 3);
                        double targetZ = attacker.getZ() + (random.nextDouble() - 0.5) * 8.0;

                        BlockPos targetPos = BlockPos.containing(targetX, targetY, targetZ);
                        BlockPos groundPos = targetPos.below();

                        // Validate solid non-water ground below + 2 air blocks above
                        if (serverLevel.getBlockState(groundPos).blocksMotion()
                                && !serverLevel.getBlockState(groundPos).getFluidState().is(FluidTags.WATER)
                                && serverLevel.getBlockState(targetPos).isAir()
                                && serverLevel.getBlockState(targetPos.above()).isAir()) {

                            // Teleport player near the attacker
                            player.teleportTo(targetX, targetY, targetZ);
                            player.resetFallDistance();

                            // Origin Particles & Sound
                            serverLevel.sendParticles(ParticleTypes.PORTAL, oldPos.x, oldPos.y + 0.5, oldPos.z, 30, 0.3, 0.5, 0.3, 0.1);
                            serverLevel.playSound(null, oldPos.x, oldPos.y, oldPos.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

                            // Destination Particles & Sound
                            serverLevel.sendParticles(ParticleTypes.PORTAL, targetX, targetY + 0.5, targetZ, 30, 0.3, 0.5, 0.3, 0.1);
                            serverLevel.playSound(null, targetX, targetY, targetZ, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
                            player.getCooldowns().addCooldown(sword.getItem().getDefaultInstance(), 100);
                            return false;
                        }
                    }
                }
                return true;
            }
        });
        register(new WeaponAbility() {
            @Override public String id() { return "frostbite"; }
            @Override public Component displayName() { return Component.literal("§bFrostbite"); }
            @Override public Component description() { return Component.literal(
                    "§7Applies freezing to the target, slowing their movement and attack speed. "+
                            "Hitting a fully frozen target shatters the ice dealing 10.0 damage.");
            }
            @Override public WeaponType weaponType() { return WeaponType.SWORD; }
            @Override public Map<MaterialGroup, Double> requiredThresholds() {
                return Map.of(GROUPS.get("tundra"), 50.0);
            }
            @Override
            public void onMeleeHit(ItemStack sword, Player player, LivingEntity target) {
                if (player.level() instanceof ServerLevel serverLevel) {
                    boolean wasFullyFrozen = target.isFullyFrozen();

                    // Apply freezing ticks (freezes the target rapidly)
                    int currentFreeze = target.getTicksFrozen();
                    int requiredFreeze = target.getTicksRequiredToFreeze();
                    target.setTicksFrozen(Math.min(requiredFreeze + 60, currentFreeze + 50));

                    // Ice Shatter on already-frozen enemies
                    if (wasFullyFrozen) {
                        // Reset i-frames so the bonus shatter damage registers immediately
                        target.invulnerableTime = 0;

                        // Deals +10.0 shatter damage then unfreeze the target
                        target.hurtServer(serverLevel, player.damageSources().playerAttack(player), 10.0F);
                        target.setTicksFrozen(0);

                        serverLevel.sendParticles(ParticleTypes.ITEM_SNOWBALL, target.getX(), target.getY() + (target.getBbHeight() / 2.0), target.getZ(), 25, 0.3, 0.3, 0.3, 0.1);
                        serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2F, 1.4F);
                    } else {
                        // Frost accumulation particles and sound
                        serverLevel.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + (target.getBbHeight() / 2.0), target.getZ(), 15, 0.3, 0.4, 0.3, 0.05);
                        serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.0F, 1.2F);
                    }
                }
            }
        });
        register(new WeaponAbility() {
            @Override public String id() { return "double"; }
            @Override public Component displayName() { return Component.literal("§6Double Attack"); }
            @Override public Component description() { return Component.literal(
                    "§7Performs a second strike upon hitting an entity.");
            }
            @Override public WeaponType weaponType() { return WeaponType.SWORD; }
            @Override public Map<MaterialGroup, Double> requiredThresholds() {
                return Map.of(GROUPS.get("nether"), 50.0);
            }
            @Override
            public void onMeleeHit(ItemStack sword, Player player, LivingEntity target) {
                if (player.level() instanceof ServerLevel serverLevel) {
                    double attackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
                    double fullCooldownTicks = 20.0 / Math.max(0.1, attackSpeed);
                    int midCooldownDelay = Math.max(1, (int) Math.round(fullCooldownTicks / 2.0));

                    // Schedules the second strike at the middle of the cooldown
                    DoubleAttackScheduler.scheduleSecondStrike(serverLevel, player, target, sword, midCooldownDelay);
                }
            }
        });

        // ╔═════════════════════════╗
        // ║      BOW ABILITIES      ║
        // ╚═════════════════════════╝

        register(new WeaponAbility() {
            @Override public String id() { return "auto"; }
            @Override public Component displayName() { return Component.literal("§cAuto Reload"); }
            @Override public WeaponType weaponType() { return WeaponType.BOW; }
            @Override public Map<MaterialGroup, Double> requiredThresholds() {
                return Map.of(GROUPS.get("neutral"), 20.0, GROUPS.get("nether"), 20.0);
            }
            @Override public Component description() {
                return Component.literal(
                        "§7Automatically releases fully charged arrows "+
                                "and instantly load the next arrow.");
            }
        });
        register(new WeaponAbility() {
            @Override public String id() { return "sniper"; }
            @Override public Component displayName() { return Component.literal("§2Sniper"); }
            @Override public Component description() { return Component.literal(
                    "§7Increases arrow damage and velocity at the cost of longer charging time. "+
                            "Movement will be restricted while charging.");
            }
            @Override public WeaponType weaponType() { return WeaponType.BOW; }
            @Override public Map<MaterialGroup, Double> requiredThresholds() {
                return Map.of(GROUPS.get("earth"), 50.0);
            }
            @Override
            public void onArrowFired(LivingEntity shooter, Projectile projectile, int index, float velocity, float inaccuracy, float angle, LivingEntity target) {
                // Increases arrow speed. Damage will naturally increase too since it is tied to the arrow speed
                projectile.setDeltaMovement(projectile.getDeltaMovement().scale(10));
            }
        });
        register(new WeaponAbility() {
            @Override public String id() { return "singularity"; }
            @Override public Component displayName() { return Component.literal("§5Singularity"); }
            @Override public Component description() { return Component.literal(
                    "§7Fires an arrow that create a gravitational vortex on impact, "+
                            "pulling nearby entities. Increases charging time.");
            }
            @Override public WeaponType weaponType() { return WeaponType.BOW; }
            @Override public Map<MaterialGroup, Double> requiredThresholds() {
                return Map.of(GROUPS.get("end"), 50.0);
            }
            @Override
            public void onArrowHit(AbstractArrow arrow, HitResult hitResult, ServerLevel level) {
                Vec3 center = hitResult.getLocation();
                AABB area = AABB.ofSize(center, 16, 16, 16);
                List<Entity> targets = level.getEntities((Entity) null, area, e ->
                        !(e instanceof AbstractArrow) &&
                                e.isAlive() &&
                                !e.isSpectator() &&
                                !(e instanceof Player player && player.isCreative())
                );

                for (Entity target : targets) {
                    Vec3 pull = center.subtract(target.position()).normalize().scale(0.5);
                    target.setDeltaMovement(target.getDeltaMovement().add(pull.x, pull.y, pull.z));
                    target.hurtMarked = true;
                }

                level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y + 0.5, center.z, 20, 0.2, 0.2, 0.2, 0.05);
                level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5F, 0.25F);
                level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.5F, 0.25F);

                // Start continuous gravitational vortex for 3s duration
                SingularityVortexScheduler.startVortex(level, center, arrow.getOwner(), 60);

                // Remove tag to arrow. This will make sure that only one vortex is created
                arrow.removeTag("blocksmith_ability:singularity");
            }
        });
        register(new WeaponAbility() {
            @Override public String id() { return "explosive"; }
            @Override public Component displayName() { return Component.literal("§6Explosive"); }
            @Override public Component description() { return Component.literal(
                    "§7Arrow explodes upon hitting an entity. "+
                            "Decreases arrow velocity.");
            }
            @Override public WeaponType weaponType() { return WeaponType.BOW; }
            @Override public Map<MaterialGroup, Double> requiredThresholds() {
                return Map.of(GROUPS.get("nether"), 50.0);
            }
            @Override
            public void onArrowFired(LivingEntity shooter, Projectile projectile, int index, float velocity, float inaccuracy, float angle, LivingEntity target) {
                projectile.setDeltaMovement(projectile.getDeltaMovement().scale(0.5));
            }
            @Override
            public void afterArrowHitEntity(AbstractArrow arrow, LivingEntity mob, ServerLevel level) {
                // Clears i-frames first so explosion deals damage immediately
                mob.invulnerableTime = 0;
                level.explode(arrow, arrow.getX(), arrow.getY(), arrow.getZ(), 2.5F, false, Level.ExplosionInteraction.NONE);
            }
        });
        register(new WeaponAbility() {
            @Override public String id() { return "burst"; }
            @Override public Component displayName() { return Component.literal("§7Burst Fire"); }
            @Override public Component description() { return Component.literal(
                    "§7Fires additional 2 consecutive arrows. Knockbacks will be ignored.");
            }
            @Override public WeaponType weaponType() { return WeaponType.BOW; }
            @Override public Map<MaterialGroup, Double> requiredThresholds() {
                return Map.of(GROUPS.get("neutral"), 50.0);
            }
            @Override
            public void onArrowFired(LivingEntity shooter, Projectile projectile, int index, float velocity, float inaccuracy, float angle, LivingEntity target) {
                // Resets the CANCEL_KNOCKBACK in case of crashes that leaves CANCEL_KNOCKBACK in true state
                BlocksmithCombatHooks.CANCEL_KNOCKBACK.set(false);
            }
            @Override
            public void onArrowHitEntity(AbstractArrow arrow, EntityHitResult hitResult, ServerLevel level) {
                if (hitResult instanceof EntityHitResult)
                    BlocksmithCombatHooks.CANCEL_KNOCKBACK.set(true);
            }
            @Override
            public void afterArrowHitEntity(AbstractArrow arrow, LivingEntity mob, ServerLevel level) {
                // Clears i-frames so consecutive arrows deal damage immediately
                mob.invulnerableTime = 0;
                BlocksmithCombatHooks.CANCEL_KNOCKBACK.set(false);
            }
        });

        LOGGER.info("Weapon Abilities loaded.");
    }

    public static void registerGroup(MaterialGroup group) {
        GROUPS.put(group.id(), group);
    }

    public static void register(WeaponAbility ability) {
        ABILITIES.put(ability.id(), ability);
    }

    public static WeaponAbility get(String id) {
        return ABILITIES.get(id);
    }

    public static List<WeaponAbility> getUnlockedAbilities(ItemStack stack) {
        List<WeaponAbility> unlocked = new ArrayList<>();
        if (net.adeptfrog.blocksmith.data.VoxelMaterialRegistry.areAbilitiesDisabled()) return unlocked;
        for (WeaponAbility ability : ABILITIES.values()) {
            if (ability.isUnlocked(stack)) {
                unlocked.add(ability);
            }
        }
        return unlocked;
    }

    public static boolean hasUnlockedAbilities(ItemStack stack, String ability) {
        return WeaponAbilityRegistry.getUnlockedAbilities(stack)
                .stream().anyMatch(a -> ability.equalsIgnoreCase(a.id()));
    }

    public static boolean arrowHasAbility(AbstractArrow arrow, String ability) {
        if (net.adeptfrog.blocksmith.data.VoxelMaterialRegistry.areAbilitiesDisabled()) return false;
        return arrow.entityTags().contains("blocksmith_ability:"+ability);
    }
}