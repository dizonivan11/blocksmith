package net.adeptfrog.blocksmith;

import net.adeptfrog.blocksmith.ability.BlocksmithCombatHooks;
import net.adeptfrog.blocksmith.ability.BurstFireScheduler;
import net.adeptfrog.blocksmith.ability.DoubleAttackScheduler;
import net.adeptfrog.blocksmith.ability.SingularityVortexScheduler;
import net.adeptfrog.blocksmith.block.WeaponTableBlock;
import net.adeptfrog.blocksmith.compat.McCompat;
import net.adeptfrog.blocksmith.data.VoxelMaterialRegistry;
import net.adeptfrog.blocksmith.item.ModularBowItem;

import net.minecraft.network.chat.Component;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.adeptfrog.blocksmith.component.ModDataComponents;
import net.adeptfrog.blocksmith.item.ModularSwordItem;
import net.minecraft.core.registries.BuiltInRegistries;


public final class Blocksmith {
	public static final String MOD_ID = "blocksmith";
	public static int BASE_GRID_SIZE = 32;
	public static final int MIN_VOXELS = 8;
	/** Voxel capacity of a SINGLE layer (grid x grid). Ability thresholds are relative to this. */
	public static int MAX_VOXELS() { return Math.multiplyExact(VoxelMaterialRegistry.getGridSize(), VoxelMaterialRegistry.getGridSize()); }

	// Layers extend outward from the base layer (z = 0): -8 .. +8 (seventeen layers). Every layer is the full grid size.
	public static final int MAX_LAYER = 8;
	public static final int MIN_LAYER = -MAX_LAYER;
	public static final int LAYER_COUNT = MAX_LAYER * 2 + 1;
	/** Voxel capacity across all layers. */
	public static int MAX_TOTAL_VOXELS() { return Math.multiplyExact(MAX_VOXELS(), LAYER_COUNT); }
	public static int ABILITY_MIN_VOXELS = 25; // In percentage
	public static final int BASE_DURABILITY = McCompat.netheriteDurability();

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Registered objects. Forge only accepts registrations during its RegisterEvent, so these are created there
	// (see the version's loader entry point) instead of when this class loads.
	public static Block WEAPON_TABLE;
	public static Item WEAPON_TABLE_ITEM;
	public static Item MODULAR_SWORD;
	public static Item MODULAR_BOW;
	public static CreativeModeTab BLOCKSMITH_TAB;

	private Blocksmith() {
	}

	/** Loads the config before anything that reads it (item default stats) is created. */
	public static void initialize() {
		VoxelMaterialRegistry.initialize();
	}

	public static void registerBlocks() {
		WEAPON_TABLE = McCompat.registerBlock(
				"weapon_table",
				WeaponTableBlock::new,
				BlockBehaviour.Properties.of()
						.strength(5f)
						.sound(SoundType.ANVIL)
						.lightLevel(unused2 -> 6)
		);
	}

	public static void registerItems() {
		WEAPON_TABLE_ITEM = McCompat.registerItem(
				"weapon_table",
				props -> new BlockItem(WEAPON_TABLE, props),
				new Item.Properties()
		);

		MODULAR_SWORD = McCompat.registerItem(
				"modular_sword",
				ModularSwordItem::new,
				McCompat.swordProperties(new Item.Properties())
						.durability(ModularSwordItem.calculateDefaultMaxDurability())
						.attributes(ModularSwordItem.createDefaultAttributes())
		);

		MODULAR_BOW = McCompat.registerItem(
				"modular_bow",
				ModularBowItem::new,
				McCompat.bowProperties(new Item.Properties())
						.durability(ModularBowItem.calculateDefaultMaxDurability())
		);
	}

	public static void registerDataComponents() {
		ModDataComponents.register();
	}

	public static void registerCreativeTabs() {
		BLOCKSMITH_TAB = McCompat.register(BuiltInRegistries.CREATIVE_MODE_TAB, "blocksmith_tab", CreativeModeTab.builder()
				.icon(() -> new ItemStack(WEAPON_TABLE_ITEM))
				.title(Component.translatable("itemGroup.blocksmith.blocksmith_tab"))
				.displayItems((unused1, output) -> {
					output.accept(WEAPON_TABLE_ITEM);
					output.accept(MODULAR_SWORD);
					output.accept(MODULAR_BOW);
				})
				.build());
	}

	/** Ability timers: called at the end of every server tick. */
	public static void onServerTick() {
		BurstFireScheduler.tick();
		DoubleAttackScheduler.tick();
		SingularityVortexScheduler.tick();
	}

	/** Drops pending ability tasks: they hold the old world and must not carry into the next one. */
	public static void onServerStopped() {
		BurstFireScheduler.clear();
		DoubleAttackScheduler.clear();
		SingularityVortexScheduler.clear();
	}

	/** Remembers how charged each swing is: melee abilities only fire on charged swings. */
	public static void onAttackEntity(Player player) {
		if (!player.level().isClientSide()) BlocksmithCombatHooks.beforeAttack(player);
	}
}
