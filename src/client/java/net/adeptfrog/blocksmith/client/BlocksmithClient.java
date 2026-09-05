package net.adeptfrog.blocksmith.client;

import net.adeptfrog.blocksmith.Blocksmith;
import net.adeptfrog.blocksmith.client.render.ModularWeaponSpecialRenderer;
import net.adeptfrog.blocksmith.item.ModularBowItem;
import net.fabricmc.api.ClientModInitializer;

import net.adeptfrog.blocksmith.client.gui.CustomizationScreen;
import net.adeptfrog.blocksmith.item.ModularSwordItem;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class BlocksmithClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.

		// Register the special renderer type for JSON models
		SpecialModelRenderers.ID_MAPPER.put(
				Identifier.fromNamespaceAndPath(Blocksmith.MOD_ID, "modular_weapon"),
				ModularWeaponSpecialRenderer.Unbaked.MAP_CODEC
		);

		// Client-side block interaction listener
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (level.isClientSide()) {
				BlockState state = level.getBlockState(hitResult.getBlockPos());

				if (state.is(Blocksmith.WEAPON_TABLE)) {
					ItemStack held = player.getItemInHand(hand);
					if (held.getItem() instanceof ModularSwordItem || held.getItem() instanceof ModularBowItem) {
						Minecraft.getInstance().setScreen(new CustomizationScreen(held));
						return InteractionResult.SUCCESS;
					} else {
						player.sendOverlayMessage(Component.literal("§eHold a modular weapon in your hand to customize it!"));
						return InteractionResult.CONSUME;
					}
				}
			}
			return InteractionResult.PASS;
		});
	}
}