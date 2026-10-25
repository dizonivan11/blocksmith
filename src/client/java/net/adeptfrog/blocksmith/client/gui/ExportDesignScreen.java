package net.adeptfrog.blocksmith.client.gui;

import net.adeptfrog.blocksmith.client.compat.ClientCompat;
import net.adeptfrog.blocksmith.client.compat.Gfx;
import net.adeptfrog.blocksmith.client.compat.CompatScreen;
import net.adeptfrog.blocksmith.client.utils.InputCompat;
import net.adeptfrog.blocksmith.client.utils.StringUtils;
import net.adeptfrog.blocksmith.data.VoxelDesignSerializer;
import net.adeptfrog.blocksmith.data.DesignFileManager;
import net.adeptfrog.blocksmith.data.WeaponOffset;
import net.adeptfrog.blocksmith.data.WeaponVoxel;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.adeptfrog.blocksmith.data.DesignFileManager.DesignEntry;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;

public class ExportDesignScreen extends CompatScreen {
    private final CustomizationScreen parent;
    private final List<WeaponVoxel> voxels;
    private final WeaponOffset offset;
    private final int gridSize;
    private final String fileName;
    private final String crafter;
    private final String attribution;

    private EditBox nameBox;
    private EditBox crafterBox;
    private MultiLineEditBox attributionBox;
    private boolean overwriteConfirmed = false;
    private String errorMessage = "";

    public ExportDesignScreen(CustomizationScreen parent, DesignEntry entry, int gridSize) {
        super(Component.literal("Export Blueprint"));
        this.parent = parent;
        this.voxels = entry.voxels();
        this.offset = entry.offset();
        this.fileName = StringUtils.rawFileName(entry.fileName());
        this.crafter = entry.crafter();
        this.attribution = entry.attribution();
        this.gridSize = gridSize;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // File Name
        this.nameBox = new EditBox(this.font, centerX - 100, centerY - 54, 200, 16, Component.literal("File Name"));
        this.nameBox.setValue(Objects.equals(this.fileName, "") ? "custom_design" : this.fileName);
        this.nameBox.setTextColor(0xFFFFFFFF);
        this.addRenderableWidget(this.nameBox);

        // Crafter
        String playerName = this.minecraft.player != null ? ClientCompat.playerName(this.minecraft.player) : "";
        this.crafterBox = new EditBox(this.font, centerX - 100, centerY - 20, 200, 16, Component.literal("Crafter"));
        this.crafterBox.setValue(Objects.equals(this.crafter, "") ? playerName : this.crafter);
        this.crafterBox.setTextColor(0xFFFFFFFF);
        this.addRenderableWidget(this.crafterBox);

        // Attribution
        String cc4Template = "\"Work Title\" by Author [Source](https://minecraft.net), "+
                "licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/deed.en).";
        this.attributionBox = ClientCompat.multiLineEditBox(this.font, 0, 0, 200, 64, Component.literal("Attribution"));
        this.attributionBox.setPosition(centerX - 100, centerY + 14);
        this.attributionBox.setValue(Objects.equals(this.attribution, "") ? cc4Template :  this.attribution);
        this.addRenderableWidget(this.attributionBox);

        // Action buttons
        int btnY = centerY + 86;
        this.addRenderableWidget(Button.builder(Component.literal("Export"), unused27 -> exportBlueprint()).bounds(centerX - 100, btnY, 96, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), unused28 -> this.onClose()).bounds(centerX + 4, btnY, 96, 20).build());
    }

    private void exportBlueprint() {
        String rawName = nameBox.getValue().trim();
        String cleanName = rawName.replaceAll("[^a-zA-Z0-9_-]", "_");

        if (cleanName.isEmpty()) {
            this.errorMessage = "§cName cannot be empty!";
            return;
        }

        try {
            // Save .json file directly into .minecraft/blocksmith_blueprints/
            DesignFileManager.ensureDirectory();
            File file = new File(DesignFileManager.BLUEPRINTS_DIR, cleanName + ".json");

            if (file.exists() && !this.overwriteConfirmed) {
                this.errorMessage = "§eFile already exists! Click Export again to overwrite.";
                this.overwriteConfirmed = true;
                return;
            }

            String json = VoxelDesignSerializer.exportToJson(
                    this.voxels, this.offset, this.gridSize,
                    attributionBox.getValue(), crafterBox.getValue()
            );
            Files.writeString(file.toPath(), json);

            this.parent.triggerExportFeedback();
            this.onClose();
        } catch (Exception e) {
            this.errorMessage = "§cError: " + e.getMessage();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        InputCompat.openScreen(this.minecraft, this.parent);
    }

    @Override
    protected void renderScreen(Gfx guiGraphics, int mouseX, int mouseY, float delta) {
        renderVanilla(guiGraphics, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Screen Title
        guiGraphics.text(this.font, this.title, centerX - (this.font.width(this.title) / 2), centerY - 82, 0xFFFFFFFF, true);

        // Labels placed directly above inputs
        guiGraphics.text(this.font, Component.literal("File Name"), centerX - 100, centerY - 66, 0xFFAAAAAA, true);
        guiGraphics.text(this.font, Component.literal("Crafter"), centerX - 100, centerY - 32, 0xFFAAAAAA, true);
        guiGraphics.text(this.font, Component.literal("Attribution"), centerX - 100, centerY + 2, 0xFFAAAAAA, true);

        // Error message if saving fails
        if (!errorMessage.isEmpty()) {
            guiGraphics.text(this.font, Component.literal(errorMessage), centerX - (this.font.width(errorMessage) / 2), centerY + 112, 0xFFFF5555, true);
        }
    }
}