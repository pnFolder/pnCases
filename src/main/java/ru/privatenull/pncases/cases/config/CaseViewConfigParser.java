package ru.privatenull.pncases.cases.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.pncases.cases.model.CaseGuiLayout;
import ru.privatenull.pncases.cases.model.CaseGuiLayoutRules;
import ru.privatenull.pncases.cases.model.IdleParticleSettings;
import ru.privatenull.pnlibrary.text.ColorUtil;
import ru.privatenull.pnlibrary.item.ItemFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class CaseViewConfigParser {

    CaseGuiLayout parseLayout(ConfigurationSection gui) {
        CaseGuiLayout defaults = CaseGuiLayout.defaults();
        if (gui == null) return defaults;

        int size = resolveSize(gui);

        int openSlot = gui.getInt("open_slot", defaults.openSlot());
        int animationSlot = gui.getInt("animation_slot", defaults.animationSlot());

        ConfigurationSection decor = gui.getConfigurationSection("decor");
        ConfigurationSection history = gui.getConfigurationSection("history");
        CaseGuiLayoutRules.ResolvedSlots slots = CaseGuiLayoutRules.resolveSlots(
                size,
                openSlot, defaults.openSlot(),
                animationSlot, defaults.animationSlot(),
                readSlots(history, defaults.historySlots(), "slots"),
                readSlots(decor, defaults.decorSlots(), "slots")
        );
        return new CaseGuiLayout(
                size,
                slots.openSlot(),
                slots.animationSlot(),
                previewEnabled(gui),
                slots.historySlots(),
                readItem(history, "empty", defaults.emptyHistoryItem(), Material.BARRIER, "&8История пуста"),
                slots.decorSlots(),
                readItem(decor, "item", null, Material.GRAY_STAINED_GLASS_PANE, " ")
        );
    }

    static int resolveSize(ConfigurationSection gui) {
        if (gui == null) return CaseGuiLayout.DEFAULT_SIZE;
        return CaseGuiLayoutRules.normalizeSize(gui.getInt("size", CaseGuiLayout.DEFAULT_SIZE));
    }

    static boolean previewEnabled(ConfigurationSection gui) {
        return gui == null || gui.getBoolean("preview_enabled", true);
    }

    IdleParticleSettings parseIdleParticles(ConfigurationSection section) {
        IdleParticleSettings defaults = IdleParticleSettings.defaults();
        if (section == null) return defaults;

        return new IdleParticleSettings(
                section.getBoolean("enabled", defaults.enabled()),
                enumValue(section.getString("theme"), defaults.theme()),
                Math.max(2, section.getInt("interval_ticks", defaults.intervalTicks())),
                clamp(section.getDouble("radius", defaults.radius()), 0.25, 2.50),
                clamp(section.getDouble("height", defaults.height()), 0.30, 3.00),
                clamp(section.getDouble("speed", defaults.speed()), 0.02, 0.80),
                clamp(section.getDouble("view_distance", defaults.viewDistance()), 4.0, 64.0)
        );
    }

    private ItemStack readItem(ConfigurationSection root, String key, ItemStack fallback, Material material, String name) {
        if (root != null) {
            ItemStack item = ItemFactory.fromSection(root.getConfigurationSection(key));
            if (item != null) return item;
        }
        if (fallback != null) return fallback.clone();
        if (material == null) return null;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null && name != null) {
            meta.setDisplayName(ColorUtil.colorize(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<Integer> readSlots(ConfigurationSection section, List<Integer> fallback, String key) {
        if (section == null || !section.isList(key)) return fallback;
        List<Integer> result = new ArrayList<>();
        for (Object raw : section.getList(key, Collections.emptyList())) {
            if (raw instanceof Number number) {
                result.add(number.intValue());
            } else {
                try {
                    result.add(Integer.parseInt(String.valueOf(raw)));
                } catch (NumberFormatException ignored) {
                    // Invalid slots are skipped and the remaining layout stays usable.
                }
            }
        }
        return result;
    }

    private IdleParticleSettings.Theme enumValue(String raw, IdleParticleSettings.Theme fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return IdleParticleSettings.Theme.valueOf(normalizeEnum(raw));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private String normalizeEnum(String raw) {
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
