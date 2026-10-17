package net.adeptfrog.blocksmith;

import net.adeptfrog.blocksmith.ability.BurstFireScheduler;
import net.adeptfrog.blocksmith.ability.DoubleAttackScheduler;
import net.adeptfrog.blocksmith.ability.SingularityVortexScheduler;
import net.adeptfrog.blocksmith.block.VoxelCubeBlock;
import net.adeptfrog.blocksmith.block.WeaponTableBlock;
import net.adeptfrog.blocksmith.dimension.DimensionalEditor;
import net.adeptfrog.blocksmith.item.VoxelBrushItem;
import net.adeptfrog.blocksmith.data.VoxelMaterialRegistry;
import net.adeptfrog.blocksmith.item.ModularBowItem;
import net.adeptfrog.blocksmith.network.SaveWeaponVoxelsPayload;
import net.adeptfrog.blocksmith.network.StartVortexPayload;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.adeptfrog.blocksmith.component.ModDataComponents;
import net.adeptfrog.blocksmith.item.ModularSwordItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

import java.util.function.Function;

public class Blocksmith implements ModInitializer {
	public static final String MOD_ID = "blocksmith";
	public static int BASE_GRID_SIZE = 32;
	public static final int MIN_VOXELS = 8;
	/** Voxel capacity of a SINGLE layer (grid x grid). Ability thresholds are relative to this. */
	public static int MAX_VOXELS() { return Math.powExact(VoxelMaterialRegistry.getGridSize(), 2); }

	// Layers extend outward from the base layer (z = 0): -2, -1, 0, +1, +2. Every layer is the full grid size.
	public static final int MAX_LAYER = 2;
	public static final int MIN_LAYER = -MAX_LAYER;
	public static final int LAYER_COUNT = MAX_LAYER * 2 + 1;
	/** Voxel capacity across all layers. */
	public static int MAX_TOTAL_VOXELS() { return Math.multiplyExact(MAX_VOXELS(), LAYER_COUNT); }
	public static int ABILITY_MIN_VOXELS = 25; // In percentage
	public static final int BASE_DURABILITY = ToolMaterial.NETHERITE.durability();

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static <T extends Block> T registerBlock(ResourceKey<Block> key, Function<BlockBehaviour.Properties, T> factory, BlockBehaviour.Properties properties) {
		T block = factory.apply(properties.setId(key));
		return Registry.register(BuiltInRegistries.BLOCK, key, block);
	}

	public static <T extends Item> T registerItem(ResourceKey<Item> key, Function<Item.Properties, T> factory, Item.Properties properties) {
		T item = factory.apply(properties.setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static final ResourceKey<Block> WEAPON_TABLE_BLOCK_KEY = ResourceKey.create(
			Registries.BLOCK,
			Identifier.fromNamespaceAndPath(MOD_ID, "weapon_table")
	);

	public static final ResourceKey<Item> WEAPON_TABLE_ITEM_KEY = ResourceKey.create(
			Registries.ITEM,
			Identifier.fromNamespaceAndPath(MOD_ID, "weapon_table")
	);

	public static final ResourceKey<Block> VOXEL_CUBE_KEY = ResourceKey.create(
			Registries.BLOCK,
			Identifier.fromNamespaceAndPath(MOD_ID, "voxel_cube")
	);

	public static final ResourceKey<Item> VOXEL_BRUSH_KEY = ResourceKey.create(
			Registries.ITEM,
			Identifier.fromNamespaceAndPath(MOD_ID, "voxel_brush")
	);

	public static final ResourceKey<Item> MODULAR_SWORD_KEY = ResourceKey.create(
			Registries.ITEM,
			Identifier.fromNamespaceAndPath(MOD_ID, "modular_sword")
	);

	public static final ResourceKey<Item> MODULAR_BOW_KEY = ResourceKey.create(
			Registries.ITEM,
			Identifier.fromNamespaceAndPath(MOD_ID, "modular_bow")
	);

	public static final ResourceKey<CreativeModeTab> BLOCKSMITH_TAB_KEY = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB,
			Identifier.fromNamespaceAndPath(MOD_ID, "blocksmith_tab")
	);

	public static final Block WEAPON_TABLE = registerBlock(
			WEAPON_TABLE_BLOCK_KEY,
			WeaponTableBlock::new,
			BlockBehaviour.Properties.of()
					.strength(5f)
					.sound(SoundType.ANVIL)
					.lightLevel(_ -> 6)
	);

	/** A weapon voxel as a block; only used inside the Dimensional Editor (no item form). */
	public static final VoxelCubeBlock VOXEL_CUBE = registerBlock(
			VOXEL_CUBE_KEY,
			VoxelCubeBlock::new,
			BlockBehaviour.Properties.of()
					.strength(0.0f)
					.sound(SoundType.STONE)
					.lightLevel(_ -> 15)
	);

	/** The tool used to place voxels in the Dimensional Editor. Handed out on entry, removed on exit. */
	public static final Item VOXEL_BRUSH = registerItem(
			VOXEL_BRUSH_KEY,
			VoxelBrushItem::new,
			new Item.Properties().stacksTo(1)
	);

	public static final Item WEAPON_TABLE_ITEM = registerItem(
			WEAPON_TABLE_ITEM_KEY,
			props -> new BlockItem(WEAPON_TABLE, props),
			new Item.Properties()
	);

	public static final Item MODULAR_SWORD = registerItem(
			MODULAR_SWORD_KEY,
			ModularSwordItem::new,
			new Item.Properties()
					.sword(ToolMaterial.NETHERITE, 0f, 0f)
					.durability(ModularSwordItem.calculateDefaultMaxDurability())
					.enchantable(ToolMaterial.NETHERITE.enchantmentValue())
					.component(ModDataComponents.WEAPON_VOXELS, ModularSwordItem.getDefaultVoxels())
					.component(ModDataComponents.WEAPON_OFFSET, ModularSwordItem.getDefaultOffset())
					.attributes(ModularSwordItem.createDefaultAttributes())
	);

	public static final Item MODULAR_BOW = registerItem(
			MODULAR_BOW_KEY,
			ModularBowItem::new,
			new Item.Properties()
					.durability(ModularBowItem.calculateDefaultMaxDurability())
					.enchantable(1)
					.component(ModDataComponents.WEAPON_VOXELS, ModularBowItem.getDefaultBowVoxels())
					.component(ModDataComponents.WEAPON_OFFSET, ModularBowItem.getDefaultBowOffset())
	);

	public static final CreativeModeTab BLOCKSMITH_TAB = CreativeModeTab.builder(CreativeModeTab.Row.BOTTOM, 0)
			.icon(() -> new ItemStack(WEAPON_TABLE_ITEM))
			.title(Component.translatable("itemGroup.blocksmith.blocksmith_tab"))
			.displayItems((_, output) -> {
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
		DimensionalEditor.register();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, BLOCKSMITH_TAB_KEY, BLOCKSMITH_TAB);

		// Register ability timers
		ServerTickEvents.END_SERVER_TICK.register(_ -> {
			BurstFireScheduler.tick();
			DoubleAttackScheduler.tick();
			SingularityVortexScheduler.tick();
		});

		// Handles Singularity Ability particle spawning
		PayloadTypeRegistry.clientboundPlay().register(StartVortexPayload.TYPE, StartVortexPayload.STREAM_CODEC);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
