package ru.privatenull.pncases.command;

import org.bukkit.command.CommandSender;
import net.kyori.adventure.text.event.ClickEvent;
import ru.privatenull.pncases.PnCasesPlugin;
import ru.privatenull.pncases.support.text.ColorUtil;
import ru.privatenull.pncases.support.compat.ServerVersion;

final class CasesCommandMenu {

    private final PnCasesPlugin plugin;

    CasesCommandMenu(PnCasesPlugin plugin) {
        this.plugin = plugin;
    }

    /** The version string the server reports, for example "1.21.4" or "26.3". */
    private static String minecraftVersion() {
        try {
            String reported = org.bukkit.Bukkit.getMinecraftVersion();
            if (reported != null && !reported.isBlank()) return reported;
        } catch (LinkageError unsupported) {
            // Servers without Paper's API fall back to the parsed version below.
        }
        return ServerVersion.current().toString();
    }

    void send(CommandSender sender) {
        String currentVersion = plugin.getDescription().getVersion();
        String prefix = plugin.getUi().get("prefix");

        sender.sendMessage(ColorUtil.component(prefix + " &8| &fПанель администратора"));
        sender.sendMessage(ColorUtil.component(""));
        sender.sendMessage(ColorUtil.component("&#9EFC65 «Состояние плагина»"));
        sender.sendMessage(ColorUtil.component(" &7- &fВерсия: " + ColorUtil.paint("&#FCA865", currentVersion)));
        sender.sendMessage(ColorUtil.component(" &7- &fСервер: " + ColorUtil.paint("&#FCA865", minecraftVersion())));
        sender.sendMessage(ColorUtil.component(" &7- &fОбновление: " + plugin.updateStatus(currentVersion)));
        sender.sendMessage(ColorUtil.component(" &7- &fПоддержка: ")
                .append(ColorUtil.component("&#65D1FC" + plugin.getSupportDiscord())
                        .clickEvent(ClickEvent.openUrl(plugin.getSupportDiscord()))));
        sender.sendMessage(ColorUtil.component(""));
        sender.sendMessage(ColorUtil.component("&#65D1FC «Управление»"));
        sender.sendMessage(ColorUtil.component(" &#EFF7B9▸ /pncases reload &8— &7перезагрузить настройки"));
        sender.sendMessage(ColorUtil.component(" &#EFF7B9▸ /pncases machine &8[&7кейс&8] &8— &7открыть Machine"));
        sender.sendMessage(ColorUtil.component(" &#EFF7B9▸ /pncases createcase &8[&7id&8] &8— &7создать кейс"));
        sender.sendMessage(ColorUtil.component(" &#EFF7B9▸ /pncases copycase &8[&7откуда&8] [&7куда&8] &8— &7копия кейса"));
        sender.sendMessage(ColorUtil.component(" &#EFF7B9▸ /pncases setcase &8[&7кейс&8] &8— &7поставить кейс на блок"));
        sender.sendMessage(ColorUtil.component(" &#EFF7B9▸ /pncases delcase &8[&7кейс&8] &8— &7удалить кейс"));
        sender.sendMessage(ColorUtil.component(" &#EFF7B9▸ /pncases givekey &8[&7игрок&8] [&7кейс&8] [&7кол-во&8] &8— &7выдать ключи"));
        sender.sendMessage(ColorUtil.component(" &#EFF7B9▸ /pncases takekey &8[&7игрок&8] [&7кейс&8] [&7кол-во&8] &8— &7забрать ключи"));
        sender.sendMessage(ColorUtil.component(" &#EFF7B9▸ /pncases gift &8[&7игрок&8] &8— &7подарить ключи другому игроку (меню)"));
        sender.sendMessage(ColorUtil.component(" &#EFF7B9▸ /pncases workbench &8[&7кейс&8] &8— &7рецепты ключей: игроки крафтят их в обычном верстаке"));
        sender.sendMessage(ColorUtil.component(" &#EFF7B9▸ /pncases giftlimit &8—&7меню лимитов подарков (по группам и общие)"));
        sender.sendMessage(ColorUtil.component(" &#EFF7B9▸ /pncases giftlimit &8<&7игрок&8> [reset] &8— &7лимит игрока и сколько он подарил сегодня"));
        sender.sendMessage(ColorUtil.component(""));
    }

}
