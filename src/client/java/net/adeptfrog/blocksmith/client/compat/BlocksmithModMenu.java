package net.adeptfrog.blocksmith.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.adeptfrog.blocksmith.client.gui.MaterialConfigScreen;

public class BlocksmithModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return MaterialConfigScreen::new;
    }
}