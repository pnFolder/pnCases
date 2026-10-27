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
import ru.privatenull.pncases.gui.machine.MachineItemFactory.Hint;

import java.util.List;

import static ru.privatenull.pncases.gui.machine.MachineGuiSupport.*;

/**
 * The window players see: size, title and layout in the first row, the three configurable items in the
 * second, and the content-preview switch with a "look as a player" button below.
 */
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
                        "machine.titles.menu", "&0Меню игрока", items.replacements(definition))
        );
        fill(inventory, definition);
        caseManager.getPlugin().openGui(player, inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    private void fill(Inventory inventory, CaseDefinition definition) {
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        // A bright strip behind the three item buttons so a gray-glass decor item does not vanish into the background.
        for (int slot = 18; slot <= 26; slot++) {
            inventory.setItem(slot, items.pane(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ", List.of()));
        }

        inventory.setItem(SLOT_MENU_INFO, items.builtInButton(MachineIcon.MENU_HEADER,
                items.name("Меню игрока"), items.hintLore(
                        List.of("Окно, в котором игрок открывает кейс",
                                "Сверху настройки окна, на синей полосе предметы"),
                        List.of("Размер", (definition.guiLayout().size() / 9) + " рядов"))));
        inventory.setItem(SLOT_GUI_SIZE, size(definition));
        inventory.setItem(SLOT_GUI_TITLE, title(definition));
        inventory.setItem(SLOT_LAYOUT, items.configuredButton(
                "machine.menu.layout", MachineIcon.MENU_LAYOUT,
                items.name("Раскладка"), items.hintLore(
                        List.of("Назначение роли для каждого слота"),
                        List.of("Слотов", String.valueOf(definition.guiLayout().size())),
                        Hint.left("открыть редактор")), definition));

        inventory.setItem(SLOT_OPEN_ITEM, highlighted(items.copyItemButton(
                "machine.menu.button", definition.openButton(), Material.CHEST, definition,
                "&#65D1FCКнопка кейса", "Предмет, по которому игрок открывает кейс.")));
        inventory.setItem(SLOT_DECOR_ITEM, highlighted(items.copyItemButton(
                "machine.menu.decor-item", definition.guiLayout().decorItem(),
                Material.GRAY_STAINED_GLASS_PANE, definition,
                "&#65D1FCДекор меню", "Предмет, которым заполняются декоративные слоты.")));
        inventory.setItem(SLOT_EMPTY_HISTORY_ITEM, highlighted(items.copyItemButton(
                "machine.menu.empty-history", definition.guiLayout().emptyHistoryItem(),
                Material.BARRIER, definition,
                "&#65D1FCПустая история",
                "Предмет, который отображается в свободных слотах истории.")));

        inventory.setItem(SLOT_CONTENT_TOGGLE, contentToggle(definition));
        inventory.setItem(SLOT_BACK, items.backButton(definition));
    }

    private static ItemStack highlighted(ItemStack item) {
        MachineItemFactory.addGlow(item);
        return item;
    }

    private ItemStack size(CaseDefinition definition) {
        int slots = definition.guiLayout().size();
        int rows = slots / 9;
        return items.configuredButton("machine.menu.size", MachineIcon.MENU_SIZE,
                items.name("Размер окна"), items.hintLore(
                        List.of("Сколько рядов в меню кейса"),
                        List.of("Размер", rows + " рядов, " + slots + " слотов"),
                        Hint.left("+1 ряд"), Hint.right("-1 ряд"),
                        Hint.shiftLeft("+2 ряда"), Hint.shiftRight("-2 ряда")), definition,
                "value", String.valueOf(slots),
                "rows", String.valueOf(rows));
    }

    private ItemStack title(CaseDefinition definition) {
        return items.configuredButton("machine.menu.title", MachineIcon.MENU_TITLE,
                items.name("Заголовок"), items.hintLore(
                        List.of("Название окна, которое видит игрок"),
                        List.of("Текст", definition.guiTitle()),
                        Hint.left("изменить")), definition,
                "value", definition.guiTitle());
    }

    private ItemStack contentToggle(CaseDefinition definition) {
        boolean featureEnabled = caseManager.getPlugin().previewEnabled();
        boolean enabled = featureEnabled && definition.guiLayout().previewEnabled();
        List<String> description = featureEnabled
                ? List.of("Игрок видит список наград перед открытием")
                : List.of("Игрок видит список наград перед открытием",
                "&cСначала включите «Каталог содержимого» в разделе «Дополнительно»");
        ItemStack item = items.configuredButton("machine.menu.content-toggle", MachineIcon.MENU_CONTENT,
                items.name("Каталог наград"), items.hintLore(
                        description,
                        List.of("Состояние", enabled ? "&aВключено" : "&cВыключено"),
                        featureEnabled
                                ? new Hint[]{Hint.left(enabled ? "выключить" : "включить")}
                                : new Hint[0]), definition,
                "status", enabled ? "&aвключено" : "&cвыключено",
                "action", enabled ? "выключить" : "включить");
        if (enabled) MachineItemFactory.addGlow(item);
        return item;
    }

}
