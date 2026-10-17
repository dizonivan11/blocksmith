package net.adeptfrog.blocksmith.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.adeptfrog.blocksmith.Blocksmith;
import net.adeptfrog.blocksmith.ability.WeaponAbility;
import net.adeptfrog.blocksmith.ability.WeaponAbilityRegistry;
import net.adeptfrog.blocksmith.client.render.ClientVortexHandler;
import net.adeptfrog.blocksmith.client.render.ModularWeaponSpecialRenderer;
import net.adeptfrog.blocksmith.item.ModularBowItem;
import net.adeptfrog.blocksmith.block.VoxelCubeBlock;
import net.adeptfrog.blocksmith.client.gui.VoxelPaletteScreen;
import net.adeptfrog.blocksmith.data.VoxelMaterial;
import net.adeptfrog.blocksmith.network.OpenPalettePayload;
import net.adeptfrog.blocksmith.network.StartVortexPayload;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.fabricmc.api.ClientModInitializer;

import net.adeptfrog.blocksmith.client.gui.CustomizationScreen;
import net.adeptfrog.blocksmith.item.ModularSwordItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class BlocksmithClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.

		// Register the special renderer type for JSON models
		SpecialModelRenderers.ID_MAPPER.put(
				Identifier.fromNamespaceAndPath(Blocksmith.MOD_ID, "modular_weapon"),
				ModularWeaponSpecialRenderer.Unbaked.MAP_CODEC
		);

		// Listen for server vortex start packet & tick client particles
		ClientPlayNetworking.registerGlobalReceiver(StartVortexPayload.TYPE, (payload, context) ->
				context.client().execute(() ->
						ClientVortexHandler.addVortex(new Vec3(payload.x(), payload.y(), payload.z()), payload.duration())));
		ClientTickEvents.END_CLIENT_TICK.register(ClientVortexHandler::tick);

		// Dimensional Editor: voxel cubes are plain white cubes tinted with the material palette colour
		BlockColorRegistry.register(List.of(new BlockTintSource() {
			@Override
			public int color(BlockState state) {
				List<VoxelMaterial> mats = VoxelCubeBlock.orderedMaterials();
				int idx = state.getValue(VoxelCubeBlock.MATERIAL);
				if (idx >= mats.size()) return 0xFFFFFFFF;
				return 0xFF000000 | (mats.get(idx).getColorRgb(state.getValue(VoxelCubeBlock.SHADE)) & 0xFFFFFF);
			}

			@Override
			public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
				return color(state);
			}
		}), Blocksmith.VOXEL_CUBE);

		ClientPlayNetworking.registerGlobalReceiver(OpenPalettePayload.TYPE, (payload, context) ->
				context.client().execute(() -> context.client().setScreen(new VoxelPaletteScreen())));

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
						player.sendOverlayMessage(Component.literal("§eHold a forged weapon in your hand to customize it!"));
						return InteractionResult.CONSUME;
					}
				}
			}
			return InteractionResult.PASS;
		});

		// Register custom tooltip for Forged weapons
		ItemTooltipCallback.EVENT.register((stack, _, _, lines) -> {
			boolean isSword = stack.getItem() instanceof ModularSwordItem;
			boolean isBow = stack.getItem() instanceof ModularBowItem;
			boolean isModular = isSword || isBow;
			if (isModular) {
				List<WeaponAbility> abilities = WeaponAbilityRegistry.getUnlockedAbilities(stack);

				if (!abilities.isEmpty()) {
					Window window = Minecraft.getInstance().getWindow();
					boolean holdingAlt = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT)
							|| InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT);

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