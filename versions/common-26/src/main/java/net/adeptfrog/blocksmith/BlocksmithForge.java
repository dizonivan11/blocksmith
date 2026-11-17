package net.adeptfrog.blocksmith;

import net.adeptfrog.blocksmith.network.BlocksmithNetwork;
import net.adeptfrog.blocksmith.network.ServerSettingsPayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Forge entry point (Minecraft 26.x, EventBus 7). Hooks the shared code in {@link Blocksmith} up to Forge's events;
 * the client half is in {@code BlocksmithForgeClient}.
 */
@Mod(Blocksmith.MOD_ID)
public final class BlocksmithForge {
    public BlocksmithForge(FMLJavaModLoadingContext context) {
        BusGroup modBus = context.getModBusGroup();

        Blocksmith.initialize();

        // Forge only accepts registrations while its RegisterEvent runs for that registry
        RegisterEvent.getBus(modBus).addListener(event -> {
            if (event.getRegistryKey().equals(Registries.BLOCK)) Blocksmith.registerBlocks();
            else if (event.getRegistryKey().equals(Registries.ITEM)) Blocksmith.registerItems();
            else if (event.getRegistryKey().equals(Registries.DATA_COMPONENT_TYPE)) Blocksmith.registerDataComponents();
            else if (event.getRegistryKey().equals(Registries.CREATIVE_MODE_TAB)) Blocksmith.registerCreativeTabs();
        });

        FMLCommonSetupEvent.getBus(modBus).addListener(event -> {
            BlocksmithNetwork.register();
            ServerSettingsPayload.register();
        });

        // Ability timers
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> Blocksmith.onServerTick());
        ServerStartedEvent.BUS.addListener(event -> ServerSettingsPayload.onServerStarted(event.getServer()));
        ServerStoppedEvent.BUS.addListener(event -> {
            Blocksmith.onServerStopped();
            ServerSettingsPayload.onServerStopped();
        });

        // Each player gets the server's config on join
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) ServerSettingsPayload.onPlayerJoin(player);
        });

        // Remember how charged each swing is: melee abilities only fire on charged swings
        AttackEntityEvent.BUS.addListener(event -> {
            Blocksmith.onAttackEntity(event.getEntity());
            return false;
        });

        if (FMLEnvironment.dist == Dist.CLIENT) {
            net.adeptfrog.blocksmith.client.BlocksmithForgeClient.init(context);
        }
    }
}
