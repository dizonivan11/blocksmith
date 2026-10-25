package net.adeptfrog.blocksmith.client;

import net.adeptfrog.blocksmith.client.utils.InputCompat;
import net.adeptfrog.blocksmith.Blocksmith;
import net.adeptfrog.blocksmith.client.compat.ClientCompat;
import net.adeptfrog.blocksmith.compat.EntityCompat;
import net.adeptfrog.blocksmith.ability.WeaponAbility;
import net.adeptfrog.blocksmith.ability.WeaponAbilityRegistry;
import net.adeptfrog.blocksmith.client.render.ClientVortexHandler;
import net.adeptfrog.blocksmith.item.ModularBowItem;
import net.adeptfrog.blocksmith.network.StartVortexPayload;
import net.fabricmc.api.ClientModInitializer;

import net.adeptfrog.blocksmith.client.gui.CustomizationScreen;
import net.adeptfrog.blocksmith.item.ModularSwordItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class BlocksmithClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.

		// Voxel renderer for the forged weapons (registration differs per Minecraft version)
		ClientCompat.registerWeaponRenderer();

		// Listen for server vortex start packet & tick client particles
		ClientPlayNetworking.registerGlobalReceiver(StartVortexPayload.TYPE, (payload, context) ->
				context.client().execute(() ->
						ClientVortexHandler.addVortex(new Vec3(payload.x(), payload.y(), payload.z()), payload.duration())));
		ClientTickEvents.END_CLIENT_TICK.register(ClientVortexHandler::tick);

		// Client-side block interaction listener
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (level.isClientSide()) {
				BlockState state = level.getBlockState(hitResult.getBlockPos());

				if (state.is(Blocksmith.WEAPON_TABLE)) {
					ItemStack held = player.getItemInHand(hand);
					if (held.getItem() instanceof ModularSwordItem || held.getItem() instanceof ModularBowItem) {
						InputCompat.openScreen(new CustomizationScreen(held));
						return InteractionResult.SUCCESS;
					} else {
						EntityCompat.actionBar(player, Component.literal("§eHold a forged weapon in your hand to customize it!"));
						return InteractionResult.CONSUME;
					}
				}
			}
			return InteractionResult.PASS;
		});

		// Register custom tooltip for Forged weapons
		ItemTooltipCallback.EVENT.register((stack, unused4, unused5, lines) -> {
			boolean isSword = stack.getItem() instanceof ModularSwordItem;
			boolean isBow = stack.getItem() instanceof ModularBowItem;
			boolean isModular = isSword || isBow;
			if (isModular) {
				List<WeaponAbility> abilities = WeaponAbilityRegistry.getUnlockedAbilities(stack);

				if (!abilities.isEmpty()) {
					boolean holdingAlt = InputCompat.altDown();

					// Header prompt
					lines.add(Component.literal(holdingAlt ? "§6✦ Abilities:" : "§6✦ Abilities: §8(Hold Alt for Info)"));

					for (WeaponAbility ability : abilities) {
						lines.add(Component.literal("  ").append(ability.displayName()));

						// Show description indented below the ability name when Alt is pressed
						if (holdingAlt && !ability.description().getString().isEmpty()) {
							Font font = Minecraft.getInstance().font;
							for (FormattedText wrappedLine : font.getSplitter().splitLines(ability.description(), 165, Style.EMPTY)) {
								lines.add(Component.literal("    §7" + wrappedLine.getString().trim()));
							}
						}
					}
				}
			}
		});
	}
}