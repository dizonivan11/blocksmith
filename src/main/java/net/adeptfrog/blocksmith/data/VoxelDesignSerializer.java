package net.adeptfrog.blocksmith.data;

import com.google.gson.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VoxelDesignSerializer {
    private static final Gson GSON = new GsonBuilder().create();

    // 1. Embedded Default Sword Blueprint
    private static final String DEFAULT_SWORD_BLUEPRINT_JSON = """
    {
      "format": "blocksmith_blueprint",
      "version": 1,
      "voxels": [
        { "x": 16, "y": 17, "m": "iron", "s": 2 },
        { "x": 17, "y": 16, "m": "iron", "s": 2 },
        { "x": 18, "y": 17, "m": "iron", "s": 2 },
        { "x": 19, "y": 18, "m": "iron", "s": 2 },
        { "x": 16, "y": 16, "m": "iron", "s": 2 },
        { "x": 22, "y": 22, "m": "iron", "s": 4 },
        { "x": 22, "y": 21, "m": "iron", "s": 4 },
        { "x": 21, "y": 21, "m": "iron", "s": 4 },
        { "x": 21, "y": 20, "m": "iron", "s": 4 },
        { "x": 20, "y": 19, "m": "iron", "s": 4 },
        { "x": 14, "y": 15, "m": "iron", "s": 3 },
        { "x": 14, "y": 14, "m": "iron", "s": 2 },
        { "x": 15, "y": 14, "m": "iron", "s": 2 },
        { "x": 15, "y": 15, "m": "iron", "s": 3 },
        { "x": 15, "y": 16, "m": "iron", "s": 3 },
        { "x": 16, "y": 15, "m": "iron", "s": 3 },
        { "x": 20, "y": 21, "m": "iron", "s": 2 },
        { "x": 21, "y": 22, "m": "iron", "s": 2 },
        { "x": 20, "y": 20, "m": "iron", "s": 2 },
        { "x": 4, "y": 4, "m": "iron", "s": 2 },
        { "x": 3, "y": 3, "m": "iron", "s": 2 },
        { "x": 1, "y": 1, "m": "iron", "s": 1 },
        { "x": 2, "y": 2, "m": "iron", "s": 2 },
        { "x": 9, "y": 8, "m": "iron", "s": 2 },
        { "x": 8, "y": 9, "m": "iron", "s": 2 },
        { "x": 9, "y": 9, "m": "iron", "s": 2 },
        { "x": 10, "y": 9, "m": "iron", "s": 2 },
        { "x": 13, "y": 14, "m": "iron", "s": 2 },
        { "x": 19, "y": 19, "m": "iron", "s": 2 },
        { "x": 18, "y": 18, "m": "iron", "s": 2 },
        { "x": 17, "y": 17, "m": "iron", "s": 2 },
        { "x": 14, "y": 13, "m": "iron", "s": 1 },
        { "x": 9, "y": 11, "m": "iron", "s": 3 },
        { "x": 10, "y": 11, "m": "iron", "s": 3 },
        { "x": 12, "y": 13, "m": "iron", "s": 4 },
        { "x": 17, "y": 18, "m": "iron", "s": 4 },
        { "x": 18, "y": 19, "m": "iron", "s": 4 },
        { "x": 19, "y": 20, "m": "iron", "s": 4 },
        { "x": 9, "y": 7, "m": "iron", "s": 2 },
        { "x": 7, "y": 9, "m": "iron", "s": 2 },
        { "x": 8, "y": 10, "m": "iron", "s": 2 },
        { "x": 10, "y": 8, "m": "iron", "s": 2 },
        { "x": 10, "y": 10, "m": "iron", "s": 2 },
        { "x": 9, "y": 10, "m": "iron", "s": 2 },
        { "x": 11, "y": 11, "m": "iron", "s": 3 },
        { "x": 11, "y": 10, "m": "iron", "s": 3 },
        { "x": 11, "y": 9, "m": "iron", "s": 3 },
        { "x": 11, "y": 12, "m": "iron", "s": 3 },
        { "x": 12, "y": 12, "m": "iron", "s": 3 },
        { "x": 12, "y": 11, "m": "iron", "s": 3 },
        { "x": 13, "y": 13, "m": "iron", "s": 4 },
        { "x": 13, "y": 12, "m": "iron", "s": 4 },
        { "x": 2, "y": 0, "m": "copper", "s": 0 },
        { "x": 1, "y": 0, "m": "copper", "s": 0 },
        { "x": 0, "y": 0, "m": "copper", "s": 0 },
        { "x": 0, "y": 1, "m": "copper", "s": 0 },
        { "x": 0, "y": 2, "m": "copper", "s": 0 },
        { "x": 1, "y": 2, "m": "copper", "s": 0 },
        { "x": 2, "y": 3, "m": "copper", "s": 0 },
        { "x": 2, "y": 1, "m": "copper", "s": 0 },
        { "x": 3, "y": 2, "m": "copper", "s": 0 },
        { "x": 4, "y": 3, "m": "copper", "s": 0 },
        { "x": 3, "y": 4, "m": "copper", "s": 0 },
        { "x": 4, "y": 5, "m": "copper", "s": 0 },
        { "x": 4, "y": 6, "m": "copper", "s": 0 },
        { "x": 3, "y": 7, "m": "copper", "s": 0 },
        { "x": 2, "y": 8, "m": "copper", "s": 0 },
        { "x": 1, "y": 9, "m": "copper", "s": 0 },
        { "x": 2, "y": 10, "m": "copper", "s": 0 },
        { "x": 3, "y": 10, "m": "copper", "s": 0 },
        { "x": 4, "y": 9, "m": "copper", "s": 0 },
        { "x": 5, "y": 9, "m": "copper", "s": 0 },
        { "x": 6, "y": 9, "m": "copper", "s": 0 },
        { "x": 6, "y": 10, "m": "copper", "s": 0 },
        { "x": 7, "y": 10, "m": "copper", "s": 0 },
        { "x": 8, "y": 11, "m": "copper", "s": 0 },
        { "x": 8, "y": 12, "m": "copper", "s": 0 },
        { "x": 10, "y": 12, "m": "copper", "s": 0 },
        { "x": 9, "y": 12, "m": "copper", "s": 0 },
        { "x": 11, "y": 13, "m": "copper", "s": 0 },
        { "x": 12, "y": 14, "m": "copper", "s": 0 },
        { "x": 13, "y": 15, "m": "copper", "s": 0 },
        { "x": 14, "y": 16, "m": "copper", "s": 0 },
        { "x": 15, "y": 17, "m": "copper", "s": 0 },
        { "x": 16, "y": 18, "m": "copper", "s": 0 },
        { "x": 17, "y": 19, "m": "copper", "s": 0 },
        { "x": 18, "y": 20, "m": "copper", "s": 0 },
        { "x": 19, "y": 21, "m": "copper", "s": 0 },
        { "x": 20, "y": 22, "m": "copper", "s": 0 },
        { "x": 21, "y": 23, "m": "copper", "s": 0 },
        { "x": 22, "y": 23, "m": "copper", "s": 0 },
        { "x": 23, "y": 23, "m": "copper", "s": 0 },
        { "x": 23, "y": 22, "m": "copper", "s": 0 },
        { "x": 23, "y": 21, "m": "copper", "s": 0 },
        { "x": 22, "y": 20, "m": "copper", "s": 0 },
        { "x": 21, "y": 19, "m": "copper", "s": 0 },
        { "x": 20, "y": 18, "m": "copper", "s": 0 },
        { "x": 19, "y": 17, "m": "copper", "s": 0 },
        { "x": 18, "y": 16, "m": "copper", "s": 0 },
        { "x": 17, "y": 15, "m": "copper", "s": 0 },
        { "x": 16, "y": 14, "m": "copper", "s": 0 },
        { "x": 15, "y": 13, "m": "copper", "s": 0 },
        { "x": 14, "y": 12, "m": "copper", "s": 0 },
        { "x": 13, "y": 11, "m": "copper", "s": 0 },
        { "x": 12, "y": 10, "m": "copper", "s": 0 },
        { "x": 12, "y": 9, "m": "copper", "s": 0 },
        { "x": 12, "y": 8, "m": "copper", "s": 0 },
        { "x": 11, "y": 8, "m": "copper", "s": 0 },
        { "x": 10, "y": 7, "m": "copper", "s": 0 },
        { "x": 10, "y": 6, "m": "copper", "s": 0 },
        { "x": 9, "y": 6, "m": "copper", "s": 0 },
        { "x": 9, "y": 5, "m": "copper", "s": 0 },
        { "x": 9, "y": 4, "m": "copper", "s": 0 },
        { "x": 10, "y": 3, "m": "copper", "s": 0 },
        { "x": 10, "y": 2, "m": "copper", "s": 0 },
        { "x": 9, "y": 1, "m": "copper", "s": 0 },
        { "x": 8, "y": 2, "m": "copper", "s": 0 },
        { "x": 7, "y": 3, "m": "copper", "s": 0 },
        { "x": 6, "y": 4, "m": "copper", "s": 0 },
        { "x": 5, "y": 4, "m": "copper", "s": 0 },
        { "x": 9, "y": 2, "m": "copper", "s": 1 },
        { "x": 7, "y": 4, "m": "copper", "s": 1 },
        { "x": 4, "y": 7, "m": "copper", "s": 1 },
        { "x": 3, "y": 8, "m": "copper", "s": 1 },
        { "x": 5, "y": 7, "m": "copper", "s": 3 },
        { "x": 4, "y": 8, "m": "copper", "s": 3 },
        { "x": 5, "y": 8, "m": "copper", "s": 3 },
        { "x": 8, "y": 5, "m": "copper", "s": 3 },
        { "x": 8, "y": 4, "m": "copper", "s": 3 },
        { "x": 7, "y": 5, "m": "copper", "s": 3 },
        { "x": 8, "y": 8, "m": "copper", "s": 4 },
        { "x": 7, "y": 8, "m": "copper", "s": 4 },
        { "x": 8, "y": 7, "m": "copper", "s": 4 },
        { "x": 6, "y": 8, "m": "copper", "s": 4 },
        { "x": 8, "y": 6, "m": "copper", "s": 4 },
        { "x": 3, "y": 9, "m": "copper", "s": 2 },
        { "x": 2, "y": 9, "m": "copper", "s": 2 },
        { "x": 9, "y": 3, "m": "copper", "s": 2 },
        { "x": 8, "y": 3, "m": "copper", "s": 2 },
        { "x": 5, "y": 5, "m": "emerald", "s": 1 },
        { "x": 5, "y": 6, "m": "emerald", "s": 2 },
        { "x": 6, "y": 6, "m": "emerald", "s": 3 },
        { "x": 6, "y": 5, "m": "emerald", "s": 1 }
      ]
    }
    """;

    // 2. Embedded Default Bow Blueprint (144 Voxels)
    private static final String DEFAULT_BOW_BLUEPRINT_JSON = """
    {
      "format": "blocksmith_blueprint",
      "version": 1,
      "voxels": [
        { "x": 4, "y": 0, "m": "copper", "s": 0 },
        { "x": 3, "y": 0, "m": "copper", "s": 0 },
        { "x": 2, "y": 0, "m": "copper", "s": 0 },
        { "x": 2, "y": 4, "m": "copper", "s": 0 },
        { "x": 2, "y": 3, "m": "copper", "s": 0 },
        { "x": 2, "y": 2, "m": "copper", "s": 0 },
        { "x": 2, "y": 1, "m": "copper", "s": 0 },
        { "x": 23, "y": 19, "m": "copper", "s": 0 },
        { "x": 23, "y": 20, "m": "copper", "s": 0 },
        { "x": 23, "y": 21, "m": "copper", "s": 0 },
        { "x": 19, "y": 21, "m": "copper", "s": 0 },
        { "x": 20, "y": 21, "m": "copper", "s": 0 },
        { "x": 21, "y": 21, "m": "copper", "s": 0 },
        { "x": 22, "y": 21, "m": "copper", "s": 0 },
        { "x": 3, "y": 5, "m": "copper", "s": 0 },
        { "x": 3, "y": 6, "m": "copper", "s": 0 },
        { "x": 4, "y": 7, "m": "copper", "s": 0 },
        { "x": 4, "y": 8, "m": "copper", "s": 0 },
        { "x": 5, "y": 9, "m": "copper", "s": 0 },
        { "x": 5, "y": 10, "m": "copper", "s": 0 },
        { "x": 6, "y": 11, "m": "copper", "s": 0 },
        { "x": 6, "y": 12, "m": "copper", "s": 0 },
        { "x": 7, "y": 13, "m": "copper", "s": 0 },
        { "x": 8, "y": 14, "m": "copper", "s": 0 },
        { "x": 9, "y": 15, "m": "copper", "s": 0 },
        { "x": 10, "y": 16, "m": "copper", "s": 0 },
        { "x": 11, "y": 17, "m": "copper", "s": 0 },
        { "x": 12, "y": 17, "m": "copper", "s": 0 },
        { "x": 13, "y": 18, "m": "copper", "s": 0 },
        { "x": 14, "y": 18, "m": "copper", "s": 0 },
        { "x": 15, "y": 19, "m": "copper", "s": 0 },
        { "x": 16, "y": 19, "m": "copper", "s": 0 },
        { "x": 17, "y": 20, "m": "copper", "s": 0 },
        { "x": 18, "y": 20, "m": "copper", "s": 0 },
        { "x": 22, "y": 19, "m": "copper", "s": 0 },
        { "x": 21, "y": 19, "m": "copper", "s": 0 },
        { "x": 20, "y": 18, "m": "copper", "s": 0 },
        { "x": 19, "y": 18, "m": "copper", "s": 0 },
        { "x": 18, "y": 17, "m": "copper", "s": 0 },
        { "x": 17, "y": 17, "m": "copper", "s": 0 },
        { "x": 16, "y": 16, "m": "copper", "s": 0 },
        { "x": 15, "y": 16, "m": "copper", "s": 0 },
        { "x": 14, "y": 15, "m": "copper", "s": 0 },
        { "x": 13, "y": 14, "m": "copper", "s": 0 },
        { "x": 13, "y": 13, "m": "copper", "s": 0 },
        { "x": 12, "y": 12, "m": "copper", "s": 0 },
        { "x": 11, "y": 11, "m": "copper", "s": 0 },
        { "x": 10, "y": 10, "m": "copper", "s": 0 },
        { "x": 9, "y": 10, "m": "copper", "s": 0 },
        { "x": 8, "y": 9, "m": "copper", "s": 0 },
        { "x": 7, "y": 8, "m": "copper", "s": 0 },
        { "x": 7, "y": 7, "m": "copper", "s": 0 },
        { "x": 6, "y": 6, "m": "copper", "s": 0 },
        { "x": 6, "y": 5, "m": "copper", "s": 0 },
        { "x": 5, "y": 4, "m": "copper", "s": 0 },
        { "x": 5, "y": 3, "m": "copper", "s": 0 },
        { "x": 4, "y": 2, "m": "copper", "s": 0 },
        { "x": 4, "y": 1, "m": "copper", "s": 0 },
        { "x": 3, "y": 1, "m": "copper", "s": 2 },
        { "x": 4, "y": 3, "m": "copper", "s": 2 },
        { "x": 5, "y": 5, "m": "copper", "s": 2 },
        { "x": 6, "y": 7, "m": "copper", "s": 2 },
        { "x": 7, "y": 10, "m": "copper", "s": 2 },
        { "x": 12, "y": 14, "m": "copper", "s": 2 },
        { "x": 13, "y": 15, "m": "copper", "s": 2 },
        { "x": 15, "y": 17, "m": "copper", "s": 2 },
        { "x": 18, "y": 18, "m": "copper", "s": 2 },
        { "x": 17, "y": 18, "m": "copper", "s": 2 },
        { "x": 20, "y": 19, "m": "copper", "s": 2 },
        { "x": 19, "y": 19, "m": "copper", "s": 2 },
        { "x": 3, "y": 4, "m": "copper", "s": 1 },
        { "x": 4, "y": 6, "m": "copper", "s": 1 },
        { "x": 5, "y": 8, "m": "copper", "s": 1 },
        { "x": 6, "y": 10, "m": "copper", "s": 1 },
        { "x": 7, "y": 12, "m": "copper", "s": 1 },
        { "x": 8, "y": 13, "m": "copper", "s": 1 },
        { "x": 9, "y": 14, "m": "copper", "s": 1 },
        { "x": 10, "y": 15, "m": "copper", "s": 1 },
        { "x": 11, "y": 16, "m": "copper", "s": 1 },
        { "x": 12, "y": 16, "m": "copper", "s": 1 },
        { "x": 13, "y": 17, "m": "copper", "s": 1 },
        { "x": 14, "y": 17, "m": "copper", "s": 1 },
        { "x": 15, "y": 18, "m": "copper", "s": 1 },
        { "x": 16, "y": 18, "m": "copper", "s": 1 },
        { "x": 18, "y": 19, "m": "copper", "s": 1 },
        { "x": 17, "y": 19, "m": "copper", "s": 1 },
        { "x": 19, "y": 20, "m": "copper", "s": 1 },
        { "x": 20, "y": 20, "m": "copper", "s": 1 },
        { "x": 21, "y": 20, "m": "copper", "s": 1 },
        { "x": 22, "y": 20, "m": "copper", "s": 1 },
        { "x": 13, "y": 16, "m": "copper", "s": 3 },
        { "x": 10, "y": 14, "m": "copper", "s": 3 },
        { "x": 9, "y": 13, "m": "copper", "s": 3 },
        { "x": 8, "y": 12, "m": "copper", "s": 3 },
        { "x": 7, "y": 11, "m": "copper", "s": 3 },
        { "x": 8, "y": 11, "m": "copper", "s": 2 },
        { "x": 14, "y": 16, "m": "copper", "s": 4 },
        { "x": 16, "y": 17, "m": "copper", "s": 4 },
        { "x": 10, "y": 11, "m": "copper", "s": 4 },
        { "x": 9, "y": 11, "m": "copper", "s": 4 },
        { "x": 8, "y": 10, "m": "copper", "s": 4 },
        { "x": 7, "y": 9, "m": "copper", "s": 4 },
        { "x": 12, "y": 13, "m": "copper", "s": 4 },
        { "x": 11, "y": 12, "m": "copper", "s": 4 },
        { "x": 6, "y": 9, "m": "copper", "s": 3 },
        { "x": 5, "y": 7, "m": "copper", "s": 3 },
        { "x": 4, "y": 5, "m": "copper", "s": 3 },
        { "x": 4, "y": 4, "m": "copper", "s": 3 },
        { "x": 3, "y": 3, "m": "copper", "s": 3 },
        { "x": 3, "y": 2, "m": "copper", "s": 3 },
        { "x": 5, "y": 6, "m": "copper", "s": 4 },
        { "x": 6, "y": 8, "m": "copper", "s": 4 },
        { "x": 12, "y": 15, "m": "copper", "s": 3 },
        { "x": 11, "y": 15, "m": "copper", "s": 4 },
        { "x": 5, "y": 11, "m": "copper", "s": 0 },
        { "x": 12, "y": 18, "m": "copper", "s": 0 },
        { "x": 5, "y": 12, "m": "copper", "s": 0 },
        { "x": 11, "y": 18, "m": "copper", "s": 0 },
        { "x": 5, "y": 13, "m": "copper", "s": 0 },
        { "x": 10, "y": 18, "m": "copper", "s": 0 },
        { "x": 5, "y": 14, "m": "copper", "s": 0 },
        { "x": 9, "y": 18, "m": "copper", "s": 0 },
        { "x": 6, "y": 1, "m": "iron", "s": 0 },
        { "x": 7, "y": 2, "m": "iron", "s": 0 },
        { "x": 8, "y": 3, "m": "iron", "s": 1 },
        { "x": 9, "y": 4, "m": "iron", "s": 1 },
        { "x": 10, "y": 5, "m": "iron", "s": 1 },
        { "x": 11, "y": 6, "m": "iron", "s": 1 },
        { "x": 12, "y": 7, "m": "iron", "s": 2 },
        { "x": 13, "y": 8, "m": "iron", "s": 2 },
        { "x": 14, "y": 9, "m": "iron", "s": 2 },
        { "x": 15, "y": 10, "m": "iron", "s": 2 },
        { "x": 16, "y": 11, "m": "iron", "s": 2 },
        { "x": 17, "y": 12, "m": "iron", "s": 2 },
        { "x": 18, "y": 13, "m": "iron", "s": 3 },
        { "x": 19, "y": 14, "m": "iron", "s": 3 },
        { "x": 20, "y": 15, "m": "iron", "s": 3 },
        { "x": 21, "y": 16, "m": "iron", "s": 4 },
        { "x": 22, "y": 17, "m": "iron", "s": 4 },
        { "x": 23, "y": 18, "m": "iron", "s": 4 },
        { "x": 5, "y": 0, "m": "iron", "s": 0 }
      ]
    }
    """;

    private static final List<WeaponVoxel> COMPILED_DEFAULT_SWORD_VOXELS;
    private static final List<WeaponVoxel> COMPILED_DEFAULT_BOW_VOXELS;

    static {
        List<WeaponVoxel> swordList;
        List<WeaponVoxel> bowList;
        try {
            swordList = importFromJson(DEFAULT_SWORD_BLUEPRINT_JSON);
        } catch (Exception e) {
            swordList = Collections.emptyList();
            System.err.println("[Blocksmith] Failed to compile default sword blueprint: " + e.getMessage());
        }

        try {
            bowList = importFromJson(DEFAULT_BOW_BLUEPRINT_JSON);
        } catch (Exception e) {
            bowList = Collections.emptyList();
            System.err.println("[Blocksmith] Failed to compile default bow blueprint: " + e.getMessage());
        }

        COMPILED_DEFAULT_SWORD_VOXELS = Collections.unmodifiableList(swordList);
        COMPILED_DEFAULT_BOW_VOXELS = Collections.unmodifiableList(bowList);
    }

    public static List<WeaponVoxel> getDefaultVoxels() {
        return new ArrayList<>(COMPILED_DEFAULT_SWORD_VOXELS);
    }

    public static List<WeaponVoxel> getDefaultBowVoxels() {
        return new ArrayList<>(COMPILED_DEFAULT_BOW_VOXELS);
    }

    public static String exportToJson(List<WeaponVoxel> voxels) {
        JsonObject root = new JsonObject();
        root.addProperty("format", "blocksmith_blueprint");
        root.addProperty("version", 1);

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

    public static List<WeaponVoxel> importFromJson(String jsonStr) throws Exception {
        JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();
        if (!root.has("voxels")) {
            throw new IllegalArgumentException("Invalid blueprint format: missing 'voxels' array!");
        }

        JsonArray array = root.getAsJsonArray("voxels");
        List<WeaponVoxel> list = new ArrayList<>();

        for (JsonElement el : array) {
            JsonObject obj = el.getAsJsonObject();
            int x = obj.get("x").getAsInt();
            int y = obj.get("y").getAsInt();
            String mat = obj.has("m") ? obj.get("m").getAsString() : "iron";
            int s = obj.has("s") ? obj.get("s").getAsInt() : 2;

            if (x >= 0 && x < 24 && y >= 0 && y < 24) {
                list.add(new WeaponVoxel(x, y, 0, mat, s));
            }
        }

        return list;
    }
}