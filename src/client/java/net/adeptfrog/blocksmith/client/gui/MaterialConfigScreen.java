package net.adeptfrog.blocksmith.client.gui;

import net.adeptfrog.blocksmith.data.VoxelMaterial;
import net.adeptfrog.blocksmith.data.VoxelMaterialRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public class MaterialConfigScreen extends Screen {
    private final Screen parent;
    private int scrollY = 0;
    private boolean isScrolling = false;

    private static final int CARD_WIDTH = 370;
    private static final int ROW_HEIGHT = 28;
    private static final int ROW_SPACING = 31; // ROW_HEIGHT (28) + 3px gap

    public MaterialConfigScreen(Screen parent) {
        super(Component.literal("Blocksmith Material Configuration"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int bottomY = this.height - 24;

        // --- Fixed Bottom Action Buttons ---
        // [+ Add Material] Button
        this.addRenderableWidget(
                Button.builder(Component.literal("+ Add Material"), _ -> {
                    VoxelMaterial newTemplate = new VoxelMaterial(
                            "custom_" + (VoxelMaterialRegistry.getAll().size() + 1),
                            0.05f, 0.001f, 15,
                            new int[]{0xFF444444, 0xFF666666, 0xFF888888, 0xFFAAAAAA, 0xFFCCCCCC},
                            Identifier.fromNamespaceAndPath("minecraft", "iron_ingot")
                    );
                    this.minecraft.setScreen(new EditMaterialScreen(this, newTemplate, true));
                }).bounds(centerX - 165, bottomY, 100, 20).build()
        );

        // [Reset Defaults] Button
        this.addRenderableWidget(
                Button.builder(Component.literal("Reset Defaults"), _ -> {
                    VoxelMaterialRegistry.resetToDefaults();
                    this.scrollY = 0;
                }).bounds(centerX - 55, bottomY, 100, 20).build()
        );

        // [Done] Button
        this.addRenderableWidget(
                Button.builder(Component.literal("Done"), _ -> this.onClose())
                        .bounds(centerX + 55, bottomY, 100, 20).build()
        );
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        List<VoxelMaterial> mats = new ArrayList<>(VoxelMaterialRegistry.getAll());
        int listTopY = 36;
        int listBottomY = this.height - 30;
        int listHeight = listBottomY - listTopY;
        int totalContentHeight = mats.size() * ROW_SPACING;
        int maxScroll = Math.max(0, totalContentHeight - listHeight);

        if (maxScroll > 0 && verticalAmount != 0) {
            this.scrollY = (int) Math.clamp(this.scrollY - (verticalAmount * 18), 0, maxScroll);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
        int centerX = this.width / 2;
        int cardX = centerX - (CARD_WIDTH / 2);

        int listTopY = 36;
        int listBottomY = this.height - 30;
        int listHeight = listBottomY - listTopY;

        List<VoxelMaterial> mats = new ArrayList<>(VoxelMaterialRegistry.getAll());
        int totalContentHeight = mats.size() * ROW_SPACING;
        int maxScroll = Math.max(0, totalContentHeight - listHeight);
        this.scrollY = Math.clamp(this.scrollY, 0, maxScroll);

        // --- 1. Render Scrollable Material List with Scissor Clipping ---
        guiGraphics.enableScissor(cardX - 4, listTopY, cardX + CARD_WIDTH + 14, listBottomY);

        for (int i = 0; i < mats.size(); i++) {
            VoxelMaterial mat = mats.get(i);
            int y = listTopY - scrollY + (i * ROW_SPACING);

            // Cull off-screen rows for performance
            if (y + ROW_HEIGHT < listTopY - 10 || y > listBottomY + 10) continue;

            // Row Container Card
            guiGraphics.fill(cardX, y, cardX + CARD_WIDTH, y + ROW_HEIGHT, 0x55000000);
            guiGraphics.fill(cardX, y, cardX + CARD_WIDTH, y + 1, 0x33FFFFFF);
            guiGraphics.fill(cardX, y + ROW_HEIGHT - 1, cardX + CARD_WIDTH, y + ROW_HEIGHT, 0x33000000);

            // Column 1: Name & Item ID
            String name = mat.id().substring(0, 1).toUpperCase() + mat.id().substring(1);
            guiGraphics.text(this.font, Component.literal("§e" + name), cardX + 6, y + 4, 0xFFFFFFFF, false);
            guiGraphics.text(this.font, Component.literal("§7" + mat.iconItem().getPath()), cardX + 6, y + 15, 0xFFAAAAAA, false);

            // Column 2: Stats
            guiGraphics.text(this.font, Component.literal("§c+" + mat.getBonusDamage() + " Dmg"), cardX + 90, y + 10, 0xFFFFFFFF, false);
            guiGraphics.text(this.font, Component.literal("§b+" + mat.getBonusSpeed() + " Spd"), cardX + 150, y + 10, 0xFFFFFFFF, false);
            guiGraphics.text(this.font, Component.literal("§a+" + mat.getBonusDurability() + " Dur"), cardX + 215, y + 10, 0xFFFFFFFF, false);

            // Column 3: 5-Shade Swatches
            int[] palette = mat.getPalette();
            if (palette != null) {
                int swatchStartX = cardX + 276;
                for (int s = 0; s < palette.length; s++) {
                    int swX = swatchStartX + (s * 9);
                    int swY = y + 8;
                    guiGraphics.fill(swX - 1, swY - 1, swX + 8, swY + 13, 0xFF000000);
                    guiGraphics.fill(swX, swY, swX + 7, swY + 12, palette[s] | 0xFF000000);
                }
            }

            // Column 4: Inline [Edit] Button
            int editBtnW = 38;
            int editBtnH = 18;
            int editBtnX = cardX + CARD_WIDTH - editBtnW - 4;
            int editBtnY = y + 5;
            boolean hoveredEdit = mouseX >= editBtnX && mouseX <= editBtnX + editBtnW && mouseY >= editBtnY && mouseY <= editBtnY + editBtnH && mouseY >= listTopY && mouseY <= listBottomY;

            drawEditButton(guiGraphics, editBtnX, editBtnY, editBtnW, editBtnH, hoveredEdit);
        }

        // --- 2. Render Vertical Scrollbar Track & Thumb ---
        if (maxScroll > 0) {
            int scrollbarX = cardX + CARD_WIDTH + 4;
            guiGraphics.fill(scrollbarX, listTopY, scrollbarX + 6, listBottomY, 0x88000000);

            int thumbHeight = Math.max(16, (int) ((float) listHeight / totalContentHeight * listHeight));
            int thumbY = listTopY + (int) ((float) scrollY / maxScroll * (listHeight - thumbHeight));
            boolean hoveredThumb = mouseX >= scrollbarX && mouseX <= scrollbarX + 6 && mouseY >= thumbY && mouseY <= thumbY + thumbHeight;

            guiGraphics.fill(scrollbarX, thumbY, scrollbarX + 6, thumbY + thumbHeight, (hoveredThumb || isScrolling) ? 0xFFCCCCCC : 0xFF888888);
            guiGraphics.fill(scrollbarX + 1, thumbY + 1, scrollbarX + 5, thumbY + thumbHeight - 1, (hoveredThumb || isScrolling) ? 0xFFFFFFFF : 0xFFAAAAAA);
        }

        guiGraphics.disableScissor();

        // --- 3. Fixed Top Header Backdrop ---
        guiGraphics.fill(0, 0, this.width, 32, 0xD0000000);
        guiGraphics.fill(0, 32, this.width, 33, 0x55FFFFFF);
        guiGraphics.text(this.font, this.title, centerX - (this.font.width(this.title) / 2), 8, 0xFFFFFFFF, true);
        Component sub = Component.literal("Configure stats, palettes, and required items in-game");
        guiGraphics.text(this.font, sub, centerX - (this.font.width(sub) / 2), 20, 0xFFAAAAAA, false);

        // --- 4. Fixed Bottom Footer Backdrop ---
        guiGraphics.fill(0, this.height - 30, this.width, this.height, 0xD0000000);
        guiGraphics.fill(0, this.height - 30, this.width, this.height - 29, 0x55FFFFFF);

        // --- 5. Render Buttons ---
        super.extractRenderState(guiGraphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();

        int centerX = this.width / 2;
        int cardX = centerX - (CARD_WIDTH / 2);
        int listTopY = 36;
        int listBottomY = this.height - 30;

        // 1. Check Scrollbar Drag Start
        int scrollbarX = cardX + CARD_WIDTH + 4;
        if (mouseX >= scrollbarX && mouseX <= scrollbarX + 8 && mouseY >= listTopY && mouseY <= listBottomY) {
            this.isScrolling = true;
            return true;
        }

        // 2. Check [Edit] Button Clicks inside Scrollable List
        if (mouseY >= listTopY && mouseY <= listBottomY && mouseX >= cardX && mouseX <= cardX + CARD_WIDTH) {
            List<VoxelMaterial> mats = new ArrayList<>(VoxelMaterialRegistry.getAll());
            for (int i = 0; i < mats.size(); i++) {
                int y = listTopY - scrollY + (i * ROW_SPACING);
                int editBtnW = 38;
                int editBtnH = 18;
                int editBtnX = cardX + CARD_WIDTH - editBtnW - 4;
                int editBtnY = y + 5;

                if (mouseX >= editBtnX && mouseX <= editBtnX + editBtnW && mouseY >= editBtnY && mouseY <= editBtnY + editBtnH) {
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    this.minecraft.setScreen(new EditMaterialScreen(this, mats.get(i), false));
                    return true;
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        this.isScrolling = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double deltaX, double deltaY) {
        if (this.isScrolling) {
            int listTopY = 36;
            int listBottomY = this.height - 30;
            int listHeight = listBottomY - listTopY;
            List<VoxelMaterial> mats = new ArrayList<>(VoxelMaterialRegistry.getAll());
            int totalContentHeight = mats.size() * ROW_SPACING;
            int maxScroll = Math.max(0, totalContentHeight - listHeight);

            if (maxScroll > 0) {
                float scrollRatio = (float) deltaY / listHeight;
                this.scrollY = (int) Math.clamp(this.scrollY + (scrollRatio * totalContentHeight), 0, maxScroll);
                return true;
            }
        }

        return super.mouseDragged(event, deltaX, deltaY);
    }

    private void drawEditButton(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hovered) {
        int bg = hovered ? 0xFF5A5A5A : 0xFF3C3C3C;
        g.fill(x, y, x + w, y + h, bg);

        int highlight = hovered ? 0xFFFFFFFF : 0xFF666666;
        int shadow = hovered ? 0xFF888888 : 0xFF222222;
        g.fill(x, y, x + w - 1, y + 1, highlight);
        g.fill(x, y, x + 1, y + h - 1, highlight);
        g.fill(x + 1, y + h - 1, x + w, y + h, shadow);
        g.fill(x + w - 1, y + 1, x + w, y + h, shadow);

        g.fill(x - 1, y - 1, x + w + 1, y, 0xFF1E1E1E);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, 0xFF1E1E1E);
        g.fill(x - 1, y, x, y + h, 0xFF1E1E1E);
        g.fill(x + w, y, x + w + 1, y + h, 0xFF1E1E1E);

        Component text = Component.literal("Edit");
        int textX = x + (w - this.font.width(text)) / 2;
        int textY = y + (h - 8) / 2 + 1;
        g.text(this.font, text, textX, textY, hovered ? 0xFFFFFFFF : 0xFFCCCCCC, false);
    }
}