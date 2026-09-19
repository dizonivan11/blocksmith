package net.adeptfrog.blocksmith.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.adeptfrog.blocksmith.Blocksmith;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;

public class VoxelMaterialRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("blocksmith_materials.json").toFile();

    private static final Map<String, VoxelMaterial> MATERIALS = new LinkedHashMap<>();

    public static final VoxelMaterial FALLBACK = new VoxelMaterial(
            "iron", 0.01f, 0.0f, 3,
            new int[]{0xFF707070, 0xFF9E9E9E, 0xFFD8D8D8, 0xFFEEEEEE, 0xFFFFFFFF},
            Identifier.fromNamespaceAndPath("minecraft", "iron_ingot")
    );

    private static int gridSize = Blocksmith.BASE_GRID_SIZE; // Default resolution

    static {
        load();
    }

    public static void initialize() {}

    public static void load() {
        MATERIALS.clear();

        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                com.google.gson.JsonElement element = com.google.gson.JsonParser.parseReader(reader);
                com.google.gson.JsonObject root = element.getAsJsonObject();

                if (root.has("gridSize")) {
                    gridSize = Math.clamp(root.get("gridSize").getAsInt(), 16, 64);
                }

                if (root.has("materials")) {
                    Type type = new TypeToken<List<MaterialConfigEntry>>() {}.getType();
                    List<MaterialConfigEntry> entries = GSON.fromJson(root.get("materials"), type);
                    if (entries != null) {
                        for (MaterialConfigEntry e : entries) {
                            MATERIALS.put(e.id.toLowerCase(), e.toMaterial());
                        }
                        return;
                    }
                }
            } catch (Exception e) {
                System.err.println("[Blocksmith] Failed to load config, falling back to defaults: " + e.getMessage());
            }
        }

        loadDefaults();
        save();
    }

    public static void save() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                com.google.gson.JsonObject root = new com.google.gson.JsonObject();

                // Grid Size
                root.addProperty("gridSize", gridSize);

                // Materials
                List<MaterialConfigEntry> entries = new ArrayList<>();
                for (VoxelMaterial mat : MATERIALS.values()) {
                    entries.add(MaterialConfigEntry.fromMaterial(mat));
                }
                root.add("materials", GSON.toJsonTree(entries));

                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            System.err.println("[Blocksmith] Failed to save config: " + e.getMessage());
        }
    }

    public static void resetToDefaults() {
        MATERIALS.clear();
        loadDefaults();
        save();
    }

    private static void loadDefaults() {
        register(new VoxelMaterial("iron", 1f, 0f, 512,
                new int[]{0xFF353535, 0xFF5E5E5E, 0xFFA8A8A8, 0xFFD8D8D8, 0xFFFFFFFF},
                Identifier.fromNamespaceAndPath("minecraft", "iron_ingot")));

        register(new VoxelMaterial("copper", 1f, 0f, 256,
                new int[]{0xFF6D3421, 0xFF9C4529, 0xFFC15A36, 0xFFE77C56, 0xFFFC9982},
                Identifier.fromNamespaceAndPath("minecraft", "copper_ingot")));

        register(new VoxelMaterial("lazuli", 1.5f, 0.5f, 256,
                new int[]{0xFF052463, 0xFF1A3D8F, 0xFF345EC3, 0xFF5A82E2, 0xFF7497EA},
                Identifier.fromNamespaceAndPath("minecraft", "lapis_lazuli")));

        register(new VoxelMaterial("prismarine", 4f, 0.5f, 256,
                new int[]{0xFF49645C, 0xFF72A498, 0xFF91C5B7, 0xFFB4D8CA, 0xFFDFE9DC},
                Identifier.fromNamespaceAndPath("minecraft", "prismarine_crystals")));

        register(new VoxelMaterial("gold", -3f, 5f, 128,
                new int[]{0xFF752802, 0xFFB26411, 0xFFE9B115, 0xFFFAD64A, 0xFFFDF55F},
                Identifier.fromNamespaceAndPath("minecraft", "gold_ingot")));

        register(new VoxelMaterial("diamond", 4f, 1f, 2048,
                new int[]{0xFF11727A, 0xFF1C919A, 0xFF20C5B5, 0xFF4AEDD9, 0xFFA1FBE8},
                Identifier.fromNamespaceAndPath("minecraft", "diamond")));

        register(new VoxelMaterial("netherite", 6f, 0.5f, 4096,
                new int[]{0xFF111111, 0xFF31292A, 0xFF3B393B, 0xFF5A575A, 0xFF737173},
                Identifier.fromNamespaceAndPath("minecraft", "netherite_ingot")));

        register(new VoxelMaterial("emerald", 1f, 0.1f, 256,
                new int[]{0xFF005300, 0xFF007B18, 0xFF00AA2C, 0xFF17DD62, 0xFFAFFDCD},
                Identifier.fromNamespaceAndPath("minecraft", "emerald")));

        register(new VoxelMaterial("amethyst", 3f, 0.15f, 1024,
                new int[]{0xFF54398A, 0xFF6F4FAB, 0xFF8D6ACC, 0xFFB38EF3, 0xFFFECBE6},
                Identifier.fromNamespaceAndPath("minecraft", "amethyst_shard")));

        register(new VoxelMaterial("ender", 10f, -3f, -1024,
                new int[]{0xFF2A1138, 0xFF4D1C68, 0xFF8A38B0, 0xFFB762D8, 0xFFE3A6FF},
                Identifier.fromNamespaceAndPath("minecraft", "end_crystal")));

        register(new VoxelMaterial("redstone", 5f, 0.15f, 512,
                new int[]{0xFF410500, 0xFF5C0700, 0xFF720000, 0xFFAA0F01, 0xFFFF0000},
                Identifier.fromNamespaceAndPath("minecraft", "redstone_block")));
    }

    public static void register(VoxelMaterial mat) {
        MATERIALS.put(mat.id().toLowerCase(), mat);
    }

    public static void remove(String id) {
        if (id != null) {
            MATERIALS.remove(id.toLowerCase());
            save();
        }
    }

    public static VoxelMaterial getOrDefault(String id) {
        return MATERIALS.getOrDefault(id != null ? id.toLowerCase() : "", FALLBACK);
    }

    public static int getGridSize() {
        return gridSize;
    }
    public static void setGridSize(int size) {
        gridSize = Math.clamp(size, 16, 64);
        save();
    }

    public static Collection<VoxelMaterial> getAll() {
        return Collections.unmodifiableCollection(MATERIALS.values());
    }

    public static class MaterialConfigEntry {
        public String id;
        public float bonusDamage;
        public float bonusSpeed;
        public int bonusDurability;
        public int[] palette;
        public String iconItem;

        public static MaterialConfigEntry fromMaterial(VoxelMaterial m) {
            MaterialConfigEntry e = new MaterialConfigEntry();
            e.id = m.id();
            e.bonusDamage = m.bonusDamage();
            e.bonusSpeed = m.bonusSpeed();
            e.bonusDurability = m.bonusDurability();
            e.palette = m.palette();
            e.iconItem = m.iconItem().toString();
            return e;
        }

        public VoxelMaterial toMaterial() {
            return new VoxelMaterial(
                    id, bonusDamage, bonusSpeed, bonusDurability, palette,
                    Identifier.parse(iconItem != null ? iconItem : "minecraft:iron_ingot")
            );
        }
    }
}