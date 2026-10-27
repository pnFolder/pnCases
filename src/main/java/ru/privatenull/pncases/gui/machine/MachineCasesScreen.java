package ru.privatenull.pncases.gui.machine;

import ru.privatenull.pncases.gui.GuiDesign;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.config.CaseConfigRepository;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.gui.GuiSession;
import ru.privatenull.pncases.gui.machine.MachineItemFactory.Hint;
import ru.privatenull.pncases.support.text.ColorUtil;

import java.util.ArrayList;
import java.util.List;

import static ru.privatenull.pncases.gui.machine.RewardScreenLayout.CONTENT_SLOTS;

/**
 * The first screen of the Machine: every case with its state, a button to open its settings, and a button to
 * create a new case. Nothing has to be typed except the name of a new case.
 */
final class MachineCasesScreen {

    static final int SLOT_PREVIOUS = 48;
    static final int SLOT_NEW = 49;
    static final int SLOT_NEXT = 50;
    static final int SLOT_CLOSE = 53;

    private final CaseManager caseManager;
    private final MachineItemFactory items;
    private final MachineMainScreen mainScreen;
    private final MachineAnvilInput anvil;
    private final MachineGiftScreen giftScreen;

    MachineCasesScreen(CaseManager caseManager, MachineItemFactory items, MachineMainScreen mainScreen) {
        this.caseManager = caseManager;
        this.items = items;
        this.mainScreen = mainScreen;
        this.anvil = new MachineAnvilInput(caseManager.getPlugin());
        this.giftScreen = new MachineGiftScreen(caseManager, items);
    }

    void open(Player player, int requestedPage) {
        List<CaseDefinition> cases = caseManager.getCases();
        int pages = RewardScreenLayout.pageCount(cases.size());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        // Turning a page or coming back to the list redraws the open window instead of opening a new one.
        if (MachineGuiSupport.refreshIfOpen(caseManager, player, GuiSession.Screen.MACHINE_CASES, "", page, -1,
                inventory -> fill(inventory, cases, page, pages))) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(GuiSession.machineCases(page), 54,
                ColorUtil.colorize("&0Кейсы"));
        fill(inventory, cases, page, pages);
        caseManager.getPlugin().openGui(player, inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.22f, 1.15f);
    }

    /** The plugin-wide gift limits, opened by {@code /pncases giftlimit}. */
    void openGifts(Player player) {
        giftScreen.open(player, 0);
    }

