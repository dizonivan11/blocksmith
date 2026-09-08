package net.adeptfrog.blocksmith.client.gui;


import net.adeptfrog.blocksmith.data.VoxelDesignSerializer;
import net.adeptfrog.blocksmith.data.VoxelMaterial;
import net.adeptfrog.blocksmith.data.VoxelMaterialRegistry;
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
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

import static net.adeptfrog.blocksmith.Blocksmith.MIN_VOXELS;
import static net.adeptfrog.blocksmith.Blocksmith.MAX_VOXELS;

public class CustomizationScreen extends Screen {
    private final ItemStack weaponStack;
    private final List<WeaponVoxel> workingVoxels;

    private VoxelMaterial selectedMaterial = VoxelMaterialRegistry.FALLBACK;
    private int selectedShade = 2; // 0=Darkest, 2=Base, 4=Lightest
    private int saveFeedbackTimer = 0;
    private int exportFeedbackTimer = 0;
    private int importFeedbackTimer = 0;
    private int materialIndexOffset = 0;

    private static final int FONT_COLOR = 0xFF3F3F3F;

    private static final int WINDOW_WIDTH = 276;
    private static final int WINDOW_HEIGHT = 232;

    public static final int GRID_SIZE = 24;
    private static final int CELL_SIZE = 7;
    private static final int GRID_DIM = GRID_SIZE * CELL_SIZE;

    // Material Section
    private static final int VISIBLE_SLOTS = 8;
    private static final int SLOT_SIZE = 16;
    private static final int SLOT_SPACING = 19;

    // Top Header Buttons
    private static final int TOP_BTN_W = 41;
    private static final int TOP_BTN_H = 11;

    // Compact Arrow Buttons
    private static final int ARROW_BTN_W = 11;
    private static final int ARROW_BTN_H = 9;

    // Fixed Shade Dimensions
    private static final int SHADE_SIZE = 16;
    private static final int SHADE_SPACING = 19;

