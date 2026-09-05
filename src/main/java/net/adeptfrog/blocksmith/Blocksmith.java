package net.adeptfrog.blocksmith;

import net.adeptfrog.blocksmith.block.WeaponTableBlock;
import net.adeptfrog.blocksmith.data.VoxelMaterialRegistry;
import net.adeptfrog.blocksmith.item.ModularBowItem;
import net.adeptfrog.blocksmith.network.SaveWeaponVoxelsPayload;
import net.fabricmc.api.ModInitializer;

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
	public static final int MIN_VOXELS = 8;
	public static final int MAX_VOXELS = 192;
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

	// 1. Create the ResourceKey first
	public static final ResourceKey<Block> WEAPON_TABLE_BLOCK_KEY = ResourceKey.create(
			Registries.BLOCK,
			Identifier.fromNamespaceAndPath(MOD_ID, "weapon_table")
	);

	public static final ResourceKey<Item> WEAPON_TABLE_ITEM_KEY = ResourceKey.create(
			Registries.ITEM,
			Identifier.fromNamespaceAndPath(MOD_ID, "weapon_table")
	);

	public static final ResourceKey<Item> MODULAR_SWORD_KEY = ResourceKey.create(
			Registries.ITEM,
			Identifier.fromNamespaceAndPath(MOD_ID, "modular_sword")
	);

	public static final ResourceKey<Item> MODULAR_BOW_KEY = ResourceKey.create(
			Registries.ITEM,
			Identifier.fromNamespaceAndPath(MOD_ID, "modular_bow")
	);

	// 2. Create the Creative Mode Tab
	public static final ResourceKey<CreativeModeTab> BLOCKSMITH_TAB_KEY = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB,
			Identifier.fromNamespaceAndPath(MOD_ID, "blocksmith_tab")
	);

	// 3. Register the blocks and items
	public static final Block WEAPON_TABLE = registerBlock(
			WEAPON_TABLE_BLOCK_KEY,
			WeaponTableBlock::new,
			BlockBehaviour.Properties.of().strength(5f).sound(SoundType.ANVIL)
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
					.component(ModDataComponents.WEAPON_VOXELS, ModularSwordItem.getDefaultVoxels())
					.attributes(ModularSwordItem.createDefaultAttributes())
					.durability(ModularSwordItem.calculateDefaultMaxDurability())
	);

	public static final Item MODULAR_BOW = registerItem(
			MODULAR_BOW_KEY,
			ModularBowItem::new,
			new Item.Properties()
					.component(ModDataComponents.WEAPON_VOXELS, ModularBowItem.getDefaultBowVoxels())
					.durability(ModularBowItem.calculateDefaultMaxDurability())
	);

	// 3. Creative Tab Instance
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
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, BLOCKSMITH_TAB_KEY, BLOCKSMITH_TAB);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
