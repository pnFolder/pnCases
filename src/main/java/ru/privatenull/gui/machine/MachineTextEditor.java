package ru.privatenull.gui.machine;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.CaseDefinition;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static ru.privatenull.gui.machine.MachineGuiSupport.section;

final class MachineTextEditor {

    private final CaseManager caseManager;
    private final MachineGuiSupport support;
    private final MachineMainScreen mainScreen;
    private final Map<UUID, PendingEdit> pendingEdits = new ConcurrentHashMap<>();

    MachineTextEditor(
            CaseManager caseManager,
            MachineGuiSupport support,
            MachineMainScreen mainScreen
    ) {
        this.caseManager = caseManager;
        this.support = support;
        this.mainScreen = mainScreen;
    }

    void startGuiTitle(Player player, CaseDefinition definition) {
        start(player, definition, Field.GUI_TITLE);
    }

    void startHologramLines(Player player, CaseDefinition definition) {
        start(player, definition, Field.HOLOGRAM_LINES);
    }

    private void start(Player player, CaseDefinition definition, Field field) {
        pendingEdits.put(player.getUniqueId(), new PendingEdit(definition.name(), field));
        player.closeInventory();

        switch (field) {
            case GUI_TITLE -> {
                message(player, "machine-text.gui-title-prompt");
                message(player, "machine-text.current", "value", definition.guiTitle());
            }
            case HOLOGRAM_LINES -> {
                message(player, "machine-text.hologram-prompt");
                message(player, "machine-text.hologram-example");
            }
        }
        message(player, "machine-text.cancel-hint");
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    void handleChat(AsyncPlayerChatEvent event) {
        PendingEdit pending = pendingEdits.remove(event.getPlayer().getUniqueId());
        if (pending == null) {
            return;
        }

        event.setCancelled(true);
        String value = event.getMessage() == null ? "" : event.getMessage().trim();
        Bukkit.getScheduler().runTask(caseManager.getPlugin(), () -> apply(event.getPlayer(), pending, value));
    }

    void cancel(Player player) {
        pendingEdits.remove(player.getUniqueId());
    }

    private void apply(Player player, PendingEdit pending, String value) {
        if (value.equalsIgnoreCase("cancel") || value.equalsIgnoreCase("отмена")) {
            message(player, "machine-text.cancelled");
            mainScreen.open(player, pending.caseName());
            return;
        }
        if (value.isBlank()) {
            message(player, "machine-text.empty");
            mainScreen.open(player, pending.caseName());
            return;
        }

        switch (pending.field()) {
            case GUI_TITLE -> support.update(player, pending.caseName(),
                    root -> section(root, "gui").set("title", value));
            case HOLOGRAM_LINES -> applyHologramLines(player, pending.caseName(), value);
        }
        mainScreen.open(player, pending.caseName());
    }

    private void applyHologramLines(Player player, String caseName, String value) {
        List<String> lines = support.parseHologramLines(value);
        if (lines.isEmpty()) {
            message(player, "machine-text.hologram-empty");
            return;
        }
        support.updateHologram(player, caseName, root -> {
            ConfigurationSection hologram = section(root, "hologram");
            hologram.set("enabled", true);
            hologram.set("lines", lines);
        });
    }

    private void message(Player player, String path, String... replacements) {
        player.sendMessage(caseManager.getPlugin().getUi().get(path, replacements));
    }

    private enum Field {
        GUI_TITLE,
        HOLOGRAM_LINES
    }

    private record PendingEdit(String caseName, Field field) {
    }
}
