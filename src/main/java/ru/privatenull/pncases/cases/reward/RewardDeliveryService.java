package ru.privatenull.pncases.cases.reward;

import ru.privatenull.pncases.integration.Log;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.pncases.api.event.CaseRewardGrantEvent;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pncases.PnCasesPlugin;
import static ru.privatenull.pncases.cases.animation.core.AbstractCaseAnimation.playSound;
import ru.privatenull.pncases.storage.CasesDatabase;
import ru.privatenull.pncases.support.text.ColorUtil;

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
        return deliver(player, definition, reward, true);
    }

    /** Batch delivery keeps the actions/history, but announces only the completed series. */
    public boolean deliverSilently(Player player, CaseDefinition definition, Reward reward) {
        return deliver(player, definition, reward, false);
    }

    private boolean deliver(Player player, CaseDefinition definition, Reward reward, boolean announce) {
        if (player == null || reward == null) return false;
        ItemStack visual = presentation.buildDisplayItem(definition, reward);
        String rewardLabel = color(presentation.resolveViewName(reward, visual));
        boolean delivered = switch (reward.type()) {
            case ITEM -> deliverItem(player, reward);
            case LUCKPERMS -> deliverLuckPerms(player, reward);
            case VAULT -> deliverVault(player, reward);
            case PLAYERPOINTS -> deliverPlayerPoints(player, reward);
            case PERSONAL -> deliverPersonal(player, reward);
        };
        if (!delivered) return false;
        Bukkit.getPluginManager().callEvent(new CaseRewardGrantEvent(
                player, definition == null ? null : definition.name(), reward.type().name(), rewardLabel));
        if (definition != null && plugin.historyEnabled()) {
            String caseName = definition.name();
            String playerName = player.getName();
            long openedAt = java.time.Instant.now().getEpochSecond();
            plugin.getDatabaseExecutor().completeSync(
                    plugin.getDatabaseExecutor().run("history:" + caseName, () ->
                            database.addHistory(caseName, playerName, rewardLabel, openedAt)),
                    ignored -> { },
                    exception -> Log.warning("Награда выдана, но историю открытия сохранить не удалось.", exception));
        }
        try {
            if (announce) announceReward(player, reward, rewardLabel);
            if (announce && definition != null && definition.broadcastEnabled()) {
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
            Log.warning("Награда выдана, но объявление отправить не удалось.", exception);
        }
        return true;
    }

    private boolean deliverItem(Player player, Reward reward) {
        ItemStack item = reward.item();
        if (item == null) {
            player.sendMessage(plugin.getUi().get("reward.invalid"));
            Log.warning("Не удалось выдать ITEM-награду игроку " + player.getName()
                    + ": предмет отсутствует.");
            return false;
        }
        giveToInventoryOrDrop(player, item.clone());
        return true;
    }

    private boolean deliverLuckPerms(Player player, Reward reward) {
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            player.sendMessage(plugin.getUi().get("reward.provider-missing",
                    "provider", "LuckPerms"));
            Log.warning("Не удалось выдать LuckPerms-награду игроку " + player.getName()
                    + ": плагин LuckPerms недоступен.");
            return false;
        }

        String subject = player.getUniqueId().toString();
        String duration = reward.lpDuration();
        boolean attempted = false;
        boolean dispatched = true;
        if (reward.lpGroup() != null && !reward.lpGroup().isBlank()) {
            attempted = true;
            if (duration != null && !duration.isBlank()) {
                dispatched &= dispatchLuckPerms("lp user " + subject + " parent addtemp "
                        + reward.lpGroup() + " " + duration + " accumulate");
            } else {
                dispatched &= dispatchLuckPerms("lp user " + subject + " parent removetemp " + reward.lpGroup());
                dispatched &= dispatchLuckPerms("lp user " + subject + " parent add " + reward.lpGroup());
            }
        }
        if (reward.lpNode() != null && !reward.lpNode().isBlank()) {
            attempted = true;
            if (duration != null && !duration.isBlank()) {
                dispatched &= dispatchLuckPerms("lp user " + subject + " permission settemp "
                        + reward.lpNode() + " true " + duration + " accumulate");
            } else {
                dispatched &= dispatchLuckPerms("lp user " + subject + " permission unsettemp " + reward.lpNode());
                dispatched &= dispatchLuckPerms("lp user " + subject + " permission set " + reward.lpNode() + " true");
            }
        }
        if (!attempted || !dispatched) {
            player.sendMessage(plugin.getUi().get("reward.provider-missing",
                    "provider", "LuckPerms"));
            Log.warning("LuckPerms не подтвердил выдачу награды игроку " + player.getName() + ".");
            return false;
        }
        return true;
    }

    private boolean deliverVault(Player player, Reward reward) {
        var economy = plugin.getEconomies().vault();
        if (!economy.available() || !economy.deposit(player, reward.vaultAmount())) {
            player.sendMessage(plugin.getUi().get("reward.provider-missing",
                    "provider", "Vault"));
            Log.warning("Не удалось выдать Vault-награду игроку " + player.getName()
                    + ": " + reward.vaultAmount());
            return false;
        }
        return true;
    }

    private boolean deliverPlayerPoints(Player player, Reward reward) {
        var points = plugin.getEconomies().playerPoints();
        if (!points.available() || !points.deposit(player, reward.playerPointsAmount())) {
            player.sendMessage(plugin.getUi().get("reward.provider-missing",
                    "provider", "PlayerPoints"));
            Log.warning("Не удалось выдать PlayerPoints-награду игроку " + player.getName()
                    + ": " + reward.playerPointsAmount());
            return false;
        }
        return true;
    }

    private boolean deliverPersonal(Player player, Reward reward) {
        if (reward.commands().isEmpty()) return false;
        for (String configured : reward.commands()) {
            String command = configured
                    .replace("{player}", player.getName())
                    .replace("{uuid}", player.getUniqueId().toString());
            if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
                Log.warning("Не удалось выполнить PERSONAL-награду для "
                        + player.getName() + ": " + command);
                return false;
            }
        }
        return true;
    }

    private void announceReward(Player player, Reward reward, String rewardLabel) {
        sendRewardMessage(player, reward, rewardLabel,
                reward.type() == Reward.Type.VAULT ? presentation.formatVaultAmount(reward.vaultAmount()) : "",
                reward.type() == Reward.Type.PLAYERPOINTS ? presentation.formatPlayerPointsAmount(reward.playerPointsAmount()) : "");
        Sound sound = switch (reward.type()) {
            case ITEM, VAULT -> Sound.ENTITY_PLAYER_LEVELUP;
            case PLAYERPOINTS -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            case LUCKPERMS, PERSONAL -> Sound.UI_TOAST_CHALLENGE_COMPLETE;
        };
        float pitch = switch (reward.type()) {
            case ITEM -> 1.6f;
            case VAULT -> 1.7f;
            case PLAYERPOINTS -> 1.45f;
            case LUCKPERMS -> 1.2f;
            case PERSONAL -> 1.3f;
        };
        float volume = reward.type() == Reward.Type.LUCKPERMS || reward.type() == Reward.Type.PERSONAL
                ? 0.20f : 0.18f;
        playSound(player, player.getLocation(), sound, volume, pitch);
    }

    /** Called once, only after all rewards have been acknowledged by storage. */
    public void announceBatch(Player player, CaseDefinition definition, String caseId, List<Reward> rewards) {
        if (player == null || !player.isOnline()) return;
        try {
            String[] values = {"player", player.getName(), "case", definition == null ? caseId : caseDisplayName(definition),
                    "case_id", caseId, "amount", String.valueOf(rewards.size())};
            sendBatchSummary(player, values, batchRewardLines(definition, rewards));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.20f, 1.25f);
            if (definition != null && definition.broadcastEnabled()) {
                String broadcast = plugin.getUi().get("bulk-opening.broadcast", values);
                if (!broadcast.isBlank()) Bukkit.getOnlinePlayers().stream()
                        .filter(viewer -> !viewer.getUniqueId().equals(player.getUniqueId()))
                        .forEach(viewer -> viewer.sendMessage(broadcast));
            }
        } catch (RuntimeException exception) {
            Log.warning("Серия выдана, но итоговое уведомление отправить не удалось.", exception);
        }
    }

    /** The summary is a block like the broadcast of a single opening; the {rewards} line expands into one line per reward. */
    private void sendBatchSummary(Player player, String[] values, List<String> rewardLines) {
        String marker = "@@rewards@@";
        String[] withMarker = java.util.Arrays.copyOf(values, values.length + 2);
        withMarker[values.length] = "rewards";
        withMarker[values.length + 1] = marker;
        for (String line : plugin.getUi().getList("bulk-opening.completed", withMarker)) {
            if (org.bukkit.ChatColor.stripColor(line).contains(marker)) {
                rewardLines.forEach(player::sendMessage);
            } else {
                player.sendMessage(line);
            }
        }
    }

    /** One line per distinct reward; equal rewards are merged and get a multiplier. */
    private List<String> batchRewardLines(CaseDefinition definition, List<Reward> rewards) {
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (Reward reward : rewards) {
            String label = color(presentation.resolveViewName(reward, presentation.buildDisplayItem(definition, reward)));
            counts.merge(label, 1, Integer::sum);
        }
        List<String> lines = new java.util.ArrayList<>();
        counts.forEach((label, count) -> lines.add(plugin.getUi().get("bulk-opening.line",
                "reward", label, "count", count > 1 ? " &#A6B0BA×" + count : "")));
        return lines;
    }

    private boolean dispatchLuckPerms(String command) {
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    private void sendRewardMessage(Player player, Reward reward, String rewardLabel,
                                   String moneyAmount, String pointsAmount) {
        String configured = reward.message();
        if (configured == null || configured.isBlank()) return;
        player.sendMessage(formatMessage(player, reward, configured, rewardLabel, moneyAmount, pointsAmount));
    }

    private String formatMessage(Player player, Reward reward, String raw, String rewardLabel,
                                 String moneyAmount, String pointsAmount) {
        String amount = switch (reward.type()) {
            case ITEM -> reward.item() == null ? "" : String.valueOf(reward.item().getAmount());
            case VAULT -> moneyAmount;
            case PLAYERPOINTS -> pointsAmount;
            case LUCKPERMS, PERSONAL -> "";
        };
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
