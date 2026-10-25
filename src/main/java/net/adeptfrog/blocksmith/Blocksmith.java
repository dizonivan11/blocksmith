package net.adeptfrog.blocksmith;

import net.adeptfrog.blocksmith.ability.BurstFireScheduler;
import net.adeptfrog.blocksmith.ability.DoubleAttackScheduler;
import net.adeptfrog.blocksmith.ability.SingularityVortexScheduler;
import net.adeptfrog.blocksmith.block.WeaponTableBlock;
import net.adeptfrog.blocksmith.compat.McCompat;
import net.adeptfrog.blocksmith.data.VoxelMaterialRegistry;
import net.adeptfrog.blocksmith.item.ModularBowItem;
import net.adeptfrog.blocksmith.network.SaveWeaponVoxelsPayload;
import net.adeptfrog.blocksmith.network.StartVortexPayload;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;

import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.adeptfrog.blocksmith.component.ModDataComponents;
import net.adeptfrog.blocksmith.item.ModularSwordItem;
import net.minecraft.core.registries.BuiltInRegistries;


public class Blocksmith implements ModInitializer {
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

	public static final Block WEAPON_TABLE = McCompat.registerBlock(
			"weapon_table",
			WeaponTableBlock::new,
			BlockBehaviour.Properties.of()
					.strength(5f)
					.sound(SoundType.ANVIL)
					.lightLevel(unused2 -> 6)
	);

	public static final Item WEAPON_TABLE_ITEM = McCompat.registerItem(
			"weapon_table",
			props -> new BlockItem(WEAPON_TABLE, props),
			new Item.Properties()
	);

	public static final Item MODULAR_SWORD = McCompat.registerItem(
			"modular_sword",
			ModularSwordItem::new,
			McCompat.swordProperties(new Item.Properties())
					.durability(ModularSwordItem.calculateDefaultMaxDurability())
					.component(ModDataComponents.WEAPON_VOXELS, ModularSwordItem.getDefaultVoxels())
					.component(ModDataComponents.WEAPON_OFFSET, ModularSwordItem.getDefaultOffset())
					.attributes(ModularSwordItem.createDefaultAttributes())
	);

	public static final Item MODULAR_BOW = McCompat.registerItem(
			"modular_bow",
			ModularBowItem::new,
			McCompat.bowProperties(new Item.Properties())
					.durability(ModularBowItem.calculateDefaultMaxDurability())
					.component(ModDataComponents.WEAPON_VOXELS, ModularBowItem.getDefaultBowVoxels())
					.component(ModDataComponents.WEAPON_OFFSET, ModularBowItem.getDefaultBowOffset())
	);

	public static final CreativeModeTab BLOCKSMITH_TAB = CreativeModeTab.builder(CreativeModeTab.Row.BOTTOM, 0)
			.icon(() -> new ItemStack(WEAPON_TABLE_ITEM))
			.title(Component.translatable("itemGroup.blocksmith.blocksmith_tab"))
			.displayItems((unused1, output) -> {
				output.accept(WEAPON_TABLE_ITEM);
				output.accept(MODULAR_SWORD);
				output.accept(MODULAR_BOW);
			})
			.build();

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		VoxelMaterialRegistry.initialize();
		ModDataComponents.initialize();
		SaveWeaponVoxelsPayload.register();
		McCompat.register(BuiltInRegistries.CREATIVE_MODE_TAB, "blocksmith_tab", BLOCKSMITH_TAB);

		// Register ability timers
		ServerTickEvents.END_SERVER_TICK.register(unused3 -> {
			BurstFireScheduler.tick();
			DoubleAttackScheduler.tick();
			SingularityVortexScheduler.tick();
		});

		// Handles Singularity Ability particle spawning
		McCompat.registerClientbound(StartVortexPayload.TYPE, StartVortexPayload.STREAM_CODEC);
	}
}
