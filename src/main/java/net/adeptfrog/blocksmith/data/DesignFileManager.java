package net.adeptfrog.blocksmith.data;

import net.fabricmc.loader.api.FabricLoader;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class DesignFileManager {
    public static final File BLUEPRINTS_DIR = FabricLoader.getInstance().getGameDir().resolve("blocksmith_blueprints").toFile();
    private static List<DesignEntry> CACHED_ENTRIES = null;
    /** How pictures are turned into voxels; kept for the session so the library remembers the player's choice. */
    private static ImageVoxelizer.Options imageOptions = ImageVoxelizer.Options.DEFAULT;

    public static ImageVoxelizer.Options getImageOptions() { return imageOptions; }
    public static void setImageOptions(ImageVoxelizer.Options options) { imageOptions = options; }

    /**
     * One design in the library. {@code folder} is the sub-folder it came from ("" for the top level), which groups
     * pictures and blueprints; an {@code attribution.txt} in that folder is the default attribution of its files.
     */
    public record DesignEntry(
            String fileName,
            boolean isImage,
            List<WeaponVoxel> voxels,
            WeaponOffset offset,
            String attribution,
            String crafter,
            String folder) {
        public DesignEntry(String fileName, boolean isImage, List<WeaponVoxel> voxels, WeaponOffset offset,
                           String attribution, String crafter) {
            this(fileName, isImage, voxels, offset, attribution, crafter, "");
        }
    }

    public static void ensureDirectory() {
        if (!BLUEPRINTS_DIR.exists()) BLUEPRINTS_DIR.mkdirs();
    }

    /** Name of the optional text file that gives every picture and attribution-less blueprint in its folder an attribution. */
    public static final String ATTRIBUTION_FILE = "attribution.txt";
    private static final String BUNDLED_PATH = "assets/blocksmith/blueprints";

    public static List<DesignEntry> loadAllDesigns(int targetGridSize, boolean forceReload) {
        if (CACHED_ENTRIES != null && !forceReload) {
            return CACHED_ENTRIES;
        }

        ensureDirectory();
        List<DesignEntry> list = new ArrayList<>();

        // The player's own library: .minecraft/blocksmith_blueprints, with any sub-folders
        scan(BLUEPRINTS_DIR.toPath(), "", "", targetGridSize, list, true);

        // Folders shipped inside the mod (assets/blocksmith/blueprints/<folder>); the loose files at the top of that
        // folder are the default sword and bow, which are not library entries
        FabricLoader.getInstance().getModContainer("blocksmith")
                .flatMap(c -> c.findPath(BUNDLED_PATH))
                .ifPresent(root -> scan(root, "", "", targetGridSize, list, false));

        CACHED_ENTRIES = list;
        return list;
    }

    /**
     * Reads one folder: its {@code attribution.txt} (inherited by sub-folders that have none of their own) becomes the
     * default attribution for pictures and for blueprints that carry none.
     */
    private static void scan(Path dir, String folder, String inherited, int grid, List<DesignEntry> out, boolean includeFiles) {
        String defaultAttribution = inherited;
        Path attrFile = dir.resolve(ATTRIBUTION_FILE);
        try {
            if (Files.isRegularFile(attrFile)) {
                String text = Files.readString(attrFile).trim();
                if (!text.isEmpty()) defaultAttribution = text;
            }
        } catch (Exception ignored) {}

        List<Path> children = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.forEach(children::add);
        } catch (Exception e) {
            return;
        }
        children.sort(Comparator.comparing(pa -> pa.getFileName().toString().toLowerCase()));

        // Files of this folder first, then each sub-folder
        for (Path child : children) {
            if (!includeFiles || !Files.isRegularFile(child)) continue;
            String name = child.getFileName().toString();
            String lower = name.toLowerCase();
            try {
                if (lower.endsWith(".json")) {
                    String content = Files.readString(child);
                    VoxelDesignSerializer.BlueprintData bp = VoxelDesignSerializer.importBlueprint(content, grid);
                    String attribution = bp.attribution() == null || bp.attribution().isBlank() ? defaultAttribution : bp.attribution();
                    out.add(new DesignEntry(name, false, bp.voxels(), bp.offset(), attribution, bp.crafter(), folder));
                } else if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".bmp") || lower.endsWith(".webp")) {
                    BufferedImage img;
                    try (InputStream in = Files.newInputStream(child)) {
                        img = ImageIO.read(in);
                    }
                    if (img != null) {
                        List<WeaponVoxel> voxels = VoxelDesignSerializer.convertImageToVoxels(img, grid, imageOptions);
                        out.add(new DesignEntry(name, true, voxels, WeaponOffset.ZERO, defaultAttribution, "", folder));
                    }
                }
            } catch (Exception ignored) {}
        }
        for (Path child : children) {
            if (!Files.isDirectory(child)) continue;
            String name = child.getFileName().toString();
            scan(child, folder.isEmpty() ? name : folder + "/" + name, defaultAttribution, grid, out, true);
        }
    }
}
