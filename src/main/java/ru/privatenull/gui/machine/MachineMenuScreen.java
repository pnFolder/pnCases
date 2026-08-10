package ru.privatenull.gui.machine;

import ru.privatenull.gui.GuiSession;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.gui.GuiDesign;
import ru.privatenull.modules.ModuleId;

import java.util.List;

import static ru.privatenull.gui.machine.MachineGuiSupport.*;

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
                        "machine.titles.menu", "&0Настройка меню кейса", items.replacements(definition))
        );
        fill(inventory, definition);
        caseManager.getPlugin().openGui(player, inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    private void fill(Inventory inventory, CaseDefinition definition) {
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inventory.setItem(4, items.configuredButton(
                "machine.menu.header", GuiDesign.MENU,
                "&x&4&2&9&F&9&1Раздел: Меню кейса", List.of(
                        "", "&#9EFC65 «Описание»",
                        " &7- &fНастройте внешний вид меню кейса.",
                        " &7- &fЗдесь меняются размер, название",
                        " &7- &fи расположение всех элементов.",
                        ""), definition));
        inventory.setItem(SLOT_GUI_SIZE, size(definition));
        inventory.setItem(SLOT_GUI_TITLE, title(definition));
        inventory.setItem(SLOT_LAYOUT, items.configuredButton(
                "machine.menu.layout", GuiDesign.LAYOUT,
                "&x&4&2&9&F&9&1Расставить слоты", List.of(
                        "", "&#9EFC65 «Описание»",
                        " &7- &fНазначьте каждому слоту его роль.",
                        " &7- &fМожно менять и сами предметы.",
                        "", "&#FCA965 «Управление»",
                        " &7- &fЛКМ — открыть редактор", ""), definition));
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
                "machine.menu.preview-case", GuiDesign.PREVIEW,
                "&x&4&2&9&F&9&1Предпросмотр меню",
                List.of("", "&#9EFC65 «Описание»",
                        " &7- &fПоказывает итоговое меню кейса",
                        " &7- &fточно так же, как его увидит игрок.",
                        "", "&#FCA965 «Управление»",
                        " &7- &fЛКМ — посмотреть", ""),
                definition));
        inventory.setItem(SLOT_BACK, items.backButton(definition));
    }

    private ItemStack size(CaseDefinition definition) {
        int rows = definition.guiLayout().size() / 9;
        return items.configuredButton("machine.menu.size", GuiDesign.SIZE,
                "&x&4&2&9&F&9&1Размер меню", List.of(
                        "", "&#65D1FC «Сейчас»",
                        " &7- &fРазмер: &#FCA865" + definition.guiLayout().size() + " слотов",
                        " &7- &fРядов: &#FCA865" + rows,
                        "", "&#FCA965 «Управление»",
                        " &7- &fЛКМ — добавить ряд",
                        " &7- &fПКМ — убрать ряд",
                        " &7- &fShift — изменить сразу на два ряда", ""), definition,
                "value", String.valueOf(definition.guiLayout().size()),
                "rows", String.valueOf(rows));
    }

    private ItemStack title(CaseDefinition definition) {
        return items.configuredButton("machine.menu.title", GuiDesign.TITLE,
                "&x&4&2&9&F&9&1Название меню", List.of(
                        "", "&#65D1FC «Сейчас»",
                        " &7- &fНазвание: " + definition.guiTitle(),
                        "", "&#FCA965 «Управление»",
                        " &7- &fЛКМ — изменить через чат", ""), definition,
                "value", definition.guiTitle());
    }

    private ItemStack contentToggle(CaseDefinition definition) {
        boolean moduleEnabled = caseManager.getPlugin().getModules().isEnabled(ModuleId.PREVIEW);
        boolean enabled = moduleEnabled && definition.guiLayout().previewEnabled();
        return items.configuredButton("machine.menu.content-toggle", GuiDesign.CONTENT,
                "&x&4&2&9&F&9&1Содержимое кейса", List.of(
                        "", "&#65D1FC «Сейчас»",
                        " &7- &fМодуль: " + (moduleEnabled ? "&aвключён" : "&cвыключен"),
                        " &7- &fДля кейса: " + (definition.guiLayout().previewEnabled() ? "&aвключено" : "&cвыключено"),
                        "", "&#9EFC65 «Результат»",
                        enabled
                                ? " &7- &fЛКМ открывает список наград."
                                : " &7- &fЛКМ и ПКМ сразу открывают кейс.",
                        "", "&#FCA965 «Управление»",
                        moduleEnabled
                                ? " &7- &fЛКМ — " + (enabled ? "выключить" : "включить")
                                : " &7- &fВключите модуль в разделе «Модули».", ""), definition,
                "status", enabled ? "&aвключено" : "&cвыключено",
                "action", enabled ? "выключить" : "включить");
    }

}
