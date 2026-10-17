package net.adeptfrog.blocksmith.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.adeptfrog.blocksmith.ability.WeaponAbilityRegistry;
import net.adeptfrog.blocksmith.client.render.VoxelRaycaster;
import net.adeptfrog.blocksmith.client.utils.StringUtils;
import net.adeptfrog.blocksmith.component.ModDataComponents;
import net.adeptfrog.blocksmith.data.*;
import net.adeptfrog.blocksmith.item.ModularBowItem;
import net.adeptfrog.blocksmith.item.ModularSwordItem;
import net.adeptfrog.blocksmith.network.SaveWeaponVoxelsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;
import net.adeptfrog.blocksmith.data.DesignFileManager.DesignEntry;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.List;

import static net.adeptfrog.blocksmith.Blocksmith.LAYER_COUNT;
import static net.adeptfrog.blocksmith.Blocksmith.MAX_LAYER;
import static net.adeptfrog.blocksmith.Blocksmith.MAX_TOTAL_VOXELS;
import static net.adeptfrog.blocksmith.Blocksmith.MIN_LAYER;
import static net.adeptfrog.blocksmith.Blocksmith.MIN_VOXELS;

public class CustomizationScreen extends Screen {
    private static final Identifier GUI_TEXTURE = Identifier.fromNamespaceAndPath("blocksmith", "textures/gui/container/forging_table.png");

    private final ItemStack weaponStack;
    private final List<WeaponVoxel> workingVoxels;

    private VoxelMaterial selectedMaterial = VoxelMaterialRegistry.FALLBACK;
    private int selectedShade = 2; // 0=Darkest, 2=Base, 4=Lightest
    private int saveFeedbackTimer = 0;
    private int exportFeedbackTimer = 0;
    private int importFeedbackTimer = 0;
    private int materialIndexOffset = 0;
    private int activeLayer = 0; // 2D mode only: layer being edited, MIN_LAYER..MAX_LAYER, 0 = base layer

    // 2D drawing tools
    private static final int T2_PENCIL = 0, T2_LINE = 1, T2_RECT = 2, T2_FRAME = 3, T2_FILL = 4, T2_PICK = 5;
    private static final String[] TOOL2D_NAMES = {"Pencil", "Line", "Rectangle", "Frame", "Bucket fill", "Eyedropper"};
    private static final int[] THICKNESS = {1, 3, 5};
    private int tool2d = T2_PENCIL;
    private boolean mirrorX = false;
    private int thicknessIdx = 0;
    private int profile = 0;
    private int shapeAnchorX = -1, shapeAnchorY = -1, shapeCurX = -1, shapeCurY = -1;
    private boolean shapeErase = false;
    private int lastPx = -1, lastPy = -1;

    // Layer strip: per-layer visibility and lock, indexed by z + MAX_LAYER
    private final boolean[] layerHidden = new boolean[LAYER_COUNT];
    private final boolean[] layerLocked = new boolean[LAYER_COUNT];
    private static final int THUMB = 17, THUMB_PITCH = 18, STRIP_X = 281, STRIP_Y = 212;
    private static final int RECENT_SIZE = 10, RECENT_PITCH = 11, RECENT_X = 281, RECENT_Y = 251, MAX_RECENT = 8;
    private final int[][] thumbPixels = new int[LAYER_COUNT][THUMB * THUMB];
    private boolean thumbsDirty = true;

    // Undo / redo: whole-design snapshots, one per stroke or action
    private static final int MAX_UNDO = 60;
    private final Deque<List<WeaponVoxel>> undoStack = new ArrayDeque<>();
    private final Deque<List<WeaponVoxel>> redoStack = new ArrayDeque<>();
    private List<WeaponVoxel> strokeSnapshot = null;

    private record Swatch(VoxelMaterial material, int shade) {}
    private final List<Swatch> recentSwatches = new ArrayList<>();

    // 3D editor
    private static final int TOOL_ADD = 0, TOOL_RECOLOR = 1, TOOL_ERASE = 2;
    private static final String[] TOOL_NAMES = {"Add", "Recolor", "Erase"};
    // Left-column icon buttons: one 18x18 vanilla-item icon per action, each with a tooltip.
    // Tool and Reset view only exist in the 3D editor; the drawing-tool, mirror and thickness buttons only in the 2D editor.
    private static final int BTN_EXPORT = 0, BTN_IMPORT = 1, BTN_FLIP_H = 2, BTN_FLIP_V = 3, BTN_ROT_CW = 4,
            BTN_ROT_CCW = 5, BTN_VIEW = 6, BTN_UNDO = 7, BTN_REDO = 8, BTN_TOOL2D = 9, BTN_MIRROR = 10, BTN_THICK = 11,
            BTN_PROFILE = 12, BTN_TOOL = 13, BTN_RESET = 14, BTN_CODEX = 15, BTN_COUNT = 16;
    private static final int ICON_BTN = 18, ICON_STEP = 20;
    // Vanilla items drawn at runtime as the button icons: nothing is shipped, so no asset or licence is involved.
    private static final ItemStack ICON_EXPORT = new ItemStack(Items.WRITABLE_BOOK);
    private static final ItemStack ICON_IMPORT = new ItemStack(Items.CHEST);
    private static final ItemStack ICON_FLIP_H = new ItemStack(Items.PISTON);
    private static final ItemStack ICON_FLIP_V = new ItemStack(Items.STICKY_PISTON);
    private static final ItemStack ICON_ROT_CW = new ItemStack(Items.REPEATER);
    private static final ItemStack ICON_ROT_CCW = new ItemStack(Items.COMPARATOR);
    private static final ItemStack ICON_VIEW_3D = new ItemStack(Items.ENDER_EYE);
    private static final ItemStack ICON_VIEW_2D = new ItemStack(Items.MAP);
    private static final ItemStack[] ICON_TOOLS = {new ItemStack(Items.STONE), new ItemStack(Items.PAINTING), new ItemStack(Items.SPONGE)};
    private static final ItemStack ICON_RESET = new ItemStack(Items.COMPASS);
    private static final ItemStack ICON_UNDO = new ItemStack(Items.CLOCK);
    private static final ItemStack[] ICON_TOOLS_2D = {new ItemStack(Items.STICK), new ItemStack(Items.LEAD), new ItemStack(Items.ITEM_FRAME),
            new ItemStack(Items.GLOW_ITEM_FRAME), new ItemStack(Items.BUCKET), new ItemStack(Items.SPYGLASS)};
    private static final ItemStack ICON_MIRROR = new ItemStack(Items.GLASS_PANE);
    private static final ItemStack ICON_THICK = new ItemStack(Items.SLIME_BLOCK);
    private static final ItemStack[] ICON_PROFILES = {new ItemStack(Items.SMOOTH_STONE_SLAB), new ItemStack(Items.STONE_STAIRS), new ItemStack(Items.HAY_BLOCK)};
    private static final ItemStack ICON_CODEX = new ItemStack(Items.ENCHANTED_BOOK);
    private boolean view3d = true;
    private int tool3d = TOOL_ADD;
    private boolean orbiting = false; // right-drag in the 3D view
    private boolean panning = false; // middle-drag in the 3D view
    private int strokeAxis = -1;   // axis of the plane a paint-drag stays on, -1 when no stroke is active
    private int strokeIndex = 0;
    private boolean sceneDirty = true;
    private int lastHoverKey = -2;
    private VoxelRaycaster.Hit hoverHit = null;
    private VoxelRaycaster raycaster;
    private int renderScale = 0;   // render pixels per GUI pixel the raycaster and textures were built for
    private int[] fgBase;          // model layer (model + floor grid + outline) at native resolution, no hover highlight
    private int[] fgView;          // fgBase plus hover highlight
    private int[] bgBuffer;        // animated background, one pixel per GUI pixel
    private final long animStartNanos = System.nanoTime();
    private DynamicTexture viewTexture;
    private NativeImage viewImage;
    private Identifier viewTextureId;
    private DynamicTexture bgTexture;
    private NativeImage bgImage;
    private Identifier bgTextureId;

    private int workingOffsetX;
    private int workingOffsetY;
    private String recentFileName = "";
    private String recentCrafter = "";
    private String recentAttribution = "";

    private static final int FONT_COLOR = 0xFF3F3F3F;

    // Container Window Dimensions
    private static final int WINDOW_WIDTH = 381;
    private static final int WINDOW_HEIGHT = 317;

    // Workspace Allocated Area
    private static final int WORKSPACE_OFFSET_X = 12;
    private static final int WORKSPACE_OFFSET_Y = 12;
    private static final int WORKSPACE_DIM = 256;

    // Bottom Slot Configuration
    private static final int VISIBLE_SLOTS = 8;
    private static final int SLOT_SIZE = 16;
    private static final int MAT_SLOT_SPACING = 18;
    private static final int SHADE_SLOT_SPACING = 18;

    private static final int MAT_START_X = 8;
    private static final int SHADE_START_X = 164;
    private static final int SLOTS_Y = 293;

    // Arrow Buttons
    private static final int ARROW_BTN_W = 10;
    private static final int ARROW_BTN_H = 8;

    public final int gridSize;
    private final int cellSize;

    private final boolean[] occupiedGrid;
    private final int[] colorGrid;

    private final int[] cachedAvailableCounts = new int[VISIBLE_SLOTS];
    private final ItemStack[] cachedMaterialStacks = new ItemStack[VISIBLE_SLOTS];
    private final Collection<MaterialGroup> cachedMaterialGroups;
    private double cachedCurrentDmg = 0;
    private double cachedCurrentSpd = 0;
    private int cachedCurrentDur = 0;
    private double cachedDiffDmg = 0;
    private double cachedDiffSpd = 0;
    private int cachedDiffDur = 0;

    // Material Percentage Breakdown Panel
    public record MaterialBreakdown(VoxelMaterial material, ItemStack iconStack, int percent) {}
    private final List<MaterialBreakdown> cachedBreakdown = new ArrayList<>();
    private static final int MAX_BREAKDOWN = 10;

