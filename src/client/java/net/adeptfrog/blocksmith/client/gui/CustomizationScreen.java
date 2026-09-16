package net.adeptfrog.blocksmith.client.gui;

import net.adeptfrog.blocksmith.component.ModDataComponents;
import net.adeptfrog.blocksmith.data.VoxelDesignSerializer;
import net.adeptfrog.blocksmith.data.VoxelMaterial;
import net.adeptfrog.blocksmith.data.VoxelMaterialRegistry;
import net.adeptfrog.blocksmith.data.WeaponOffset;
import net.adeptfrog.blocksmith.data.WeaponVoxel;
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
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

import static net.adeptfrog.blocksmith.Blocksmith.MAX_VOXELS;
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

    private int workingOffsetX;
    private int workingOffsetY;

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
    private double cachedCurrentDmg = 0;
    private double cachedCurrentSpd = 0;
    private int cachedCurrentDur = 0;
    private double cachedDiffDmg = 0;
    private double cachedDiffSpd = 0;
    private int cachedDiffDur = 0;

    public CustomizationScreen(ItemStack weaponStack) {
        super(Component.literal("Weapon Forging"));
        this.weaponStack = weaponStack;
        this.workingVoxels = new ArrayList<>(ModularSwordItem.getVoxels(weaponStack));

        this.gridSize = VoxelMaterialRegistry.getGridSize();
        this.cellSize = Math.max(1, WORKSPACE_DIM / this.gridSize);

        this.occupiedGrid = new boolean[this.gridSize * this.gridSize];
        this.colorGrid = new int[this.gridSize * this.gridSize];

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

        java.util.Arrays.fill(this.occupiedGrid, false);
        java.util.Arrays.fill(this.colorGrid, 0);

        for (WeaponVoxel v : workingVoxels) {
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

        // Stats panel
        int statsX = leftPos + 212;
        int statsY = topPos + 35;

        boolean isBow = weaponStack.getItem() instanceof ModularBowItem;
        List<WeaponVoxel> savedVoxels = ModularSwordItem.getVoxels(weaponStack);
        int diffVoxels = workingVoxels.size() - savedVoxels.size();

        // Stats text
        guiGraphics.text(this.font, Component.literal("Voxels:"), statsX + 6, statsY + 6, FONT_COLOR, false);
        String voxelStr = workingVoxels.size() + " / " + MAX_VOXELS();
        if (diffVoxels > 0) voxelStr += " §a(+" + diffVoxels + ")";
        else if (diffVoxels < 0) voxelStr += " §c(" + diffVoxels + ")";
        guiGraphics.text(this.font, Component.literal(voxelStr), statsX + 6, statsY + 16, FONT_COLOR, false);

        guiGraphics.text(this.font, Component.literal(isBow ? "Arrow Damage:" : "Attack Damage:"), statsX + 6, statsY + 30, FONT_COLOR, false);
        String dmgStr = "§a+" + String.format("%.1f", this.cachedCurrentDmg);
        if (this.cachedDiffDmg > 0.001) dmgStr += " §a(+" + String.format("%.1f", this.cachedDiffDmg) + ")";
        else if (this.cachedDiffDmg < -0.001) dmgStr += " §c(" + String.format("%.1f", this.cachedDiffDmg) + ")";
        guiGraphics.text(this.font, Component.literal(dmgStr), statsX + 6, statsY + 40, 0xFFFFFFFF, false);

        guiGraphics.text(this.font, Component.literal(isBow ? "Draw Speed:" : "Attack Speed:"), statsX + 6, statsY + 54, FONT_COLOR, false);
        String spdStr = "§a+" + String.format("%.2f", this.cachedCurrentSpd);
        if (this.cachedDiffSpd > 0.0001) spdStr += " §a(+" + String.format("%.2f", this.cachedDiffSpd) + ")";
        else if (this.cachedDiffSpd < -0.0001) spdStr += " §c(" + String.format("%.2f", this.cachedDiffSpd) + ")";
        guiGraphics.text(this.font, Component.literal(spdStr), statsX + 6, statsY + 64, 0xFFFFFFFF, false);

        guiGraphics.text(this.font, Component.literal("Durability:"), statsX + 6, statsY + 78, FONT_COLOR, false);
        String durStr = "§a+" + this.cachedCurrentDur;
        if (this.cachedDiffDur > 0) durStr += " §a(+" + this.cachedDiffDur + ")";
        else if (this.cachedDiffDur < 0) durStr += " §c(" + this.cachedDiffDur + ")";
        guiGraphics.text(this.font, Component.literal(durStr), statsX + 6, statsY + 88, 0xFFFFFFFF, false);

        guiGraphics.text(this.font, Component.literal("[Left] Paint"), statsX + 6, statsY + 104, FONT_COLOR, false);

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
        int subBtnW = 39;
        int subBtnH = 16;
        int subBtnX = leftPos - subBtnW - 4;
        int exportBtnY = topPos + 1;
        int importBtnY = exportBtnY + subBtnH + 4;

        boolean exportHovered = mouseX >= subBtnX && mouseX <= subBtnX + subBtnW && mouseY >= exportBtnY && mouseY <= exportBtnY + subBtnH;
        boolean importHovered = mouseX >= subBtnX && mouseX <= subBtnX + subBtnW && mouseY >= importBtnY && mouseY <= importBtnY + subBtnH;

        String exportLabel = exportFeedbackTimer > 0 ? "§aCopied!" : "Copy";
        String importLabel = importFeedbackTimer > 0 ? "§aLoaded!" : (importFeedbackTimer < 0 ? "§cInvalid!" : "Paste");

        drawSmallHeaderButton(guiGraphics, subBtnX, exportBtnY, subBtnW, subBtnH, exportLabel, exportHovered);
        drawSmallHeaderButton(guiGraphics, subBtnX, importBtnY, subBtnW, subBtnH, importLabel, importHovered);

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
            String name = hoveredMaterial.toString().substring(0, 1).toUpperCase() + hoveredMaterial.toString().substring(1);
            boolean isBowMaterial = weaponStack.getItem() instanceof ModularBowItem;

            List<ClientTooltipComponent> tooltipLines = List.of(
                    ClientTooltipComponent.create(Component.literal("§e" + name).getVisualOrderText()),
                    ClientTooltipComponent.create(Component.literal("+" + hoveredMaterial.getBonusDamage() + (isBowMaterial ? " Arrow Damage" : " Attack Damage")).getVisualOrderText()),
                    ClientTooltipComponent.create(Component.literal("+" + hoveredMaterial.getBonusSpeed() + (isBowMaterial ? " Draw Speed" : " Attack Speed")).getVisualOrderText()),
                    ClientTooltipComponent.create(Component.literal("+" + hoveredMaterial.getBonusDurability() + " Durability").getVisualOrderText())
            );
            guiGraphics.tooltip(this.font, tooltipLines, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
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

        // Copy button click
        int subBtnW = 39;
        int subBtnH = 16;
        int subBtnX = leftPos - subBtnW - 4;
        int exportBtnY = topPos + 1;
        int importBtnY = exportBtnY + subBtnH + 4;

        if (mouseX >= subBtnX && mouseX <= subBtnX + subBtnW && mouseY >= exportBtnY && mouseY <= exportBtnY + subBtnH) {
            WeaponOffset currentOffset = new WeaponOffset(this.workingOffsetX, this.workingOffsetY);
            String json = VoxelDesignSerializer.exportToJson(workingVoxels, currentOffset, this.gridSize);
            this.minecraft.keyboardHandler.setClipboard(json);
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.exportFeedbackTimer = 40;
            return true;
        }

        // Paste button click
        if (mouseX >= subBtnX && mouseX <= subBtnX + subBtnW && mouseY >= importBtnY && mouseY <= importBtnY + subBtnH) {
            try {
                String clipboard = this.minecraft.keyboardHandler.getClipboard();
                VoxelDesignSerializer.BlueprintData blueprint = VoxelDesignSerializer.importBlueprint(clipboard, this.gridSize);

                this.workingVoxels.clear();
                this.workingVoxels.addAll(blueprint.voxels());
                this.workingOffsetX = blueprint.offset().x();
                this.workingOffsetY = blueprint.offset().y();
                recalculateCachedStats();
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_USE, 1.2F));
                this.importFeedbackTimer = 40;
            } catch (Exception e) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                this.importFeedbackTimer = -40;
            }
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

        // Grid painting and erasing
        if (handleGridInteraction(mouseX, mouseY, event.button())) {
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (handleGridInteraction((int) event.x(), (int) event.y(), event.button())) {
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    private boolean handleGridInteraction(int mouseX, int mouseY, int button) {
        int leftPos = (this.width - WINDOW_WIDTH) / 2;
        int topPos = (this.height - WINDOW_HEIGHT) / 2;
        int gridX = leftPos + WORKSPACE_OFFSET_X;
        int gridY = topPos + WORKSPACE_OFFSET_Y;

        int cellX = (mouseX - gridX) / this.cellSize;
        int cellY = (mouseY - gridY) / this.cellSize;

        if (cellX >= 0 && cellX < this.gridSize && cellY >= 0 && cellY < this.gridSize) {
            int voxelY = (this.gridSize - 1) - cellY;

            WeaponVoxel existing = null;
            for (WeaponVoxel v : workingVoxels) {
                if (v.x() == cellX && v.y() == voxelY && v.z() == 0) {
                    existing = v;
                    break;
                }
            }

            if (button == 0) { // Left-Click = Paint
                boolean isCreative = this.minecraft.player != null && this.minecraft.player.getAbilities().instabuild;

                if (existing == null) {
                    if (!isCreative && getAvailableCount(selectedMaterial) <= 0) {
                        return false;
                    }
                    if (workingVoxels.size() < MAX_VOXELS()) {
                        workingVoxels.add(new WeaponVoxel(cellX, voxelY, 0, selectedMaterial, selectedShade));
                        recalculateCachedStats();
                        return true;
                    }
                } else if (!existing.materialId().equalsIgnoreCase(selectedMaterial.id()) || existing.shade() != selectedShade) {
                    if (!existing.materialId().equalsIgnoreCase(selectedMaterial.id()) && !isCreative && getAvailableCount(selectedMaterial) <= 0) {
                        return false;
                    }
                    workingVoxels.remove(existing);
                    workingVoxels.add(new WeaponVoxel(cellX, voxelY, 0, selectedMaterial, selectedShade));
                    recalculateCachedStats();
                    return true;
                }
            } else if (button == 1) { // Right-Click = Erase
                if (existing != null) {
                    if (workingVoxels.size() <= MIN_VOXELS) {
                        return false;
                    }
                    workingVoxels.remove(existing);
                    recalculateCachedStats();
                    return true;
                }
            }
        }
        return false;
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
        List<WeaponVoxel> savedVoxels = ModularSwordItem.getVoxels(weaponStack);
        double savedDmg = 0, savedSpd = 0;
        int savedDur = 0;
        for (WeaponVoxel v : savedVoxels) {
            VoxelMaterial m = v.material();
            savedDmg += m.getBonusDamage();
            savedSpd += m.getBonusSpeed();
            savedDur += m.getBonusDurability();
        }

        this.cachedCurrentDmg = 0;
        this.cachedCurrentSpd = 0;
        this.cachedCurrentDur = 0;
        for (WeaponVoxel v : workingVoxels) {
            VoxelMaterial m = v.material();
            this.cachedCurrentDmg += m.getBonusDamage();
            this.cachedCurrentSpd += m.getBonusSpeed();
            this.cachedCurrentDur += m.getBonusDurability();
        }

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