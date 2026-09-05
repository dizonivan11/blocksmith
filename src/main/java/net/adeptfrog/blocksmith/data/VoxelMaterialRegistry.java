package net.adeptfrog.blocksmith.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
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

    // Updated Fallback to match new Iron defaults
    public static final VoxelMaterial FALLBACK = new VoxelMaterial(
            "iron", 0.01f, 0.0f, 3,
            new int[]{0xFF707070, 0xFF9E9E9E, 0xFFD8D8D8, 0xFFEEEEEE, 0xFFFFFFFF},
            Identifier.fromNamespaceAndPath("minecraft", "iron_ingot")
    );

    static {
        load();
    }

    public static void initialize() {}

    public static void load() {
        MATERIALS.clear();

        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                Type type = new TypeToken<List<MaterialConfigEntry>>() {}.getType();
                List<MaterialConfigEntry> entries = GSON.fromJson(reader, type);

                if (entries != null && !entries.isEmpty()) {
                    for (MaterialConfigEntry e : entries) {
                        MATERIALS.put(e.id.toLowerCase(), e.toMaterial());
                    }
                    return;
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
                List<MaterialConfigEntry> entries = new ArrayList<>();
                for (VoxelMaterial mat : MATERIALS.values()) {
                    entries.add(MaterialConfigEntry.fromMaterial(mat));
                }
                GSON.toJson(entries, writer);
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
        register(new VoxelMaterial("iron", 0.01f, 0.0f, 3,
                new int[]{0xFF707070, 0xFF9E9E9E, 0xFFD8D8D8, 0xFFEEEEEE, 0xFFFFFFFF},
                Identifier.fromNamespaceAndPath("minecraft", "iron_ingot")));

        register(new VoxelMaterial("copper", 0.01f, 0.0f, 2,
                new int[]{0xFF5A2C1C, 0xFF9E5232, 0xFFD36E42, 0xFFF0956E, 0xFFFFC0A8},
                Identifier.fromNamespaceAndPath("minecraft", "copper_ingot")));

        register(new VoxelMaterial("gold", 0.02f, 0.005f, 1,
                new int[]{0xFF9E6F00, 0xFFCCA010, 0xFFFFE135, 0xFFFFF070, 0xFFFFFFB8},
                Identifier.fromNamespaceAndPath("minecraft", "gold_ingot")));

        register(new VoxelMaterial("diamond", 0.04f, 0.002f, 6,
                new int[]{0xFF1B7A72, 0xFF2BB5AB, 0xFF4AEDD9, 0xFF85FAF2, 0xFFC7FFFF},
                Identifier.fromNamespaceAndPath("minecraft", "diamond")));

        register(new VoxelMaterial("netherite", 0.06f, 0.001f, 10,
                new int[]{0xFF211A18, 0xFF332825, 0xFF4A3C38, 0xFF66534E, 0xFF8A726C},
                Identifier.fromNamespaceAndPath("minecraft", "netherite_ingot")));

        register(new VoxelMaterial("emerald", 0.01f, 0.001f, 2,
                new int[]{0xFF0A371B, 0xFF0E5B2C, 0xFF179048, 0xFF39D375, 0xFF82F2AE},
                Identifier.fromNamespaceAndPath("minecraft", "emerald")));

        register(new VoxelMaterial("amethyst", 0.03f, 0.0015f, 4,
                new int[]{0xFF2A1138, 0xFF4D1C68, 0xFF8A38B0, 0xFFB762D8, 0xFFE3A6FF},
                Identifier.fromNamespaceAndPath("minecraft", "amethyst_shard")));

        register(new VoxelMaterial("redstone", 0.05f, 0.0015f, 3,
                new int[]{0xFF5C0000, 0xFF990000, 0xFFFF2222, 0xFFFF6B6B, 0xFFFFB3B3},
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