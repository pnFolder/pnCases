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
import ru.privatenull.cases.model.IdleParticleSettings;
import ru.privatenull.gui.GuiDesign;

import java.util.List;

import static ru.privatenull.gui.machine.MachineGuiSupport.*;

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
                        "machine.titles.showcase", "&0Витрина кейса", items.replacements(definition))
        );
        fill(inventory, definition);
        caseManager.getPlugin().openGui(player, inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    private void fill(Inventory inventory, CaseDefinition definition) {
        IdleParticleSettings settings = definition.idleParticles();
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inventory.setItem(4, items.configuredButton(
                "machine.showcase.header", GuiDesign.SHOWCASE,
                "&x&4&2&9&F&9&1Раздел: Витрина кейса", List.of(
                        "", "&#9EFC65 «Описание»",
                        " &7- &fНастройте анимированную сцену,",
                        " &7- &fкоторая отображается над кейсом.",
                        " &7- &fВо время открытия витрина скрывается.", ""), definition));
        inventory.setItem(SLOT_PARTICLES_TOGGLE, toggle(definition, settings));
        inventory.setItem(SLOT_PARTICLES_THEME, theme(definition, settings));
        inventory.setItem(SLOT_BACK, items.backButton(definition));
    }

    private ItemStack toggle(CaseDefinition definition, IdleParticleSettings settings) {
        return items.configuredButton("machine.showcase.toggle",
                GuiDesign.SHOWCASE_TOGGLE,
                "&x&4&2&9&F&9&1Витрина", List.of(
                        "", "&#65D1FC «Сейчас»",
                        " &7- &fСтатус: " + (settings.enabled() ? "&aвключена" : "&cвыключена"),
                        "", "&#9EFC65 «Результат»",
                        " &7- &fПри выключении сцена полностью исчезнет.",
                        "", "&#FCA965 «Управление»",
                        " &7- &fЛКМ — " + (settings.enabled() ? "выключить" : "включить"), ""), definition,
                "status", settings.enabled() ? "&aвключена" : "&cвыключена",
                "action", settings.enabled() ? "выключить" : "включить");
    }

    private ItemStack theme(CaseDefinition definition, IdleParticleSettings settings) {
        List<String> lore = new java.util.ArrayList<>();
        lore.add("");
        lore.add("&#65D1FC «Текущая сцена»");
        lore.add(" &7- &fТема: &#FCA865" + settings.theme().displayName());
        lore.add("");
        lore.add("&#9EFC65 «Как выглядит»");
        for (String line : settings.theme().description()) {
            lore.add(" &7- &f" + line);
        }
        lore.add("");
        lore.add("&#FCA965 «Управление»");
        lore.add(" &7- &fЛКМ — следующая тема");
        lore.add(" &7- &fПКМ — предыдущая тема");
        lore.add("");
        return items.configuredButton("machine.showcase.theme", GuiDesign.showcaseTheme(settings.theme()),
                "&x&4&2&9&F&9&1Тема витрины", lore, definition,
                "value", settings.theme().displayName());
    }

}
