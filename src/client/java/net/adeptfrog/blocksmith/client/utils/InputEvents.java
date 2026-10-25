package net.adeptfrog.blocksmith.client.utils;

/**
 * Minecraft 1.21.1 passes raw mouse and key values to screens. These small records carry them so the screen
 * code reads the same as on newer versions, which pass event objects.
 */
public final class InputEvents {
    private InputEvents() {
    }

    public record Mouse(double x, double y, int button) {
    }

    public record Key(int key, int scancode, int modifiers) {
    }
}
