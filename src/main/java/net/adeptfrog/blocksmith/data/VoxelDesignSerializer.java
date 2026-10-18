package net.adeptfrog.blocksmith.data;

import com.google.gson.*;

import net.adeptfrog.blocksmith.Blocksmith;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static net.adeptfrog.blocksmith.Blocksmith.BASE_GRID_SIZE;
import static net.adeptfrog.blocksmith.Blocksmith.MAX_TOTAL_VOXELS;

public class VoxelDesignSerializer {
    private static final Gson GSON = new GsonBuilder().create();

    private static final String SWORD_BLUEPRINT_PATH = "/assets/blocksmith/blueprints/forged_sword.json";
    private static final String BOW_BLUEPRINT_PATH = "/assets/blocksmith/blueprints/forged_bow.json";

    /** Reads a bundled default blueprint from the mod's resources; returns an empty blueprint if it is missing. */
    private static String readBlueprintResource(String path) {
        try (java.io.InputStream in = VoxelDesignSerializer.class.getResourceAsStream(path)) {
            if (in == null) throw new java.io.FileNotFoundException(path);
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[Blocksmith] Failed to read default blueprint " + path + ": " + e.getMessage());
            return "{\"format\":\"blocksmith_blueprint\",\"version\":1,\"gridSize\":" + BASE_GRID_SIZE + ",\"voxels\":[]}";
        }
    }

    private static final String DEFAULT_SWORD_BLUEPRINT_JSON = readBlueprintResource(SWORD_BLUEPRINT_PATH);
    private static final String DEFAULT_BOW_BLUEPRINT_JSON = readBlueprintResource(BOW_BLUEPRINT_PATH);

    private static final List<WeaponVoxel> COMPILED_DEFAULT_SWORD_VOXELS;
    private static final List<WeaponVoxel> COMPILED_DEFAULT_BOW_VOXELS;

    static {
        List<WeaponVoxel> swordList;
        List<WeaponVoxel> bowList;
        try {
            swordList = importFromJson(DEFAULT_SWORD_BLUEPRINT_JSON, BASE_GRID_SIZE);
        } catch (Exception e) {
            swordList = Collections.emptyList();
            System.err.println("[Blocksmith] Failed to compile default sword blueprint: " + e.getMessage());
        }

        try {
            bowList = importFromJson(DEFAULT_BOW_BLUEPRINT_JSON, BASE_GRID_SIZE);
        } catch (Exception e) {
            bowList = Collections.emptyList();
            System.err.println("[Blocksmith] Failed to compile default bow blueprint: " + e.getMessage());
        }

        COMPILED_DEFAULT_SWORD_VOXELS = Collections.unmodifiableList(swordList);
        COMPILED_DEFAULT_BOW_VOXELS = Collections.unmodifiableList(bowList);
    }

    public record BlueprintData(List<WeaponVoxel> voxels, WeaponOffset offset, String attribution, String crafter) {}

    public static List<WeaponVoxel> getDefaultVoxels() {
        try {
            return importFromJson(DEFAULT_SWORD_BLUEPRINT_JSON, VoxelMaterialRegistry.getGridSize());
        } catch (Exception e) {
            return new ArrayList<>(COMPILED_DEFAULT_SWORD_VOXELS);
        }
    }

    public static WeaponOffset getDefaultOffset() {
        try {
            return importBlueprint(DEFAULT_SWORD_BLUEPRINT_JSON, VoxelMaterialRegistry.getGridSize()).offset();
        } catch (Exception e) {
            return WeaponOffset.ZERO;
        }
    }

    public static List<WeaponVoxel> getDefaultBowVoxels() {
        try {
            return importFromJson(DEFAULT_BOW_BLUEPRINT_JSON, VoxelMaterialRegistry.getGridSize());
        } catch (Exception e) {
            return new ArrayList<>(COMPILED_DEFAULT_BOW_VOXELS);
        }
    }

    public static WeaponOffset getDefaultBowOffset() {
        try {
            return importBlueprint(DEFAULT_BOW_BLUEPRINT_JSON, VoxelMaterialRegistry.getGridSize()).offset();
        } catch (Exception e) {
            return WeaponOffset.ZERO;
        }
    }

    public static String exportToJson(List<WeaponVoxel> voxels, WeaponOffset offset, int gridSize, String attribution, String crafter) {
        JsonObject root = new JsonObject();
        root.addProperty("format", "blocksmith_blueprint");
        root.addProperty("version", 1);
        root.addProperty("gridSize", gridSize);
        root.addProperty("offsetX", offset.x());
        root.addProperty("offsetY", offset.y());

        // Attribution & Source
        root.addProperty("attribution", attribution != null ? attribution.trim() : "");
        root.addProperty("crafter", crafter != null ? crafter.trim() : "");

        JsonArray array = new JsonArray();
        for (WeaponVoxel v : voxels) {
            JsonObject obj = new JsonObject();
            obj.addProperty("x", v.x());
            obj.addProperty("y", v.y());
            if (v.z() != 0) obj.addProperty("z", v.z()); // Layer index; omitted for the base layer (z = 0)
            obj.addProperty("m", v.materialId());
            obj.addProperty("s", v.shade());
            array.add(obj);
        }
        root.add("voxels", array);
        return GSON.toJson(root);
    }

