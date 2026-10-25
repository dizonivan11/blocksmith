package net.adeptfrog.blocksmith.client.gui;

import net.adeptfrog.blocksmith.compat.McCompat;
import net.adeptfrog.blocksmith.client.utils.InputEvents;
import net.adeptfrog.blocksmith.client.compat.Gfx;
import net.adeptfrog.blocksmith.client.compat.CompatScreen;
import net.adeptfrog.blocksmith.client.utils.InputCompat;
import net.adeptfrog.blocksmith.data.VoxelMaterial;
import net.adeptfrog.blocksmith.data.VoxelMaterialRegistry;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

public class MaterialConfigScreen extends CompatScreen {
    private final Screen parent;
    private int scrollY = 0;
    private boolean isScrolling = false;

    private static final int TAB_MATERIALS = 0;
    private static final int TAB_ADD = 1;
    private static final int TAB_CONFIG = 2;
    private static final String[] TAB_NAMES = {"Materials", "Add Material", "Settings"};
    private static final int[] GRID_SIZES = {16, 32, 64};

    private static final int CARD_WIDTH = 370;
    private static final int ROW_HEIGHT = 28;
    private static final int ROW_SPACING = 31;
    private static final int TAB_Y = 38;
    private static final int LIST_TOP = 68;
    private static final int FORM_WIDTH = 240;

    private int tab = TAB_MATERIALS;

    // Add-material form state (survives widget rebuilds, e.g. window resize)
    private String fId = "", fItem = "minecraft:iron_ingot", fDmg = "0.05", fSpd = "0.001", fDur = "15";
    private final String[] fShade = {"#444444", "#666666", "#888888", "#AAAAAA", "#CCCCCC"};
    private final EditBox[] formBoxes = new EditBox[10];
    private String formError = "";

    public MaterialConfigScreen(Screen parent) {
        super(Component.literal("Blocksmith Configuration"));
        this.parent = parent;
    }

    private int listBottom() {
        return this.height - 32;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;

        // Tab row (left-aligned compact tabs on a dark strip; the active tab is the greyed-out one)
        int tx = 14;
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            int tw = this.font.width(TAB_NAMES[i]) + 14;
            Button tb = Button.builder(Component.literal(TAB_NAMES[i]), unused21 -> {
                this.tab = idx;
                this.formError = "";
                this.rebuildWidgets();
            }).bounds(tx, TAB_Y, tw, 20).build();
            tb.active = (tab != i);
            this.addRenderableWidget(tb);
            tx += tw + 4;
        }

        int btnY = this.height - 25;
        int btnW = 86;
        int btnGap = 6;

