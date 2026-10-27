package ru.privatenull.pncases.gui.machine;

import ru.privatenull.pncases.gui.GuiDesign;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pncases.gui.GuiSession;
import ru.privatenull.pncases.gui.machine.MachineItemFactory.Hint;
import ru.privatenull.pncases.support.item.HeadUtil;
import ru.privatenull.pncases.support.item.ItemFactory;
import ru.privatenull.pncases.support.text.ColorUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static ru.privatenull.pncases.gui.machine.RewardConfigEditor.applyValue;
import static ru.privatenull.pncases.gui.machine.RewardConfigEditor.baseReward;
import static ru.privatenull.pncases.gui.machine.RewardConfigEditor.chanceForPercent;
import static ru.privatenull.pncases.gui.machine.RewardConfigEditor.configuredCommands;
import static ru.privatenull.pncases.gui.machine.RewardConfigEditor.deepMap;
import static ru.privatenull.pncases.gui.machine.RewardConfigEditor.inputError;
import static ru.privatenull.pncases.gui.machine.RewardConfigEditor.newReward;
import static ru.privatenull.pncases.gui.machine.RewardConfigEditor.positiveDouble;
import static ru.privatenull.pncases.gui.machine.RewardConfigEditor.removeNestedMessage;
import static ru.privatenull.pncases.gui.machine.RewardConfigEditor.rewardMaps;
import static ru.privatenull.pncases.gui.machine.RewardLabels.chancePercent;
import static ru.privatenull.pncases.gui.machine.RewardLabels.currentValue;
import static ru.privatenull.pncases.gui.machine.RewardLabels.percentText;
import static ru.privatenull.pncases.gui.machine.RewardLabels.rewardDescription;
import static ru.privatenull.pncases.gui.machine.RewardLabels.titleText;
import static ru.privatenull.pncases.gui.machine.RewardLabels.valueTitle;
import static ru.privatenull.pncases.gui.machine.RewardScreenLayout.CONTENT_SLOTS;
import static ru.privatenull.pncases.gui.machine.RewardScreenLayout.SLOT_ADD_ITEM;
import static ru.privatenull.pncases.gui.machine.RewardScreenLayout.SLOT_ADD_LUCKPERMS;
import static ru.privatenull.pncases.gui.machine.RewardScreenLayout.SLOT_ADD_PERMISSION;
import static ru.privatenull.pncases.gui.machine.RewardScreenLayout.SLOT_ADD_PERSONAL;
import static ru.privatenull.pncases.gui.machine.RewardScreenLayout.SLOT_ADD_POINTS;
import static ru.privatenull.pncases.gui.machine.RewardScreenLayout.SLOT_ADD_VAULT;
import static ru.privatenull.pncases.gui.machine.RewardScreenLayout.SLOT_BACK;
import static ru.privatenull.pncases.gui.machine.RewardScreenLayout.SLOT_EQUALIZE;
import static ru.privatenull.pncases.gui.machine.RewardScreenLayout.SLOT_INFO;
import static ru.privatenull.pncases.gui.machine.RewardScreenLayout.SLOT_NEXT;
import static ru.privatenull.pncases.gui.machine.RewardScreenLayout.SLOT_PREVIOUS;
import static ru.privatenull.pncases.gui.machine.RewardScreenLayout.SLOT_SIMULATE;

/** Reward pool list, reward editor, move, delete and icon screens of the Machine GUI. */
final class MachineRewardScreen {

    private static final int EDIT_CHANCE = 11;
    private static final int EDIT_RARITY = 13;
    private static final int EDIT_VISUAL = 15;
    private static final int EDIT_VALUE = 20;
    private static final int EDIT_MOVE = 22;
    private static final int EDIT_DELETE = 24;
    private static final int EDIT_EXTRA = 29;
    private static final int EDIT_MESSAGE = 31;
    private static final int EDIT_BACK = 40;
    private static final int ICON_FROM_ITEM = 11;
    private static final int ICON_NAME = 13;
    private static final int ICON_FROM_BASE64 = 15;
    private static final int ICON_PREVIEW = 4;
    private static final int ICON_BACK = 22;

    private final CaseManager caseManager;
    private final MachineGuiSupport support;
    private final MachineMainScreen mainScreen;
    private final MachineItemFactory items;
    private final MachineAnvilInput anvil;
    private final RewardLabels labels;
    private final RewardSimulationScreen simulation;
    private final Map<UUID, PendingTexture> pendingTextures = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingChance = new ConcurrentHashMap<>();

    MachineRewardScreen(CaseManager caseManager, MachineGuiSupport support,
                        MachineMainScreen mainScreen, MachineItemFactory items) {
        this.caseManager = caseManager;
        this.support = support;
        this.mainScreen = mainScreen;
        this.items = items;
        this.anvil = new MachineAnvilInput(caseManager.getPlugin());
        this.labels = new RewardLabels(caseManager);
        this.simulation = new RewardSimulationScreen(caseManager, items, labels,
                (player, caseName, page) -> open(player, caseName, page));
    }

    void open(Player player, String caseName) {
        open(player, caseName, 0);
    }

    void open(Player player, String caseName, int requestedPage) {
        open(player, caseName, requestedPage, true);
    }

