package net.adeptfrog.blocksmith.client.gui;

import net.adeptfrog.blocksmith.ability.WeaponAbility;
import net.adeptfrog.blocksmith.ability.WeaponAbilityRegistry;
import net.adeptfrog.blocksmith.client.utils.StringUtils;
import net.adeptfrog.blocksmith.data.MaterialGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.*;

public class AbilityBookScreen extends BookViewScreen {
    private final Screen parent;

    public AbilityBookScreen(Screen parent) {
        super(createBookAccess());
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private static BookAccess createBookAccess() {
        List<Component> pages = new ArrayList<>();

        // Page 1: Introduction / Cover Page
        pages.add(Component.literal(
                "§1§lBlocksmith\n"+
                "§0§lAbility Codex\n\n"+
                
                "§0Forged weapons unlock powerful abilities when built with at least §4"+WeaponAbility.minVoxelCount()+" voxels§0 and meeting material thresholds.\n\n"+
                
                "§8Turn pages to browse all active abilities §0➡"));

        // Build a continuous formatted text stream for all registered abilities
        StringBuilder sb = new StringBuilder();
        for (WeaponAbility ability : WeaponAbilityRegistry.getAll()) {
            sb.append("§l").append(ability.displayName().getString()).append("\n");
            sb.append("§8Type: §0").append(ability.weaponType().name()).append("\n");
            sb.append("§8Req: ");
            List<String> reqs = new ArrayList<>();
            for (Map.Entry<MaterialGroup, Double> req : ability.requiredThresholds().entrySet()) {
                reqs.add("§2" + req.getValue().intValue() + "% " + StringUtils.capitalize(req.getKey().id()));
            }
            sb.append(String.join("§8, ", reqs)).append("\n");
            sb.append("§0").append(ability.description().getString().replace("§7", "§0")).append("\n");
            sb.append("§8§m                    §r\n");
        }

        // Wrap texts into 114px lines and paginate into 14 lines per page
        Font font = Minecraft.getInstance().font;
        int bookPageWidth = 114;
        int maxLinesPerPage = 14;

        List<FormattedText> allLines = font.getSplitter().splitLines(Component.literal(sb.toString()), bookPageWidth, Style.EMPTY);
        List<FormattedText> currentLines = new ArrayList<>();

        for (FormattedText line : allLines) {
            currentLines.add(line);
            if (currentLines.size() >= maxLinesPerPage) {
                pages.add(joinPageLines(currentLines));
                currentLines.clear();
            }
        }

        if (!currentLines.isEmpty()) {
            pages.add(joinPageLines(currentLines));
        }

        return new BookAccess(pages);
    }

    private static Component joinPageLines(List<FormattedText> lines) {
        MutableComponent pageComponent = Component.empty();

        for (int i = 0; i < lines.size(); i++) {
            FormattedText line = lines.get(i);

            // Visits each styled fragment and preserves its inherited color across the wrap
            line.visit((style, text) -> {
                pageComponent.append(Component.literal(text).setStyle(style));
                return Optional.empty();
            }, Style.EMPTY);

            if (i < lines.size() - 1) {
                pageComponent.append(Component.literal("\n"));
            }
        }

        return pageComponent;
    }
}