package net.adeptfrog.blocksmith.client;

import net.adeptfrog.blocksmith.client.gui.MaterialConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Client half of the Forge entry point (Minecraft 26.x, EventBus 7): hooks {@link BlocksmithClient} up to Forge's events. */
public final class BlocksmithForgeClient {
    private BlocksmithForgeClient() {
    }

    public static void init(FMLJavaModLoadingContext context) {
        BlocksmithClient.init();

        // The config button on Forge's mod list (Mod Menu on Fabric)
        MinecraftForge.registerConfigScreen(MaterialConfigScreen::new);

        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> BlocksmithClient.onClientTick(Minecraft.getInstance()));
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> BlocksmithClient.onDisconnect());

        // Opening the Forging Table's editor
        PlayerInteractEvent.RightClickBlock.BUS.addListener(event -> {
            InteractionResult result = BlocksmithClient.onUseBlock(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
            if (result == InteractionResult.PASS) return false;
            event.setCancellationResult(result);
            return true;
        });

        ItemTooltipEvent.BUS.addListener(event -> BlocksmithClient.onTooltip(event.getItemStack(), event.getToolTip()));
    }
}
