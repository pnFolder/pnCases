package ru.privatenull.pncases.gui.machine;

import ru.privatenull.pncases.gui.GuiSession;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.gui.GuiDesign;

import java.util.List;

import static ru.privatenull.pncases.gui.machine.MachineGuiSupport.*;

final class MachineHologramScreen {

    private final CaseManager caseManager;
    private final MachineGuiSupport state;
    private final MachineItemFactory items;

    MachineHologramScreen(CaseManager caseManager, MachineGuiSupport state, MachineItemFactory items) {
        this.caseManager = caseManager;
        this.state = state;
        this.items = items;
    }

    void open(Player player, String caseName) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) return;
        if (MachineGuiSupport.refreshIfOpen(
                caseManager, player, GuiSession.Screen.MACHINE_HOLOGRAM, definition.name(),
                inventory -> fill(inventory, definition))) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(
                GuiSession.machine(GuiSession.Screen.MACHINE_HOLOGRAM, caseName), 54,
                caseManager.getPlugin().getUi().text(
                        "machine.titles.hologram", "&0Студия голограммы", items.replacements(definition))
        );
        fill(inventory, definition);
        caseManager.getPlugin().openGui(player, inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    private void fill(Inventory inventory, CaseDefinition definition) {
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inventory.setItem(4, items.configuredButton(
                "machine.hologram.header", MachineIcon.HOLOGRAM_HEADER,
                items.name("Голограмма"),
                items.lore(List.of("Настройка текста над кейсом"),
                        List.of(), null, null), definition));
        inventory.setItem(SLOT_HOLOGRAM_TOGGLE, toggle(definition));
        inventory.setItem(SLOT_HOLOGRAM_HEIGHT, height(definition));
        inventory.setItem(SLOT_HOLOGRAM_LINES, lines(definition));
        inventory.setItem(SLOT_BACK, items.backButton(definition));
    }

    private ItemStack toggle(CaseDefinition definition) {
        boolean enabled = state.hologramEnabled(definition);
        return items.configuredButton("machine.hologram.toggle",
                enabled ? MachineIcon.HOLOGRAM_TOGGLE_ON : MachineIcon.HOLOGRAM_TOGGLE_OFF,
                items.name("Видимость"), items.lore(
                        "Включение и отключение голограммы",
                        "Состояние", enabled ? "&aВключена" : "&cВыключена",
                        enabled ? "Выключить" : "Включить", null), definition,
                "status", enabled ? "&aвключена" : "&cвыключена",
                "action", enabled ? "выключить" : "включить");
    }

    private ItemStack height(CaseDefinition definition) {
        return items.configuredButton("machine.hologram.height", MachineIcon.HOLOGRAM_HEIGHT,
                items.name("Высота"), items.lore(
                        List.of("Позиция голограммы над кейсом", "Shift изменяет значение на 1.0"),
                        List.of("Высота", String.valueOf(state.hologramHeight(definition))),
                        "Поднять на 0.1", "Опустить на 0.1"), definition,
                "value", String.valueOf(state.hologramHeight(definition)));
    }

    private ItemStack lines(CaseDefinition definition) {
        List<String> configured = state.hologramLines(definition);
        List<String> description = configured.isEmpty()
                ? List.of("Текст голограммы не задан")
                : configured.stream().map(line -> items.format(definition, line)).toList();
        List<String> lore = items.lore(description,
                List.of("Количество строк", String.valueOf(configured.size())),
                "Изменить", null);
        String visible = configured.isEmpty()
                ? "&8строк нет"
                : String.join("|", configured.stream()
                .map(line -> items.format(definition, line))
                .toList());
        return items.configuredButton("machine.hologram.lines", MachineIcon.HOLOGRAM_LINES,
                items.name("Текст голограммы"), lore, definition, "lines", visible);
    }
}