    public CustomizationScreen(ItemStack weaponStack) {
        super(Component.literal("Weapon Forging"));
        this.weaponStack = weaponStack;
        this.workingVoxels = new ArrayList<>(ModularSwordItem.getVoxels(weaponStack));

        this.gridSize = VoxelMaterialRegistry.getGridSize();
        this.cellSize = Math.max(1, WORKSPACE_DIM / this.gridSize);

        this.occupiedGrid = new boolean[this.gridSize * this.gridSize];
        this.colorGrid = new int[this.gridSize * this.gridSize];

        List<VoxelMaterial> mats = new ArrayList<>(VoxelMaterialRegistry.getAll());
        if (!mats.isEmpty()) selectedMaterial = mats.getFirst();

        cachedMaterialGroups = WeaponAbilityRegistry.getAllGroups();

        WeaponOffset savedOffset = (weaponStack.getItem() instanceof ModularBowItem)
                ? ModularBowItem.getOffset(weaponStack)
                : ModularSwordItem.getOffset(weaponStack);

        this.workingOffsetX = savedOffset.x();
        this.workingOffsetY = savedOffset.y();

        recalculateCachedStats();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        var window = this.minecraft.getWindow();
        boolean ctrl = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
        boolean shift = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
        int key = event.key();
        if (ctrl && key == GLFW.GLFW_KEY_Z) {
            if (shift) redo(); else undo();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_Y) {
            redo();
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_UP && this.activeLayer < MAX_LAYER) {
            this.activeLayer++;
            this.sceneDirty = true;
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_DOWN && this.activeLayer > MIN_LAYER) {
            this.activeLayer--;
            this.sceneDirty = true;
            return true;
        }

        if (super.keyPressed(event)) {
            return true;
        }

        if (this.minecraft.options.keyInventory.matches(event)) {
            this.onClose();
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Scrolling over the 3D view zooms it
        if (this.view3d && verticalAmount != 0 && isInsideWorkspace((int) mouseX, (int) mouseY)) {
            ensureRaycaster();
            this.raycaster.zoom(Math.pow(1.12, verticalAmount));
            this.sceneDirty = true;
            return true;
        }

        List<VoxelMaterial> mats = new ArrayList<>(VoxelMaterialRegistry.getAll());
        int maxOffset = Math.max(0, mats.size() - VISIBLE_SLOTS);

        if (maxOffset > 0 && verticalAmount != 0) {
            int oldOffset = this.materialIndexOffset;
            if (verticalAmount > 0 && this.materialIndexOffset > 0) {
                this.materialIndexOffset--;
            } else if (verticalAmount < 0 && this.materialIndexOffset < maxOffset) {
                this.materialIndexOffset++;
            }

            if (oldOffset != this.materialIndexOffset) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                recalculateCachedStats();
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, delta);

        if (saveFeedbackTimer > 0) saveFeedbackTimer--;
        else if (saveFeedbackTimer < 0) saveFeedbackTimer++;

        if (exportFeedbackTimer > 0) exportFeedbackTimer--;
        if (importFeedbackTimer > 0) importFeedbackTimer--;
        else if (importFeedbackTimer < 0) importFeedbackTimer++;

        int leftPos = (this.width - WINDOW_WIDTH) / 2;
        int topPos = (this.height - WINDOW_HEIGHT) / 2;

        // Background texture
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, GUI_TEXTURE, leftPos, topPos, 0.0F, 0.0F, WINDOW_WIDTH, WINDOW_HEIGHT, 512, 512);

        // Workspace
        int gridX = leftPos + WORKSPACE_OFFSET_X;
        int gridY = topPos + WORKSPACE_OFFSET_Y;

        if (this.view3d) {
            render3dWorkspace(guiGraphics, gridX, gridY, mouseX, mouseY);
        } else {
            java.util.Arrays.fill(this.occupiedGrid, false);
            java.util.Arrays.fill(this.colorGrid, 0);

            // Other layers are drawn as faint ghosts under the layer being edited
            for (WeaponVoxel v : WeaponVoxel.sortedBackToFront(workingVoxels)) {
                if (v.z() == this.activeLayer || this.layerHidden[v.z() + MAX_LAYER]) continue;
                if (v.x() < 0 || v.x() >= this.gridSize || v.y() < 0 || v.y() >= this.gridSize) continue;
                int gx = gridX + v.x() * this.cellSize;
                int gy = gridY + (this.gridSize - 1 - v.y()) * this.cellSize;
                // Onion skin: the neighbouring layers show clearly, the rest only faintly
                int ghostAlpha = Math.abs(v.z() - this.activeLayer) == 1 ? 0x55 : 0x22;
                guiGraphics.fill(gx, gy, gx + this.cellSize, gy + this.cellSize, (v.getColorRgb() & 0xFFFFFF) | (ghostAlpha << 24));
            }

            for (WeaponVoxel v : workingVoxels) {
                if (v.z() != this.activeLayer || this.layerHidden[this.activeLayer + MAX_LAYER]) continue;
                if (v.x() >= 0 && v.x() < this.gridSize && v.y() >= 0 && v.y() < this.gridSize) {
                    int idx = v.x() + v.y() * this.gridSize;
                    this.occupiedGrid[idx] = true;
                    this.colorGrid[idx] = v.getColorRgb() | 0xFF000000;
                }
            }

            for (int y = 0; y < this.gridSize; y++) {
                int py = gridY + (this.gridSize - 1 - y) * this.cellSize;
                int x = 0;

                while (x < this.gridSize) {
                    int idx = x + y * this.gridSize;
                    if (!this.occupiedGrid[idx]) {
                        x++;
                        continue;
                    }

                    int color = this.colorGrid[idx];
                    int startX = x;

                    while (x < this.gridSize && this.occupiedGrid[x + y * this.gridSize] && this.colorGrid[x + y * this.gridSize] == color) {
                        x++;
                    }

                    int px1 = gridX + startX * this.cellSize;
                    int px2 = gridX + x * this.cellSize;
                    guiGraphics.fill(px1, py, px2, py + this.cellSize, color);
                }
            }

            int outlineColor = 0xFFFFFFFF;
            for (int y = 0; y < this.gridSize; y++) {
                int py = gridY + (this.gridSize - 1 - y) * this.cellSize;

                int x = 0;
                while (x < this.gridSize) {
                    boolean isTopExposed = isOccupied(x, y) && !isOccupied(x, y + 1);
                    if (!isTopExposed) {
                        x++;
                        continue;
                    }

                    int startX = x;
                    while (x < this.gridSize && isOccupied(x, y) && !isOccupied(x, y + 1)) {
                        x++;
                    }

                    guiGraphics.fill(gridX + startX * this.cellSize, py - 1, gridX + x * this.cellSize, py, outlineColor);
                }

                x = 0;
                while (x < this.gridSize) {
                    boolean isBottomExposed = isOccupied(x, y) && !isOccupied(x, y - 1);
                    if (!isBottomExposed) {
                        x++;
                        continue;
                    }

                    int startX = x;
                    while (x < this.gridSize && isOccupied(x, y) && !isOccupied(x, y - 1)) {
                        x++;
                    }

                    guiGraphics.fill(gridX + startX * this.cellSize, py + this.cellSize, gridX + x * this.cellSize, py + this.cellSize + 1, outlineColor);
                }
            }

            for (WeaponVoxel voxel : workingVoxels) {
                if (voxel.z() != this.activeLayer || this.layerHidden[this.activeLayer + MAX_LAYER]) continue;
                int vx = voxel.x();
                int vy = voxel.y();
                if (vx < 0 || vx >= this.gridSize || vy < 0 || vy >= this.gridSize) continue;

                int px = gridX + vx * this.cellSize;
                int py = gridY + (this.gridSize - 1 - vy) * this.cellSize;

                if (!isOccupied(vx - 1, vy)) guiGraphics.fill(px - 1, py, px, py + this.cellSize, outlineColor);
                if (!isOccupied(vx + 1, vy)) guiGraphics.fill(px + this.cellSize, py, px + this.cellSize + 1, py + this.cellSize, outlineColor);

                if (!isOccupied(vx, vy + 1) && !isOccupied(vx + 1, vy) && !isOccupied(vx + 1, vy + 1)) {
                    guiGraphics.fill(px + this.cellSize, py - 1, px + this.cellSize + 1, py, outlineColor);
                }
                if (!isOccupied(vx, vy + 1) && !isOccupied(vx - 1, vy) && !isOccupied(vx - 1, vy + 1)) {
                    guiGraphics.fill(px - 1, py - 1, px, py, outlineColor);
                }
                if (!isOccupied(vx, vy - 1) && !isOccupied(vx + 1, vy) && !isOccupied(vx + 1, vy - 1)) {
                    guiGraphics.fill(px + this.cellSize, py + this.cellSize, px + this.cellSize + 1, py + this.cellSize + 1, outlineColor);
                }
                if (!isOccupied(vx, vy - 1) && !isOccupied(vx - 1, vy) && !isOccupied(vx - 1, vy - 1)) {
                    guiGraphics.fill(px - 1, py + this.cellSize, px, py + this.cellSize + 1, outlineColor);
                }
            }

            // Hover cursor
            int hX = (mouseX - gridX) / this.cellSize;
            int hY = (mouseY - gridY) / this.cellSize;
            if (hX >= 0 && hX < this.gridSize && hY >= 0 && hY < this.gridSize) {
                int px = gridX + hX * this.cellSize;
                int py = gridY + hY * this.cellSize;
                guiGraphics.fill(px, py, px + this.cellSize, py + this.cellSize, 0x44FFFFFF);
            }

            // Mirror axis
            if (this.mirrorX) {
                int axisX = gridX + (this.gridSize * this.cellSize) / 2;
                guiGraphics.fill(axisX, gridY, axisX + 1, gridY + this.gridSize * this.cellSize, 0xAAFFFF55);
            }

            // Live preview of a line / rectangle being dragged out
            if (this.shapeAnchorX >= 0) {
                int previewColor = this.shapeErase ? 0x77FF4444 : ((this.selectedMaterial.getColorRgb(this.selectedShade) & 0xFFFFFF) | 0xAA000000);
                for (int[] c : withMirror(shapeFootprint())) {
                    int px = gridX + c[0] * this.cellSize;
                    int py = gridY + (this.gridSize - 1 - c[1]) * this.cellSize;
                    guiGraphics.fill(px, py, px + this.cellSize, py + this.cellSize, previewColor);
                }
            }
        }

        // Stats panel
        int statsX = leftPos + 276;
        int statsY = topPos + 35;

        boolean isBow = weaponStack.getItem() instanceof ModularBowItem;
        List<WeaponVoxel> savedVoxels = ModularSwordItem.getVoxels(weaponStack);
        int diffVoxels = workingVoxels.size() - savedVoxels.size();

        // Stats text
        guiGraphics.text(this.font, Component.literal("Voxels:"), statsX + 6, statsY + 6, FONT_COLOR, false);
        String voxelStr = workingVoxels.size() + " / " + MAX_TOTAL_VOXELS();
        if (diffVoxels > 0) voxelStr += " §a(+" + diffVoxels + ")";
        else if (diffVoxels < 0) voxelStr += " §c(" + diffVoxels + ")";
        guiGraphics.text(this.font, Component.literal(voxelStr), statsX + 6, statsY + 16, FONT_COLOR, false);

        // Damage
        guiGraphics.text(this.font, Component.literal(isBow ? "Arrow Damage:" : "Attack Damage:"), statsX + 6, statsY + 30, FONT_COLOR, false);
        String dmgPrefix = this.cachedCurrentDmg > 0 ? "§a+" : (this.cachedCurrentDmg < 0 ? "§c" : "");
        String dmgStr = dmgPrefix + String.format("%.1f", this.cachedCurrentDmg);
        if (this.cachedDiffDmg > 0.001) dmgStr += " §a(+" + String.format("%.1f", this.cachedDiffDmg) + ")";
        else if (this.cachedDiffDmg < -0.001) dmgStr += " §c(" + String.format("%.1f", this.cachedDiffDmg) + ")";
        guiGraphics.text(this.font, Component.literal(dmgStr), statsX + 6, statsY + 40, 0xFFFFFFFF, false);

        // Speed
        guiGraphics.text(this.font, Component.literal(isBow ? "Draw Speed:" : "Attack Speed:"), statsX + 6, statsY + 54, FONT_COLOR, false);
        String spdPrefix = this.cachedCurrentSpd > 0 ? "§a+" : (this.cachedCurrentSpd < 0 ? "§c" : "");
        String spdStr = spdPrefix + String.format("%.2f", this.cachedCurrentSpd);
        if (this.cachedDiffSpd > 0.0001) spdStr += " §a(+" + String.format("%.2f", this.cachedDiffSpd) + ")";
        else if (this.cachedDiffSpd < -0.0001) spdStr += " §c(" + String.format("%.2f", this.cachedDiffSpd) + ")";
        guiGraphics.text(this.font, Component.literal(spdStr), statsX + 6, statsY + 64, 0xFFFFFFFF, false);

        // Durability
        guiGraphics.text(this.font, Component.literal("Durability:"), statsX + 6, statsY + 78, FONT_COLOR, false);
        String durPrefix = this.cachedCurrentDur > 0 ? "§a+" : (this.cachedCurrentDur < 0 ? "§c" : "");
        String durStr = durPrefix + this.cachedCurrentDur;
        if (this.cachedDiffDur > 0) durStr += " §a(+" + this.cachedDiffDur + ")";
        else if (this.cachedDiffDur < 0) durStr += " §c(" + this.cachedDiffDur + ")";
        guiGraphics.text(this.font, Component.literal(durStr), statsX + 6, statsY + 88, 0xFFFFFFFF, false);

        String leftLabel = this.view3d ? "[Left] " + TOOL_NAMES[this.tool3d] : "[Left] " + TOOL2D_NAMES[this.tool2d];
        guiGraphics.text(this.font, Component.literal(leftLabel), statsX + 6, statsY + 104, FONT_COLOR, false);

        Component eraseText = Component.literal(this.view3d ? "[Right] Orbit" : (this.tool2d == T2_PICK ? "" : "[Right] Erase "));
        guiGraphics.text(this.font, eraseText, statsX + 6, statsY + 114, FONT_COLOR, false);

        // Erase warning icon
        int infoX = statsX + 6 + this.font.width(eraseText);
        int infoY = statsY + 114;
        Component infoIcon = Component.literal(this.view3d || this.tool2d == T2_PICK ? "" : "§6(!)");
        guiGraphics.text(this.font, infoIcon, infoX, infoY, 0xFFFFFFFF, false);

        // Hover detection for Erase warning icon
        int infoW = this.font.width(infoIcon);
        if (!this.view3d && this.tool2d != T2_PICK && mouseX >= infoX - 1 && mouseX <= infoX + infoW + 1 && mouseY >= infoY - 1 && mouseY <= infoY + 9) {
            List<ClientTooltipComponent> infoTooltip = List.of(
                    ClientTooltipComponent.create(Component.literal("§cMaterials will be destroyed upon erasing!").getVisualOrderText())
            );
            guiGraphics.tooltip(this.font, infoTooltip, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
        }

        // Grip offset
        int offsetUiY = statsY + 130;
        guiGraphics.text(this.font, Component.literal("Grip Offset:"), statsX + 6, offsetUiY, FONT_COLOR, false);

        int rowX_Y = offsetUiY + 10;
        int rowY_Y = offsetUiY + 21;
        int offBtnW = 10, offBtnH = 9;

        drawVanillaArrowButton(guiGraphics, statsX + 6, rowX_Y, "-", true, mouseX >= statsX + 6 && mouseX <= statsX + 6 + offBtnW && mouseY >= rowX_Y && mouseY <= rowX_Y + offBtnH);
        guiGraphics.text(this.font, Component.literal(String.format("X:%+d", workingOffsetX)), statsX + 22, rowX_Y + 1, FONT_COLOR, false);
        drawVanillaArrowButton(guiGraphics, statsX + 60, rowX_Y, "+", true, mouseX >= statsX + 60 && mouseX <= statsX + 60 + offBtnW && mouseY >= rowX_Y && mouseY <= rowX_Y + offBtnH);

        drawVanillaArrowButton(guiGraphics, statsX + 6, rowY_Y, "-", true, mouseX >= statsX + 6 && mouseX <= statsX + 6 + offBtnW && mouseY >= rowY_Y && mouseY <= rowY_Y + offBtnH);
        guiGraphics.text(this.font, Component.literal(String.format("Y:%+d", workingOffsetY)), statsX + 22, rowY_Y + 1, FONT_COLOR, false);
        drawVanillaArrowButton(guiGraphics, statsX + 60, rowY_Y, "+", true, mouseX >= statsX + 60 && mouseX <= statsX + 60 + offBtnW && mouseY >= rowY_Y && mouseY <= rowY_Y + offBtnH);

        // Save button
        int btnW = 40;
        int btnH = 20;
        int btnX =  leftPos + WINDOW_WIDTH - btnW - 8;
        int saveBtnY = topPos + WINDOW_HEIGHT - btnH - 8;

        boolean isHoveredSave = (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= saveBtnY && mouseY <= saveBtnY + btnH);
        drawSaveButton(guiGraphics, btnX, saveBtnY, btnW, btnH, isHoveredSave, saveFeedbackTimer > 0);

        Component saveLabel;
        if (saveFeedbackTimer > 0) saveLabel = Component.literal("§aSaved!");
        else if (saveFeedbackTimer == -1) saveLabel = Component.literal("§cInvalid!");
        else if (saveFeedbackTimer == -2) saveLabel = Component.literal("§cMissing!");
        else if (hasMissingMaterials()) saveLabel = Component.literal("§cMissing!");
        else saveLabel = Component.literal(isHoveredSave ? "§fSave" : "§7Save");

        int labelWidth = this.font.width(saveLabel);
        guiGraphics.text(this.font, saveLabel, btnX + (btnW - labelWidth) / 2, saveBtnY + 6, 0xFFFFFFFF, false);

        // Icon buttons (one vanilla item per action) with tooltips
        int subBtnX = leftPos - ICON_BTN - 4;
        int hoveredBtn = -1;
        for (int row = 0; row < BTN_COUNT; row++) {
            if (!buttonVisible(row)) continue;
            int rowY = buttonY(topPos, row);
            boolean hov = mouseX >= subBtnX && mouseX < subBtnX + ICON_BTN && mouseY >= rowY && mouseY < rowY + ICON_BTN;
            if (hov) hoveredBtn = row;
            drawSmallHeaderButton(guiGraphics, subBtnX, rowY, ICON_BTN, ICON_BTN, "", hov);
            guiGraphics.fakeItem(buttonIcon(row), subBtnX + 1, rowY + 1);
            String overlay = buttonOverlay(row);
            if (overlay != null) {
                guiGraphics.text(this.font, Component.literal(overlay), subBtnX + ICON_BTN - 6, rowY + ICON_BTN - 9, 0xFFFFFFFF, true);
            }
            if (row == BTN_MIRROR && this.mirrorX) {
                int gc = 0xFF55FF55;
                guiGraphics.fill(subBtnX - 1, rowY - 1, subBtnX + ICON_BTN + 1, rowY, gc);
                guiGraphics.fill(subBtnX - 1, rowY + ICON_BTN, subBtnX + ICON_BTN + 1, rowY + ICON_BTN + 1, gc);
                guiGraphics.fill(subBtnX - 1, rowY, subBtnX, rowY + ICON_BTN, gc);
                guiGraphics.fill(subBtnX + ICON_BTN, rowY, subBtnX + ICON_BTN + 1, rowY + ICON_BTN, gc);
            }
        }

        drawLayerStrip(guiGraphics, leftPos, topPos, mouseX, mouseY);
        drawRecentSwatches(guiGraphics, leftPos, topPos, mouseX, mouseY);

        if (hoveredBtn >= 0) {
            List<ClientTooltipComponent> tip = new ArrayList<>();
            for (String line : buttonTooltip(hoveredBtn)) {
                tip.add(ClientTooltipComponent.create(Component.literal(line).getVisualOrderText()));
            }
            guiGraphics.tooltip(this.font, tip, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
        }

        // Materials and Shades
        int slotsY = topPos + SLOTS_Y;
        boolean isCreative = this.minecraft.player != null && this.minecraft.player.getAbilities().instabuild;

        List<VoxelMaterial> mats = new ArrayList<>(VoxelMaterialRegistry.getAll());
        if (selectedMaterial == null && !mats.isEmpty()) {
            selectedMaterial = mats.getFirst();
        }

        int maxOffset = Math.max(0, mats.size() - VISIBLE_SLOTS);
        this.materialIndexOffset = Math.clamp(this.materialIndexOffset, 0, maxOffset);

        // Material navigator buttons
        int arrowY = topPos + 280;
        int leftArrowX = leftPos + 126;
        int rightArrowX = leftPos + 140;

        boolean leftEnabled = materialIndexOffset > 0;
        boolean rightEnabled = materialIndexOffset < maxOffset;
        boolean leftHovered = mouseX >= leftArrowX && mouseX <= leftArrowX + ARROW_BTN_W && mouseY >= arrowY && mouseY <= arrowY + ARROW_BTN_H;
        boolean rightHovered = mouseX >= rightArrowX && mouseX <= rightArrowX + ARROW_BTN_W && mouseY >= arrowY && mouseY <= arrowY + ARROW_BTN_H;

        drawVanillaArrowButton(guiGraphics, leftArrowX, arrowY, "<", leftEnabled, leftHovered);
        drawVanillaArrowButton(guiGraphics, rightArrowX, arrowY, ">", rightEnabled, rightHovered);

        // Render material items inside baked slots
        VoxelMaterial hoveredMaterial = null;
        for (int i = 0; i < VISIBLE_SLOTS && (i + materialIndexOffset) < mats.size(); i++) {
            VoxelMaterial mat = mats.get(i + materialIndexOffset);
            int sX = leftPos + MAT_START_X + (i * MAT_SLOT_SPACING);
            boolean isSelected = (mat.id().equalsIgnoreCase(selectedMaterial.id()));

            // Display the group's color
            for (MaterialGroup group : cachedMaterialGroups) {
                if (Arrays.asList(group.materials().split("[,|/]")).contains(mat.id())) {
                    guiGraphics.fill(sX, slotsY, sX + SLOT_SIZE, slotsY + SLOT_SIZE, group.color());
                    break;
                }
            }

            // Selected highlight overlay
            if (isSelected) {
                guiGraphics.fill(sX - 1, slotsY - 1, sX + SLOT_SIZE + 1, slotsY, 0xFFFFFFFF);
                guiGraphics.fill(sX - 1, slotsY + SLOT_SIZE, sX + SLOT_SIZE + 1, slotsY + SLOT_SIZE + 1, 0xFFFFFFFF);
                guiGraphics.fill(sX - 1, slotsY, sX, slotsY + SLOT_SIZE, 0xFFFFFFFF);
                guiGraphics.fill(sX + SLOT_SIZE, slotsY, sX + SLOT_SIZE + 1, slotsY + SLOT_SIZE, 0xFFFFFFFF);
            }

            guiGraphics.fakeItem(this.cachedMaterialStacks[i], sX, slotsY);

            int available = this.cachedAvailableCounts[i];
            String countStr = isCreative ? "∞" : (available > 99 ? "99+" : String.valueOf(available));
            int countColor = (available < 0 && !isCreative) ? 0xFFFF5555 : 0xFFFFFFFF;
            int countX = sX + SLOT_SIZE - this.font.width(countStr) - 1;
            int countY = slotsY + SLOT_SIZE - 8;
            guiGraphics.text(this.font, Component.literal(countStr), countX, countY, countColor, true);

            if (mouseX >= sX && mouseX <= sX + SLOT_SIZE && mouseY >= slotsY && mouseY <= slotsY + SLOT_SIZE) {
                hoveredMaterial = mat;
            }
        }

        // Render 5 shade swatches inside baked slots
        int[] palette = selectedMaterial.getPalette();
        if (palette != null) {
            for (int s = 0; s < palette.length && s < 5; s++) {
                int swX = leftPos + SHADE_START_X + (s * SHADE_SLOT_SPACING);
                boolean isSelectedShade = (s == selectedShade);

                // Fill color swatch into the baked slots
                guiGraphics.fill(swX + 1, slotsY + 1, swX + SLOT_SIZE - 1, slotsY + SLOT_SIZE - 1, palette[s] | 0xFF000000);

                // Selected highlight overlay
                if (isSelectedShade) {
                    guiGraphics.fill(swX - 1, slotsY - 1, swX + SLOT_SIZE + 1, slotsY, 0xFFFFFFFF);
                    guiGraphics.fill(swX - 1, slotsY + SLOT_SIZE, swX + SLOT_SIZE + 1, slotsY + SLOT_SIZE + 1, 0xFFFFFFFF);
                    guiGraphics.fill(swX - 1, slotsY, swX, slotsY + SLOT_SIZE, 0xFFFFFFFF);
                    guiGraphics.fill(swX + SLOT_SIZE, slotsY, swX + SLOT_SIZE + 1, slotsY + SLOT_SIZE, 0xFFFFFFFF);
                }
            }
        }

        // Material tooltip
        if (hoveredMaterial != null) {
            String name = StringUtils.capitalize(hoveredMaterial.id());
            boolean isBowMaterial = weaponStack.getItem() instanceof ModularBowItem;

            String dmgSign = hoveredMaterial.getBonusDamage() > 0 ? "+" : "";
            String spdSign = hoveredMaterial.getBonusSpeed() > 0 ? "+" : "";
            String durSign = hoveredMaterial.getBonusDurability() > 0 ? "+" : "";

            List<ClientTooltipComponent> tooltipLines = List.of(
                    ClientTooltipComponent.create(Component.literal("§e" + name).getVisualOrderText()),
                    ClientTooltipComponent.create(Component.literal(dmgSign + hoveredMaterial.getBonusDamage() + (isBowMaterial ? " Arrow Damage" : " Attack Damage")).getVisualOrderText()),
                    ClientTooltipComponent.create(Component.literal(spdSign + hoveredMaterial.getBonusSpeed() + (isBowMaterial ? " Draw Speed" : " Attack Speed")).getVisualOrderText()),
                    ClientTooltipComponent.create(Component.literal(durSign + hoveredMaterial.getBonusDurability() + " Durability").getVisualOrderText())
            );
            guiGraphics.tooltip(this.font, tooltipLines, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
        }

        // Material percentage breakdown panel
        int breakdownX = leftPos + WINDOW_WIDTH + 6;
        int startY = topPos + 16;
        int rowH = 20;

        for (int i = 0; i < cachedBreakdown.size() && i < MAX_BREAKDOWN; i++) {
            MaterialBreakdown entry = cachedBreakdown.get(i);
            int rowY = startY + (i * rowH);
            guiGraphics.fakeItem(entry.iconStack(), breakdownX, rowY);
            String pctText = entry.percent() + "%";
            guiGraphics.text(this.font, Component.literal(pctText), breakdownX + 19, rowY + 4, 0xFFFFFFFF, true);
        }


    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int leftPos = (this.width - WINDOW_WIDTH) / 2;
        int topPos = (this.height - WINDOW_HEIGHT) / 2;
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();

        int statsX = leftPos + 276;
        int statsY = topPos + 35;

        // Icon buttons
        int subBtnX = leftPos - ICON_BTN - 4;
        if (mouseX >= subBtnX && mouseX < subBtnX + ICON_BTN) {
            for (int row = 0; row < BTN_COUNT; row++) {
                if (!buttonVisible(row)) continue;
                int rowY = buttonY(topPos, row);
                if (mouseY >= rowY && mouseY < rowY + ICON_BTN) {
                    handleButtonAction(row);
                    return true;
                }
            }
        }

        // Grip offset buttons click
        int offsetUiY = statsY + 130;
        int rowX_Y = offsetUiY + 10;
        int rowY_Y = offsetUiY + 21;
        int offBtnW = 10, offBtnH = 9;

        boolean decrementClicked = mouseX >= statsX + 6 && mouseX <= statsX + 6 + offBtnW;
        boolean incrementClicked = mouseX >= statsX + 60 && mouseX <= statsX + 60 + offBtnW;
        if (mouseY >= rowX_Y && mouseY <= rowX_Y + offBtnH) {
            if (decrementClicked && this.workingOffsetX > -this.gridSize) {
                this.workingOffsetX--;
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            } else if (incrementClicked && this.workingOffsetX < this.gridSize) {
                this.workingOffsetX++;
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        if (mouseY >= rowY_Y && mouseY <= rowY_Y + offBtnH) {
            if (decrementClicked && this.workingOffsetY > -this.gridSize) {
                this.workingOffsetY--;
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            } else if (incrementClicked && this.workingOffsetY < this.gridSize) {
                this.workingOffsetY++;
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        // Save button click
        int btnW = 40;
        int btnH = 20;
        int btnX =  leftPos + WINDOW_WIDTH - btnW - 8;
        int saveBtnY = topPos + WINDOW_HEIGHT - btnH - 8;

        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= saveBtnY && mouseY <= saveBtnY + btnH) {
            if (workingVoxels.size() < MIN_VOXELS) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
                this.saveFeedbackTimer = -1;
                return true;
            }

            if (hasMissingMaterials()) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
                this.saveFeedbackTimer = -2;
                return true;
            }

            if (hasUnsavedChanges()) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_USE, 1.0F));
                WeaponOffset newOffset = new WeaponOffset(workingOffsetX, workingOffsetY);
                weaponStack.set(ModDataComponents.WEAPON_OFFSET, newOffset);
                ModularSwordItem.saveVoxels(weaponStack, workingVoxels);
                ClientPlayNetworking.send(new SaveWeaponVoxelsPayload(workingVoxels, newOffset));
                recalculateCachedStats();
                this.saveFeedbackTimer = 40;
            } else {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            return true;
        }

        // Material navigator buttons click
        int arrowY = topPos + 280;
        int leftArrowX = leftPos + 126;
        int rightArrowX = leftPos + 140;

        List<VoxelMaterial> mats = new ArrayList<>(VoxelMaterialRegistry.getAll());
        int maxOffset = Math.max(0, mats.size() - VISIBLE_SLOTS);

        if (mouseX >= leftArrowX && mouseX <= leftArrowX + ARROW_BTN_W && mouseY >= arrowY && mouseY <= arrowY + ARROW_BTN_H) {
            if (materialIndexOffset > 0) {
                materialIndexOffset--;
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                recalculateCachedStats();
                return true;
            }
        }

        if (mouseX >= rightArrowX && mouseX <= rightArrowX + ARROW_BTN_W && mouseY >= arrowY && mouseY <= arrowY + ARROW_BTN_H) {
            if (materialIndexOffset < maxOffset) {
                materialIndexOffset++;
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                recalculateCachedStats();
                return true;
            }
        }

        // Material click
        int slotsY = topPos + SLOTS_Y;
        for (int i = 0; i < VISIBLE_SLOTS && (i + materialIndexOffset) < mats.size(); i++) {
            int sX = leftPos + MAT_START_X + (i * MAT_SLOT_SPACING);
            if (mouseX >= sX && mouseX <= sX + SLOT_SIZE && mouseY >= slotsY && mouseY <= slotsY + SLOT_SIZE) {
                this.selectedMaterial = mats.get(i + materialIndexOffset);
                return true;
            }
        }

        // Shade swatch click
        for (int s = 0; s < 5; s++) {
            int swX = leftPos + SHADE_START_X + (s * SHADE_SLOT_SPACING);
            if (mouseX >= swX && mouseX <= swX + SLOT_SIZE && mouseY >= slotsY && mouseY <= slotsY + SLOT_SIZE) {
                this.selectedShade = s;
                return true;
            }
        }

        if (event.button() == 0 && handleStripClick(leftPos, topPos, mouseX, mouseY)) {
            return true;
        }

        // Grid painting and erasing
        if (isInsideWorkspace(mouseX, mouseY) && (event.button() == 0 || (event.button() == 1 && !this.view3d))) {
            beginStroke();
        }
        if (this.view3d) {
            if (isInsideWorkspace(mouseX, mouseY)) {
                int btn = event.button();
                this.strokeAxis = -1;
                if (btn == 1) { // right-drag orbits
                    this.orbiting = true;
                    return true;
                }
                if (btn == 2) { // middle-drag pans
                    this.panning = true;
                    return true;
                }
                handle3dInteraction(mouseX, mouseY, btn, false);
                return true;
            }
        } else if (press2d(mouseX, mouseY, event.button())) {
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (this.view3d) {
            if (this.orbiting) {
                ensureRaycaster();
                this.raycaster.orbit(-deltaX * 0.012, deltaY * 0.012);
                this.sceneDirty = true;
                return true;
            }
            if (this.panning) {
                ensureRaycaster();
                this.raycaster.pan(deltaX * this.renderScale, deltaY * this.renderScale);
                this.sceneDirty = true;
                return true;
            }
            if (handle3dInteraction((int) event.x(), (int) event.y(), event.button(), true)) {
                return true;
            }
        } else if (drag2d((int) event.x(), (int) event.y(), event.button())) {
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (!this.view3d) finish2dStroke();
        this.strokeSnapshot = null;
        this.orbiting = false;
        this.panning = false;
        this.strokeAxis = -1;
        this.sceneDirty = true;
        return super.mouseReleased(event);
    }

    private boolean isInsideWorkspace(int mouseX, int mouseY) {
        int gridX = (this.width - WINDOW_WIDTH) / 2 + WORKSPACE_OFFSET_X;
        int gridY = (this.height - WINDOW_HEIGHT) / 2 + WORKSPACE_OFFSET_Y;
        return mouseX >= gridX && mouseX < gridX + WORKSPACE_DIM && mouseY >= gridY && mouseY < gridY + WORKSPACE_DIM;
    }

    private WeaponVoxel findVoxel(int x, int y, int layer) {
        for (WeaponVoxel v : workingVoxels) {
            if (v.x() == x && v.y() == y && v.z() == layer) return v;
        }
        return null;
    }

    // ----- Undo / redo -----

    private void pushUndo(List<WeaponVoxel> snapshot) {
        this.undoStack.push(snapshot);
        while (this.undoStack.size() > MAX_UNDO) this.undoStack.pollLast();
        this.redoStack.clear();
    }

    /** Remembers the design as it was when a stroke began; it becomes an undo step only if the stroke changes something. */
    private void beginStroke() {
        this.strokeSnapshot = new ArrayList<>(this.workingVoxels);
    }

    private void commitSnapshot() {
        if (this.strokeSnapshot != null) {
            pushUndo(this.strokeSnapshot);
            this.strokeSnapshot = null;
        }
    }

    private void restoreFrom(Deque<List<WeaponVoxel>> from, Deque<List<WeaponVoxel>> to) {
        if (from.isEmpty()) return;
        to.push(new ArrayList<>(this.workingVoxels));
        List<WeaponVoxel> state = from.pop();
        this.workingVoxels.clear();
        this.workingVoxels.addAll(state);
        this.strokeSnapshot = null;
        recalculateCachedStats();
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void undo() {
        restoreFrom(this.undoStack, this.redoStack);
    }

    private void redo() {
        restoreFrom(this.redoStack, this.undoStack);
    }

    // ----- 2D tools -----

    private boolean isLayerLocked(int layer) {
        int i = layer + MAX_LAYER;
        return this.layerLocked[i] || this.layerHidden[i];
    }

    /** Grid cell under the mouse as {x, voxelY}, or null outside the grid. With clamp the nearest cell is returned instead. */
    private int[] cellAt(int mouseX, int mouseY, boolean clamp) {
        int gridX = (this.width - WINDOW_WIDTH) / 2 + WORKSPACE_OFFSET_X;
        int gridY = (this.height - WINDOW_HEIGHT) / 2 + WORKSPACE_OFFSET_Y;
        int cx = Math.floorDiv(mouseX - gridX, this.cellSize);
        int cy = Math.floorDiv(mouseY - gridY, this.cellSize);
        if (cx < 0 || cx >= this.gridSize || cy < 0 || cy >= this.gridSize) {
            if (!clamp) return null;
            cx = Math.clamp(cx, 0, this.gridSize - 1);
            cy = Math.clamp(cy, 0, this.gridSize - 1);
        }
        return new int[]{cx, (this.gridSize - 1) - cy};
    }

    private List<int[]> shapeFootprint() {
        if (this.tool2d == T2_LINE) return EditorGeometry.line(this.shapeAnchorX, this.shapeAnchorY, this.shapeCurX, this.shapeCurY);
        return EditorGeometry.rect(this.shapeAnchorX, this.shapeAnchorY, this.shapeCurX, this.shapeCurY, this.tool2d == T2_RECT);
    }

    private List<int[]> withMirror(List<int[]> cells) {
        if (!this.mirrorX) return cells;
        int n = this.gridSize - 1;
        List<int[]> all = new ArrayList<>(cells);
        for (int[] c : cells) {
            if (n - c[0] != c[0]) all.add(new int[]{n - c[0], c[1]});
        }
        return all;
    }

    private boolean press2d(int mouseX, int mouseY, int button) {
        if (button != 0 && button != 1) return false;
        int[] cell = cellAt(mouseX, mouseY, false);
        if (cell == null) return false;
        boolean erase = button == 1;

        switch (this.tool2d) {
            case T2_PENCIL -> {
                this.lastPx = cell[0];
                this.lastPy = cell[1];
                applyFootprint(List.of(cell), erase);
            }
            case T2_LINE, T2_RECT, T2_FRAME -> {
                this.shapeAnchorX = this.shapeCurX = cell[0];
                this.shapeAnchorY = this.shapeCurY = cell[1];
                this.shapeErase = erase;
            }
            case T2_FILL -> {
                final int layer = this.activeLayer;
                WeaponVoxel start = findVoxel(cell[0], cell[1], layer);
                if (erase && start == null) return true;
                if (!erase && start != null && start.materialId().equalsIgnoreCase(this.selectedMaterial.id()) && start.shade() == this.selectedShade) return true;
                String startKey = voxelState(start);
                java.util.Map<Long, String> states = new java.util.HashMap<>();
                for (WeaponVoxel v : this.workingVoxels) {
                    if (v.z() == layer) states.put(((long) v.x() << 32) | v.y(), voxelState(v));
                }
                applyFootprint(EditorGeometry.flood(this.gridSize, cell[0], cell[1],
                        (x, y) -> startKey.equals(states.getOrDefault(((long) x << 32) | y, ""))), erase);
            }
            case T2_PICK -> {
                if (!erase) {
                    WeaponVoxel v = findVoxel(cell[0], cell[1], this.activeLayer);
                    if (v != null) selectSwatch(v.material(), v.shade());
                }
            }
            default -> { }
        }
        return true;
    }

    private static String voxelState(WeaponVoxel v) {
        return v == null ? "" : v.materialId().toLowerCase() + "#" + v.shade();
    }

    private boolean drag2d(int mouseX, int mouseY, int button) {
        if (button != 0 && button != 1) return false;
        if (this.tool2d == T2_PENCIL) {
            int[] cell = cellAt(mouseX, mouseY, false);
            if (cell == null) {
                this.lastPx = -1;
                return true;
            }
            if (this.lastPx >= 0 && (cell[0] != this.lastPx || cell[1] != this.lastPy)) {
                applyFootprint(EditorGeometry.line(this.lastPx, this.lastPy, cell[0], cell[1]), button == 1);
            } else if (this.lastPx < 0) {
                applyFootprint(List.of(cell), button == 1);
            }
            this.lastPx = cell[0];
            this.lastPy = cell[1];
            return true;
        }
        if (this.shapeAnchorX >= 0) {
            int[] cell = cellAt(mouseX, mouseY, true);
            this.shapeCurX = cell[0];
            this.shapeCurY = cell[1];
            return true;
        }
        return false;
    }

    private void finish2dStroke() {
        if (this.shapeAnchorX >= 0) {
            applyFootprint(shapeFootprint(), this.shapeErase);
            this.shapeAnchorX = this.shapeAnchorY = this.shapeCurX = this.shapeCurY = -1;
        }
        this.lastPx = this.lastPy = -1;
    }

    /** Paints or erases a set of cells on the active layer, honouring mirror and thickness. */
    private boolean applyFootprint(List<int[]> cells, boolean erase) {
        List<int[]> all = withMirror(cells);
        int half = (THICKNESS[this.thicknessIdx] - 1) / 2;
        boolean changed = false;

        if (erase) {
            for (int[] c : all) {
                for (int d = -half; d <= half; d++) changed |= removeVoxel(c[0], c[1], this.activeLayer + d);
            }
        } else {
            for (int[] c : all) changed |= putVoxel(c[0], c[1], this.activeLayer, this.selectedMaterial, this.selectedShade);
            if (half > 0) changed |= extrude(all, half);
        }

        if (changed) {
            commitSnapshot();
            if (!erase) noteRecent(this.selectedMaterial, this.selectedShade);
            recalculateCachedStats();
        }
        return changed;
    }

    /** Copies the drawn layer onto the layers around it according to the thickness profile. */
    private boolean extrude(List<int[]> footprint, int half) {
        int n = this.gridSize;
        boolean[] occ = new boolean[n * n];
        WeaponVoxel[] source = new WeaponVoxel[n * n];
        for (WeaponVoxel v : this.workingVoxels) {
            if (v.z() == this.activeLayer && v.x() >= 0 && v.x() < n && v.y() >= 0 && v.y() < n) {
                occ[v.x() + v.y() * n] = true;
                source[v.x() + v.y() * n] = v;
            }
        }
        EditorGeometry.Cell occupied = (x, y) -> x >= 0 && y >= 0 && x < n && y < n && occ[x + y * n];

        boolean[] drawn = new boolean[n * n];
        int minX = n, minY = n, maxX = -1, maxY = -1;
        for (int[] c : footprint) {
            drawn[c[0] + c[1] * n] = true;
            minX = Math.min(minX, c[0]); maxX = Math.max(maxX, c[0]);
            minY = Math.min(minY, c[1]); maxY = Math.max(maxY, c[1]);
        }
        // Neighbours of the drawn cells change depth too, so look a little beyond the footprint
        int reach = half + 1;
        minX = Math.max(0, minX - reach); minY = Math.max(0, minY - reach);
        maxX = Math.min(n - 1, maxX + reach); maxY = Math.min(n - 1, maxY + reach);

        boolean changed = false;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                WeaponVoxel src = source[x + y * n];
                if (src == null) continue;
                int depth = EditorGeometry.depth(occupied, x, y, EditorGeometry.MAX_DEPTH);
                for (int d = 1; d <= half; d++) {
                    if (!EditorGeometry.extrudes(this.profile, d, depth)) continue;
                    for (int sign = -1; sign <= 1; sign += 2) {
                        int layer = this.activeLayer + sign * d;
                        if (!WeaponVoxel.isValidLayer(layer)) continue;
                        // Cells you just drew replace what was behind them; untouched neighbours only fill gaps
                        if (!drawn[x + y * n] && findVoxel(x, y, layer) != null) continue;
                        changed |= putVoxel(x, y, layer, src.material(), src.shade());
                    }
                }
            }
        }
        return changed;
    }

    /** Places or recolors one voxel with no undo or stats bookkeeping. */
    private boolean putVoxel(int x, int y, int layer, VoxelMaterial mat, int shade) {
        if (x < 0 || x >= this.gridSize || y < 0 || y >= this.gridSize || !WeaponVoxel.isValidLayer(layer)) return false;
        if (isLayerLocked(layer)) return false;
        WeaponVoxel existing = findVoxel(x, y, layer);
        boolean isCreative = this.minecraft.player != null && this.minecraft.player.getAbilities().instabuild;

        if (existing == null) {
            if (!isCreative && getAvailableCount(mat) <= 0) return false;
            if (workingVoxels.size() >= MAX_TOTAL_VOXELS()) return false;
            workingVoxels.add(new WeaponVoxel(x, y, layer, mat, shade));
            return true;
        }
        if (existing.materialId().equalsIgnoreCase(mat.id()) && existing.shade() == shade) return false;
        if (!existing.materialId().equalsIgnoreCase(mat.id()) && !isCreative && getAvailableCount(mat) <= 0) return false;
        workingVoxels.remove(existing);
        workingVoxels.add(new WeaponVoxel(x, y, layer, mat, shade));
        return true;
    }

    private boolean removeVoxel(int x, int y, int layer) {
        if (!WeaponVoxel.isValidLayer(layer) || isLayerLocked(layer)) return false;
        WeaponVoxel existing = findVoxel(x, y, layer);
        if (existing == null || workingVoxels.size() <= MIN_VOXELS) return false;
        workingVoxels.remove(existing);
        return true;
    }

    /** Places a voxel of the selected material, or recolors the one already there (3D editor). */
    private boolean paintAt(int x, int y, int layer) {
        if (!putVoxel(x, y, layer, this.selectedMaterial, this.selectedShade)) return false;
        commitSnapshot();
        noteRecent(this.selectedMaterial, this.selectedShade);
        recalculateCachedStats();
        return true;
    }

    private boolean eraseAt(int x, int y, int layer) {
        if (!removeVoxel(x, y, layer)) return false;
        commitSnapshot();
        recalculateCachedStats();
        return true;
    }

    // ----- Recent colours and layer strip -----

    private void noteRecent(VoxelMaterial mat, int shade) {
        this.recentSwatches.removeIf(sw -> sw.material().id().equalsIgnoreCase(mat.id()) && sw.shade() == shade);
        this.recentSwatches.addFirst(new Swatch(mat, shade));
        while (this.recentSwatches.size() > MAX_RECENT) this.recentSwatches.removeLast();
    }

    /** Makes a material and shade the current brush, scrolling the material row so it is visible. */
    private void selectSwatch(VoxelMaterial mat, int shade) {
        this.selectedMaterial = mat;
        this.selectedShade = shade;
        List<VoxelMaterial> mats = new ArrayList<>(VoxelMaterialRegistry.getAll());
        int idx = mats.indexOf(mat);
        if (idx >= 0) {
            if (idx < this.materialIndexOffset) this.materialIndexOffset = idx;
            else if (idx >= this.materialIndexOffset + VISIBLE_SLOTS) this.materialIndexOffset = idx - VISIBLE_SLOTS + 1;
            recalculateCachedStats();
        }
    }

    private void rebuildThumbnails() {
        int n = this.gridSize;
        for (int[] px : this.thumbPixels) Arrays.fill(px, 0xFF2B2B2B);
        for (WeaponVoxel v : this.workingVoxels) {
            if (v.x() < 0 || v.x() >= n || v.y() < 0 || v.y() >= n) continue;
            int tx = v.x() * THUMB / n;
            int ty = (n - 1 - v.y()) * THUMB / n;
            this.thumbPixels[v.z() + MAX_LAYER][tx + ty * THUMB] = v.getColorRgb() | 0xFF000000;
        }
        this.thumbsDirty = false;
    }

    private int thumbX(int leftPos, int i) { return leftPos + STRIP_X + i * THUMB_PITCH; }

    private static int outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x - 1, y - 1, x + w + 1, y, color);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, color);
        g.fill(x - 1, y, x, y + h, color);
        g.fill(x + w, y, x + w + 1, y + h, color);
        return color;
    }

