package ru.privatenull.pncases.gui.machine;

import ru.privatenull.pncases.gui.GuiSession;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.gift.GiftLimits;
import ru.privatenull.pncases.gift.GiftLimits.Limit;
import ru.privatenull.pncases.gift.GiftLimits.Rule;
import ru.privatenull.pncases.gui.GuiDesign;
import ru.privatenull.pncases.gui.machine.MachineItemFactory.Hint;
import ru.privatenull.pncases.support.text.ColorUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Gift limits for keys ("Подарки ключей"). They belong to the whole plugin, not to a case, so the screen is
 * opened by the command {@code /pncases giftlimit} and is not part of the case Machine. The first window lists the default limit and one card per permission
 * rule; the second one edits a single limit: keys per gift and keys per day. Every change is saved to the main
 * config at once, like the switches of "Дополнительно".
 */
final class MachineGiftScreen {

    static final int SLOT_INFO = 4;
    static final int SLOT_DEFAULT = 13;
    static final List<Integer> RULE_SLOTS = List.of(19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34);
    static final int SLOT_ADD_RULE = 40;
    static final int SLOT_PREVIOUS = 48;
    static final int SLOT_BACK = 49;
    static final int SLOT_NEXT = 50;

    static final int SLOT_PER_GIFT = 20;
    static final int SLOT_PER_DAY = 24;
    static final int SLOT_DELETE_RULE = 40;

    /** Numbers a limit gets back when it is switched from "no limit" with the middle click. */
    private static final int RESTORED_PER_GIFT = 10;
    private static final int RESTORED_PER_DAY = 30;
    private static final int SHIFT_STEP = 10;

    private final CaseManager caseManager;
    private final MachineItemFactory items;
    private final MachineAnvilInput anvil;

    MachineGiftScreen(CaseManager caseManager, MachineItemFactory items) {
        this.caseManager = caseManager;
        this.items = items;
        this.anvil = new MachineAnvilInput(caseManager.getPlugin());
    }

    private GiftLimits limits() {
        return caseManager.getPlugin().getGiftLimits();
    }

    void open(Player player, int requestedPage) {
        List<Rule> rules = limits().rules();
        int pages = Math.max(1, (rules.size() + RULE_SLOTS.size() - 1) / RULE_SLOTS.size());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        if (MachineGuiSupport.refreshIfOpen(caseManager, player, GuiSession.Screen.MACHINE_GIFTS, "", page, -1,
                inventory -> fillOverview(inventory, page, pages))) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(GuiSession.machineGifts(page), 54,
                ColorUtil.colorize("&0Подарки ключей"));
        fillOverview(inventory, page, pages);
        caseManager.getPlugin().openGui(player, inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.22f, 1.15f);
    }

    void openDefault(Player player) {
        openEditor(player, GuiSession.Screen.MACHINE_GIFT_DEFAULT, -1, null);
    }

    void openRule(Player player, int index) {
        List<Rule> rules = limits().rules();
        if (index < 0 || index >= rules.size()) {
            open(player, 0);
            return;
        }
        openEditor(player, GuiSession.Screen.MACHINE_GIFT_RULE, index, rules.get(index));
    }

