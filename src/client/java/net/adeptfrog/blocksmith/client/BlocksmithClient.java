package net.adeptfrog.blocksmith.client;

import net.adeptfrog.blocksmith.client.utils.InputCompat;
import net.adeptfrog.blocksmith.Blocksmith;
import net.adeptfrog.blocksmith.client.compat.ClientCompat;
import net.adeptfrog.blocksmith.compat.EntityCompat;
import net.adeptfrog.blocksmith.ability.WeaponAbility;
import net.adeptfrog.blocksmith.ability.WeaponAbilityRegistry;
import net.adeptfrog.blocksmith.client.render.ClientVortexHandler;
import net.adeptfrog.blocksmith.item.ModularBowItem;
import net.adeptfrog.blocksmith.network.BlocksmithNetwork;

import net.adeptfrog.blocksmith.client.gui.CustomizationScreen;
import net.adeptfrog.blocksmith.item.ModularSwordItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Client setup and the client's event handlers, independent of the mod loader. The version's
 * {@code BlocksmithForgeClient} calls these from Forge's events.
 */
public final class BlocksmithClient {
	private BlocksmithClient() {
	}

	/** Runs once while the mod is constructed on the client. */
	public static void init() {
		// Voxel renderer for the forged weapons (registration differs per Minecraft version)
		ClientCompat.registerWeaponRenderer();

		// Listen for server vortex start packet
		BlocksmithNetwork.onStartVortex = payload ->
				ClientVortexHandler.addVortex(new Vec3(payload.x(), payload.y(), payload.z()), payload.duration());

		// Use the connected server's Dynamic Stats / Weapon Abilities settings
		ServerSettingsClient.register();
	}

	/** End of every client tick. */
	public static void onClientTick(Minecraft client) {
		ClientVortexHandler.tick(client);
		ServerSettingsClient.tick(client);
	}

	/** The client left a world or server. */
	public static void onDisconnect() {
		ServerSettingsClient.clear();
	}

	/**
	 * Right-clicking a block. Anything but {@link InteractionResult#PASS} stops the click there, with that result.
	 */
	public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
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
	}

	/** Adds the ability lines to a forged weapon's tooltip. */
	public static void onTooltip(ItemStack stack, List<Component> lines) {
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
	}
}
