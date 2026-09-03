package net.adeptfrog.blocksmith.network;

import net.adeptfrog.blocksmith.Blocksmith;
import net.adeptfrog.blocksmith.data.VoxelMaterial;
import net.adeptfrog.blocksmith.data.WeaponVoxel;
import net.adeptfrog.blocksmith.item.ModularSwordItem;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public record ModifyWeaponPayload(boolean isAdd, int x, int y, int z, VoxelMaterial material, int shade) implements CustomPacketPayload {
    public static final Type<ModifyWeaponPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Blocksmith.MOD_ID, "modify_weapon"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ModifyWeaponPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ModifyWeaponPayload::isAdd,
            ByteBufCodecs.VAR_INT, ModifyWeaponPayload::x,
            ByteBufCodecs.VAR_INT, ModifyWeaponPayload::y,
            ByteBufCodecs.VAR_INT, ModifyWeaponPayload::z,
            VoxelMaterial.STREAM_CODEC, ModifyWeaponPayload::material,
            ByteBufCodecs.VAR_INT, ModifyWeaponPayload::shade,
            ModifyWeaponPayload::new
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ItemStack stack = context.player().getMainHandItem();
                if (!(stack.getItem() instanceof ModularSwordItem)) {
                    stack = context.player().getOffhandItem();
                    if (!(stack.getItem() instanceof ModularSwordItem)) return;
                }

                List<WeaponVoxel> currentVoxels = new ArrayList<>(ModularSwordItem.getVoxels(stack));

                if (payload.isAdd() && currentVoxels.size() < ModularSwordItem.MAX_VOXELS) {
                    currentVoxels.removeIf(v -> v.x() == payload.x() && v.y() == payload.y() && v.z() == payload.z());
                    currentVoxels.add(new WeaponVoxel(payload.x(), payload.y(), payload.z(), payload.material(), payload.shade()));
                } else if (!payload.isAdd()) {
                    currentVoxels.removeIf(v -> v.x() == payload.x() && v.y() == payload.y() && v.z() == payload.z());
                }

                ModularSwordItem.saveVoxels(stack, currentVoxels);
                context.player().inventoryMenu.broadcastChanges();
            });
        });
    }
}