package net.adeptfrog.blocksmith;

import net.adeptfrog.blocksmith.network.BlocksmithNetwork;
import net.adeptfrog.blocksmith.network.ServerSettingsPayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Forge entry point (Minecraft 1.21.1, EventBus 6). Hooks the shared code in {@link Blocksmith} up to Forge's events;
 * the client half is in {@code BlocksmithForgeClient}.
 */
@Mod(Blocksmith.MOD_ID)
public final class BlocksmithForge {
    public BlocksmithForge(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();

        Blocksmith.initialize();

        // Forge only accepts registrations while its RegisterEvent runs for that registry
        modBus.addListener(BlocksmithForge::onRegister);
        modBus.addListener(BlocksmithForge::onCommonSetup);

        IEventBus forgeBus = MinecraftForge.EVENT_BUS;
        forgeBus.addListener(BlocksmithForge::onServerTick);
        forgeBus.addListener(BlocksmithForge::onServerStarted);
        forgeBus.addListener(BlocksmithForge::onServerStopped);
        forgeBus.addListener(BlocksmithForge::onPlayerLoggedIn);
        forgeBus.addListener(BlocksmithForge::onAttackEntity);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            net.adeptfrog.blocksmith.client.BlocksmithForgeClient.init(context);
        }
    }

    private static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.BLOCK)) Blocksmith.registerBlocks();
        else if (event.getRegistryKey().equals(Registries.ITEM)) Blocksmith.registerItems();
        else if (event.getRegistryKey().equals(Registries.DATA_COMPONENT_TYPE)) Blocksmith.registerDataComponents();
        else if (event.getRegistryKey().equals(Registries.CREATIVE_MODE_TAB)) Blocksmith.registerCreativeTabs();
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        BlocksmithNetwork.register();
        ServerSettingsPayload.register();
    }

    // Ability timers
    private static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        Blocksmith.onServerTick();
    }

    private static void onServerStarted(ServerStartedEvent event) {
        ServerSettingsPayload.onServerStarted(event.getServer());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        Blocksmith.onServerStopped();
        ServerSettingsPayload.onServerStopped();
    }

    // Each player gets the server's config on join
    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ServerSettingsPayload.onPlayerJoin(player);
    }

    // Remember how charged each swing is: melee abilities only fire on charged swings
    private static void onAttackEntity(AttackEntityEvent event) {
        Blocksmith.onAttackEntity(event.getEntity());
    }
}
