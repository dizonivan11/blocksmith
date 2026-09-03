package net.adeptfrog.blocksmith.client.gui;

import net.adeptfrog.blocksmith.data.VoxelMaterial;
import net.adeptfrog.blocksmith.data.WeaponVoxel;
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

public class CustomizationScreen extends Screen {
    private final ItemStack weaponStack;
    private final List<WeaponVoxel> workingVoxels;

    private VoxelMaterial selectedMaterial = VoxelMaterial.IRON;
    private int selectedShade = 2; // 0=Darkest, 2=Base, 4=Lightest
    private int saveFeedbackTimer = 0;

    private static final int FONT_COLOR = 0xFF3F3F3F;

    private static final int WINDOW_WIDTH = 276;
    private static final int WINDOW_HEIGHT = 232;

    public static final int GRID_SIZE = 24;
    private static final int CELL_SIZE = 7; // 168x168px area
    private static final int GRID_DIM = GRID_SIZE * CELL_SIZE;

    private static final int SLOT_SIZE = 22;
    private static final int SLOT_SPACING = 25;

    private static final int SHADE_SIZE = 16;
    private static final int SHADE_SPACING = 19;

    public CustomizationScreen(ItemStack weaponStack) {
        super(Component.literal("Weapon Workbench"));
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
    public void extractRenderState(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, delta);

        if (saveFeedbackTimer > 0) {
            saveFeedbackTimer--;
        }

        int leftPos = (this.width - WINDOW_WIDTH) / 2;
        int topPos = (this.height - WINDOW_HEIGHT) / 2;

        // 1. Container Background
        drawClassicContainer(guiGraphics, leftPos, topPos, WINDOW_WIDTH, WINDOW_HEIGHT);

        // 2. Title
        guiGraphics.text(this.font, this.title, leftPos + 8, topPos + 6, FONT_COLOR, false);

        // 3. Workspace
        int gridX = leftPos + 8;
        int gridY = topPos + 18;
        drawRecessedBox(guiGraphics, gridX, gridY, GRID_DIM, GRID_DIM);

        guiGraphics.fill(gridX, gridY, gridX + GRID_DIM, gridY + GRID_DIM, 0xFF2B2B2B);

        for (int x = 0; x < GRID_SIZE; x++) {
            for (int y = 0; y < GRID_SIZE; y++) {
                int px = gridX + x * CELL_SIZE;
                int py = gridY + y * CELL_SIZE;
                int slotAlpha = ((x + y) % 2 == 0) ? 0x0EFFFFFF : 0x05FFFFFF;
                guiGraphics.fill(px, py, px + CELL_SIZE, py + CELL_SIZE, slotAlpha);
            }
        }

        // 4. Render Voxels
        for (WeaponVoxel voxel : workingVoxels) {
            int px = gridX + voxel.x() * CELL_SIZE;
            int py = gridY + (GRID_SIZE - 1 - voxel.y()) * CELL_SIZE;
            guiGraphics.fill(px, py, px + CELL_SIZE, py + CELL_SIZE, voxel.getColorRgb() | 0xFF000000);
        }

        // 5. Grid Hover Cursor
        int hX = (mouseX - gridX) / CELL_SIZE;
        int hY = (mouseY - gridY) / CELL_SIZE;
        if (hX >= 0 && hX < GRID_SIZE && hY >= 0 && hY < GRID_SIZE) {
            int px = gridX + hX * CELL_SIZE;
            int py = gridY + hY * CELL_SIZE;
            guiGraphics.fill(px, py, px + CELL_SIZE, py + CELL_SIZE, 0x44FFFFFF);
        }

        // 6. Stats Panel & Save Button
        int statsX = gridX + GRID_DIM + 6;
        int statsY = gridY;
        int statsW = 86;
        int statsH = GRID_DIM;

        // --- 6A. Header Title Box ---
        int headerH = 14;
        drawRecessedBox(guiGraphics, statsX, statsY, statsW, headerH);
        guiGraphics.fill(statsX, statsY, statsX + statsW, statsY + headerH, 0xFF353535);

        Component titleText = Component.literal("Bonus Stats");
        int titleX = statsX + (statsW - this.font.width(titleText)) / 2;
        guiGraphics.text(this.font, titleText, titleX, statsY + 3, 0xFFFFFFFF, true);

        // --- 6B. Main Stats Content Box ---
        int bodyY = statsY + headerH + 2;
        int bodyH = statsH - (headerH + 2);
        drawRecessedBox(guiGraphics, statsX, bodyY, statsW, bodyH);

        // Calculate baseline saved stats vs current working sandbox stats
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

        // 1. Voxels Row
        guiGraphics.text(this.font, Component.literal("Voxels:"), statsX + 6, bodyY + 6, FONT_COLOR, false);
        String voxelStr = workingVoxels.size() + " / " + ModularSwordItem.MAX_VOXELS;
        if (diffVoxels > 0) voxelStr += " §a(+" + diffVoxels + ")";
        else if (diffVoxels < 0) voxelStr += " §c(" + diffVoxels + ")";
        guiGraphics.text(this.font, Component.literal(voxelStr), statsX + 6, bodyY + 16, FONT_COLOR, false);

        // 2. Attack Damage Row
        guiGraphics.text(this.font, Component.literal("Attack Damage:"), statsX + 6, bodyY + 30, FONT_COLOR, false);
        String dmgStr = "§a+" + String.format("%.1f", currentDmg);
        if (diffDmg > 0.001) dmgStr += " §a(+" + String.format("%.1f", diffDmg) + ")";
        else if (diffDmg < -0.001) dmgStr += " §c(" + String.format("%.1f", diffDmg) + ")";
        guiGraphics.text(this.font, Component.literal(dmgStr), statsX + 6, bodyY + 40, 0xFFFFFFFF, false);

        // 3. Attack Speed Row
        guiGraphics.text(this.font, Component.literal("Attack Speed:"), statsX + 6, bodyY + 54, FONT_COLOR, false);
        String spdStr = "§a+" + String.format("%.2f", currentSpd);
        if (diffSpd > 0.0001) spdStr += " §a(+" + String.format("%.2f", diffSpd) + ")";
        else if (diffSpd < -0.0001) spdStr += " §c(" + String.format("%.2f", diffSpd) + ")";
        guiGraphics.text(this.font, Component.literal(spdStr), statsX + 6, bodyY + 64, 0xFFFFFFFF, false);

        // 4. Durability Row
        guiGraphics.text(this.font, Component.literal("Durability:"), statsX + 6, bodyY + 78, FONT_COLOR, false);
        String durStr = "§a+" + currentDur;
        if (diffDur > 0) durStr += " §a(+" + diffDur + ")";
        else if (diffDur < 0) durStr += " §c(" + diffDur + ")";
        guiGraphics.text(this.font, Component.literal(durStr), statsX + 6, bodyY + 88, 0xFFFFFFFF, false);

        // Controls Hints
        guiGraphics.text(this.font, Component.literal("[Drag L] Paint"), statsX + 6, bodyY + 104, FONT_COLOR, false);
        guiGraphics.text(this.font, Component.literal("[Drag R] Erase"), statsX + 6, bodyY + 114, FONT_COLOR, false);

        // Save Button
        int btnW = 74;
        int btnH = 20;
        int btnX = statsX + (statsW - btnW) / 2;
        int btnY = statsY + statsH - btnH - 6;

        boolean isHoveredSave = (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH);
        drawSaveButton(guiGraphics, btnX, btnY, btnW, btnH, isHoveredSave, saveFeedbackTimer > 0);

        // Save Button Label & Feedback
        Component saveLabel;
        if (saveFeedbackTimer > 0) {
            saveLabel = Component.literal("§aSaved!");
        } else if (saveFeedbackTimer < 0) {
            saveLabel = Component.literal("§cInvalid!");
        } else {
            saveLabel = Component.literal(isHoveredSave ? "§fSave" : "§7Save");
        }
        int labelWidth = this.font.width(saveLabel);
        guiGraphics.text(this.font, saveLabel, btnX + (btnW - labelWidth) / 2, btnY + 6, 0xFFFFFFFF, false);

        // 7. Bottom Section (Material & Shade Palettes)
        int bottomY = gridY + GRID_DIM + 6;
        guiGraphics.text(this.font, Component.literal("Material:"), gridX, bottomY, FONT_COLOR, false);

        VoxelMaterial[] mats = VoxelMaterial.values();
        int shadeStartX = gridX + (mats.length * SLOT_SPACING) + 8;
        guiGraphics.text(this.font, Component.literal("Shade:"), shadeStartX, bottomY, FONT_COLOR, false);

        int slotsY = bottomY + 10;
        boolean isCreative = this.minecraft.player != null && this.minecraft.player.getAbilities().instabuild;

        // Render Material Slots with Stack Numbers on Bottom-Right
        VoxelMaterial hoveredMaterial = null;
        for (int i = 0; i < mats.length; i++) {
            VoxelMaterial mat = mats[i];
            int sX = gridX + (i * SLOT_SPACING);
            boolean isSelected = (mat == selectedMaterial);

            drawItemSlot(guiGraphics, sX, slotsY, SLOT_SIZE, isSelected);
            guiGraphics.fakeItem(new ItemStack(mat.getIconItem()), sX + 3, slotsY + 3);

            // Item Stack Count in the Bottom-Right corner
            int available = getAvailableCount(mat);
            String countStr = isCreative ? "∞" : (available > 99 ? "99+" : String.valueOf(available));
            int countColor = (available <= 0 && !isCreative) ? 0xFFFF5555 : 0xFFFFFFFF;
            int countX = sX + SLOT_SIZE - this.font.width(countStr) - 2;
            int countY = slotsY + SLOT_SIZE - 9;
            guiGraphics.text(this.font, Component.literal(countStr), countX, countY, countColor, true);

            if (mouseX >= sX && mouseX <= sX + SLOT_SIZE && mouseY >= slotsY && mouseY <= slotsY + SLOT_SIZE) {
                hoveredMaterial = mat;
            }
        }

        // Render 5 Shade Swatches
        int[] palette = selectedMaterial.getPalette();
        for (int s = 0; s < palette.length; s++) {
            int swX = shadeStartX + (s * SHADE_SPACING);
            int swY = slotsY + 3;
            boolean isSelectedShade = (s == selectedShade);

            drawShadeSwatch(guiGraphics, swX, swY, SHADE_SIZE, isSelectedShade, palette[s] | 0xFF000000);
        }

        // Clean Tooltip without redundant count
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

        int gridX = leftPos + 8;
        int gridY = topPos + 18;
        int statsX = gridX + GRID_DIM + 6;
        int statsY = gridY;
        int statsW = 86;
        int statsH = GRID_DIM;

        // 1. Save Button Click
        int btnW = 74;
        int btnH = 20;
        int btnX = statsX + (statsW - btnW) / 2;
        int btnY = statsY + statsH - btnH - 6;

        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            // Check minimum voxel threshold
            if (workingVoxels.size() < ModularSwordItem.MIN_VOXELS) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.5F));
                this.saveFeedbackTimer = -40; // Negative timer indicates minimum warning
                return true;
            }

            if (hasUnsavedChanges()) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_USE, 1.0F));
                ModularSwordItem.saveVoxels(weaponStack, workingVoxels);
                ClientPlayNetworking.send(new SaveWeaponVoxelsPayload(workingVoxels));
                this.saveFeedbackTimer = 40; // Positive timer indicates success
            } else {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            return true;
        }

        // 2. Material Selector Clicks
        int bottomY = gridY + GRID_DIM + 6;
        int slotsY = bottomY + 10;
        VoxelMaterial[] mats = VoxelMaterial.values();
        for (int i = 0; i < mats.length; i++) {
            int sX = gridX + (i * SLOT_SPACING);
            if (mouseX >= sX && mouseX <= sX + SLOT_SIZE && mouseY >= slotsY && mouseY <= slotsY + SLOT_SIZE) {
                this.selectedMaterial = mats[i];
                return true;
            }
        }

        // 3. Shade Swatch Clicks
        int shadeStartX = gridX + (mats.length * SLOT_SPACING) + 8;
        for (int s = 0; s < 5; s++) {
            int swX = shadeStartX + (s * SHADE_SPACING);
            int swY = slotsY + 3;
            if (mouseX >= swX && mouseX <= swX + SHADE_SIZE && mouseY >= swY && mouseY <= swY + SHADE_SIZE) {
                this.selectedShade = s;
                return true;
            }
        }

        // 4. Grid Clicks
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
                    if (workingVoxels.size() < ModularSwordItem.MAX_VOXELS) {
                        workingVoxels.add(new WeaponVoxel(cellX, voxelY, 0, selectedMaterial, selectedShade));
                        return true;
                    }
                } else if (existing.material() != selectedMaterial || existing.shade() != selectedShade) {
                    if (existing.material() != selectedMaterial && !isCreative && getAvailableCount(selectedMaterial) <= 0) {
                        return false;
                    }
                    workingVoxels.remove(existing);
                    workingVoxels.add(new WeaponVoxel(cellX, voxelY, 0, selectedMaterial, selectedShade));
                    return true;
                }
            } else if (button == 1) { // Right-Click = Erase
                if (existing != null) {
                    // Prevent erasing below threshold
                    if (workingVoxels.size() <= ModularSwordItem.MIN_VOXELS) {
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

        int onOriginalWeapon = 0;
        for (WeaponVoxel v : ModularSwordItem.getVoxels(weaponStack)) {
            if (v.material() == mat) {
                onOriginalWeapon++;
            }
        }

        int placedInWorking = 0;
        for (WeaponVoxel v : workingVoxels) {
            if (v.material() == mat) {
                placedInWorking++;
            }
        }

        return inInventory + onOriginalWeapon - placedInWorking;
    }

    private boolean hasUnsavedChanges() {
        List<WeaponVoxel> saved = ModularSwordItem.getVoxels(weaponStack);
        if (saved.size() != workingVoxels.size()) {
            return true;
        }
        return !new java.util.HashSet<>(saved).equals(new java.util.HashSet<>(workingVoxels));
    }

    // --- GUI Styling Helpers ---

    private void drawDivider(GuiGraphicsExtractor g, int x, int y, int width) {
        g.fill(x, y, x + width, y + 1, 0xFF282828); // Shadow line
        g.fill(x, y + 1, x + width, y + 2, 0xFF4A4A4A); // Highlight line
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

    private void drawClassicContainer(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFFC6C6C6);
        g.fill(x, y, x + w - 1, y + 1, 0xFFFFFFFF);
        g.fill(x, y, x + 1, y + h - 1, 0xFFFFFFFF);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF555555);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF555555);
        g.fill(x - 1, y - 1, x + w + 1, y, 0xFF373737);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, 0xFF373737);
        g.fill(x - 1, y, x, y + h, 0xFF373737);
        g.fill(x + w, y, x + w + 1, y + h, 0xFF373737);
    }

    private void drawRecessedBox(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF8B8B8B);
        g.fill(x - 1, y - 1, x + w + 1, y, 0xFF373737);
        g.fill(x - 1, y, x, y + h, 0xFF373737);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, 0xFFFFFFFF);
        g.fill(x + w, y - 1, x + w + 1, y + h + 1, 0xFFFFFFFF);
    }

    private void drawItemSlot(GuiGraphicsExtractor g, int x, int y, int size, boolean selected) {
        g.fill(x, y, x + size, y + size, selected ? 0xFF353535 : 0xFF222222);

        if (selected) {
            g.fill(x - 1, y - 1, x + size + 1, y, 0xFFFFFFFF);
            g.fill(x - 1, y + size, x + size + 1, y + size + 1, 0xFFFFFFFF);
            g.fill(x - 1, y, x, y + size, 0xFFFFFFFF);
            g.fill(x + size, y, x + size + 1, y + size, 0xFFFFFFFF);
        } else {
            g.fill(x - 1, y - 1, x + size + 1, y, 0xFF373737);
            g.fill(x - 1, y, x, y + size, 0xFF373737);
            g.fill(x - 1, y + size, x + size + 1, y + size + 1, 0xFFFFFFFF);
            g.fill(x + size, y - 1, x + size + 1, y + size + 1, 0xFFFFFFFF);
        }
    }

    private void drawShadeSwatch(GuiGraphicsExtractor g, int x, int y, int size, boolean selected, int color) {
        g.fill(x, y, x + size, y + size, color);

        if (selected) {
            g.fill(x - 1, y - 1, x + size + 1, y, 0xFFFFFFFF);
            g.fill(x - 1, y + size, x + size + 1, y + size + 1, 0xFFFFFFFF);
            g.fill(x - 1, y, x, y + size, 0xFFFFFFFF);
            g.fill(x + size, y, x + size + 1, y + size, 0xFFFFFFFF);
        } else {
            g.fill(x - 1, y - 1, x + size + 1, y, 0xFF373737);
            g.fill(x - 1, y + size, x + size + 1, y + size + 1, 0xFF373737);
            g.fill(x - 1, y, x, y + size, 0xFF373737);
            g.fill(x + size, y, x + size + 1, y + size, 0xFF373737);
        }
    }
}
