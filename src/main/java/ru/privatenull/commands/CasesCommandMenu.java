package ru.privatenull.commands;

import org.bukkit.command.CommandSender;
import net.kyori.adventure.text.event.ClickEvent;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.pnlibrary.text.ColorUtil;
import ru.privatenull.pnlibrary.update.UpdateChecker;
import ru.privatenull.pnlibrary.compat.ServerVersion;

final class CasesCommandMenu {

    private final PnCasesPlugin plugin;

    CasesCommandMenu(PnCasesPlugin plugin) {
        this.plugin = plugin;
    }

    void send(CommandSender sender) {
        String currentVersion = plugin.getDescription().getVersion();
        String prefix = plugin.getUi().get("prefix");

        sender.sendMessage(ColorUtil.component(prefix + " &8| &fПанель администратора"));
        sender.sendMessage(ColorUtil.component(""));
        sender.sendMessage(ColorUtil.component("&#9EFC65 «Состояние плагина»"));
        sender.sendMessage(ColorUtil.component(" &7- &fВерсия: &#FCA865" + currentVersion));
        sender.sendMessage(ColorUtil.component(" &7- &fСервер: &#FCA865"
                + ServerVersion.current()));
        sender.sendMessage(ColorUtil.component(" &7- &fОбновление: " + updateStatus(currentVersion)));
        sender.sendMessage(ColorUtil.component(" &7- &fПоддержка: ")
                .append(ColorUtil.component("&#65D1FC" + plugin.getSupportDiscord())
                        .clickEvent(ClickEvent.openUrl(plugin.getSupportDiscord()))));
        sender.sendMessage(ColorUtil.component(""));
        sender.sendMessage(ColorUtil.component("&#65D1FC «Управление»"));
        sender.sendMessage(ColorUtil.component(""));
        sender.sendMessage(ColorUtil.component(" &#EFF7B9▸ /pncases reload &8— &7перезагрузить настройки"));
        sender.sendMessage(ColorUtil.component(" &#EFF7B9▸ /pncases machine &8[&7кейс&8] &8— &7открыть Machine"));
        sender.sendMessage(ColorUtil.component(" &#EFF7B9▸ /pncases createcase &8[&7id&8] &8— &7создать кейс"));
        sender.sendMessage(ColorUtil.component(" &#EFF7B9▸ /pncases setcase &8[&7кейс&8] &8— &7поставить кейс на блок"));
        sender.sendMessage(ColorUtil.component(" &#EFF7B9▸ /pncases delcase &8[&7кейс&8] &8— &7удалить кейс"));
        sender.sendMessage(ColorUtil.component(" &#EFF7B9▸ /pncases givekey &8[&7игрок&8] [&7кейс&8] [&7кол-во&8] &8— &7выдать ключи"));
        sender.sendMessage(ColorUtil.component(" &#EFF7B9▸ /pncases takekey &8[&7игрок&8] [&7кейс&8] [&7кол-во&8] &8— &7забрать ключи"));
    }

    private String updateStatus(String currentVersion) {
        UpdateChecker checker = plugin.getUpdateChecker();
        if (checker == null || !checker.isCheckCompleted()) {
            return "&7проверяется";
        }
        if (checker.isUpdateAvailable()) {
            String latest = checker.getLatestVersion();
            return "&eдоступно &f" + currentVersion + " &8→ &#D8DF9D"
                    + (latest == null || latest.isBlank() ? "неизвестно" : latest);
        }
        if (checker.getLastError() != null && !checker.getLastError().isBlank()) {
            return "&cпроверка недоступна";
        }
        return "&aактуальная версия";
    }

}
