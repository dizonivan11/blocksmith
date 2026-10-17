package net.adeptfrog.blocksmith.network;

import net.adeptfrog.blocksmith.Blocksmith;
import net.adeptfrog.blocksmith.data.WeaponOffset;
import net.adeptfrog.blocksmith.data.WeaponSaveService;
import net.adeptfrog.blocksmith.data.WeaponVoxel;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;


public record SaveWeaponVoxelsPayload(List<WeaponVoxel> voxels, WeaponOffset offset) implements CustomPacketPayload {
    public static final Type<SaveWeaponVoxelsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Blocksmith.MOD_ID, "save_weapon_voxels"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveWeaponVoxelsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, WeaponVoxel.STREAM_CODEC), SaveWeaponVoxelsPayload::voxels,
            WeaponOffset.STREAM_CODEC, SaveWeaponVoxelsPayload::offset,
            SaveWeaponVoxelsPayload::new
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> context.server().execute(() -> {
            Player player = context.player();
            ItemStack stack = WeaponSaveService.findModularStack(player);
            if (stack == null) return;
            WeaponSaveService.apply(player, stack, payload.voxels(), payload.offset(), player.getAbilities().instabuild);
        }));
    }
}