    public CustomizationScreen(ItemStack weaponStack) {
        super(Component.literal("Weapon Forging"));
        this.weaponStack = weaponStack;
        this.workingVoxels = new ArrayList<>(ModularSwordItem.getVoxels(weaponStack));
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

        drawClassicContainer(guiGraphics, leftPos, topPos);

        guiGraphics.text(this.font, this.title, leftPos + 8, topPos + 6, FONT_COLOR, false);

        int exportBtnX = leftPos + WINDOW_WIDTH - (TOP_BTN_W * 2) - 12;
        int importBtnX = leftPos + WINDOW_WIDTH - TOP_BTN_W - 8;
        int topBtnY = topPos + 4;

        boolean exportHovered = mouseX >= exportBtnX && mouseX <= exportBtnX + TOP_BTN_W && mouseY >= topBtnY && mouseY <= topBtnY + TOP_BTN_H;
        boolean importHovered = mouseX >= importBtnX && mouseX <= importBtnX + TOP_BTN_W && mouseY >= topBtnY && mouseY <= topBtnY + TOP_BTN_H;

        String exportLabel = exportFeedbackTimer > 0 ? "§aCopied!" : "Copy";
        String importLabel = importFeedbackTimer > 0 ? "§aLoaded!" : (importFeedbackTimer < 0 ? "§cInvalid!" : "Paste");

        drawSmallHeaderButton(guiGraphics, exportBtnX, topBtnY, exportLabel, exportHovered);
        drawSmallHeaderButton(guiGraphics, importBtnX, topBtnY, importLabel, importHovered);

        int gridX = leftPos + 8;
        int gridY = topPos + 18;
        drawRecessedBox(guiGraphics, gridX, gridY, GRID_DIM, GRID_DIM);

        guiGraphics.enableScissor(gridX, gridY, gridX + GRID_DIM, gridY + GRID_DIM);

        int checkerSize = 8;
        long time = System.currentTimeMillis();
        int pan = (int) ((time / 45L) % (checkerSize * 2));

        for (int px = gridX - (checkerSize * 2) + pan; px < gridX + GRID_DIM + checkerSize; px += checkerSize) {
            for (int py = gridY - (checkerSize * 2) + pan; py < gridY + GRID_DIM + checkerSize; py += checkerSize) {
                int tileX = (px - pan) / checkerSize;
                int tileY = (py - pan) / checkerSize;
                int checkerColor = ((tileX + tileY) % 2 == 0) ? 0xFF3E3E3E : 0xFF525252;
                guiGraphics.fill(px, py, px + checkerSize, py + checkerSize, checkerColor);
            }
        }

        for (int x = 0; x < GRID_SIZE; x++) {
            for (int y = 0; y < GRID_SIZE; y++) {
                int px = gridX + x * CELL_SIZE;
                int py = gridY + y * CELL_SIZE;
                guiGraphics.fill(px, py, px + CELL_SIZE, py + CELL_SIZE, 0x0CFFFFFF);
            }
        }

        boolean[][] occupied = new boolean[GRID_SIZE][GRID_SIZE];
        for (WeaponVoxel v : workingVoxels) {
            if (v.x() >= 0 && v.x() < GRID_SIZE && v.y() >= 0 && v.y() < GRID_SIZE) {
                occupied[v.x()][v.y()] = true;
            }
        }

        int outlineColor = 0xFFFFFFFF;
        for (int x = 0; x < GRID_SIZE; x++) {
            for (int y = 0; y < GRID_SIZE; y++) {
                if (!occupied[x][y]) {
                    int px = gridX + x * CELL_SIZE;
                    int py = gridY + (GRID_SIZE - 1 - y) * CELL_SIZE;

                    boolean hasTop = (y + 1 < GRID_SIZE) && occupied[x][y + 1];
                    boolean hasBottom = (y - 1 >= 0) && occupied[x][y - 1];
                    boolean hasLeft = (x - 1 >= 0) && occupied[x - 1][y];
                    boolean hasRight = (x + 1 < GRID_SIZE) && occupied[x + 1][y];

                    if (hasTop) guiGraphics.fill(px, py, px + CELL_SIZE, py + 1, outlineColor);
                    if (hasBottom) guiGraphics.fill(px, py + CELL_SIZE - 1, px + CELL_SIZE, py + CELL_SIZE, outlineColor);
                    if (hasLeft) guiGraphics.fill(px, py, px + 1, py + CELL_SIZE, outlineColor);
                    if (hasRight) guiGraphics.fill(px + CELL_SIZE - 1, py, px + CELL_SIZE, py + CELL_SIZE, outlineColor);

                    if (!hasTop && !hasRight && (x + 1 < GRID_SIZE && y + 1 < GRID_SIZE && occupied[x + 1][y + 1])) {
                        guiGraphics.fill(px + CELL_SIZE - 1, py, px + CELL_SIZE, py + 1, outlineColor);
                    }
                    if (!hasTop && !hasLeft && (x - 1 >= 0 && y + 1 < GRID_SIZE && occupied[x - 1][y + 1])) {
                        guiGraphics.fill(px, py, px + 1, py + 1, outlineColor);
                    }
                    if (!hasBottom && !hasRight && (x + 1 < GRID_SIZE && y - 1 >= 0 && occupied[x + 1][y - 1])) {
                        guiGraphics.fill(px + CELL_SIZE - 1, py + CELL_SIZE - 1, px + CELL_SIZE, py + CELL_SIZE, outlineColor);
                    }
                    if (!hasBottom && !hasLeft && (x - 1 >= 0 && y - 1 >= 0 && occupied[x - 1][y - 1])) {
                        guiGraphics.fill(px, py + CELL_SIZE - 1, px + 1, py + CELL_SIZE, outlineColor);
                    }
                }
            }
        }

        for (WeaponVoxel voxel : workingVoxels) {
            int px = gridX + voxel.x() * CELL_SIZE;
            int py = gridY + (GRID_SIZE - 1 - voxel.y()) * CELL_SIZE;
            guiGraphics.fill(px, py, px + CELL_SIZE, py + CELL_SIZE, voxel.getColorRgb() | 0xFF000000);
        }

        int hX = (mouseX - gridX) / CELL_SIZE;
        int hY = (mouseY - gridY) / CELL_SIZE;
        if (hX >= 0 && hX < GRID_SIZE && hY >= 0 && hY < GRID_SIZE) {
            int px = gridX + hX * CELL_SIZE;
            int py = gridY + hY * CELL_SIZE;
            guiGraphics.fill(px, py, px + CELL_SIZE, py + CELL_SIZE, 0x44FFFFFF);
        }

        guiGraphics.disableScissor();

        int statsX = gridX + GRID_DIM + 6;
        int statsW = 86;
        int statsH = GRID_DIM;

        int headerH = 14;
        drawRecessedBox(guiGraphics, statsX, gridY, statsW, headerH);
        guiGraphics.fill(statsX, gridY, statsX + statsW, gridY + headerH, 0xFF353535);

        Component titleText = Component.literal("Bonus Stats");
        int titleX = statsX + (statsW - this.font.width(titleText)) / 2;
        guiGraphics.text(this.font, titleText, titleX, gridY + 3, 0xFFFFFFFF, true);

        int bodyY = gridY + headerH + 2;
        int bodyH = statsH - (headerH + 2);
        drawRecessedBox(guiGraphics, statsX, bodyY, statsW, bodyH);

        List<WeaponVoxel> savedVoxels = ModularSwordItem.getVoxels(weaponStack);
        double savedDmg = savedVoxels.stream().mapToDouble(v -> v.material().getBonusDamage()).sum();
        double savedSpd = savedVoxels.stream().mapToDouble(v -> v.material().getBonusSpeed()).sum();
        int savedDur = savedVoxels.stream().mapToInt(v -> v.material().getBonusDurability()).sum();

        double currentDmg = workingVoxels.stream().mapToDouble(v -> v.material().getBonusDamage()).sum();
        double currentSpd = workingVoxels.stream().mapToDouble(v -> v.material().getBonusSpeed()).sum();
        int currentDur = workingVoxels.stream().mapToInt(v -> v.material().getBonusDurability()).sum();

        int diffVoxels = workingVoxels.size() - savedVoxels.size();
        double diffDmg = currentDmg - savedDmg;
        double diffSpd = currentSpd - savedSpd;
        int diffDur = currentDur - savedDur;

        guiGraphics.text(this.font, Component.literal("Voxels:"), statsX + 6, bodyY + 6, FONT_COLOR, false);
        String voxelStr = workingVoxels.size() + " / " + MAX_VOXELS;
        if (diffVoxels > 0) voxelStr += " §a(+" + diffVoxels + ")";
        else if (diffVoxels < 0) voxelStr += " §c(" + diffVoxels + ")";
        guiGraphics.text(this.font, Component.literal(voxelStr), statsX + 6, bodyY + 16, FONT_COLOR, false);

        boolean isBow = weaponStack.getItem() instanceof ModularBowItem;

        guiGraphics.text(this.font, Component.literal(isBow ? "Arrow Damage:" : "Attack Damage:"), statsX + 6, bodyY + 30, FONT_COLOR, false);
        String dmgStr = "§a+" + String.format("%.1f", currentDmg);
        if (diffDmg > 0.001) dmgStr += " §a(+" + String.format("%.1f", diffDmg) + ")";
        else if (diffDmg < -0.001) dmgStr += " §c(" + String.format("%.1f", diffDmg) + ")";
        guiGraphics.text(this.font, Component.literal(dmgStr), statsX + 6, bodyY + 40, 0xFFFFFFFF, false);

        guiGraphics.text(this.font, Component.literal(isBow ? "Draw Speed:" : "Attack Speed:"), statsX + 6, bodyY + 54, FONT_COLOR, false);
        String spdStr = "§a+" + String.format("%.2f", currentSpd);
        if (diffSpd > 0.0001) spdStr += " §a(+" + String.format("%.2f", diffSpd) + ")";
        else if (diffSpd < -0.0001) spdStr += " §c(" + String.format("%.2f", diffSpd) + ")";
        guiGraphics.text(this.font, Component.literal(spdStr), statsX + 6, bodyY + 64, 0xFFFFFFFF, false);

        guiGraphics.text(this.font, Component.literal("Durability:"), statsX + 6, bodyY + 78, FONT_COLOR, false);
        String durStr = "§a+" + currentDur;
        if (diffDur > 0) durStr += " §a(+" + diffDur + ")";
        else if (diffDur < 0) durStr += " §c(" + diffDur + ")";
        guiGraphics.text(this.font, Component.literal(durStr), statsX + 6, bodyY + 88, 0xFFFFFFFF, false);

        guiGraphics.text(this.font, Component.literal("[Drag L] Paint"), statsX + 6, bodyY + 104, FONT_COLOR, false);
        guiGraphics.text(this.font, Component.literal("[Drag R] Erase"), statsX + 6, bodyY + 114, FONT_COLOR, false);

        int btnW = 74;
        int btnH = 20;
        int btnX = statsX + (statsW - btnW) / 2;
        int btnY = gridY + statsH - btnH - 6;

        boolean isHoveredSave = (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH);
        drawSaveButton(guiGraphics, btnX, btnY, btnW, btnH, isHoveredSave, saveFeedbackTimer > 0);

        Component saveLabel;
        if (saveFeedbackTimer > 0) {
            saveLabel = Component.literal("§aSaved!");
        } else if (saveFeedbackTimer == -1) {
            saveLabel = Component.literal("§cInvalid!");
        } else if (saveFeedbackTimer == -2) {
            saveLabel = Component.literal("§cInsufficient!");
        } else if (hasMissingMaterials()) {
            saveLabel = Component.literal("§cInsufficient!");
        } else {
            saveLabel = Component.literal(isHoveredSave ? "§fSave" : "§7Save");
        }
        int labelWidth = this.font.width(saveLabel);
        guiGraphics.text(this.font, saveLabel, btnX + (btnW - labelWidth) / 2, btnY + 6, 0xFFFFFFFF, false);

        int bottomY = gridY + GRID_DIM + 6;
        int slotsY = bottomY + 12;
        boolean isCreative = this.minecraft.player != null && this.minecraft.player.getAbilities().instabuild;

        List<VoxelMaterial> mats = new ArrayList<>(VoxelMaterialRegistry.getAll());
        if (selectedMaterial == null && !mats.isEmpty()) {
            selectedMaterial = mats.getFirst();
        }

        int maxOffset = Math.max(0, mats.size() - VISIBLE_SLOTS);
        this.materialIndexOffset = Math.clamp(this.materialIndexOffset, 0, maxOffset);

        guiGraphics.text(this.font, Component.literal("Material:"), gridX, bottomY, FONT_COLOR, false);

        int matSectionRight = gridX + (VISIBLE_SLOTS - 1) * SLOT_SPACING + SLOT_SIZE;
        int arrowY = bottomY - 1;
        int rightArrowX = matSectionRight - ARROW_BTN_W;
        int leftArrowX = rightArrowX - ARROW_BTN_W - 2;

        boolean leftEnabled = materialIndexOffset > 0;
        boolean rightEnabled = materialIndexOffset < maxOffset;
        boolean leftHovered = mouseX >= leftArrowX && mouseX <= leftArrowX + ARROW_BTN_W && mouseY >= arrowY && mouseY <= arrowY + ARROW_BTN_H;
        boolean rightHovered = mouseX >= rightArrowX && mouseX <= rightArrowX + ARROW_BTN_W && mouseY >= arrowY && mouseY <= arrowY + ARROW_BTN_H;

        drawVanillaArrowButton(guiGraphics, leftArrowX, arrowY, "<", leftEnabled, leftHovered);
        drawVanillaArrowButton(guiGraphics, rightArrowX, arrowY, ">", rightEnabled, rightHovered);

        int shadeStartX = leftPos + 168;
        guiGraphics.text(this.font, Component.literal("Shade:"), shadeStartX, bottomY, FONT_COLOR, false);

        VoxelMaterial hoveredMaterial = null;
        for (int i = 0; i < VISIBLE_SLOTS && (i + materialIndexOffset) < mats.size(); i++) {
            VoxelMaterial mat = mats.get(i + materialIndexOffset);
            int sX = gridX + (i * SLOT_SPACING);
            boolean isSelected = (mat.id().equalsIgnoreCase(selectedMaterial.id()));

            drawItemSlot(guiGraphics, sX, slotsY, isSelected);
            guiGraphics.fakeItem(new ItemStack(mat.getIconItem()), sX, slotsY);

            int available = getAvailableCount(mat);
            String countStr = isCreative ? "∞" : (available > 99 ? "99+" : String.valueOf(available));
            int countColor = (available < 0 && !isCreative) ? 0xFFFF5555 : 0xFFFFFFFF;
            int countX = sX + SLOT_SIZE - this.font.width(countStr) - 1;
            int countY = slotsY + SLOT_SIZE - 8;
            guiGraphics.text(this.font, Component.literal(countStr), countX, countY, countColor, true);

            if (mouseX >= sX && mouseX <= sX + SLOT_SIZE && mouseY >= slotsY && mouseY <= slotsY + SLOT_SIZE) {
                hoveredMaterial = mat;
            }
        }

        int[] palette = selectedMaterial.getPalette();
        if (palette != null) {
            for (int s = 0; s < palette.length && s < 5; s++) {
                int swX = shadeStartX + (s * SHADE_SPACING);
                boolean isSelectedShade = (s == selectedShade);

                drawShadeSwatch(guiGraphics, swX, slotsY, isSelectedShade, palette[s] | 0xFF000000);
            }
        }

        if (hoveredMaterial != null) {
            String name = hoveredMaterial.toString().substring(0, 1).toUpperCase() + hoveredMaterial.toString().substring(1);
            List<ClientTooltipComponent> tooltipLines = List.of(
                    ClientTooltipComponent.create(Component.literal("§e" + name).getVisualOrderText()),
                    ClientTooltipComponent.create(Component.literal("+" + hoveredMaterial.getBonusDamage() + " Attack Damage").getVisualOrderText()),
                    ClientTooltipComponent.create(Component.literal("+" + hoveredMaterial.getBonusSpeed() + " Attack Speed").getVisualOrderText()),
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

        int exportBtnX = leftPos + WINDOW_WIDTH - (TOP_BTN_W * 2) - 12;
        int topBtnY = topPos + 4;
        if (mouseX >= exportBtnX && mouseX <= exportBtnX + TOP_BTN_W && mouseY >= topBtnY && mouseY <= topBtnY + TOP_BTN_H) {
            String json = VoxelDesignSerializer.exportToJson(workingVoxels);
            this.minecraft.keyboardHandler.setClipboard(json);
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.exportFeedbackTimer = 40;
            return true;
        }

        int importBtnX = leftPos + WINDOW_WIDTH - TOP_BTN_W - 8;
        if (mouseX >= importBtnX && mouseX <= importBtnX + TOP_BTN_W && mouseY >= topBtnY && mouseY <= topBtnY + TOP_BTN_H) {
            try {
                String clipboard = this.minecraft.keyboardHandler.getClipboard();
                List<WeaponVoxel> imported = VoxelDesignSerializer.importFromJson(clipboard);
                this.workingVoxels.clear();
                this.workingVoxels.addAll(imported);
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_USE, 1.2F));
                this.importFeedbackTimer = 40;
            } catch (Exception e) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                this.importFeedbackTimer = -40;
            }
            return true;
        }