    private void openEditor(Player player, GuiSession.Screen screen, int index, Rule rule) {
        if (MachineGuiSupport.refreshIfOpen(caseManager, player, screen, "", -1, index,
                inventory -> fillEditor(inventory, rule))) {
            return;
        }
        GuiSession session = rule == null ? GuiSession.machineGiftDefault() : GuiSession.machineGiftRule(index);
        Inventory inventory = Bukkit.createInventory(session, 54, ColorUtil.colorize(
                rule == null ? "&0Подарки | По умолчанию" : "&0Подарки | " + rule.id()));
        fillEditor(inventory, rule);
        caseManager.getPlugin().openGui(player, inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    // ---------------------------------------------------------------------- the list of rules

    private void fillOverview(Inventory inventory, int page, int pages) {
        GiftLimits limits = limits();
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));

        inventory.setItem(SLOT_INFO, items.builtInButton(GuiDesign.INFO, items.name("Как это работает"), items.hintLore(
                List.of("Игрок может дарить ключи, пока не превысил свой лимит",
                        "Если подходят несколько прав, берётся самый щедрый лимит",
                        "«Без ограничений» значит, что лимита нет"),
                List.of())));

        inventory.setItem(SLOT_DEFAULT, items.builtInButton(MachineIcon.REWARD_VALUE,
                items.name("По умолчанию"), items.hintLore(
                        List.of("Действует для всех, кому не подошло ни одно правило"),
                        limitParameters(limits.defaultLimit()),
                        Hint.left("настроить"))));

        List<Rule> rules = limits.rules();
        int start = page * RULE_SLOTS.size();
        for (int position = 0; position < RULE_SLOTS.size(); position++) {
            int index = start + position;
            if (index >= rules.size()) break;
            inventory.setItem(RULE_SLOTS.get(position), ruleCard(rules.get(index)));
        }
        if (rules.isEmpty()) {
            inventory.setItem(22, items.builtInButton(GuiDesign.INFO, items.name("Правил пока нет"), items.hintLore(
                    List.of("Добавьте право, например группу donate, и для неё будет свой лимит"),
                    List.of())));
        }

        inventory.setItem(SLOT_ADD_RULE, items.builtInButton(MachineIcon.REWARDS_ADD_PERMISSION,
                items.name("Новое правило"), items.hintLore(
                        List.of("Лимит для игроков с нужным правом, например group.donate"),
                        List.of(),
                        Hint.left("ввести право"))));
        if (page > 0) {
            inventory.setItem(SLOT_PREVIOUS, items.builtInButton(MachineIcon.REWARDS_PREVIOUS, "&#65D1FCПредыдущая", List.of()));
        }
        if (page + 1 < pages) {
            inventory.setItem(SLOT_NEXT, items.builtInButton(MachineIcon.REWARDS_NEXT, "&#65D1FCСледующая", List.of()));
        }
        inventory.setItem(SLOT_BACK, items.closeButton(null));
    }

    private ItemStack ruleCard(Rule rule) {
        return items.builtInButton(MachineIcon.REWARDS_ADD_GROUP, items.name(rule.id()), items.hintLore(
                List.of("Игроки с правом &f" + rule.permission()),
                limitParameters(rule.limit()),
                Hint.left("настроить")));
    }

    // ---------------------------------------------------------------------- one limit

