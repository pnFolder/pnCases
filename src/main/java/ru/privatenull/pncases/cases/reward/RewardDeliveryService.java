package ru.privatenull.pncases.cases.reward;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pncases.PnCasesPlugin;
import static ru.privatenull.pncases.cases.animation.core.AbstractCaseAnimation.playSound;
import ru.privatenull.pncases.storage.CasesDatabase;
import ru.privatenull.pnlibrary.text.ColorUtil;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class RewardDeliveryService {

    private final PnCasesPlugin plugin;
    private final CasesDatabase database;
    private final RewardPresentationService presentation;

    public RewardDeliveryService(PnCasesPlugin plugin, CasesDatabase database,
                                 RewardPresentationService presentation) {
        this.plugin = plugin;
        this.database = database;
        this.presentation = presentation;
    }

    public boolean deliver(Player player, CaseDefinition definition, Reward reward) {
        if (player == null || reward == null) return false;
        ItemStack visual = presentation.buildDisplayItem(definition, reward);
        String rewardLabel = color(presentation.resolveViewName(reward, visual));
        boolean delivered = switch (reward.type()) {
            case ITEM -> deliverItem(player, reward, rewardLabel);
            case LUCKPERMS -> deliverLuckPerms(player, reward, rewardLabel);
            case VAULT -> deliverVault(player, reward, rewardLabel);
            case PLAYERPOINTS -> deliverPlayerPoints(player, reward, rewardLabel);
            case PERSONAL -> deliverPersonal(player, reward, rewardLabel);
        };
        if (!delivered) return false;
        if (definition != null) {
            String caseName = definition.name();
            String playerName = player.getName();
            long openedAt = java.time.Instant.now().getEpochSecond();
            plugin.getDatabaseExecutor().completeSync(
                    plugin.getDatabaseExecutor().run("history:" + caseName, () ->
                            database.addHistory(caseName, playerName, rewardLabel, openedAt)),
                    ignored -> { },
                    exception -> plugin.getLogger().log(Level.WARNING,
                            "Награда выдана, но историю открытия сохранить не удалось.", exception));
        }
        try {
            if (definition != null && definition.broadcastEnabled()) {
                List<String> broadcast = definition.broadcastLines().stream()
                        .map(line -> formatBroadcast(
                                line,
                                player.getName(),
                                caseDisplayName(definition),
                                definition.name(),
                                rewardLabel))
                        .toList();
                Bukkit.getOnlinePlayers().forEach(
                        online -> broadcast.forEach(online::sendMessage));
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Награда выдана, но объявление отправить не удалось.", exception);
        }
        return true;
    }

    private boolean deliverItem(Player player, Reward reward, String rewardLabel) {
        ItemStack item = reward.item();
        if (item == null) {
            player.sendMessage(plugin.getUi().get("reward.invalid"));
            plugin.getLogger().warning("Не удалось выдать ITEM-награду игроку " + player.getName()
                    + ": предмет отсутствует.");
            return false;
        }
        giveToInventoryOrDrop(player, item.clone());
        sendRewardMessage(player, reward, rewardLabel, "", "");
        playSound(player, player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.18f, 1.6f);
        return true;
    }

    private boolean deliverLuckPerms(Player player, Reward reward, String rewardLabel) {
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            player.sendMessage(plugin.getUi().get("reward.provider-missing",
                    "provider", "LuckPerms"));
            plugin.getLogger().warning("Не удалось выдать LuckPerms-награду игроку " + player.getName()
                    + ": плагин LuckPerms недоступен.");
            return false;
        }

        String subject = player.getUniqueId().toString();
        String duration = reward.lpDuration();
        boolean attempted = false;
        boolean dispatched = true;
        if (reward.lpGroup() != null && !reward.lpGroup().isBlank()) {
            String command = duration != null && !duration.isBlank()
                    ? "lp user " + subject + " parent addtemp " + reward.lpGroup() + " " + duration
                    : "lp user " + subject + " parent add " + reward.lpGroup();
            attempted = true;
            dispatched &= Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
        if (reward.lpNode() != null && !reward.lpNode().isBlank()) {
            String command = duration != null && !duration.isBlank()
                    ? "lp user " + subject + " permission settemp " + reward.lpNode() + " true " + duration
                    : "lp user " + subject + " permission set " + reward.lpNode() + " true";
            attempted = true;
            dispatched &= Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
        if (!attempted || !dispatched) {
            player.sendMessage(plugin.getUi().get("reward.provider-missing",
                    "provider", "LuckPerms"));
            plugin.getLogger().warning("LuckPerms не подтвердил выдачу награды игроку " + player.getName() + ".");
            return false;
        }
        sendRewardMessage(player, reward, rewardLabel, "", "");
        playSound(player, player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.20f, 1.2f);
        return true;
    }

    private boolean deliverVault(Player player, Reward reward, String rewardLabel) {
        var economy = plugin.getEconomies().vault();
        String amount = presentation.formatVaultAmount(reward.vaultAmount());
        if (!economy.available() || !economy.deposit(player, reward.vaultAmount())) {
            player.sendMessage(plugin.getUi().get("reward.provider-missing",
                    "provider", "Vault"));
            plugin.getLogger().warning("Не удалось выдать Vault-награду игроку " + player.getName()
                    + ": " + reward.vaultAmount());
            return false;
        }
        sendRewardMessage(player, reward, rewardLabel, amount, "");
        playSound(player, player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.18f, 1.7f);
        return true;
    }

    private boolean deliverPlayerPoints(Player player, Reward reward, String rewardLabel) {
        var points = plugin.getEconomies().playerPoints();
        String amount = presentation.formatPlayerPointsAmount(reward.playerPointsAmount());
        if (!points.available() || !points.deposit(player, reward.playerPointsAmount())) {
            player.sendMessage(plugin.getUi().get("reward.provider-missing",
                    "provider", "PlayerPoints"));
            plugin.getLogger().warning("Не удалось выдать PlayerPoints-награду игроку " + player.getName()
                    + ": " + reward.playerPointsAmount());
            return false;
        }
        sendRewardMessage(player, reward, rewardLabel, "", amount);
        playSound(player, player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.18f, 1.45f);
        return true;
    }

    private boolean deliverPersonal(Player player, Reward reward, String rewardLabel) {
        String command = reward.command();
        if (command == null || command.isBlank()) return false;
        command = command.trim().replaceFirst("^/", "")
                .replace("{player}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString());
        if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
            plugin.getLogger().warning("Не удалось выполнить PERSONAL-награду для " + player.getName() + ": " + command);
            return false;
        }
        sendRewardMessage(player, reward, rewardLabel, "", "");
        playSound(player, player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.20f, 1.3f);
        return true;
    }

    private void sendRewardMessage(Player player, Reward reward, String rewardLabel,
                                   String moneyAmount, String pointsAmount) {
        String configured = reward.message();
        if (configured == null || configured.isBlank()) return;
        player.sendMessage(formatMessage(player, reward, configured, rewardLabel, moneyAmount, pointsAmount));
    }

    private String formatMessage(Player player, Reward reward, String raw, String rewardLabel,
                                 String moneyAmount, String pointsAmount) {
        String amount = !moneyAmount.isBlank() ? moneyAmount : pointsAmount;
        return color(raw
                .replace("{player}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString())
                .replace("{reward}", rewardLabel)
                .replace("{amount}", amount)
                .replace("{money}", moneyAmount)
                .replace("{points}", pointsAmount)
                .replace("{group}", value(reward.lpGroup()))
                .replace("{permission}", value(reward.lpNode()))
                .replace("{duration}", value(reward.lpDuration())));
    }

    private String formatBroadcast(
            String raw,
            String player,
            String caseName,
            String caseId,
            String reward
    ) {
        return color((raw == null ? "" : raw)
                .replace("{player}", player)
                .replace("{case}", caseName)
                .replace("{case_id}", caseId)
                .replace("{reward}", reward));
    }

    private void giveToInventoryOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        leftover.values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
    }

    private String caseDisplayName(CaseDefinition definition) {
        if (definition == null) return "кейс";
        if (definition.displayName() != null && !definition.displayName().isBlank()) {
            return color(definition.displayName());
        }
        if (definition.guiTitle() != null && !definition.guiTitle().isBlank()) {
            return color(definition.guiTitle());
        }
        return definition.name();
    }

    private static String color(String value) {
        return ColorUtil.colorize(value);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
