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

import java.util.List;

import static ru.privatenull.gui.machine.MachineGuiSupport.*;

final class MachinePurchaseScreen {

    private final CaseManager caseManager;
    private final MachineGuiSupport state;
    private final MachineItemFactory items;

    MachinePurchaseScreen(CaseManager caseManager, MachineGuiSupport state, MachineItemFactory items) {
        this.caseManager = caseManager;
        this.state = state;
        this.items = items;
    }

    void open(Player player, String caseName) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) return;
        if (MachineGuiSupport.refreshIfOpen(
                caseManager, player, GuiSession.Screen.MACHINE_PURCHASE, definition.name(),
                inventory -> fill(inventory, definition))) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(
                GuiSession.machine(GuiSession.Screen.MACHINE_PURCHASE, caseName), 54,
                caseManager.getPlugin().getUi().text(
                        "machine.titles.purchase", "&0Покупка за опыт", items.replacements(definition))
        );
        fill(inventory, definition);
        caseManager.getPlugin().getGuiOpenAnimations().open(player, inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    private void fill(Inventory inventory, CaseDefinition definition) {
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inventory.setItem(4, items.configuredButton(
                "machine.purchase.header", GuiDesign.PURCHASE,
                "&x&4&2&9&F&9&1Раздел: Покупка за опыт", List.of(
                        "", "&#9EFC65 «Описание»",
                        " &7- &fРазрешает открыть кейс за опыт,",
                        " &7- &fесли у игрока недостаточно ключей.", ""), definition));
        inventory.setItem(SLOT_XP_BUY, toggle(definition));
        inventory.setItem(SLOT_XP_LEVELS, levels(definition));
        inventory.setItem(SLOT_BACK, items.backButton(definition));
    }

    private ItemStack toggle(CaseDefinition definition) {
        boolean enabled = state.xpBuyEnabled(definition);
        int levels = Math.max(0, definition.buyKeyWithXpLevels());
        return items.configuredButton("machine.purchase.toggle",
                GuiDesign.TOGGLE,
                "&x&4&2&9&F&9&1Покупка за опыт", List.of(
                        "", "&#65D1FC «Сейчас»",
                        " &7- &fСтатус: " + (enabled ? "&aвключена" : "&cвыключена"),
                        " &7- &fЦена: &#FCA865" + levels + " уровней",
                        "", "&#FCA965 «Управление»",
                        " &7- &fЛКМ — " + (enabled ? "выключить" : "включить"), ""), definition,
                "status", enabled ? "&aвключена" : "&cвыключена",
                "action", enabled ? "выключить" : "включить",
                "levels", String.valueOf(levels));
    }

    private ItemStack levels(CaseDefinition definition) {
        boolean enabled = state.xpBuyEnabled(definition);
        int levels = Math.max(0, definition.buyKeyWithXpLevels());
        return items.configuredButton("machine.purchase.levels", GuiDesign.SIZE,
                "&x&4&2&9&F&9&1Цена покупки за опыт", List.of(
                        "", "&#65D1FC «Сейчас»",
                        " &7- &fЦена: &#FCA865" + levels + " уровней",
                        " &7- &fСтатус: " + (enabled ? "&aвключена" : "&cвыключена"),
                        "", "&#FCA965 «Управление»",
                        " &7- &fЛКМ / ПКМ — изменить на 1",
                        " &7- &fShift — изменить на 5", ""), definition,
                "levels", String.valueOf(levels),
                "status", enabled ? "&aвключена" : "&cвыключена");
    }
}
