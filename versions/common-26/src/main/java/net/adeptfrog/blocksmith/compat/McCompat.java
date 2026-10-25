package net.adeptfrog.blocksmith.compat;

import net.adeptfrog.blocksmith.Blocksmith;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.Function;

/**
 * Registry, id and item-property calls whose names differ between Minecraft versions, so the shared code never
 * names them directly. Minecraft 26.x version (Identifier, ids set on properties, ToolMaterial).
 */
public final class McCompat {
    private McCompat() {
    }

    /** Validates and normalises an item or block id ("stick" becomes "minecraft:stick"); throws if invalid. */
    public static String checkId(String id) {
        return Identifier.parse(id).toString();
    }

    /** The item with this id ("minecraft:stick"), or air if there is none. */
    public static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    }

    public static <T extends Block> T registerBlock(String path, Function<BlockBehaviour.Properties, T> factory, BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Blocksmith.MOD_ID, path));
        return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
    }

    public static <T extends Item> T registerItem(String path, Function<Item.Properties, T> factory, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Blocksmith.MOD_ID, path));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
    }

    /** Registers a mod object (data component, creative tab, ...) under blocksmith:path. */
    public static <V, T extends V> T register(Registry<V> registry, String path, T value) {
        return Registry.register(registry, Identifier.fromNamespaceAndPath(Blocksmith.MOD_ID, path), value);
    }

    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Blocksmith.MOD_ID, path));
    }

    public static <T extends CustomPacketPayload> void registerServerbound(CustomPacketPayload.Type<T> type, StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        PayloadTypeRegistry.serverboundPlay().register(type, codec);
    }

    public static <T extends CustomPacketPayload> void registerClientbound(CustomPacketPayload.Type<T> type, StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        PayloadTypeRegistry.clientboundPlay().register(type, codec);
    }

    /** Flat attribute bonus with a vanilla id ("base_attack_damage" makes the tooltip show it like a vanilla weapon's). */
    public static AttributeModifier vanillaModifier(String vanillaPath, double amount) {
        return new AttributeModifier(Identifier.withDefaultNamespace(vanillaPath), amount, AttributeModifier.Operation.ADD_VALUE);
    }

    public static int netheriteDurability() {
        return ToolMaterial.NETHERITE.durability();
    }

    /** Sword behaviour (netherite tool material, enchantability) on top of the given properties. */
    public static Item.Properties swordProperties(Item.Properties properties) {
        return properties.sword(ToolMaterial.NETHERITE, 0f, 0f).enchantable(ToolMaterial.NETHERITE.enchantmentValue());
    }

    public static Item.Properties bowProperties(Item.Properties properties) {
        return properties.enchantable(1);
    }

    public static Holder<MobEffect> slowness() {
        return MobEffects.SLOWNESS;
    }
}
