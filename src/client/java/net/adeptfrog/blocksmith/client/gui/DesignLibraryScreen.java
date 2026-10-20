package net.adeptfrog.blocksmith.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.adeptfrog.blocksmith.client.utils.StringUtils;
import net.adeptfrog.blocksmith.data.DesignFileManager;
import net.adeptfrog.blocksmith.data.EditorGeometry;
import net.adeptfrog.blocksmith.data.ImageVoxelizer;
import net.adeptfrog.blocksmith.data.WeaponVoxel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class DesignLibraryScreen extends Screen {
    private final CustomizationScreen parent;
    private static final int CARD_W = 92;
    private static final int CARD_H = 92;
    private static final int CARD_GAP = 6;
    private static final int THUMB_SIZE = 64;
    private static final int SLOTS_PER_ROW = 10;
    private static final int SIDEBAR_W = 140;
    private static final int LARGE_THUMB_SIZE = 128;
    private static final int ATLAS_SIZE = 1024;

    private int currentPage = 0;
    private int cardsPerPage = 8;
    private int maxPages = 1;

    private DesignFileManager.DesignEntry selectedEntry = null;
    private DynamicTexture atlasTexture;
    private NativeImage atlasImage;
    private Identifier atlasTextureId;

    private List<DesignFileManager.DesignEntry> allEntries = new ArrayList<>(); // everything in the library
    private List<DesignFileManager.DesignEntry> entries = new ArrayList<>();    // what the folder filter lets through

    // Folder filter: tick any number of folders to show only those; nothing ticked shows everything
    private static final Set<String> FOLDER_FILTER = new LinkedHashSet<>();
    private static final int FP_X = 8, FP_Y = 27, FP_W = 190, FP_ROW = 12, FP_MAX_ROWS = 12;
    private boolean folderPanelOpen = false;
    private int folderScroll = 0;
    private Button folderButton;

    // Picture import options, shown in the sidebar while a picture is selected
    private final List<Button> optionButtons = new ArrayList<>();
    private Button thicknessButton, profileButton, ditherButton, fitButton;

    public record LinkBox(int minX, int minY, int maxX, int maxY, String url) {}
    private final List<LinkBox> activeLinks = new ArrayList<>();

    public DesignLibraryScreen(CustomizationScreen parent) {
        super(Component.literal("Blueprint Library"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.allEntries = DesignFileManager.loadAllDesigns(this.parent.gridSize, false);
        filterEntries();

        int sidebarX = this.width - SIDEBAR_W - 8;
        int galleryAreaW = sidebarX - 16;
        int listHeight = (this.height - 30) - 34;

        // Calculate exact columns and rows per page
        int columns = Math.max(1, (galleryAreaW - 10) / (CARD_W + CARD_GAP));
        int rows = Math.max(1, listHeight / (CARD_H + CARD_GAP));
        this.cardsPerPage = Math.max(1, columns * rows);
        this.maxPages = Math.max(1, (int) Math.ceil((double) entries.size() / cardsPerPage));
        this.currentPage = Math.clamp(this.currentPage, 0, maxPages - 1);

        // Initialize Atlas
        if (this.atlasImage == null) {
            this.atlasImage = new NativeImage(ATLAS_SIZE, ATLAS_SIZE, true);
            this.atlasTexture = new DynamicTexture(() -> "blocksmith_library_atlas", this.atlasImage);
            this.atlasTextureId = Identifier.fromNamespaceAndPath("blocksmith", "atlas_" + System.identityHashCode(this));
            Minecraft.getInstance().getTextureManager().register(this.atlasTextureId, this.atlasTexture);
        }

        bakeThumbnailAtlas();

        int centerX = this.width / 2;
        int bottomY = this.height - 24;

        // Bottom Action & Pagination Buttons

        // Open Folder
        this.addRenderableWidget(Button.builder(Component.literal("Open Folder"), _ -> {
            DesignFileManager.ensureDirectory();
            Util.getPlatform().openFile(DesignFileManager.BLUEPRINTS_DIR);
        }).bounds(centerX - 170, bottomY, 80, 20).build());

        // Refresh
        this.addRenderableWidget(Button.builder(Component.literal("Refresh"), _ -> {
            this.allEntries = DesignFileManager.loadAllDesigns(this.parent.gridSize, true);
            filterEntries();
            this.maxPages = Math.max(1, (int) Math.ceil((double) entries.size() / cardsPerPage));
            this.currentPage = Math.clamp(this.currentPage, 0, maxPages - 1);
            bakeThumbnailAtlas();
        }).bounds(centerX - 85, bottomY, 55, 20).build());

        // Previous Page Button
        this.addRenderableWidget(Button.builder(Component.literal("<"), _ -> {
            if (this.currentPage > 0) {
                this.currentPage--;
                bakeThumbnailAtlas();
            }
        }).bounds(centerX - 25, bottomY, 20, 20).build());

        // Next Page Button
        this.addRenderableWidget(Button.builder(Component.literal(">"), _ -> {
            if (this.currentPage < this.maxPages - 1 && (this.currentPage + 1) * this.cardsPerPage < entries.size()) {
                this.currentPage++;
                bakeThumbnailAtlas();
            }
        }).bounds(centerX + 35, bottomY, 20, 20).build());

        // Cancel
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), _ -> this.onClose())
                .bounds(centerX + 60, bottomY, 60, 20).build());

        // Folder filter (top left, opens a list of folders with check boxes)
        this.folderButton = Button.builder(Component.empty(), _ -> {
            this.folderPanelOpen = !this.folderPanelOpen;
            this.folderScroll = 0;
        }).bounds(FP_X, 5, 100, 20).build();
        this.addRenderableWidget(this.folderButton);
        refreshFolderButton();

        // Picture import options (bottom of the sidebar, above the Load button)
        this.optionButtons.clear();
        int optY = (this.height - 30) - 24 - 34;
        int optX = sidebarX + 6;
        this.thicknessButton = Button.builder(Component.empty(), _ -> {
            ImageVoxelizer.Options o = DesignFileManager.getImageOptions();
            int next = o.thickness() >= 9 ? 1 : o.thickness() + 2;
            applyImageOptions(o.withThickness(next));
        }).bounds(optX, optY, 63, 14).build();
        this.profileButton = Button.builder(Component.empty(), _ -> {
            ImageVoxelizer.Options o = DesignFileManager.getImageOptions();
            applyImageOptions(o.withProfile((o.profile() + 1) % EditorGeometry.PROFILE_NAMES.length));
        }).bounds(optX + 65, optY, 63, 14).build();
        this.ditherButton = Button.builder(Component.empty(), _ -> {
            ImageVoxelizer.Options o = DesignFileManager.getImageOptions();
            applyImageOptions(o.withDither(!o.dither()));
        }).bounds(optX, optY + 16, 63, 14).build();
        this.fitButton = Button.builder(Component.empty(), _ -> {
            ImageVoxelizer.Options o = DesignFileManager.getImageOptions();
            applyImageOptions(o.withStretch(!o.stretch()));
        }).bounds(optX + 65, optY + 16, 63, 14).build();
        for (Button b : List.of(this.thicknessButton, this.profileButton, this.ditherButton, this.fitButton)) {
            this.optionButtons.add(b);
            this.addRenderableWidget(b);
        }
        refreshOptionLabels();
    }

    /** Distinct folders of the library, the top level ("") first. */
    private List<String> folderNames() {
        TreeSet<String> names = new TreeSet<>();
        for (DesignFileManager.DesignEntry e : this.allEntries) names.add(e.folder());
        return new ArrayList<>(names); // "" sorts first
    }

    private int countIn(String folder) {
        int n = 0;
        for (DesignFileManager.DesignEntry e : this.allEntries) if (e.folder().equals(folder)) n++;
        return n;
    }

    /** Rebuilds the visible list from the library and the ticked folders. */
    private void filterEntries() {
        FOLDER_FILTER.retainAll(folderNames()); // a folder that no longer exists cannot stay ticked
        if (FOLDER_FILTER.isEmpty()) {
            this.entries = new ArrayList<>(this.allEntries);
        } else {
            List<DesignFileManager.DesignEntry> kept = new ArrayList<>();
            for (DesignFileManager.DesignEntry e : this.allEntries) if (FOLDER_FILTER.contains(e.folder())) kept.add(e);
            this.entries = kept;
        }
        if (this.selectedEntry != null && !this.entries.contains(this.selectedEntry)) this.selectedEntry = null;
        if (this.folderButton != null) refreshFolderButton();
    }

    private void onFilterChanged() {
        filterEntries();
        this.currentPage = 0;
        this.maxPages = Math.max(1, (int) Math.ceil((double) entries.size() / cardsPerPage));
        bakeThumbnailAtlas();
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void refreshFolderButton() {
        boolean hasFolders = folderNames().stream().anyMatch(f -> !f.isEmpty());
        this.folderButton.active = hasFolders;
        this.folderButton.setMessage(Component.literal(FOLDER_FILTER.isEmpty() ? "Folders: All" : "Folders (" + FOLDER_FILTER.size() + ")"));
        if (!hasFolders) this.folderPanelOpen = false;
    }

    private static String folderLabel(String folder) {
        return folder.isEmpty() ? "Library root" : folder;
    }

    private int folderPanelRows() {
        return Math.min(FP_MAX_ROWS, folderNames().size() + 1);
    }

    private boolean inFolderPanel(int mouseX, int mouseY) {
        int h = folderPanelRows() * FP_ROW + 4;
        return mouseX >= FP_X && mouseX < FP_X + FP_W && mouseY >= FP_Y && mouseY < FP_Y + h;
    }

    private void drawFolderPanel(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        List<String> names = folderNames();
        int total = names.size() + 1;
        int visible = folderPanelRows();
        this.folderScroll = Math.clamp(this.folderScroll, 0, Math.max(0, total - visible));
        int h = visible * FP_ROW + 4;
        g.fill(FP_X - 1, FP_Y - 1, FP_X + FP_W + 1, FP_Y + h + 1, 0xFF000000);
        g.fill(FP_X, FP_Y, FP_X + FP_W, FP_Y + h, 0xF0202020);
        for (int r = 0; r < visible; r++) {
            int idx = r + this.folderScroll; // 0 is the "show everything" row
            int y = FP_Y + 2 + r * FP_ROW;
            boolean hov = mouseX >= FP_X && mouseX < FP_X + FP_W && mouseY >= y && mouseY < y + FP_ROW;
            if (hov) g.fill(FP_X + 1, y, FP_X + FP_W - 1, y + FP_ROW, 0x44FFFFFF);
            boolean checked;
            String label;
            if (idx == 0) {
                checked = FOLDER_FILTER.isEmpty();
                label = "All folders (" + this.allEntries.size() + ")";
            } else {
                String folder = names.get(idx - 1);
                checked = FOLDER_FILTER.contains(folder);
                label = folderLabel(folder) + " (" + countIn(folder) + ")";
            }
            int bx = FP_X + 4, by = y + 2;
            g.fill(bx, by, bx + 8, by + 8, 0xFF8B8B8B);
            g.fill(bx + 1, by + 1, bx + 7, by + 7, 0xFF101010);
            if (checked) g.fill(bx + 2, by + 2, bx + 6, by + 6, 0xFF55FF55);
            String shown = label;
            int maxW = FP_W - 22;
            if (this.font.width(shown) > maxW) {
                while (shown.length() > 1 && this.font.width("…" + shown) > maxW) shown = shown.substring(1);
                shown = "…" + shown;
            }
            g.text(this.font, Component.literal((idx == 0 ? "§e" : "§f") + shown), FP_X + 16, y + 2, 0xFFFFFFFF, false);
        }
        if (total > visible) {
            int trackH = h - 4;
            int thumbH = Math.max(8, trackH * visible / total);
            int thumbY = FP_Y + 2 + (trackH - thumbH) * this.folderScroll / (total - visible);
            g.fill(FP_X + FP_W - 4, thumbY, FP_X + FP_W - 2, thumbY + thumbH, 0xFFAAAAAA);
        }
    }

    /** Handles a click while the folder list is open; true when it was used. */
    private boolean clickFolderPanel(int mouseX, int mouseY) {
        if (!inFolderPanel(mouseX, mouseY)) return false;
        int r = (mouseY - FP_Y - 2) / FP_ROW;
        int idx = r + this.folderScroll;
        List<String> names = folderNames();
        if (r < 0 || idx > names.size()) return true;
        if (idx == 0) {
            FOLDER_FILTER.clear();
        } else {
            String folder = names.get(idx - 1);
            if (!FOLDER_FILTER.remove(folder)) FOLDER_FILTER.add(folder);
        }
        onFilterChanged();
        return true;
    }

    private void refreshOptionLabels() {
        ImageVoxelizer.Options o = DesignFileManager.getImageOptions();
        this.thicknessButton.setMessage(Component.literal("Layers: " + o.thickness()));
        this.profileButton.setMessage(Component.literal(EditorGeometry.PROFILE_NAMES[o.profile()]));
        this.ditherButton.setMessage(Component.literal("Dither: " + (o.dither() ? "On" : "Off")));
        this.fitButton.setMessage(Component.literal(o.stretch() ? "Stretch" : "Keep ratio"));
        this.profileButton.active = o.thickness() > 1;
    }

    /** Remembers new picture options and converts every picture in the library again with them. */
    private void applyImageOptions(ImageVoxelizer.Options options) {
        DesignFileManager.setImageOptions(options);
        refreshOptionLabels();
        String selectedName = this.selectedEntry != null ? this.selectedEntry.fileName() : null;
        this.allEntries = DesignFileManager.loadAllDesigns(this.parent.gridSize, true);
        filterEntries();
        this.maxPages = Math.max(1, (int) Math.ceil((double) entries.size() / cardsPerPage));
        this.currentPage = Math.clamp(this.currentPage, 0, maxPages - 1);
        this.selectedEntry = null;
        if (selectedName != null) {
            for (DesignFileManager.DesignEntry e : this.entries) {
                if (e.fileName().equals(selectedName)) this.selectedEntry = e;
            }
        }
        bakeThumbnailAtlas();
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.atlasTexture != null) {
            this.atlasTexture.close();
            this.atlasTexture = null;
        }
        if (this.atlasTextureId != null) {
            this.minecraft.getTextureManager().release(this.atlasTextureId);
        }
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.folderPanelOpen && inFolderPanel((int) mouseX, (int) mouseY)) {
            this.folderScroll -= (int) Math.signum(verticalAmount);
            return true;
        }
        if (verticalAmount != 0 && this.maxPages > 1) {
            if (verticalAmount < 0 && this.currentPage < this.maxPages - 1) {
                this.currentPage++;
                bakeThumbnailAtlas();
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            } else if (verticalAmount > 0 && this.currentPage > 0) {
                this.currentPage--;
                bakeThumbnailAtlas();
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
        int listTopY = 34;
        int listBottomY = this.height - 30;

        // Calculate available width for gallery grid and responsive columns
        int sidebarX = this.width - SIDEBAR_W - 8;
        int galleryAreaW = sidebarX - 16;
        int columns = Math.max(1, (galleryAreaW - 10) / (CARD_W + CARD_GAP));
        int galleryW = (columns * CARD_W) + ((columns - 1) * CARD_GAP);
        int galleryStartX = 8 + (galleryAreaW - galleryW) / 2;

        // Render gallery grid
        guiGraphics.enableScissor(4, listTopY, sidebarX - 6, listBottomY);

        if (entries.isEmpty() && !this.allEntries.isEmpty()) {
            guiGraphics.text(this.font, Component.literal("§7No designs in the ticked folders"), 20, listTopY + 20, 0xFFFFFFFF, true);
        } else if (entries.isEmpty()) {
            guiGraphics.text(this.font, Component.literal("§7No files found in .minecraft/blocksmith_blueprints/ (sub-folders work too)"), 20, listTopY + 20, 0xFFFFFFFF, true);
        }

        int maxGrid = Math.max(16, this.parent.gridSize);

        // Render current page cards
        int startIndex = this.currentPage * this.cardsPerPage;
        int count = 0;
        if (startIndex < entries.size()) {
            count = Math.min(this.cardsPerPage, entries.size() - startIndex);
        }

        for (int i = 0; i < count; i++) {
            DesignFileManager.DesignEntry entry = entries.get(startIndex + i);
            int col = i % columns;
            int row = i / columns;

            int cardX = galleryStartX + (col * (CARD_W + CARD_GAP));
            int cardY = listTopY + 4 + (row * (CARD_H + CARD_GAP));

            boolean isSelected = (entry == this.selectedEntry);
            boolean hovered = mouseX >= cardX && mouseX <= cardX + CARD_W && mouseY >= cardY && mouseY <= cardY + CARD_H && mouseY >= listTopY && mouseY <= listBottomY;

            // Card frame
            guiGraphics.fill(cardX, cardY, cardX + CARD_W, cardY + CARD_H, isSelected ? 0x99000000 : (hovered ? 0x77000000 : 0x44000000));
            int borderColor = isSelected ? 0xFF55FF55 : (hovered ? 0xFFFFFFFF : 0x33FFFFFF);
            guiGraphics.fill(cardX, cardY, cardX + CARD_W, cardY + 1, borderColor);
            guiGraphics.fill(cardX, cardY + CARD_H - 1, cardX + CARD_W, cardY + CARD_H, borderColor);
            guiGraphics.fill(cardX, cardY, cardX + 1, cardY + CARD_H, borderColor);
            guiGraphics.fill(cardX + CARD_W - 1, cardY, cardX + CARD_W, cardY + CARD_H, borderColor);

            // Thumbnail (Mapped to active page slot index)
            int thumbX = cardX + (CARD_W - THUMB_SIZE) / 2;
            int thumbY = cardY + 4;
            if (this.atlasTextureId != null) {
                float u = (float) ((i % SLOTS_PER_ROW) * THUMB_SIZE + 3);
                float v = (float) ((i / SLOTS_PER_ROW) * THUMB_SIZE + 3);
                guiGraphics.blit(RenderPipelines.GUI_TEXTURED, this.atlasTextureId, thumbX, thumbY, u, v, THUMB_SIZE, THUMB_SIZE, ATLAS_SIZE, ATLAS_SIZE);
            }

            // File name and small info
            String rawName = StringUtils.rawFileName(entry.fileName());
            String shortName = rawName.length() > 9 ? rawName.substring(0, 8) + "…" : rawName;
            guiGraphics.text(this.font, Component.literal("§f" + shortName), cardX + (CARD_W - this.font.width(shortName)) / 2, cardY + THUMB_SIZE + 6, 0xFFFFFFFF, false);

            String tag = (entry.isImage() ? "§bIMG " : "§eJSON ") + "§7" + entry.voxels().size() + "v";
            guiGraphics.text(this.font, Component.literal(tag), cardX + (CARD_W - this.font.width(tag)) / 2, cardY + THUMB_SIZE + 17, 0xFFAAAAAA, false);
        }

        guiGraphics.disableScissor();

        // Sidebar
        guiGraphics.fill(sidebarX, listTopY, sidebarX + SIDEBAR_W, listBottomY, 0x66000000);
        guiGraphics.fill(sidebarX, listTopY, sidebarX + SIDEBAR_W, listTopY + 1, 0x33FFFFFF);
        guiGraphics.fill(sidebarX, listBottomY - 1, sidebarX + SIDEBAR_W, listBottomY, 0x33000000);
        guiGraphics.fill(sidebarX, listTopY, sidebarX + 1, listBottomY, 0x33FFFFFF);
        guiGraphics.fill(sidebarX + SIDEBAR_W - 1, listTopY, sidebarX + SIDEBAR_W, listBottomY, 0x33000000);

        DesignFileManager.DesignEntry detail = this.selectedEntry != null ? this.selectedEntry : (!entries.isEmpty() ? entries.getFirst() : null);

        if (detail != null) {
            // Large thumbnail
            int lThumbX = sidebarX + (SIDEBAR_W - LARGE_THUMB_SIZE) / 2;
            int lThumbY = listTopY + 6;
            guiGraphics.fill(lThumbX, lThumbY, lThumbX + LARGE_THUMB_SIZE, lThumbY + LARGE_THUMB_SIZE, 0xFF141414);

            float lScale = (float) LARGE_THUMB_SIZE / maxGrid;
            for (WeaponVoxel v : WeaponVoxel.sortedBackToFront(detail.voxels())) {
                if (v.x() < 0 || v.x() >= maxGrid || v.y() < 0 || v.y() >= maxGrid) continue;
                int vx = lThumbX + (int) (v.x() * lScale);
                int vy = lThumbY + (int) ((maxGrid - 1 - v.y()) * lScale);
                int pSize = Math.max(1, Math.round(lScale));
                guiGraphics.fill(vx, vy, vx + pSize, vy + pSize, v.getColorRgb() | 0xFF000000);
            }

            // Metadata info
            int infoY = lThumbY + LARGE_THUMB_SIZE + 6;

            // File Type section
            guiGraphics.text(this.font, Component.literal("§7Type: " + (detail.isImage() ? "§bImage (PNG)" : "§eJSON Blueprint")), sidebarX + 6, infoY, 0xFFFFFFFF, false);

            // Voxel Count section
            guiGraphics.text(this.font, Component.literal("§7Voxels: §a" + detail.voxels().size()), sidebarX + 6, infoY + 11, 0xFFFFFFFF, false);

            // Folder section: only for designs that live in a sub-folder of the library
            int folderShift = 0;
            if (!detail.folder().isEmpty()) {
                String folderText = detail.folder();
                int maxW = SIDEBAR_W - 12 - this.font.width("Folder: ");
                if (this.font.width(folderText) > maxW) {
                    while (folderText.length() > 1 && this.font.width(folderText + "…") > maxW) folderText = folderText.substring(1);
                    folderText = "…" + folderText;
                }
                guiGraphics.text(this.font, Component.literal("§7Folder: §f" + folderText), sidebarX + 6, infoY + 22, 0xFFFFFFFF, false);
                folderShift = 11;
            }

            // Crafter section
            String crf = detail.crafter().isEmpty() ? "§8None" : "§f" + detail.crafter();
            guiGraphics.text(this.font, Component.literal("§bCrafter:"), sidebarX + 6, infoY + 24 + folderShift, 0xFFFFFFFF, false);
            guiGraphics.text(this.font, Component.literal(crf), sidebarX + 6, infoY + 34 + folderShift, 0xFFFFFFFF, false);

            // Attribution section
            int attrY = infoY + 47 + folderShift;
            guiGraphics.text(this.font, Component.literal("§6Attribution:"), sidebarX + 6, attrY, 0xFFFFFFFF, false);

            activeLinks.clear();
            if (!detail.attribution().isEmpty()) {
                renderMarkdownAttribution(guiGraphics, detail.attribution(), sidebarX + 6, attrY + 10, SIDEBAR_W - 12);
            } else {
                guiGraphics.text(this.font, Component.literal("§8None"), sidebarX + 6, attrY + 10, 0xFFFFFFFF, false);
            }

            int loadBtnW = SIDEBAR_W - 12;
            int loadBtnH = 20;
            int loadBtnX = sidebarX + 6;
            int loadBtnY = listBottomY - loadBtnH - 4;

            boolean hoveredLoad = mouseX >= loadBtnX && mouseX <= loadBtnX + loadBtnW && mouseY >= loadBtnY && mouseY <= loadBtnY + loadBtnH;
            drawActionButton(guiGraphics, loadBtnX, loadBtnY, loadBtnW, loadBtnH, "Load Blueprint", hoveredLoad);
        }

        // Fixed header and footer bars
        guiGraphics.fill(0, 0, this.width, 30, 0xD0000000);
        guiGraphics.text(this.font, this.title, (this.width - this.font.width(this.title)) / 2, 6, 0xFFFFFFFF, true);
        Component sub = Component.literal("Hover over a card to view detailed specifications and large preview");
        guiGraphics.text(this.font, sub, (this.width - this.font.width(sub)) / 2, 17, 0xFFAAAAAA, false);

        guiGraphics.fill(0, this.height - 28, this.width, this.height, 0xD0000000);

        // Picture import options only apply to pictures
        boolean showOptions = detail != null && detail.isImage();
        for (Button b : this.optionButtons) b.visible = showOptions;
        if (showOptions) {
            guiGraphics.text(this.font, Component.literal("§7Picture import:"), sidebarX + 6, (this.height - 30) - 24 - 44, 0xFFFFFFFF, false);
        }

        // Page Indicator (ex. "1 / 4") between [<] and [>] buttons
        String pageStr = (this.currentPage + 1) + " / " + this.maxPages;
        int pageTextX = (this.width / 2) + 15 - (this.font.width(pageStr) / 2);
        guiGraphics.text(this.font, Component.literal(pageStr), pageTextX, this.height - 18, 0xFFFFFFFF, true);

        super.extractRenderState(guiGraphics, mouseX, mouseY, delta);
        if (this.folderPanelOpen) drawFolderPanel(guiGraphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();

        if (this.folderPanelOpen) {
            boolean onButton = mouseX >= FP_X && mouseX < FP_X + 100 && mouseY >= 5 && mouseY < 25;
            if (clickFolderPanel(mouseX, mouseY)) return true;
            if (!onButton) { // a click anywhere else closes the list
                this.folderPanelOpen = false;
                return true;
            }
        }

        int listTopY = 34;
        int listBottomY = this.height - 30;
        int sidebarX = this.width - SIDEBAR_W - 8;

        int galleryAreaW = sidebarX - 16;
        int columns = Math.max(1, (galleryAreaW - 10) / (CARD_W + CARD_GAP));
        int galleryW = (columns * CARD_W) + ((columns - 1) * CARD_GAP);
        int galleryStartX = 8 + (galleryAreaW - galleryW) / 2;

        // Load Blueprint click
        int loadBtnW = SIDEBAR_W - 12;
        int loadBtnH = 20;
        int loadBtnX = sidebarX + 6;
        int loadBtnY = listBottomY - loadBtnH - 4;
        if (mouseX >= loadBtnX && mouseX <= loadBtnX + loadBtnW && mouseY >= loadBtnY && mouseY <= loadBtnY + loadBtnH && this.selectedEntry != null) {
            this.parent.loadImportedDesign(selectedEntry);
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_USE, 1.2F));
            this.minecraft.setScreen(this.parent);
            return true;
        }

        // Select cards to inspect its details
        int startIndex = this.currentPage * this.cardsPerPage;
        int count = Math.min(this.cardsPerPage, entries.size() - startIndex);
        if (mouseY >= listTopY && mouseY <= listBottomY) {
            for (int i = 0; i < count; i++) {
                int col = i % columns;
                int row = i / columns;
                int cardX = galleryStartX + (col * (CARD_W + CARD_GAP));
                int cardY = listTopY + 4 + (row * (CARD_H + CARD_GAP));

                if (mouseX >= cardX && mouseX <= cardX + CARD_W && mouseY >= cardY && mouseY <= cardY + CARD_H) {
                    this.selectedEntry = entries.get(startIndex + i);
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
        }

        // Make Markdown attribution links clickable
        for (LinkBox link : activeLinks) {
            if (mouseX >= link.minX() && mouseX <= link.maxX() && mouseY >= link.minY() && mouseY <= link.maxY()) {
                try {
                    String url = link.url().trim();
                    if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
                    net.minecraft.util.Util.getPlatform().openUri(new java.net.URI(url));
                    return true;
                } catch (Exception ignored) {}
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    private void bakeThumbnailAtlas() {
        if (this.atlasImage == null) return;

        // Clear atlas
        for (int x = 0; x < ATLAS_SIZE; x++) {
            for (int y = 0; y < ATLAS_SIZE; y++) {
                this.atlasImage.setPixel(x, y, 0);
            }
        }

        int maxGrid = Math.max(16, this.parent.gridSize);
        float scale = (float) THUMB_SIZE / maxGrid;

        int startIndex = this.currentPage * this.cardsPerPage;
        if (startIndex >= entries.size()) {
            this.atlasTexture.upload();
            return;
        }
        int count = Math.min(this.cardsPerPage, entries.size() - startIndex);

        // Bakes only the designs on the current page into the atlas
        for (int i = 0; i < count; i++) {
            DesignFileManager.DesignEntry entry = entries.get(startIndex + i);
            int slotX = (i % SLOTS_PER_ROW) * THUMB_SIZE + 3;
            int slotY = (i / SLOTS_PER_ROW) * THUMB_SIZE + 3;

            for (WeaponVoxel v : WeaponVoxel.sortedBackToFront(entry.voxels())) {
                if (v.x() < 0 || v.x() >= maxGrid || v.y() < 0 || v.y() >= maxGrid) continue;

                int px = slotX + (int) (v.x() * scale);
                int py = slotY + (int) ((maxGrid - 1 - v.y()) * scale);
                int pSize = Math.max(1, Math.round(scale));

                int color = v.getColorRgb() | 0xFF000000;
                for (int dx = 0; dx < pSize && (px + dx - slotX) < THUMB_SIZE; dx++) {
                    for (int dy = 0; dy < pSize && (py + dy - slotY) < THUMB_SIZE; dy++) {
                        this.atlasImage.setPixel(px + dx, py + dy, color);
                    }
                }
            }
        }
        this.atlasTexture.upload();
    }

    /** Lays out chunks of attribution text left to right, wrapping at the edge and spacing only where the source text has spaces. */
    private static final int LINK_COLOR = 0xFF8EC4FF; // light blue: readable on the dark sidebar

    private final class AttributionFlow {
        final GuiGraphicsExtractor g;
        final int startX, maxW;
        int x, y;
        boolean space; // whitespace seen since the last chunk

        AttributionFlow(GuiGraphicsExtractor g, int startX, int startY, int maxW) {
            this.g = g;
            this.startX = startX;
            this.x = startX;
            this.y = startY;
            this.maxW = maxW;
        }

        private void newLine() {
            x = startX;
            y += 10;
            space = false;
        }

        /** Moves to where the next chunk of the given width goes, wrapping when it would not fit. */
        private int place(int width) {
            int gap = space && x > startX ? font.width(" ") : 0;
            if (x > startX && x + gap + width > startX + maxW) {
                newLine();
                gap = 0;
            }
            x += gap;
            space = false;
            int at = x;
            x += width;
            return at;
        }

        /** Draws one unbroken chunk; a chunk wider than the whole line is cut into pieces that each fit. */
        private void emit(String chunk, int color, boolean link, String url) {
            while (!chunk.isEmpty()) {
                String piece = chunk;
                if (font.width(piece) > maxW) {
                    int n = piece.length();
                    while (n > 1 && font.width(piece.substring(0, n)) > maxW) n--;
                    piece = piece.substring(0, n);
                    if (x > startX) newLine();
                }
                int w = font.width(piece);
                int at = place(w);
                g.text(font, Component.literal(link ? "§n" + piece : piece), at, y, color, false);
                if (link) activeLinks.add(new LinkBox(at, y, at + w, y + 9, url));
                chunk = chunk.substring(piece.length());
                if (!chunk.isEmpty()) newLine();
            }
        }

        void plain(String text) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\n|[^\\S\\n]+|\\S+").matcher(text);
            while (m.find()) {
                String chunk = m.group();
                if (chunk.equals("\n")) newLine();
                else if (chunk.isBlank()) space = true;
                else emit(chunk, 0xFFAAAAAA, false, null);
            }
        }

        /** A link wraps word by word like the text around it, and every word is clickable. */
        void link(String label, String url) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\S+").matcher(label);
            int last = 0;
            boolean first = true;
            while (m.find()) {
                if (!first && m.start() > last) space = true;
                emit(m.group(), LINK_COLOR, true, url);
                last = m.end();
                first = false;
            }
        }
    }

    private void renderMarkdownAttribution(GuiGraphicsExtractor g, String text, int startX, int startY, int maxW) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\[([^]]+)]\\(([^)]+)\\)").matcher(text);
        AttributionFlow flow = new AttributionFlow(g, startX, startY, maxW);
        int lastEnd = 0;
        while (matcher.find()) {
            flow.plain(text.substring(lastEnd, matcher.start()));
            flow.link(matcher.group(1), matcher.group(2));
            lastEnd = matcher.end();
        }
        flow.plain(text.substring(lastEnd));
    }

    private void drawActionButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String text, boolean hovered) {
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
}