package net.adeptfrog.blocksmith.client;

import net.adeptfrog.blocksmith.client.gui.MaterialConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Client half of the Forge entry point (Minecraft 1.21.1, EventBus 6): hooks {@link BlocksmithClient} up to Forge's events. */
public final class BlocksmithForgeClient {
    private BlocksmithForgeClient() {
    }

    public static void init(FMLJavaModLoadingContext context) {
        BlocksmithClient.init();

        // The config button on Forge's mod list (Mod Menu on Fabric)
        MinecraftForge.registerConfigScreen(MaterialConfigScreen::new);

        IEventBus forgeBus = MinecraftForge.EVENT_BUS;
        forgeBus.addListener(BlocksmithForgeClient::onClientTick);
        forgeBus.addListener(BlocksmithForgeClient::onLoggingOut);
        forgeBus.addListener(BlocksmithForgeClient::onRightClickBlock);
        forgeBus.addListener(BlocksmithForgeClient::onTooltip);
    }

    private static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        BlocksmithClient.onClientTick(Minecraft.getInstance());
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        BlocksmithClient.onDisconnect();
    }

    // Opening the Forging Table's editor
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        InteractionResult result = BlocksmithClient.onUseBlock(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
        if (result == InteractionResult.PASS) return;
        event.setCancellationResult(result);
        event.setCanceled(true);
    }

    private static void onTooltip(ItemTooltipEvent event) {
        BlocksmithClient.onTooltip(event.getItemStack(), event.getToolTip());
    }
}
