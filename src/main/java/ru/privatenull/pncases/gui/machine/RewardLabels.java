package ru.privatenull.pncases.gui.machine;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pncases.support.text.ColorUtil;

import java.util.Locale;

/** Texts shown for a reward in the Machine GUI. */
final class RewardLabels {

    private final CaseManager caseManager;

    RewardLabels(CaseManager caseManager) {
        this.caseManager = caseManager;
    }

    String itemName(ItemStack item) {
        return item == null || item.getType().isAir()
                ? "нет"
                : caseManager.getPlugin().getItemLocalization().getPlainName(item);
    }

    String plainRewardName(Reward reward) {
        String name = reward.displayName();
        if (name == null || name.isBlank()) name = itemName(reward.visualItem());
        String plain = ChatColor.stripColor(ColorUtil.colorize(name));
        return plain == null || plain.isBlank() ? "Награда" : plain;
    }

    String rewardSummary(Reward reward) {
        return switch (reward.type()) {
            case ITEM -> "Предмет: " + ColorUtil.paint("&#FCA865", itemName(reward.item()))
                    + " &7× " + ColorUtil.paint("&#FCA865", reward.item() == null ? 0 : reward.item().getAmount());
            case VAULT -> "Монеты: " + ColorUtil.paint("&#FCA865", reward.vaultAmount());
            case PLAYERPOINTS -> "Поинты: " + ColorUtil.paint("&#FCA865", reward.playerPointsAmount());
            case LUCKPERMS -> reward.lpGroup() != null
                    ? "Привилегия: " + ColorUtil.paint("&#FCA865", reward.lpGroup())
                    + (reward.lpDuration() == null ? "" : " &7на " + ColorUtil.paint("&#FCA865", reward.lpDuration()))
                    : "Право: " + ColorUtil.paint("&#FCA865", reward.lpNode());
            case PERSONAL -> reward.commands().size() <= 1
                    ? "Команда: " + ColorUtil.paint("&#FCA865", reward.command() == null ? "не задана" : reward.command())
                    : "Команды: " + ColorUtil.paint("&#FCA865", reward.commands().size());
        };
    }

    static String valueTitle(Reward reward) {
        return switch (reward.type()) {
            case ITEM -> "Выдаваемый предмет";
            case VAULT -> "Монеты";
            case PLAYERPOINTS -> "Поинты";
            case LUCKPERMS -> reward.lpGroup() != null ? "Привилегия" : "Право";
            case PERSONAL -> "Команда";
        };
    }

    static String titleText(String value) {
        return value.length() <= 20 ? value : value.substring(0, 19) + "…";
    }

    static String rewardDescription(Reward reward) {
        return switch (reward.type()) {
            case ITEM -> "Предмет будет выдан игроку.";
            case VAULT -> "Монеты будут зачислены через Vault.";
            case PLAYERPOINTS -> "Поинты будут зачислены игроку.";
            case LUCKPERMS -> reward.lpGroup() != null
                    ? "Привилегия будет выдана через LuckPerms."
                    : "Право будет выдано через LuckPerms.";
            case PERSONAL -> "Команда будет выполнена от консоли.";
        };
    }

    /** Share of the pool as text without trailing zeros, for example "12.5". */
    static String chancePercent(Reward reward, CaseDefinition definition) {
        return percentText(sharePercent(reward, definition));
    }

    /** The share of the pool this reward has, in percent. */
    static double sharePercent(Reward reward, CaseDefinition definition) {
        int total = definition.rewards().stream().mapToInt(value -> Math.max(0, value.chance())).sum();
        return total <= 0 ? 0 : reward.chance() * 100.0 / total;
    }

    /** A percentage with up to two decimals and no trailing zeros, for example "12.5". */
    static String percentText(double percent) {
        String formatted = String.format(Locale.US, "%.2f", percent);
        if (formatted.indexOf('.') < 0) return formatted;
        return formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    static String currentValue(Reward reward) {
        return switch (reward.type()) {
            case ITEM -> "";
            case VAULT -> String.valueOf(reward.vaultAmount());
            case PLAYERPOINTS -> String.valueOf(reward.playerPointsAmount());
            case LUCKPERMS -> reward.lpGroup() != null ? reward.lpGroup() : String.valueOf(reward.lpNode());
            case PERSONAL -> reward.command() == null ? "" : reward.command();
        };
    }
}
