package com.terraformersmc.modmenu.api;

import java.util.Collections;
import java.util.Map;

/** Compile-time stand-in for Mod Menu's API; excluded from the built jar. */
public interface ModMenuApi {
    default ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> null;
    }

    default Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
        return Collections.emptyMap();
    }
}