    private void fillEditor(Inventory inventory, Rule rule) {
        Limit limit = rule == null ? limits().defaultLimit() : rule.limit();
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));

        inventory.setItem(SLOT_INFO, rule == null
                ? items.builtInButton(MachineIcon.REWARD_VALUE, items.name("По умолчанию"), items.hintLore(
                        List.of("Действует для всех, кому не подошло ни одно правило"), List.of()))
                : items.builtInButton(MachineIcon.REWARDS_ADD_GROUP, items.name(rule.id()), items.hintLore(
                        List.of("Игроки с правом &f" + rule.permission()), List.of())));

        inventory.setItem(SLOT_PER_GIFT, items.builtInButton(GuiDesign.REWARD_ITEM,
                items.name("За один подарок"), items.hintLore(
                        List.of("Сколько ключей можно передать за один раз"),
                        List.of("Сейчас", amount(limit.perGift())),
                        Hint.left("+1"), Hint.right("-1"),
                        Hint.shiftLeft("+10"), Hint.shiftRight("-10"),
                        Hint.middle("без ограничений / вернуть"))));

        inventory.setItem(SLOT_PER_DAY, items.builtInButton(GuiDesign.THEME,
                items.name("За сутки"), items.hintLore(
                        List.of("Сколько ключей можно передать за сутки",
                                "Сутки считаются по дате сервера"),
                        List.of("Сейчас", amount(limit.perDay())),
                        Hint.left("+1"), Hint.right("-1"),
                        Hint.shiftLeft("+10"), Hint.shiftRight("-10"),
                        Hint.middle("без ограничений / вернуть"))));

        if (rule != null) {
            inventory.setItem(SLOT_DELETE_RULE, items.builtInButton(MachineIcon.REWARD_DELETE,
                    items.name("Удалить правило"), items.hintLore(
                            List.of("Игроки с этим правом снова получат лимит по умолчанию"),
                            List.of(),
                            Hint.shiftRight("удалить правило"))));
        }
        inventory.setItem(SLOT_BACK, items.backButton(null));
    }

    // ---------------------------------------------------------------------- clicks

    /** Clicks on the list of rules. */
    void handleClick(GuiSession holder, Player player, InventoryClickEvent event, int slot) {
        if (slot == SLOT_BACK) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_PREVIOUS) {
            open(player, holder.page() - 1);
            return;
        }
        if (slot == SLOT_NEXT) {
            open(player, holder.page() + 1);
            return;
        }
        if (slot == SLOT_DEFAULT) {
            openDefault(player);
            return;
        }
        if (slot == SLOT_ADD_RULE) {
            startAddRule(player, holder.page());
            return;
        }
        int position = RULE_SLOTS.indexOf(slot);
        if (position < 0) return;
        int index = holder.page() * RULE_SLOTS.size() + position;
        if (index < limits().rules().size()) openRule(player, index);
    }

    /** Clicks on the editor of the default limit or of one rule. */
    void handleLimitClick(GuiSession holder, Player player, InventoryClickEvent event, int slot) {
        if (slot == SLOT_BACK) {
            open(player, 0);
            return;
        }
        boolean rule = holder.screen() == GuiSession.Screen.MACHINE_GIFT_RULE;
        Rule target = rule ? ruleAt(holder.selection()) : null;
        if (rule && target == null) {
            open(player, 0);
            return;
        }
        Limit current = target == null ? limits().defaultLimit() : target.limit();

        if (slot == SLOT_DELETE_RULE && target != null) {
            if (!event.isShiftClick() || !event.isRightClick()) return;
            boolean removed = limits().removeRule(target.id());
            feedback(player, removed);
            open(player, 0);
            return;
        }
        Limit next;
        if (slot == SLOT_PER_GIFT) {
            next = new Limit(changed(current.perGift(), event, RESTORED_PER_GIFT), current.perDay());
        } else if (slot == SLOT_PER_DAY) {
            next = new Limit(current.perGift(), changed(current.perDay(), event, RESTORED_PER_DAY));
        } else {
            return;
        }

        boolean saved = target == null ? limits().setDefault(next) : limits().setRule(target.id(), next);
        feedback(player, saved);
        if (target == null) {
            openDefault(player);
        } else {
            openRule(player, holder.selection());
        }
    }

    private void startAddRule(Player player, int page) {
        anvil.open(player, "&0Право (group.donate)", "",
                MachineIcon.REWARDS_ADD_PERMISSION.item("Новое правило"),
                MachineGiftScreen::permissionError,
                permission -> addRule(player, permission),
                () -> open(player, page));
    }

    private void addRule(Player player, String permission) {
        String id = limits().addRule(permission);
        if (id == null) {
            player.sendMessage(ColorUtil.colorize("&#FC7165Не удалось добавить правило. Возможно, такое право уже есть."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.18f, 1.0f);
            open(player, 0);
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
        openRule(player, indexOf(id));
    }

    private static String permissionError(String value) {
        if (value.isEmpty()) return "Введите право";
        if (!GiftLimits.validPermission(value)) return "Право не может содержать пробелы";
        return null;
    }

    private Rule ruleAt(int index) {
        List<Rule> rules = limits().rules();
        return index >= 0 && index < rules.size() ? rules.get(index) : null;
    }

    private int indexOf(String id) {
        List<Rule> rules = limits().rules();
        for (int index = 0; index < rules.size(); index++) {
            if (rules.get(index).id().equals(id)) return index;
        }
        return 0;
    }

    /** The number after a click: a plain click steps by one, Shift by ten, the middle click switches "no limit". */
    private static int changed(int current, InventoryClickEvent event, int restored) {
        if (event.getClick() == ClickType.MIDDLE) return current < 0 ? restored : -1;
        int step = event.isShiftClick() ? SHIFT_STEP : 1;
        return GiftLimits.adjust(current, event.isRightClick() ? -step : step);
    }

    private void feedback(Player player, boolean saved) {
        if (saved) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.35f);
            return;
        }
        player.sendMessage(caseManager.getPlugin().getUi().get("machine-save-failed"));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.18f, 1.0f);
    }

    // ---------------------------------------------------------------------- text

    private static List<String> limitParameters(Limit limit) {
        List<String> parameters = new ArrayList<>();
        parameters.add("За один подарок");
        parameters.add(amount(limit.perGift()));
        parameters.add("За сутки");
        parameters.add(amount(limit.perDay()));
        return parameters;
    }

    private static String amount(int value) {
        return value < 0 ? "&7без ограничений" : "&f" + value;
    }
}
