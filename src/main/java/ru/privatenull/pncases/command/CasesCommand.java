package ru.privatenull.pncases.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.privatenull.pncases.PnCasesPlugin;
import ru.privatenull.pncases.support.text.ColorUtil;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.gui.machine.MachineGuiListener;
import ru.privatenull.pncases.gui.gift.KeyGiftMenu;

import java.util.Locale;
import java.util.UUID;

public final class CasesCommand implements CommandExecutor {

    private final PnCasesPlugin plugin;
    private final CaseManager caseManager;
    private final MachineGuiListener machineGui;
    private final CasesCommandMenu menu;
    private final KeyGiftMenu giftMenu;

    public CasesCommand(PnCasesPlugin plugin, CaseManager caseManager, MachineGuiListener machineGui,
                        KeyGiftMenu giftMenu) {
        this.plugin = plugin;
        this.caseManager = caseManager;
        this.machineGui = machineGui;
        this.giftMenu = giftMenu;
        this.menu = new CasesCommandMenu(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("gift")) {
            return gift(sender, args);
        }
        if (!sender.hasPermission("pncases.admin")) {
            sender.sendMessage(plugin.getUi().get("no-permission"));
            return true;
        }
        if (args.length == 0) {
            menu.send(sender);
            return true;
        }

        boolean known = switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "givekey" -> giveKey(sender, args);
            case "takekey" -> takeKey(sender, args);
            case "delcase" -> deleteCase(sender, args);
            case "createcase" -> createCase(sender, args);
            case "copycase" -> copyCase(sender, args);
            case "machine" -> openMachine(sender, args);
            case "workbench" -> openWorkbench(sender, args);
            case "setcase" -> setCase(sender, args);
            case "giftlimit" -> giftLimit(sender, args);
            default -> false;
        };
        if (!known) {
            sender.sendMessage(plugin.getUi().get("unknown-command"));
            menu.send(sender);
        }
        return true;
    }

    private boolean gift(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getUi().get("only-player"));
            return true;
        }
        if (!player.hasPermission(plugin.giftPermission())) {
            player.sendMessage(plugin.getUi().get("no-permission"));
            return true;
        }
        if (args.length < 2) {
            giftMenu.openPicker(player, 0);
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(plugin.getUi().get("gift-player-offline"));
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getUi().get("gift-self"));
            return true;
        }
        giftMenu.open(player, target);
        return true;
    }

    /**
     * /pncases giftlimit: opens the gift limits menu (the console gets a text list instead).
     * /pncases giftlimit [игрок] [reset]: shows a player's gift limits and what is left, or resets today's count.
     */
    private boolean giftLimit(CommandSender sender, String[] args) {
        var limits = plugin.getGiftLimits();
        if (args.length < 2 && sender instanceof Player player) {
            machineGui.openGifts(player);
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.colorize("&#FFE66DЛимиты подарков ключей:"));
            sender.sendMessage(ColorUtil.colorize("&#A6B0BA  по умолчанию: &f" + describe(limits.defaultLimit())));
            for (var rule : limits.rules()) {
                sender.sendMessage(ColorUtil.colorize("&#A6B0BA  " + rule.id() + " (&f" + rule.permission()
                        + "&#A6B0BA): &f" + describe(rule.limit())));
            }
            sender.sendMessage(ColorUtil.colorize("&#A6B0BAПосмотреть игрока: /pncases giftlimit <игрок> [reset]"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getUi().get("gift-player-offline"));
            return true;
        }
        if (args.length >= 3 && args[2].equalsIgnoreCase("reset")) {
            limits.reset(target.getUniqueId());
            sender.sendMessage(ColorUtil.colorize("&#A7F3D0Счётчик подарков игрока &f" + target.getName()
                    + "&#A7F3D0 за сегодня сброшен."));
            return true;
        }
        var limit = limits.limitFor(target);
        sender.sendMessage(ColorUtil.colorize("&#FFE66D" + target.getName() + "&#A6B0BA: &f" + describe(limit)
                + "&#A6B0BA, сегодня отправлено &f" + limits.sentToday(target.getUniqueId())));
        return true;
    }

    private static String describe(ru.privatenull.pncases.gift.GiftLimits.Limit limit) {
        return "за раз " + (limit.perGiftLimited() ? limit.perGift() : "без ограничений")
                + ", в день " + (limit.perDayLimited() ? limit.perDay() : "без ограничений");
    }

    private boolean reload(CommandSender sender) {
        var synchronization = plugin.synchronizeConfiguration();
        if (!synchronization.successful()) {
            sender.sendMessage(plugin.getUi().get("config-sync-failed",
                    "error", synchronization.error()));
            return true;
        }
        plugin.reloadConfig();
        plugin.getUi().load();
        plugin.reloadRuntimeConfig();
        boolean casesReloaded = caseManager.reloadFromConfig();
        if (casesReloaded) {
            sender.sendMessage(plugin.getUi().get("config-reloaded"));
        } else {
            sender.sendMessage(plugin.getUi().get("cases-reload-failed"));
        }
        return true;
    }

    private boolean giveKey(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(plugin.getUi().get("givekey-usage"));
            return true;
        }
        OfflinePlayer target = resolveOfflinePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getUi().get("givekey-player-not-found"));
            return true;
        }

        String keyId = args[2].toLowerCase(Locale.ROOT);
        ParsedAmount parsed = parseAmount(args, 3, true);
        if (parsed.amount() <= 0) {
            sender.sendMessage(plugin.getUi().get("givekey-amount-nan"));
            return true;
        }
        if (!caseManager.keyExists(keyId)) {
            sender.sendMessage(plugin.getUi().get("givekey-key-not-found", "key", keyId));
            return true;
        }

        String keyName = caseManager.getKeyDisplayName(keyId);
        UUID playerId = target.getUniqueId();
        plugin.getDatabaseExecutor().completeSync(
                plugin.getDatabaseExecutor().supply(playerId, () -> {
                    plugin.getDatabase().addKeys(playerId, keyId, parsed.amount());
                    return plugin.getDatabase().getKeys(playerId, keyId);
                }),
                balance -> {
                    caseManager.updateCachedKeyBalance(playerId, keyId, balance);
                    Player online = target.getPlayer();
                    if (online != null) {
                        sender.sendMessage(plugin.getUi().get("givekey-success-sender",
                                "amount", String.valueOf(parsed.amount()), "key_name", keyName,
                                "player", online.getName()));
                        if (!parsed.silent()) online.sendMessage(plugin.getUi().get("givekey-success-target",
                                "amount", String.valueOf(parsed.amount()), "key_name", keyName,
                                "balance", String.valueOf(balance)));
                    } else {
                        sender.sendMessage(plugin.getUi().get("givekey-offline",
                                "amount", String.valueOf(parsed.amount()), "key_name", keyName,
                                "player", offlineName(target)));
                    }
                },
                failure -> sender.sendMessage(plugin.getUi().get("case-open-storage-error")));
        return true;
    }

    private boolean takeKey(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(plugin.getUi().get("takekey-usage"));
            return true;
        }
        OfflinePlayer target = resolveOfflinePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getUi().get("givekey-player-not-found"));
            return true;
        }

        Integer amount = parseInteger(args[3]);
        if (amount == null) {
            sender.sendMessage(plugin.getUi().get("givekey-amount-nan"));
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage(plugin.getUi().get("givekey-amount-negative"));
            return true;
        }

        String keyId = args[2].toLowerCase(Locale.ROOT);
        if (!caseManager.keyExists(keyId)) {
            sender.sendMessage(plugin.getUi().get("givekey-key-not-found", "key", keyId));
            return true;
        }

        String keyName = caseManager.getKeyDisplayName(keyId);
        String playerName = offlineName(target);
        UUID playerId = target.getUniqueId();
        plugin.getDatabaseExecutor().completeSync(
                plugin.getDatabaseExecutor().supply(playerId, () -> {
                    boolean taken = plugin.getDatabase().takeKeys(playerId, keyId, amount);
                    return new KeyChange(taken, plugin.getDatabase().getKeys(playerId, keyId));
                }),
                change -> {
                    caseManager.updateCachedKeyBalance(playerId, keyId, change.balance());
                    if (!change.changed()) {
                        sender.sendMessage(plugin.getUi().get("takekey-not-enough",
                                "player", playerName, "key_name", keyName,
                                "have", String.valueOf(change.balance()), "amount", String.valueOf(amount)));
                        return;
                    }
                    sender.sendMessage(plugin.getUi().get("takekey-success-sender",
                            "player", playerName, "amount", String.valueOf(amount), "key_name", keyName));
                    Player online = target.getPlayer();
                    if (online != null) online.sendMessage(plugin.getUi().get("takekey-success-target",
                            "amount", String.valueOf(amount), "key_name", keyName,
                            "balance", String.valueOf(change.balance())));
                },
                failure -> sender.sendMessage(plugin.getUi().get("case-open-storage-error")));
        return true;
    }

    private boolean deleteCase(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getUi().get("delcase-usage"));
            return true;
        }
        String caseName = normalize(args[1]);
        switch (caseManager.deleteCase(caseName)) {
            case DELETED -> sender.sendMessage(plugin.getUi().get("delcase-success", "case", caseName));
            case NOT_FOUND -> sender.sendMessage(plugin.getUi().get("delcase-not-found", "case", caseName));
            case DELETE_FAILED -> sender.sendMessage(plugin.getUi().get("delcase-save-failed", "case", caseName));
            case RELOAD_FAILED -> sender.sendMessage(plugin.getUi().get("delcase-refresh-failed", "case", caseName));
        }
        return true;
    }

    private boolean createCase(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getUi().get("createcase-usage"));
            return true;
        }
        String caseName = normalize(args[1]);
        sendCreateResult(sender, caseName, caseManager.createCustomCase(caseName));
        return true;
    }

    private boolean copyCase(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ColorUtil.colorize("&#FC7165Использование: /pncases copycase <откуда> <куда>"));
            return true;
        }
        String from = normalize(args[1]);
        String to = normalize(args[2]);
        String message = switch (caseManager.copyCase(from, to)) {
            case COPIED -> "&#A7F3D0Кейс &f" + to + "&#A7F3D0 создан как копия &f" + from
                    + "&#A7F3D0. Привяжите его к блоку: /pncases setcase " + to;
            case SOURCE_NOT_FOUND -> "&#FC7165Кейс " + from + " не найден.";
            case ALREADY_EXISTS -> "&#FC7165Кейс " + to + " уже существует.";
            case INVALID_ID -> "&#FC7165Недопустимый id: только a-z, 0-9, _ и - (до 64 символов).";
            case SAVE_FAILED -> "&#FC7165Не удалось сохранить копию. Подробности в консоли.";
            case RELOAD_FAILED -> "&#FC7165Копия сохранена, но перезагрузка кейсов не удалась. Проверьте консоль.";
        };
        sender.sendMessage(ColorUtil.colorize(message));
        return true;
    }

    /** /pncases workbench [case]: the key recipes made in the crafting table; a case, or a list to pick one. */
    private boolean openWorkbench(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getUi().get("only-player"));
            return true;
        }
        if (args.length < 2) {
            machineGui.openWorkbench(player, null);
            return true;
        }
        CaseDefinition definition = caseManager.getCaseByName(normalize(args[1]));
        if (definition == null) {
            player.sendMessage(plugin.getUi().get("machine-look-at-case"));
            return true;
        }
        machineGui.openWorkbench(player, definition.name());
        return true;
    }

    private boolean openMachine(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getUi().get("only-player"));
            return true;
        }
        CaseDefinition definition = args.length >= 2
                ? caseManager.getCaseByName(normalize(args[1]))
                : caseAtTarget(player, 6);
        if (definition == null) {
            if (args.length >= 2) {
                player.sendMessage(plugin.getUi().get("machine-look-at-case"));
            } else {
                machineGui.openCases(player);
            }
            return true;
        }
        machineGui.openMain(player, definition.name());
        return true;
    }

    private boolean setCase(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getUi().get("only-player"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.getUi().get("setcase-usage"));
            return true;
        }

        String caseName = normalize(args[1]);
        Block target = player.getTargetBlockExact(5);
        if (target == null) {
            player.sendMessage(plugin.getUi().get("setcase-look-at-block"));
            return true;
        }
        if (!caseManager.caseExists(caseName)) {
            player.sendMessage(plugin.getUi().get("setcase-not-found", "case", caseName));
            return true;
        }

        switch (caseManager.bindCaseToBlock(caseName, target)) {
            case BOUND -> {
                player.sendMessage(plugin.getUi().get("setcase-success",
                        "case", caseName, "world", target.getWorld().getName(),
                        "x", String.valueOf(target.getX()), "y", String.valueOf(target.getY()),
                        "z", String.valueOf(target.getZ())));
                player.sendMessage(plugin.getUi().get("setcase-machine-hint", "case", caseName));
            }
            case ALREADY_BOUND -> player.sendMessage(plugin.getUi().get("setcase-already-bound", "case", caseName));
            case NOT_FOUND -> player.sendMessage(plugin.getUi().get("setcase-not-found", "case", caseName));
            case BLOCK_OCCUPIED -> player.sendMessage(plugin.getUi().get("setcase-block-occupied"));
            case SAVE_FAILED -> player.sendMessage(plugin.getUi().get("setcase-save-failed", "case", caseName));
            case REFRESH_FAILED -> player.sendMessage(plugin.getUi().get("setcase-refresh-failed", "case", caseName));
        }
        return true;
    }


    private void sendCreateResult(CommandSender sender, String caseName, CaseManager.CreateCaseResult result) {
        switch (result) {
            case CREATED -> sender.sendMessage(plugin.getUi().get("createcase-success", "case", caseName));
            case ALREADY_EXISTS -> sender.sendMessage(plugin.getUi().get("createcase-exists", "case", caseName));
            case INVALID_ID -> sender.sendMessage(plugin.getUi().get("createcase-invalid", "case", caseName));
            case SAVE_FAILED -> sender.sendMessage(plugin.getUi().get("createcase-failed", "case", caseName));
            case RELOAD_FAILED -> sender.sendMessage(plugin.getUi().get("createcase-reload-failed", "case", caseName));
        }
    }

    private CaseDefinition caseAtTarget(Player player, int distance) {
        Block target = player.getTargetBlockExact(distance);
        return target == null ? null : caseManager.getCaseByBlock(target);
    }

    private static ParsedAmount parseAmount(String[] args, int start, boolean allowSilent) {
        int amount = 0;
        boolean silent = false;
        for (int index = start; index < args.length; index++) {
            String value = args[index].trim();
            if (allowSilent && (value.equalsIgnoreCase("-s") || value.equalsIgnoreCase("-silent"))) {
                silent = true;
            } else if (amount == 0) {
                Integer parsed = parseInteger(value);
                if (parsed != null) amount = parsed;
            }
        }
        return new ParsedAmount(amount, silent);
    }

    private static Integer parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static OfflinePlayer resolveOfflinePlayer(String input) {
        try {
            return Bukkit.getOfflinePlayer(UUID.fromString(input));
        } catch (IllegalArgumentException ignored) {
            return Bukkit.getOfflinePlayerIfCached(input);
        }
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static String offlineName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    private record ParsedAmount(int amount, boolean silent) {
    }

    private record KeyChange(boolean changed, int balance) {
    }
}
