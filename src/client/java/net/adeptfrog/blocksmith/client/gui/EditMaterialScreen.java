package net.adeptfrog.blocksmith.client.gui;

import net.adeptfrog.blocksmith.data.VoxelMaterial;
import net.adeptfrog.blocksmith.data.VoxelMaterialRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public class EditMaterialScreen extends Screen {
    private final Screen parent;
    private final VoxelMaterial original;
    private final boolean isNew;

    private EditBox idBox;
    private EditBox itemBox;
    private EditBox damageBox;
    private EditBox speedBox;
    private EditBox durabilityBox;

    private final EditBox[] shadeBoxes = new EditBox[5];
    private String errorMessage = "";

    private static final int FORM_WIDTH = 240;

    public EditMaterialScreen(Screen parent, VoxelMaterial material, boolean isNew) {
        super(Component.literal(isNew ? "Create New Material" : ("Edit Material: " + material.id())));
        this.parent = parent;
        this.original = material;
        this.isNew = isNew;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int formLeft = centerX - (FORM_WIDTH / 2);

        int row1Y = 38;
        this.idBox = createStyledEditBox(formLeft, row1Y, 95, "Material ID", isNew);
        this.idBox.setValue(original.id());
        this.idBox.setEditable(isNew);
        this.addRenderableWidget(idBox);

        this.itemBox = createStyledEditBox(formLeft + 103, row1Y, 137, "Item ID", true);
        this.itemBox.setValue(original.iconItem().toString());
        this.addRenderableWidget(itemBox);

        int row2Y = 74;
        int statW = 74;
        int statGap = 9;

        this.damageBox = createStyledEditBox(formLeft, row2Y, statW, "Damage", true);
        this.damageBox.setValue(String.valueOf(original.bonusDamage()));
        this.addRenderableWidget(damageBox);

        this.speedBox = createStyledEditBox(formLeft + statW + statGap, row2Y, statW, "Speed", true);
        this.speedBox.setValue(String.valueOf(original.bonusSpeed()));
        this.addRenderableWidget(speedBox);

        this.durabilityBox = createStyledEditBox(formLeft + (statW + statGap) * 2, row2Y, statW, "Durability", true);
        this.durabilityBox.setValue(String.valueOf(original.bonusDurability()));
        this.addRenderableWidget(durabilityBox);

        int row3Y = 114;
        int[] palette = original.getPalette();

        for (int i = 0; i < 5; i++) {
            int colX = formLeft + (i * 49);
            int rgb = (palette != null && i < palette.length) ? (palette[i] & 0xFFFFFF) : 0xFFFFFF;
            String hex = String.format("#%06X", rgb);

            shadeBoxes[i] = createStyledEditBox(colX, row3Y, 44, "Shade " + i, true);
            shadeBoxes[i].setMaxLength(7);
            shadeBoxes[i].setValue(hex);
            this.addRenderableWidget(shadeBoxes[i]);
        }

        // --- Bottom Action Buttons ---
        int btnY = this.height - 28;

        if (isNew) {
            this.addRenderableWidget(
                    Button.builder(Component.literal("Create"), _ -> saveAndClose())
                            .bounds(centerX - 80, btnY, 75, 20).build()
            );
            this.addRenderableWidget(
                    Button.builder(Component.literal("Cancel"), _ ->
                            this.minecraft.setScreen(this.parent)).bounds(centerX + 5, btnY, 75, 20).build()
            );
        } else {
            this.addRenderableWidget(
                    Button.builder(Component.literal("Save"), _ -> saveAndClose())
                            .bounds(centerX - 110, btnY, 68, 20).build()
            );
            this.addRenderableWidget(
                    Button.builder(Component.literal("§cDelete"), _ -> {
                        VoxelMaterialRegistry.remove(original.id());
                        this.minecraft.setScreen(this.parent);
                    }).bounds(centerX - 34, btnY, 68, 20).build()
            );
            this.addRenderableWidget(
                    Button.builder(Component.literal("Cancel"), _ ->
                            this.minecraft.setScreen(this.parent)).bounds(centerX + 42, btnY, 68, 20).build()
            );
        }
    }

    private EditBox createStyledEditBox(int x, int y, int w, String label, boolean editable) {
        EditBox box = new EditBox(this.font, x, y, w, 16, Component.literal(label));
        if (editable) {
            box.setTextColor(0xFFFFFFFF);
            box.setTextColorUneditable(0xFFFFFFFF);
        } else {
            box.setTextColor(0xFF777777);
            box.setTextColorUneditable(0xFF777777);
        }
        return box;
    }

    private void saveAndClose() {
        try {
            String id = idBox.getValue().trim().toLowerCase();
            if (id.isEmpty()) throw new IllegalArgumentException("Material ID cannot be empty!");

            String itemStr = itemBox.getValue().trim();
            if (!itemStr.contains(":")) itemStr = "minecraft:" + itemStr;
            Identifier iconItem = Identifier.parse(itemStr);

            float dmg = Float.parseFloat(damageBox.getValue().trim());
            float spd = Float.parseFloat(speedBox.getValue().trim());
            int dur = Integer.parseInt(durabilityBox.getValue().trim());

            int[] palette = new int[5];
            for (int i = 0; i < 5; i++) {
                String hex = shadeBoxes[i].getValue().trim().replace("#", "");
                palette[i] = 0xFF000000 | Integer.parseInt(hex, 16);
            }

            VoxelMaterial updated = new VoxelMaterial(id, dmg, spd, dur, palette, iconItem);
            VoxelMaterialRegistry.register(updated);
            VoxelMaterialRegistry.save();

            this.minecraft.setScreen(this.parent);
        } catch (Exception e) {
            this.errorMessage = "§cError: " + e.getMessage();
        }
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int formLeft = centerX - (FORM_WIDTH / 2);

        guiGraphics.text(this.font, this.title, centerX - (this.font.width(this.title) / 2), 10, 0xFFFFFFFF, true);

        Component idLabel = Component.literal("Material ID");
        int idLabelX = formLeft + (95 - this.font.width(idLabel)) / 2;
        guiGraphics.text(this.font, idLabel, idLabelX, 26, isNew ? 0xFFEEEEEE : 0xFFAAAAAA, true);

        Component itemLabel = Component.literal("Item Identifier");
        int itemLabelX = (formLeft + 103) + (137 - this.font.width(itemLabel)) / 2;
        guiGraphics.text(this.font, itemLabel, itemLabelX, 26, 0xFFEEEEEE, true);

        int statW = 74;
        int statGap = 9;

        Component dmgLabel = Component.literal("Attack Dmg");
        int dmgLabelX = formLeft + (statW - this.font.width(dmgLabel)) / 2;
        guiGraphics.text(this.font, dmgLabel, dmgLabelX, 62, 0xFFFF7777, true);

        Component spdLabel = Component.literal("Attack Spd");
        int spdLabelX = (formLeft + statW + statGap) + (statW - this.font.width(spdLabel)) / 2;
        guiGraphics.text(this.font, spdLabel, spdLabelX, 62, 0xFF77FFFF, true);

        Component durLabel = Component.literal("Durability");
        int durLabelX = (formLeft + (statW + statGap) * 2) + (statW - this.font.width(durLabel)) / 2;
        guiGraphics.text(this.font, durLabel, durLabelX, 62, 0xFF77FF77, true);

        Component paletteTitle = Component.literal("5-Shade Color Palette (Hex)");
        guiGraphics.text(this.font, paletteTitle, centerX - (this.font.width(paletteTitle) / 2), 100, 0xFFEEEEEE, true);

        int swatchY = 133;
        int swatchHeight = 10;

        for (int i = 0; i < 5; i++) {
            int colX = formLeft + (i * 49);

            int color = 0xFF888888;
            try {
                String hex = shadeBoxes[i].getValue().trim().replace("#", "");
                color = 0xFF000000 | Integer.parseInt(hex, 16);
            } catch (Exception ignored) {}

            guiGraphics.fill(colX - 1, swatchY - 1, colX + 45, swatchY + swatchHeight + 1, 0xFF000000);
            guiGraphics.fill(colX, swatchY, colX + 44, swatchY + swatchHeight, color);
        }

        // Error message if input validation fails
        if (!errorMessage.isEmpty()) {
            Component err = Component.literal(errorMessage);
            guiGraphics.text(this.font, err, centerX - (this.font.width(err) / 2), this.height - 46, 0xFFFF5555, true);
        }
    }
}