        int gridX = leftPos + 8;
        int gridY = topPos + 18;
        int statsX = gridX + GRID_DIM + 6;
        int statsW = 86;

        int btnW = 74;
        int btnH = 20;
        int btnX = statsX + (statsW - btnW) / 2;
        int btnY = gridY + GRID_DIM - btnH - 6;

        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            if (workingVoxels.size() < MIN_VOXELS) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                this.saveFeedbackTimer = -1; // Code -1 = Min 8
                return true;
            }

            if (hasMissingMaterials()) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                this.saveFeedbackTimer = -2; // Code -2 = Missing materials
                return true;
            }

            if (hasUnsavedChanges()) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_USE, 1.0F));
                // Save locally and send to server
                ModularSwordItem.saveVoxels(weaponStack, workingVoxels);
                ClientPlayNetworking.send(new SaveWeaponVoxelsPayload(workingVoxels));
                this.saveFeedbackTimer = 40; // Positive = Saved
            } else {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            return true;
        }

        int bottomY = gridY + GRID_DIM + 6;
        int arrowY = bottomY - 1;
        int matSectionRight = gridX + (VISIBLE_SLOTS - 1) * SLOT_SPACING + SLOT_SIZE;
        int rightArrowX = matSectionRight - ARROW_BTN_W;
        int leftArrowX = rightArrowX - ARROW_BTN_W - 2;

        List<VoxelMaterial> mats = new ArrayList<>(VoxelMaterialRegistry.getAll());
        int maxOffset = Math.max(0, mats.size() - VISIBLE_SLOTS);

        if (mouseX >= leftArrowX && mouseX <= leftArrowX + ARROW_BTN_W && mouseY >= arrowY && mouseY <= arrowY + ARROW_BTN_H) {
            if (materialIndexOffset > 0) {
                materialIndexOffset--;
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        if (mouseX >= rightArrowX && mouseX <= rightArrowX + ARROW_BTN_W && mouseY >= arrowY && mouseY <= arrowY + ARROW_BTN_H) {
            if (materialIndexOffset < maxOffset) {
                materialIndexOffset++;
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        int slotsY = bottomY + 12;
        for (int i = 0; i < VISIBLE_SLOTS && (i + materialIndexOffset) < mats.size(); i++) {
            int sX = gridX + (i * SLOT_SPACING);
            if (mouseX >= sX && mouseX <= sX + SLOT_SIZE && mouseY >= slotsY && mouseY <= slotsY + SLOT_SIZE) {
                this.selectedMaterial = mats.get(i + materialIndexOffset);
                return true;
            }
        }

        int shadeStartX = leftPos + 168;
        for (int s = 0; s < 5; s++) {
            int swX = shadeStartX + (s * SHADE_SPACING);
            if (mouseX >= swX && mouseX <= swX + SHADE_SIZE && mouseY >= slotsY && mouseY <= slotsY + SHADE_SIZE) {
                this.selectedShade = s;
                return true;
            }
        }

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
        int gridX = leftPos + 8;
        int gridY = topPos + 18;

        int cellX = (mouseX - gridX) / CELL_SIZE;
        int cellY = (mouseY - gridY) / CELL_SIZE;

        if (cellX >= 0 && cellX < GRID_SIZE && cellY >= 0 && cellY < GRID_SIZE) {
            int voxelY = (GRID_SIZE - 1) - cellY;

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
                    if (workingVoxels.size() < MAX_VOXELS) {
                        workingVoxels.add(new WeaponVoxel(cellX, voxelY, 0, selectedMaterial, selectedShade));
                        return true;
                    }
                } else if (!existing.materialId().equalsIgnoreCase(selectedMaterial.id()) || existing.shade() != selectedShade) {
                    if (!existing.materialId().equalsIgnoreCase(selectedMaterial.id()) && !isCreative && getAvailableCount(selectedMaterial) <= 0) {
                        return false;
                    }
                    workingVoxels.remove(existing);
                    workingVoxels.add(new WeaponVoxel(cellX, voxelY, 0, selectedMaterial, selectedShade));
                    return true;
                }
            } else if (button == 1) { // Right-Click = Erase
                if (existing != null) {
                    if (workingVoxels.size() <= MIN_VOXELS) {
                        return false;
                    }
                    workingVoxels.remove(existing);
                    return true;
                }
            }
        }
        return false;
    }

    private int getAvailableCount(VoxelMaterial mat) {
        if (this.minecraft.player == null) return 0;
        if (this.minecraft.player.getAbilities().instabuild) return 999;

        int inInventory = 0;

        for (ItemStack s : this.minecraft.player.getInventory().getNonEquipmentItems()) {
            if (s.is(mat.getIconItem())) {
                inInventory += s.getCount();
            }
        }

        ItemStack offhand = this.minecraft.player.getOffhandItem();
        if (offhand.is(mat.getIconItem())) {
            inInventory += offhand.getCount();
        }

        int onOriginalWeapon = 0;
        for (WeaponVoxel v : ModularSwordItem.getVoxels(weaponStack)) {
            if (v.materialId().equalsIgnoreCase(mat.id())) {
                onOriginalWeapon++;
            }
        }

        int placedInWorking = 0;
        for (WeaponVoxel v : workingVoxels) {
            if (v.materialId().equalsIgnoreCase(mat.id())) {
                placedInWorking++;
            }
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
        return !new java.util.HashSet<>(saved).equals(new java.util.HashSet<>(workingVoxels));
    }

    // --- GUI Styling Helpers ---

    private void drawSmallHeaderButton(GuiGraphicsExtractor g, int x, int y, String text, boolean hovered) {
        int bg = hovered ? 0xFFD6D6D6 : 0xFFBCBCBC;
        g.fill(x, y, x + CustomizationScreen.TOP_BTN_W, y + CustomizationScreen.TOP_BTN_H, bg);

        int highlight = hovered ? 0xFFFFFFFF : 0xFFE0E0E0;
        int shadow = 0xFF555555;

        g.fill(x, y, x + CustomizationScreen.TOP_BTN_W - 1, y + 1, highlight);
        g.fill(x, y, x + 1, y + CustomizationScreen.TOP_BTN_H - 1, highlight);
        g.fill(x + 1, y + CustomizationScreen.TOP_BTN_H - 1, x + CustomizationScreen.TOP_BTN_W, y + CustomizationScreen.TOP_BTN_H, shadow);
        g.fill(x + CustomizationScreen.TOP_BTN_W - 1, y + 1, x + CustomizationScreen.TOP_BTN_W, y + CustomizationScreen.TOP_BTN_H, shadow);

        g.fill(x - 1, y - 1, x + CustomizationScreen.TOP_BTN_W + 1, y, 0xFF373737);
        g.fill(x - 1, y + CustomizationScreen.TOP_BTN_H, x + CustomizationScreen.TOP_BTN_W + 1, y + CustomizationScreen.TOP_BTN_H + 1, 0xFF373737);
        g.fill(x - 1, y, x, y + CustomizationScreen.TOP_BTN_H, 0xFF373737);
        g.fill(x + CustomizationScreen.TOP_BTN_W, y, x + CustomizationScreen.TOP_BTN_W + 1, y + CustomizationScreen.TOP_BTN_H, 0xFF373737);

        int textX = x + (CustomizationScreen.TOP_BTN_W - this.font.width(text)) / 2;
        int textY = y + (CustomizationScreen.TOP_BTN_H - 7) / 2;
        g.text(this.font, Component.literal(text), textX, textY, hovered ? 0xFF000000 : 0xFF303030, false);
    }

    private void drawVanillaArrowButton(GuiGraphicsExtractor g, int x, int y, String arrow, boolean enabled, boolean hovered) {
        int bg = !enabled ? 0xFF8B8B8B : (hovered ? 0xFFD6D6D6 : 0xFFBCBCBC);
        g.fill(x, y, x + CustomizationScreen.ARROW_BTN_W, y + CustomizationScreen.ARROW_BTN_H, bg);

        int highlight = !enabled ? 0xFFA0A0A0 : (hovered ? 0xFFFFFFFF : 0xFFE0E0E0);
        int shadow = !enabled ? 0xFF666666 : (hovered ? 0xFF666666 : 0xFF555555);

        g.fill(x, y, x + CustomizationScreen.ARROW_BTN_W - 1, y + 1, highlight);
        g.fill(x, y, x + 1, y + CustomizationScreen.ARROW_BTN_H - 1, highlight);
        g.fill(x + 1, y + CustomizationScreen.ARROW_BTN_H - 1, x + CustomizationScreen.ARROW_BTN_W, y + CustomizationScreen.ARROW_BTN_H, shadow);
        g.fill(x + CustomizationScreen.ARROW_BTN_W - 1, y + 1, x + CustomizationScreen.ARROW_BTN_W, y + CustomizationScreen.ARROW_BTN_H, shadow);

        g.fill(x - 1, y - 1, x + CustomizationScreen.ARROW_BTN_W + 1, y, 0xFF373737);
        g.fill(x - 1, y + CustomizationScreen.ARROW_BTN_H, x + CustomizationScreen.ARROW_BTN_W + 1, y + CustomizationScreen.ARROW_BTN_H + 1, 0xFF373737);
        g.fill(x - 1, y, x, y + CustomizationScreen.ARROW_BTN_H, 0xFF373737);
        g.fill(x + CustomizationScreen.ARROW_BTN_W, y, x + CustomizationScreen.ARROW_BTN_W + 1, y + CustomizationScreen.ARROW_BTN_H, 0xFF373737);

        int textColor = !enabled ? 0xFF666666 : (hovered ? 0xFF000000 : 0xFF303030);
        int textX = x + (CustomizationScreen.ARROW_BTN_W - this.font.width(arrow)) / 2 + 1;
        int textY = y + (CustomizationScreen.ARROW_BTN_H - 7) / 2;
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

    private void drawClassicContainer(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + CustomizationScreen.WINDOW_WIDTH, y + CustomizationScreen.WINDOW_HEIGHT, 0xFFC6C6C6);
        g.fill(x, y, x + CustomizationScreen.WINDOW_WIDTH - 1, y + 1, 0xFFFFFFFF);
        g.fill(x, y, x + 1, y + CustomizationScreen.WINDOW_HEIGHT - 1, 0xFFFFFFFF);
        g.fill(x, y + CustomizationScreen.WINDOW_HEIGHT - 1, x + CustomizationScreen.WINDOW_WIDTH, y + CustomizationScreen.WINDOW_HEIGHT, 0xFF555555);
        g.fill(x + CustomizationScreen.WINDOW_WIDTH - 1, y, x + CustomizationScreen.WINDOW_WIDTH, y + CustomizationScreen.WINDOW_HEIGHT, 0xFF555555);
        g.fill(x - 1, y - 1, x + CustomizationScreen.WINDOW_WIDTH + 1, y, 0xFF373737);
        g.fill(x - 1, y + CustomizationScreen.WINDOW_HEIGHT, x + CustomizationScreen.WINDOW_WIDTH + 1, y + CustomizationScreen.WINDOW_HEIGHT + 1, 0xFF373737);
        g.fill(x - 1, y, x, y + CustomizationScreen.WINDOW_HEIGHT, 0xFF373737);
        g.fill(x + CustomizationScreen.WINDOW_WIDTH, y, x + CustomizationScreen.WINDOW_WIDTH + 1, y + CustomizationScreen.WINDOW_HEIGHT, 0xFF373737);
    }

    private void drawRecessedBox(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF8B8B8B);
        g.fill(x - 1, y - 1, x + w + 1, y, 0xFF373737);
        g.fill(x - 1, y, x, y + h, 0xFF373737);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, 0xFFFFFFFF);
        g.fill(x + w, y - 1, x + w + 1, y + h + 1, 0xFFFFFFFF);
    }

    private void drawItemSlot(GuiGraphicsExtractor g, int x, int y, boolean selected) {
        g.fill(x, y, x + CustomizationScreen.SLOT_SIZE, y + CustomizationScreen.SLOT_SIZE, selected ? 0xFF353535 : 0xFF222222);

        if (selected) {
            g.fill(x - 1, y - 1, x + CustomizationScreen.SLOT_SIZE + 1, y, 0xFFFFFFFF);
            g.fill(x - 1, y + CustomizationScreen.SLOT_SIZE, x + CustomizationScreen.SLOT_SIZE + 1, y + CustomizationScreen.SLOT_SIZE + 1, 0xFFFFFFFF);
            g.fill(x - 1, y, x, y + CustomizationScreen.SLOT_SIZE, 0xFFFFFFFF);
            g.fill(x + CustomizationScreen.SLOT_SIZE, y, x + CustomizationScreen.SLOT_SIZE + 1, y + CustomizationScreen.SLOT_SIZE, 0xFFFFFFFF);
        } else {
            g.fill(x - 1, y - 1, x + CustomizationScreen.SLOT_SIZE + 1, y, 0xFF373737);
            g.fill(x - 1, y, x, y + CustomizationScreen.SLOT_SIZE, 0xFF373737);
            g.fill(x - 1, y + CustomizationScreen.SLOT_SIZE, x + CustomizationScreen.SLOT_SIZE + 1, y + CustomizationScreen.SLOT_SIZE + 1, 0xFFFFFFFF);
            g.fill(x + CustomizationScreen.SLOT_SIZE, y - 1, x + CustomizationScreen.SLOT_SIZE + 1, y + CustomizationScreen.SLOT_SIZE + 1, 0xFFFFFFFF);
        }
    }

    private void drawShadeSwatch(GuiGraphicsExtractor g, int x, int y, boolean selected, int color) {
        g.fill(x, y, x + CustomizationScreen.SHADE_SIZE, y + CustomizationScreen.SHADE_SIZE, color);

        if (selected) {
            g.fill(x - 1, y - 1, x + CustomizationScreen.SHADE_SIZE + 1, y, 0xFFFFFFFF);
            g.fill(x - 1, y + CustomizationScreen.SHADE_SIZE, x + CustomizationScreen.SHADE_SIZE + 1, y + CustomizationScreen.SHADE_SIZE + 1, 0xFFFFFFFF);
            g.fill(x - 1, y, x, y + CustomizationScreen.SHADE_SIZE, 0xFFFFFFFF);
            g.fill(x + CustomizationScreen.SHADE_SIZE, y, x + CustomizationScreen.SHADE_SIZE + 1, y + CustomizationScreen.SHADE_SIZE, 0xFFFFFFFF);
        } else {
            g.fill(x - 1, y - 1, x + CustomizationScreen.SHADE_SIZE + 1, y, 0xFF373737);
            g.fill(x - 1, y + CustomizationScreen.SHADE_SIZE, x + CustomizationScreen.SHADE_SIZE + 1, y + CustomizationScreen.SHADE_SIZE + 1, 0xFF373737);
            g.fill(x - 1, y, x, y + CustomizationScreen.SHADE_SIZE, 0xFF373737);
            g.fill(x + CustomizationScreen.SHADE_SIZE, y, x + CustomizationScreen.SHADE_SIZE + 1, y + CustomizationScreen.SHADE_SIZE, 0xFF373737);
        }
    }
}