    /** Clicks in the list of cases and in the screens that belong to no case (the gift limits). */
    void handleClick(GuiSession holder, Player player, InventoryClickEvent event, int slot) {
        switch (holder.screen()) {
            case MACHINE_GIFTS -> {
                giftScreen.handleClick(holder, player, event, slot);
                return;
            }
            case MACHINE_GIFT_DEFAULT, MACHINE_GIFT_RULE -> {
                giftScreen.handleLimitClick(holder, player, event, slot);
                return;
            }
            default -> {
            }
        }
        int page = holder.page();
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_NEW) {
            startCreate(player, page);
            return;
        }
        if (slot == SLOT_PREVIOUS) {
            open(player, page - 1);
            return;
        }
        if (slot == SLOT_NEXT) {
            open(player, page + 1);
            return;
        }
        int position = CONTENT_SLOTS.indexOf(slot);
        if (position < 0) return;
        List<CaseDefinition> cases = caseManager.getCases();
        int index = page * CONTENT_SLOTS.size() + position;
        if (index < cases.size()) mainScreen.open(player, cases.get(index).name());
    }

    private void fill(Inventory inventory, List<CaseDefinition> cases, int page, int pages) {
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        int start = page * CONTENT_SLOTS.size();
        for (int position = 0; position < CONTENT_SLOTS.size(); position++) {
            int index = start + position;
            if (index >= cases.size()) break;
            inventory.setItem(CONTENT_SLOTS.get(position), caseButton(cases.get(index)));
        }
        inventory.setItem(SLOT_NEW, items.builtInButton(GuiDesign.STYLE,
                items.name("Новый кейс"), items.hintLore(
                        List.of("Создаёт кейс с готовыми настройками, дальше всё делается кликами"),
                        List.of(),
                        Hint.left("создать"))));
        if (page > 0) {
            inventory.setItem(SLOT_PREVIOUS, items.builtInButton(MachineIcon.REWARDS_PREVIOUS, "&#65D1FCПредыдущая", List.of()));
        }
        if (page + 1 < pages) {
            inventory.setItem(SLOT_NEXT, items.builtInButton(MachineIcon.REWARDS_NEXT, "&#65D1FCСледующая", List.of()));
        }
        inventory.setItem(SLOT_CLOSE, items.closeButton(null));
        if (cases.isEmpty()) {
            inventory.setItem(22, items.builtInButton(GuiDesign.INFO, items.name("Кейсов пока нет"), items.hintLore(
                    List.of("Нажмите «Новый кейс» внизу, чтобы создать первый"), List.of())));
        }
    }

    private ItemStack caseButton(CaseDefinition definition) {
        List<CaseHealthCheck.Issue> issues = mainScreen.healthIssues(definition);
        String status = switch (CaseHealthCheck.status(issues)) {
            case READY -> "&#A7F3D0✔ Готов к работе";
            case WARNINGS -> "&#FCA865⚠ Есть предупреждения: " + issues.size();
            case ERRORS -> "&#FC7165✖ Не готов, замечаний: " + issues.size();
        };
        List<String> lore = items.hintLore(
                List.of(status),
                List.of(
                        "ID", definition.name(),
                        "Наград", String.valueOf(definition.rewards().size()),
                        "Блоков", String.valueOf(definition.blockLocations().size())),
                Hint.left("открыть настройки"));
        ItemStack source = definition.openButton();
        ItemStack icon = source == null || source.getType().isAir() ? new ItemStack(Material.CHEST) : source;
        return items.builtInButton(icon, items.name(definition.displayName()), lore);
    }

    private void startCreate(Player player, int page) {
        anvil.open(player, "&0Новый кейс", "daily",
                new ItemStack(Material.WRITABLE_BOOK),
                value -> idError(value),
                value -> create(player, value, page),
                () -> open(player, page));
    }

    /** Why a typed id cannot be used, or null when it is fine. */
    String idError(String value) {
        String id = CaseConfigRepository.normalizeName(value);
        if (id.isEmpty()) return "Введите название";
        if (!CaseConfigRepository.isValidId(id)) return "Только a-z, 0-9, _ и - (до 64)";
        if (caseManager.caseExists(id)) return "Такой кейс уже есть";
        return null;
    }

    private void create(Player player, String typed, int page) {
        String id = CaseConfigRepository.normalizeName(typed);
        switch (caseManager.createCustomCase(id)) {
            case CREATED -> {
                player.sendMessage(ColorUtil.colorize("&#A7F3D0Кейс &f" + id + "&#A7F3D0 создан. "
                        + "Добавьте награды: откройте «Награды» и кликайте по предметам в вашем инвентаре, "
                        + "затем нажмите «Блок кейса»."));
                mainScreen.open(player, id);
            }
            case ALREADY_EXISTS -> {
                player.sendMessage(ColorUtil.colorize("&#FC7165Кейс " + id + " уже существует."));
                open(player, page);
            }
            case INVALID_ID -> {
                player.sendMessage(ColorUtil.colorize("&#FC7165Недопустимое название кейса."));
                open(player, page);
            }
            case SAVE_FAILED, RELOAD_FAILED -> {
                player.sendMessage(ColorUtil.colorize("&#FC7165Не удалось создать кейс. Подробности в консоли."));
                open(player, page);
            }
        }
    }

    /** All pages needed for {@code count} cases, at least one. */
    static int pages(int count) {
        return RewardScreenLayout.pageCount(count);
    }

    /** Names shown on one page, for tests and for the paging logic. */
    static List<String> pageNames(List<String> all, int page) {
        List<String> names = new ArrayList<>();
        int start = page * CONTENT_SLOTS.size();
        for (int index = start; index < Math.min(all.size(), start + CONTENT_SLOTS.size()); index++) {
            names.add(all.get(index));
        }
        return names;
    }
}