    public static BlueprintData importBlueprint(String jsonStr, int targetGridSize) {
        JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();
        if (!root.has("voxels")) {
            throw new IllegalArgumentException("Invalid blueprint format: missing 'voxels' array!");
        }

        int sourceGridSize = root.has("gridSize") ? root.get("gridSize").getAsInt() : 24;
        int srcOffX = root.has("offsetX") ? root.get("offsetX").getAsInt() : 0;
        int srcOffY = root.has("offsetY") ? root.get("offsetY").getAsInt() : 0;

        // Read attribution metadata with empty string fallbacks
        String attribution = root.has("attribution") ? root.get("attribution").getAsString() : "";
        String crafter = root.has("crafter") ? root.get("crafter").getAsString() : "";

        double scale = (double) targetGridSize / sourceGridSize;
        int targetOffX = (int) Math.round(srcOffX * scale);
        int targetOffY = (int) Math.round(srcOffY * scale);

        List<WeaponVoxel> voxels = scaleAndConvertVoxels(root.getAsJsonArray("voxels"), sourceGridSize, targetGridSize);
        return new BlueprintData(voxels, new WeaponOffset(targetOffX, targetOffY), attribution, crafter);
    }

    public static List<WeaponVoxel> importFromJson(String jsonStr, int targetGridSize) {
        return importBlueprint(jsonStr, targetGridSize).voxels();
    }

    /** Unique map key per grid cell AND layer, so voxels on different layers never overwrite each other. */
    private static long voxelKey(int x, int y, int z) {
        return ((long) (z - Blocksmith.MIN_LAYER) << 48) | ((long) (x & 0xFFFFFF) << 24) | (y & 0xFFFFFFL);
    }

    private static List<WeaponVoxel> scaleAndConvertVoxels(JsonArray array, int sourceGrid, int targetGrid) {
        double scale = (double) targetGrid / sourceGrid;
        java.util.Map<Long, WeaponVoxel> scaledMap = new java.util.LinkedHashMap<>();

        for (JsonElement el : array) {
            JsonObject obj = el.getAsJsonObject();
            int srcX = obj.get("x").getAsInt();
            int srcY = obj.get("y").getAsInt();
            int z = obj.has("z") ? obj.get("z").getAsInt() : 0;
            if (!WeaponVoxel.isValidLayer(z)) continue; // Outside the supported layer range
            String mat = obj.has("m") ? obj.get("m").getAsString() : "iron";
            int s = obj.has("s") ? obj.get("s").getAsInt() : 2;

            if (targetGrid == sourceGrid) {
                // Exact match: 1-to-1 mapping
                if (srcX >= 0 && srcX < targetGrid && srcY >= 0 && srcY < targetGrid) {
                    scaledMap.put(voxelKey(srcX, srcY, z), new WeaponVoxel(srcX, srcY, z, mat, s));
                }
            } else if (targetGrid > sourceGrid) {
                // Upscaling (e.g. 16 -> 64): Fills target sub-grid area to prevent hollow gaps
                int minX = (int) Math.round(srcX * scale);
                int maxX = Math.max(minX + 1, (int) Math.round((srcX + 1) * scale));
                int minY = (int) Math.round(srcY * scale);
                int maxY = Math.max(minY + 1, (int) Math.round((srcY + 1) * scale));

                for (int tx = minX; tx < maxX && tx < targetGrid; tx++) {
                    for (int ty = minY; ty < maxY && ty < targetGrid; ty++) {
                        scaledMap.put(voxelKey(tx, ty, z), new WeaponVoxel(tx, ty, z, mat, s));
                    }
                }
            } else {
                // Downscaling (e.g. 64 -> 16): Deduplicates points to fit smaller resolution
                int tx = Math.clamp((int) Math.round(srcX * scale), 0, targetGrid - 1);
                int ty = Math.clamp((int) Math.round(srcY * scale), 0, targetGrid - 1);
                scaledMap.put(voxelKey(tx, ty, z), new WeaponVoxel(tx, ty, z, mat, s));
            }
        }

        return new ArrayList<>(scaledMap.values());
    }

    public static List<WeaponVoxel> convertImageToVoxels(Image rawImage, int targetGridSize) {
        return convertImageToVoxels(rawImage, targetGridSize, ImageVoxelizer.Options.DEFAULT);
    }

    /** Converts a picture to voxels matched to the material palette; see {@link ImageVoxelizer}. */
    public static List<WeaponVoxel> convertImageToVoxels(Image rawImage, int targetGridSize, ImageVoxelizer.Options options) {
        BufferedImage img;
        if (rawImage instanceof BufferedImage bi) {
            img = bi;
        } else {
            img = new BufferedImage(Math.max(1, rawImage.getWidth(null)), Math.max(1, rawImage.getHeight(null)), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.drawImage(rawImage, 0, 0, null);
            g.dispose();
        }

        // Every shade of every material is a colour the picture may be matched to
        List<VoxelMaterial> owners = new ArrayList<>();
        List<Integer> shades = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (VoxelMaterial mat : VoxelMaterialRegistry.getAll()) {
            int[] palette = mat.getPalette();
            if (palette == null) continue;
            for (int s = 0; s < palette.length; s++) {
                owners.add(mat);
                shades.add(s);
                colors.add(palette[s] & 0xFFFFFF);
            }
        }
        if (colors.isEmpty()) throw new IllegalStateException("No materials to match the image against!");

        int[] rgb = colors.stream().mapToInt(Integer::intValue).toArray();
        List<ImageVoxelizer.Cell> cells = ImageVoxelizer.convert(img, targetGridSize, options, rgb, MAX_TOTAL_VOXELS());

        List<WeaponVoxel> list = new ArrayList<>(cells.size());
        for (ImageVoxelizer.Cell c : cells) {
            list.add(new WeaponVoxel(c.x(), c.y(), c.z(), owners.get(c.paletteIndex()).id(), shades.get(c.paletteIndex())));
        }
        return list;
    }
}