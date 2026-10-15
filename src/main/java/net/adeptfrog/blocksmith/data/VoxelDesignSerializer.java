package net.adeptfrog.blocksmith.data;

import com.google.gson.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static net.adeptfrog.blocksmith.Blocksmith.BASE_GRID_SIZE;

public class VoxelDesignSerializer {
    private static final Gson GSON = new GsonBuilder().create();

    private static final String DEFAULT_SWORD_BLUEPRINT_JSON = """
    {
      "format": "blocksmith_blueprint",
      "version": 1,
      "gridSize": 16,
      "offsetX": -1,
      "offsetY": -1,
      "attribution": "\\"Forged Sword\\" by adeptfrog.",
      "crafter": "adeptfrog",
      "voxels": [
        { "x": 8, "y": 2, "m": "flint", "s": 0 },
        { "x": 9, "y": 2, "m": "flint", "s": 0 },
        { "x": 9, "y": 3, "m": "flint", "s": 0 },
        { "x": 7, "y": 3, "m": "flint", "s": 0 },
        { "x": 6, "y": 3, "m": "flint", "s": 0 },
        { "x": 5, "y": 4, "m": "flint", "s": 0 },
        { "x": 4, "y": 3, "m": "flint", "s": 0 },
        { "x": 3, "y": 2, "m": "flint", "s": 0 },
        { "x": 2, "y": 1, "m": "flint", "s": 0 },
        { "x": 2, "y": 0, "m": "flint", "s": 0 },
        { "x": 1, "y": 0, "m": "flint", "s": 0 },
        { "x": 0, "y": 0, "m": "flint", "s": 0 },
        { "x": 0, "y": 1, "m": "flint", "s": 1 },
        { "x": 0, "y": 2, "m": "flint", "s": 1 },
        { "x": 1, "y": 2, "m": "flint", "s": 1 },
        { "x": 2, "y": 3, "m": "flint", "s": 1 },
        { "x": 3, "y": 4, "m": "flint", "s": 1 },
        { "x": 4, "y": 5, "m": "flint", "s": 1 },
        { "x": 3, "y": 6, "m": "flint", "s": 1 },
        { "x": 3, "y": 7, "m": "flint", "s": 1 },
        { "x": 2, "y": 8, "m": "flint", "s": 1 },
        { "x": 2, "y": 9, "m": "flint", "s": 1 },
        { "x": 3, "y": 9, "m": "flint", "s": 1 },
        { "x": 4, "y": 8, "m": "flint", "s": 1 },
        { "x": 8, "y": 4, "m": "flint", "s": 0 },
        { "x": 8, "y": 3, "m": "flint", "s": 1 },
        { "x": 3, "y": 8, "m": "flint", "s": 2 },
        { "x": 1, "y": 1, "m": "flint", "s": 3 },
        { "x": 2, "y": 2, "m": "flint", "s": 2 },
        { "x": 4, "y": 4, "m": "flint", "s": 2 },
        { "x": 3, "y": 3, "m": "flint", "s": 3 },
        { "x": 6, "y": 4, "m": "flint", "s": 1 },
        { "x": 7, "y": 4, "m": "flint", "s": 1 },
        { "x": 6, "y": 5, "m": "flint", "s": 1 },
        { "x": 6, "y": 6, "m": "flint", "s": 2 },
        { "x": 4, "y": 7, "m": "flint", "s": 3 },
        { "x": 4, "y": 6, "m": "flint", "s": 3 },
        { "x": 5, "y": 6, "m": "flint", "s": 3 },
        { "x": 5, "y": 5, "m": "flint", "s": 2 },
        { "x": 6, "y": 7, "m": "coal", "s": 0 },
        { "x": 7, "y": 7, "m": "coal", "s": 0 },
        { "x": 7, "y": 6, "m": "coal", "s": 0 },
        { "x": 7, "y": 8, "m": "coal", "s": 1 },
        { "x": 8, "y": 8, "m": "coal", "s": 1 },
        { "x": 8, "y": 7, "m": "coal", "s": 1 },
        { "x": 8, "y": 9, "m": "coal", "s": 1 },
        { "x": 9, "y": 9, "m": "coal", "s": 1 },
        { "x": 9, "y": 8, "m": "coal", "s": 1 },
        { "x": 10, "y": 11, "m": "coal", "s": 2 },
        { "x": 11, "y": 11, "m": "coal", "s": 2 },
        { "x": 11, "y": 10, "m": "coal", "s": 2 },
        { "x": 13, "y": 12, "m": "coal", "s": 3 },
        { "x": 13, "y": 13, "m": "coal", "s": 3 },
        { "x": 12, "y": 13, "m": "coal", "s": 3 },
        { "x": 5, "y": 7, "m": "coal", "s": 0 },
        { "x": 7, "y": 5, "m": "coal", "s": 0 },
        { "x": 8, "y": 6, "m": "coal", "s": 0 },
        { "x": 6, "y": 8, "m": "coal", "s": 0 },
        { "x": 7, "y": 9, "m": "coal", "s": 0 },
        { "x": 8, "y": 10, "m": "coal", "s": 0 },
        { "x": 9, "y": 11, "m": "coal", "s": 0 },
        { "x": 10, "y": 12, "m": "coal", "s": 0 },
        { "x": 11, "y": 13, "m": "coal", "s": 0 },
        { "x": 12, "y": 14, "m": "coal", "s": 0 },
        { "x": 13, "y": 15, "m": "coal", "s": 0 },
        { "x": 14, "y": 15, "m": "coal", "s": 0 },
        { "x": 15, "y": 15, "m": "coal", "s": 0 },
        { "x": 15, "y": 14, "m": "coal", "s": 0 },
        { "x": 15, "y": 13, "m": "coal", "s": 0 },
        { "x": 14, "y": 12, "m": "coal", "s": 0 },
        { "x": 13, "y": 11, "m": "coal", "s": 0 },
        { "x": 12, "y": 10, "m": "coal", "s": 0 },
        { "x": 11, "y": 9, "m": "coal", "s": 0 },
        { "x": 10, "y": 8, "m": "coal", "s": 0 },
        { "x": 9, "y": 7, "m": "coal", "s": 0 },
        { "x": 9, "y": 10, "m": "coal", "s": 2 },
        { "x": 10, "y": 10, "m": "coal", "s": 2 },
        { "x": 10, "y": 9, "m": "coal", "s": 2 },
        { "x": 11, "y": 12, "m": "coal", "s": 3 },
        { "x": 12, "y": 12, "m": "coal", "s": 3 },
        { "x": 12, "y": 11, "m": "coal", "s": 3 },
        { "x": 13, "y": 14, "m": "coal", "s": 4 },
        { "x": 14, "y": 14, "m": "coal", "s": 4 },
        { "x": 14, "y": 13, "m": "coal", "s": 4 }
      ]
    }
    """;

