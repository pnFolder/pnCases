package ru.privatenull.pncases.gui.machine;

import ru.privatenull.pncases.gui.GuiDesign;
import ru.privatenull.pncases.gui.GuiSession;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pncases.gui.machine.MachineItemFactory.Hint;
import ru.privatenull.pncases.support.text.ColorUtil;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static ru.privatenull.pncases.gui.machine.MachineGuiSupport.*;

/**
 * Home screen of the Machine. The first row holds the four things an administrator edits (rewards,
 * animation, player menu, additional settings); the second row holds three tools that act on the case.
 * Switches such as hologram or showcase live in "Additional", not here.
 */
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
                ColorUtil.colorize("&0Настройки | " + definition.displayName())
        );
        fill(inventory, definition);
        caseManager.getPlugin().openGui(player, inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.22f, 1.15f);
    }

    void fill(Inventory inventory, CaseDefinition definition) {
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));

        int rewards = definition.rewards().size();
        inventory.setItem(SLOT_MAIN_REWARDS, items.sectionButton(
                "machine.main.rewards", MachineIcon.MAIN_REWARDS,
                items.name("Награды"), items.hintLore(
                        List.of("Что может выпасть игроку, шансы и редкости"),
                        List.of("Наград в кейсе", rewards > 0 ? String.valueOf(rewards) : "&cпока нет"),
                        Hint.left("открыть")), definition));
        inventory.setItem(SLOT_MAIN_ANIMATION, items.sectionButton(
                "machine.main.animation", MachineIcon.MAIN_ANIMATION,
                items.name("Анимация открытия"), items.hintLore(
                        List.of("Сценарий, который видит игрок при открытии"),
                        List.of("Сейчас", state.currentAnimationLabel(definition)),
                        Hint.left("выбрать")), definition));
        inventory.setItem(SLOT_MAIN_MENU, items.sectionButton(
                "machine.main.menu", MachineIcon.MAIN_MENU,
                items.name("Меню игрока"), items.hintLore(
                        List.of("Окно кейса: размер, заголовок, кнопки и раскладка"),
                        List.of("Размер", (definition.guiLayout().size() / 9) + " рядов"),
                        Hint.left("настроить")), definition));
        inventory.setItem(SLOT_MAIN_FEATURES, items.sectionButton(
                "machine.main.features", MachineIcon.MAIN_FEATURES,
                items.name("Дополнительно"), items.hintLore(
                        List.of("Голограмма, витрина, открытие за опыт, редкости и предпросмотр"),
                        List.of(
                                "Голограмма", onOff(state.hologramEnabled(definition)),
                                "Витрина", onOff(definition.idleParticles().enabled()),
                                "Открытие за опыт", onOff(state.xpBuyEnabled(definition))),
                        Hint.left("открыть")), definition));

        inventory.setItem(SLOT_MAIN_CHECK, checkButton(definition));
        inventory.setItem(SLOT_MAIN_PREVIEW, items.builtInButton(GuiDesign.PREVIEW,
                items.name("Посмотреть как игрок"), items.hintLore(
                        List.of("Откроет меню кейса так, как его видят игроки"),
                        List.of(),
                        Hint.left("открыть меню"))));
        int undoDepth = caseManager.undoDepth(definition.name());
        inventory.setItem(SLOT_MAIN_UNDO, items.builtInButton(GuiDesign.TOGGLE,
                items.name("Откатить правку"), items.hintLore(
                        List.of("Возвращает кейс к состоянию до последнего изменения"),
                        List.of("Доступно откатов", undoDepth == 0 ? "&7нет изменений" : String.valueOf(undoDepth)),
                        undoDepth == 0 ? new Hint[0] : new Hint[]{Hint.left("откатить")})));

        inventory.setItem(SLOT_MAIN_BIND, bindButton(definition));
        inventory.setItem(SLOT_MAIN_KEY, keyButton(definition));
        inventory.setItem(SLOT_MAIN_CASES, items.builtInButton(GuiDesign.CONTENT,
                items.name("Все кейсы"), items.hintLore(
                        List.of("Список кейсов: открыть другой или создать новый"),
                        List.of(),
                        Hint.left("открыть список"))));
        inventory.setItem(SLOT_MAIN_CLOSE, items.closeButton(definition));
    }

    List<CaseHealthCheck.Issue> healthIssues(CaseDefinition definition) {
        Set<Reward.Type> types = EnumSet.noneOf(Reward.Type.class);
        for (Reward reward : definition.rewards()) types.add(reward.type());
        var economies = caseManager.getPlugin().getEconomies();
        return CaseHealthCheck.evaluate(new CaseHealthCheck.Input(
                definition.rewards().size(),
                definition.blockLocations().size(),
                types,
                economies != null && economies.vault().available(),
                economies != null && economies.playerPoints().available(),
                Bukkit.getPluginManager().isPluginEnabled("LuckPerms")));
    }

    /**
     * One button for both checks: what stops the case from working (left click) and how often each reward falls
     * out (right click, a simulation that gives nothing away).
     */
    private ItemStack checkButton(CaseDefinition definition) {
        List<CaseHealthCheck.Issue> issues = healthIssues(definition);
        boolean canSimulate = RewardSimulationScreen.canSimulate(definition.rewards().size());
        List<String> lines = new ArrayList<>();
        if (issues.isEmpty()) {
            lines.add("&#A7F3D0✔ Всё готово: кейс привязан к блоку, награды выдаются");
        }
        for (CaseHealthCheck.Issue issue : issues) {
            lines.add(issue.severity() == CaseHealthCheck.Severity.ERROR
                    ? "&#FC7165✖ " + issue.text()
                    : "&#FCA865⚠ " + issue.text());
        }
        lines.add("");
        lines.add(canSimulate
                ? "Шансы проверяются на 100 000 виртуальных открытий, награды не выдаются"
                : "Чтобы проверить шансы, добавьте в кейс минимум две награды");
        Hint[] hints = canSimulate
                ? new Hint[]{Hint.left("показать проверку в чате"), Hint.right("проверить шансы")}
                : new Hint[]{Hint.left("показать проверку в чате")};
        return items.builtInButton(issues.isEmpty() ? Material.EMERALD : Material.REDSTONE,
                items.name("Проверка"), items.hintLore(lines, List.of(), hints));
    }

    void showCheck(Player player, CaseDefinition definition) {
        List<CaseHealthCheck.Issue> issues = healthIssues(definition);
        player.sendMessage(ColorUtil.colorize("&#65D1FCПроверка кейса &f" + definition.name() + "&#65D1FC:"));
        if (issues.isEmpty()) {
            player.sendMessage(ColorUtil.colorize(" &#A7F3D0✔ Всё готово: кейс привязан, награды выдаются."));
        }
        for (CaseHealthCheck.Issue issue : issues) {
            player.sendMessage(ColorUtil.colorize(issue.severity() == CaseHealthCheck.Severity.ERROR
                    ? " &#FC7165✖ " + issue.text()
                    : " &#FCA865⚠ " + issue.text()));
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.3f);
    }

    private ItemStack bindButton(CaseDefinition definition) {
        List<Location> blocks = definition.blockLocations();
        List<String> parameters = new ArrayList<>();
        parameters.add("Привязано блоков");
        parameters.add(blocks.isEmpty() ? "&cни одного" : String.valueOf(blocks.size()));
        for (int index = 0; index < Math.min(3, blocks.size()); index++) {
            Location block = blocks.get(index);
            parameters.add(index == 0 ? "Блоки" : " ");
            parameters.add(block.getWorld() == null ? "?" : block.getWorld().getName()
                    + " " + block.getBlockX() + " " + block.getBlockY() + " " + block.getBlockZ());
        }
        return items.builtInButton(GuiDesign.LAYOUT, items.name("Блок кейса"), items.hintLore(
                List.of("Привяжите кейс к блоку, на который смотрите (не дальше 5 блоков от вас)"),
                parameters,
                Hint.left("привязать блок перед вами"), Hint.shiftRight("отвязать от всех блоков")));
    }

    private ItemStack keyButton(CaseDefinition definition) {
        return items.builtInButton(GuiDesign.REWARD_PRIVILEGE, items.name("Ключ для проверки"), items.hintLore(
                List.of("Выдайте себе ключи, чтобы проверить открытие кейса",
                        "Другим игрокам: /pncases givekey <игрок> " + definition.name() + " <кол-во>"),
                List.of(),
                Hint.left("выдать 1 ключ"), Hint.shiftLeft("выдать 10 ключей")));
    }

    /** Binds the case to the block the player is looking at, or unbinds it from all blocks. */
    void bind(Player player, CaseDefinition definition, boolean unbind) {
        String name = definition.name();
        if (unbind) {
            switch (caseManager.unbindCaseFromBlock(name)) {
                case REMOVED -> player.sendMessage(ColorUtil.colorize("&#A7F3D0Кейс отвязан от всех блоков."));
                case NOT_BOUND -> player.sendMessage(ColorUtil.colorize("&#A6B0BAКейс и так не привязан к блокам."));
                default -> player.sendMessage(ColorUtil.colorize("&#FC7165Не удалось отвязать кейс. Подробности в консоли."));
            }
            open(player, name);
            return;
        }
        Block target = player.getTargetBlockExact(5);
        if (target == null) {
            player.sendMessage(ColorUtil.colorize("&#FC7165Посмотрите на блок на расстоянии до 5 блоков и нажмите снова."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.18f, 1.0f);
            return;
        }
        String message = switch (caseManager.bindCaseToBlock(name, target)) {
            case BOUND -> "&#A7F3D0Кейс привязан к блоку " + target.getX() + " " + target.getY() + " " + target.getZ() + ".";
            case ALREADY_BOUND -> "&#A6B0BAЭтот блок уже принадлежит кейсу.";
            case BLOCK_OCCUPIED -> "&#FC7165Этот блок уже занят другим кейсом.";
            case NOT_FOUND -> "&#FC7165Кейс не найден.";
            case SAVE_FAILED -> "&#FC7165Не удалось сохранить привязку. Подробности в консоли.";
            case REFRESH_FAILED -> "&#FC7165Привязка сохранена, но кейс не обновился. Проверьте консоль.";
        };
        player.sendMessage(ColorUtil.colorize(message));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.2f);
        open(player, name);
    }

    /** Gives the clicking administrator keys of this case, for testing the opening. */
    void giveTestKeys(Player player, CaseDefinition definition, int amount) {
        caseManager.giveKey(player, definition.name(), amount);
        player.sendMessage(ColorUtil.colorize("&#A7F3D0Вы получили &f" + amount + "&#A7F3D0 ключ(ей) для кейса &f"
                + definition.name() + "&#A7F3D0."));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.2f, 1.4f);
    }

    /** Reverts the last saved change of the case and shows the result. */
    void undo(Player player, CaseDefinition definition) {
        if (caseManager.undoDepth(definition.name()) == 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.18f, 1.0f);
            return;
        }
        boolean undone = caseManager.undoLastCaseChange(definition.name());
        player.playSound(player.getLocation(),
                undone ? Sound.UI_BUTTON_CLICK : Sound.ENTITY_VILLAGER_NO, 0.18f, undone ? 0.9f : 1.0f);
        player.sendMessage(ColorUtil.colorize(undone
                ? "&#A7F3D0Последнее изменение кейса отменено."
                : "&#FC7165Не удалось откатить изменение. Подробности в консоли."));
        open(player, definition.name());
    }

    static String onOff(boolean enabled) {
        return enabled ? "&aвкл." : "&7выкл.";
    }
}
