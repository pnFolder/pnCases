package ru.privatenull.pncases.gui.machine;

import ru.privatenull.pncases.gui.GuiSession;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pnlibrary.text.ColorUtil;
import ru.privatenull.pncases.gui.GuiDesign;

import java.util.List;

import static ru.privatenull.pncases.gui.machine.MachineGuiSupport.*;

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
                ColorUtil.colorize("&0Настройки | " + caseName(definition))
        );
        fill(inventory, definition);
        caseManager.getPlugin().openGui(player, inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.22f, 1.15f);
    }

    void fill(Inventory inventory, CaseDefinition definition) {
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inventory.setItem(4, items.builtInButton(MachineIcon.MAIN_OVERVIEW,
                items.name("Сводка кейса"),
                items.lore(
                        List.of("Краткая информация о выбранном кейсе"),
                        List.of(
                                "Название", caseName(definition),
                                "ID", definition.name(),
                                "Награды", String.valueOf(definition.rewards().size()),
                                "Установленные блоки", String.valueOf(definition.blockLocations().size()),
                                "Анимация", state.currentAnimationLabel(definition)
                        ), null, null)));

        inventory.setItem(SLOT_MAIN_ANIMATION, items.sectionButton(
                "machine.main.animation", MachineIcon.MAIN_ANIMATION,
                items.name("Анимация открытия"), items.lore(
                        "Сценарий, эффекты и показ выпавшей награды",
                        "Анимация", state.currentAnimationLabel(definition),
                        "Изменить", null), definition));
        inventory.setItem(SLOT_MAIN_MENU, items.sectionButton(
                "machine.main.menu", MachineIcon.MAIN_MENU,
                items.name("Меню кейса"), items.lore(
                        "Кнопки, заголовок и расположение слотов",
                        "Размер меню", (definition.guiLayout().size() / 9) + " рядов, " + definition.guiLayout().size() + " слота",
                        "Изменить", null), definition));
        inventory.setItem(SLOT_MAIN_REWARDS, items.sectionButton(
                "machine.main.rewards", MachineIcon.MAIN_REWARDS,
                items.name("Содержимое кейса"), items.lore(
                        "Награды, которые могут выпасть игроку",
                        "Добавлено наград", String.valueOf(definition.rewards().size()),
                        "Изменить", null), definition));
        inventory.setItem(SLOT_MAIN_FEATURES, items.sectionButton(
                "machine.main.features", MachineIcon.MAIN_FEATURES,
                items.name("Дополнительные функции"), items.lore(
                        List.of("Редкости наград и предпросмотр содержимого"),
                        List.of(
                                "Редкости", status(caseManager.getPlugin().rarityEnabled(), "включены", "выключены"),
                                "Предпросмотр", status(caseManager.getPlugin().previewEnabled(), "включен", "выключен")
                        ), "Изменить", null), definition));
        inventory.setItem(SLOT_MAIN_HOLOGRAM, items.sectionButton(
                "machine.main.hologram", MachineIcon.MAIN_HOLOGRAM,
                items.name("Текст над кейсом"), items.lore(
                        List.of("Голограмма с названием и подсказками для игроков"),
                        List.of(
                                "Состояние", status(state.hologramEnabled(definition), "включена", "выключена"),
                                "Высота", String.valueOf(state.hologramHeight(definition))
                        ), "Изменить", null), definition));
        inventory.setItem(SLOT_MAIN_PARTICLES, items.sectionButton(
                "machine.main.showcase", MachineIcon.MAIN_SHOWCASE,
                items.name("Витрина"), items.lore(
                        List.of("Постоянные частицы, которые привлекают внимание к кейсу"),
                        List.of(
                                "Состояние", status(definition.idleParticles().enabled(), "включена", "выключена"),
                                "Тема", definition.idleParticles().theme().displayName()
                        ), "Изменить", null), definition));
        inventory.setItem(SLOT_MAIN_PURCHASE, items.sectionButton(
                "machine.main.purchase", MachineIcon.MAIN_PURCHASE,
                items.name("Открытие за опыт"), items.lore(
                        List.of("Открытие кейса за уровни опыта, если нет ключа"),
                        List.of(
                                "Состояние", status(state.xpBuyEnabled(definition), "включено", "выключено"),
                                "Стоимость", Math.max(0, definition.buyKeyWithXpLevels()) + " уровней"
                        ), "Изменить", null), definition));
        inventory.setItem(SLOT_MAIN_CLOSE, items.closeButton(definition));
    }

    /** Uses the case display name directly; virtual keys have no separate config entry. */
    private String caseName(CaseDefinition definition) {
        return definition.displayName();
    }

    private static String status(boolean enabled, String enabledText, String disabledText) {
        return enabled ? "&a" + enabledText : "&c" + disabledText;
    }

}
