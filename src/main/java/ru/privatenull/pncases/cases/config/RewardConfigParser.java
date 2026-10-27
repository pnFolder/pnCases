package ru.privatenull.pncases.cases.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pncases.cases.reward.RewardPresentationService;
import ru.privatenull.pncases.support.item.ItemFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RewardConfigParser {

    private final RewardPresentationService presentation;

    RewardConfigParser(RewardPresentationService presentation) {
        this.presentation = presentation;
    }

    List<Reward> parse(ConfigurationSection caseSection) {
        List<Reward> rewards = new ArrayList<>();
        if (caseSection.isList("rewards")) {
            for (Object raw : caseSection.getList("rewards", List.of())) {
                if (!(raw instanceof Map<?, ?> map)) continue;
                Reward reward = parseReward(map);
                if (reward != null) rewards.add(reward);
            }
        }
        return rewards;
    }

    private Reward parseReward(Map<?, ?> map) {
        int chance = integer(map.get("chance"), 0);
        String rawType = String.valueOf(map.containsKey("type") ? map.get("type") : "ITEM");
        Reward.Type type = parseType(rawType);
        if (type == null) type = Reward.Type.ITEM;
        String message = rewardMessage(map, type);
        Object rarity = map.get("rarity");
        String rarityId = Reward.normalizeRarityId(rarity == null ? null : String.valueOf(rarity), chance);

        ParsedReward parsed = switch (type) {
            case ITEM -> parseItem(map);
            case LUCKPERMS -> parseLuckPerms(map);
            case VAULT -> parseVault(map);
            case PLAYERPOINTS -> parsePlayerPoints(map);
            case PERSONAL -> parsePersonal(map);
        };
        if (!isValid(chance, type, parsed)) return null;
        String configuredDisplayName = value(map.get("name"));
        String displayName = configuredDisplayName == null || configuredDisplayName.isBlank()
                ? parsed.displayName()
                : configuredDisplayName;

        return new Reward(
                chance,
                type,
                parsed.item(),
                parsed.group(),
                parsed.node(),
                parsed.duration(),
                parsed.vaultAmount(),
                parsed.playerPointsAmount(),
                message,
                displayName,
                rarityId,
                parsed.visualItem(),
                parsed.command(),
                parsed.commands()
        );
    }

    private ParsedReward parseItem(Map<?, ?> map) {
        ItemStack item = null;
        String displayName = null;
        Object itemObject = map.get("item");
        if (itemObject instanceof Map<?, ?> itemMap) {
            item = ItemFactory.fromMap(itemMap);
            displayName = value(itemMap.get("name"));
        }
        if (displayName == null && item != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) displayName = meta.getDisplayName();
        }
        if (displayName == null && item != null) displayName = presentation.defaultItemName(item);
        ItemStack visual = visualItem(map, displayName);
        if (item == null) item = visual;
        if (visual == null) visual = item;
        return new ParsedReward(item, visual, null, null, null, 0.0, 0, displayName, null);
    }

    private ParsedReward parseLuckPerms(Map<?, ?> map) {
        String group = null;
        String node = null;
        String duration = null;
        String displayName = null;
        Object raw = map.get("luckperms");
        if (raw instanceof Map<?, ?> luckPerms) {
            group = value(luckPerms.get("group"));
            node = value(luckPerms.get("node"));
            duration = value(luckPerms.get("duration"));
            displayName = value(firstPresent(luckPerms, map, "name"));
        }
        ItemStack visual = visualItem(map, displayName);
        displayName = visualName(visual, displayName == null ? "&dПривилегия" : displayName);
        return new ParsedReward(null, visual, group, node, duration, 0.0, 0, displayName, null);
    }

    private ParsedReward parseVault(Map<?, ?> map) {
        Map<?, ?> vault = nestedMap(map, "vault");
        double amount = decimal(firstPresent(vault, "amount"), 0.0);
        String displayName = "&a" + presentation.formatVaultAmount(amount);
        ItemStack visual = visualItem(map, displayName);
        return new ParsedReward(null, visual, null, null, null, amount, 0, visualName(visual, displayName), null);
    }

    private ParsedReward parsePlayerPoints(Map<?, ?> map) {
        Map<?, ?> points = nestedMap(map, "playerpoints");
        int amount = Math.max(0, integer(firstPresent(points, "amount"), 0));
        String displayName = "&b" + presentation.formatPlayerPointsAmount(amount);
        ItemStack visual = visualItem(map, displayName);
        return new ParsedReward(null, visual, null, null, null, 0.0, amount, visualName(visual, displayName), null);
    }

    private ParsedReward parsePersonal(Map<?, ?> map) {
        String command = value(map.get("command"));
        List<String> commands = stringList(map.get("commands"));
        String displayName = value(map.get("name"));
        ItemStack visual = visualItem(map, displayName);
        displayName = visualName(visual, displayName == null ? "&dЛичная награда" : displayName);
        return new ParsedReward(null, visual, null, null, null, 0.0, 0, displayName, command, commands);
    }

    private String rewardMessage(Map<?, ?> reward, Reward.Type type) {
        if (reward.containsKey("message")) return value(reward.get("message"));
        String sectionName = switch (type) {
            case LUCKPERMS -> "luckperms";
            case VAULT -> "vault";
            case PLAYERPOINTS -> "playerpoints";
            case ITEM, PERSONAL -> null;
        };
        if (sectionName == null) return null;
        Map<?, ?> section = nestedMap(reward, sectionName);
        return section == null ? null : value(section.get("message"));
    }

    private ItemStack visualItem(Map<?, ?> reward, String displayName) {
        Object raw = firstPresent(reward, "visual", "item");
        if (raw instanceof Map<?, ?> map) return ItemFactory.fromMap(map);
        if (raw instanceof String value && !value.isBlank()) {
            Map<String, Object> icon = new HashMap<>();
            if (looksLikeBase64(value)) {
                icon.put("base64", value);
            } else {
                icon.put("material", value);
            }
            if (displayName != null && !displayName.isBlank()) icon.put("name", displayName);
            return ItemFactory.fromMap(icon);
        }
        if (!reward.containsKey("base64") && !reward.containsKey("material")) return null;

        Map<String, Object> values = new HashMap<>();
        for (Map.Entry<?, ?> entry : reward.entrySet()) {
            if (entry.getKey() != null) values.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        if (!values.containsKey("name") && displayName != null && !displayName.isBlank()) {
            values.put("name", displayName);
        }
        return ItemFactory.fromMap(values);
    }

    private boolean looksLikeBase64(String value) {
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("base64:") || normalized.startsWith("base64-") || normalized.startsWith("eyj");
    }

    private String visualName(ItemStack item, String fallback) {
        if (item == null) return fallback;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() ? meta.getDisplayName() : fallback;
    }

    private boolean isValid(int chance, Reward.Type type, ParsedReward parsed) {
        if (chance <= 0) return false;
        return switch (type) {
            case ITEM -> parsed.item() != null;
            case VAULT -> parsed.vaultAmount() > 0.0;
            case PLAYERPOINTS -> parsed.playerPointsAmount() > 0;
            case LUCKPERMS -> notBlank(parsed.group()) || notBlank(parsed.node());
            case PERSONAL -> notBlank(parsed.command()) || !parsed.commands().isEmpty();
        };
    }

    private String value(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> values)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object value : values) {
            String command = value(value);
            if (command != null && !command.isBlank()) result.add(command);
        }
        return List.copyOf(result);
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private double decimal(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public static Reward.Type parseType(String rawType) {
        if (rawType == null) return Reward.Type.ITEM;
        return switch (rawType.trim().toUpperCase(Locale.ROOT)) {
            case "ITEM" -> Reward.Type.ITEM;
            case "LUCKPERMS" -> Reward.Type.LUCKPERMS;
            case "VAULT" -> Reward.Type.VAULT;
            case "PLAYERPOINTS" -> Reward.Type.PLAYERPOINTS;
            case "PERSONAL" -> Reward.Type.PERSONAL;
            default -> null;
        };
    }

    public static Map<?, ?> nestedMap(Map<?, ?> map, String... keys) {
        if (map == null) return null;
        for (String key : keys) {
            if (map.get(key) instanceof Map<?, ?> nested) return nested;
        }
        return null;
    }

    public static Object firstPresent(Map<?, ?> primary, Map<?, ?> fallback, String... keys) {
        Object value = firstPresent(primary, keys);
        return value != null ? value : firstPresent(fallback, keys);
    }

    public static Object firstPresent(Map<?, ?> map, String... keys) {
        if (map == null) return null;
        for (String key : keys) {
            if (map.containsKey(key)) return map.get(key);
        }
        return null;
    }

    private record ParsedReward(
            ItemStack item,
            ItemStack visualItem,
            String group,
            String node,
            String duration,
            double vaultAmount,
            int playerPointsAmount,
            String displayName,
            String command,
            List<String> commands
    ) {
        private ParsedReward(ItemStack item, ItemStack visualItem, String group, String node,
                             String duration, double vaultAmount, int playerPointsAmount,
                             String displayName, String command) {
            this(item, visualItem, group, node, duration, vaultAmount, playerPointsAmount,
                    displayName, command, List.of());
        }
    }
}
