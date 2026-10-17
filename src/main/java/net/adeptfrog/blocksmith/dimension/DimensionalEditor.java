package net.adeptfrog.blocksmith.dimension;

import net.adeptfrog.blocksmith.Blocksmith;
import net.adeptfrog.blocksmith.block.VoxelCubeBlock;
import net.adeptfrog.blocksmith.data.VoxelMaterial;
import net.adeptfrog.blocksmith.data.VoxelMaterialRegistry;
import net.adeptfrog.blocksmith.data.WeaponOffset;
import net.adeptfrog.blocksmith.data.WeaponSaveService;
import net.adeptfrog.blocksmith.data.WeaponVoxel;
import net.adeptfrog.blocksmith.network.DimensionalActionPayload;
import net.adeptfrog.blocksmith.network.EnterDimensionalEditorPayload;
import net.adeptfrog.blocksmith.network.OpenPalettePayload;
import net.adeptfrog.blocksmith.network.SelectVoxelPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static net.adeptfrog.blocksmith.Blocksmith.LAYER_COUNT;
import static net.adeptfrog.blocksmith.Blocksmith.MAX_LAYER;

/**
 * Dimensional Editor (experimental): the weapon's voxels are laid out as blocks in a shared void dimension, the
 * player builds with the Voxel Brush, and leaving converts the blocks back into voxels.
 *
 * <p>Every active session gets a temporary plot in the shared {@code blocksmith:forge} dimension. Nothing is kept
 * on disk: the plot is built when the session starts and wiped when it ends, and the weapon item stays the only
 * source of truth.
 *
 * <p>Plot layout (x right, y up, layer index z+2 going away from the player): voxel (x, y, z) is the block at
 * {@code origin + (x, y, z + MAX_LAYER)}. The player stands on the +Z side looking north, so the front layer (+2)
 * is nearest to them, as in the 3D editor.
 */
