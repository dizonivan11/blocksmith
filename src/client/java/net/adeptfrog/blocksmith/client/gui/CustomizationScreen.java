package net.adeptfrog.blocksmith.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.adeptfrog.blocksmith.ability.WeaponAbility;
import net.adeptfrog.blocksmith.ability.WeaponAbilityRegistry;
import net.adeptfrog.blocksmith.data.MaterialGroup;
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

    private static final String[] MIRROR_NAMES = {"Off", "Horizontal (left/right)", "Vertical (top/bottom)", "Diagonal /", "Diagonal \\", "Depth (front/back layers)"};
    private static final String[] MIRROR_TAGS = {"", "H", "V", "/", "\\", "Z"};
    private int mirrorMode = 0; // 0 off, 1 horizontal, 2 vertical, 3 diagonal /, 4 diagonal \, 5 depth (layer -> -layer)
    private int lastPx = -1, lastPy = -1;

    // Layer strip: per-layer visibility and lock, indexed by z + MAX_LAYER
    private final boolean[] layerHidden = new boolean[LAYER_COUNT];
    private final boolean[] layerLocked = new boolean[LAYER_COUNT];
    private static final int STATS_X = 404; // left edge of the bonus-stats panel; its text starts 6 px further in
    private static final int THUMB = 14, LAYER_ROW_H = 16, LAYER_LIST_X = STATS_X + 6, LAYER_LIST_Y = 213, LAYER_LIST_W = 88, LAYER_LIST_H = 96, SCROLLBAR_W = 12, SCROLLER_H = 15;
    // The scroll bar thumb of the creative inventory tab
    private static final Identifier SCROLLER_SPRITE = Identifier.withDefaultNamespace("container/creative_inventory/scroller");
    private static final Identifier SCROLLER_DISABLED_SPRITE = Identifier.withDefaultNamespace("container/creative_inventory/scroller_disabled");
    private double layerScroll = 0;           // pixels scrolled down in the layers list
    private boolean draggingScrollbar = false;
    private int scrollGrab = 0;               // where on the thumb the scroll bar was grabbed
    private static final int RECENT_SIZE = 9, RECENT_PITCH = 11, RECENT_X = STATS_X + 6, RECENT_Y = 326, MAX_RECENT = 8;
    private final int[][] thumbPixels = new int[LAYER_COUNT][THUMB * THUMB];
    private boolean thumbsDirty = true;

    // Undo / redo: whole-design snapshots, one per stroke or action
    private static final int MAX_UNDO = 60;
    private final Deque<List<WeaponVoxel>> undoStack = new ArrayDeque<>();
    private final Deque<List<WeaponVoxel>> redoStack = new ArrayDeque<>();
    private List<WeaponVoxel> strokeSnapshot = null;

    private record Swatch(VoxelMaterial material, int shade) {}
    private final List<Swatch> recentSwatches = new ArrayList<>();

    // Tools: the same five in the 2D and 3D editors (Pencil in 2D is called Add in 3D)
    private static final int TOOL_ADD = 0, TOOL_RECOLOR = 1, TOOL_ERASE = 2, TOOL_PICK = 3, TOOL_FILL = 4,
            TOOL_SELECT = 5, TOOL_MOVE = 6;
    private static final String[] TOOL_KEYS = {"P", "O", "R", "I", "B", "S", "V"};
    private int tool = TOOL_ADD;

    private String toolName(int i) {
        return switch (i) {
            case TOOL_ADD -> this.view3d ? "Add" : "Pencil";
            case TOOL_RECOLOR -> "Recolor";
            case TOOL_ERASE -> "Erase";
            case TOOL_PICK -> "Eyedropper";
            case TOOL_SELECT -> "Select";
            case TOOL_MOVE -> "Move";
            default -> "Bucket fill";
        };
    }
    // Left-column icon buttons: one 18x18 vanilla-item icon per action, each with a tooltip.
    // Reset view only exists in the 3D editor. The five tool buttons come first, one button per tool (identical in
    // both editors); the active one is outlined.
    private static final int BTN_TOOL_FIRST = 0, BTN_TOOL_SLOTS = 7, BTN_FLIP_H = 7, BTN_FLIP_V = 8, BTN_ROT_CW = 9,
            BTN_ROT_CCW = 10, BTN_VIEW = 11, BTN_UNDO = 12, BTN_REDO = 13, BTN_MIRROR = 14,
            BTN_RESET = 15, BTN_CODEX = 16, BTN_COUNT = 17;
    private static final int ICON_BTN = 18, ICON_STEP = 20, EI_BTN_W = 44;
    // Vanilla items drawn at runtime as the button icons: nothing is shipped, so no asset or licence is involved.
    private static final ItemStack ICON_FLIP_H = new ItemStack(Items.PISTON);
    private static final ItemStack ICON_FLIP_V = new ItemStack(Items.STICKY_PISTON);
    private static final ItemStack ICON_ROT_CW = new ItemStack(Items.REPEATER);
    private static final ItemStack ICON_ROT_CCW = new ItemStack(Items.COMPARATOR);
    private static final ItemStack ICON_VIEW_3D = new ItemStack(Items.ENDER_EYE);
    private static final ItemStack ICON_VIEW_2D = new ItemStack(Items.MAP);
    private static final ItemStack[] ICON_TOOLS = {new ItemStack(Items.STICK), new ItemStack(Items.PAINTING), new ItemStack(Items.SPONGE), new ItemStack(Items.SPYGLASS), new ItemStack(Items.BUCKET), new ItemStack(Items.SHEARS), new ItemStack(Items.LEAD)};
    private static final ItemStack ICON_RESET = new ItemStack(Items.COMPASS);
    private static final ItemStack ICON_UNDO = new ItemStack(Items.CLOCK);
    private static final ItemStack ICON_MIRROR = new ItemStack(Items.GLASS_PANE);
    private static final ItemStack ICON_CODEX = new ItemStack(Items.ENCHANTED_BOOK);
    private boolean view3d = true;
    private boolean orbiting = false; // right-drag in the 3D view
    private boolean panning = false; // middle-drag in the 3D view
    private int strokeAxis = -1;   // axis of the plane a paint-drag stays on, -1 when no stroke is active
    private int strokeIndex = 0;
    private boolean sceneDirty = true;
    private double fullRenderMs = 0; // smoothed cost of one full-detail 3D pass

    // Cached so the per-frame work and the per-voxel checks stay cheap (rebuilt by recalculateCachedStats)
    private int savedVoxelCount = 0;
    private final java.util.Map<String, Integer> originalCounts = new java.util.HashMap<>(); // voxels per material on the saved weapon
    private final java.util.Map<String, Integer> workingCounts = new java.util.HashMap<>();  // voxels per material in the design
    private boolean cachedMissing = false;
    private long nextMissingCheck = 0;
    private int lastHoverKey = -2;
    private VoxelRaycaster.Hit hoverHit = null;
    private VoxelRaycaster raycaster;
    private int renderScale = 0;   // render pixels per GUI pixel the raycaster and textures were built for
    private int[] fgBase;          // model layer (model + floor grid + outline) at native resolution, no hover highlight
    private int[] fgView;          // fgBase plus hover highlight
    private int[] bgBuffer;        // animated background, BG_DIM x BG_DIM (stretched over the workspace; it is a soft gradient)
    private int[] sceneGrid;       // scratch grid for VoxelRaycaster.syncCells
    private int[] lastHlRect;      // area the previous hover highlight touched in fgView
    private boolean bgDirty = true;
    private static final int BG_DIM = 192;
    // Backdrop: a calm "studio" look, a soft glow behind the middle fading to darker corners (plus a faint
    // checkerboard under the 2D grid). Nothing moves and nothing competes with the voxel colours.
    private static final int BACKDROP_CENTRE = 0x4A5160, BACKDROP_EDGE = 0x2A2E37;
    private long hintUntil = 0; // control hint is shown for a few seconds, then fades; hovering it brings it back

    // Fit-to-screen: the whole layout (window, icon column on the left, material list on the right) is drawn in
    // "virtual" GUI coordinates and scaled down by fit (< 1) only when it would not be fully visible.
    private static final int FIT_LEFT = 24, FIT_RIGHT = 58, FIT_MARGIN = 4;
    private double fit = 1.0;

    private void updateFit() {
        double needW = WINDOW_WIDTH + 2.0 * Math.max(FIT_LEFT, FIT_RIGHT) + 2 * FIT_MARGIN;
        double needH = WINDOW_HEIGHT + 2.0 * FIT_MARGIN;
        double f = Math.min(1.0, Math.min(this.width / needW, this.height / needH));
        this.fit = f >= 0.999 ? 1.0 : Math.max(0.2, f);
    }

    /** Screen size in virtual (layout) coordinates. */
    private int vw() { return (int) Math.floor(this.width / this.fit); }
    private int vh() { return (int) Math.floor(this.height / this.fit); }

    private List<ClientTooltipComponent> pendingTooltip = null;
    private int pendingTipX, pendingTipY;

    /**
     * Queues a tooltip at the real (unscaled) cursor position so it keeps its normal size while the layout is scaled
     * down. It is drawn after everything else ({@link #flushTooltip}) so nothing paints over it.
     */
    private void realTooltip(GuiGraphicsExtractor g, List<ClientTooltipComponent> lines, int realX, int realY) {
        this.pendingTooltip = lines;
        this.pendingTipX = realX;
        this.pendingTipY = realY;
    }

    private void flushTooltip(GuiGraphicsExtractor g) {
        List<ClientTooltipComponent> lines = this.pendingTooltip;
        this.pendingTooltip = null;
        if (lines == null) return;
        g.tooltip(this.font, lines, pendingTipX, pendingTipY, DefaultTooltipPositioner.INSTANCE, null);
    }
    private int realMouseX, realMouseY;
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
    private static final int WINDOW_WIDTH = 509;
    private static final int WINDOW_HEIGHT = 445;

    // Workspace Allocated Area
    private static final int WORKSPACE_OFFSET_X = 12;
    private static final int WORKSPACE_OFFSET_Y = 12;
    private static final int WORKSPACE_DIM = 384;

    // Bottom Slot Configuration
    private static final int VISIBLE_SLOTS = 8;
    private static final int SLOT_SIZE = 16;
    private static final int MAT_SLOT_SPACING = 18;
    private static final int SHADE_SLOT_SPACING = 18;

    private static final int MAT_START_X = 8;
    private static final int SHADE_START_X = 164;
    private static final int SLOTS_Y = 421;

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
    private final List<WeaponAbility> cachedAbilities = new ArrayList<>();

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
        revealActiveLayer();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Hotkeys: P pencil/add, B bucket, I eyedropper, O recolor, R erase, M mirror,
     * 1-5 shade, H hide and K lock the edited layer, [ ] layer down/up, Tab view.
     */
    private boolean handleHotkey(int key) {
        if (key == GLFW.GLFW_KEY_TAB) {
            handleButtonAction(BTN_VIEW);
            return true;
        }
        if (key == GLFW.GLFW_KEY_M) {
            handleButtonAction(BTN_MIRROR);
            return true;
        }
        if ((key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_5) || (key >= GLFW.GLFW_KEY_KP_1 && key <= GLFW.GLFW_KEY_KP_5)) {
            int shade = key >= GLFW.GLFW_KEY_KP_1 ? key - GLFW.GLFW_KEY_KP_1 : key - GLFW.GLFW_KEY_1;
            int[] palette = this.selectedMaterial == null ? null : this.selectedMaterial.getPalette();
            if (palette != null && shade < palette.length) this.selectedShade = shade;
            return true;
        }
        int li = this.activeLayer + MAX_LAYER;
        if (key == GLFW.GLFW_KEY_H) { // hide / show the layer being edited
            this.layerHidden[li] = !this.layerHidden[li];
            this.sceneDirty = true;
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        if (key == GLFW.GLFW_KEY_K) { // lock / unlock it
            this.layerLocked[li] = !this.layerLocked[li];
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        if ((key == GLFW.GLFW_KEY_RIGHT_BRACKET || key == GLFW.GLFW_KEY_LEFT_BRACKET)) {
            int next = this.activeLayer + (key == GLFW.GLFW_KEY_RIGHT_BRACKET ? 1 : -1);
            if (next >= MIN_LAYER && next <= MAX_LAYER) {
                this.activeLayer = next;
                this.sceneDirty = true;
                revealActiveLayer();
            }
            return true;
        }
        String[] keys = TOOL_KEYS;
        String pressed = switch (key) {
            case GLFW.GLFW_KEY_P -> "P";
            case GLFW.GLFW_KEY_B -> "B";
            case GLFW.GLFW_KEY_I -> "I";
            case GLFW.GLFW_KEY_O -> "O";
            case GLFW.GLFW_KEY_R -> "R";
            case GLFW.GLFW_KEY_S -> "S";
            case GLFW.GLFW_KEY_V -> "V";
            default -> null;
        };
        if (pressed == null) return false;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equals(pressed)) {
                handleButtonAction(i);
                return true;
            }
        }
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
        if (ctrl && !shift && handleSelectionShortcut(key)) return true;
        if (!ctrl && !shift && handleSelectionKey(key)) return true;
        if (!ctrl && !shift && handleHotkey(key)) return true;
        if (key == GLFW.GLFW_KEY_PAGE_UP && this.activeLayer < MAX_LAYER) {
            this.activeLayer++;
            this.sceneDirty = true;
            revealActiveLayer();
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_DOWN && this.activeLayer > MIN_LAYER) {
            this.activeLayer--;
            this.sceneDirty = true;
            revealActiveLayer();
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
        // Scrolling over the layers list moves through the layers
        {
            int leftPos = (vw() - WINDOW_WIDTH) / 2, topPos = (vh() - WINDOW_HEIGHT) / 2;
            if (verticalAmount != 0 && overLayerList(leftPos, topPos, (int) (mouseX / this.fit), (int) (mouseY / this.fit))) {
                this.layerScroll -= verticalAmount * LAYER_ROW_H;
                clampLayerScroll();
                return true;
            }
        }

        // Scrolling over the 3D view zooms it
        if (this.view3d && verticalAmount != 0 && isInsideWorkspace((int) (mouseX / this.fit), (int) (mouseY / this.fit))) {
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
                refreshSlots();
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, delta);
        updateFit();
        this.realMouseX = mouseX;
        this.realMouseY = mouseY;
        this.pendingTooltip = null;
        if (this.fit == 1.0) {
            renderContents(guiGraphics, mouseX, mouseY, delta);
        } else {
            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().scale((float) this.fit, (float) this.fit);
            renderContents(guiGraphics, (int) Math.floor(mouseX / this.fit), (int) Math.floor(mouseY / this.fit), delta);
            guiGraphics.pose().popMatrix();
        }
        flushTooltip(guiGraphics);
    }

    private void renderContents(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {

        if (saveFeedbackTimer > 0) saveFeedbackTimer--;
        else if (saveFeedbackTimer < 0) saveFeedbackTimer++;

        if (exportFeedbackTimer > 0) exportFeedbackTimer--;
        if (importFeedbackTimer > 0) importFeedbackTimer--;
        else if (importFeedbackTimer < 0) importFeedbackTimer++;

        this.curMouseX = mouseX;
        this.curMouseY = mouseY;
        int leftPos = (vw() - WINDOW_WIDTH) / 2;
        int topPos = (vh() - WINDOW_HEIGHT) / 2;

        // Background texture
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, GUI_TEXTURE, leftPos, topPos, 0.0F, 0.0F, WINDOW_WIDTH, WINDOW_HEIGHT, 512, 512);

        // Workspace
        int gridX = leftPos + WORKSPACE_OFFSET_X;
        int gridY = topPos + WORKSPACE_OFFSET_Y;

        if (this.view3d) {
            render3dWorkspace(guiGraphics, gridX, gridY, mouseX, mouseY);
        } else {
            ensureViewTexture();
            drawBackdrop(guiGraphics, gridX, gridY);
            // Only every 8th grid line is drawn; the cell under the cursor gets its own outline
            int wsSize = this.gridSize * this.cellSize;
            for (int i = 0; i <= this.gridSize; i += 8) {
                guiGraphics.fill(gridX + i * this.cellSize, gridY, gridX + i * this.cellSize + 1, gridY + wsSize, 0x40FFFFFF);
                guiGraphics.fill(gridX, gridY + i * this.cellSize, gridX + wsSize, gridY + i * this.cellSize + 1, 0x40FFFFFF);
            }
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
                guiGraphics.fill(px, py, px + this.cellSize, py + this.cellSize, 0x33FFFFFF);
                outline(guiGraphics, px, py, this.cellSize, this.cellSize, 0xAAFFFFFF);
            }

            // Selection
            if (this.selActive && this.selLayer == this.activeLayer) {
                int sx0 = gridX + this.selX0 * this.cellSize;
                int sx1 = gridX + (this.selX1 + 1) * this.cellSize;
                int sy0 = gridY + (this.gridSize - 1 - this.selY1) * this.cellSize;
                int sy1 = gridY + (this.gridSize - this.selY0) * this.cellSize;
                guiGraphics.fill(sx0, sy0, sx1, sy1, 0x2255AAFF);
                int ph = (int) (System.currentTimeMillis() / 90);
                drawAntLine(guiGraphics, gridX, gridY, sx0, sy0, sx1, sy0, ph);
                drawAntLine(guiGraphics, gridX, gridY, sx1, sy0, sx1, sy1, ph);
                drawAntLine(guiGraphics, gridX, gridY, sx1, sy1, sx0, sy1, ph);
                drawAntLine(guiGraphics, gridX, gridY, sx0, sy1, sx0, sy0, ph);
            }

            // Mirror axis
            if (this.mirrorMode != 0) {
                int size = this.gridSize * this.cellSize;
                if (this.mirrorMode == 1) {
                    int axisX = gridX + size / 2;
                    guiGraphics.fill(axisX, gridY, axisX + 1, gridY + size, 0xAAFFFF55);
                } else if (this.mirrorMode == 2) {
                    int axisY = gridY + size / 2;
                    guiGraphics.fill(gridX, axisY, gridX + size, axisY + 1, 0xAAFFFF55);
                } else {
                    for (int i = 0; i < this.gridSize; i++) {
                        int dx = this.mirrorMode == 3 ? i : this.gridSize - 1 - i;
                        int px = gridX + dx * this.cellSize + this.cellSize / 2;
                        int py = gridY + (this.gridSize - 1 - i) * this.cellSize + this.cellSize / 2;
                        guiGraphics.fill(px, py, px + 1, py + 1, 0xAAFFFF55);
                    }
                }
            }

        }

        // Stats panel
        int statsX = leftPos + STATS_X;
        int statsY = topPos + 35;

        boolean isBow = weaponStack.getItem() instanceof ModularBowItem;
        int diffVoxels = workingVoxels.size() - this.savedVoxelCount;

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

        String leftLabel = "[Left] " + toolName(this.tool);
        guiGraphics.text(this.font, Component.literal(leftLabel), statsX + 6, statsY + 104, FONT_COLOR, false);

        Component eraseText = Component.literal(this.view3d ? "[Right] Orbit" : (this.tool == TOOL_PICK || this.tool == TOOL_MOVE ? "" : this.tool == TOOL_SELECT ? "[Right] Deselect" : "[Right] Erase "));
        guiGraphics.text(this.font, eraseText, statsX + 6, statsY + 114, FONT_COLOR, false);

        // Erase warning icon
        int infoX = statsX + 6 + this.font.width(eraseText);
        int infoY = statsY + 114;
        Component infoIcon = Component.literal(this.view3d || this.tool == TOOL_PICK || this.tool == TOOL_SELECT || this.tool == TOOL_MOVE ? "" : "§6(!)");
        guiGraphics.text(this.font, infoIcon, infoX, infoY, 0xFFFFFFFF, false);

        // Hover detection for Erase warning icon
        int infoW = this.font.width(infoIcon);
        if (!this.view3d && this.tool != TOOL_PICK && this.tool != TOOL_SELECT && this.tool != TOOL_MOVE && mouseX >= infoX - 1 && mouseX <= infoX + infoW + 1 && mouseY >= infoY - 1 && mouseY <= infoY + 9) {
            List<ClientTooltipComponent> infoTooltip = List.of(
                    ClientTooltipComponent.create(Component.literal("§cMaterials will be destroyed upon erasing!").getVisualOrderText())
            );
            realTooltip(guiGraphics, infoTooltip, realMouseX, realMouseY);
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

        // Import / Export text buttons sit to the left of Save
        int importX = btnX - 4 - EI_BTN_W, exportX = importX - 4 - EI_BTN_W;
        boolean hovImport = mouseX >= importX && mouseX <= importX + EI_BTN_W && mouseY >= saveBtnY && mouseY <= saveBtnY + btnH;
        boolean hovExport = mouseX >= exportX && mouseX <= exportX + EI_BTN_W && mouseY >= saveBtnY && mouseY <= saveBtnY + btnH;
        drawSaveButton(guiGraphics, exportX, saveBtnY, EI_BTN_W, btnH, hovExport, exportFeedbackTimer > 0);
        drawSaveButton(guiGraphics, importX, saveBtnY, EI_BTN_W, btnH, hovImport, importFeedbackTimer > 0);
        Component exportLabel = Component.literal(exportFeedbackTimer > 0 ? "§aCopied!" : (hovExport ? "§fExport" : "§7Export"));
        Component importLabel = Component.literal(importFeedbackTimer > 0 ? "§aLoaded!" : (importFeedbackTimer < 0 ? "§cInvalid!" : (hovImport ? "§fImport" : "§7Import")));
        guiGraphics.text(this.font, exportLabel, exportX + (EI_BTN_W - this.font.width(exportLabel)) / 2, saveBtnY + 6, 0xFFFFFFFF, false);
        guiGraphics.text(this.font, importLabel, importX + (EI_BTN_W - this.font.width(importLabel)) / 2, saveBtnY + 6, 0xFFFFFFFF, false);

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
            String keyHint = buttonKey(row);
            if (keyHint != null) {
                guiGraphics.text(this.font, Component.literal(keyHint), subBtnX + 2, rowY + 2, 0xFFFFE066, true);
            }
            String overlay = buttonOverlay(row);
            if (overlay != null) {
                guiGraphics.text(this.font, Component.literal(overlay), subBtnX + ICON_BTN - 6, rowY + ICON_BTN - 9, 0xFFFFFFFF, true);
            }
            if ((row == BTN_MIRROR && this.mirrorMode != 0) || (row < BTN_TOOL_SLOTS && toolActive(row))) {
                int gc = 0xFF55FF55;
                guiGraphics.fill(subBtnX - 1, rowY - 1, subBtnX + ICON_BTN + 1, rowY, gc);
                guiGraphics.fill(subBtnX - 1, rowY + ICON_BTN, subBtnX + ICON_BTN + 1, rowY + ICON_BTN + 1, gc);
                guiGraphics.fill(subBtnX - 1, rowY, subBtnX, rowY + ICON_BTN, gc);
                guiGraphics.fill(subBtnX + ICON_BTN, rowY, subBtnX + ICON_BTN + 1, rowY + ICON_BTN, gc);
            }
        }

        drawLayerPanel(guiGraphics, leftPos, topPos, mouseX, mouseY);
        drawRecentSwatches(guiGraphics, leftPos, topPos, mouseX, mouseY);

        if (hoveredBtn >= 0) {
            List<ClientTooltipComponent> tip = new ArrayList<>();
            for (String line : buttonTooltip(hoveredBtn)) {
                tip.add(ClientTooltipComponent.create(Component.literal(line).getVisualOrderText()));
            }
            realTooltip(guiGraphics, tip, realMouseX, realMouseY);
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
        int arrowY = topPos + SLOTS_Y - 13;
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

        drawSelectionStatus(guiGraphics, leftPos + WORKSPACE_OFFSET_X, topPos + WORKSPACE_OFFSET_Y);

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
            realTooltip(guiGraphics, tooltipLines, realMouseX, realMouseY);
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

        drawAbilityPanel(guiGraphics, breakdownX, startY + Math.min(cachedBreakdown.size(), MAX_BREAKDOWN) * rowH + 8, mouseX, mouseY);


    }

    /** Lists the abilities the current design activates, with the material-group icons each one requires. */
    private void drawAbilityPanel(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
        g.text(this.font, Component.literal("§6✦ Abilities"), x, y, 0xFFFFFFFF, true);
        y += 12;

        boolean disabled = net.adeptfrog.blocksmith.data.VoxelMaterialRegistry.areAbilitiesDisabled();
        if (disabled) {
            g.text(this.font, Component.literal("Disabled in settings"), x, y, 0xFFFF7777, false);
            return;
        }
        int need = WeaponAbility.minVoxelCount();
        if (workingVoxels.size() < need) {
            g.text(this.font, Component.literal("Needs " + need + " voxels"), x, y, 0xFFFFFFFF, false);
            g.text(this.font, Component.literal("(" + workingVoxels.size() + "/" + need + ")"), x, y + 10, 0xFFAAAAAA, false);
            return;
        }
        if (cachedAbilities.isEmpty()) {
            g.text(this.font, Component.literal("None active"), x, y, 0xFFAAAAAA, false);
            return;
        }

        WeaponAbility hovered = null;
        for (WeaponAbility ability : cachedAbilities) {
            Component name = ability.displayName();
            g.text(this.font, name, x, y, 0xFFFFFFFF, true);
            int nameW = this.font.width(name);
            if (mouseX >= x && mouseX < x + nameW && mouseY >= y - 1 && mouseY < y + 10) hovered = ability;
            y += 11;

            int ix = x;
            for (java.util.Map.Entry<MaterialGroup, Double> req : ability.requiredThresholds().entrySet()) {
                ItemStack icon = req.getKey().icon().getDefaultInstance();
                g.pose().pushMatrix();
                g.pose().translate(ix, y);
                g.pose().scale(0.75f, 0.75f);
                g.fakeItem(icon, 0, 0);
                g.pose().popMatrix();
                String pct = req.getValue().intValue() + "%";
                g.text(this.font, Component.literal(pct), ix + 13, y + 2, 0xFF000000 | req.getKey().color(), true);
                ix += 13 + this.font.width(pct) + 6;
            }
            y += 16;
        }

        if (hovered != null) {
            List<ClientTooltipComponent> lines = new ArrayList<>();
            lines.add(ClientTooltipComponent.create(hovered.displayName().getVisualOrderText()));
            for (net.minecraft.network.chat.FormattedText w : this.font.getSplitter().splitLines(hovered.description(), 180, net.minecraft.network.chat.Style.EMPTY)) {
                lines.add(ClientTooltipComponent.create(Component.literal("§7" + w.getString().trim()).getVisualOrderText()));
            }
            realTooltip(g, lines, realMouseX, realMouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int leftPos = (vw() - WINDOW_WIDTH) / 2;
        int topPos = (vh() - WINDOW_HEIGHT) / 2;
        int mouseX = (int) Math.floor(event.x() / this.fit);
        int mouseY = (int) Math.floor(event.y() / this.fit);

        int statsX = leftPos + STATS_X;
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

        {
            int importX = btnX - 4 - EI_BTN_W, exportX = importX - 4 - EI_BTN_W;
            if (mouseY >= saveBtnY && mouseY <= saveBtnY + btnH) {
                if (mouseX >= exportX && mouseX <= exportX + EI_BTN_W) { openExport(); return true; }
                if (mouseX >= importX && mouseX <= importX + EI_BTN_W) { openImport(); return true; }
            }
        }
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
        int arrowY = topPos + SLOTS_Y - 13;
        int leftArrowX = leftPos + 126;
        int rightArrowX = leftPos + 140;

        List<VoxelMaterial> mats = new ArrayList<>(VoxelMaterialRegistry.getAll());
        int maxOffset = Math.max(0, mats.size() - VISIBLE_SLOTS);

        if (mouseX >= leftArrowX && mouseX <= leftArrowX + ARROW_BTN_W && mouseY >= arrowY && mouseY <= arrowY + ARROW_BTN_H) {
            if (materialIndexOffset > 0) {
                materialIndexOffset--;
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                refreshSlots();
                return true;
            }
        }

        if (mouseX >= rightArrowX && mouseX <= rightArrowX + ARROW_BTN_W && mouseY >= arrowY && mouseY <= arrowY + ARROW_BTN_H) {
            if (materialIndexOffset < maxOffset) {
                materialIndexOffset++;
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                refreshSlots();
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

        if (event.button() == 0 && handlePanelClick(leftPos, topPos, mouseX, mouseY)) {
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
        if (this.draggingScrollbar) {
            dragScrollbar((int) Math.floor(event.y() / this.fit));
            return true;
        }
        if (this.view3d) {
            if (this.orbiting) {
                ensureRaycaster();
                this.raycaster.orbit(-deltaX * 0.012, deltaY * 0.012);
                this.sceneDirty = true;
                return true;
            }
            if (this.panning) {
                ensureRaycaster();
                this.raycaster.pan(deltaX / this.fit * this.renderScale, deltaY / this.fit * this.renderScale);
                this.sceneDirty = true;
                return true;
            }
            if (handle3dInteraction((int) Math.floor(event.x() / this.fit), (int) Math.floor(event.y() / this.fit), event.button(), true)) {
                return true;
            }
        } else if (drag2d((int) Math.floor(event.x() / this.fit), (int) Math.floor(event.y() / this.fit), event.button())) {
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        this.draggingScrollbar = false;
        this.selecting = false;
        this.moving = false;
        this.moveBase = null;
        if (!this.view3d) finish2dStroke();
        this.strokeSnapshot = null;
        this.orbiting = false;
        this.panning = false;
        this.strokeAxis = -1;
        this.sceneDirty = true;
        return super.mouseReleased(event);
    }

    private boolean isInsideWorkspace(int mouseX, int mouseY) {
        int gridX = (vw() - WINDOW_WIDTH) / 2 + WORKSPACE_OFFSET_X;
        int gridY = (vh() - WINDOW_HEIGHT) / 2 + WORKSPACE_OFFSET_Y;
        return mouseX >= gridX && mouseX < gridX + WORKSPACE_DIM && mouseY >= gridY && mouseY < gridY + WORKSPACE_DIM;
    }

    private WeaponVoxel findVoxel(int x, int y, int layer) {
        for (WeaponVoxel v : workingVoxels) {
            if (v.x() == x && v.y() == y && v.z() == layer) return v;
        }
        return null;
    }

    // ----- Selection, move, copy / paste (shared by the 2D and 3D editors) -----
    // A selection is a rectangle of cells on one layer (selLayer); it covers the voxels of that layer inside it.

    private boolean selActive = false;
    private int selX0, selY0, selX1, selY1, selLayer;
    private boolean selecting = false;
    private int selAnchorX, selAnchorY;

    private boolean moving = false;
    private int moveAnchorX, moveAnchorY, moveDx, moveDy;
    private int[] moveOrig;
    private List<WeaponVoxel> moveBase, moveSel;
    private java.util.Set<WeaponVoxel> moveSelSet;

    private record ClipVoxel(int dx, int dy, VoxelMaterial material, int shade) {}
    private final List<ClipVoxel> clipboard = new ArrayList<>();
    private int clipW = 0, clipH = 0;

    private int curMouseX, curMouseY;
    private String toastMsg = "";
    private long toastUntil = 0;

    private void toast(String msg) {
        this.toastMsg = msg;
        this.toastUntil = System.nanoTime() + 2_500_000_000L;
    }

    private void clearSelection() {
        this.selActive = false;
        this.selecting = false;
    }

    private void setSelection(int x0, int y0, int x1, int y1, int layer) {
        this.selX0 = Math.clamp(Math.min(x0, x1), 0, this.gridSize - 1);
        this.selX1 = Math.clamp(Math.max(x0, x1), 0, this.gridSize - 1);
        this.selY0 = Math.clamp(Math.min(y0, y1), 0, this.gridSize - 1);
        this.selY1 = Math.clamp(Math.max(y0, y1), 0, this.gridSize - 1);
        this.selLayer = layer;
        this.selActive = true;
    }

    private void beginSelect(int x, int y) {
        this.selecting = true;
        this.selAnchorX = x;
        this.selAnchorY = y;
        setSelection(x, y, x, y, this.activeLayer);
    }

    private void beginMove(int x, int y) {
        if (!this.selActive) {
            toast("Select an area first");
            return;
        }
        if (isLayerLocked(this.selLayer)) {
            toast("That layer is locked or hidden");
            return;
        }
        if (!startMoveState()) return;
        this.moving = true;
        this.moveAnchorX = x;
        this.moveAnchorY = y;
    }

    private void dragSelectOrMove(int x, int y) {
        if (this.tool == TOOL_SELECT && this.selecting) {
            setSelection(this.selAnchorX, this.selAnchorY, x, y, this.selLayer);
        } else if (this.tool == TOOL_MOVE && this.moving) {
            applyMove(x - this.moveAnchorX, y - this.moveAnchorY);
        }
    }

    private List<WeaponVoxel> selectedVoxels() {
        List<WeaponVoxel> out = new ArrayList<>();
        if (!this.selActive) return out;
        for (WeaponVoxel v : this.workingVoxels) {
            if (v.z() == this.selLayer && v.x() >= this.selX0 && v.x() <= this.selX1 && v.y() >= this.selY0 && v.y() <= this.selY1) out.add(v);
        }
        return out;
    }

    private boolean startMoveState() {
        this.moveBase = new ArrayList<>(this.workingVoxels);
        this.moveSel = selectedVoxels();
        this.moveSelSet = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        this.moveSelSet.addAll(this.moveSel);
        this.moveOrig = new int[]{this.selX0, this.selY0, this.selX1, this.selY1};
        this.moveDx = 0;
        this.moveDy = 0;
        return true;
    }

    /**
     * Shows the selection's voxels shifted by (dx, dy) from where the move began. The result is always rebuilt from the
     * untouched starting state, so voxels the selection passes over come back when it moves on.
     */
    private void applyMove(int dx, int dy) {
        if (this.moveBase == null) return;
        dx = Math.clamp(dx, -this.moveOrig[0], this.gridSize - 1 - this.moveOrig[2]);
        dy = Math.clamp(dy, -this.moveOrig[1], this.gridSize - 1 - this.moveOrig[3]);
        if (dx == this.moveDx && dy == this.moveDy) return;

        java.util.Set<Long> targets = new java.util.HashSet<>();
        for (WeaponVoxel v : this.moveSel) targets.add(((long) (v.x() + dx) << 32) | (v.y() + dy));
        List<WeaponVoxel> result = new ArrayList<>(this.moveBase.size());
        for (WeaponVoxel v : this.moveBase) {
            if (this.moveSelSet.contains(v)) continue;
            if (v.z() == this.selLayer && targets.contains(((long) v.x() << 32) | v.y())) continue; // overwritten
            result.add(v);
        }
        for (WeaponVoxel v : this.moveSel) result.add(new WeaponVoxel(v.x() + dx, v.y() + dy, v.z(), v.material(), v.shade()));
        if (result.size() < MIN_VOXELS) return;

        if (dx != 0 || dy != 0) commitSnapshot();
        this.workingVoxels.clear();
        this.workingVoxels.addAll(result);
        this.selX0 = this.moveOrig[0] + dx;
        this.selX1 = this.moveOrig[2] + dx;
        this.selY0 = this.moveOrig[1] + dy;
        this.selY1 = this.moveOrig[3] + dy;
        this.moveDx = dx;
        this.moveDy = dy;
        recalculateCachedStats();
    }

    private void nudgeSelection(int dx, int dy) {
        if (!this.selActive || isLayerLocked(this.selLayer)) return;
        beginStroke();
        startMoveState();
        applyMove(dx, dy);
        this.strokeSnapshot = null;
        this.moveBase = null;
    }

    private void copySelection() {
        if (!this.selActive) {
            toast("Nothing selected");
            return;
        }
        this.clipboard.clear();
        for (WeaponVoxel v : selectedVoxels()) {
            this.clipboard.add(new ClipVoxel(v.x() - this.selX0, v.y() - this.selY0, v.material(), v.shade()));
        }
        this.clipW = this.selX1 - this.selX0 + 1;
        this.clipH = this.selY1 - this.selY0 + 1;
        toast(this.clipboard.isEmpty() ? "Selection is empty" : "Copied " + this.clipboard.size() + " voxels");
    }

    /** Pastes the clipboard centred on the cell under the cursor, on the selected layer, and selects what was pasted. */
    private void pasteClipboard() {
        if (this.clipboard.isEmpty()) {
            toast("Nothing to paste: select and copy first");
            return;
        }
        int[] cell = null;
        if (isInsideWorkspace(this.curMouseX, this.curMouseY)) {
            if (this.view3d) {
                ensureRaycaster();
                int gridX = (vw() - WINDOW_WIDTH) / 2 + WORKSPACE_OFFSET_X;
                int gridY = (vh() - WINDOW_HEIGHT) / 2 + WORKSPACE_OFFSET_Y;
                int px = (this.curMouseX - gridX) * this.renderScale + this.renderScale / 2;
                int py = (this.curMouseY - gridY) * this.renderScale + this.renderScale / 2;
                cell = this.raycaster.pickOnPlane(px, py, 2, this.activeLayer + MAX_LAYER);
            } else {
                cell = cellAt(this.curMouseX, this.curMouseY, false);
            }
        }
        if (cell == null) {
            toast("Point at the grid to paste");
            return;
        }
        if (isLayerLocked(this.activeLayer)) {
            toast("That layer is locked or hidden");
            return;
        }
        int ox = Math.clamp(cell[0] - this.clipW / 2, 0, Math.max(0, this.gridSize - this.clipW));
        int oy = Math.clamp(cell[1] - this.clipH / 2, 0, Math.max(0, this.gridSize - this.clipH));

        beginStroke();
        int placed = 0;
        for (ClipVoxel c : this.clipboard) {
            if (putVoxel(ox + c.dx(), oy + c.dy(), this.activeLayer, c.material(), c.shade())) placed++;
        }
        if (placed > 0) {
            commitSnapshot();
            recalculateCachedStats();
        }
        this.strokeSnapshot = null;
        setSelection(ox, oy, ox + this.clipW - 1, oy + this.clipH - 1, this.activeLayer);
        this.tool = TOOL_MOVE;
        int missed = this.clipboard.size() - placed;
        toast("Pasted " + placed + " voxels" + (missed > 0 ? " (" + missed + " skipped: no material left or no change)" : ""));
    }

    private void deleteSelection() {
        if (!this.selActive || isLayerLocked(this.selLayer)) return;
        beginStroke();
        boolean changed = false;
        for (WeaponVoxel v : selectedVoxels()) changed |= removeVoxel(v.x(), v.y(), v.z());
        if (changed) {
            commitSnapshot();
            recalculateCachedStats();
        }
        this.strokeSnapshot = null;
    }

    private boolean handleSelectionShortcut(int key) {
        switch (key) {
            case GLFW.GLFW_KEY_C -> { copySelection(); return true; }
            case GLFW.GLFW_KEY_V -> { pasteClipboard(); return true; }
            case GLFW.GLFW_KEY_A -> {
                setSelection(0, 0, this.gridSize - 1, this.gridSize - 1, this.activeLayer);
                return true;
            }
            case GLFW.GLFW_KEY_D -> { clearSelection(); return true; }
            default -> { return false; }
        }
    }

    private boolean handleSelectionKey(int key) {
        if (!this.selActive) return false;
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> nudgeSelection(-1, 0);
            case GLFW.GLFW_KEY_RIGHT -> nudgeSelection(1, 0);
            case GLFW.GLFW_KEY_UP -> nudgeSelection(0, 1);
            case GLFW.GLFW_KEY_DOWN -> nudgeSelection(0, -1);
            case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> deleteSelection();
            default -> { return false; }
        }
        return true;
    }

    /** A dotted "marching ants" line in GUI pixels, clipped to the workspace. */
    private void drawAntLine(GuiGraphicsExtractor g, int gridX, int gridY, double x0, double y0, double x1, double y1, int phase) {
        int steps = (int) Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
        if (steps < 1 || steps > 4000) return;
        for (int i = 0; i <= steps; i++) {
            int x = (int) Math.round(x0 + (x1 - x0) * i / steps);
            int y = (int) Math.round(y0 + (y1 - y0) * i / steps);
            if (x < gridX || y < gridY || x >= gridX + WORKSPACE_DIM || y >= gridY + WORKSPACE_DIM) continue;
            g.fill(x, y, x + 1, y + 1, ((i + phase) / 4) % 2 == 0 ? 0xFFFFFFFF : 0xFF1A1A1A);
        }
    }

    /** The selection rectangle drawn on the plane of its layer in the 3D view. */
    private void drawSelection3d(GuiGraphicsExtractor g, int gridX, int gridY) {
        if (!this.selActive || this.raycaster == null) return;
        double z = this.selLayer + MAX_LAYER + 0.5;
        double[][] c = {
                this.raycaster.project(this.selX0, this.selY0, z),
                this.raycaster.project(this.selX1 + 1, this.selY0, z),
                this.raycaster.project(this.selX1 + 1, this.selY1 + 1, z),
                this.raycaster.project(this.selX0, this.selY1 + 1, z)};
        double s = this.renderScale;
        int ph = (int) (System.currentTimeMillis() / 90);
        for (int i = 0; i < 4; i++) {
            double[] a = c[i], b = c[(i + 1) % 4];
            drawAntLine(g, gridX, gridY, gridX + a[0] / s, gridY + a[1] / s, gridX + b[0] / s, gridY + b[1] / s, ph);
        }
    }

    /** Small readout in the workspace corner: selection size, clipboard, and short messages. */
    private void drawSelectionStatus(GuiGraphicsExtractor g, int gridX, int gridY) {
        String text = null;
        long now = System.nanoTime();
        if (now < this.toastUntil) {
            text = this.toastMsg;
        } else if (this.selActive) {
            int w = this.selX1 - this.selX0 + 1, h = this.selY1 - this.selY0 + 1;
            text = "Selection " + w + "x" + h + " | " + selectedVoxels().size() + " voxels | layer " + this.selLayer;
        } else if (this.tool == TOOL_SELECT || this.tool == TOOL_MOVE) {
            text = this.tool == TOOL_SELECT ? "Drag to select an area" : "Select an area first";
        }
        if (text == null) return;
        int tw = this.font.width(text);
        int x = gridX + 4, y = gridY + 4;
        g.fill(x - 3, y - 2, x + tw + 3, y + 10, 0x90000000);
        g.text(this.font, Component.literal(text), x, y, 0xFFFFFFFF, false);
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
        int gridX = (vw() - WINDOW_WIDTH) / 2 + WORKSPACE_OFFSET_X;
        int gridY = (vh() - WINDOW_HEIGHT) / 2 + WORKSPACE_OFFSET_Y;
        int cx = Math.floorDiv(mouseX - gridX, this.cellSize);
        int cy = Math.floorDiv(mouseY - gridY, this.cellSize);
        if (cx < 0 || cx >= this.gridSize || cy < 0 || cy >= this.gridSize) {
            if (!clamp) return null;
            cx = Math.clamp(cx, 0, this.gridSize - 1);
            cy = Math.clamp(cy, 0, this.gridSize - 1);
        }
        return new int[]{cx, (this.gridSize - 1) - cy};
    }

    /** The mirror image of a cell for the current mirror mode (equal to the cell itself when it lies on the axis). */
    private int[] mirrorOf(int x, int y) {
        int n = this.gridSize - 1;
        return switch (this.mirrorMode) {
            case 1 -> new int[]{n - x, y};
            case 2 -> new int[]{x, n - y};
            case 3 -> new int[]{y, x};
            case 4 -> new int[]{n - y, n - x};
            default -> new int[]{x, y};
        };
    }

    /** Mirror image of a voxel cell as {x, y, layer}, or null when it is its own mirror image (or mirroring is off). */
    private int[] mirror3(int x, int y, int layer) {
        if (this.mirrorMode == 0) return null;
        if (this.mirrorMode == 5) return layer == 0 ? null : new int[]{x, y, -layer};
        int[] m = mirrorOf(x, y);
        return (m[0] == x && m[1] == y) ? null : new int[]{m[0], m[1], layer};
    }

    private List<int[]> withMirror(List<int[]> cells) {
        if (this.mirrorMode == 0) return cells;
        List<int[]> all = new ArrayList<>(cells);
        for (int[] c : cells) {
            int[] m = mirrorOf(c[0], c[1]);
            if (m[0] != c[0] || m[1] != c[1]) all.add(m);
        }
        return all;
    }

    private boolean press2d(int mouseX, int mouseY, int button) {
        if (button != 0 && button != 1) return false;
        int[] cell = cellAt(mouseX, mouseY, false);
        if (cell == null) return false;
        boolean erase = button == 1 || this.tool == TOOL_ERASE;

        switch (this.tool) {
            case TOOL_ADD, TOOL_RECOLOR, TOOL_ERASE -> {
                this.lastPx = cell[0];
                this.lastPy = cell[1];
                applyFootprint(List.of(cell), erase);
            }
            case TOOL_FILL -> fillLayerRegion(cell[0], cell[1], erase);
            case TOOL_SELECT -> {
                if (button == 1) clearSelection(); else beginSelect(cell[0], cell[1]);
            }
            case TOOL_MOVE -> {
                if (button == 0) beginMove(cell[0], cell[1]);
            }
            case TOOL_PICK -> {
                if (!erase) {
                    WeaponVoxel v = findVoxel(cell[0], cell[1], this.activeLayer);
                    if (v != null) selectSwatch(v.material(), v.shade());
                }
            }
            default -> { }
        }
        return true;
    }

    /** Bucket fill on the active layer: the connected area of identical cells around (x, y) is painted or erased. */
    private boolean fillLayerRegion(int x, int y, boolean erase) {
        final int layer = this.activeLayer;
        WeaponVoxel start = findVoxel(x, y, layer);
        if (erase && start == null) return true;
        if (!erase && start != null && start.materialId().equalsIgnoreCase(this.selectedMaterial.id()) && start.shade() == this.selectedShade) return true;
        String startKey = voxelState(start);
        java.util.Map<Long, String> states = new java.util.HashMap<>();
        for (WeaponVoxel v : this.workingVoxels) {
            if (v.z() == layer) states.put(((long) v.x() << 32) | v.y(), voxelState(v));
        }
        applyFootprint(EditorGeometry.flood(this.gridSize, x, y,
                (cx, cy) -> startKey.equals(states.getOrDefault(((long) cx << 32) | cy, ""))), erase);
        return true;
    }

    private static String voxelState(WeaponVoxel v) {
        return v == null ? "" : v.materialId().toLowerCase() + "#" + v.shade();
    }

    private boolean drag2d(int mouseX, int mouseY, int button) {
        if (button != 0 && button != 1) return false;
        if (this.tool == TOOL_SELECT || this.tool == TOOL_MOVE) {
            int[] cell = cellAt(mouseX, mouseY, true);
            if (button == 0 && cell != null) dragSelectOrMove(cell[0], cell[1]);
            return true;
        }
        if (this.tool == TOOL_ADD || this.tool == TOOL_RECOLOR || this.tool == TOOL_ERASE) {
            int[] cell = cellAt(mouseX, mouseY, false);
            boolean eraseStroke = button == 1 || this.tool == TOOL_ERASE;
            if (cell == null) {
                this.lastPx = -1;
                return true;
            }
            if (this.lastPx >= 0 && (cell[0] != this.lastPx || cell[1] != this.lastPy)) {
                applyFootprint(EditorGeometry.line(this.lastPx, this.lastPy, cell[0], cell[1]), eraseStroke);
            } else if (this.lastPx < 0) {
                applyFootprint(List.of(cell), eraseStroke);
            }
            this.lastPx = cell[0];
            this.lastPy = cell[1];
            return true;
        }
        return false;
    }

    private void finish2dStroke() {
        this.lastPx = this.lastPy = -1;
    }

    /** Paints or erases a set of cells on the active layer, honouring the mirror mode. */
    private boolean applyFootprint(List<int[]> cells, boolean erase) {
        boolean changed = applyFootprintOn(cells, erase);
        if (this.mirrorMode == 5 && this.activeLayer != 0) {
            // Depth mirror: the same edit lands on the opposite layer too
            int saved = this.activeLayer;
            this.activeLayer = -saved;
            try {
                changed |= applyFootprintOn(cells, erase);
            } finally {
                this.activeLayer = saved;
            }
        }
        return changed;
    }

    private boolean applyFootprintOn(List<int[]> cells, boolean erase) {
        List<int[]> all = withMirror(cells);
        boolean changed = false;

        if (erase) {
            for (int[] c : all) changed |= removeVoxel(c[0], c[1], this.activeLayer);
        } else if (this.tool == TOOL_RECOLOR) {
            // Recolor only: voxels that are already there change, nothing is added
            for (int[] c : all) {
                if (findVoxel(c[0], c[1], this.activeLayer) != null) {
                    changed |= putVoxel(c[0], c[1], this.activeLayer, this.selectedMaterial, this.selectedShade);
                }
            }
        } else {
            for (int[] c : all) changed |= putVoxel(c[0], c[1], this.activeLayer, this.selectedMaterial, this.selectedShade);
        }

        if (changed) {
            commitSnapshot();
            if (!erase) noteRecent(this.selectedMaterial, this.selectedShade);
            recalculateCachedStats();
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
            this.workingCounts.merge(mat.id().toLowerCase(), 1, Integer::sum);
            return true;
        }
        if (existing.materialId().equalsIgnoreCase(mat.id()) && existing.shade() == shade) return false;
        if (!existing.materialId().equalsIgnoreCase(mat.id()) && !isCreative && getAvailableCount(mat) <= 0) return false;
        workingVoxels.remove(existing);
        workingVoxels.add(new WeaponVoxel(x, y, layer, mat, shade));
        this.workingCounts.merge(existing.materialId().toLowerCase(), -1, Integer::sum);
        this.workingCounts.merge(mat.id().toLowerCase(), 1, Integer::sum);
        return true;
    }

    private boolean removeVoxel(int x, int y, int layer) {
        if (!WeaponVoxel.isValidLayer(layer) || isLayerLocked(layer)) return false;
        WeaponVoxel existing = findVoxel(x, y, layer);
        if (existing == null || workingVoxels.size() <= MIN_VOXELS) return false;
        workingVoxels.remove(existing);
        this.workingCounts.merge(existing.materialId().toLowerCase(), -1, Integer::sum);
        return true;
    }

    /** Places a voxel of the selected material, or recolors the one already there (3D editor). */
    private boolean paintAt(int x, int y, int layer) {
        boolean changed = putVoxel(x, y, layer, this.selectedMaterial, this.selectedShade);
        int[] m = mirror3(x, y, layer);
        if (m != null) changed |= putVoxel(m[0], m[1], m[2], this.selectedMaterial, this.selectedShade);
        if (!changed) return false;
        commitSnapshot();
        noteRecent(this.selectedMaterial, this.selectedShade);
        recalculateCachedStats();
        return true;
    }

    /** Recolors an existing voxel (and its mirror image, if that cell holds a voxel too); never adds new ones. */
    private boolean recolorAt(int x, int y, int layer) {
        boolean changed = putVoxel(x, y, layer, this.selectedMaterial, this.selectedShade);
        int[] m = mirror3(x, y, layer);
        if (m != null && findVoxel(m[0], m[1], m[2]) != null) {
            changed |= putVoxel(m[0], m[1], m[2], this.selectedMaterial, this.selectedShade);
        }
        if (!changed) return false;
        commitSnapshot();
        noteRecent(this.selectedMaterial, this.selectedShade);
        recalculateCachedStats();
        return true;
    }

    /** 3D bucket fill: every voxel face-connected to the one at (x, y, layer) with the same material and shade is recolored. */
    private boolean fillConnected(int x, int y, int layer) {
        WeaponVoxel start = findVoxel(x, y, layer);
        if (start == null) return false;
        if (start.materialId().equalsIgnoreCase(this.selectedMaterial.id()) && start.shade() == this.selectedShade) return false;
        String key = voxelState(start);
        java.util.Map<Long, WeaponVoxel> byCell = new java.util.HashMap<>();
        for (WeaponVoxel v : this.workingVoxels) byCell.put(cellKey(v.x(), v.y(), v.z()), v);

        List<WeaponVoxel> region = new ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<WeaponVoxel> queue = new java.util.ArrayDeque<>();
        queue.add(start);
        seen.add(cellKey(x, y, layer));
        int[][] nb = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            WeaponVoxel v = queue.poll();
            region.add(v);
            for (int[] d : nb) {
                long k = cellKey(v.x() + d[0], v.y() + d[1], v.z() + d[2]);
                if (seen.contains(k)) continue;
                WeaponVoxel w = byCell.get(k);
                if (w == null || !key.equals(voxelState(w))) continue;
                seen.add(k);
                queue.add(w);
            }
        }

        boolean changed = false;
        for (WeaponVoxel v : region) changed |= putVoxel(v.x(), v.y(), v.z(), this.selectedMaterial, this.selectedShade);
        if (!changed) return false;
        commitSnapshot();
        noteRecent(this.selectedMaterial, this.selectedShade);
        recalculateCachedStats();
        return true;
    }

    private static long cellKey(int x, int y, int z) {
        return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
    }

    private boolean eraseAt(int x, int y, int layer) {
        boolean changed = removeVoxel(x, y, layer);
        int[] m = mirror3(x, y, layer);
        if (m != null) changed |= removeVoxel(m[0], m[1], m[2]);
        if (!changed) return false;
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
            refreshSlots();
        }
    }

    /** Fills the backdrop texture: a soft radial glow, and in 2D a faint checkerboard showing the cells. */
    private void buildBackdrop() {
        final int n = BG_DIM;
        final boolean checker = !this.view3d;
        final int cells = Math.max(1, this.gridSize);
        for (int y = 0; y < n; y++) {
            double v = (y + 0.5) / n * 2 - 1;
            for (int x = 0; x < n; x++) {
                double u = (x + 0.5) / n * 2 - 1;
                double d = Math.min(1.0, Math.sqrt(u * u + v * v) / 1.4142);
                double k = 1.0 - Math.pow(d, 1.6); // 1 at the middle, 0 in the corners
                int lift = (checker && (((x * cells / n) + (y * cells / n)) & 1) == 1) ? 5 : 0;
                int r = mix((BACKDROP_EDGE >> 16) & 0xFF, (BACKDROP_CENTRE >> 16) & 0xFF, k) + lift;
                int g = mix((BACKDROP_EDGE >> 8) & 0xFF, (BACKDROP_CENTRE >> 8) & 0xFF, k) + lift;
                int b = mix(BACKDROP_EDGE & 0xFF, BACKDROP_CENTRE & 0xFF, k) + lift;
                this.bgBuffer[y * n + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
    }

    private static int mix(int from, int to, double t) {
        return (int) Math.round(from + (to - from) * t);
    }

    /** Draws the backdrop over the whole workspace, rebuilding it first when needed. */
    private void drawBackdrop(GuiGraphicsExtractor guiGraphics, int gridX, int gridY) {
        if (this.bgDirty) {
            buildBackdrop();
            for (int y = 0; y < BG_DIM; y++) {
                for (int x = 0; x < BG_DIM; x++) {
                    this.bgImage.setPixel(x, y, this.bgBuffer[y * BG_DIM + x]);
                }
            }
            this.bgTexture.upload();
            this.bgDirty = false;
        }
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, this.bgTextureId, gridX, gridY, 0.0F, 0.0F, WORKSPACE_DIM, WORKSPACE_DIM, BG_DIM, BG_DIM, BG_DIM, BG_DIM);
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

    private static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x - 1, y - 1, x + w + 1, y, color);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, color);
        g.fill(x - 1, y, x, y + h, color);
        g.fill(x + w, y, x + w + 1, y + h, color);
    }

    // ----- Layers panel (Photoshop style: top layer first, scrollable) -----

    private int layerListX(int leftPos) { return leftPos + LAYER_LIST_X; }

    private int layerListY(int topPos) { return topPos + LAYER_LIST_Y; }

    private int layerContentHeight() { return LAYER_COUNT * LAYER_ROW_H; }

    private int maxLayerScroll() { return Math.max(0, layerContentHeight() - LAYER_LIST_H); }

    private boolean layerListScrolls() { return maxLayerScroll() > 0; }

    private void clampLayerScroll() {
        this.layerScroll = Math.clamp(this.layerScroll, 0.0, (double) maxLayerScroll());
    }

    /** Scrolls the list just far enough that the layer being edited is fully visible. */
    private void revealActiveLayer() {
        int rowTop = (MAX_LAYER - this.activeLayer) * LAYER_ROW_H;
        if (rowTop < this.layerScroll) this.layerScroll = rowTop;
        else if (rowTop + LAYER_ROW_H > this.layerScroll + LAYER_LIST_H) this.layerScroll = rowTop + LAYER_ROW_H - LAYER_LIST_H;
        clampLayerScroll();
    }

    private int scrollerHeight() {
        return SCROLLER_H;
    }

    private int scrollerOffset() {
        int travel = LAYER_LIST_H - scrollerHeight();
        return maxLayerScroll() == 0 ? 0 : (int) Math.round(this.layerScroll / maxLayerScroll() * travel);
    }

    private boolean overLayerList(int leftPos, int topPos, int mouseX, int mouseY) {
        int x = layerListX(leftPos), y = layerListY(topPos);
        return mouseX >= x && mouseX < x + LAYER_LIST_W && mouseY >= y && mouseY < y + LAYER_LIST_H;
    }

    private void drawLayerPanel(GuiGraphicsExtractor g, int leftPos, int topPos, int mouseX, int mouseY) {
        if (this.thumbsDirty) rebuildThumbnails();
        clampLayerScroll();
        int lx = layerListX(leftPos), ly = layerListY(topPos);
        boolean scrolls = layerListScrolls();
        int rowW = LAYER_LIST_W - SCROLLBAR_W - 2;

        g.text(this.font, Component.literal("Layers"), lx, ly - 10, FONT_COLOR, false);
        g.fill(lx, ly, lx + rowW, ly + LAYER_LIST_H, 0xFF2B2B2B);

        List<String> tip = null;
        g.enableScissor(lx, ly, lx + rowW, ly + LAYER_LIST_H);
        for (int r = 0; r < LAYER_COUNT; r++) {
            int layer = MAX_LAYER - r;
            int i = layer + MAX_LAYER;
            int ry = ly + r * LAYER_ROW_H - (int) this.layerScroll;
            if (ry + LAYER_ROW_H <= ly || ry >= ly + LAYER_LIST_H) continue;

            boolean active = layer == this.activeLayer;
            boolean hidden = this.layerHidden[i];
            boolean rowHover = mouseX >= lx && mouseX < lx + rowW && mouseY >= Math.max(ry, ly) && mouseY < Math.min(ry + LAYER_ROW_H, ly + LAYER_LIST_H);
            g.fill(lx, ry, lx + rowW, ry + LAYER_ROW_H, active ? 0xFF4A6FA5 : (rowHover ? 0xFF444444 : 0xFF353535));
            g.fill(lx, ry + LAYER_ROW_H - 1, lx + rowW, ry + LAYER_ROW_H, 0xFF202020);

            // Visibility (green = shown) and lock (orange = locked)
            int eyeX = lx + 2, lockX = lx + 11, boxY = ry + (LAYER_ROW_H - 7) / 2;
            g.fill(eyeX, boxY, eyeX + 7, boxY + 7, hidden ? 0xFF555555 : 0xFF55CC55);
            g.fill(lockX, boxY, lockX + 7, boxY + 7, this.layerLocked[i] ? 0xFFFFAA00 : 0xFF8B8B8B);

            // Thumbnail
            int tx0 = lx + 21, ty0 = ry + (LAYER_ROW_H - THUMB) / 2;
            int[] px = this.thumbPixels[i];
            for (int ty = 0; ty < THUMB; ty++) {
                int tx = 0;
                while (tx < THUMB) {
                    int c = px[tx + ty * THUMB];
                    int run = tx + 1;
                    while (run < THUMB && px[run + ty * THUMB] == c) run++;
                    g.fill(tx0 + tx, ty0 + ty, tx0 + run, ty0 + ty + 1, c);
                    tx = run;
                }
            }
            if (hidden) g.fill(tx0, ty0, tx0 + THUMB, ty0 + THUMB, 0xBB1A1A1A);
            outline(g, tx0, ty0, THUMB, THUMB, 0xFF151515);

            String name = layer == 0 ? "Base" : String.format("%+d", layer);
            String fullName = layer == 0 ? "Base layer" : String.format("Layer %+d", layer);
            g.text(this.font, Component.literal(name), tx0 + THUMB + 4, ry + (LAYER_ROW_H - 8) / 2 + 1, hidden ? 0xFF8A8A8A : 0xFFFFFFFF, false);

            if (rowHover) {
                if (mouseX >= eyeX && mouseX < eyeX + 7) {
                    tip = List.of("§e" + (hidden ? "Show " : "Hide ") + fullName.toLowerCase(), "§7Hidden layers can't be edited");
                } else if (mouseX >= lockX && mouseX < lockX + 7) {
                    tip = List.of("§e" + (this.layerLocked[i] ? "Unlock " : "Lock ") + fullName.toLowerCase(), "§7Locked layers can't be changed");
                } else {
                    tip = List.of("§e" + fullName, "§7Click to edit this layer", "§7Scroll, or Page Up / Page Down, to move through layers");
                }
            }
        }
        g.disableScissor();
        outline(g, lx, ly, rowW, LAYER_LIST_H, 0xFF373737);

        // Scroll bar as in the creative inventory: an inset track with the creative tab's thumb
        int sx = lx + LAYER_LIST_W - SCROLLBAR_W;
        g.fill(sx - 1, ly - 1, sx + SCROLLBAR_W + 1, ly + LAYER_LIST_H + 1, 0xFFFFFFFF);
        g.fill(sx - 1, ly - 1, sx + SCROLLBAR_W, ly + LAYER_LIST_H, 0xFF373737);
        g.fill(sx, ly, sx + SCROLLBAR_W, ly + LAYER_LIST_H, 0xFF8B8B8B);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, scrolls ? SCROLLER_SPRITE : SCROLLER_DISABLED_SPRITE,
                sx, ly + (scrolls ? scrollerOffset() : 0), SCROLLBAR_W, SCROLLER_H);

        if (tip != null && !this.draggingScrollbar) {
            List<ClientTooltipComponent> lines = new ArrayList<>();
            for (String t : tip) lines.add(ClientTooltipComponent.create(Component.literal(t).getVisualOrderText()));
            realTooltip(g, lines, realMouseX, realMouseY);
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
                    ClientTooltipComponent.create(Component.literal("§eRecent: " + StringUtils.capitalize(hovered.material().id())).getVisualOrderText()),
                    ClientTooltipComponent.create(Component.literal("§7Shade " + (hovered.shade() + 1) + " - click to use").getVisualOrderText()));
            realTooltip(g, lines, realMouseX, realMouseY);
        }
    }

    private boolean handlePanelClick(int leftPos, int topPos, int mouseX, int mouseY) {
        int lx = layerListX(leftPos), ly = layerListY(topPos);
        boolean scrolls = layerListScrolls();
        int rowW = LAYER_LIST_W - SCROLLBAR_W - 2;

        // Scroll bar: drag the thumb, or click the track to page
        if (scrolls && mouseX >= lx + LAYER_LIST_W - SCROLLBAR_W && mouseX < lx + LAYER_LIST_W
                && mouseY >= ly && mouseY < ly + LAYER_LIST_H) {
            int thumbTop = ly + scrollerOffset();
            if (mouseY >= thumbTop && mouseY < thumbTop + scrollerHeight()) {
                this.draggingScrollbar = true;
                this.scrollGrab = mouseY - thumbTop;
            } else {
                this.layerScroll += mouseY < thumbTop ? -LAYER_LIST_H : LAYER_LIST_H;
                clampLayerScroll();
            }
            return true;
        }

        if (mouseX >= lx && mouseX < lx + rowW && mouseY >= ly && mouseY < ly + LAYER_LIST_H) {
            int r = (int) Math.floor((mouseY - ly + this.layerScroll) / LAYER_ROW_H);
            if (r >= 0 && r < LAYER_COUNT) {
                int layer = MAX_LAYER - r;
                int i = layer + MAX_LAYER;
                if (mouseX >= lx + 2 && mouseX < lx + 9) {
                    this.layerHidden[i] = !this.layerHidden[i];
                    this.sceneDirty = true;
                } else if (mouseX >= lx + 11 && mouseX < lx + 18) {
                    this.layerLocked[i] = !this.layerLocked[i];
                } else {
                    this.activeLayer = layer;
                    this.sceneDirty = true;
                    revealActiveLayer();
                }
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

    private void dragScrollbar(int mouseY) {
        int topPos = (vh() - WINDOW_HEIGHT) / 2;
        int travel = LAYER_LIST_H - scrollerHeight();
        if (travel <= 0) return;
        double frac = (mouseY - this.scrollGrab - layerListY(topPos)) / (double) travel;
        this.layerScroll = frac * maxLayerScroll();
        clampLayerScroll();
    }

    // ----- 3D editor -----

    /** Render pixels per GUI pixel to use so the 3D view is sharp on screen at the player's GUI scale (1..3). */
    private int desiredRenderScale() {
        var window = this.minecraft.getWindow();
        int guiScaledWidth = Math.max(1, window.getGuiScaledWidth());
        // Real pixels per layout pixel: the GUI scale, reduced when the layout is scaled down to fit
        return Math.max(1, Math.min(3, (int) Math.round(window.getWidth() / (double) guiScaledWidth * this.fit)));
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
        this.bgBuffer = new int[BG_DIM * BG_DIM];
        this.sceneGrid = new int[this.raycaster.cellCount()];
        this.lastHlRect = null;
        this.bgDirty = true;
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

            this.bgImage = new NativeImage(BG_DIM, BG_DIM, true);
            this.bgTexture = new DynamicTexture(() -> "blocksmith_forge_bg", this.bgImage);
            this.bgTextureId = Identifier.fromNamespaceAndPath("blocksmith", "forge_bg_" + System.identityHashCode(this));
            this.minecraft.getTextureManager().register(this.bgTextureId, this.bgTexture);

            this.sceneDirty = true;
            this.bgDirty = true;
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

    /** Brings the renderer's grid in line with the design; only cells that differ mark their chunk for redraw. */
    private void syncScene() {
        java.util.Arrays.fill(this.sceneGrid, 0);
        for (WeaponVoxel v : workingVoxels) {
            if (this.layerHidden[v.z() + MAX_LAYER]) continue;
            this.sceneGrid[this.raycaster.cellIndex(v.x(), v.y(), v.z() + MAX_LAYER)] = v.getColorRgb() | 0xFF000000;
        }
        this.raycaster.syncCells(this.sceneGrid);
    }

    private static int[] unionRect(int[] a, int[] b) {
        if (a == null || b == null) return null;
        if (a[2] < a[0]) return b;
        if (b[2] < b[0]) return a;
        return new int[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[2], b[2]), Math.max(a[3], b[3])};
    }

    private void render3dWorkspace(GuiGraphicsExtractor guiGraphics, int gridX, int gridY, int mouseX, int mouseY) {
        ensureViewTexture();
        final int scale = this.renderScale;
        final int n = WORKSPACE_DIM * scale;

        int px = mouseX - gridX;
        int py = mouseY - gridY;
        boolean inside = px >= 0 && py >= 0 && px < WORKSPACE_DIM && py < WORKSPACE_DIM;
        int hoverKey = (inside && !this.orbiting && !this.panning) ? (px << 16) | py : -1;

        // Area of the model picture that changed this frame: {x0,y0,x1,y1}, empty when x1<x0, null = everything
        int[] changed = new int[]{0, 0, -1, -1};
        boolean baseChanged = false;

        // Heavy pass: only when the model or camera changed. Edits redraw just the chunks they touched; if a full
        // pass is slow, a coarser preview is drawn until the mouse is released.
        if (this.sceneDirty) {
            syncScene();
            int step = ((this.orbiting || this.panning) && this.fullRenderMs > 50.0) ? 2 : 1;
            long renderStart = System.nanoTime();
            int[] rect = this.raycaster.render(this.fgBase, null, step);
            double passMs = (System.nanoTime() - renderStart) / 1.0e6;
            if (rect == null || step > 1) {
                double fullMs = step == 1 ? passMs : passMs * 3.5;
                this.fullRenderMs = this.fullRenderMs == 0 ? fullMs : this.fullRenderMs * 0.6 + fullMs * 0.4;
            }
            this.sceneDirty = false;
            changed = rect;
            baseChanged = rect == null || rect[2] >= rect[0];
        }

        // Light pass: re-trace just the hovered voxel when the cursor moves
        if (baseChanged || hoverKey != this.lastHoverKey) {
            this.hoverHit = hoverKey >= 0 ? this.raycaster.pick(px * scale + scale / 2, py * scale + scale / 2) : null;
            int[] hl = this.raycaster.renderWithHighlight(this.fgBase, this.fgView, this.hoverHit);
            changed = unionRect(changed, hl);
            changed = unionRect(changed, this.lastHlRect);
            this.lastHlRect = hl;
            this.lastHoverKey = hoverKey;
        }

        if (changed == null || changed[2] >= changed[0]) {
            int x0 = 0, y0 = 0, x1 = n - 1, y1 = n - 1;
            if (changed != null) {
                x0 = Math.max(0, changed[0]); y0 = Math.max(0, changed[1]);
                x1 = Math.min(n - 1, changed[2]); y1 = Math.min(n - 1, changed[3]);
            }
            for (int y = y0; y <= y1; y++) {
                int row = y * n;
                for (int x = x0; x <= x1; x++) {
                    this.viewImage.setPixel(x, y, this.fgView[row + x]);
                }
            }
            this.viewTexture.upload();
        }

        drawBackdrop(guiGraphics, gridX, gridY);
        // The model layer is rendered at native resolution (scale x the GUI size) and drawn scaled down 1:scale
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, this.viewTextureId, gridX, gridY, 0.0F, 0.0F, WORKSPACE_DIM, WORKSPACE_DIM, n, n, n, n);
        drawSelection3d(guiGraphics, gridX, gridY);

        // Control hint: a small pill that shows for a few seconds, fades away, and returns while the cursor is over it
        String hint = "Right-drag orbit  |  Mid-drag pan  |  Wheel zoom";
        int hintW = this.font.width(hint);
        int hintX = gridX + 4, hintY = gridY + WORKSPACE_DIM - 14;
        long nowNs = System.nanoTime();
        if (this.hintUntil == 0) this.hintUntil = nowNs + 6_000_000_000L;
        if (mouseX >= hintX - 3 && mouseX <= hintX + hintW + 3 && mouseY >= hintY - 2 && mouseY <= hintY + 10) {
            this.hintUntil = Math.max(this.hintUntil, nowNs + 1_500_000_000L);
        }
        double hintAlpha = Math.max(0.0, Math.min(1.0, (this.hintUntil - nowNs) / 1.0e9));
        if (hintAlpha > 0.02) {
            int a8 = (int) Math.round(hintAlpha * 255);
            guiGraphics.fill(hintX - 3, hintY - 2, hintX + hintW + 3, hintY + 10, ((int) Math.round(hintAlpha * 0x90) << 24) | 0x0E131C);
            guiGraphics.text(this.font, Component.literal(hint), hintX, hintY, (a8 << 24) | 0xE6EAF2, false);
        }
    }

    private boolean handle3dInteraction(int mouseX, int mouseY, int button, boolean drag) {
        if (!isInsideWorkspace(mouseX, mouseY)) return false;
        ensureRaycaster();

        int gridX = (vw() - WINDOW_WIDTH) / 2 + WORKSPACE_OFFSET_X;
        int gridY = (vh() - WINDOW_HEIGHT) / 2 + WORKSPACE_OFFSET_Y;
        // GUI pixel -> centre of the matching block of render pixels
        final int scale = this.renderScale;
        int px = (mouseX - gridX) * scale + scale / 2;
        int py = (mouseY - gridY) * scale + scale / 2;

        if (button != 0) return false; // right-drag orbits and middle-drag pans, handled in mouseClicked/Dragged

        if (this.tool == TOOL_SELECT || this.tool == TOOL_MOVE) {
            // Both work on the plane of one layer: the selected layer for a new selection, the selection's own layer for a move
            int planeLayer = (this.tool == TOOL_MOVE && this.selActive) ? this.selLayer : this.activeLayer;
            int[] c = this.raycaster.pickOnPlane(px, py, 2, planeLayer + MAX_LAYER);
            if (c == null) return true;
            if (!drag) {
                if (this.tool == TOOL_SELECT) beginSelect(c[0], c[1]); else beginMove(c[0], c[1]);
            } else {
                dragSelectOrMove(c[0], c[1]);
            }
            return true;
        }

        if (this.tool == TOOL_ERASE) {
            VoxelRaycaster.Hit hit = this.raycaster.pick(px, py);
            if (hit == null || hit.floor()) return false;
            return eraseAt(hit.x(), hit.y(), hit.zi() - MAX_LAYER);
        }

        if (this.tool == TOOL_RECOLOR) {
            VoxelRaycaster.Hit hit = this.raycaster.pick(px, py);
            if (hit == null || hit.floor()) return false;
            return recolorAt(hit.x(), hit.y(), hit.zi() - MAX_LAYER);
        }

        if (drag && (this.tool == TOOL_PICK || this.tool == TOOL_FILL)) return false; // these act once per click

        if (this.tool == TOOL_PICK) {
            VoxelRaycaster.Hit hit = this.raycaster.pick(px, py);
            if (hit == null || hit.floor()) return false;
            WeaponVoxel v = findVoxel(hit.x(), hit.y(), hit.zi() - MAX_LAYER);
            if (v == null) return false;
            selectSwatch(v.material(), v.shade());
            return true;
        }

        if (this.tool == TOOL_FILL) {
            VoxelRaycaster.Hit hit = this.raycaster.pick(px, py);
            if (hit != null && !hit.floor()) return fillConnected(hit.x(), hit.y(), hit.zi() - MAX_LAYER);
            // Empty space: fill the selected layer instead, at the cell where the ray crosses that layer
            int[] c = this.raycaster.pickOnPlane(px, py, 2, this.activeLayer + MAX_LAYER);
            if (c == null) return false;
            return fillLayerRegion(c[0], c[1], false);
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

        String key = mat.id().toLowerCase();
        int onOriginalWeapon = this.originalCounts.getOrDefault(key, 0);
        int placedInWorking = this.workingCounts.getOrDefault(key, 0);

        return inInventory + onOriginalWeapon - placedInWorking;
    }

    /** Refreshes the material slots' icons and counts (cheap; used when only the visible materials change). */
    private void refreshSlots() {
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
        this.cachedMissing = computeMissingMaterials();
    }

    private boolean computeMissingMaterials() {
        if (this.minecraft.player == null) return false;
        if (this.minecraft.player.getAbilities().instabuild) return false;

        for (VoxelMaterial mat : VoxelMaterialRegistry.getAll()) {
            if (getAvailableCount(mat) < 0) {
                return true;
            }
        }
        return false;
    }

    /** Cached: the answer only changes when the design or the inventory does, and it is asked for every frame. */
    private boolean hasMissingMaterials() {
        long now = System.nanoTime();
        if (now >= this.nextMissingCheck) {
            this.cachedMissing = computeMissingMaterials();
            this.nextMissingCheck = now + 400_000_000L;
        }
        return this.cachedMissing;
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

    /** The design changed: redraw the 3D view and layer thumbnails and refresh every cached figure. */
    private void recalculateCachedStats() {
        this.sceneDirty = true;
        this.thumbsDirty = true;
        double gridArea = this.gridSize * this.gridSize;

        List<WeaponVoxel> savedVoxels = ModularSwordItem.getVoxels(weaponStack);
        this.savedVoxelCount = savedVoxels.size();
        this.originalCounts.clear();
        this.workingCounts.clear();
        double savedRawDmg = 0, savedRawSpd = 0, savedRawDur = 0;
        for (WeaponVoxel v : savedVoxels) {
            this.originalCounts.merge(v.materialId().toLowerCase(), 1, Integer::sum);
            VoxelMaterial m = v.material();
            savedRawDmg += m.getBonusDamage();
            savedRawSpd += m.getBonusSpeed();
            savedRawDur += m.getBonusDurability();
        }

        double workingRawDmg = 0, workingRawSpd = 0, workingRawDur = 0;
        for (WeaponVoxel v : workingVoxels) {
            this.workingCounts.merge(v.materialId().toLowerCase(), 1, Integer::sum);
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

        refreshSlots();

        this.cachedAbilities.clear();
        if (!net.adeptfrog.blocksmith.data.VoxelMaterialRegistry.areAbilitiesDisabled()) {
            boolean bow = weaponStack.getItem() instanceof ModularBowItem;
            for (WeaponAbility ability : WeaponAbilityRegistry.getAll()) {
                if (ability.isUnlockedFor(bow, workingVoxels)) cachedAbilities.add(ability);
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
        if (row == BTN_RESET) return this.view3d;
        return true;
    }

    private String[] toolTooltip(int i) {
        String key = "§8Hotkey: " + TOOL_KEYS[i];
        if (this.view3d) {
            return switch (i) {
                case TOOL_ADD -> new String[]{"§eAdd", "§7Place a voxel on the face you point at",
                        "§7Right-drag orbits, middle-drag pans, wheel zooms", key};
                case TOOL_RECOLOR -> new String[]{"§eRecolor", "§7Repaint the voxel you point at", key};
                case TOOL_ERASE -> new String[]{"§eErase", "§7Remove the voxel you point at", "§cErased materials are destroyed!", key};
                case TOOL_PICK -> new String[]{"§eEyedropper", "§7Pick the material and shade of the voxel you point at", key};
                case TOOL_SELECT -> new String[]{"§eSelect", "§7Drag a rectangle on the selected layer",
                        "§7Ctrl+C copy, Ctrl+V paste at the cursor", "§7Ctrl+A select layer, Ctrl+D deselect, Del erase", key};
                case TOOL_MOVE -> new String[]{"§eMove", "§7Drag the selected voxels to a new spot",
                        "§7Arrow keys nudge by one cell", key};
                default -> new String[]{"§eBucket fill", "§7On a voxel: recolor everything connected to it that matches",
                        "§7On empty space: fill the selected layer", key};
            };
        }
        return switch (i) {
            case TOOL_ADD -> new String[]{"§ePencil", "§7Freehand, drags join up smoothly", "§7Right-click erases", key};
            case TOOL_RECOLOR -> new String[]{"§eRecolor", "§7Repaint existing voxels only, never adds new ones", key};
            case TOOL_ERASE -> new String[]{"§eErase", "§7Remove voxels freehand", "§cErased materials are destroyed!", key};
            case TOOL_PICK -> new String[]{"§eEyedropper", "§7Pick the material and shade", key};
            case TOOL_SELECT -> new String[]{"§eSelect", "§7Drag a rectangle on the selected layer", "§7Right-click deselects",
                    "§7Ctrl+C copy, Ctrl+V paste at the cursor", "§7Ctrl+A select layer, Del erase", key};
            case TOOL_MOVE -> new String[]{"§eMove", "§7Drag the selected voxels to a new spot", "§7Arrow keys nudge by one cell", key};
            default -> new String[]{"§eBucket fill", "§7Fill a connected area", "§7Right-click erases it", key};
        };
    }

    private boolean toolActive(int i) {
        return i == this.tool;
    }

    private ItemStack buttonIcon(int row) {
        if (row < BTN_TOOL_SLOTS) return ICON_TOOLS[row];
        return switch (row) {
            case BTN_FLIP_H -> ICON_FLIP_H;
            case BTN_FLIP_V -> ICON_FLIP_V;
            case BTN_ROT_CW -> ICON_ROT_CW;
            case BTN_ROT_CCW -> ICON_ROT_CCW;
            case BTN_VIEW -> this.view3d ? ICON_VIEW_3D : ICON_VIEW_2D;
            case BTN_UNDO, BTN_REDO -> ICON_UNDO;
            case BTN_MIRROR -> ICON_MIRROR;
            case BTN_RESET -> ICON_RESET;
            default -> ICON_CODEX;
        };
    }

    /** Hotkey letter drawn in the corner of a button, or null. */
    private String buttonKey(int row) {
        if (row < BTN_TOOL_SLOTS) return TOOL_KEYS[row];
        return switch (row) {
            case BTN_MIRROR -> "M";
            default -> null;
        };
    }

    private String buttonOverlay(int row) {
        return switch (row) {
            case BTN_UNDO -> "<";
            case BTN_REDO -> ">";
            case BTN_MIRROR -> this.mirrorMode == 0 ? null : MIRROR_TAGS[this.mirrorMode];
            default -> null;
        };
    }

    private String[] buttonTooltip(int row) {
        if (row < BTN_TOOL_SLOTS) return toolTooltip(row);
        return switch (row) {
            case BTN_FLIP_H -> new String[]{"§eFlip horizontally"};
            case BTN_FLIP_V -> new String[]{"§eFlip vertically"};
            case BTN_ROT_CW -> new String[]{"§eRotate clockwise"};
            case BTN_ROT_CCW -> new String[]{"§eRotate counter-clockwise"};
            case BTN_VIEW -> this.view3d
                    ? new String[]{"§eView: 3D", "§7Click to switch to the 2D layer editor", "§8Hotkey: Tab"}
                    : new String[]{"§eView: 2D", "§7Click to switch to the 3D editor", "§8Hotkey: Tab"};
            case BTN_UNDO -> new String[]{"§eUndo", "§7Ctrl+Z" + (this.undoStack.isEmpty() ? " §8(nothing to undo)" : "")};
            case BTN_REDO -> new String[]{"§eRedo", "§7Ctrl+Y or Ctrl+Shift+Z" + (this.redoStack.isEmpty() ? " §8(nothing to redo)" : "")};
            case BTN_MIRROR -> new String[]{"§eMirror: " + (this.mirrorMode == 0 ? "§cOff" : "§a" + MIRROR_NAMES[this.mirrorMode]) + " §7(click to cycle)",
                    "§7Off / Horizontal / Vertical / Diagonal / and \\ / Depth",
                    "§7Depth copies each edit onto the opposite layer (layer -n for layer n)",
                    this.view3d ? "§7Other modes keep the copy on the same layer as the original" : "§7Draws on both sides of the axis",
                    "§8Hotkey: M"};
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

    private void openExport() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        WeaponOffset currentOffset = new WeaponOffset(this.workingOffsetX, this.workingOffsetY);
        this.minecraft.setScreen(new ExportDesignScreen(this, new DesignEntry(
                this.recentFileName, false, this.workingVoxels, currentOffset,
                this.recentAttribution, this.recentCrafter), this.gridSize));
    }

    private void openImport() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        this.minecraft.setScreen(new DesignLibraryScreen(this));
    }

    private void handleButtonAction(int row) {
        int n = this.gridSize - 1;
        if (row < BTN_TOOL_SLOTS) {
            this.tool = row;
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return;
        }
        switch (row) {
            case BTN_FLIP_H -> transformVoxels(v -> new WeaponVoxel(n - v.x(), v.y(), v.z(), v.materialId(), v.shade()));
            case BTN_FLIP_V -> transformVoxels(v -> new WeaponVoxel(v.x(), n - v.y(), v.z(), v.materialId(), v.shade()));
            case BTN_ROT_CW -> transformVoxels(v -> new WeaponVoxel(v.y(), n - v.x(), v.z(), v.materialId(), v.shade()));
            case BTN_ROT_CCW -> transformVoxels(v -> new WeaponVoxel(n - v.y(), v.x(), v.z(), v.materialId(), v.shade()));
            case BTN_VIEW -> {
                this.view3d = !this.view3d;
                this.bgDirty = true;
                this.orbiting = false;
                this.panning = false;
                this.strokeAxis = -1;
                this.sceneDirty = true;
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            case BTN_UNDO -> undo();
            case BTN_REDO -> redo();
            case BTN_MIRROR -> {
                this.mirrorMode = (this.mirrorMode + 1) % MIRROR_NAMES.length;
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