    private static final String DEFAULT_BOW_BLUEPRINT_JSON = """
    {
      "format": "blocksmith_blueprint",
      "version": 1,
      "gridSize": 16,
      "offsetX": 0,
      "offsetY": 0,
      "attribution": "\\"Forged Bow\\" by adeptfrog.",
      "crafter": "adeptfrog",
      "voxels": [
        { "x": 1, "y": 0, "m": "flint", "s": 1 },
        { "x": 2, "y": 0, "m": "flint", "s": 0 },
        { "x": 0, "y": 0, "m": "flint", "s": 1 },
        { "x": 0, "y": 1, "m": "flint", "s": 2 },
        { "x": 1, "y": 1, "m": "flint", "s": 4 },
        { "x": 2, "y": 1, "m": "flint", "s": 3 },
        { "x": 2, "y": 2, "m": "flint", "s": 3 },
        { "x": 2, "y": 3, "m": "flint", "s": 2 },
        { "x": 2, "y": 4, "m": "flint", "s": 3 },
        { "x": 1, "y": 2, "m": "flint", "s": 0 },
        { "x": 1, "y": 3, "m": "flint", "s": 0 },
        { "x": 1, "y": 4, "m": "flint", "s": 0 },
        { "x": 2, "y": 5, "m": "flint", "s": 0 },
        { "x": 2, "y": 6, "m": "flint", "s": 0 },
        { "x": 2, "y": 7, "m": "flint", "s": 0 },
        { "x": 3, "y": 1, "m": "flint", "s": 0 },
        { "x": 3, "y": 2, "m": "flint", "s": 0 },
        { "x": 3, "y": 3, "m": "flint", "s": 0 },
        { "x": 3, "y": 4, "m": "flint", "s": 0 },
        { "x": 3, "y": 6, "m": "flint", "s": 1 },
        { "x": 3, "y": 5, "m": "flint", "s": 2 },
        { "x": 3, "y": 7, "m": "flint", "s": 3 },
        { "x": 4, "y": 6, "m": "flint", "s": 0 },
        { "x": 5, "y": 7, "m": "flint", "s": 0 },
        { "x": 5, "y": 8, "m": "flint", "s": 0 },
        { "x": 4, "y": 8, "m": "flint", "s": 0 },
        { "x": 6, "y": 9, "m": "flint", "s": 0 },
        { "x": 7, "y": 10, "m": "flint", "s": 0 },
        { "x": 7, "y": 11, "m": "flint", "s": 0 },
        { "x": 8, "y": 10, "m": "flint", "s": 0 },
        { "x": 9, "y": 11, "m": "flint", "s": 0 },
        { "x": 11, "y": 12, "m": "flint", "s": 0 },
        { "x": 12, "y": 12, "m": "flint", "s": 0 },
        { "x": 13, "y": 12, "m": "flint", "s": 0 },
        { "x": 14, "y": 12, "m": "flint", "s": 0 },
        { "x": 15, "y": 13, "m": "flint", "s": 0 },
        { "x": 15, "y": 14, "m": "flint", "s": 1 },
        { "x": 15, "y": 15, "m": "flint", "s": 1 },
        { "x": 14, "y": 15, "m": "flint", "s": 2 },
        { "x": 13, "y": 14, "m": "flint", "s": 0 },
        { "x": 12, "y": 14, "m": "flint", "s": 0 },
        { "x": 11, "y": 14, "m": "flint", "s": 0 },
        { "x": 10, "y": 13, "m": "flint", "s": 0 },
        { "x": 9, "y": 13, "m": "flint", "s": 0 },
        { "x": 8, "y": 13, "m": "flint", "s": 0 },
        { "x": 8, "y": 12, "m": "flint", "s": 3 },
        { "x": 10, "y": 12, "m": "flint", "s": 2 },
        { "x": 9, "y": 12, "m": "flint", "s": 1 },
        { "x": 14, "y": 14, "m": "flint", "s": 4 },
        { "x": 14, "y": 13, "m": "flint", "s": 3 },
        { "x": 13, "y": 13, "m": "flint", "s": 3 },
        { "x": 11, "y": 13, "m": "flint", "s": 3 },
        { "x": 12, "y": 13, "m": "flint", "s": 2 },
        { "x": 4, "y": 7, "m": "flint", "s": 1 },
        { "x": 8, "y": 11, "m": "flint", "s": 1 },
        { "x": 1, "y": 8, "m": "flint", "s": 4 },
        { "x": 7, "y": 14, "m": "flint", "s": 4 },
        { "x": 2, "y": 8, "m": "flint", "s": 3 },
        { "x": 7, "y": 13, "m": "flint", "s": 3 },
        { "x": 3, "y": 9, "m": "flint", "s": 0 },
        { "x": 6, "y": 12, "m": "flint", "s": 0 },
        { "x": 4, "y": 2, "m": "flint", "s": 0 },
        { "x": 5, "y": 3, "m": "flint", "s": 0 },
        { "x": 13, "y": 11, "m": "flint", "s": 0 },
        { "x": 12, "y": 10, "m": "flint", "s": 0 },
        { "x": 6, "y": 4, "m": "flint", "s": 1 },
        { "x": 7, "y": 5, "m": "flint", "s": 1 },
        { "x": 11, "y": 9, "m": "flint", "s": 1 },
        { "x": 10, "y": 8, "m": "flint", "s": 1 },
        { "x": 8, "y": 6, "m": "flint", "s": 2 },
        { "x": 9, "y": 7, "m": "flint", "s": 2 },
        { "x": 5, "y": 10, "m": "coal", "s": 4 },
        { "x": 4, "y": 9, "m": "coal", "s": 4 },
        { "x": 6, "y": 11, "m": "coal", "s": 4 },
        { "x": 6, "y": 10, "m": "coal", "s": 3 },
        { "x": 5, "y": 9, "m": "coal", "s": 3 },
        { "x": 5, "y": 11, "m": "coal", "s": 2 },
        { "x": 4, "y": 10, "m": "coal", "s": 2 },
        { "x": 2, "y": 9, "m": "coal", "s": 2 },
        { "x": 3, "y": 8, "m": "coal", "s": 2 },
        { "x": 6, "y": 13, "m": "coal", "s": 2 },
        { "x": 7, "y": 12, "m": "coal", "s": 2 }
      ]
    }
    """;

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

