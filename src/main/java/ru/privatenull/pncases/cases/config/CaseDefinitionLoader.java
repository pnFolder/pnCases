package ru.privatenull.pncases.cases.config;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.pncases.cases.model.*;
import ru.privatenull.pncases.cases.reward.RewardPresentationService;
import ru.privatenull.pnlibrary.text.ColorUtil;
import ru.privatenull.pnlibrary.item.ItemFactory;

import java.util.*;

public final class CaseDefinitionLoader {

    private final CaseBlockCodec blockCodec;
    private final CaseViewConfigParser viewParser = new CaseViewConfigParser();
    private final RewardConfigParser rewardParser;

    public CaseDefinitionLoader(CaseBlockCodec blockCodec, RewardPresentationService presentation) {
        this.blockCodec = blockCodec;
        this.rewardParser = new RewardConfigParser(presentation);
    }

    public CaseDefinition load(String caseName, ConfigurationSection section) {
        String displayName = section.getString("display-name", humanizeCaseName(caseName));
        List<Location> blocks = blockCodec.readLoadedLocations(section);

        ConfigurationSection gui = section.getConfigurationSection("gui");
        String title = resolveGuiTitle(displayName, gui == null ? null : gui.getString("title"));
        ConfigurationSection openItemSection = gui == null ? null : gui.getConfigurationSection("button");
        ItemStack openButton = ItemFactory.fromSection(openItemSection);
        if (openButton == null) openButton = new ItemStack(Material.CHEST);
        applyDisplayNameToken(openButton, openItemSection, displayName);
        CaseGuiLayout layout = viewParser.parseLayout(gui);
        IdleParticleSettings idleParticles = viewParser.parseIdleParticles(section.getConfigurationSection("idle-particles"));

        int buyXpLevels = parseBuyXpLevels(section.getConfigurationSection("cost"));
        Animation animation = parseAnimation(section.getConfigurationSection("animation"));
        ConfigurationSection broadcast = section.getConfigurationSection("broadcast");
        boolean broadcastEnabled = broadcast == null || broadcast.getBoolean("enabled", true);
        List<String> broadcastLines = broadcast != null && broadcast.isList("lines")
                ? broadcast.getStringList("lines")
                : CaseDefinition.DEFAULT_BROADCAST_LINES;
        List<Reward> rewards = rewardParser.parse(section);
        if (rewards.isEmpty()) {
            throw new IllegalArgumentException("Кейс '" + caseName + "' не содержит ни одной корректной награды.");
        }

        return new CaseDefinition(
                caseName.toLowerCase(Locale.ROOT),
                displayName,
                blocks,
                title,
                openButton,
                layout,
                idleParticles,
                buyXpLevels,
                animation.durationTicks(),
                animation.cycleEveryTicks(),
                animation.riseBlocks(),
                animation.spinDegreesPerTick(),
                animation.fixed(),
                animation.items(),
                broadcastEnabled,
                broadcastLines,
                rewards
        );
    }

    static String resolveGuiTitle(String displayName, String configuredTitle) {
        if (configuredTitle == null || configuredTitle.isBlank() || configuredTitle.equals("{display-name}")) {
            return displayName;
        }
        return configuredTitle;
    }

    private static void applyDisplayNameToken(
            ItemStack item,
            ConfigurationSection configuredItem,
            String displayName
    ) {
        if (item == null || configuredItem == null
                || !"{display-name}".equals(configuredItem.getString("name"))) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.setDisplayName(ColorUtil.colorize(displayName));
        item.setItemMeta(meta);
    }

    private int parseBuyXpLevels(ConfigurationSection section) {
        int buyXpLevels = 0;
        ConfigurationSection xpPurchase = section == null
                ? null
                : section.getConfigurationSection("xp-purchase");
        if (xpPurchase != null && xpPurchase.getBoolean("enabled", false)) {
            buyXpLevels = xpPurchase.getInt("levels", 0);
        }
        return buyXpLevels;
    }

    private Animation parseAnimation(ConfigurationSection section) {
        int duration = section == null ? 80 : section.getInt("duration_ticks", 80);
        int cycleEvery = section == null ? 2 : section.getInt("cycle_every_ticks", 2);
        double rise = section == null ? 1.2 : section.getDouble("rise_blocks", 1.2);
        float spin = section == null ? 18.0F : (float) section.getDouble("spin_degrees_per_tick", 18.0);

        List<ItemStack> items = new ArrayList<>();
        if (section != null && section.isList("items")) {
            for (Object raw : section.getList("items", List.of())) {
                if (raw instanceof Map<?, ?> map) items.add(ItemFactory.fromMap(map));
            }
        }
        items.removeIf(Objects::isNull);

        return new Animation(duration, cycleEvery, rise, spin, fixedAnimation(section), items);
    }

    private AnimationType fixedAnimation(ConfigurationSection section) {
        String raw = section == null ? null : section.getString("fixed");
        if (raw == null || raw.isBlank()) return null;

        try {
            return AnimationType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String humanizeCaseName(String caseName) {
        return caseName == null || caseName.isBlank() ? "&fКейс" : "&f" + caseName.replace('_', ' ');
    }

    private record Animation(
            int durationTicks,
            int cycleEveryTicks,
            double riseBlocks,
            float spinDegreesPerTick,
            AnimationType fixed,
            List<ItemStack> items
    ) {
    }
}
