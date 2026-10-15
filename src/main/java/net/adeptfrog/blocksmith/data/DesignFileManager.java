package net.adeptfrog.blocksmith.data;

import net.fabricmc.loader.api.FabricLoader;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class DesignFileManager {
    public static final File BLUEPRINTS_DIR = FabricLoader.getInstance().getGameDir().resolve("blocksmith_blueprints").toFile();
    private static List<DesignEntry> CACHED_ENTRIES = null;

    public record DesignEntry(
            String fileName,
            boolean isImage,
            List<WeaponVoxel> voxels,
            WeaponOffset offset,
            String attribution,
            String crafter) {}

    public static void ensureDirectory() {
        if (!BLUEPRINTS_DIR.exists()) BLUEPRINTS_DIR.mkdirs();
    }

    public static List<DesignEntry> loadAllDesigns(int targetGridSize, boolean forceReload) {
        if (CACHED_ENTRIES != null && !forceReload) {
            return CACHED_ENTRIES;
        }

        ensureDirectory();
        List<DesignEntry> list = new ArrayList<>();
        File[] files = BLUEPRINTS_DIR.listFiles();
        if (files == null) return list;

        for (File file : files) {
            if (!file.isFile()) continue;
            String name = file.getName();
            String lower = name.toLowerCase();

            try {
                if (lower.endsWith(".json")) {
                    String content = Files.readString(file.toPath());
                    VoxelDesignSerializer.BlueprintData bp = VoxelDesignSerializer.importBlueprint(content, targetGridSize);
                    list.add(new DesignEntry(name, false, bp.voxels(), bp.offset(), bp.attribution(), bp.crafter()));
                } else if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".bmp") || lower.endsWith(".webp")) {
                    BufferedImage img = ImageIO.read(file);
                    if (img != null) {
                        List<WeaponVoxel> voxels = VoxelDesignSerializer.convertImageToVoxels(img, targetGridSize);
                        list.add(new DesignEntry(name, true, voxels, WeaponOffset.ZERO, "", ""));
                    }
                }
            } catch (Exception ignored) {}
        }

        CACHED_ENTRIES = list;
        return list;
    }
}