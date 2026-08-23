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
import ru.privatenull.pncases.cases.model.IdleParticleSettings;
import ru.privatenull.pncases.gui.GuiDesign;

import java.util.List;

import static ru.privatenull.pncases.gui.machine.MachineGuiSupport.*;

final class MachineShowcaseScreen {

    private final CaseManager caseManager;
    private final MachineItemFactory items;

    MachineShowcaseScreen(CaseManager caseManager, MachineItemFactory items) {
        this.caseManager = caseManager;
        this.items = items;
    }

    void open(Player player, String caseName) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) return;
        if (MachineGuiSupport.refreshIfOpen(
                caseManager, player, GuiSession.Screen.MACHINE_SHOWCASE, definition.name(),
                inventory -> fill(inventory, definition))) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(
                GuiSession.machine(GuiSession.Screen.MACHINE_SHOWCASE, caseName), 54,
                caseManager.getPlugin().getUi().text(
                        "machine.titles.showcase", "&0Атмосфера кейса", items.replacements(definition))
        );
        fill(inventory, definition);
        caseManager.getPlugin().openGui(player, inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    private void fill(Inventory inventory, CaseDefinition definition) {
        IdleParticleSettings settings = definition.idleParticles();
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inventory.setItem(4, items.configuredButton(
                "machine.showcase.header", MachineIcon.SHOWCASE_HEADER,
                items.name("Витрина"), items.lore(
                        List.of("Настройка постоянного эффекта над кейсом"),
                        List.of(), null, null), definition));
        inventory.setItem(SLOT_PARTICLES_TOGGLE, toggle(definition, settings));
        inventory.setItem(SLOT_PARTICLES_THEME, theme(definition, settings));
        inventory.setItem(SLOT_BACK, items.backButton(definition));
    }

    private ItemStack toggle(CaseDefinition definition, IdleParticleSettings settings) {
        return items.configuredButton("machine.showcase.toggle",
                settings.enabled() ? MachineIcon.SHOWCASE_TOGGLE_ON : MachineIcon.SHOWCASE_TOGGLE_OFF,
                items.name("Состояние витрины"), items.lore(
                        "Включение и отключение эффекта над кейсом",
                        "Состояние", settings.enabled() ? "&aВключена" : "&cВыключена",
                        settings.enabled() ? "Выключить" : "Включить", null), definition,
                "status", settings.enabled() ? "&aвключена" : "&cвыключена",
                "action", settings.enabled() ? "выключить" : "включить");
    }

    private ItemStack theme(CaseDefinition definition, IdleParticleSettings settings) {
        List<String> lore = items.lore(
                settings.theme().description(),
                List.of("Тема", settings.theme().displayName()),
                "Следующая", "Предыдущая");
        return items.configuredButton("machine.showcase.theme", MachineIcon.SHOWCASE_THEME,
                items.name("Тема витрины"), lore, definition,
                "value", settings.theme().displayName());
    }

}