    private void open(Player player, String caseName, int requestedPage, boolean playSound) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) return;
        int pages = RewardScreenLayout.pageCount(definition.rewards().size());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        // Already on the reward list: update it in place instead of opening a new window.
        if (MachineGuiSupport.refreshIfOpen(caseManager, player, GuiSession.Screen.MACHINE_REWARDS,
                definition.name(), page, -1, inventory -> fillList(inventory, definition, page, pages))) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(
                GuiSession.machineRewards(caseName, page), 54,
                ColorUtil.colorize("&0Пул наград | " + definition.displayName())
        );
        fillList(inventory, definition, page, pages);
        caseManager.getPlugin().openGui(player, inventory);
        if (playSound) player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.2f);
    }

    void openEditor(Player player, String caseName, int index) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null || index < 0 || index >= definition.rewards().size()) {
            open(player, caseName);
            return;
        }
        // Same reward already open (for example after a chance step): update it in place.
        if (MachineGuiSupport.refreshIfOpen(caseManager, player, GuiSession.Screen.MACHINE_REWARD,
                definition.name(), -1, index, inventory -> fillEditor(inventory, definition, index))) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(
                GuiSession.machineReward(caseName, index), 45,
                ColorUtil.colorize("&0" + titleText(labels.plainRewardName(definition.rewards().get(index))))
        );
        fillEditor(inventory, definition, index);
        caseManager.getPlugin().openGui(player, inventory);
    }

    void handleListClick(GuiSession holder, Player player, InventoryClickEvent event,
                         CaseDefinition definition, int slot) {
        int position = CONTENT_SLOTS.indexOf(slot);
        if (position >= 0) {
            int index = holder.page() * CONTENT_SLOTS.size() + position;
            if (index < definition.rewards().size()) {
                openEditor(player, definition.name(), index);
            }
            return;
        }
        switch (slot) {
            case SLOT_BACK -> mainScreen.open(player, definition.name());
            case SLOT_ADD_ITEM -> addItem(player, event, definition);
            case SLOT_ADD_VAULT -> startCreate(player, definition, Reward.Type.VAULT);
            case SLOT_ADD_POINTS -> startCreate(player, definition, Reward.Type.PLAYERPOINTS);
            case SLOT_ADD_LUCKPERMS -> startCreate(player, definition, Reward.Type.LUCKPERMS);
            case SLOT_ADD_PERSONAL -> startCreate(player, definition, Reward.Type.PERSONAL);
            case SLOT_ADD_PERMISSION -> startCreatePermission(player, definition);
            case SLOT_PREVIOUS -> open(player, definition.name(), holder.page() - 1);
            case SLOT_NEXT -> open(player, definition.name(), holder.page() + 1);
            case SLOT_SIMULATE -> simulation.start(player, definition);
            case SLOT_EQUALIZE -> equalizeChances(player, definition);
            default -> { }
        }
    }

    /** Adds the clicked inventory item as an item reward; the item itself stays with the player. */
    void addFromInventory(Player player, CaseDefinition definition, ItemStack clicked) {
        if (!ItemFactory.isRealItem(clicked)) return;
        Map<String, Object> reward = baseReward(Reward.Type.ITEM);
        reward.put("item", ItemFactory.toMap(clicked));
        if (!appendReward(player, definition.name(), reward)) return;
        CaseDefinition updated = caseManager.getCaseByName(definition.name());
        int total = updated == null ? 1 : updated.rewards().size();
        player.sendMessage(ColorUtil.colorize("&#A7F3D0Добавлено в награды: &f" + labels.itemName(clicked)
                + " &#69747E(всего наград: " + total + ")"));
        open(player, definition.name(), Math.max(0, (total - 1) / CONTENT_SLOTS.size()), false);
    }

    /** A click on the chance button moves the reward share; the wheel asks for an exact value instead. */
    private void changeChance(Player player, InventoryClickEvent event, CaseDefinition definition,
                              int index, Reward reward) {
        if (event.getClick() == ClickType.MIDDLE) {
            startEditChance(player, definition, index, reward);
            return;
        }
        if (definition.rewards().size() < 2) {
            player.sendMessage(ColorUtil.colorize("&#A6B0BAЕдинственная награда всегда выпадает со 100%. "
                    + "Добавьте ещё награды, чтобы менять шансы."));
            return;
        }
        double step = event.isShiftClick() ? 10.0 : 1.0;
        double next = RewardConfigEditor.steppedPercent(
                RewardLabels.sharePercent(reward, definition), event.isRightClick() ? -step : step);
        if (updateChance(player, definition.name(), index, percentText(next))) {
            openEditor(player, definition.name(), index);
        }
    }

    private void equalizeChances(Player player, CaseDefinition definition) {
        if (definition.rewards().size() < 2) return;
        boolean saved = support.update(player, definition.name(), root -> {
            List<Map<String, Object>> rewards = rewardMaps(root);
            RewardConfigEditor.equalizeChances(rewards);
            root.set("rewards", rewards);
        });
        if (saved) {
            player.sendMessage(ColorUtil.colorize("&#A7F3D0Шансы выровнены: у всех наград одинаковая доля."));
            open(player, definition.name(), 0, false);
        }
    }

    void startSimulation(Player player, CaseDefinition definition) {
        simulation.start(player, definition);
    }

    void handleSimulationClick(GuiSession holder, Player player, CaseDefinition definition, int slot) {
        simulation.handleClick(holder, player, definition, slot);
    }

    void handleEditorClick(GuiSession holder, Player player, InventoryClickEvent event,
                           CaseDefinition definition, int slot) {
        int index = holder.selection();
        if (index < 0 || index >= definition.rewards().size()) {
            open(player, definition.name());
            return;
        }
        Reward reward = definition.rewards().get(index);
        switch (slot) {
            case EDIT_CHANCE -> changeChance(player, event, definition, index, reward);
            case EDIT_RARITY -> changeRarity(player, definition, index, reward, event.isRightClick());
            case EDIT_VISUAL -> openIconEditor(player, definition, index);
            case EDIT_VALUE -> startEditValue(player, event, definition, index, reward);
            case EDIT_MOVE -> openMoveScreen(player, definition, index, index / CONTENT_SLOTS.size());
            case EDIT_DELETE -> openDeleteConfirm(player, definition, index);
            case EDIT_EXTRA -> handleExtra(player, event, definition, index, reward);
            case EDIT_MESSAGE -> handleMessage(player, event, definition, index, reward);
            case EDIT_BACK -> open(player, definition.name(), index / CONTENT_SLOTS.size());
            default -> { }
        }
    }

    void handleIconClick(GuiSession holder, Player player, InventoryClickEvent event,
                         CaseDefinition definition, int slot) {
        int index = holder.selection();
        if (index < 0 || index >= definition.rewards().size()) {
            open(player, definition.name());
            return;
        }
        if (slot == ICON_BACK) {
            openEditor(player, definition.name(), index);
        } else if (slot == ICON_FROM_ITEM) {
            replaceVisual(player, event, definition, index, definition.rewards().get(index));
        } else if (slot == ICON_NAME) {
            startEditDisplayName(player, definition, index, definition.rewards().get(index));
        } else if (slot == ICON_FROM_BASE64) {
            openBase64Input(player, definition, index);
        }
    }

    boolean handleChat(AsyncPlayerChatEvent event) {
        PendingTexture pending = pendingTextures.remove(event.getPlayer().getUniqueId());
        if (pending == null) return false;
        event.setCancelled(true);
        String value = event.getMessage() == null ? "" : event.getMessage().trim();
        caseManager.getPlugin().tasks().run(() -> {
            if (value.equalsIgnoreCase("cancel") || value.equalsIgnoreCase("отмена")) {
                openEditor(event.getPlayer(), pending.caseName(), pending.index());
                return;
            }
            if (!updateBase64Icon(event.getPlayer(), pending, value)) {
                event.getPlayer().sendMessage(ColorUtil.colorize("&#FC7165Текстура не распознана. Используйте Base64, URL или хеш."));
            }
            openEditor(event.getPlayer(), pending.caseName(), pending.index());
        });
        return true;
    }

    void cancel(Player player) {
        pendingTextures.remove(player.getUniqueId());
        pendingChance.remove(player.getUniqueId());
        simulation.cancel(player);
    }

    void handleMoveClick(GuiSession holder, Player player, CaseDefinition definition, int slot) {
        if (slot == SLOT_BACK) {
            openEditor(player, definition.name(), holder.selection());
            return;
        }
        if (slot == SLOT_PREVIOUS) {
            openMoveScreen(player, definition, holder.selection(), holder.page() - 1);
            return;
        }
        if (slot == SLOT_NEXT) {
            openMoveScreen(player, definition, holder.selection(), holder.page() + 1);
            return;
        }
        int position = CONTENT_SLOTS.indexOf(slot);
        if (position < 0) return;
        int target = holder.page() * CONTENT_SLOTS.size() + position;
        int source = holder.selection();
        if (source < 0 || source >= definition.rewards().size() || target >= definition.rewards().size()) return;
        if (source == target) {
            openEditor(player, definition.name(), source);
            return;
        }
        final int newIndex = target;
        boolean saved = support.update(player, definition.name(), root -> {
            List<Map<String, Object>> rewards = rewardMaps(root);
            Map<String, Object> moved = rewards.remove(source);
            rewards.add(Math.min(newIndex, rewards.size()), moved);
            root.set("rewards", rewards);
        });
        if (saved) openEditor(player, definition.name(), newIndex);
    }

    void handleDeleteClick(GuiSession holder, Player player, CaseDefinition definition, int slot) {
        int index = holder.selection();
        if (slot == 11) deleteReward(player, definition, index);
        else if (slot == 15) openEditor(player, definition.name(), index);
    }

    private void openMoveScreen(Player player, CaseDefinition definition, int source, int requestedPage) {
        int pages = RewardScreenLayout.pageCount(definition.rewards().size());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inventory = Bukkit.createInventory(
                GuiSession.machineRewardMove(definition.name(), source, page), 54,
                ColorUtil.colorize("&0Маршрут награды"));
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        int start = page * CONTENT_SLOTS.size();
        int total = definition.rewards().stream().mapToInt(Reward::chance).sum();
        for (int position = 0; position < CONTENT_SLOTS.size(); position++) {
            int index = start + position;
            if (index >= definition.rewards().size()) break;
            Reward reward = definition.rewards().get(index);
            ItemStack button = rewardButton(definition, reward, index, total);
            ItemMeta meta = button.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ColorUtil.colorize(items.name(labels.plainRewardName(reward))));
                meta.setLore(items.lore(
                                index == source ? "Текущее место награды" : "Перемещение награды в пуле",
                                "Позиция", String.valueOf(index + 1),
                                index == source ? null : "Выбрать", null)
                        .stream().map(ColorUtil::colorize).toList());
                button.setItemMeta(meta);
            }
            inventory.setItem(CONTENT_SLOTS.get(position), button);
        }
        inventory.setItem(SLOT_BACK, items.backButton(definition));
        if (page > 0) inventory.setItem(SLOT_PREVIOUS,
                items.builtInButton(MachineIcon.MOVE_PREVIOUS, "&#65D1FCПредыдущая страница", List.of()));
        if (page + 1 < pages) inventory.setItem(SLOT_NEXT,
                items.builtInButton(MachineIcon.MOVE_NEXT, "&#65D1FCСледующая страница", List.of()));
        inventory.setItem(SLOT_INFO, items.builtInButton(MachineIcon.MOVE_INFO,
                items.name("Позиция награды"), items.lore(
                        List.of("Выберите награду, перед которой нужно её поместить"),
                        List.of(), null, null)));
        caseManager.getPlugin().openGui(player, inventory);
    }

    private void openDeleteConfirm(Player player, CaseDefinition definition, int index) {
        Reward reward = definition.rewards().get(index);
        Inventory inventory = Bukkit.createInventory(
                GuiSession.machineRewardDelete(definition.name(), index), 27,
                ColorUtil.colorize("&0Подтверждение удаления"));
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inventory.setItem(11, items.builtInButton(MachineIcon.REWARD_CONFIRM,
                "&#FC7165Удалить", items.lore(
                        "&cДействие нельзя отменить",
                        "Награда", labels.plainRewardName(reward),
                        "Подтвердить", null)));
        inventory.setItem(13, reward.visualItem() == null ? new ItemStack(Material.CHEST) : reward.visualItem().clone());
        inventory.setItem(15, items.builtInButton(MachineIcon.REWARD_CANCEL,
                items.name("Отменить"), items.lore(
                        "Возврат без удаления награды",
                        null, null, "Вернуться", null)));
        caseManager.getPlugin().openGui(player, inventory);
    }

    private void fillList(Inventory inventory, CaseDefinition definition, int page, int pages) {
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        int totalChance = definition.rewards().stream().mapToInt(Reward::chance).sum();
        int start = page * CONTENT_SLOTS.size();
        for (int position = 0; position < CONTENT_SLOTS.size(); position++) {
            int index = start + position;
            if (index >= definition.rewards().size()) break;
            Reward reward = definition.rewards().get(index);
            inventory.setItem(CONTENT_SLOTS.get(position), rewardButton(definition, reward, index, totalChance));
        }
        inventory.setItem(SLOT_BACK, items.backButton(definition));
        inventory.setItem(SLOT_ADD_ITEM, items.builtInButton(MachineIcon.REWARDS_ADD_ITEM,
                items.name("Добавить: Предмет"), items.hintLore(
                        List.of("Просто кликните по предмету в вашем инвентаре, и он станет наградой",
                                "Можно и взять предмет на курсор или в руку"),
                        List.of(),
                        Hint.item("добавить предмет с курсора"))));
        if (definition.rewards().size() >= 2) {
            inventory.setItem(SLOT_EQUALIZE, items.builtInButton(GuiDesign.SIZE,
                    items.name("Выровнять шансы"), items.hintLore(
                            List.of("Все награды станут одинаково вероятными",
                                    "Отменить можно на главном экране"),
                            List.of(),
                            Hint.left("выровнять"))));
        }
        inventory.setItem(SLOT_ADD_VAULT, addButton(MachineIcon.REWARDS_ADD_COINS, "Монеты", "Создайте денежную выплату через Vault."));
        inventory.setItem(SLOT_ADD_POINTS, addButton(MachineIcon.REWARDS_ADD_POINTS, "Поинты", "Добавьте баланс PlayerPoints."));
        inventory.setItem(SLOT_ADD_LUCKPERMS, addButton(MachineIcon.REWARDS_ADD_GROUP, "Привилегия", "Выдайте игроку группу LuckPerms."));
        inventory.setItem(SLOT_ADD_PERSONAL, addButton(MachineIcon.REWARDS_ADD_COMMAND, "Команда", "Выполните собственную команду от консоли."));
        inventory.setItem(SLOT_ADD_PERMISSION, addButton(MachineIcon.REWARDS_ADD_PERMISSION, "Право", "Выдайте отдельный permission LuckPerms."));
        inventory.setItem(SLOT_SIMULATE, items.builtInButton(GuiDesign.ANIMATION,
                items.name("Проверить распределение"), items.hintLore(
                        List.of(RewardSimulationScreen.canSimulate(definition.rewards().size())
                                        ? "100 000 виртуальных открытий без выдачи наград"
                                        : "Нужно минимум две награды: одна выпадает всегда",
                                "Личный гарант не учитывается"),
                        List.of("Наград", String.valueOf(definition.rewards().size())),
                        RewardSimulationScreen.canSimulate(definition.rewards().size())
                                ? new MachineItemFactory.Hint[]{MachineItemFactory.Hint.left("запустить симуляцию")}
                                : new MachineItemFactory.Hint[0])));
        if (page > 0) inventory.setItem(SLOT_PREVIOUS, items.builtInButton(MachineIcon.REWARDS_PREVIOUS, "&#65D1FCПредыдущая", List.of()));
        if (page + 1 < pages) inventory.setItem(SLOT_NEXT, items.builtInButton(MachineIcon.REWARDS_NEXT, "&#65D1FCСледующая", List.of()));
    }

    private void fillEditor(Inventory inventory, CaseDefinition definition, int index) {
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        Reward reward = definition.rewards().get(index);
        inventory.setItem(4, rewardButton(definition, reward, index, definition.rewards().stream().mapToInt(Reward::chance).sum()));
        inventory.setItem(EDIT_CHANCE, items.builtInButton(MachineIcon.REWARD_CHANCE,
                items.name("Шанс выпадения"), items.hintLore(
                        List.of("Доля этой награды в общем пуле: остальные подстроятся"),
                        List.of("Шанс", chancePercent(reward, definition) + "%"),
                        Hint.left("+1%"), Hint.right("-1%"),
                        Hint.shiftLeft("+10%"), Hint.shiftRight("-10%"),
                        Hint.middle("ввести точное значение"))));
        boolean rarityEnabled = caseManager.getPlugin().rarityEnabled();
        inventory.setItem(EDIT_RARITY, items.builtInButton(MachineIcon.REWARD_RARITY,
                items.name("Редкость"), items.lore(
                        rarityEnabled ? "Оформление награды в интерфейсе игрока" : "Функция редкостей отключена",
                        "Редкость", caseManager.getRewardPresentation().formattedRarity(reward),
                        rarityEnabled ? "Следующая" : null, rarityEnabled ? "Предыдущая" : null)));
        inventory.setItem(EDIT_VISUAL, items.builtInButton(MachineIcon.REWARD_VISUAL,
                items.name("Оформление"), items.lore(
                        List.of("Иконка и название награды"),
                        List.of("Название", labels.plainRewardName(reward), "Иконка", labels.itemName(reward.visualItem())),
                        "Изменить", null)));
        inventory.setItem(EDIT_VALUE, items.builtInButton(MachineIcon.REWARD_VALUE,
                items.name(valueTitle(reward)), valueLore(reward)));
        inventory.setItem(EDIT_MOVE, items.builtInButton(MachineIcon.REWARD_MOVE,
                items.name("Позиция"), items.lore(
                        "Порядок наград в редакторе",
                        "Позиция", String.valueOf(index + 1),
                        "Изменить", null)));
        inventory.setItem(EDIT_DELETE, items.builtInButton(MachineIcon.REWARD_DELETE,
                "&#FC7165Удалить награду", items.lore(
                "&cПолное удаление награды из кейса",
                "Награда", labels.plainRewardName(reward),
                "Продолжить", null)));
        if (reward.type() == Reward.Type.LUCKPERMS) {
            inventory.setItem(EDIT_EXTRA, items.builtInButton(MachineIcon.REWARD_VALUE,
                    items.name("Срок действия"), items.lore(
                            "Продолжительность привилегии или права",
                            "Срок", reward.lpDuration() == null ? "&#A7F3D0Навсегда" : ColorUtil.paint("&#FCA865", reward.lpDuration()),
                            "Изменить", "Сделать навсегда")));
        } else if (reward.type() == Reward.Type.PERSONAL) {
            inventory.setItem(EDIT_EXTRA, items.builtInButton(MachineIcon.REWARDS_ADD_COMMAND,
                    items.name("Команды"), items.lore(
                            "Последовательность команд от консоли",
                            "Количество", String.valueOf(reward.commands().size()),
                            "Добавить", reward.commands().size() > 1 ? "Удалить последнюю" : null)));
        }
        inventory.setItem(EDIT_MESSAGE, items.builtInButton(MachineIcon.ANVIL_REWARD_NAME,
                items.name("Сообщение игроку"), items.lore(
                        "Текст после успешной выдачи награды",
                        "Сообщение", reward.message() == null ? "&7Не задано" : reward.message(),
                        "Изменить", reward.message() == null ? null : "Очистить")));
        inventory.setItem(EDIT_BACK, items.backButton(definition));
    }

    private ItemStack rewardButton(CaseDefinition definition, Reward reward, int index, int totalChance) {
        ItemStack item = caseManager.getRewardPresentation().buildPreviewItem(definition, reward, totalChance);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        String name = labels.plainRewardName(reward);
        meta.setDisplayName(ColorUtil.colorize(items.name(name)));
        List<String> parameters = new ArrayList<>(List.of(
                "Выдача", labels.rewardSummary(reward),
                "Шанс", chancePercent(reward, definition) + "%"));
        if (caseManager.getPlugin().rarityEnabled()) parameters.addAll(List.of(
                "Редкость", caseManager.getRewardPresentation().formattedRarity(reward)));
        meta.setLore(items.lore(List.of(rewardDescription(reward)), parameters, "Изменить", null)
                .stream().map(ColorUtil::colorize).toList());
        item.setItemMeta(meta);
        return item;
    }

    private List<String> valueLore(Reward reward) {
        String action = reward.type() == Reward.Type.ITEM ? "Заменить с курсора" : "Изменить";
        return items.lore(rewardDescription(reward), "Значение", labels.rewardSummary(reward), action, null);
    }

    private void openIconEditor(Player player, CaseDefinition definition, int index) {
        CaseDefinition current = caseManager.getCaseByName(definition.name());
        if (current != null) definition = current;
        Reward reward = definition.rewards().get(index);
        Inventory inventory = Bukkit.createInventory(
                GuiSession.machineRewardIcon(definition.name(), index), 27,
                ColorUtil.colorize("&0Образ награды | " + titleText(labels.plainRewardName(reward))));
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        ItemStack preview = reward.visualItem() == null
                ? new ItemStack(Material.ITEM_FRAME)
                : reward.visualItem().clone();
        ItemMeta previewMeta = preview.getItemMeta();
        if (previewMeta != null) {
            previewMeta.setDisplayName(ColorUtil.colorize(reward.displayName() == null
                    ? "&fНаграда"
                    : reward.displayName()));
            preview.setItemMeta(previewMeta);
        }
        inventory.setItem(ICON_PREVIEW, preview);
        inventory.setItem(ICON_FROM_ITEM, items.builtInButton(MachineIcon.ICON_FROM_ITEM,
                items.name("Предмет с курсора"), items.lore(
                        "Замена визуального предмета награды",
                        null, null, "Применить", null)));
        inventory.setItem(ICON_NAME, items.builtInButton(MachineIcon.ICON_NAME,
                items.name("Название"), items.lore(
                        "Подпись награды в анимации и предпросмотре",
                        "Название", labels.plainRewardName(reward),
                        "Изменить", null)));
        inventory.setItem(ICON_FROM_BASE64, items.builtInButton(MachineIcon.ICON_BASE64,
                items.name("Текстура головы"), items.lore(
                        "Base64, ссылка textures.minecraft.net или хеш",
                        null, null, "Изменить", null)));
        inventory.setItem(ICON_BACK, items.backButton(definition));
        caseManager.getPlugin().openGui(player, inventory);
    }

    private void openBase64Input(Player player, CaseDefinition definition, int index) {
        pendingTextures.put(player.getUniqueId(), new PendingTexture(
                definition.name(), index, definition.rewards().get(index).type()));
        player.closeInventory();
        player.sendMessage(ColorUtil.colorize("&#65D1FCВставьте Base64 текстуры одним сообщением."));
        player.sendMessage(ColorUtil.colorize("&7Также поддерживаются ссылка textures.minecraft.net и хеш. Для отмены: &fcancel"));
    }

    private void startEditDisplayName(Player player, CaseDefinition definition, int index, Reward reward) {
        String currentName = reward.displayName();
        if (currentName == null || currentName.isBlank()) currentName = labels.plainRewardName(reward);
        anvil.open(player, "&0Подпись награды", currentName,
                MachineIcon.ANVIL_REWARD_NAME.item("&fНазвание награды"),
                value -> value == null || value.trim().isBlank() ? "Введите название" : null,
                value -> {
                    updateReward(player, definition.name(), index,
                            map -> map.put("name", value.trim()));
                    openIconEditor(player, definition, index);
                },
                () -> openIconEditor(player, definition, index));
    }

    private ItemStack addButton(MachineIcon icon, String type, String hint) {
        return items.builtInButton(icon, items.name("Добавить: " + type),
                items.lore(hint, null, null, "Создать", null));
    }

    private void addItem(Player player, InventoryClickEvent event, CaseDefinition definition) {
        ItemStack source = ItemFactory.isRealItem(event.getCursor())
                ? event.getCursor() : player.getInventory().getItemInMainHand();
        if (!ItemFactory.isRealItem(source)) {
            player.sendMessage(ColorUtil.colorize("&#FC7165Положите предмет на курсор или возьмите его в основную руку."));
            return;
        }
        Map<String, Object> reward = baseReward(Reward.Type.ITEM);
        reward.put("item", ItemFactory.toMap(source));
        if (appendReward(player, definition.name(), reward)) {
            CaseDefinition updated = caseManager.getCaseByName(definition.name());
            int index = updated == null ? 0 : Math.max(0, updated.rewards().size() - 1);
            openEditor(player, definition.name(), index);
        }
    }

    private void startCreate(Player player, CaseDefinition definition, Reward.Type type) {
        String title = switch (type) {
            case VAULT -> "Сумма монет";
            case PLAYERPOINTS -> "Количество поинтов";
            case LUCKPERMS -> "Название привилегии";
            case PERSONAL -> "Команда консоли";
            case ITEM -> "Награда";
        };
        String example = switch (type) {
            case VAULT -> "500";
            case PLAYERPOINTS -> "100";
            case LUCKPERMS -> "vip";
            case PERSONAL -> "say {player} победил";
            case ITEM -> "";
        };
        anvil.open(player, "&0" + title, example,
                MachineIcon.ANVIL_REWARD_CREATE.item("&fНовая награда"),
                value -> inputError(type, value),
                value -> {
                    createReward(player, definition.name(), type, value);
                    open(player, definition.name());
                },
                () -> open(player, definition.name()));
    }

    private void startCreatePermission(Player player, CaseDefinition definition) {
        anvil.open(player, "&0Permission-награда", "essentials.fly",
                MachineIcon.ANVIL_PERMISSION.item("&bПраво LuckPerms"),
                value -> value == null || value.trim().isBlank()
                        ? "Введите permission"
                        : value.contains(" ") ? "Permission без пробелов" : null,
                value -> {
                    String node = value.trim();
                    Map<String, Object> reward = baseReward(Reward.Type.LUCKPERMS);
                    reward.put("luckperms", Map.of("node", node));
                    reward.put("name", "&b" + node);
                    reward.put("visual", Map.of("material", "BOOK", "name", "&b" + node));
                    appendReward(player, definition.name(), reward);
                    open(player, definition.name());
                },
                () -> open(player, definition.name()));
    }

    private void startEditValue(Player player, InventoryClickEvent event,
                                CaseDefinition definition, int index, Reward reward) {
        if (reward.type() == Reward.Type.ITEM) {
            ItemStack cursor = event.getCursor();
            if (!ItemFactory.isRealItem(cursor)) {
                player.sendMessage(ColorUtil.colorize("&#FC7165Положите выдаваемый предмет на курсор и нажмите снова."));
                return;
            }
            if (updateReward(player, definition.name(), index, map -> map.put("item", ItemFactory.toMap(cursor)))) {
                openEditor(player, definition.name(), index);
            }
            return;
        }
        anvil.open(player, "&0" + valueTitle(reward), currentValue(reward),
                MachineIcon.ANVIL_REWARD_VALUE.item("&fЗначение награды"),
                value -> inputError(reward.type(), value),
                value -> {
                    updateReward(player, definition.name(), index, map -> applyValue(map, reward.type(), value));
                    openEditor(player, definition.name(), index);
                },
                () -> openEditor(player, definition.name(), index));
    }

    private void startEditChance(Player player, CaseDefinition definition, int index, Reward reward) {
        anvil.open(player, "&0Шанс выпадения", chancePercent(reward, definition),
                MachineIcon.ANVIL_CHANCE.item("&fШанс выпадения"), value -> {
                    Double percent = positiveDouble(value.replace("%", ""));
                    return percent == null || percent > 100.0 ? "От 0.01 до 100" : null;
                }, value -> openChanceConfirm(player, definition, index, value),
                () -> openEditor(player, definition.name(), index));
    }

    /** Shows what the new chance does to the whole pool before anything is saved. */
    private void openChanceConfirm(Player player, CaseDefinition definition, int index, String value) {
        CaseDefinition current = caseManager.getCaseByName(definition.name());
        Double percent = positiveDouble(value.replace("%", ""));
        if (current == null || percent == null || percent > 100.0
                || index < 0 || index >= current.rewards().size()) {
            openEditor(player, definition.name(), index);
            return;
        }
        List<Integer> weights = current.rewards().stream().map(Reward::chance).toList();
        ChanceChangePreview preview = ChanceChangePreview.compute(weights, index, percent);
        Reward reward = current.rewards().get(index);

        Inventory inventory = Bukkit.createInventory(
                GuiSession.machineRewardChance(definition.name(), index), 27,
                ColorUtil.colorize("&0Подтверждение шанса"));
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        List<String> parameters = new ArrayList<>();
        parameters.add("Шанс награды");
        parameters.add(percentText(preview.beforePercent()) + "% → " + percentText(preview.afterPercent()) + "%");
        int shown = 0;
        for (ChanceChangePreview.Shift shift : preview.others()) {
            if (shift.change() < 0.005 || shown == 4) break;
            parameters.add(labels.plainRewardName(current.rewards().get(shift.index())));
            parameters.add(percentText(shift.beforePercent()) + "% → " + percentText(shift.afterPercent()) + "%");
            shown++;
        }
        inventory.setItem(11, items.builtInButton(MachineIcon.REWARD_CONFIRM,
                "&#A7F3D0Применить", items.lore(
                        List.of("Шансы остальных наград изменятся пропорционально"),
                        parameters, "Применить", null)));
        inventory.setItem(13, reward.visualItem() == null ? new ItemStack(Material.CHEST) : reward.visualItem().clone());
        inventory.setItem(15, items.builtInButton(MachineIcon.REWARD_CANCEL,
                items.name("Отменить"), items.lore(
                        "Вернуться без изменений",
                        null, null, "Вернуться", null)));
        pendingChance.put(player.getUniqueId(), value);
        caseManager.getPlugin().openGui(player, inventory);
    }

    void handleChanceClick(GuiSession holder, Player player, CaseDefinition definition, int slot) {
        int index = holder.selection();
        if (slot == 11) {
            String value = pendingChance.remove(player.getUniqueId());
            if (value != null) updateChance(player, definition.name(), index, value);
            openEditor(player, definition.name(), index);
        } else if (slot == 15) {
            pendingChance.remove(player.getUniqueId());
            openEditor(player, definition.name(), index);
        }
    }

    private void handleExtra(Player player, InventoryClickEvent event, CaseDefinition definition,
                             int index, Reward reward) {
        if (reward.type() == Reward.Type.LUCKPERMS) {
            if (event.isRightClick()) {
                if (updateReward(player, definition.name(), index, map -> {
                    Map<String, Object> section = map.get("luckperms") instanceof Map<?, ?> current
                            ? deepMap(current) : new LinkedHashMap<>();
                    section.remove("duration");
                    map.put("luckperms", section);
                })) openEditor(player, definition.name(), index);
            } else {
                startEditDuration(player, definition, index, reward);
            }
            return;
        }
        if (reward.type() != Reward.Type.PERSONAL) return;
        if (event.isRightClick()) {
            removeLastCommand(player, definition, index, reward);
        } else {
            startAddCommand(player, definition, index);
        }
    }

    private void startEditDuration(Player player, CaseDefinition definition, int index, Reward reward) {
        String current = reward.lpDuration() == null ? "7d" : reward.lpDuration();
        anvil.open(player, "&0Срок действия", current,
                MachineIcon.ANVIL_REWARD_VALUE.item("&fСрок LuckPerms"),
                value -> value == null || value.trim().isBlank() || value.contains(" ")
                        ? "Например: 7d или 30d" : null,
                value -> {
                    updateReward(player, definition.name(), index, map -> {
                        Map<String, Object> section = map.get("luckperms") instanceof Map<?, ?> configured
                                ? deepMap(configured) : new LinkedHashMap<>();
                        section.put("duration", value.trim());
                        map.put("luckperms", section);
                    });
                    openEditor(player, definition.name(), index);
                }, () -> openEditor(player, definition.name(), index));
    }

    private void handleMessage(Player player, InventoryClickEvent event, CaseDefinition definition,
                               int index, Reward reward) {
        if (event.isRightClick()) {
            if (updateReward(player, definition.name(), index, map -> {
                map.remove("message");
                removeNestedMessage(map, reward.type());
            })) {
                openEditor(player, definition.name(), index);
            }
            return;
        }
        String current = reward.message() == null ? "&aВы получили {reward}!" : reward.message();
        anvil.open(player, "&0Сообщение награды", current,
                MachineIcon.ANVIL_REWARD_NAME.item("&fСообщение игроку"),
                value -> value == null || value.trim().isBlank() ? "Введите сообщение" : null,
                value -> {
                    updateReward(player, definition.name(), index, map -> {
                        removeNestedMessage(map, reward.type());
                        map.put("message", value.trim());
                    });
                    openEditor(player, definition.name(), index);
                }, () -> openEditor(player, definition.name(), index));
    }

    private void startAddCommand(Player player, CaseDefinition definition, int index) {
        anvil.open(player, "&0Добавить команду", "say {player} получил награду",
                MachineIcon.ANVIL_REWARD_VALUE.item("&fКоманда консоли"),
                value -> value == null || value.trim().replaceFirst("^/", "").isBlank()
                        ? "Введите команду" : null,
                value -> {
                    updateReward(player, definition.name(), index, map -> {
                        List<String> commands = configuredCommands(map);
                        String command = value.trim().replaceFirst("^/", "");
                        if (!commands.contains(command)) commands.add(command);
                        map.remove("command");
                        map.put("commands", commands);
                    });
                    openEditor(player, definition.name(), index);
                }, () -> openEditor(player, definition.name(), index));
    }

    private void removeLastCommand(Player player, CaseDefinition definition, int index, Reward reward) {
        if (reward.commands().size() <= 1) {
            player.sendMessage(ColorUtil.colorize("&#FC7165В награде должна остаться хотя бы одна команда."));
            return;
        }
        if (updateReward(player, definition.name(), index, map -> {
            List<String> commands = configuredCommands(map);
            if (commands.size() > 1) commands.remove(commands.size() - 1);
            map.remove("command");
            map.put("commands", commands);
        })) openEditor(player, definition.name(), index);
    }

    private boolean updateChance(Player player, String caseName, int index, String value) {
        Double percent = positiveDouble(value.replace("%", ""));
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (percent == null || percent > 100.0 || definition == null) return false;
        int other = 0;
        for (int i = 0; i < definition.rewards().size(); i++) {
            if (i != index) other += Math.max(1, definition.rewards().get(i).chance());
        }
        final int chance = chanceForPercent(other, percent);
        return updateReward(player, caseName, index, reward -> reward.put("chance", chance));
    }

    private boolean updateBase64Icon(Player player, PendingTexture pending, String value) {
        String texture = HeadUtil.normalizeTexture(value);
        if (texture == null) return false;
        return updateReward(player, pending.caseName(), pending.index(), reward -> {
            Map<String, Object> icon = new LinkedHashMap<>();
            icon.put("base64", texture);
            icon.put("name", reward.getOrDefault("name", "&fНаграда"));
            reward.put("visual", icon);
        });
    }

    private boolean createReward(Player player, String caseName, Reward.Type type, String value) {
        Map<String, Object> reward = newReward(type, value);
        return reward != null && appendReward(player, caseName, reward);
    }

    private void changeRarity(Player player, CaseDefinition definition, int index,
                              Reward reward, boolean backwards) {
        if (!caseManager.getPlugin().rarityEnabled()) return;
        Reward.Rarity[] values = Reward.Rarity.values();
        int current = reward.rarity().ordinal();
        int next = backwards ? (current - 1 + values.length) % values.length : (current + 1) % values.length;
        if (updateReward(player, definition.name(), index, map -> map.put("rarity", values[next].name()))) {
            openEditor(player, definition.name(), index);
        }
    }

    private void replaceVisual(Player player, InventoryClickEvent event, CaseDefinition definition,
                               int index, Reward reward) {
        ItemStack cursor = event.getCursor();
        if (!ItemFactory.isRealItem(cursor)) {
            player.sendMessage(ColorUtil.colorize("&#FC7165Положите новый предмет на курсор."));
            return;
        }
        if (updateReward(player, definition.name(), index, map -> map.put("visual", ItemFactory.toMap(cursor)))) {
            openIconEditor(player, definition, index);
        }
    }

    private void deleteReward(Player player, CaseDefinition definition, int index) {
        if (definition.rewards().size() <= 1) {
            player.sendMessage(ColorUtil.colorize("&#FC7165В кейсе должна остаться хотя бы одна награда."));
            openEditor(player, definition.name(), index);
            return;
        }
        boolean saved = support.update(player, definition.name(), root -> {
            List<Map<String, Object>> rewards = rewardMaps(root);
            if (index >= 0 && index < rewards.size()) rewards.remove(index);
            root.set("rewards", rewards);
        });
        if (saved) open(player, definition.name(), Math.max(0, (index - 1) / CONTENT_SLOTS.size()), false);
    }

    private boolean appendReward(Player player, String caseName, Map<String, Object> reward) {
        return support.update(player, caseName, root -> {
            List<Map<String, Object>> rewards = rewardMaps(root);
            rewards.add(reward);
            root.set("rewards", rewards);
        });
    }

    private boolean updateReward(Player player, String caseName, int index,
                                 java.util.function.Consumer<Map<String, Object>> updater) {
        return support.update(player, caseName, root -> {
            List<Map<String, Object>> rewards = rewardMaps(root);
            if (index < 0 || index >= rewards.size()) throw new IllegalArgumentException("reward index");
            updater.accept(rewards.get(index));
            root.set("rewards", rewards);
        });
    }

    private record PendingTexture(String caseName, int index, Reward.Type type) {
    }
}
