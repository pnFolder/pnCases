package ru.privatenull.pncases.cases.config;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.pncases.cases.model.*;
import ru.privatenull.pncases.cases.reward.RewardPresentationService;
import ru.privatenull.pncases.support.text.ColorUtil;
import ru.privatenull.pncases.support.item.ItemFactory;

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
        ProximitySettings proximity = viewParser.parseProximity(section.getConfigurationSection("proximity-animation"));

        int buyXpLevels = parseBuyXpLevels(section.getConfigurationSection("cost"));
        Animation animation = parseAnimation(section.getConfigurationSection("animation"));
        ConfigurationSection broadcast = section.getConfigurationSection("broadcast");
        boolean broadcastEnabled = broadcast == null || broadcast.getBoolean("enabled", true);
        List<String> broadcastLines = broadcast != null && broadcast.isList("lines")
                ? broadcast.getStringList("lines")
                : CaseDefinition.DEFAULT_BROADCAST_LINES;
        GuaranteeSettings guarantee = parseGuarantee(section.getConfigurationSection("guarantee"));
        List<Reward> rewards = rewardParser.parse(section);
        List<String> children = parseBundle(section.getConfigurationSection("bundle"), caseName, !rewards.isEmpty());
        if (rewards.isEmpty() && children.isEmpty()) {
            throw new IllegalArgumentException("Кейс '" + caseName + "' не содержит ни одной корректной награды.");
        }

        if (!children.isEmpty()) {
            // Several cases share this menu, so its title is the group's, not the title of one case.
            title = section.getString("bundle.title", "Кейсы");
        }

        return new CaseDefinition(
                caseName.toLowerCase(Locale.ROOT),
                displayName,
                blocks,
                title,
                openButton,
                layout,
                idleParticles,
                proximity,
                buyXpLevels,
                animation.fixed(),
                animation.items(),
                broadcastEnabled,
                broadcastLines,
                guarantee,
                rewards,
                children
        );
    }

    /** Names listed under {@code bundle.cases}, lower-cased and without repeats; the case itself may be listed. */
    private List<String> parseBundle(ConfigurationSection bundle, String caseName, boolean hasOwnRewards) {
        if (bundle == null) return List.of();
        List<String> names = new ArrayList<>();
        for (String raw : bundle.getStringList("cases")) {
            if (raw == null) continue;
            String id = raw.trim().toLowerCase(Locale.ROOT);
            if (!id.isEmpty() && !names.contains(id)) names.add(id);
        }
        // A case with its own rewards keeps its own button, first in the row, unless include_self is off.
        // An empty container has nothing to open itself, so it never gets one.
        String self = caseName.toLowerCase(Locale.ROOT);
        names.remove(self);
        if (!names.isEmpty() && hasOwnRewards && bundle.getBoolean("include_self", true)) names.add(0, self);
        return names;
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
        List<ItemStack> items = new ArrayList<>();
        if (section != null && section.isList("items")) {
            for (Object raw : section.getList("items", List.of())) {
                if (raw instanceof Map<?, ?> map) items.add(ItemFactory.fromMap(map));
            }
        }
        items.removeIf(Objects::isNull);

        return new Animation(fixedAnimation(section), items);
    }

    private GuaranteeSettings parseGuarantee(ConfigurationSection section) {
        if (section == null) return GuaranteeSettings.disabled();
        Reward.Rarity minimum = Reward.Rarity.parseKnown(section.getString("minimum-rarity"));
        return new GuaranteeSettings(
                section.getBoolean("enabled", false),
                section.getInt("openings", 50),
                minimum == null ? Reward.Rarity.LEGENDARY : minimum,
                section.getBoolean("reset-after-win", true)
        );
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
            AnimationType fixed,
            List<ItemStack> items
    ) {
    }
}
