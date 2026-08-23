package ru.privatenull.pncases.cases.reward;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.pncases.PnCasesPlugin;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pnlibrary.text.ColorUtil;
import ru.privatenull.pnlibrary.compat.BukkitCompat.MaterialCompat;
import ru.privatenull.pncases.gui.GuiDesign;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RewardPresentationService {

    private final PnCasesPlugin plugin;

    public RewardPresentationService(PnCasesPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack buildPreviewItem(CaseDefinition definition, Reward reward, int totalChance) {
        ItemStack item = buildDisplayItem(definition, reward);
        String rarity = plugin.rarityEnabled()
                ? resolveRarity(reward)
                : "";
        PreviewTypeStyle type = resolveType(reward);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String rewardName = color(resolveViewName(reward, item));
        String amount = reward.type() == Reward.Type.VAULT
                ? formatVaultAmount(reward.vaultAmount())
                : reward.type() == Reward.Type.PLAYERPOINTS
                ? formatPlayerPointsAmount(reward.playerPointsAmount()) : "";
        String duration = reward.lpDuration() == null ? "" : reward.lpDuration();
        String itemAmount = reward.item() == null ? "" : String.valueOf(reward.item().getAmount());
        String[] replacements = {
                "case", definition == null ? "" : color(definition.displayName()),
                "case_id", definition == null ? "" : definition.name(),
                "reward", rewardName,
                "rarity", rarity,
                "type", type.color() + type.name(),
                "type_name", type.name(),
                "type_id", type.id(),
                "type_color", type.color(),
                "type_symbol", type.symbol(),
                "type_description", type.description(),
                "chance", formatChancePercent(reward.chance(), totalChance),
                "amount", amount,
                "duration", duration,
                "group", reward.lpGroup() == null ? "" : reward.lpGroup(),
                "permission", reward.lpNode() == null ? "" : reward.lpNode(),
                "item_amount", itemAmount
        };
        meta.setDisplayName(plugin.getUi().text(
                "preview.reward.name", "{rarity} &f{reward}", replacements));

        List<String> originalLore = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<>(meta.getLore()) : List.of();
        List<String> configuredLore = new ArrayList<>(plugin.getUi().list(
                "preview.reward.lore", defaultPreviewLore(), replacements));
        configuredLore.removeIf(this::isEmptyValueLine);

        meta.setLore(mergeLore(originalLore, configuredLore));
        GuiDesign.hideAttributes(meta);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack buildDisplayItem(CaseDefinition definition, Reward reward) {
        if (reward.visualItem() != null) {
            return reward.visualItem().clone();
        }

        ItemStack matched = findMatchingAnimationItem(definition, reward);
        if (matched != null) {
            return matched;
        }

        Material fallbackMaterial = switch (reward.type()) {
            case VAULT -> Material.EMERALD;
            case PLAYERPOINTS -> MaterialCompat.first("AMETHYST_SHARD", "EMERALD");
            case LUCKPERMS -> Material.NETHER_STAR;
            case ITEM -> Material.CHEST;
            case PERSONAL -> Material.COMMAND_BLOCK;
        };
        ItemStack item = new ItemStack(fallbackMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = reward.displayName();
            if (name == null || name.isBlank()) {
                name = switch (reward.type()) {
                    case VAULT -> "&a" + formatVaultAmount(reward.vaultAmount());
                    case PLAYERPOINTS -> "&b" + formatPlayerPointsAmount(reward.playerPointsAmount());
                    case LUCKPERMS -> "&dПривилегия";
                    case ITEM -> "&fНаграда";
                    case PERSONAL -> "&dЛичная награда";
                };
            }
            meta.setDisplayName(color(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack findDisplayItem(CaseDefinition definition, String rewardName) {
        if (definition == null || rewardName == null) return null;
        String targetName = normalizeDisplayName(rewardName);
        for (Reward reward : definition.rewards()) {
            if (matchesDisplayName(normalizeDisplayName(reward.displayName()), targetName)) {
                return buildDisplayItem(definition, reward);
            }
        }
        return null;
    }

    public String resolveViewName(Reward reward, ItemStack visual) {
        String configured = reward == null ? null : reward.displayName();
        String visualName = customDisplayName(visual);
        if (isGenericLuckPermsName(reward, configured) && visualName != null) {
            return visualName;
        }
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        if (visualName != null) {
            return visualName;
        }
        if (reward == null) {
            return "&fНаграда";
        }
        return switch (reward.type()) {
            case VAULT -> "&a" + formatVaultAmount(reward.vaultAmount());
            case PLAYERPOINTS -> "&b" + formatPlayerPointsAmount(reward.playerPointsAmount());
            case LUCKPERMS -> "&dПривилегия";
            case ITEM -> "&fНаграда";
            case PERSONAL -> "&dЛичная награда";
        };
    }

    public String formatVaultAmount(double amount) {
        return plugin.getEconomies().vault().format(amount);
    }

    public String formatPlayerPointsAmount(int amount) {
        return plugin.getEconomies().playerPoints().format(amount);
    }

    public String formattedRarity(Reward reward) {
        if (reward == null || !plugin.rarityEnabled()) return "";
        return resolveRarity(reward);
    }

    private ItemStack findMatchingAnimationItem(CaseDefinition definition, Reward reward) {
        if (definition == null || reward == null || definition.animationItems() == null) {
            return null;
        }
        String rewardName = normalizeDisplayName(reward.displayName());
        String groupName = normalizeDisplayName(reward.lpGroup());
        String nodeName = normalizeDisplayName(reward.lpNode());
        for (ItemStack item : definition.animationItems()) {
            if (item == null) continue;
            String itemName = normalizeDisplayName(plugin.getItemLocalization().getPlainName(item));
            if (matchesDisplayName(itemName, rewardName)
                    || matchesDisplayName(itemName, groupName)
                    || matchesDisplayName(itemName, nodeName)) {
                return item.clone();
            }
        }
        return null;
    }

    private String resolveRarity(Reward reward) {
        String id = reward.rarityId();
        Reward.Rarity known = Reward.Rarity.parseKnown(id);
        String fallbackName = known == null
                ? "&f◆ " + humanizeId(id)
                : switch (known) {
                    case COMMON -> "&7◇ Обычная";
                    case UNCOMMON -> "&a◆ Необычная";
                    case RARE -> "&9◆ Редкая";
                    case EPIC -> "&5✦ Эпическая";
                    case LEGENDARY -> "&6★ Легендарная";
                    case MYTHIC -> "&d✹ Мифическая";
                };
        String humanized = humanizeId(id);
        String defaultValue = plugin.getConfig().getString("rarity.default", fallbackName);
        String configured = plugin.getConfig().getString("rarity." + id, defaultValue);
        return color((configured == null ? fallbackName : configured)
                .replace("{id}", humanized)
                .replace("{rarity_id}", humanized));
    }

    private PreviewTypeStyle resolveType(Reward reward) {
        Reward.Type rewardType = reward.type();
        String id = rewardType.name().toLowerCase(Locale.ROOT);
        return switch (rewardType) {
            case ITEM -> new PreviewTypeStyle(id, "Предмет", "&f", "◆", "");
            case VAULT -> new PreviewTypeStyle(id, "Монеты", "&a", "●", "");
            case PLAYERPOINTS -> new PreviewTypeStyle(id, "Поинты", "&b", "✦", "");
            case LUCKPERMS -> reward.lpNode() != null && !reward.lpNode().isBlank()
                    && (reward.lpGroup() == null || reward.lpGroup().isBlank())
                    ? new PreviewTypeStyle(id, "Право", "&b", "◆", "")
                    : new PreviewTypeStyle(id, "Привилегия", "&d", "★", "");
            case PERSONAL -> new PreviewTypeStyle(id, "Личная награда", "&e", "✦", "");
        };
    }

    private List<String> defaultPreviewLore() {
        return List.of(
                "", "&#9EFC65 «Основная информация»",
                " &7- &fРедкость: {rarity}",
                "", "&#65D1FC «Вероятность»",
                " &7- &fВыпадение: &#FCA865{chance}", ""
        );
    }

    private List<String> mergeLore(List<String> original, List<String> configured) {
        if (original.isEmpty()) {
            return configured;
        }
        List<String> result = new ArrayList<>(original.size() + configured.size() + 1);
        result.addAll(original);
        addSeparator(result, true);
        result.addAll(configured);
        return result;
    }

    private static void addSeparator(List<String> lines, boolean enabled) {
        if (enabled && !lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
            lines.add("");
        }
    }

    private boolean isEmptyValueLine(String line) {
        if (line == null || line.isBlank()) return false;
        String plain = ChatColor.stripColor(line);
        if (plain == null) plain = line;
        String normalized = plain.trim();
        return normalized.endsWith(":") || normalized.endsWith(": -");
    }

    private Material configuredMaterial(String raw, Material fallback) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("ORIGINAL") || raw.equalsIgnoreCase("KEEP")) {
            return fallback;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("MINECRAFT:")) {
            normalized = normalized.substring("MINECRAFT:".length());
        }
        Material material = Material.matchMaterial(normalized);
        return material == null || material.isAir() ? fallback : material;
    }

    private String customDisplayName(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() ? meta.getDisplayName() : null;
    }

    private boolean isGenericLuckPermsName(Reward reward, String value) {
        if (reward == null || reward.type() != Reward.Type.LUCKPERMS || value == null || value.isBlank()) {
            return false;
        }
        String stripped = ChatColor.stripColor(color(value));
        if (stripped == null) return false;
        String normalized = stripped.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
        return normalized.equals("luckperms") || normalized.equals("привилегия");
    }

    private String normalizeDisplayName(String value) {
        if (value == null || value.isBlank()) return "";
        String colored = color(value);
        String stripped = ChatColor.stripColor(colored);
        if (stripped == null) stripped = colored;
        return stripped.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    private boolean matchesDisplayName(String itemName, String rewardName) {
        if (itemName.length() < 2 || rewardName.length() < 2) return false;
        return itemName.equals(rewardName) || itemName.contains(rewardName) || rewardName.contains(itemName);
    }

    private String humanizeId(String id) {
        if (id == null || id.isBlank()) return "Своя";
        String value = id.replace('_', ' ').replace('-', ' ').trim();
        if (value.isBlank()) return "Своя";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String formatChancePercent(int chance, int totalChance) {
        if (totalChance <= 0 || chance <= 0) return "0%";
        double percent = chance * 100.0D / totalChance;
        if (Math.abs(percent - Math.rint(percent)) < 0.01D) {
            return (int) Math.rint(percent) + "%";
        }
        return String.format(Locale.US, "%.1f%%", percent);
    }

    private static String color(String value) {
        return ColorUtil.colorize(value);
    }

    private record PreviewTypeStyle(String id, String name, String color, String symbol, String description) {
    }
}
