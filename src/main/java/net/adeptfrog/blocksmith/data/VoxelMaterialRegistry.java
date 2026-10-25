package net.adeptfrog.blocksmith.data;

import net.adeptfrog.blocksmith.compat.McCompat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.adeptfrog.blocksmith.Blocksmith;
import net.fabricmc.loader.api.FabricLoader;

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
            "iron", 0f, 0f, 0,
            new int[]{0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF},
            "minecraft:iron_ingot"
    );

    private static int gridSize = Blocksmith.BASE_GRID_SIZE; // Default resolution
    private static boolean abilitiesDisabled = false;

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

                if (root.has("abilitiesDisabled")) {
                    abilitiesDisabled = root.get("abilitiesDisabled").getAsBoolean();
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

    private static volatile int version;

    /** Changes whenever the material list or the grid size changes (cached meshes and stats use it to refresh). */
    public static int version() {
        return version;
    }

    public static void save() {
        version++;
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                com.google.gson.JsonObject root = new com.google.gson.JsonObject();

                // Grid Size
                root.addProperty("gridSize", gridSize);
                root.addProperty("abilitiesDisabled", abilitiesDisabled);

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
        register(new VoxelMaterial("netherite", 4.1f, -0.2f, 4098,
                new int[]{0xFF000000, 0xFF131313, 0xFF262626, 0xFF393939, 0xFF4c4c4c},
                "minecraft:netherite_ingot"));

        register(new VoxelMaterial("obsidian", 3.4f, -0.11f, 2048,
                new int[]{0xFF0e0d15, 0xFF181625, 0xFF222034, 0xFF434152, 0xFF646271},
                "minecraft:obsidian"));

        register(new VoxelMaterial("chorus", 2.3f, 0.5f, 128,
                new int[]{0xFF1c1018, 0xFF301c2a, 0xFF45283c, 0xFF614859, 0xFF7c6876},
                "minecraft:chorus_fruit"));

        register(new VoxelMaterial("charge", 4.5f, -0.27f, 0,
                new int[]{0xFF291714, 0xFF482822, 0xFF663931, 0xFF7d5750, 0xFF94746e},
                "minecraft:fire_charge"));

        register(new VoxelMaterial("copper", 1.2f, 0.19f, 512,
                new int[]{0xFF392218, 0xFF643c29, 0xFF8f563b, 0xFFa06f58, 0xFFb08875},
                "minecraft:copper_ingot"));

        register(new VoxelMaterial("glowstone", 0.0f, 0.3f, -1024,
                new int[]{0xFF592d0f, 0xFF9d4f1b, 0xFFdf7126, 0xFFe48646, 0xFFe99b67},
                "minecraft:glowstone_dust"));

        register(new VoxelMaterial("blaze", 2.5f, 0.1f, 128,
                new int[]{0xFF574029, 0xFF987048, 0xFFd9a066, 0xFFdfae7d, 0xFFe4bc94},
                "minecraft:blaze_rod"));

        register(new VoxelMaterial("bone", 1.1f, 0.3f, -1024,
                new int[]{0xFF5f4e3e, 0xFFa7896c, 0xFFeec39a, 0xFFf1cca9, 0xFFf3d5b8},
                "minecraft:bone"));

        register(new VoxelMaterial("gold", -5.0f, 5.0f, -2048,
                new int[]{0xFF646116, 0xFFb0aa26, 0xFFfbf236, 0xFFfcf354, 0xFFfcf672},
                "minecraft:gold_ingot"));

        register(new VoxelMaterial("moss", -5.0f, 1.0f, -4098,
                new int[]{0xFF3d5c20, 0xFF6ba138, 0xFF99e550, 0xFFa8e96a, 0xFFb7ed84},
                "minecraft:moss_block"));

        register(new VoxelMaterial("emerald", 3.0f, 0.3f, 512,
                new int[]{0xFF2a4c13, 0xFF4a8522, 0xFF6abe30, 0xFF80c84f, 0xFF96d16e},
                "minecraft:emerald"));

        register(new VoxelMaterial("prismarine", 2.5f, 0.25f, 512,
                new int[]{0xFF163b2c, 0xFF27684d, 0xFF37946e, 0xFF55a484, 0xFF73b499},
                "minecraft:prismarine_shard"));

        register(new VoxelMaterial("slime", -0.6f, 0.51f, -2048,
                new int[]{0xFF1e2a13, 0xFF354a21, 0xFF4b692f, 0xFF667f4e, 0xFF81966d},
                "minecraft:slime_ball"));

        register(new VoxelMaterial("kelp", 0.1f, 0.01f, 0,
                new int[]{0xFF211e0e, 0xFF3a3519, 0xFF524b24, 0xFF6c6645, 0xFF868165},
                "minecraft:kelp"));

        register(new VoxelMaterial("flint", 1.0f, 0.1f, 64,
                new int[]{0xFF141817, 0xFF232a28, 0xFF323c39, 0xFF515957, 0xFF6f7674},
                "minecraft:flint"));

        register(new VoxelMaterial("lazuli", 2.0f, 0.2f, 1024,
                new int[]{0xFF19192e, 0xFF2c2c51, 0xFF3f3f74, 0xFF5c5c89, 0xFF78789d},
                "minecraft:lapis_lazuli"));

        register(new VoxelMaterial("glow", -1.0f, 0.5f, 16,
                new int[]{0xFF132634, 0xFF22435b, 0xFF306082, 0xFF4f7895, 0xFF6e8fa7},
                "minecraft:glow_ink_sac"));

        register(new VoxelMaterial("glacier", 5.0f, -0.6f, -512,
                new int[]{0xFF242c5a, 0xFF404d9e, 0xFF5b6ee1, 0xFF7384e5, 0xFF8c99ea},
                "minecraft:blue_ice"));

        register(new VoxelMaterial("ice", 5.0f, -0.6f, -1024,
                new int[]{0xFF283e66, 0xFF456db3, 0xFF639bff, 0xFF7aaaff, 0xFF8c99ea},
                "minecraft:ice"));

        register(new VoxelMaterial("diamond", 3.5f, 0.25f, 2048,
                new int[]{0xFF26525b, 0xFF4390a0, 0xFF5fcde4, 0xFF77d4e8, 0xFF8fdcec},
                "minecraft:diamond"));

        register(new VoxelMaterial("snow", -1.0f, 1.0f, -4098,
                new int[]{0xFF515865, 0xFF8e9ab1, 0xFFcbdbfc, 0xFFd3e0fc, 0xFFdae6fd},
                "minecraft:snowball"));

        register(new VoxelMaterial("quartz", 2.5f, 0.25f, 8,
                new int[]{0xFF666666, 0xFF8c8c8c, 0xFFb3b3b3, 0xFFd9d9d9, 0xFFFFFFFF},
                "minecraft:quartz"));

        register(new VoxelMaterial("iron", 2.0f, 0.2f, 1024,
                new int[]{0xFF3e4549, 0xFF6d7980, 0xFF9badb7, 0xFFaab9c2, 0xFFb9c5cc},
                "minecraft:iron_ingot"));

        register(new VoxelMaterial("clay", 0.5f, 0.05f, -64,
                new int[]{0xFF353236, 0xFF5d585f, 0xFF847e87, 0xFF969199, 0xFFa9a4ab},
                "minecraft:clay_ball"));

        register(new VoxelMaterial("gunpowder", 5.0f, -0.5f, 0,
                new int[]{0xFF2a2a2a, 0xFF4a4a4a, 0xFF696a6a, 0xFF7f8080, 0xFF969696},
                "minecraft:gunpowder"));

        register(new VoxelMaterial("coal", 0.8f, 0.08f, 16,
                new int[]{0xFF242221, 0xFF3e3c3a, 0xFF595652, 0xFF726f6c, 0xFF8a8886},
                "minecraft:coal"));

        register(new VoxelMaterial("ender", 10.0f, -0.5f, -2048,
                new int[]{0xFF2f1a37, 0xFF532e61, 0xFF76428a, 0xFF8a5e9b, 0xFF9f7aad},
                "minecraft:ender_pearl"));

        register(new VoxelMaterial("redstone", 7.5f, -0.25f, 0,
                new int[]{0xFF451414, 0xFF792323, 0xFFac3232, 0xFFb85151, 0xFFc56f6f},
                "minecraft:redstone"));

        register(new VoxelMaterial("mushroom", -4.2f, 1.1f, -1024,
                new int[]{0xFF572328, 0xFF983d45, 0xFFd95763, 0xFFdf707a, 0xFFe48991},
                "minecraft:red_mushroom_block"));

        register(new VoxelMaterial("amethyst", 3.75f, 0.0f, 32,
                new int[]{0xFF56314a, 0xFF975683, 0xFFd77bba, 0xFFdd8fc4, 0xFFe3a2cf},
                "minecraft:amethyst_shard"));

        register(new VoxelMaterial("leaf", -2.5f, 0.6f, -1024,
                new int[]{0xFF393c1e, 0xFF646a34, 0xFF8f974a, 0xFFa0a665, 0xFFb0b680},
                "minecraft:big_dripleaf"));

        register(new VoxelMaterial("cocoa", -5.5f, 1.0f, -2048,
                new int[]{0xFF372c13, 0xFF614e22, 0xFF8a6f30, 0xFF9b844f, 0xFFad9a6e},
                "minecraft:cocoa_beans"));
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

    public static boolean areAbilitiesDisabled() {
        return abilitiesDisabled;
    }
    public static void setAbilitiesDisabled(boolean disabled) {
        abilitiesDisabled = disabled;
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
            e.iconItem = m.iconItem();
            return e;
        }

        public VoxelMaterial toMaterial() {
            return new VoxelMaterial(
                    id, bonusDamage, bonusSpeed, bonusDurability, palette,
                    McCompat.checkId(iconItem != null ? iconItem : "minecraft:iron_ingot")
            );
        }
    }
}