package net.adeptfrog.blocksmith.client.compat;

import net.minecraft.resources.ResourceLocation;

/** A texture or GUI sprite id, so shared code never names Identifier / ResourceLocation. Minecraft 1.21.1 version. */
public final class TexId {
    private final ResourceLocation id;

    private TexId(ResourceLocation id) {
        this.id = id;
    }

    /** blocksmith:path */
    public static TexId mod(String path) {
        return new TexId(ResourceLocation.fromNamespaceAndPath("blocksmith", path));
    }

    /** minecraft:path */
    public static TexId vanilla(String path) {
        return new TexId(ResourceLocation.withDefaultNamespace(path));
    }

    public ResourceLocation raw() {
        return id;
    }
}
