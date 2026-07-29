package ru.privatenull.gui.machine;

import ru.privatenull.gui.GuiSession;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.pnlibrary.text.ColorUtil;
import ru.privatenull.gui.GuiDesign;

import java.util.List;

import static ru.privatenull.gui.machine.MachineGuiSupport.*;

final class MachineMainScreen {

    private final CaseManager caseManager;
    private final MachineGuiSupport state;
    private final MachineItemFactory items;

    MachineMainScreen(CaseManager caseManager, MachineGuiSupport state, MachineItemFactory items) {
        this.caseManager = caseManager;
        this.state = state;
        this.items = items;
    }

    void open(Player player, String caseName) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) {
            player.sendMessage(caseManager.getPlugin().getUi().get(
                    "machine-case-not-found", "case", caseName));
            return;
        }
        if (MachineGuiSupport.refreshIfOpen(
                caseManager, player, GuiSession.Screen.MACHINE_MAIN, definition.name(),
                inventory -> fill(inventory, definition))) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(
                GuiSession.machine(GuiSession.Screen.MACHINE_MAIN, definition.name()),
                54,
                ColorUtil.colorize("&0Настройка кейса • " + caseName(definition))
        );
        fill(inventory, definition);
        caseManager.getPlugin().getGuiOpenAnimations().open(player, inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.22f, 1.15f);
    }

    void fill(Inventory inventory, CaseDefinition definition) {
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inventory.setItem(4, items.builtInButton(GuiDesign.INFO,
                "&x&4&2&9&F&9&1Информация о кейсе",
                List.of(
                        "",
                        "&#9EFC65 «Кейс»",
                        " &7- &fНазвание: &#FCA865" + caseName(definition),
                        " &7- &fID: &#FCA865" + definition.name(),
                        " &7- &fКаждый раздел изменяет только этот кейс.",
                        ""
                )));

        inventory.setItem(SLOT_MAIN_ANIMATION, items.sectionButton(
                "machine.main.animation", GuiDesign.ANIMATION,
                "&x&4&2&9&F&9&1Анимация", List.of(
                        "&#9EFC65 «Описание»",
                        " &7- &fСценарий, который проигрывается",
                        " &7- &fво время открытия кейса.",
                        "",
                        "&#65D1FC «Сейчас»",
                        " &7- &fРежим: " + state.currentAnimationLabel(definition),
                        "",
                        "&#FCA965 «Управление»",
                        " &7- &fЛКМ — настроить анимацию"), definition));
        inventory.setItem(SLOT_MAIN_MENU, items.sectionButton(
                "machine.main.menu", GuiDesign.MENU,
                "&x&4&2&9&F&9&1Меню кейса", List.of(
                        "&#9EFC65 «Описание»",
                        " &7- &fНазвание, размер, раскладка",
                        " &7- &fи предметы интерфейса.",
                        "",
                        "&#65D1FC «Сейчас»",
                        " &7- &fРазмер: &#FCA865" + definition.guiLayout().size() + " слотов",
                        "",
                        "&#FCA965 «Управление»",
                        " &7- &fЛКМ — настроить меню"), definition));
        inventory.setItem(SLOT_MAIN_HOLOGRAM, items.sectionButton(
                "machine.main.hologram", GuiDesign.HOLOGRAM,
                "&x&4&2&9&F&9&1Голограмма", List.of(
                        "&#9EFC65 «Описание»",
                        " &7- &fТекст, отображаемый над кейсом.",
                        "",
                        "&#65D1FC «Сейчас»",
                        " &7- &fСтатус: " + status(state.hologramEnabled(definition), "включена", "выключена"),
                        " &7- &fВысота: &#FCA865" + state.hologramHeight(definition),
                        "",
                        "&#FCA965 «Управление»",
                        " &7- &fЛКМ — настроить голограмму"), definition));
        inventory.setItem(SLOT_MAIN_PARTICLES, items.sectionButton(
                "machine.main.showcase", GuiDesign.SHOWCASE,
                "&x&4&2&9&F&9&1Витрина кейса", List.of(
                        "&#9EFC65 «Описание»",
                        " &7- &fАнимированная сцена над свободным кейсом.",
                        " &7- &fПри открытии витрина скрывается.",
                        "",
                        "&#65D1FC «Сейчас»",
                        " &7- &fСтатус: " + status(definition.idleParticles().enabled(), "включена", "выключена"),
                        " &7- &fТема: &#FCA865" + definition.idleParticles().theme().displayName(),
                        "",
                        "&#FCA965 «Управление»",
                        " &7- &fЛКМ — настроить витрину"), definition));
        inventory.setItem(SLOT_MAIN_PURCHASE, items.sectionButton(
                "machine.main.purchase", GuiDesign.PURCHASE,
                "&x&4&2&9&F&9&1Покупка за опыт", List.of(
                        "&#9EFC65 «Описание»",
                        " &7- &fПокупка открытия за уровни опыта.",
                        "",
                        "&#65D1FC «Сейчас»",
                        " &7- &fСтатус: " + status(state.xpBuyEnabled(definition), "включена", "выключена"),
                        " &7- &fЦена: &#FCA865" + Math.max(0, definition.buyKeyWithXpLevels()) + " уровней",
                        "",
                        "&#FCA965 «Управление»",
                        " &7- &fЛКМ — настроить покупку"), definition));
        inventory.setItem(SLOT_MAIN_CLOSE, items.closeButton(definition));
    }

    /**
     * A case is named for administrators by the key that opens it.  This keeps
     * the Machine screen in sync with the name configured under keys.<id>.name.
     */
    private String caseName(CaseDefinition definition) {
        return definition.displayName();
    }

    private static String status(boolean enabled, String enabledText, String disabledText) {
        return enabled ? "&a" + enabledText : "&c" + disabledText;
    }

}
