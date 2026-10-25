package net.adeptfrog.blocksmith.client.compat;

import net.minecraft.resources.Identifier;

/** A texture or GUI sprite id, so shared code never names Identifier / ResourceLocation. Minecraft 26.x version. */
public final class TexId {
    private final Identifier id;

    private TexId(Identifier id) {
        this.id = id;
    }

    /** blocksmith:path */
    public static TexId mod(String path) {
        return new TexId(Identifier.fromNamespaceAndPath("blocksmith", path));
    }

    /** minecraft:path */
    public static TexId vanilla(String path) {
        return new TexId(Identifier.withDefaultNamespace(path));
    }

    public Identifier raw() {
        return id;
    }
}
