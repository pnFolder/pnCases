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

/** Open-for-experience settings. Reached from "Additional"; the back button returns there. */
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
                        "machine.titles.purchase", "&0Открытие за опыт", items.replacements(definition))
        );
        fill(inventory, definition);
        caseManager.getPlugin().openGui(player, inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    private void fill(Inventory inventory, CaseDefinition definition) {
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inventory.setItem(SLOT_XP_BUY, toggle(definition));
        inventory.setItem(SLOT_XP_LEVELS, levels(definition));
        inventory.setItem(SLOT_BACK, items.backButton(definition));
    }

    private ItemStack toggle(CaseDefinition definition) {
        boolean enabled = state.xpBuyEnabled(definition);
        int levels = Math.max(0, definition.buyKeyWithXpLevels());
        return items.configuredButton("machine.purchase.toggle",
                enabled ? MachineIcon.PURCHASE_TOGGLE_ON : MachineIcon.PURCHASE_TOGGLE_OFF,
                items.name("Доступ"), items.hintLore(
                        List.of("Разрешает покупать открытие за опыт"),
                        List.of("Состояние", enabled ? "&aВключено" : "&cВыключено"),
                        Hint.left(enabled ? "выключить" : "включить")), definition,
                "status", enabled ? "&aвключена" : "&cвыключена",
                "action", enabled ? "выключить" : "включить",
                "levels", String.valueOf(levels));
    }

    private ItemStack levels(CaseDefinition definition) {
        boolean enabled = state.xpBuyEnabled(definition);
        int levels = Math.max(0, definition.buyKeyWithXpLevels());
        return items.configuredButton("machine.purchase.levels", MachineIcon.PURCHASE_LEVELS,
                items.name("Стоимость"), items.hintLore(
                        List.of("Цена одного открытия"),
                        List.of("Уровни", String.valueOf(levels)),
                        Hint.left("+1 уровень"), Hint.right("-1 уровень"),
                        Hint.shiftLeft("+5 уровней"), Hint.shiftRight("-5 уровней")), definition,
                "levels", String.valueOf(levels),
                "status", enabled ? "&aвключена" : "&cвыключена");
    }
}
