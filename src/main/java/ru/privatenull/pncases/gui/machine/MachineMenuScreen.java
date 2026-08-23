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

final class MachineMenuScreen {

    private final CaseManager caseManager;
    private final MachineItemFactory items;

    MachineMenuScreen(CaseManager caseManager, MachineItemFactory items) {
        this.caseManager = caseManager;
        this.items = items;
    }

    void open(Player player, String caseName) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) return;
        if (MachineGuiSupport.refreshIfOpen(
                caseManager, player, GuiSession.Screen.MACHINE_MENU, definition.name(),
                inventory -> fill(inventory, definition))) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(
                GuiSession.machine(GuiSession.Screen.MACHINE_MENU, caseName), 54,
                caseManager.getPlugin().getUi().text(
                        "machine.titles.menu", "&0Конструктор интерфейса", items.replacements(definition))
        );
        fill(inventory, definition);
        caseManager.getPlugin().openGui(player, inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    private void fill(Inventory inventory, CaseDefinition definition) {
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inventory.setItem(4, items.configuredButton(
                "machine.menu.header", MachineIcon.MENU_HEADER,
                items.name("Интерфейс игроков"), items.lore(
                        List.of("Настройка меню, которое увидят игроки"),
                        List.of(), null, null), definition));
        inventory.setItem(SLOT_GUI_SIZE, size(definition));
        inventory.setItem(SLOT_GUI_TITLE, title(definition));
        inventory.setItem(SLOT_LAYOUT, items.configuredButton(
                "machine.menu.layout", MachineIcon.MENU_LAYOUT,
                items.name("Раскладка"), items.lore(
                        "Назначение роли для каждого слота",
                        "Слоты", String.valueOf(definition.guiLayout().size()),
                        "Изменить", null), definition));
        inventory.setItem(SLOT_OPEN_ITEM, items.copyItemButton(
                "machine.menu.button", definition.openButton(), Material.CHEST, definition,
                "&x&4&2&9&F&9&1Кнопка кейса", "Предмет, по которому игрок открывает кейс."));
        inventory.setItem(SLOT_DECOR_ITEM, items.copyItemButton(
                "machine.menu.decor-item", definition.guiLayout().decorItem(),
                Material.GRAY_STAINED_GLASS_PANE, definition,
                "&x&4&2&9&F&9&1Декор меню", "Предмет, которым заполняются декоративные слоты."));
        inventory.setItem(SLOT_EMPTY_HISTORY_ITEM, items.copyItemButton(
                "machine.menu.empty-history", definition.guiLayout().emptyHistoryItem(),
                Material.BARRIER, definition,
                "&x&4&2&9&F&9&1Пустая история",
                "Предмет, который отображается в свободных слотах истории."));
        inventory.setItem(SLOT_CONTENT_TOGGLE, contentToggle(definition));
        inventory.setItem(SLOT_PREVIEW_CASE, items.configuredButton(
                "machine.menu.preview-case", MachineIcon.MENU_PREVIEW,
                items.name("Предпросмотр"),
                items.lore("Проверка готового меню без запуска кейса",
                        null, null, "Открыть", null),
                definition));
        inventory.setItem(SLOT_BACK, items.backButton(definition));
    }

    private ItemStack size(CaseDefinition definition) {
        int rows = definition.guiLayout().size() / 9;
        return items.configuredButton("machine.menu.size", MachineIcon.MENU_SIZE,
                items.name("Размер интерфейса"), items.lore(
                        List.of("Количество слотов в меню", "Shift изменяет размер на 2 ряда"),
                        List.of("Размер", definition.guiLayout().size() + " слотов"),
                        "+1 ряд", "-1 ряд"), definition,
                "value", String.valueOf(definition.guiLayout().size()),
                "rows", String.valueOf(rows));
    }

    private ItemStack title(CaseDefinition definition) {
        return items.configuredButton("machine.menu.title", MachineIcon.MENU_TITLE,
                items.name("Заголовок"), items.lore(
                        "Название окна, которое видит игрок",
                        "Текст", definition.guiTitle(),
                        "Изменить", null), definition,
                "value", definition.guiTitle());
    }

    private ItemStack contentToggle(CaseDefinition definition) {
        boolean featureEnabled = caseManager.getPlugin().previewEnabled();
        boolean enabled = featureEnabled && definition.guiLayout().previewEnabled();
        return items.configuredButton("machine.menu.content-toggle", MachineIcon.MENU_CONTENT,
                items.name("Предпросмотр наград"), items.lore(
                        featureEnabled ? "Открывает список наград перед открытием"
                                : "Функция отключена в общих настройках",
                        "Состояние", enabled ? "&aВключено" : "&cВыключено",
                        featureEnabled ? (enabled ? "Выключить" : "Включить") : null, null), definition,
                "status", enabled ? "&aвключено" : "&cвыключено",
                "action", enabled ? "выключить" : "включить");
    }

}
