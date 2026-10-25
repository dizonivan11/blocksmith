package net.adeptfrog.blocksmith.client.compat;

import net.adeptfrog.blocksmith.client.utils.InputEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Base class of the Blocksmith screens. It turns the version's render and input callbacks into one shared form:
 * {@link #renderScreen} with a {@link Gfx}, and onMouse... / onKeyPressed with {@link InputEvents} records.
 * The vanilla... methods run the default Screen behaviour for the event being handled.
 * Minecraft 26.x version (extractRenderState, event objects).
 */
public abstract class CompatScreen extends Screen {
    private MouseButtonEvent rawMouse;
    private boolean rawDoubleClick;
    private KeyEvent rawKey;

    protected CompatScreen(Component title) {
        super(title);
    }

    // ---- rendering

    @Override
    public final void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        renderScreen(new Gfx(graphics), mouseX, mouseY, delta);
    }

    /** Draws the screen. Call {@link #renderVanilla} to draw the widgets. */
    protected abstract void renderScreen(Gfx g, int mouseX, int mouseY, float delta);

    /** Draws the screen's widgets (buttons, edit boxes, ...). */
    protected final void renderVanilla(Gfx g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g.raw(), mouseX, mouseY, delta);
    }

    // ---- input

    @Override
    public final boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        this.rawMouse = event;
        this.rawDoubleClick = doubleClick;
        return onMouseClicked(new InputEvents.Mouse(event.x(), event.y(), event.button()));
    }

    @Override
    public final boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        this.rawMouse = event;
        return onMouseDragged(new InputEvents.Mouse(event.x(), event.y(), event.button()), deltaX, deltaY);
    }

    @Override
    public final boolean mouseReleased(MouseButtonEvent event) {
        this.rawMouse = event;
        return onMouseReleased(new InputEvents.Mouse(event.x(), event.y(), event.button()));
    }

    @Override
    public final boolean keyPressed(KeyEvent event) {
        this.rawKey = event;
        // 26.3 key events carry no separate scancode (key() already is the SDL scancode); nothing shared needs it
        return onKeyPressed(new InputEvents.Key(event.key(), 0, event.modifiers()));
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
        return super.mouseClicked(this.rawMouse, this.rawDoubleClick);
    }

    protected final boolean vanillaMouseDragged(InputEvents.Mouse event, double deltaX, double deltaY) {
        return super.mouseDragged(this.rawMouse, deltaX, deltaY);
    }

    protected final boolean vanillaMouseReleased(InputEvents.Mouse event) {
        return super.mouseReleased(this.rawMouse);
    }

    protected final boolean vanillaKeyPressed(InputEvents.Key event) {
        return super.keyPressed(this.rawKey);
    }

    /** Whether the key is bound to "open inventory" (E by default), which also closes these screens. */
    protected final boolean isInventoryKey(InputEvents.Key event) {
        return this.minecraft.options.keyInventory.matches(this.rawKey);
    }
}