    private void drawLayerStrip(GuiGraphicsExtractor g, int leftPos, int topPos, int mouseX, int mouseY) {
        if (this.thumbsDirty) rebuildThumbnails();
        g.text(this.font, Component.literal("Layers"), leftPos + STRIP_X, topPos + STRIP_Y - 10, FONT_COLOR, false);

        int y0 = topPos + STRIP_Y;
        List<String> tip = null;
        for (int i = 0; i < LAYER_COUNT; i++) {
            int layer = i - MAX_LAYER;
            int x0 = thumbX(leftPos, i);
            boolean hidden = this.layerHidden[i];
            int[] px = this.thumbPixels[i];

            for (int ty = 0; ty < THUMB; ty++) {
                int tx = 0;
                while (tx < THUMB) {
                    int c = px[tx + ty * THUMB];
                    int run = tx + 1;
                    while (run < THUMB && px[run + ty * THUMB] == c) run++;
                    g.fill(x0 + tx, y0 + ty, x0 + run, y0 + ty + 1, c);
                    tx = run;
                }
            }
            if (hidden) g.fill(x0, y0, x0 + THUMB, y0 + THUMB, 0xBB1A1A1A);
            outline(g, x0, y0, THUMB, THUMB, layer == this.activeLayer ? 0xFFFFFFFF : 0xFF373737);

            // Hide (green = visible) and lock (orange = locked) toggles under each thumbnail
            int ty = y0 + THUMB + 2;
            g.fill(x0, ty, x0 + 8, ty + 6, hidden ? 0xFF555555 : 0xFF55CC55);
            g.fill(x0 + 9, ty, x0 + 17, ty + 6, this.layerLocked[i] ? 0xFFFFAA00 : 0xFF8B8B8B);

            String name = layer == 0 ? "Base layer" : String.format("Layer %+d", layer);
            if (mouseY >= y0 && mouseY < y0 + THUMB && mouseX >= x0 && mouseX < x0 + THUMB) {
                tip = List.of("§e" + name, "§7Click to edit this layer", "§7Page Up / Page Down also switch layers");
            } else if (mouseY >= ty && mouseY < ty + 6 && mouseX >= x0 && mouseX < x0 + 8) {
                tip = List.of("§e" + (hidden ? "Show " : "Hide ") + name.toLowerCase(), "§7Hidden layers can't be edited");
            } else if (mouseY >= ty && mouseY < ty + 6 && mouseX >= x0 + 9 && mouseX < x0 + 17) {
                tip = List.of("§e" + (this.layerLocked[i] ? "Unlock " : "Lock ") + name.toLowerCase(), "§7Locked layers can't be changed");
            }
        }
        if (tip != null) {
            List<ClientTooltipComponent> lines = new ArrayList<>();
            for (String t : tip) lines.add(ClientTooltipComponent.create(Component.literal(t).getVisualOrderText()));
            g.tooltip(this.font, lines, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
        }
    }

    private void drawRecentSwatches(GuiGraphicsExtractor g, int leftPos, int topPos, int mouseX, int mouseY) {
        g.text(this.font, Component.literal("Recent"), leftPos + RECENT_X, topPos + RECENT_Y - 10, FONT_COLOR, false);
        Swatch hovered = null;
        for (int i = 0; i < this.recentSwatches.size(); i++) {
            Swatch sw = this.recentSwatches.get(i);
            int x0 = leftPos + RECENT_X + i * RECENT_PITCH;
            int y0 = topPos + RECENT_Y;
            g.fill(x0, y0, x0 + RECENT_SIZE, y0 + RECENT_SIZE, sw.material().getColorRgb(sw.shade()) | 0xFF000000);
            boolean current = sw.material().id().equalsIgnoreCase(this.selectedMaterial.id()) && sw.shade() == this.selectedShade;
            outline(g, x0, y0, RECENT_SIZE, RECENT_SIZE, current ? 0xFFFFFFFF : 0xFF373737);
            if (mouseX >= x0 && mouseX < x0 + RECENT_SIZE && mouseY >= y0 && mouseY < y0 + RECENT_SIZE) hovered = sw;
        }
        if (hovered != null) {
            List<ClientTooltipComponent> lines = List.of(
                    ClientTooltipComponent.create(Component.literal("§e" + StringUtils.capitalize(hovered.material().id())).getVisualOrderText()),
                    ClientTooltipComponent.create(Component.literal("§7Shade " + (hovered.shade() + 1) + " - click to use").getVisualOrderText()));
            g.tooltip(this.font, lines, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
        }
    }

    private boolean handleStripClick(int leftPos, int topPos, int mouseX, int mouseY) {
        int y0 = topPos + STRIP_Y;
        int ty = y0 + THUMB + 2;
        for (int i = 0; i < LAYER_COUNT; i++) {
            int x0 = thumbX(leftPos, i);
            if (mouseY >= y0 && mouseY < y0 + THUMB && mouseX >= x0 && mouseX < x0 + THUMB) {
                this.activeLayer = i - MAX_LAYER;
                this.sceneDirty = true;
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            if (mouseY >= ty && mouseY < ty + 6 && mouseX >= x0 && mouseX < x0 + 8) {
                this.layerHidden[i] = !this.layerHidden[i];
                this.sceneDirty = true;
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            if (mouseY >= ty && mouseY < ty + 6 && mouseX >= x0 + 9 && mouseX < x0 + 17) {
                this.layerLocked[i] = !this.layerLocked[i];
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        for (int i = 0; i < this.recentSwatches.size(); i++) {
            int x0 = leftPos + RECENT_X + i * RECENT_PITCH;
            int y0r = topPos + RECENT_Y;
            if (mouseX >= x0 && mouseX < x0 + RECENT_SIZE && mouseY >= y0r && mouseY < y0r + RECENT_SIZE) {
                Swatch sw = this.recentSwatches.get(i);
                selectSwatch(sw.material(), sw.shade());
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return false;
    }

    // ----- 3D editor -----

    /** Render pixels per GUI pixel to use so the 3D view is sharp on screen at the player's GUI scale (1..3). */
    private int desiredRenderScale() {
        var window = this.minecraft.getWindow();
        int guiScaledWidth = Math.max(1, window.getGuiScaledWidth());
        return Math.max(1, Math.min(3, (int) Math.round(window.getWidth() / (double) guiScaledWidth)));
    }

    private void ensureRaycaster() {
        int wanted = desiredRenderScale();
        if (this.raycaster != null && wanted == this.renderScale) return;

        // (Re)build for the current GUI scale, keeping the camera where the player left it
        boolean hadView = this.raycaster != null;
        double yaw = 0, pitch = 0, zoom = 1, panR = 0, panU = 0;
        if (hadView) {
            yaw = this.raycaster.yaw();
            pitch = this.raycaster.pitch();
            zoom = this.raycaster.zoomLevel();
            panR = this.raycaster.panRight();
            panU = this.raycaster.panUp();
        }
        releaseViewTextures();

        this.renderScale = wanted;
        int n = WORKSPACE_DIM * wanted;
        this.raycaster = new VoxelRaycaster(this.gridSize, LAYER_COUNT, n, n, wanted);
        if (hadView) {
            this.raycaster.setView(yaw, pitch, zoom);
            this.raycaster.setPan(panR, panU);
        }
        this.fgBase = new int[n * n];
        this.fgView = new int[n * n];
        this.bgBuffer = new int[WORKSPACE_DIM * WORKSPACE_DIM];
        this.sceneDirty = true;
        this.lastHoverKey = -2;
    }

    private void ensureViewTexture() {
        ensureRaycaster();
        if (this.viewImage == null) {
            int n = WORKSPACE_DIM * this.renderScale;
            this.viewImage = new NativeImage(n, n, true);
            this.viewTexture = new DynamicTexture(() -> "blocksmith_forge_view", this.viewImage);
            this.viewTextureId = Identifier.fromNamespaceAndPath("blocksmith", "forge_view_" + System.identityHashCode(this));
            this.minecraft.getTextureManager().register(this.viewTextureId, this.viewTexture);

            this.bgImage = new NativeImage(WORKSPACE_DIM, WORKSPACE_DIM, true);
            this.bgTexture = new DynamicTexture(() -> "blocksmith_forge_bg", this.bgImage);
            this.bgTextureId = Identifier.fromNamespaceAndPath("blocksmith", "forge_bg_" + System.identityHashCode(this));
            this.minecraft.getTextureManager().register(this.bgTextureId, this.bgTexture);

            this.sceneDirty = true;
        }
    }

    private void releaseViewTextures() {
        if (this.viewTexture != null) {
            this.viewTexture.close();
            this.viewTexture = null;
        }
        if (this.bgTexture != null) {
            this.bgTexture.close();
            this.bgTexture = null;
        }
        if (this.viewTextureId != null) {
            this.minecraft.getTextureManager().release(this.viewTextureId);
            this.viewTextureId = null;
        }
        if (this.bgTextureId != null) {
            this.minecraft.getTextureManager().release(this.bgTextureId);
            this.bgTextureId = null;
        }
        this.viewImage = null;
        this.bgImage = null;
    }

    private void rebuildScene() {
        this.raycaster.clear();
        for (WeaponVoxel v : workingVoxels) {
            if (this.layerHidden[v.z() + MAX_LAYER]) continue;
            this.raycaster.setCell(v.x(), v.y(), v.z() + MAX_LAYER, v.getColorRgb() | 0xFF000000);
        }
    }

    private void render3dWorkspace(GuiGraphicsExtractor guiGraphics, int gridX, int gridY, int mouseX, int mouseY) {
        ensureViewTexture();
        final int scale = this.renderScale;
        final int n = WORKSPACE_DIM * scale;

        int px = mouseX - gridX;
        int py = mouseY - gridY;
        boolean inside = px >= 0 && py >= 0 && px < WORKSPACE_DIM && py < WORKSPACE_DIM;
        int hoverKey = (inside && !this.orbiting && !this.panning) ? (px << 16) | py : -1;

        boolean modelLayerChanged = false;

        // Heavy pass: only when the model or camera changed. While the camera is being dragged a coarser,
        // faster preview is drawn; a full-detail pass follows as soon as the mouse is released.
        if (this.sceneDirty) {
            rebuildScene();
            int step = ((this.orbiting || this.panning) && scale >= 2) ? 2 : 1;
            this.raycaster.render(this.fgBase, null, step);
            this.sceneDirty = false;
            modelLayerChanged = true;
        }

        // Light pass: re-trace just the hovered voxel when the cursor moves
        if (modelLayerChanged || hoverKey != this.lastHoverKey) {
            this.hoverHit = hoverKey >= 0 ? this.raycaster.pick(px * scale + scale / 2, py * scale + scale / 2) : null;
            this.raycaster.renderWithHighlight(this.fgBase, this.fgView, this.hoverHit);
            this.lastHoverKey = hoverKey;
            modelLayerChanged = true;
        }

        if (modelLayerChanged) {
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    this.viewImage.setPixel(x, y, this.fgView[y * n + x]);
                }
            }
            this.viewTexture.upload();
        }

        // Every frame: the small animated background
        double seconds = (System.nanoTime() - this.animStartNanos) / 1.0e9;
        VoxelRaycaster.renderBackground(this.bgBuffer, WORKSPACE_DIM, WORKSPACE_DIM, seconds);
        for (int y = 0; y < WORKSPACE_DIM; y++) {
            for (int x = 0; x < WORKSPACE_DIM; x++) {
                this.bgImage.setPixel(x, y, this.bgBuffer[y * WORKSPACE_DIM + x]);
            }
        }
        this.bgTexture.upload();

        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, this.bgTextureId, gridX, gridY, 0.0F, 0.0F, WORKSPACE_DIM, WORKSPACE_DIM, WORKSPACE_DIM, WORKSPACE_DIM);
        // The model layer is rendered at native resolution (scale x the GUI size) and drawn scaled down 1:scale
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, this.viewTextureId, gridX, gridY, 0.0F, 0.0F, WORKSPACE_DIM, WORKSPACE_DIM, n, n, n, n);

        // Control hint sits inside the view on a dark pill so it stays readable on the bright background
        String[] hints = {"Right-drag: orbit", "Mid-drag: pan  Wheel: zoom"};
        int hintX = gridX + 4;
        int hintY = gridY + WORKSPACE_DIM - 13 - 12 * (hints.length - 1);
        for (String hint : hints) {
            int hintW = this.font.width(hint);
            guiGraphics.fill(hintX - 3, hintY - 2, hintX + hintW + 3, hintY + 10, 0xB0141C2C);
            guiGraphics.text(this.font, Component.literal(hint), hintX, hintY, 0xFFFFFFFF, false);
            hintY += 12;
        }
    }

    private boolean handle3dInteraction(int mouseX, int mouseY, int button, boolean drag) {
        if (!isInsideWorkspace(mouseX, mouseY)) return false;
        ensureRaycaster();

        int gridX = (this.width - WINDOW_WIDTH) / 2 + WORKSPACE_OFFSET_X;
        int gridY = (this.height - WINDOW_HEIGHT) / 2 + WORKSPACE_OFFSET_Y;
        // GUI pixel -> centre of the matching block of render pixels
        final int scale = this.renderScale;
        int px = (mouseX - gridX) * scale + scale / 2;
        int py = (mouseY - gridY) * scale + scale / 2;

        if (button != 0) return false; // right-drag orbits and middle-drag pans, handled in mouseClicked/Dragged

        if (this.tool3d == TOOL_ERASE) {
            VoxelRaycaster.Hit hit = this.raycaster.pick(px, py);
            if (hit == null || hit.floor()) return false;
            return eraseAt(hit.x(), hit.y(), hit.zi() - MAX_LAYER);
        }

        if (this.tool3d == TOOL_RECOLOR) {
            VoxelRaycaster.Hit hit = this.raycaster.pick(px, py);
            if (hit == null || hit.floor()) return false;
            return paintAt(hit.x(), hit.y(), hit.zi() - MAX_LAYER);
        }

        // Add tool: place on the face under the cursor; a drag stays on the plane of the first voxel placed
        int[] target;
        if (drag && this.strokeAxis >= 0) {
            target = this.raycaster.pickOnPlane(px, py, this.strokeAxis, this.strokeIndex);
        } else {
            VoxelRaycaster.Hit hit = this.raycaster.pick(px, py);
            if (hit == null) return false;
            target = hit.placeCell();
            if (!this.raycaster.inBounds(target[0], target[1], target[2])) return false;
            this.strokeAxis = hit.normalAxis();
            this.strokeIndex = target[this.strokeAxis];
        }
        if (target == null) return false;
        return paintAt(target[0], target[1], target[2] - MAX_LAYER);
    }

    @Override
    public void removed() {
        releaseViewTextures();
        this.sceneDirty = true;
        super.removed();
    }

    private boolean isOccupied(int x, int y) {
        if (x < 0 || x >= this.gridSize || y < 0 || y >= this.gridSize) {
            return false;
        }
        return this.occupiedGrid[x + y * this.gridSize];
    }

    private int getAvailableCount(VoxelMaterial mat) {
        if (this.minecraft.player == null) return 0;
        if (this.minecraft.player.getAbilities().instabuild) return 999;

        int inInventory = 0;
        for (ItemStack s : this.minecraft.player.getInventory().getNonEquipmentItems()) {
            if (s.is(mat.getIconItem())) inInventory += s.getCount();
        }

        ItemStack offhand = this.minecraft.player.getOffhandItem();
        if (offhand.is(mat.getIconItem())) inInventory += offhand.getCount();

        int onOriginalWeapon = 0;
        for (WeaponVoxel v : ModularSwordItem.getVoxels(weaponStack)) {
            if (v.materialId().equalsIgnoreCase(mat.id())) onOriginalWeapon++;
        }

        int placedInWorking = 0;
        for (WeaponVoxel v : workingVoxels) {
            if (v.materialId().equalsIgnoreCase(mat.id())) placedInWorking++;
        }

        return inInventory + onOriginalWeapon - placedInWorking;
    }

    private boolean hasMissingMaterials() {
        if (this.minecraft.player == null) return false;
        if (this.minecraft.player.getAbilities().instabuild) return false;

        for (VoxelMaterial mat : VoxelMaterialRegistry.getAll()) {
            if (getAvailableCount(mat) < 0) {
                return true;
            }
        }
        return false;
    }

    private boolean hasUnsavedChanges() {
        List<WeaponVoxel> saved = ModularSwordItem.getVoxels(weaponStack);
        if (saved.size() != workingVoxels.size()) {
            return true;
        }
        WeaponOffset savedOffset = weaponStack.getOrDefault(ModDataComponents.WEAPON_OFFSET, WeaponOffset.ZERO);
        if (savedOffset.x() != workingOffsetX || savedOffset.y() != workingOffsetY) {
            return true;
        }
        return !new java.util.HashSet<>(saved).equals(new java.util.HashSet<>(workingVoxels));
    }

    private void recalculateCachedStats() {
        this.sceneDirty = true;
        this.thumbsDirty = true;
        double gridArea = this.gridSize * this.gridSize;

        List<WeaponVoxel> savedVoxels = ModularSwordItem.getVoxels(weaponStack);
        double savedRawDmg = 0, savedRawSpd = 0, savedRawDur = 0;
        for (WeaponVoxel v : savedVoxels) {
            VoxelMaterial m = v.material();
            savedRawDmg += m.getBonusDamage();
            savedRawSpd += m.getBonusSpeed();
            savedRawDur += m.getBonusDurability();
        }

        double workingRawDmg = 0, workingRawSpd = 0, workingRawDur = 0;
        for (WeaponVoxel v : workingVoxels) {
            VoxelMaterial m = v.material();
            workingRawDmg += m.getBonusDamage();
            workingRawSpd += m.getBonusSpeed();
            workingRawDur += m.getBonusDurability();
        }

        int savedCount = savedVoxels.size();
        int workingCount = workingVoxels.size();

        double savedDmg = StatScaling.scale(savedRawDmg, savedCount) / gridArea;
        double savedSpd = StatScaling.scale(savedRawSpd, savedCount) / gridArea;
        int savedDur = (int) Math.round(StatScaling.scale(savedRawDur, savedCount) / gridArea);

        this.cachedCurrentDmg = StatScaling.scale(workingRawDmg, workingCount) / gridArea;
        this.cachedCurrentSpd = StatScaling.scale(workingRawSpd, workingCount) / gridArea;
        this.cachedCurrentDur = (int) Math.round(StatScaling.scale(workingRawDur, workingCount) / gridArea);

        this.cachedDiffDmg = this.cachedCurrentDmg - savedDmg;
        this.cachedDiffSpd = this.cachedCurrentSpd - savedSpd;
        this.cachedDiffDur = this.cachedCurrentDur - savedDur;

        List<VoxelMaterial> mats = new ArrayList<>(VoxelMaterialRegistry.getAll());
        for (int i = 0; i < VISIBLE_SLOTS; i++) {
            int idx = i + materialIndexOffset;
            if (idx < mats.size()) {
                VoxelMaterial mat = mats.get(idx);
                this.cachedMaterialStacks[i] = mat.getIconItem().getDefaultInstance();
                this.cachedAvailableCounts[i] = getAvailableCount(mat);
            } else {
                this.cachedMaterialStacks[i] = ItemStack.EMPTY;
                this.cachedAvailableCounts[i] = 0;
            }
        }

        this.cachedBreakdown.clear();
        if (!workingVoxels.isEmpty()) {
            java.util.Map<VoxelMaterial, Integer> counts = new java.util.HashMap<>();
            for (WeaponVoxel v : workingVoxels) {
                counts.merge(v.material(), 1, Integer::sum);
            }
            int total = workingVoxels.size();

            // Sort all used materials by count in descending order
            List<java.util.Map.Entry<VoxelMaterial, Integer>> sorted = new ArrayList<>(counts.entrySet());
            sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

            for (int i = 0; i < sorted.size() && i < MAX_BREAKDOWN; i++) {
                java.util.Map.Entry<VoxelMaterial, Integer> entry = sorted.get(i);
                int p = (int) Math.floor((entry.getValue() * 100.0) / total);
                this.cachedBreakdown.add(new MaterialBreakdown(entry.getKey(), entry.getKey().getIconItem().getDefaultInstance(), p));
            }
        }
    }

    public void loadImportedDesign(DesignEntry entry) {
        pushUndo(new ArrayList<>(this.workingVoxels));
        this.workingVoxels.clear();
        this.workingVoxels.addAll(entry.voxels());
        this.workingOffsetX = entry.offset().x();
        this.workingOffsetY = entry.offset().y();
        this.recentFileName = entry.fileName();
        this.recentCrafter = entry.crafter();
        this.recentAttribution = entry.attribution();
        recalculateCachedStats();
        this.importFeedbackTimer = 40;
    }

    public void triggerExportFeedback() {
        this.exportFeedbackTimer = 40;
    }

    private int buttonY(int topPos, int row) {
        // The codex button is pinned to the bottom of the window; the visible rest stack from the top without gaps
        if (row == BTN_CODEX) return topPos + WINDOW_HEIGHT - ICON_BTN - 1;
        int slot = 0;
        for (int r = 0; r < row; r++) if (buttonVisible(r)) slot++;
        return topPos + 1 + slot * ICON_STEP;
    }

    private boolean buttonVisible(int row) {
        if (row == BTN_TOOL2D || row == BTN_MIRROR || row == BTN_THICK) return !this.view3d;
        if (row == BTN_PROFILE) return !this.view3d && THICKNESS[this.thicknessIdx] > 1;
        if (row == BTN_TOOL || row == BTN_RESET) return this.view3d;
        return true;
    }

    private ItemStack buttonIcon(int row) {
        return switch (row) {
            case BTN_EXPORT -> ICON_EXPORT;
            case BTN_IMPORT -> ICON_IMPORT;
            case BTN_FLIP_H -> ICON_FLIP_H;
            case BTN_FLIP_V -> ICON_FLIP_V;
            case BTN_ROT_CW -> ICON_ROT_CW;
            case BTN_ROT_CCW -> ICON_ROT_CCW;
            case BTN_VIEW -> this.view3d ? ICON_VIEW_3D : ICON_VIEW_2D;
            case BTN_UNDO, BTN_REDO -> ICON_UNDO;
            case BTN_TOOL2D -> ICON_TOOLS_2D[this.tool2d];
            case BTN_MIRROR -> ICON_MIRROR;
            case BTN_THICK -> ICON_THICK;
            case BTN_PROFILE -> ICON_PROFILES[this.profile];
            case BTN_TOOL -> ICON_TOOLS[this.tool3d];
            case BTN_RESET -> ICON_RESET;
            default -> ICON_CODEX;
        };
    }

    private String buttonOverlay(int row) {
        return switch (row) {
            case BTN_UNDO -> "<";
            case BTN_REDO -> ">";
            case BTN_THICK -> String.valueOf(THICKNESS[this.thicknessIdx]);
            default -> null;
        };
    }

    private String[] buttonTooltip(int row) {
        return switch (row) {
            case BTN_EXPORT -> new String[]{exportFeedbackTimer > 0 ? "§aCopied!" : "§eExport design", "§7Save or share this design"};
            case BTN_IMPORT -> new String[]{importFeedbackTimer > 0 ? "§aLoaded!" : (importFeedbackTimer < 0 ? "§cInvalid!" : "§eImport design"), "§7Open the design library"};
            case BTN_FLIP_H -> new String[]{"§eFlip horizontally"};
            case BTN_FLIP_V -> new String[]{"§eFlip vertically"};
            case BTN_ROT_CW -> new String[]{"§eRotate clockwise"};
            case BTN_ROT_CCW -> new String[]{"§eRotate counter-clockwise"};
            case BTN_VIEW -> this.view3d
                    ? new String[]{"§eView: 3D", "§7Click to switch to the 2D layer editor"}
                    : new String[]{"§eView: 2D", "§7Click to switch to the 3D editor"};
            case BTN_UNDO -> new String[]{"§eUndo", "§7Ctrl+Z" + (this.undoStack.isEmpty() ? " §8(nothing to undo)" : "")};
            case BTN_REDO -> new String[]{"§eRedo", "§7Ctrl+Y or Ctrl+Shift+Z" + (this.redoStack.isEmpty() ? " §8(nothing to redo)" : "")};
            case BTN_TOOL2D -> new String[]{"§eTool: " + TOOL2D_NAMES[this.tool2d] + " §7(click to cycle)",
                    "§7Pencil: freehand, drags join up smoothly",
                    "§7Line / Rectangle / Frame: drag out a shape",
                    "§7Bucket fill: fill a connected area",
                    "§7Eyedropper: pick the material and shade",
                    "§7Right-click erases with the same tool"};
            case BTN_MIRROR -> new String[]{"§eMirror: " + (this.mirrorX ? "§aOn" : "§cOff"), "§7Draw on both sides of the centre line"};
            case BTN_THICK -> new String[]{"§eThickness: " + THICKNESS[this.thicknessIdx] + (THICKNESS[this.thicknessIdx] == 1 ? " layer" : " layers"),
                    "§7Click to cycle 1 / 3 / 5", "§7Extrudes what you draw onto the layers around it"};
            case BTN_PROFILE -> new String[]{"§eShape: " + EditorGeometry.PROFILE_NAMES[this.profile] + " §7(click to cycle)",
                    "§7Flat: same outline on every layer",
                    "§7Tapered: outer layers shrink at the edges",
                    "§7Thick centre: fat in the middle, thin edges"};
            case BTN_TOOL -> new String[]{"§eTool: " + TOOL_NAMES[this.tool3d] + " §7(click to cycle)",
                    "§7Add: place a voxel on the face you point at",
                    "§7Recolor: repaint the voxel you point at",
                    "§7Erase: remove the voxel you point at",
                    "§cErased materials are destroyed!",
                    "§7Right-drag orbits, middle-drag pans, wheel zooms"};
            case BTN_RESET -> new String[]{"§eReset view", "§7Restore the default angle, zoom and position"};
            default -> new String[]{"§eAbility Codex (Guide)"};
        };
    }

    private void transformVoxels(java.util.function.UnaryOperator<WeaponVoxel> op) {
        if (workingVoxels.isEmpty()) return;
        pushUndo(new ArrayList<>(workingVoxels));
        List<WeaponVoxel> out = new ArrayList<>();
        for (WeaponVoxel v : workingVoxels) out.add(op.apply(v));
        this.workingVoxels.clear();
        this.workingVoxels.addAll(out);
        recalculateCachedStats();
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void handleButtonAction(int row) {
        int n = this.gridSize - 1;
        switch (row) {
            case BTN_EXPORT -> {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                WeaponOffset currentOffset = new WeaponOffset(this.workingOffsetX, this.workingOffsetY);
                this.minecraft.setScreen(new ExportDesignScreen(this, new DesignEntry(
                        this.recentFileName, false, this.workingVoxels, currentOffset,
                        this.recentAttribution, this.recentCrafter), this.gridSize));
            }
            case BTN_IMPORT -> {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                this.minecraft.setScreen(new DesignLibraryScreen(this));
            }
            case BTN_FLIP_H -> transformVoxels(v -> new WeaponVoxel(n - v.x(), v.y(), v.z(), v.materialId(), v.shade()));
            case BTN_FLIP_V -> transformVoxels(v -> new WeaponVoxel(v.x(), n - v.y(), v.z(), v.materialId(), v.shade()));
            case BTN_ROT_CW -> transformVoxels(v -> new WeaponVoxel(v.y(), n - v.x(), v.z(), v.materialId(), v.shade()));
            case BTN_ROT_CCW -> transformVoxels(v -> new WeaponVoxel(n - v.y(), v.x(), v.z(), v.materialId(), v.shade()));
            case BTN_VIEW -> {
                this.view3d = !this.view3d;
                this.orbiting = false;
                this.panning = false;
                this.strokeAxis = -1;
                this.sceneDirty = true;
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            case BTN_UNDO -> undo();
            case BTN_REDO -> redo();
            case BTN_TOOL2D -> {
                this.tool2d = (this.tool2d + 1) % TOOL2D_NAMES.length;
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            case BTN_MIRROR -> {
                this.mirrorX = !this.mirrorX;
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            case BTN_THICK -> {
                this.thicknessIdx = (this.thicknessIdx + 1) % THICKNESS.length;
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            case BTN_PROFILE -> {
                this.profile = (this.profile + 1) % EditorGeometry.PROFILE_NAMES.length;
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            case BTN_TOOL -> {
                this.tool3d = (this.tool3d + 1) % TOOL_NAMES.length;
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            case BTN_RESET -> {
                ensureRaycaster();
                this.raycaster.resetView();
                this.sceneDirty = true;
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            case BTN_CODEX -> {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
                this.minecraft.setScreen(new AbilityBookScreen(this));
            }
            default -> { }
        }
    }

    private void drawSmallHeaderButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String text, boolean hovered) {
        int bg = hovered ? 0xFFD6D6D6 : 0xFFBCBCBC;
        g.fill(x, y, x + w, y + h, bg);

        int highlight = hovered ? 0xFFFFFFFF : 0xFFE0E0E0;
        int shadow = 0xFF555555;

        g.fill(x, y, x + w - 1, y + 1, highlight);
        g.fill(x, y, x + 1, y + h - 1, highlight);
        g.fill(x + 1, y + h - 1, x + w, y + h, shadow);
        g.fill(x + w - 1, y + 1, x + w, y + h, shadow);

        g.fill(x - 1, y - 1, x + w + 1, y, 0xFF373737);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, 0xFF373737);
        g.fill(x - 1, y, x, y + h, 0xFF373737);
        g.fill(x + w, y, x + w + 1, y + h, 0xFF373737);

        int textX = x + (w - this.font.width(text)) / 2;
        int textY = y + (h - 7) / 2;
        g.text(this.font, Component.literal(text), textX, textY, hovered ? 0xFF000000 : 0xFF303030, false);
    }

    private void drawVanillaArrowButton(GuiGraphicsExtractor g, int x, int y, String arrow, boolean enabled, boolean hovered) {
        int bg = !enabled ? 0xFF8B8B8B : (hovered ? 0xFFD6D6D6 : 0xFFBCBCBC);
        g.fill(x, y, x + ARROW_BTN_W, y + ARROW_BTN_H, bg);

        int highlight = !enabled ? 0xFFA0A0A0 : (hovered ? 0xFFFFFFFF : 0xFFE0E0E0);
        int shadow = !enabled ? 0xFF666666 : (hovered ? 0xFF666666 : 0xFF555555);

        g.fill(x, y, x + ARROW_BTN_W - 1, y + 1, highlight);
        g.fill(x, y, x + 1, y + ARROW_BTN_H - 1, highlight);
        g.fill(x + 1, y + ARROW_BTN_H - 1, x + ARROW_BTN_W, y + ARROW_BTN_H, shadow);
        g.fill(x + ARROW_BTN_W - 1, y + 1, x + ARROW_BTN_W, y + ARROW_BTN_H, shadow);

        g.fill(x - 1, y - 1, x + ARROW_BTN_W + 1, y, 0xFF373737);
        g.fill(x - 1, y + ARROW_BTN_H, x + ARROW_BTN_W + 1, y + ARROW_BTN_H + 1, 0xFF373737);
        g.fill(x - 1, y, x, y + ARROW_BTN_H, 0xFF373737);
        g.fill(x + ARROW_BTN_W, y, x + ARROW_BTN_W + 1, y + ARROW_BTN_H, 0xFF373737);

        int textColor = !enabled ? 0xFF666666 : (hovered ? 0xFF000000 : 0xFF303030);
        int textX = x + (ARROW_BTN_W - this.font.width(arrow)) / 2 + 1;
        int textY = y + (ARROW_BTN_H - 7) / 2;
        g.text(this.font, Component.literal(arrow), textX, textY, textColor, false);
    }

    private void drawSaveButton(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hovered, boolean saved) {
        int bg = saved ? 0xFF2E5E2E : (hovered ? 0xFF4A4A4A : 0xFF383838);
        g.fill(x, y, x + w, y + h, bg);

        int highlight = saved ? 0xFF55AA55 : (hovered ? 0xFFFFFFFF : 0xFF666666);
        int shadow = saved ? 0xFF1B3D1B : 0xFF222222;

        g.fill(x, y, x + w - 1, y + 1, highlight);
        g.fill(x, y, x + 1, y + h - 1, highlight);
        g.fill(x + 1, y + h - 1, x + w, y + h, shadow);
        g.fill(x + w - 1, y + 1, x + w, y + h, shadow);

        g.fill(x - 1, y - 1, x + w + 1, y, 0xFF1E1E1E);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, 0xFF1E1E1E);
        g.fill(x - 1, y, x, y + h, 0xFF1E1E1E);
        g.fill(x + w, y, x + w + 1, y + h, 0xFF1E1E1E);
    }
}