        if (tab == TAB_MATERIALS) {
            int total = 2 * btnW + btnGap;
            int x0 = centerX - total / 2;
            this.addRenderableWidget(
                    Button.builder(Component.literal("Reset Defaults"), unused22 -> {
                        VoxelMaterialRegistry.resetToDefaults();
                        this.scrollY = 0;
                        this.rebuildWidgets();
                    }).bounds(x0, btnY, btnW, 20).build()
            );
            this.addRenderableWidget(
                    Button.builder(Component.literal("Done"), unused23 -> this.onClose())
                            .bounds(x0 + btnW + btnGap, btnY, btnW, 20).build()
            );
        } else if (tab == TAB_ADD) {
            initAddForm(centerX, btnY, btnW, btnGap);
        } else {
            initConfig(centerX);
            this.addRenderableWidget(
                    Button.builder(Component.literal("Done"), unused24 -> this.onClose())
                            .bounds(centerX - btnW / 2, btnY, btnW, 20).build()
            );
        }
    }

    // ---------- Settings tab ----------

    private void initConfig(int centerX) {
        int w = 240;
        int x = centerX - w / 2;
        this.addRenderableWidget(new GridSizeSlider(x, LIST_TOP + 22, w, 20));

        boolean off = VoxelMaterialRegistry.areAbilitiesDisabled();
        this.addRenderableWidget(
                Button.builder(abilitiesLabel(off), b -> {
                    boolean now = !VoxelMaterialRegistry.areAbilitiesDisabled();
                    VoxelMaterialRegistry.setAbilitiesDisabled(now);
                    b.setMessage(abilitiesLabel(now));
                })
                .bounds(x, LIST_TOP + 96, w, 20)
                .tooltip(Tooltip.create(Component.literal("When disabled, modular weapons behave as plain weapons: no sword or bow abilities trigger.")))
                .build()
        );
    }

    private static Component abilitiesLabel(boolean disabled) {
        return Component.literal("Weapon Abilities: " + (disabled ? "§cDisabled" : "§aEnabled"));
    }

    private static class GridSizeSlider extends AbstractSliderButton {
        GridSizeSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), indexOf(VoxelMaterialRegistry.getGridSize()) / 2.0);
            updateMessage();
        }

        private static int indexOf(int size) {
            int best = 0;
            for (int i = 0; i < GRID_SIZES.length; i++) {
                if (Math.abs(GRID_SIZES[i] - size) < Math.abs(GRID_SIZES[best] - size)) best = i;
            }
            return best;
        }

        @Override
        protected void updateMessage() {
            int size = GRID_SIZES[indexOf2(this.value)];
            this.setMessage(Component.literal("Grid Resolution: " + size + "x" + size));
        }

        private static int indexOf2(double v) {
            return (int) Math.max(0, Math.min(2, Math.round(v * 2.0)));
        }

        @Override
        protected void applyValue() {
            int idx = indexOf2(this.value);
            this.value = idx / 2.0; // snap
            int size = GRID_SIZES[idx];
            if (size != VoxelMaterialRegistry.getGridSize()) {
                VoxelMaterialRegistry.setGridSize(size);
            }
            updateMessage();
        }
    }

    // ---------- Add Material tab ----------

    private void initAddForm(int centerX, int btnY, int btnW, int btnGap) {
        int formLeft = centerX - FORM_WIDTH / 2;
        int r1 = LIST_TOP + 14;
        int r2 = LIST_TOP + 50;
        int r3 = LIST_TOP + 88;
        int statW = 74, statGap = 9;

        formBoxes[0] = formBox(formLeft, r1, 95, "Material ID", fId, v -> fId = v);
        formBoxes[1] = formBox(formLeft + 103, r1, 137, "Item ID", fItem, v -> fItem = v);
        formBoxes[2] = formBox(formLeft, r2, statW, "Damage", fDmg, v -> fDmg = v);
        formBoxes[3] = formBox(formLeft + statW + statGap, r2, statW, "Speed", fSpd, v -> fSpd = v);
        formBoxes[4] = formBox(formLeft + (statW + statGap) * 2, r2, statW, "Durability", fDur, v -> fDur = v);
        for (int i = 0; i < 5; i++) {
            final int si = i;
            formBoxes[5 + i] = formBox(formLeft + i * 49, r3, 44, "Shade " + i, fShade[i], v -> fShade[si] = v);
            formBoxes[5 + i].setMaxLength(7);
        }

        this.addRenderableWidget(
                Button.builder(Component.literal("Create"), unused25 -> createMaterial())
                        .bounds(centerX - btnW - btnGap / 2, btnY, btnW, 20).build()
        );
        this.addRenderableWidget(
                Button.builder(Component.literal("Done"), unused26 -> this.onClose())
                        .bounds(centerX + btnGap / 2, btnY, btnW, 20).build()
        );
    }

    private EditBox formBox(int x, int y, int w, String label, String value, java.util.function.Consumer<String> sink) {
        EditBox box = new EditBox(this.font, x, y, w, 16, Component.literal(label));
        box.setTextColor(0xFFFFFFFF);
        box.setValue(value);
        box.setResponder(sink);
        this.addRenderableWidget(box);
        return box;
    }

    private void createMaterial() {
        try {
            String id = fId.trim().toLowerCase();
            if (id.isEmpty()) throw new IllegalArgumentException("Material ID cannot be empty!");
            for (VoxelMaterial m : VoxelMaterialRegistry.getAll()) {
                if (m.id().equalsIgnoreCase(id)) throw new IllegalArgumentException("Material '" + id + "' already exists!");
            }

            String itemStr = fItem.trim();
            if (!itemStr.contains(":")) itemStr = "minecraft:" + itemStr;
            String iconItem = McCompat.checkId(itemStr);

            float dmg = Float.parseFloat(fDmg.trim());
            float spd = Float.parseFloat(fSpd.trim());
            int dur = Integer.parseInt(fDur.trim());

            int[] palette = new int[5];
            for (int i = 0; i < 5; i++) {
                palette[i] = 0xFF000000 | Integer.parseInt(fShade[i].trim().replace("#", ""), 16);
            }

            VoxelMaterialRegistry.register(new VoxelMaterial(id, dmg, spd, dur, palette, iconItem));
            VoxelMaterialRegistry.save();

            // Reset form and jump to the list, scrolled to the new entry
            fId = "";
            formError = "";
            tab = TAB_MATERIALS;
            scrollY = Integer.MAX_VALUE; // clamped on render
            this.rebuildWidgets();
        } catch (Exception e) {
            formError = "§cError: " + e.getMessage();
        }
    }

    @Override
    public void onClose() {
        InputCompat.openScreen(this.minecraft, this.parent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        List<VoxelMaterial> mats = new ArrayList<>(VoxelMaterialRegistry.getAll());
        int listTopY = LIST_TOP;
        int listBottomY = listBottom();
        int listHeight = listBottomY - listTopY;
        int totalContentHeight = mats.size() * ROW_SPACING;
        int maxScroll = Math.max(0, totalContentHeight - listHeight);

        if (tab == TAB_MATERIALS && maxScroll > 0 && verticalAmount != 0) {
            this.scrollY = (int) Math.clamp(this.scrollY - (verticalAmount * 18), 0, maxScroll);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    protected void renderScreen(Gfx guiGraphics, int mouseX, int mouseY, float delta) {
        int centerX = this.width / 2;
        int cardX = centerX - (CARD_WIDTH / 2);

        int listTopY = LIST_TOP;
        int listBottomY = listBottom();
        int listHeight = listBottomY - listTopY;

        List<VoxelMaterial> mats = new ArrayList<>(VoxelMaterialRegistry.getAll());
        int totalContentHeight = mats.size() * ROW_SPACING;
        int maxScroll = Math.max(0, totalContentHeight - listHeight);
        this.scrollY = Math.clamp(this.scrollY, 0, maxScroll);

        // Render centered scrollable list
        if (tab == TAB_MATERIALS) guiGraphics.enableScissor(cardX - 4, listTopY, cardX + CARD_WIDTH + 14, listBottomY);

        for (int i = 0; tab == TAB_MATERIALS && i < mats.size(); i++) {
            VoxelMaterial mat = mats.get(i);
            int y = listTopY - scrollY + (i * ROW_SPACING);

            if (y + ROW_HEIGHT < listTopY - 10 || y > listBottomY + 10) continue;

            // Row container
            guiGraphics.fill(cardX, y, cardX + CARD_WIDTH, y + ROW_HEIGHT, 0x55000000);
            guiGraphics.fill(cardX, y, cardX + CARD_WIDTH, y + 1, 0x33FFFFFF);
            guiGraphics.fill(cardX, y + ROW_HEIGHT - 1, cardX + CARD_WIDTH, y + ROW_HEIGHT, 0x33000000);

            // Column 1: Name
            String name = mat.id().substring(0, 1).toUpperCase() + mat.id().substring(1);
            guiGraphics.text(this.font, Component.literal("§e" + name), cardX + 6, y + 4, 0xFFFFFFFF, false);
            guiGraphics.text(this.font, Component.literal("§7" + mat.iconItem().substring(mat.iconItem().indexOf(':') + 1)), cardX + 6, y + 15, 0xFFAAAAAA, false);

            // Column 2: Damage
            String dmgSign = mat.getBonusDamage() > 0 ? "+" : "";
            guiGraphics.text(this.font, Component.literal("§c" + dmgSign + mat.getBonusDamage() + " Dmg"), cardX + 108, y + 10, 0xFFFFFFFF, false);

            // Column 3: Speed
            String spdSign = mat.getBonusSpeed() > 0 ? "+" : "";
            guiGraphics.text(this.font, Component.literal("§b" + spdSign + mat.getBonusSpeed() + " Spd"), cardX + 162, y + 10, 0xFFFFFFFF, false);

            // Column 4: Durability
            String durSign = mat.getBonusDurability() > 0 ? "+" : "";
            guiGraphics.text(this.font, Component.literal("§a" + durSign + mat.getBonusDurability() + " Dur"), cardX + 222, y + 10, 0xFFFFFFFF, false);

            // Column 5: 5-Shade Swatches
            int[] palette = mat.getPalette();
            if (palette != null) {
                int swatchStartX = cardX + 278;
                for (int s = 0; s < palette.length; s++) {
                    int swX = swatchStartX + (s * 9);
                    int swY = y + 8;
                    guiGraphics.fill(swX - 1, swY - 1, swX + 8, swY + 13, 0xFF000000);
                    guiGraphics.fill(swX, swY, swX + 7, swY + 12, palette[s] | 0xFF000000);
                }
            }

            // Column 6: Edit Button
            int editBtnW = 36;
            int editBtnH = 18;
            int editBtnX = cardX + CARD_WIDTH - editBtnW - 4;
            int editBtnY = y + 5;
            boolean hoveredEdit = mouseX >= editBtnX && mouseX <= editBtnX + editBtnW && mouseY >= editBtnY && mouseY <= editBtnY + editBtnH && mouseY >= listTopY && mouseY <= listBottomY;

            drawEditButton(guiGraphics, editBtnX, editBtnY, editBtnW, editBtnH, hoveredEdit);
        }

        // Scrollbar
        if (tab == TAB_MATERIALS && maxScroll > 0) {
            int scrollbarX = cardX + CARD_WIDTH + 4;
            guiGraphics.fill(scrollbarX, listTopY, scrollbarX + 6, listBottomY, 0x88000000);

            int thumbHeight = Math.max(16, (int) ((float) listHeight / totalContentHeight * listHeight));
            int thumbY = listTopY + (int) ((float) scrollY / maxScroll * (listHeight - thumbHeight));
            boolean hoveredThumb = mouseX >= scrollbarX && mouseX <= scrollbarX + 6 && mouseY >= thumbY && mouseY <= thumbY + thumbHeight;

            guiGraphics.fill(scrollbarX, thumbY, scrollbarX + 6, thumbY + thumbHeight, (hoveredThumb || isScrolling) ? 0xFFCCCCCC : 0xFF888888);
            guiGraphics.fill(scrollbarX + 1, thumbY + 1, scrollbarX + 5, thumbY + thumbHeight - 1, (hoveredThumb || isScrolling) ? 0xFFFFFFFF : 0xFFAAAAAA);
        }

        if (tab == TAB_MATERIALS) guiGraphics.disableScissor();

        // Fixed Header Backdrop
        guiGraphics.fill(0, 0, this.width, 32, 0xD0000000);
        guiGraphics.fill(0, 32, this.width, 33, 0x55FFFFFF);
        guiGraphics.text(this.font, this.title, centerX - (this.font.width(this.title) / 2), 8, 0xFFFFFFFF, true);
        Component sub = Component.literal(tab == TAB_MATERIALS ? "Browse and edit material stats, palettes and items" : tab == TAB_ADD ? "Create a new custom material" : "Global mod settings");
        guiGraphics.text(this.font, sub, centerX - (this.font.width(sub) / 2), 20, 0xFFAAAAAA, false);

        // Fixed Footer Backdrop
        guiGraphics.fill(0, this.height - 30, this.width, this.height, 0xD0000000);
        guiGraphics.fill(0, this.height - 30, this.width, this.height - 29, 0x55FFFFFF);

        // Tab strip backdrop
        guiGraphics.fill(0, TAB_Y - 5, this.width, TAB_Y + 25, 0x90000000);
        guiGraphics.fill(0, TAB_Y + 25, this.width, TAB_Y + 26, 0x55FFFFFF);

        if (tab == TAB_ADD) drawAddLabels(guiGraphics, centerX);
        if (tab == TAB_CONFIG) drawConfigLabels(guiGraphics, centerX);

        // Render widgets
        renderVanilla(guiGraphics, mouseX, mouseY, delta);
    }

    @Override
    protected boolean onMouseClicked(InputEvents.Mouse event) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();

        int centerX = this.width / 2;
        int cardX = centerX - (CARD_WIDTH / 2);
        int listTopY = LIST_TOP;
        int listBottomY = listBottom();

        if (tab != TAB_MATERIALS) return vanillaMouseClicked(event);

        int scrollbarX = cardX + CARD_WIDTH + 4;
        if (mouseX >= scrollbarX && mouseX <= scrollbarX + 8 && mouseY >= listTopY && mouseY <= listBottomY) {
            this.isScrolling = true;
            return true;
        }

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
                    InputCompat.openScreen(this.minecraft, new EditMaterialScreen(this, mats.get(i), false));
                    return true;
                }
            }
        }

        return vanillaMouseClicked(event);
    }

    @Override
    protected boolean onMouseReleased(InputEvents.Mouse event) {
        this.isScrolling = false;
        return vanillaMouseReleased(event);
    }

    @Override
    protected boolean onMouseDragged(InputEvents.Mouse event, double deltaX, double deltaY) {
        if (tab == TAB_MATERIALS && this.isScrolling) {
            int listTopY = LIST_TOP;
            int listBottomY = listBottom();
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

        return vanillaMouseDragged(event, deltaX, deltaY);
    }

    private void label(Gfx g, String text, int x, int w, int y, int color) {
        Component c = Component.literal(text);
        g.text(this.font, c, x + (w - this.font.width(c)) / 2, y, color, true);
    }

    private void drawAddLabels(Gfx g, int centerX) {
        int formLeft = centerX - FORM_WIDTH / 2;
        int r1 = LIST_TOP + 14, r2 = LIST_TOP + 50, r3 = LIST_TOP + 88;
        int statW = 74, statGap = 9;
        label(g, "Material ID", formLeft, 95, r1 - 12, 0xFFEEEEEE);
        label(g, "Item Identifier", formLeft + 103, 137, r1 - 12, 0xFFEEEEEE);
        label(g, "Attack Dmg", formLeft, statW, r2 - 12, 0xFFFF7777);
        label(g, "Attack Spd", formLeft + statW + statGap, statW, r2 - 12, 0xFF77FFFF);
        label(g, "Durability", formLeft + (statW + statGap) * 2, statW, r2 - 12, 0xFF77FF77);
        label(g, "5-Shade Color Palette (Hex)", formLeft, FORM_WIDTH, r3 - 12, 0xFFEEEEEE);
        for (int i = 0; i < 5; i++) {
            int colX = formLeft + i * 49;
            int color = 0xFF888888;
            try {
                color = 0xFF000000 | Integer.parseInt(fShade[i].trim().replace("#", ""), 16);
            } catch (Exception ignored) {}
            g.fill(colX - 1, r3 + 19, colX + 45, r3 + 31, 0xFF000000);
            g.fill(colX, r3 + 20, colX + 44, r3 + 30, color);
        }
        if (!formError.isEmpty()) {
            Component err = Component.literal(formError);
            g.text(this.font, err, centerX - this.font.width(err) / 2, this.height - 46, 0xFFFF5555, true);
        }
    }

    private void drawConfigLabels(Gfx g, int centerX) {
        int x = centerX - 120;
        g.text(this.font, Component.literal("Grid Resolution"), x, LIST_TOP + 8, 0xFFEEEEEE, true);
        // snap marks under the slider
        for (int i = 0; i < 3; i++) {
            int mx = x + 4 + (int) ((240 - 8) * (i / 2.0));
            g.fill(mx, LIST_TOP + 44, mx + 1, LIST_TOP + 48, 0xFFAAAAAA);
            String t = String.valueOf(GRID_SIZES[i]);
            g.text(this.font, Component.literal(t), mx - this.font.width(t) / 2, LIST_TOP + 50, 0xFFAAAAAA, false);
        }
        Component warn = Component.literal("§cChanging grid resolution requires a server and client restart.");
        g.text(this.font, warn, centerX - this.font.width(warn) / 2, LIST_TOP + 62, 0xFFFF5555, false);
        g.text(this.font, Component.literal("Combat"), x, LIST_TOP + 84, 0xFFEEEEEE, true);
        Component hint = Component.literal("Disables all sword and bow abilities; weapons keep their stats.");
        g.text(this.font, hint, centerX - this.font.width(hint) / 2, LIST_TOP + 120, 0xFFFFFFFF, false);
    }

    private void drawEditButton(Gfx g, int x, int y, int w, int h, boolean hovered) {
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