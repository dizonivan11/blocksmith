package net.adeptfrog.blocksmith.platform;

import net.adeptfrog.blocksmith.Blocksmith;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModFileInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Mod loader calls the shared code needs (game folders, files inside the mod jar). Forge on Minecraft 26.x. */
public final class Platform {
    private Platform() {
    }

    /** The game folder (.minecraft, or the server's folder). */
    public static Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    /** The config folder. */
    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    /** A folder or file shipped inside this mod's jar, if it exists. */
    public static Optional<Path> modResource(String path) {
        IModFileInfo info = ModList.getModFileById(Blocksmith.MOD_ID);
        if (info == null) return Optional.empty();
        Path found = info.getFile().findResource(path.split("/"));
        return found != null && Files.exists(found) ? Optional.of(found) : Optional.empty();
    }
}
