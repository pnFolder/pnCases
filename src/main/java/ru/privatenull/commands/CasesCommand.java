package ru.privatenull.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.gui.machine.MachineGuiListener;
import ru.privatenull.pnlibrary.update.UpdateChecker;
import ru.privatenull.pnlibrary.text.ColorUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class CasesCommand implements CommandExecutor, TabCompleter {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final List<String> SUBCOMMANDS = List.of(
            "createcase", "setcase", "delcase", "machine", "givekey", "takekey", "reload");
    private static final List<String> CASE_SUBCOMMANDS = List.of("setcase", "delcase", "machine");
    private static final List<String> AMOUNTS = List.of("1", "2", "3", "5", "10", "16", "32", "64");
    private static final List<String> SILENT_FLAG = List.of("-s");

    private final PnCasesPlugin plugin;
    private final CaseManager caseManager;
    private final MachineGuiListener machineGui;

    public CasesCommand(PnCasesPlugin plugin, CaseManager caseManager, MachineGuiListener machineGui) {
        this.plugin = plugin;
        this.caseManager = caseManager;
        this.machineGui = machineGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pncases.admin")) {
            sender.sendMessage(plugin.getUi().get("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sendCommandMenu(sender);
            return true;
        }

        boolean known = switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "givekey" -> giveKey(sender, args);
            case "takekey" -> takeKey(sender, args);
            case "delcase" -> deleteCase(sender, args);
            case "createcase" -> createCase(sender, args);
            case "machine" -> openMachine(sender, args);
            case "setcase" -> setCase(sender, args);
            default -> false;
        };
        if (!known) {
            sender.sendMessage(plugin.getUi().get("unknown-command"));
            sendCommandMenu(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("pncases.admin")) return new ArrayList<>();
        if (args.length == 1) return filter(SUBCOMMANDS, args[0]);

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (CASE_SUBCOMMANDS.contains(subcommand) && args.length == 2) {
            return filter(caseManager.getConfiguredCaseNames(), args[1]);
        }
        if (subcommand.equals("givekey") || subcommand.equals("takekey")) {
            if (args.length == 2) return filter(onlinePlayerNames(), args[1]);
            if (args.length == 3) return filter(caseManager.getKeyNames(), args[2]);
            if (args.length == 4) return filter(AMOUNTS, args[3]);
            if (args.length == 5 && subcommand.equals("givekey")) {
                return filter(SILENT_FLAG, args[4]);
            }
        }
        return new ArrayList<>();
    }

    private static List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
        return names;
    }

    private static List<String> filter(List<String> values, String prefix) {
        List<String> matches = new ArrayList<>();
        StringUtil.copyPartialMatches(prefix == null ? "" : prefix, values, matches);
        Collections.sort(matches);
        return matches;
    }

    private boolean reload(CommandSender sender) {
        plugin.reloadConfig();
        var validation = plugin.validateCurrentConfig();
        plugin.getUi().load();
        boolean casesReloaded = caseManager.reloadFromConfig();
        plugin.reloadRuntimeConfig();
        if (casesReloaded) {
            sender.sendMessage(plugin.getUi().get("config-reloaded"));
        } else {
            sender.sendMessage(plugin.getUi().get("cases-reload-failed"));
        }
        if (validation.hasProblems()) {
            sender.sendMessage(plugin.getUi().get("config-validation-result",
                    "errors", String.valueOf(validation.errors()),
                    "warnings", String.valueOf(validation.warnings()),
                    "patches", String.valueOf(validation.patches())));
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

        plugin.getDatabase().addKeys(target.getUniqueId(), keyId, parsed.amount());
        String keyName = caseManager.getKeyDisplayName(keyId);
        Player online = target.getPlayer();
        if (online != null) {
            int balance = plugin.getDatabase().getKeys(online.getUniqueId(), keyId);
            sender.sendMessage(plugin.getUi().get("givekey-success-sender",
                    "amount", String.valueOf(parsed.amount()), "key_name", keyName, "player", online.getName()));
            if (!parsed.silent()) {
                online.sendMessage(plugin.getUi().get("givekey-success-target",
                        "amount", String.valueOf(parsed.amount()), "key_name", keyName,
                        "balance", String.valueOf(balance)));
            }
        } else {
            sender.sendMessage(plugin.getUi().get("givekey-offline",
                    "amount", String.valueOf(parsed.amount()), "key_name", keyName,
                    "player", offlineName(target)));
        }
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
        if (!plugin.getDatabase().takeKeys(target.getUniqueId(), keyId, amount)) {
            int have = plugin.getDatabase().getKeys(target.getUniqueId(), keyId);
            sender.sendMessage(plugin.getUi().get("takekey-not-enough",
                    "player", playerName, "key_name", keyName,
                    "have", String.valueOf(have), "amount", String.valueOf(amount)));
            return true;
        }

        sender.sendMessage(plugin.getUi().get("takekey-success-sender",
                "player", playerName, "amount", String.valueOf(amount), "key_name", keyName));
        Player online = target.getPlayer();
        if (online != null) {
            int balance = plugin.getDatabase().getKeys(online.getUniqueId(), keyId);
            online.sendMessage(plugin.getUi().get("takekey-success-target",
                    "amount", String.valueOf(amount), "key_name", keyName,
                    "balance", String.valueOf(balance)));
        }
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

    private boolean openMachine(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getUi().get("only-player"));
            return true;
        }
        CaseDefinition definition = args.length >= 2
                ? caseManager.getCaseByName(normalize(args[1]))
                : caseAtTarget(player, 6);
        if (definition == null) {
            player.sendMessage(plugin.getUi().get("machine-look-at-case"));
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

    private void sendCommandMenu(CommandSender sender) {
        String current = plugin.getDescription().getVersion();
        MenuUpdateInfo update = menuUpdateInfo(current);
        String prefix = plugin.getUi().get("prefix");

        sender.sendMessage(menuComponent(prefix + " &8| &fПанель администратора"));
        sender.sendMessage(menuComponent(""));
        sender.sendMessage(menuComponent("&#9EFC65 «Состояние плагина»"));
        sender.sendMessage(menuComponent(" &7- &fВерсия: &#FCA865" + current));
        sender.sendMessage(menuComponent(" &7- &fСерверы: &#FCA8651.16.5–1.21.11"));
        sender.sendMessage(menuComponent(" &7- &fОбновление: " + update.status()));
        sender.sendMessage(menuComponent(" &7- &fПоддержка: &#65D1FC" + plugin.getSupportDiscord()));
        sender.sendMessage(menuComponent(""));
        sender.sendMessage(menuComponent("&#65D1FC «Управление»"));
        sender.sendMessage(menuComponent(" &7Нажмите на команду, чтобы вставить её в чат."));
        sender.sendMessage(menuComponent(""));
        sendCommandLine(sender, "/pncases reload", "перезагрузить настройки");
        sendCommandLine(sender, "/pncases machine <кейс>", "открыть Machine");
        sendCommandLine(sender, "/pncases createcase <id>", "создать кейс");
        sendCommandLine(sender, "/pncases setcase <кейс>", "поставить кейс на блок");
        sendCommandLine(sender, "/pncases delcase <кейс>", "удалить кейс");
        sendCommandLine(sender, "/pncases givekey <игрок> <кейс> <кол-во>", "выдать ключи");
        sendCommandLine(sender, "/pncases takekey <игрок> <кейс> <кол-во>", "забрать ключи");
    }

    private static void sendCommandLine(CommandSender sender, String command, String description) {
        Component line = menuComponent(" &#EFF7B9▸ ")
                .append(menuComponent("&#EFF7B9" + command)
                        .clickEvent(ClickEvent.suggestCommand(command.replaceAll("<[^>]+>", ""))))
                .append(menuComponent(" &8— &7" + description));
        sender.sendMessage(line);
    }

    private static Component menuComponent(String text) {
        return LEGACY.deserialize(ColorUtil.colorize(text));
    }

    private MenuUpdateInfo menuUpdateInfo(String current) {
        UpdateChecker checker = plugin.getUpdateChecker();
        if (checker == null || !checker.isCheckCompleted()) {
            return new MenuUpdateInfo("&7проверяется", "неизвестно", "");
        }
        if (checker.isUpdateAvailable()) {
            String latest = knownOrUnknown(checker.getLatestVersion());
            return new MenuUpdateInfo(
                    "&eдоступно &f" + current + " &8→ §x§D§8§D§F§9§D" + latest,
                    latest,
                    knownOrUnknown(checker.getDownloadUrl())
            );
        }
        String error = checker.getLastError();
        if (error != null && !error.isBlank()) {
            String stripped = ChatColor.stripColor(error);
            return new MenuUpdateInfo("&cошибка проверки: &f" + (stripped == null ? error : stripped),
                    knownOrUnknown(checker.getLatestVersion()), "");
        }
        return new MenuUpdateInfo("&aпоследняя версия", knownOrUnknown(checker.getLatestVersion()), "");
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

    private static String knownOrUnknown(String value) {
        return value == null || value.isBlank() ? "неизвестно" : value;
    }

    private record ParsedAmount(int amount, boolean silent) {
    }

    private record MenuUpdateInfo(String status, String latest, String download) {
    }
}
