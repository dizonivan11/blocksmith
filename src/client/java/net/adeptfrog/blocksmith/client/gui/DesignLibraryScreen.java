package net.adeptfrog.blocksmith.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.adeptfrog.blocksmith.client.utils.StringUtils;
import net.adeptfrog.blocksmith.data.DesignFileManager;
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
import java.util.List;

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

    private List<DesignFileManager.DesignEntry> entries = new ArrayList<>();

    public record LinkBox(int minX, int minY, int maxX, int maxY, String url) {}
    private final List<LinkBox> activeLinks = new ArrayList<>();

    public DesignLibraryScreen(CustomizationScreen parent) {
        super(Component.literal("Blueprint Library"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.entries = DesignFileManager.loadAllDesigns(this.parent.gridSize, false);

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
            this.entries = DesignFileManager.loadAllDesigns(this.parent.gridSize, true);
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

        if (entries.isEmpty()) {
            guiGraphics.text(this.font, Component.literal("§7No files found in .minecraft/blocksmith_blueprints/"), 20, listTopY + 20, 0xFFFFFFFF, true);
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

            // Crafter section
            String crf = detail.crafter().isEmpty() ? "§8None" : "§f" + detail.crafter();
            guiGraphics.text(this.font, Component.literal("§bCrafter:"), sidebarX + 6, infoY + 24, 0xFFFFFFFF, false);
            guiGraphics.text(this.font, Component.literal(crf), sidebarX + 6, infoY + 34, 0xFFFFFFFF, false);

            // Attribution section
            int attrY = infoY + 47;
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

        // Page Indicator (ex. "1 / 4") between [<] and [>] buttons
        String pageStr = (this.currentPage + 1) + " / " + this.maxPages;
        int pageTextX = (this.width / 2) + 15 - (this.font.width(pageStr) / 2);
        guiGraphics.text(this.font, Component.literal(pageStr), pageTextX, this.height - 18, 0xFFFFFFFF, true);

        super.extractRenderState(guiGraphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();

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

    private void renderMarkdownAttribution(GuiGraphicsExtractor g, String text, int startX, int startY, int maxW) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[([^]]+)]\\(([^)]+)\\)");
        java.util.regex.Matcher matcher = pattern.matcher(text);

        int currentX = startX;
        int currentY = startY;
        int lastEnd = 0;

        while (matcher.find()) {
            // Text before the link
            String plain = text.substring(lastEnd, matcher.start());
            if (!plain.isEmpty()) {
                for (String word : plain.split(" ")) {
                    if (word.isEmpty()) continue;
                    int wordW = this.font.width(word + " ");
                    if (currentX + wordW > startX + maxW) {
                        currentX = startX;
                        currentY += 10;
                    }
                    g.text(this.font, Component.literal(word + " "), currentX, currentY, 0xFFAAAAAA, false);
                    currentX += wordW;
                }
            }

            // Clickable Link: [text 1](text 2)
            String label = matcher.group(1);
            String url = matcher.group(2);
            int linkW = this.font.width(label + " ");

            if (currentX + linkW > startX + maxW) {
                currentX = startX;
                currentY += 10;
            }

            g.text(this.font, Component.literal("§9§n" + label + "§r "), currentX, currentY, 0xFF55AAFF, false);
            activeLinks.add(new LinkBox(currentX, currentY, currentX + linkW, currentY + 9, url));
            currentX += linkW;

            lastEnd = matcher.end();
        }

        // Remaining text after last link
        if (lastEnd < text.length()) {
            String remaining = text.substring(lastEnd);
            for (String word : remaining.split(" ")) {
                if (word.isEmpty()) continue;
                int wordW = this.font.width(word + " ");
                if (currentX + wordW > startX + maxW) {
                    currentX = startX;
                    currentY += 10;
                }
                g.text(this.font, Component.literal(word + " "), currentX, currentY, 0xFFAAAAAA, false);
                currentX += wordW;
            }
        }
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