    private static List<WeaponVoxel> scaleAndConvertVoxels(JsonArray array, int sourceGrid, int targetGrid) {
        double scale = (double) targetGrid / sourceGrid;
        java.util.Map<Long, WeaponVoxel> scaledMap = new java.util.LinkedHashMap<>();

        for (JsonElement el : array) {
            JsonObject obj = el.getAsJsonObject();
            int srcX = obj.get("x").getAsInt();
            int srcY = obj.get("y").getAsInt();
            String mat = obj.has("m") ? obj.get("m").getAsString() : "iron";
            int s = obj.has("s") ? obj.get("s").getAsInt() : 2;

            if (targetGrid == sourceGrid) {
                // Exact match: 1-to-1 mapping
                if (srcX >= 0 && srcX < targetGrid && srcY >= 0 && srcY < targetGrid) {
                    long key = ((long) srcX << 32) | (srcY & 0xFFFFFFFFL);
                    scaledMap.put(key, new WeaponVoxel(srcX, srcY, 0, mat, s));
                }
            } else if (targetGrid > sourceGrid) {
                // Upscaling (e.g. 16 -> 64): Fills target sub-grid area to prevent hollow gaps
                int minX = (int) Math.round(srcX * scale);
                int maxX = Math.max(minX + 1, (int) Math.round((srcX + 1) * scale));
                int minY = (int) Math.round(srcY * scale);
                int maxY = Math.max(minY + 1, (int) Math.round((srcY + 1) * scale));

                for (int tx = minX; tx < maxX && tx < targetGrid; tx++) {
                    for (int ty = minY; ty < maxY && ty < targetGrid; ty++) {
                        long key = ((long) tx << 32) | (ty & 0xFFFFFFFFL);
                        scaledMap.put(key, new WeaponVoxel(tx, ty, 0, mat, s));
                    }
                }
            } else {
                // Downscaling (e.g. 64 -> 16): Deduplicates points to fit smaller resolution
                int tx = Math.clamp((int) Math.round(srcX * scale), 0, targetGrid - 1);
                int ty = Math.clamp((int) Math.round(srcY * scale), 0, targetGrid - 1);
                long key = ((long) tx << 32) | (ty & 0xFFFFFFFFL);
                scaledMap.put(key, new WeaponVoxel(tx, ty, 0, mat, s));
            }
        }

        return new ArrayList<>(scaledMap.values());
    }