public final class DimensionalEditor {
    public static final ResourceKey<Level> FORGE_DIM = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(Blocksmith.MOD_ID, "forge"));

    private static final int BASE_Y = 64;
    private static final int PLOT_SPACING = 160;   // blocks between plots along X
    private static final int MAX_SLOTS = 4096;     // far more concurrent editors than any server will have
    private static final int PLATFORM_MARGIN = 6;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final BitSet USED_SLOTS = new BitSet();

    private DimensionalEditor() {}

    private static final class Session {
        final int slot;
        final BlockPos origin;
        final int gridSize;
        final ResourceKey<Level> returnDimension;
        final Vec3 returnPosition;
        final float returnYaw, returnPitch;
        final GameType previousMode;
        final boolean freeMaterials;      // the player was creative before entering
        final WeaponOffset offset;
        final ItemStack weapon;           // the stack the session started from
        final List<WeaponVoxel> originalVoxels;
        int materialIndex = 0;
        int shade = 2;

        Session(int slot, BlockPos origin, int gridSize, ServerPlayer player, WeaponOffset offset, ItemStack weapon, List<WeaponVoxel> originalVoxels) {
            this.slot = slot;
            this.origin = origin;
            this.gridSize = gridSize;
            this.returnDimension = player.level().dimension();
            this.returnPosition = player.position();
            this.returnYaw = player.getYRot();
            this.returnPitch = player.getXRot();
            this.previousMode = player.gameMode.getGameModeForPlayer();
            this.freeMaterials = player.getAbilities().instabuild;
            this.offset = offset;
            this.weapon = weapon;
            this.originalVoxels = originalVoxels;
        }

        boolean inPlot(BlockPos pos) {
            int x = pos.getX() - origin.getX(), y = pos.getY() - origin.getY(), z = pos.getZ() - origin.getZ();
            return x >= 0 && x < gridSize && y >= 0 && y < gridSize && z >= 0 && z < LAYER_COUNT;
        }

        Vec3 spawn() {
            return new Vec3(origin.getX() + gridSize / 2.0, origin.getY() + gridSize / 2.0, origin.getZ() + LAYER_COUNT + 10.0);
        }
    }

    // ----- registration -----

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(EnterDimensionalEditorPayload.TYPE, EnterDimensionalEditorPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DimensionalActionPayload.TYPE, DimensionalActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SelectVoxelPayload.TYPE, SelectVoxelPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenPalettePayload.TYPE, OpenPalettePayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(EnterDimensionalEditorPayload.TYPE, (payload, context) ->
                context.server().execute(() -> enter(context.player(), payload.voxels(), payload.offset())));

        ServerPlayNetworking.registerGlobalReceiver(DimensionalActionPayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    if (payload.action() == DimensionalActionPayload.SAVE_AND_EXIT) exit(context.player(), true, false);
                    else if (payload.action() == DimensionalActionPayload.DISCARD_AND_EXIT) exit(context.player(), false, false);
                }));

        ServerPlayNetworking.registerGlobalReceiver(SelectVoxelPayload.TYPE, (payload, context) ->
                context.server().execute(() -> select(context.player(), payload.materialId(), payload.shade())));

        // Keep players inside their plot, and clean up whenever a session ends unexpectedly
        ServerTickEvents.END_SERVER_TICK.register(DimensionalEditor::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (SESSIONS.containsKey(player.getUUID())) exit(player, true, true);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> rescueStrandedPlayer(handler.getPlayer(), server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (UUID id : new ArrayList<>(SESSIONS.keySet())) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) exit(player, true, true);
            }
        });

        // Inside the forge dimension only the Voxel Brush works, and only inside the player's own plot
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (level.dimension() != FORGE_DIM) return InteractionResult.PASS;
            return player.getItemInHand(hand).is(Blocksmith.VOXEL_BRUSH) ? InteractionResult.PASS : InteractionResult.FAIL;
        });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (level.dimension() != FORGE_DIM || level.isClientSide()) return InteractionResult.PASS;
            return canBreak(player.getUUID(), pos) ? InteractionResult.PASS : InteractionResult.FAIL;
        });
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                level.dimension() != FORGE_DIM || canBreak(player.getUUID(), pos));
    }

    private static boolean canBreak(UUID player, BlockPos pos) {
        Session session = SESSIONS.get(player);
        return session != null && session.inPlot(pos);
    }

    public static boolean isInSession(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    // ----- entering -----

    private static void enter(ServerPlayer player, List<WeaponVoxel> requested, WeaponOffset offset) {
        if (SESSIONS.containsKey(player.getUUID())) return;
        MinecraftServer server = player.level().getServer();
        if (server == null) return;

        ItemStack weapon = WeaponSaveService.findModularStack(player);
        if (weapon == null) {
            player.sendOverlayMessage(Component.literal("§eHold a forged weapon to use the Dimensional Editor!"));
            return;
        }
        ServerLevel forge = server.getLevel(FORGE_DIM);
        if (forge == null) {
            player.sendOverlayMessage(Component.literal("§cThe forge dimension is not loaded on this world."));
            return;
        }
        int slot = USED_SLOTS.nextClearBit(0);
        if (slot >= MAX_SLOTS) {
            player.sendOverlayMessage(Component.literal("§cAll forge plots are busy, try again shortly."));
            return;
        }

        int grid = VoxelMaterialRegistry.getGridSize();
        List<WeaponVoxel> voxels = sanitize(requested, grid);
        BlockPos origin = new BlockPos(slot * PLOT_SPACING, BASE_Y, 0);

        USED_SLOTS.set(slot);
        Session session = new Session(slot, origin, grid, player, offset, weapon, new ArrayList<>(WeaponSaveService.getVoxels(weapon)));
        SESSIONS.put(player.getUUID(), session);

        buildPlot(forge, session, voxels);
        giveBrush(player);

        player.setGameMode(GameType.CREATIVE); // instant break and flight; restored on exit
        Vec3 spawn = session.spawn();
        player.teleportTo(forge, spawn.x, spawn.y, spawn.z, Set.<Relative>of(), 180.0F, 0.0F, false);
        player.sendOverlayMessage(Component.literal("§aDimensional Editor: select the Voxel Brush. Right-click air for the palette."));
    }

    /** Drops voxels outside the plot, duplicates and unknown materials; keeps the rest. */
    private static List<WeaponVoxel> sanitize(List<WeaponVoxel> requested, int grid) {
        List<WeaponVoxel> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (WeaponVoxel v : requested) {
            if (v.x() < 0 || v.x() >= grid || v.y() < 0 || v.y() >= grid || !WeaponVoxel.isValidLayer(v.z())) continue;
            if (VoxelCubeBlock.indexOf(v.materialId()) < 0) continue;
            long key = ((long) v.z() << 40) | ((long) v.y() << 20) | v.x();
            if (seen.add(key)) result.add(v);
        }
        return result;
    }

    private static void buildPlot(ServerLevel level, Session session, List<WeaponVoxel> voxels) {
        BlockPos o = session.origin;
        int g = session.gridSize;

        // Platform: white inside the plot footprint, stone around it, so the build area is easy to read
        BlockState stone = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState white = Blocks.WHITE_CONCRETE.defaultBlockState();
        for (int x = -PLATFORM_MARGIN; x < g + PLATFORM_MARGIN; x++) {
            for (int z = -2; z < LAYER_COUNT + 16; z++) {
                boolean inside = x >= 0 && x < g && z >= 0 && z < LAYER_COUNT;
                level.setBlock(o.offset(x, -1, z), inside ? white : stone, Block.UPDATE_CLIENTS);
            }
        }

        for (WeaponVoxel v : voxels) {
            int idx = VoxelCubeBlock.indexOf(v.materialId());
            level.setBlock(o.offset(v.x(), v.y(), v.z() + MAX_LAYER), Blocksmith.VOXEL_CUBE.stateFor(idx, v.shade()), Block.UPDATE_CLIENTS);
        }
    }

    private static void clearPlot(ServerLevel level, Session session) {
        BlockPos o = session.origin;
        int g = session.gridSize;
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -PLATFORM_MARGIN; x < g + PLATFORM_MARGIN; x++) {
            for (int z = -2; z < LAYER_COUNT + 16; z++) {
                level.setBlock(o.offset(x, -1, z), air, Block.UPDATE_CLIENTS);
            }
        }
        for (int x = 0; x < g; x++) {
            for (int y = 0; y < g; y++) {
                for (int z = 0; z < LAYER_COUNT; z++) {
                    BlockPos pos = o.offset(x, y, z);
                    if (!level.getBlockState(pos).isAir()) level.setBlock(pos, air, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static void giveBrush(ServerPlayer player) {
        player.getInventory().add(new ItemStack(Blocksmith.VOXEL_BRUSH));
    }

    private static void removeBrushes(ServerPlayer player) {
        List<ItemStack> items = player.getInventory().getNonEquipmentItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).is(Blocksmith.VOXEL_BRUSH)) items.set(i, ItemStack.EMPTY);
        }
        if (player.getOffhandItem().is(Blocksmith.VOXEL_BRUSH)) player.getOffhandItem().setCount(0);
        player.inventoryMenu.broadcastChanges();
    }

    // ----- editing -----

    public static void openPalette(ServerPlayer player) {
        if (SESSIONS.containsKey(player.getUUID())) ServerPlayNetworking.send(player, new OpenPalettePayload());
    }

    private static void select(ServerPlayer player, String materialId, int shade) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        int idx = VoxelCubeBlock.indexOf(materialId);
        if (idx < 0) return;
        session.materialIndex = idx;
        session.shade = Math.clamp(shade, 0, VoxelCubeBlock.SHADES - 1);
        VoxelMaterial mat = VoxelCubeBlock.orderedMaterials().get(idx);
        player.sendOverlayMessage(Component.literal("§7Brush: §f" + mat.id() + " §7shade " + (session.shade + 1)));
    }

    public static void place(ServerPlayer player, BlockPos clicked, Direction face) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !(player.level() instanceof ServerLevel level)) return;
        BlockPos target = clicked.relative(face);
        if (!session.inPlot(target)) {
            player.sendOverlayMessage(Component.literal("§cOutside the build area!"));
            return;
        }
        level.setBlock(target, Blocksmith.VOXEL_CUBE.stateFor(session.materialIndex, session.shade), Block.UPDATE_CLIENTS);
    }

    // ----- leaving -----

    /** Reads the plot back into voxels. */
    private static List<WeaponVoxel> scan(ServerLevel level, Session session) {
        List<VoxelMaterial> mats = VoxelCubeBlock.orderedMaterials();
        List<WeaponVoxel> result = new ArrayList<>();
        for (int x = 0; x < session.gridSize; x++) {
            for (int y = 0; y < session.gridSize; y++) {
                for (int z = 0; z < LAYER_COUNT; z++) {
                    BlockState state = level.getBlockState(session.origin.offset(x, y, z));
                    if (!state.is(Blocksmith.VOXEL_CUBE)) continue;
                    int idx = state.getValue(VoxelCubeBlock.MATERIAL);
                    if (idx >= mats.size()) continue;
                    result.add(new WeaponVoxel(x, y, z - MAX_LAYER, mats.get(idx).id(), state.getValue(VoxelCubeBlock.SHADE)));
                }
            }
        }
        return result;
    }

    private static ItemStack findWeapon(ServerPlayer player, Session session) {
        List<ItemStack> stacks = new ArrayList<>(player.getInventory().getNonEquipmentItems());
        stacks.add(player.getOffhandItem());
        ItemStack fallback = null;
        for (ItemStack stack : stacks) {
            if (stack == session.weapon) return stack;
            if (fallback == null && WeaponSaveService.isModular(stack) && stack.getItem() == session.weapon.getItem()
                    && WeaponSaveService.getVoxels(stack).equals(session.originalVoxels)) {
                fallback = stack;
            }
        }
        return fallback;
    }

    /**
     * Ends the player's session.
     *
     * @param save  convert the plot back into the weapon's design first
     * @param force leave even if the design can't be saved (disconnects, shutdowns); otherwise a failed save keeps
     *              the player inside so they can fix it
     * @return true when the session ended
     */
    public static boolean exit(ServerPlayer player, boolean save, boolean force) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return false;
        MinecraftServer server = player.level().getServer();
        if (server == null) return false;
        ServerLevel forge = server.getLevel(FORGE_DIM);

        if (save && forge != null) {
            ItemStack weapon = findWeapon(player, session);
            if (weapon == null) {
                player.sendOverlayMessage(Component.literal("§cThe weapon is no longer in your inventory, changes discarded."));
            } else if (!WeaponSaveService.apply(player, weapon, scan(forge, session), session.offset, session.freeMaterials) && !force) {
                return false; // apply() already told the player what is wrong
            }
        }

        finish(player, session, server, forge);
        return true;
    }

    private static void finish(ServerPlayer player, Session session, MinecraftServer server, ServerLevel forge) {
        SESSIONS.remove(player.getUUID());
        USED_SLOTS.clear(session.slot);
        removeBrushes(player);

        if (forge != null) clearPlot(forge, session);
        player.setGameMode(session.previousMode);

        if (player.level().dimension() == FORGE_DIM) {
            ServerLevel back = server.getLevel(session.returnDimension);
            if (back == null) back = server.overworld();
            Vec3 p = session.returnPosition;
            player.teleportTo(back, p.x, p.y, p.z, Set.<Relative>of(), session.returnYaw, session.returnPitch, false);
        }
    }

    // ----- safety nets -----

    private static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty() || server.getTickCount() % 10 != 0) return;
        for (Map.Entry<UUID, Session> entry : new ArrayList<>(SESSIONS.entrySet())) {
            Session session = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            ServerLevel forge = server.getLevel(FORGE_DIM);
            if (player == null) { // offline without a disconnect event: just free the plot
                SESSIONS.remove(entry.getKey());
                USED_SLOTS.clear(session.slot);
                if (forge != null) clearPlot(forge, session);
                continue;
            }
            if (player.level().dimension() != FORGE_DIM) { // left some other way (command, mod): discard and tidy up
                finish(player, session, server, forge);
                continue;
            }
            Vec3 pos = player.position();
            double cx = session.origin.getX() + session.gridSize / 2.0;
            double reach = session.gridSize / 2.0 + 24;
            boolean stray = Math.abs(pos.x - cx) > reach || pos.z < session.origin.getZ() - 24 || pos.z > session.origin.getZ() + LAYER_COUNT + 40
                    || pos.y < session.origin.getY() - 20 || pos.y > session.origin.getY() + session.gridSize + 40;
            if (stray) {
                Vec3 spawn = session.spawn();
                player.teleportTo(forge, spawn.x, spawn.y, spawn.z, Set.<Relative>of(), 180.0F, 0.0F, false);
            }
        }
    }

    /** A player who logs in inside the forge dimension without a session (crash, removed mod) is sent back to their spawn. */
    private static void rescueStrandedPlayer(ServerPlayer player, MinecraftServer server) {
        if (player.level().dimension() != FORGE_DIM || SESSIONS.containsKey(player.getUUID())) return;
        ServerLevel overworld = server.overworld();
        Vec3 spawn = Vec3.atBottomCenterOf(overworld.getRespawnData().pos());
        player.teleportTo(overworld, spawn.x, spawn.y, spawn.z, Set.<Relative>of(), 0.0F, 0.0F, false);
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE && !player.getAbilities().instabuild) {
            player.setGameMode(GameType.SURVIVAL);
        }
    }
}
