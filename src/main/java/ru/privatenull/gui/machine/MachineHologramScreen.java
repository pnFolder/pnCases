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

import java.util.ArrayList;
import java.util.List;

import static ru.privatenull.gui.machine.MachineGuiSupport.*;

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
                        "machine.titles.hologram", "&0Настройка голограммы", items.replacements(definition))
        );
        fill(inventory, definition);
        caseManager.getPlugin().getGuiOpenAnimations().open(player, inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    private void fill(Inventory inventory, CaseDefinition definition) {
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inventory.setItem(4, items.configuredButton(
                "machine.hologram.header", GuiDesign.HOLOGRAM,
                "&x&4&2&9&F&9&1Раздел: Голограмма",
                List.of("", "&#9EFC65 «Описание»",
                        " &7- &fНастройте текст над блоком кейса,",
                        " &7- &fего видимость и высоту.", ""), definition));
        inventory.setItem(SLOT_HOLOGRAM_TOGGLE, toggle(definition));
        inventory.setItem(SLOT_HOLOGRAM_HEIGHT, height(definition));
        inventory.setItem(SLOT_HOLOGRAM_LINES, lines(definition));
        inventory.setItem(SLOT_BACK, items.backButton(definition));
    }

    private ItemStack toggle(CaseDefinition definition) {
        boolean enabled = state.hologramEnabled(definition);
        return items.configuredButton("machine.hologram.toggle",
                GuiDesign.TOGGLE,
                "&x&4&2&9&F&9&1Голограмма", List.of(
                        "", "&#65D1FC «Сейчас»",
                        " &7- &fСтатус: " + (enabled ? "&aвключена" : "&cвыключена"),
                        " &7- &fВысота: &#FCA865" + state.hologramHeight(definition),
                        "", "&#FCA965 «Управление»",
                        " &7- &fЛКМ — " + (enabled ? "выключить" : "включить"), ""), definition,
                "status", enabled ? "&aвключена" : "&cвыключена",
                "action", enabled ? "выключить" : "включить");
    }

    private ItemStack height(CaseDefinition definition) {
        return items.configuredButton("machine.hologram.height", GuiDesign.SIZE,
                "&x&4&2&9&F&9&1Высота голограммы", List.of(
                        "", "&#65D1FC «Сейчас»",
                        " &7- &fВысота: &#FCA865" + state.hologramHeight(definition),
                        "", "&#FCA965 «Управление»",
                        " &7- &fЛКМ / ПКМ — изменить на 0.1",
                        " &7- &fShift + ЛКМ / ПКМ — изменить на 1.0", ""), definition,
                "value", String.valueOf(state.hologramHeight(definition)));
    }

    private ItemStack lines(CaseDefinition definition) {
        List<String> configured = state.hologramLines(definition);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("&#65D1FC «Сейчас»");
        if (configured.isEmpty()) {
            lore.add(" &7- &8Строк нет");
        } else {
            configured.forEach(line -> lore.add(" &7- &f" + items.format(definition, line)));
        }
        lore.add("");
        lore.add("&#FCA965 «Управление»");
        lore.add(" &7- &fЛКМ — изменить текст через чат");
        lore.add(" &7- &fРазделитель строк: &b|");
        lore.add("");
        String visible = configured.isEmpty()
                ? "&8строк нет"
                : String.join("|", configured.stream()
                .map(line -> items.format(definition, line))
                .toList());
        return items.configuredButton("machine.hologram.lines", GuiDesign.TITLE,
                "&x&4&2&9&F&9&1Текст голограммы", lore, definition, "lines", visible);
    }
}
