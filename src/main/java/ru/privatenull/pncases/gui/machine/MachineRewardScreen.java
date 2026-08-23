package ru.privatenull.pncases.gui.machine;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pncases.gui.GuiSession;
import ru.privatenull.pnlibrary.text.ColorUtil;
import ru.privatenull.pnlibrary.item.HeadUtil;
import ru.privatenull.pnlibrary.item.ItemFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class MachineRewardScreen {

    private static final List<Integer> CONTENT_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    );
    private static final int SLOT_BACK = 45;
    private static final int SLOT_ADD_ITEM = 46;
    private static final int SLOT_ADD_VAULT = 47;
    private static final int SLOT_ADD_POINTS = 48;
    private static final int SLOT_INFO = 53;
    private static final int SLOT_ADD_LUCKPERMS = 49;
    private static final int SLOT_ADD_PERSONAL = 50;
    private static final int SLOT_ADD_PERMISSION = 51;
    private static final int SLOT_PREVIOUS = 52;
    private static final int SLOT_NEXT = 44;

    private static final int EDIT_CHANCE = 11;
    private static final int EDIT_RARITY = 13;
    private static final int EDIT_VISUAL = 15;
    private static final int EDIT_VALUE = 20;
    private static final int EDIT_MOVE = 22;
    private static final int EDIT_DELETE = 24;
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
    private final Map<UUID, PendingTexture> pendingTextures = new ConcurrentHashMap<>();

    MachineRewardScreen(CaseManager caseManager, MachineGuiSupport support,
                        MachineMainScreen mainScreen, MachineItemFactory items) {
        this.caseManager = caseManager;
        this.support = support;
        this.mainScreen = mainScreen;
        this.items = items;
        this.anvil = new MachineAnvilInput(caseManager.getPlugin());
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
        int pages = Math.max(1, (definition.rewards().size() + CONTENT_SLOTS.size() - 1) / CONTENT_SLOTS.size());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
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
        Inventory inventory = Bukkit.createInventory(
                GuiSession.machineReward(caseName, index), 45,
                ColorUtil.colorize("&0" + titleText(plainRewardName(definition.rewards().get(index))))
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
            default -> { }
        }
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
            case EDIT_CHANCE -> startEditChance(player, definition, index, reward);
            case EDIT_RARITY -> changeRarity(player, definition, index, reward, event.isRightClick());
            case EDIT_VISUAL -> openIconEditor(player, definition, index);
            case EDIT_VALUE -> startEditValue(player, event, definition, index, reward);
            case EDIT_MOVE -> openMoveScreen(player, definition, index, index / CONTENT_SLOTS.size());
            case EDIT_DELETE -> openDeleteConfirm(player, definition, index);
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
        Bukkit.getScheduler().runTask(caseManager.getPlugin(), () -> {
            if (value.equalsIgnoreCase("cancel") || value.equalsIgnoreCase("отмена")) {
                openEditor(event.getPlayer(), pending.caseName(), pending.index());
                return;
            }
            PendingChat input = new PendingChat(pending.caseName(), pending.index(), pending.type(), ChatAction.ICON_BASE64);
            if (!updateBase64Icon(event.getPlayer(), input, value)) {
                event.getPlayer().sendMessage(ColorUtil.colorize("&#FC7165Текстура не распознана. Используйте Base64, URL или хеш."));
            }
            openEditor(event.getPlayer(), pending.caseName(), pending.index());
        });
        return true;
    }

    void cancel(Player player) {
        pendingTextures.remove(player.getUniqueId());
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
        int pages = Math.max(1, (definition.rewards().size() + CONTENT_SLOTS.size() - 1) / CONTENT_SLOTS.size());
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
                meta.setDisplayName(ColorUtil.colorize(items.name(plainRewardName(reward))));
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
                        "Награда", plainRewardName(reward),
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
        inventory.setItem(SLOT_ADD_ITEM, addButton(MachineIcon.REWARDS_ADD_ITEM, "Предмет", "Возьмите предмет на курсор и добавьте его в пул."));
        inventory.setItem(SLOT_ADD_VAULT, addButton(MachineIcon.REWARDS_ADD_COINS, "Монеты", "Создайте денежную выплату через Vault."));
        inventory.setItem(SLOT_ADD_POINTS, addButton(MachineIcon.REWARDS_ADD_POINTS, "Поинты", "Добавьте баланс PlayerPoints."));
        inventory.setItem(SLOT_ADD_LUCKPERMS, addButton(MachineIcon.REWARDS_ADD_GROUP, "Привилегия", "Выдайте игроку группу LuckPerms."));
        inventory.setItem(SLOT_ADD_PERSONAL, addButton(MachineIcon.REWARDS_ADD_COMMAND, "Команда", "Выполните собственную команду от консоли."));
        inventory.setItem(SLOT_ADD_PERMISSION, addButton(MachineIcon.REWARDS_ADD_PERMISSION, "Право", "Выдайте отдельный permission LuckPerms."));
        inventory.setItem(SLOT_INFO, items.builtInButton(MachineIcon.REWARDS_INFO,
                items.name("Награды"), items.lore(
                        List.of("Управление содержимым кейса"),
                        List.of(
                                "Количество", String.valueOf(definition.rewards().size()),
                                "Страница", (page + 1) + " / " + pages
                        ), null, null)));
        if (page > 0) inventory.setItem(SLOT_PREVIOUS, items.builtInButton(MachineIcon.REWARDS_PREVIOUS, "&#65D1FCПредыдущая", List.of()));
        if (page + 1 < pages) inventory.setItem(SLOT_NEXT, items.builtInButton(MachineIcon.REWARDS_NEXT, "&#65D1FCСледующая", List.of()));
    }

    private void fillEditor(Inventory inventory, CaseDefinition definition, int index) {
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        Reward reward = definition.rewards().get(index);
        inventory.setItem(4, rewardButton(definition, reward, index, definition.rewards().stream().mapToInt(Reward::chance).sum()));
        inventory.setItem(EDIT_CHANCE, items.builtInButton(MachineIcon.REWARD_CHANCE,
                items.name("Шанс выпадения"), items.lore(
                        "Вероятность получить эту награду",
                        "Шанс", chancePercent(reward, definition) + "%",
                        "Изменить", null)));
        boolean rarityEnabled = caseManager.getPlugin().rarityEnabled();
        inventory.setItem(EDIT_RARITY, items.builtInButton(MachineIcon.REWARD_RARITY,
                items.name("Редкость"), items.lore(
                        rarityEnabled ? "Оформление награды в интерфейсе игрока" : "Функция редкостей отключена",
                        "Редкость", caseManager.getRewardPresentation().formattedRarity(reward),
                        rarityEnabled ? "Следующая" : null, rarityEnabled ? "Предыдущая" : null)));
        inventory.setItem(EDIT_VISUAL, items.builtInButton(MachineIcon.REWARD_VISUAL,
                items.name("Оформление"), items.lore(
                        List.of("Иконка и название награды"),
                        List.of("Название", plainRewardName(reward), "Иконка", itemName(reward.visualItem())),
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
                        "Награда", plainRewardName(reward),
                        "Продолжить", null)));
        inventory.setItem(EDIT_BACK, items.backButton(definition));
    }

    private ItemStack rewardButton(CaseDefinition definition, Reward reward, int index, int totalChance) {
        ItemStack item = caseManager.getRewardPresentation().buildPreviewItem(definition, reward, totalChance);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        String name = plainRewardName(reward);
        meta.setDisplayName(ColorUtil.colorize(items.name(name)));
        List<String> parameters = new ArrayList<>(List.of(
                "Выдача", rewardSummary(reward),
                "Шанс", chancePercent(reward, definition) + "%"));
        if (caseManager.getPlugin().rarityEnabled()) parameters.addAll(List.of(
                "Редкость", caseManager.getRewardPresentation().formattedRarity(reward)));
        meta.setLore(items.lore(List.of(rewardDescription(reward)), parameters, "Изменить", null)
                .stream().map(ColorUtil::colorize).toList());
        item.setItemMeta(meta);
        return item;
    }

    private void openIconEditor(Player player, CaseDefinition definition, int index) {
        CaseDefinition current = caseManager.getCaseByName(definition.name());
        if (current != null) definition = current;
        Reward reward = definition.rewards().get(index);
        Inventory inventory = Bukkit.createInventory(
                GuiSession.machineRewardIcon(definition.name(), index), 27,
                ColorUtil.colorize("&0Образ награды | " + titleText(plainRewardName(reward))));
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
                        "Название", plainRewardName(reward),
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
        if (currentName == null || currentName.isBlank()) currentName = plainRewardName(reward);
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
        reward.put("item", itemMap(source));
        if (appendReward(player, definition.name(), reward)) {
            CaseDefinition updated = caseManager.getCaseByName(definition.name());
            int index = updated == null ? 0 : Math.max(0, updated.rewards().size() - 1);
            openEditor(player, definition.name(), index);
        }
    }

    private void startCreate(Player player, CaseDefinition definition, Reward.Type type) {
        PendingChat input = new PendingChat(definition.name(), -1, type, ChatAction.CREATE);
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
                    if (createFromChat(player, input, value)) open(player, definition.name());
                    else open(player, definition.name());
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
            if (updateReward(player, definition.name(), index, map -> map.put("item", itemMap(cursor)))) {
                openEditor(player, definition.name(), index);
            }
            return;
        }
        PendingChat input = new PendingChat(definition.name(), index, reward.type(), ChatAction.VALUE);
        anvil.open(player, "&0" + valueTitle(reward), currentValue(reward),
                MachineIcon.ANVIL_REWARD_VALUE.item("&fЗначение награды"),
                value -> inputError(reward.type(), value),
                value -> {
                    updateValue(player, input, value);
                    openEditor(player, definition.name(), index);
                },
                () -> openEditor(player, definition.name(), index));
    }

    private void startEditChance(Player player, CaseDefinition definition, int index, Reward reward) {
        PendingChat input = new PendingChat(definition.name(), index, reward.type(), ChatAction.CHANCE);
        anvil.open(player, "&0Шанс выпадения", chancePercent(reward, definition),
                MachineIcon.ANVIL_CHANCE.item("&fШанс выпадения"), value -> {
                    Double percent = positiveDouble(value.replace("%", ""));
                    return percent == null || percent > 100.0 ? "От 0.01 до 100" : null;
                }, value -> {
                    updateChance(player, input, value);
                    openEditor(player, definition.name(), index);
                }, () -> openEditor(player, definition.name(), index));
    }

    private boolean updateChance(Player player, PendingChat pending, String value) {
        Double percent = positiveDouble(value.replace("%", ""));
        CaseDefinition definition = caseManager.getCaseByName(pending.caseName());
        if (percent == null || percent > 100.0 || definition == null) return false;
        int other = 0;
        for (int i = 0; i < definition.rewards().size(); i++) {
            if (i != pending.index()) other += Math.max(1, definition.rewards().get(i).chance());
        }
        final int chance;
        if (other == 0) chance = 100;
        else {
            double safePercent = Math.min(99.99, percent);
            chance = (int) Math.min(Integer.MAX_VALUE,
                    Math.max(1L, Math.round(other * safePercent / (100.0 - safePercent))));
        }
        return updateReward(player, pending.caseName(), pending.index(), reward -> reward.put("chance", chance));
    }

    private boolean updateBase64Icon(Player player, PendingChat pending, String value) {
        String texture = HeadUtil.normalizeTexture(value);
        if (texture == null) return false;
        return updateReward(player, pending.caseName(), pending.index(), reward -> {
            Map<String, Object> icon = new LinkedHashMap<>();
            icon.put("base64", texture);
            icon.put("name", reward.getOrDefault("name", "&fНаграда"));
            reward.put("visual", icon);
        });
    }

    private boolean createFromChat(Player player, PendingChat pending, String value) {
        Map<String, Object> reward = baseReward(pending.type());
        switch (pending.type()) {
            case VAULT -> {
                Double amount = positiveDouble(value);
                if (amount == null) return false;
                reward.put("vault", Map.of("amount", amount));
                reward.put("visual", Map.of("material", "EMERALD", "name", "&a" + amount + " монет"));
            }
            case PLAYERPOINTS -> {
                Integer amount = positiveInteger(value);
                if (amount == null) return false;
                reward.put("playerpoints", Map.of("amount", amount));
                reward.put("visual", Map.of("material", "EMERALD", "name", "&b" + amount + " поинтов"));
            }
            case LUCKPERMS -> {
                reward.put("luckperms", Map.of("group", value.trim()));
                reward.put("visual", Map.of("material", "NAME_TAG", "name", "&d" + value.trim()));
            }
            case PERSONAL -> {
                String command = value.startsWith("/") ? value.substring(1) : value;
                if (command.isBlank()) return false;
                reward.put("command", command);
                reward.put("name", "&eКомандная награда");
                reward.put("visual", Map.of("material", "COMMAND_BLOCK", "name", "&eКомандная награда"));
            }
            case ITEM -> { return false; }
        }
        return appendReward(player, pending.caseName(), reward);
    }

    private boolean updateValue(Player player, PendingChat pending, String value) {
        return updateReward(player, pending.caseName(), pending.index(), reward -> {
            switch (pending.type()) {
                case VAULT -> {
                    Double amount = positiveDouble(value);
                    if (amount == null) throw new IllegalArgumentException("amount");
                    reward.put("vault", Map.of("amount", amount));
                }
                case PLAYERPOINTS -> {
                    Integer amount = positiveInteger(value);
                    if (amount == null) throw new IllegalArgumentException("amount");
                    reward.put("playerpoints", Map.of("amount", amount));
                }
                case LUCKPERMS -> {
                    Map<String, Object> current = reward.get("luckperms") instanceof Map<?, ?> map
                            ? deepMap(map) : new LinkedHashMap<>();
                    if (current.containsKey("node") && !current.containsKey("group")) current.put("node", value.trim());
                    else current.put("group", value.trim());
                    reward.put("luckperms", current);
                }
                case PERSONAL -> reward.put("command", value.startsWith("/") ? value.substring(1) : value);
                case ITEM -> throw new IllegalArgumentException("item");
            }
        });
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
        if (updateReward(player, definition.name(), index, map -> map.put("visual", itemMap(cursor)))) {
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

    private static Map<String, Object> baseReward(Reward.Type type) {
        Map<String, Object> reward = new LinkedHashMap<>();
        reward.put("type", type.name());
        reward.put("chance", 100);
        reward.put("rarity", "COMMON");
        return reward;
    }

    private static Map<String, Object> itemMap(ItemStack source) {
        return ItemFactory.toMap(source);
    }

    private static List<Map<String, Object>> rewardMaps(ConfigurationSection root) {
        List<Map<String, Object>> rewards = new ArrayList<>();
        for (Map<?, ?> map : root.getMapList("rewards")) rewards.add(deepMap(map));
        return rewards;
    }

    private static Map<String, Object> deepMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) continue;
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) value = deepMap(nested);
            else if (value instanceof List<?> list) value = new ArrayList<>(list);
            copy.put(String.valueOf(entry.getKey()), value);
        }
        return copy;
    }

    private static Integer positiveInteger(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Double positiveDouble(String value) {
        try {
            double parsed = Double.parseDouble(value.replace(',', '.'));
            return Double.isFinite(parsed) && parsed > 0.0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<String> valueLore(Reward reward) {
        String action = reward.type() == Reward.Type.ITEM ? "Заменить с курсора" : "Изменить";
        return items.lore(rewardDescription(reward), "Значение", rewardSummary(reward), action, null);
    }

    private static String valueTitle(Reward reward) {
        return switch (reward.type()) {
            case ITEM -> "Выдаваемый предмет";
            case VAULT -> "Монеты";
            case PLAYERPOINTS -> "Поинты";
            case LUCKPERMS -> reward.lpGroup() != null ? "Привилегия" : "Право";
            case PERSONAL -> "Команда";
        };
    }

    private String plainRewardName(Reward reward) {
        String name = reward.displayName();
        if (name == null || name.isBlank()) name = itemName(reward.visualItem());
        String plain = org.bukkit.ChatColor.stripColor(ColorUtil.colorize(name));
        return plain == null || plain.isBlank() ? "Награда" : plain;
    }

    private static String titleText(String value) {
        return value.length() <= 20 ? value : value.substring(0, 19) + "…";
    }

    private String rewardSummary(Reward reward) {
        return switch (reward.type()) {
            case ITEM -> "Предмет: &#FCA865" + itemName(reward.item())
                    + " &7× &#FCA865" + (reward.item() == null ? 0 : reward.item().getAmount());
            case VAULT -> "Монеты: &#FCA865" + reward.vaultAmount();
            case PLAYERPOINTS -> "Поинты: &#FCA865" + reward.playerPointsAmount();
            case LUCKPERMS -> reward.lpGroup() != null
                    ? "Привилегия: &#FCA865" + reward.lpGroup()
                    + (reward.lpDuration() == null ? "" : " &7на &#FCA865" + reward.lpDuration())
                    : "Право: &#FCA865" + reward.lpNode();
            case PERSONAL -> "Команда: &#FCA865" + (reward.command() == null ? "не задана" : reward.command());
        };
    }

    private String itemName(ItemStack item) {
        return item == null || item.getType().isAir()
                ? "нет"
                : caseManager.getPlugin().getItemLocalization().getPlainName(item);
    }

    private static String rewardDescription(Reward reward) {
        return switch (reward.type()) {
            case ITEM -> "Предмет будет выдан игроку.";
            case VAULT -> "Монеты будут зачислены через Vault.";
            case PLAYERPOINTS -> "Поинты будут зачислены игроку.";
            case LUCKPERMS -> reward.lpGroup() != null
                    ? "Привилегия будет выдана через LuckPerms."
                    : "Право будет выдано через LuckPerms.";
            case PERSONAL -> "Команда будет выполнена от консоли.";
        };
    }

    private static String chancePercent(Reward reward, CaseDefinition definition) {
        int total = definition.rewards().stream().mapToInt(value -> Math.max(0, value.chance())).sum();
        if (total <= 0) return "0";
        double percent = reward.chance() * 100.0 / total;
        String formatted = String.format(Locale.US, "%.2f", percent);
        return formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String currentValue(Reward reward) {
        return switch (reward.type()) {
            case ITEM -> "";
            case VAULT -> String.valueOf(reward.vaultAmount());
            case PLAYERPOINTS -> String.valueOf(reward.playerPointsAmount());
            case LUCKPERMS -> reward.lpGroup() != null ? reward.lpGroup() : String.valueOf(reward.lpNode());
            case PERSONAL -> reward.command() == null ? "" : reward.command();
        };
    }

    private static String inputError(Reward.Type type, String value) {
        if (value == null || value.isBlank()) return "Введите значение";
        return switch (type) {
            case VAULT -> positiveDouble(value) == null ? "Введите число" : null;
            case PLAYERPOINTS -> positiveInteger(value) == null ? "Введите целое число" : null;
            case LUCKPERMS -> value.contains(" ") ? "Только название" : null;
            case PERSONAL -> value.equals("/") ? "Введите команду" : null;
            case ITEM -> null;
        };
    }

    private enum ChatAction { CREATE, VALUE, CHANCE, ICON_BASE64 }

    private record PendingChat(String caseName, int index, Reward.Type type, ChatAction action) {
    }

    private record PendingTexture(String caseName, int index, Reward.Type type) {
    }
}
