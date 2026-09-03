package net.adeptfrog.blocksmith;

import net.adeptfrog.blocksmith.block.WeaponTableBlock;
import net.adeptfrog.blocksmith.network.SaveWeaponVoxelsPayload;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.resources.Identifier;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.adeptfrog.blocksmith.component.ModDataComponents;
import net.adeptfrog.blocksmith.item.ModularSwordItem;
import net.adeptfrog.blocksmith.network.ModifyWeaponPayload;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

import java.util.function.Function;

public class Blocksmith implements ModInitializer {
	public static final String MOD_ID = "blocksmith";

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

	public static void registerToCreativeTab(Item item) {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.accept(item);
		});
	}

	// 1. Create the ResourceKey first
	public static final ResourceKey<Item> MODULAR_SWORD_KEY = ResourceKey.create(
			Registries.ITEM,
			Identifier.fromNamespaceAndPath(MOD_ID, "modular_sword")
	);

	// 2. Register with setId attached to Item.Properties
	public static final Item MODULAR_SWORD = registerItem(
			MODULAR_SWORD_KEY,
			ModularSwordItem::new,
			new Item.Properties()
					.durability(ToolMaterial.NETHERITE.durability())
					.sword(ToolMaterial.NETHERITE, 0f, 0f)
					.component(ModDataComponents.WEAPON_VOXELS, ModularSwordItem.getDefaultVoxels())
					.attributes(ModularSwordItem.createDefaultAttributes())
	);

	public static final ResourceKey<Block> WEAPON_TABLE_BLOCK_KEY = ResourceKey.create(
			Registries.BLOCK,
			Identifier.fromNamespaceAndPath(MOD_ID, "weapon_table")
	);

	public static final Block WEAPON_TABLE = registerBlock(
			WEAPON_TABLE_BLOCK_KEY,
			WeaponTableBlock::new,
			BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.METAL)
	);

	public static final ResourceKey<Item> WEAPON_TABLE_ITEM_KEY = ResourceKey.create(
			Registries.ITEM,
			Identifier.fromNamespaceAndPath(MOD_ID, "weapon_table")
	);

	public static final Item WEAPON_TABLE_ITEM = registerItem(
			WEAPON_TABLE_ITEM_KEY,
			props -> new BlockItem(WEAPON_TABLE, props),
			new Item.Properties()
	);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		ModDataComponents.initialize();
		SaveWeaponVoxelsPayload.register();
		registerToCreativeTab(MODULAR_SWORD);
		registerToCreativeTab(WEAPON_TABLE_ITEM);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