    public static List<WeaponVoxel> convertImageToVoxels(Image rawImage, int targetGridSize) {
        BufferedImage img = new BufferedImage(targetGridSize, targetGridSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.drawImage(rawImage, 0, 0, targetGridSize, targetGridSize, null);
        g.dispose();

        List<WeaponVoxel> list = new ArrayList<>();
        Collection<VoxelMaterial> materials = VoxelMaterialRegistry.getAll();

        for (int ix = 0; ix < targetGridSize; ix++) {
            for (int iy = 0; iy < targetGridSize; iy++) {
                int argb = img.getRGB(ix, iy);
                int a = (argb >> 24) & 0xFF;
                if (a < 15) continue; // Skip transparent pixels

                int r = (argb >> 16) & 0xFF;
                int gCol = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                VoxelMaterial bestMat = VoxelMaterialRegistry.FALLBACK;
                int bestShade = 2;
                double minDistance = Double.MAX_VALUE;

                for (VoxelMaterial mat : materials) {
                    int[] palette = mat.getPalette();
                    if (palette == null) continue;

                    for (int s = 0; s < palette.length; s++) {
                        int c = palette[s];
                        int cr = (c >> 16) & 0xFF;
                        int cg = (c >> 8) & 0xFF;
                        int cb = c & 0xFF;

                        double dist = 2 * Math.pow(r - cr, 2) + 4 * Math.pow(gCol - cg, 2) + 3 * Math.pow(b - cb, 2);
                        if (dist < minDistance) {
                            minDistance = dist;
                            bestMat = mat;
                            bestShade = s;
                        }
                    }
                }

                int voxelY = (targetGridSize - 1) - iy;
                list.add(new WeaponVoxel(ix, voxelY, 0, bestMat.id(), bestShade));
            }
        }

        if (list.isEmpty()) {
            throw new IllegalArgumentException("No visible pixels found in image!");
        }

        return list;
    }
}