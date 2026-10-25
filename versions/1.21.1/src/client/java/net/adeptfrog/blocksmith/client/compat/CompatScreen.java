package net.adeptfrog.blocksmith.client.compat;

import net.adeptfrog.blocksmith.client.utils.InputEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base class of the Blocksmith screens. It turns the version's render and input callbacks into one shared form:
 * {@link #renderScreen} with a {@link Gfx}, and onMouse... / onKeyPressed with {@link InputEvents} records.
 * The vanilla... methods run the default Screen behaviour for the event being handled.
 * Minecraft 1.21.1 version (render, raw mouse / key values).
 */
public abstract class CompatScreen extends Screen {

    protected CompatScreen(Component title) {
        super(title);
        // 1.21.1 only fills these in init(); the shared screens already need them while constructing.
        this.minecraft = Minecraft.getInstance();
        this.font = this.minecraft.font;
    }

    // ---- rendering

    /**
     * 1.21.1 draws the background inside Screen#render, which would cover what the screen drew before drawing its
     * widgets. It is drawn once, first, in {@link #render} instead.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public final void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.renderBackground(graphics, mouseX, mouseY, delta);
        renderScreen(new Gfx(graphics), mouseX, mouseY, delta);
    }

    /** Draws the screen. Call {@link #renderVanilla} to draw the widgets. */
    protected abstract void renderScreen(Gfx g, int mouseX, int mouseY, float delta);

    /** Draws the screen's widgets (buttons, edit boxes, ...). */
    protected final void renderVanilla(Gfx g, int mouseX, int mouseY, float delta) {
        super.render(g.raw(), mouseX, mouseY, delta);
    }

    // ---- input

    @Override
    public final boolean mouseClicked(double mouseX, double mouseY, int button) {
        return onMouseClicked(new InputEvents.Mouse(mouseX, mouseY, button));
    }

    @Override
    public final boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return onMouseDragged(new InputEvents.Mouse(mouseX, mouseY, button), deltaX, deltaY);
    }

    @Override
    public final boolean mouseReleased(double mouseX, double mouseY, int button) {
        return onMouseReleased(new InputEvents.Mouse(mouseX, mouseY, button));
    }

    @Override
    public final boolean keyPressed(int key, int scancode, int modifiers) {
        return onKeyPressed(new InputEvents.Key(key, scancode, modifiers));
    }

    protected boolean onMouseClicked(InputEvents.Mouse event) {
        return vanillaMouseClicked(event);
    }

    protected boolean onMouseDragged(InputEvents.Mouse event, double deltaX, double deltaY) {
        return vanillaMouseDragged(event, deltaX, deltaY);
    }

    protected boolean onMouseReleased(InputEvents.Mouse event) {
        return vanillaMouseReleased(event);
    }

    protected boolean onKeyPressed(InputEvents.Key event) {
        return vanillaKeyPressed(event);
    }

    protected final boolean vanillaMouseClicked(InputEvents.Mouse event) {
        return super.mouseClicked(event.x(), event.y(), event.button());
    }

    protected final boolean vanillaMouseDragged(InputEvents.Mouse event, double deltaX, double deltaY) {
        return super.mouseDragged(event.x(), event.y(), event.button(), deltaX, deltaY);
    }

    protected final boolean vanillaMouseReleased(InputEvents.Mouse event) {
        return super.mouseReleased(event.x(), event.y(), event.button());
    }

    protected final boolean vanillaKeyPressed(InputEvents.Key event) {
        return super.keyPressed(event.key(), event.scancode(), event.modifiers());
    }

    /** Whether the key is bound to "open inventory" (E by default), which also closes these screens. */
    protected final boolean isInventoryKey(InputEvents.Key event) {
        return this.minecraft.options.keyInventory.matches(event.key(), event.scancode());
    }
}
