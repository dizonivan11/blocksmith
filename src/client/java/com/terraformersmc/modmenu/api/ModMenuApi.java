package com.terraformersmc.modmenu.api;

import net.minecraft.network.chat.Component;
import java.util.Collections;
import java.util.Map;

public interface ModMenuApi {
    default ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> null;
    }

    default Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
        return Collections.emptyMap();
    }

    // Fixes the NoSuchMethodError crash
    static Component createModsButtonText() {
        return Component.translatable("modmenu.title");
    }
}