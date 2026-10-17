package net.adeptfrog.blocksmith.client.gui;

import net.adeptfrog.blocksmith.block.VoxelCubeBlock;
import net.adeptfrog.blocksmith.data.VoxelMaterial;
import net.adeptfrog.blocksmith.network.DimensionalActionPayload;
import net.adeptfrog.blocksmith.network.SelectVoxelPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Material and shade picker for the Dimensional Editor. Every change is sent to the server straight away, so the
 * Voxel Brush places the chosen voxel as soon as the screen is closed.
 */
public class VoxelPaletteScreen extends Screen {
    private static final int COLUMNS = 8, CELL = 22, PADDING = 10;
    private static final int PANEL_W = COLUMNS * CELL + PADDING * 2;

    // Remembered between openings
    private static int lastMaterial = 0;
    private static int lastShade = 2;

    private final List<VoxelMaterial> materials = VoxelCubeBlock.orderedMaterials();
    private final List<ItemStack> icons = new ArrayList<>();

    public VoxelPaletteScreen() {
        super(Component.literal("Voxel Palette"));
        for (VoxelMaterial m : materials) icons.add(new ItemStack(m.getIconItem()));
        if (lastMaterial >= materials.size()) lastMaterial = 0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int panelH() {
        int rows = Math.max(1, (materials.size() + COLUMNS - 1) / COLUMNS);
        return 22 + rows * CELL + 40 + 34;
    }

    private int left() { return (this.width - PANEL_W) / 2; }
    private int top() { return (this.height - panelH()) / 2; }
    private int shadeY() { return top() + 22 + Math.max(1, (materials.size() + COLUMNS - 1) / COLUMNS) * CELL + 10; }
    private int buttonsY() { return top() + panelH() - 24; }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        int left = left(), top = top();

        g.fill(left - 2, top - 2, left + PANEL_W + 2, top + panelH() + 2, 0xFF373737);
        g.fill(left, top, left + PANEL_W, top + panelH(), 0xFFC6C6C6);
        g.text(this.font, Component.literal("Voxel Palette"), left + PADDING, top + 8, 0xFF3F3F3F, false);

        List<ClientTooltipComponent> tooltip = null;
        for (int i = 0; i < materials.size(); i++) {
            int x = left + PADDING + (i % COLUMNS) * CELL, y = top + 22 + (i / COLUMNS) * CELL;
            boolean selected = i == lastMaterial;
            boolean hovered = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
            g.fill(x, y, x + CELL - 1, y + CELL - 1, selected ? 0xFFFFFFFF : (hovered ? 0xFFE0E0E0 : 0xFF8B8B8B));
            g.fill(x + 1, y + 1, x + CELL - 2, y + CELL - 2, 0xFF555555);
            g.fakeItem(icons.get(i), x + 2, y + 2);
            if (hovered) tooltip = List.of(ClientTooltipComponent.create(Component.literal("§e" + materials.get(i).id()).getVisualOrderText()));
        }

        int shadeY = shadeY();
        g.text(this.font, Component.literal("Shade"), left + PADDING, shadeY - 9, 0xFF3F3F3F, false);
        VoxelMaterial current = materials.isEmpty() ? null : materials.get(lastMaterial);
        for (int s = 0; s < VoxelCubeBlock.SHADES; s++) {
            int x = left + PADDING + s * 34;
            boolean selected = s == lastShade;
            g.fill(x - 1, shadeY - 1, x + 31, shadeY + 17, selected ? 0xFFFFFFFF : 0xFF373737);
            g.fill(x, shadeY, x + 30, shadeY + 16, current == null ? 0xFF888888 : (current.getColorRgb(s) | 0xFF000000));
        }

        drawButton(g, left + PADDING, buttonsY(), 84, "Save & Exit", mouseX, mouseY);
        drawButton(g, left + PADDING + 90, buttonsY(), 84, "Discard & Exit", mouseX, mouseY);
        drawButton(g, left + PANEL_W - PADDING - 40, buttonsY(), 40, "Done", mouseX, mouseY);

        if (tooltip != null) g.tooltip(this.font, tooltip, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
    }

    private void drawButton(GuiGraphicsExtractor g, int x, int y, int w, String label, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 16;
        g.fill(x - 1, y - 1, x + w + 1, y + 17, 0xFF373737);
        g.fill(x, y, x + w, y + 16, hovered ? 0xFFD6D6D6 : 0xFFBCBCBC);
        g.text(this.font, Component.literal(label), x + (w - this.font.width(label)) / 2, y + 4, hovered ? 0xFF000000 : 0xFF303030, false);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int left = left(), top = top();

        for (int i = 0; i < materials.size(); i++) {
            int x = left + PADDING + (i % COLUMNS) * CELL, y = top + 22 + (i / COLUMNS) * CELL;
            if (in(mx, my, x, y, CELL, CELL)) {
                lastMaterial = i;
                click();
                sendSelection();
                return true;
            }
        }
        for (int s = 0; s < VoxelCubeBlock.SHADES; s++) {
            if (in(mx, my, left + PADDING + s * 34, shadeY(), 30, 16)) {
                lastShade = s;
                click();
                sendSelection();
                return true;
            }
        }
        if (in(mx, my, left + PADDING, buttonsY(), 84, 16)) {
            click();
            ClientPlayNetworking.send(new DimensionalActionPayload(DimensionalActionPayload.SAVE_AND_EXIT));
            this.onClose();
            return true;
        }
        if (in(mx, my, left + PADDING + 90, buttonsY(), 84, 16)) {
            click();
            ClientPlayNetworking.send(new DimensionalActionPayload(DimensionalActionPayload.DISCARD_AND_EXIT));
            this.onClose();
            return true;
        }
        if (in(mx, my, left + PANEL_W - PADDING - 40, buttonsY(), 40, 16)) {
            click();
            this.onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void click() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void sendSelection() {
        if (materials.isEmpty()) return;
        ClientPlayNetworking.send(new SelectVoxelPayload(materials.get(lastMaterial).id(), lastShade));
    }
}
