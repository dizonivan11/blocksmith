package net.adeptfrog.blocksmith.client.gui;

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
import org.jspecify.annotations.NonNull;
import net.adeptfrog.blocksmith.data.DesignFileManager.DesignEntry;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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

    // 3D editor
    private static final int TOOL_ADD = 0, TOOL_RECOLOR = 1, TOOL_ORBIT = 2;
    private static final String[] TOOL_NAMES = {"Add", "Recolor", "Orbit"};
    private static final int VIEW_BTN_Y = 177, TOOL_BTN_Y = 196, RESET_BTN_Y = 215; // relative to the window top
    private boolean view3d = true;
    private int tool3d = TOOL_ADD;
    private boolean orbiting = false;
    private int strokeAxis = -1;   // axis of the plane a paint-drag stays on, -1 when no stroke is active
    private int strokeIndex = 0;
    private boolean sceneDirty = true;
    private int lastHoverKey = -2;
    private VoxelRaycaster.Hit hoverHit = null;
    private VoxelRaycaster raycaster;
    private int[] pixelBuffer;   // composited (opaque) pixels uploaded to the texture
    private int[] fgBase;        // ray-cast model without hover highlight
    private int[] fgView;        // fgBase plus hover highlight
    private final long animStartNanos = System.nanoTime();
    private DynamicTexture viewTexture;
    private NativeImage viewImage;
    private Identifier viewTextureId;

    private int workingOffsetX;
    private int workingOffsetY;
    private String recentFileName = "";
    private String recentCrafter = "";
    private String recentAttribution = "";

    private static final int FONT_COLOR = 0xFF3F3F3F;

    // Container Window Dimensions
    private static final int WINDOW_WIDTH = 317;
    private static final int WINDOW_HEIGHT = 253;

    // Workspace Allocated Area
    private static final int WORKSPACE_OFFSET_X = 12;
    private static final int WORKSPACE_OFFSET_Y = 12;
    private static final int WORKSPACE_DIM = 192;

    // Bottom Slot Configuration
    private static final int VISIBLE_SLOTS = 8;
    private static final int SLOT_SIZE = 16;
    private static final int MAT_SLOT_SPACING = 18;
    private static final int SHADE_SLOT_SPACING = 18;

    private static final int MAT_START_X = 8;
    private static final int SHADE_START_X = 164;
    private static final int SLOTS_Y = 229;

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
                if (v.z() == this.activeLayer) continue;
                if (v.x() < 0 || v.x() >= this.gridSize || v.y() < 0 || v.y() >= this.gridSize) continue;
                int gx = gridX + v.x() * this.cellSize;
                int gy = gridY + (this.gridSize - 1 - v.y()) * this.cellSize;
                guiGraphics.fill(gx, gy, gx + this.cellSize, gy + this.cellSize, (v.getColorRgb() & 0xFFFFFF) | 0x55000000);
            }

            for (WeaponVoxel v : workingVoxels) {
                if (v.z() != this.activeLayer) continue;
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
                if (voxel.z() != this.activeLayer) continue;
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
        }

        // Stats panel
        int statsX = leftPos + 212;
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

        String leftLabel = this.view3d ? "[Left] " + TOOL_NAMES[this.tool3d] : "[Left] Paint";
        guiGraphics.text(this.font, Component.literal(leftLabel), statsX + 6, statsY + 104, FONT_COLOR, false);

        Component eraseText = Component.literal("[Right] Erase ");
        guiGraphics.text(this.font, eraseText, statsX + 6, statsY + 114, FONT_COLOR, false);

        // Erase warning icon
        int infoX = statsX + 6 + this.font.width(eraseText);
        int infoY = statsY + 114;
        Component infoIcon = Component.literal("§6(!)");
        guiGraphics.text(this.font, infoIcon, infoX, infoY, 0xFFFFFFFF, false);

        // Hover detection for Erase warning icon
        int infoW = this.font.width(infoIcon);
        if (mouseX >= infoX - 1 && mouseX <= infoX + infoW + 1 && mouseY >= infoY - 1 && mouseY <= infoY + 9) {
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

        // Side buttons
        int subBtnW = 45;
        int subBtnH = 16;
        int subBtnX = leftPos - subBtnW - 4;

        // Copy and Paste buttons
        int exportBtnY = topPos + 1;
        int importBtnY = exportBtnY + subBtnH + 4;

        boolean exportHovered = mouseX >= subBtnX && mouseX <= subBtnX + subBtnW && mouseY >= exportBtnY && mouseY <= exportBtnY + subBtnH;
        boolean importHovered = mouseX >= subBtnX && mouseX <= subBtnX + subBtnW && mouseY >= importBtnY && mouseY <= importBtnY + subBtnH;

        String exportLabel = exportFeedbackTimer > 0 ? "§aCopied!" : "Export";
        String importLabel = importFeedbackTimer > 0 ? "§aLoaded!" : (importFeedbackTimer < 0 ? "§cInvalid!" : "Import");

        drawSmallHeaderButton(guiGraphics, subBtnX, exportBtnY, subBtnW, subBtnH, exportLabel, exportHovered);
        drawSmallHeaderButton(guiGraphics, subBtnX, importBtnY, subBtnW, subBtnH, importLabel, importHovered);

        // Flip buttons
        int flipHBtnY = importBtnY + subBtnH + 4;
        int flipVBtnY = flipHBtnY + subBtnH + 4;

        boolean flipHHovered = mouseX >= subBtnX && mouseX <= subBtnX + subBtnW && mouseY >= flipHBtnY && mouseY <= flipHBtnY + subBtnH;
        boolean flipVHovered = mouseX >= subBtnX && mouseX <= subBtnX + subBtnW && mouseY >= flipVBtnY && mouseY <= flipVBtnY + subBtnH;

        drawSmallHeaderButton(guiGraphics, subBtnX, flipHBtnY, subBtnW, subBtnH, "Flip H", flipHHovered);
        drawSmallHeaderButton(guiGraphics, subBtnX, flipVBtnY, subBtnW, subBtnH, "Flip V", flipVHovered);

        // Rotate buttons
        int rotCWBtnY = flipVBtnY + subBtnH + 4;
        int rotCCWBtnY = rotCWBtnY + subBtnH + 4;

        boolean rotCWHovered = mouseX >= subBtnX && mouseX <= subBtnX + subBtnW && mouseY >= rotCWBtnY && mouseY <= rotCWBtnY + subBtnH;
        boolean rotCCWHovered = mouseX >= subBtnX && mouseX <= subBtnX + subBtnW && mouseY >= rotCCWBtnY && mouseY <= rotCCWBtnY + subBtnH;

        drawSmallHeaderButton(guiGraphics, subBtnX, rotCWBtnY, subBtnW, subBtnH, "Rot CW", rotCWHovered);
        drawSmallHeaderButton(guiGraphics, subBtnX, rotCCWBtnY, subBtnW, subBtnH, "Rot CCW", rotCCWHovered);

        // Layer buttons (2D mode) / 3D controls
        int layerUpBtnY = rotCCWBtnY + subBtnH + 4;
        int layerDownBtnY = layerUpBtnY + subBtnH + 4;

        if (!this.view3d) {
            boolean layerUpHovered = mouseX >= subBtnX && mouseX <= subBtnX + subBtnW && mouseY >= layerUpBtnY && mouseY <= layerUpBtnY + subBtnH;
            boolean layerDownHovered = mouseX >= subBtnX && mouseX <= subBtnX + subBtnW && mouseY >= layerDownBtnY && mouseY <= layerDownBtnY + subBtnH;

            drawSmallHeaderButton(guiGraphics, subBtnX, layerUpBtnY, subBtnW, subBtnH, "Layer +", layerUpHovered);
            drawSmallHeaderButton(guiGraphics, subBtnX, layerDownBtnY, subBtnW, subBtnH, "Layer -", layerDownHovered);

            String layerText = String.format("Layer %+d", this.activeLayer).replace("+0", "0");
            int layerTextX = subBtnX + (subBtnW - this.font.width(layerText)) / 2; // centred under the buttons
            guiGraphics.text(this.font, Component.literal(layerText), layerTextX, layerDownBtnY + subBtnH + 5, 0xFFFFFFFF, true);
        }

        int viewBtnY = topPos + VIEW_BTN_Y;
        int toolBtnY = topPos + TOOL_BTN_Y;
        int resetBtnY = topPos + RESET_BTN_Y;
        boolean viewHovered = mouseX >= subBtnX && mouseX <= subBtnX + subBtnW && mouseY >= viewBtnY && mouseY <= viewBtnY + subBtnH;
        drawSmallHeaderButton(guiGraphics, subBtnX, viewBtnY, subBtnW, subBtnH, this.view3d ? "View: 3D" : "View: 2D", viewHovered);

        if (this.view3d) {
            boolean toolHovered = mouseX >= subBtnX && mouseX <= subBtnX + subBtnW && mouseY >= toolBtnY && mouseY <= toolBtnY + subBtnH;
            boolean resetHovered = mouseX >= subBtnX && mouseX <= subBtnX + subBtnW && mouseY >= resetBtnY && mouseY <= resetBtnY + subBtnH;
            drawSmallHeaderButton(guiGraphics, subBtnX, toolBtnY, subBtnW, subBtnH, TOOL_NAMES[this.tool3d], toolHovered);
            drawSmallHeaderButton(guiGraphics, subBtnX, resetBtnY, subBtnW, subBtnH, "Reset", resetHovered);

            if (toolHovered) {
                List<ClientTooltipComponent> toolTooltip = List.of(
                        ClientTooltipComponent.create(Component.literal("§eLeft-click tool (click to cycle)").getVisualOrderText()),
                        ClientTooltipComponent.create(Component.literal("§7Add: place a voxel on the face you point at").getVisualOrderText()),
                        ClientTooltipComponent.create(Component.literal("§7Recolor: repaint the voxel you point at").getVisualOrderText()),
                        ClientTooltipComponent.create(Component.literal("§7Orbit: drag to rotate the view").getVisualOrderText()),
                        ClientTooltipComponent.create(Component.literal("§7Right-click erases, middle-drag orbits,").getVisualOrderText()),
                        ClientTooltipComponent.create(Component.literal("§7scroll wheel zooms.").getVisualOrderText())
                );
                guiGraphics.tooltip(this.font, toolTooltip, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
            }
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
        int arrowY = topPos + 216;
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

        // Ability Codex book button
        int bookBtnY = topPos + WINDOW_HEIGHT - subBtnH - 1;

        boolean bookHovered = mouseX >= subBtnX && mouseX <= subBtnX + subBtnW && mouseY >= bookBtnY && mouseY <= bookBtnY + subBtnH;
        drawSmallHeaderButton(guiGraphics, subBtnX, bookBtnY, subBtnW, subBtnH, "Codex", bookHovered);

        if (bookHovered) {
            List<ClientTooltipComponent> bookTooltip = List.of(
                    ClientTooltipComponent.create(Component.literal("§eAbility Codex (Guide)").getVisualOrderText())
            );
            guiGraphics.tooltip(this.font, bookTooltip, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int leftPos = (this.width - WINDOW_WIDTH) / 2;
        int topPos = (this.height - WINDOW_HEIGHT) / 2;
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();

        int statsX = leftPos + 212;
        int statsY = topPos + 35;

        // Side buttons
        int subBtnW = 45;
        int subBtnH = 16;
        int subBtnX = leftPos - subBtnW - 4;

        // Export and Import button click
        int exportBtnY = topPos + 1;
        int importBtnY = exportBtnY + subBtnH + 4;

        if (mouseX >= subBtnX && mouseX <= subBtnX + subBtnW && mouseY >= exportBtnY && mouseY <= exportBtnY + subBtnH) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            WeaponOffset currentOffset = new WeaponOffset(this.workingOffsetX, this.workingOffsetY);
            this.minecraft.setScreen(new ExportDesignScreen(this, new DesignEntry(
                    this.recentFileName,
                    false,
                    this.workingVoxels,
                    currentOffset,
                    this.recentAttribution,
                    this.recentCrafter), this.gridSize));
            return true;
        }

        if (mouseX >= subBtnX && mouseX <= subBtnX + subBtnW && mouseY >= importBtnY && mouseY <= importBtnY + subBtnH) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.minecraft.setScreen(new DesignLibraryScreen(this));
            return true;
        }

        // Flip buttons click
        int flipHBtnY = importBtnY + subBtnH + 4;
        int flipVBtnY = flipHBtnY + subBtnH + 4;

        if (mouseX >= subBtnX && mouseX <= subBtnX + subBtnW && mouseY >= flipHBtnY && mouseY <= flipHBtnY + subBtnH) {
            if (!workingVoxels.isEmpty()) {
                List<WeaponVoxel> flipped = new ArrayList<>();
                for (WeaponVoxel v : workingVoxels) {
                    int newX = (this.gridSize - 1) - v.x();
                    flipped.add(new WeaponVoxel(newX, v.y(), v.z(), v.materialId(), v.shade()));
                }
                this.workingVoxels.clear();
                this.workingVoxels.addAll(flipped);
                recalculateCachedStats();

                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        if (mouseX >= subBtnX && mouseX <= subBtnX + subBtnW && mouseY >= flipVBtnY && mouseY <= flipVBtnY + subBtnH) {
            if (!workingVoxels.isEmpty()) {
                List<WeaponVoxel> flipped = new ArrayList<>();
                for (WeaponVoxel v : workingVoxels) {
                    int newY = (this.gridSize - 1) - v.y();
                    flipped.add(new WeaponVoxel(v.x(), newY, v.z(), v.materialId(), v.shade()));
                }
                this.workingVoxels.clear();
                this.workingVoxels.addAll(flipped);
                recalculateCachedStats();

                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        // Rotate buttons click
        int rotCWBtnY = flipVBtnY + subBtnH + 4;
        int rotCCWBtnY = rotCWBtnY + subBtnH + 4;

        if (mouseX >= subBtnX && mouseX <= subBtnX + subBtnW && mouseY >= rotCWBtnY && mouseY <= rotCWBtnY + subBtnH) {
            if (!workingVoxels.isEmpty()) {
                List<WeaponVoxel> rotated = new ArrayList<>();
                for (WeaponVoxel v : workingVoxels) {
                    int newX = v.y();
                    int newY = (this.gridSize - 1) - v.x();
                    rotated.add(new WeaponVoxel(newX, newY, v.z(), v.materialId(), v.shade()));
                }
                this.workingVoxels.clear();
                this.workingVoxels.addAll(rotated);

                recalculateCachedStats();
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        if (mouseX >= subBtnX && mouseX <= subBtnX + subBtnW && mouseY >= rotCCWBtnY && mouseY <= rotCCWBtnY + subBtnH) {
            if (!workingVoxels.isEmpty()) {
                List<WeaponVoxel> rotated = new ArrayList<>();
                for (WeaponVoxel v : workingVoxels) {
                    int newX = (this.gridSize - 1) - v.y();
                    int newY = v.x();
                    rotated.add(new WeaponVoxel(newX, newY, v.z(), v.materialId(), v.shade()));
                }
                this.workingVoxels.clear();
                this.workingVoxels.addAll(rotated);

                recalculateCachedStats();
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        // Layer buttons click
        int layerUpBtnY = rotCCWBtnY + subBtnH + 4;
        int layerDownBtnY = layerUpBtnY + subBtnH + 4;

        if (!this.view3d && mouseX >= subBtnX && mouseX <= subBtnX + subBtnW && mouseY >= layerUpBtnY && mouseY <= layerUpBtnY + subBtnH) {
            if (this.activeLayer < MAX_LAYER) this.activeLayer++;
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        if (!this.view3d && mouseX >= subBtnX && mouseX <= subBtnX + subBtnW && mouseY >= layerDownBtnY && mouseY <= layerDownBtnY + subBtnH) {
            if (this.activeLayer > MIN_LAYER) this.activeLayer--;
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        // View toggle, tool and reset buttons
        int viewBtnY = topPos + VIEW_BTN_Y;
        int toolBtnY = topPos + TOOL_BTN_Y;
        int resetBtnY = topPos + RESET_BTN_Y;
        boolean inLeftColumn = mouseX >= subBtnX && mouseX <= subBtnX + subBtnW;

        if (inLeftColumn && mouseY >= viewBtnY && mouseY <= viewBtnY + subBtnH) {
            this.view3d = !this.view3d;
            this.orbiting = false;
            this.strokeAxis = -1;
            this.sceneDirty = true;
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        if (this.view3d && inLeftColumn && mouseY >= toolBtnY && mouseY <= toolBtnY + subBtnH) {
            this.tool3d = (this.tool3d + 1) % TOOL_NAMES.length;
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        if (this.view3d && inLeftColumn && mouseY >= resetBtnY && mouseY <= resetBtnY + subBtnH) {
            ensureRaycaster();
            this.raycaster.resetView();
            this.sceneDirty = true;
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
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
        int arrowY = topPos + 216;
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

        // Check Ability Codex book button click
        int bookBtnY = topPos + WINDOW_HEIGHT - subBtnH - 1;

        if (mouseX >= subBtnX && mouseX <= subBtnX + subBtnW && mouseY >= bookBtnY && mouseY <= bookBtnY + subBtnH) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
            this.minecraft.setScreen(new AbilityBookScreen(this));
            return true;
        }

        // Grid painting and erasing
        if (this.view3d) {
            if (isInsideWorkspace(mouseX, mouseY)) {
                int btn = event.button();
                this.strokeAxis = -1;
                if (btn == 2 || (btn == 0 && this.tool3d == TOOL_ORBIT)) {
                    this.orbiting = true;
                    return true;
                }
                handle3dInteraction(mouseX, mouseY, btn, false);
                return true;
            }
        } else if (handleGridInteraction(mouseX, mouseY, event.button())) {
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
            if (handle3dInteraction((int) event.x(), (int) event.y(), event.button(), true)) {
                return true;
            }
        } else if (handleGridInteraction((int) event.x(), (int) event.y(), event.button())) {
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        this.orbiting = false;
        this.strokeAxis = -1;
        this.sceneDirty = true;
        return super.mouseReleased(event);
    }

    private boolean handleGridInteraction(int mouseX, int mouseY, int button) {
        int leftPos = (this.width - WINDOW_WIDTH) / 2;
        int topPos = (this.height - WINDOW_HEIGHT) / 2;
        int gridX = leftPos + WORKSPACE_OFFSET_X;
        int gridY = topPos + WORKSPACE_OFFSET_Y;

        int cellX = (mouseX - gridX) / this.cellSize;
        int cellY = (mouseY - gridY) / this.cellSize;

        if (mouseX >= gridX && mouseY >= gridY && cellX >= 0 && cellX < this.gridSize && cellY >= 0 && cellY < this.gridSize) {
            int voxelY = (this.gridSize - 1) - cellY;
            if (button == 0) return paintAt(cellX, voxelY, this.activeLayer); // Left-Click = Paint
            if (button == 1) return eraseAt(cellX, voxelY, this.activeLayer); // Right-Click = Erase
        }
        return false;
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

    /** Places a voxel of the selected material, or recolors the one already there. */
    private boolean paintAt(int x, int y, int layer) {
        if (x < 0 || x >= this.gridSize || y < 0 || y >= this.gridSize || !WeaponVoxel.isValidLayer(layer)) return false;
        WeaponVoxel existing = findVoxel(x, y, layer);
        boolean isCreative = this.minecraft.player != null && this.minecraft.player.getAbilities().instabuild;

        if (existing == null) {
            if (!isCreative && getAvailableCount(selectedMaterial) <= 0) {
                return false;
            }
            if (workingVoxels.size() < MAX_TOTAL_VOXELS()) {
                workingVoxels.add(new WeaponVoxel(x, y, layer, selectedMaterial, selectedShade));
                recalculateCachedStats();
                return true;
            }
        } else if (!existing.materialId().equalsIgnoreCase(selectedMaterial.id()) || existing.shade() != selectedShade) {
            if (!existing.materialId().equalsIgnoreCase(selectedMaterial.id()) && !isCreative && getAvailableCount(selectedMaterial) <= 0) {
                return false;
            }
            workingVoxels.remove(existing);
            workingVoxels.add(new WeaponVoxel(x, y, layer, selectedMaterial, selectedShade));
            recalculateCachedStats();
            return true;
        }
        return false;
    }

    private boolean eraseAt(int x, int y, int layer) {
        WeaponVoxel existing = findVoxel(x, y, layer);
        if (existing == null || workingVoxels.size() <= MIN_VOXELS) {
            return false;
        }
        workingVoxels.remove(existing);
        recalculateCachedStats();
        return true;
    }

    // ----- 3D editor -----

    private void ensureRaycaster() {
        if (this.raycaster == null) {
            this.raycaster = new VoxelRaycaster(this.gridSize, LAYER_COUNT, WORKSPACE_DIM, WORKSPACE_DIM);
            this.pixelBuffer = new int[WORKSPACE_DIM * WORKSPACE_DIM];
            this.fgBase = new int[WORKSPACE_DIM * WORKSPACE_DIM];
            this.fgView = new int[WORKSPACE_DIM * WORKSPACE_DIM];
        }
    }

    private void ensureViewTexture() {
        ensureRaycaster();
        if (this.viewImage == null) {
            this.viewImage = new NativeImage(WORKSPACE_DIM, WORKSPACE_DIM, true);
            this.viewTexture = new DynamicTexture(() -> "blocksmith_forge_view", this.viewImage);
            this.viewTextureId = Identifier.fromNamespaceAndPath("blocksmith", "forge_view_" + System.identityHashCode(this));
            this.minecraft.getTextureManager().register(this.viewTextureId, this.viewTexture);
            this.sceneDirty = true;
        }
    }

    private void rebuildScene() {
        this.raycaster.clear();
        for (WeaponVoxel v : workingVoxels) {
            this.raycaster.setCell(v.x(), v.y(), v.z() + MAX_LAYER, v.getColorRgb() | 0xFF000000);
        }
    }

    private void render3dWorkspace(GuiGraphicsExtractor guiGraphics, int gridX, int gridY, int mouseX, int mouseY) {
        ensureViewTexture();

        int px = mouseX - gridX;
        int py = mouseY - gridY;
        boolean inside = px >= 0 && py >= 0 && px < WORKSPACE_DIM && py < WORKSPACE_DIM;
        int hoverKey = (inside && !this.orbiting) ? (px << 16) | py : -1;

        // Heavy pass: only when the model or camera changed
        boolean rebuilt = this.sceneDirty;
        if (rebuilt) {
            rebuildScene();
            this.raycaster.render(this.fgBase, null);
            this.sceneDirty = false;
        }

        // Light pass: re-trace just the hovered voxel when the cursor moves
        if (rebuilt || hoverKey != this.lastHoverKey) {
            this.hoverHit = hoverKey >= 0 ? this.raycaster.pick(px, py) : null;
            this.raycaster.renderWithHighlight(this.fgBase, this.fgView, this.hoverHit);
            this.lastHoverKey = hoverKey;
        }

        // Every frame: lay the model over the animated background and upload
        double seconds = (System.nanoTime() - this.animStartNanos) / 1.0e9;
        this.raycaster.composite(this.fgView, this.pixelBuffer, seconds);
        for (int y = 0; y < WORKSPACE_DIM; y++) {
            for (int x = 0; x < WORKSPACE_DIM; x++) {
                this.viewImage.setPixel(x, y, this.pixelBuffer[y * WORKSPACE_DIM + x]);
            }
        }
        this.viewTexture.upload();

        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, this.viewTextureId, gridX, gridY, 0.0F, 0.0F, WORKSPACE_DIM, WORKSPACE_DIM, WORKSPACE_DIM, WORKSPACE_DIM);

        // Control hint sits inside the view on a dark pill so it stays readable on the bright background
        String hint = this.tool3d == TOOL_ORBIT ? "Drag: orbit  Wheel: zoom" : "Mid-drag: orbit  Wheel: zoom";
        int hintW = this.font.width(hint);
        int hintX = gridX + 4;
        int hintY = gridY + WORKSPACE_DIM - 13;
        guiGraphics.fill(hintX - 3, hintY - 2, hintX + hintW + 3, hintY + 10, 0xB0141C2C);
        guiGraphics.text(this.font, Component.literal(hint), hintX, hintY, 0xFFFFFFFF, false);
    }

    private boolean handle3dInteraction(int mouseX, int mouseY, int button, boolean drag) {
        if (!isInsideWorkspace(mouseX, mouseY)) return false;
        ensureRaycaster();

        int gridX = (this.width - WINDOW_WIDTH) / 2 + WORKSPACE_OFFSET_X;
        int gridY = (this.height - WINDOW_HEIGHT) / 2 + WORKSPACE_OFFSET_Y;
        int px = mouseX - gridX;
        int py = mouseY - gridY;

        if (button == 1) { // Right-Click = Erase whatever voxel is under the cursor
            VoxelRaycaster.Hit hit = this.raycaster.pick(px, py);
            if (hit == null || hit.floor()) return false;
            return eraseAt(hit.x(), hit.y(), hit.zi() - MAX_LAYER);
        }

        if (button != 0) return false;

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
        if (this.viewTexture != null) {
            this.viewTexture.close();
            this.viewTexture = null;
        }
        if (this.viewTextureId != null) {
            this.minecraft.getTextureManager().release(this.viewTextureId);
            this.viewTextureId = null;
        }
        this.viewImage = null;
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