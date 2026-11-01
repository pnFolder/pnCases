package ru.privatenull.pncases.gui.gift;

import ru.privatenull.pncases.integration.Log;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.gui.GuiDesign;
import ru.privatenull.pncases.support.compat.BukkitCompat.InventoryViewCompat;
import ru.privatenull.pncases.support.text.ColorUtil;

import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Player-facing key transfer menu opened by /pncases gift <player>. */
public final class KeyGiftMenu implements Listener {

    private static final List<Integer> CONTENT_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43);
    private static final int LIMITS_INFO = 4;
    private static final int CHANGE_PLAYER = 47;
    private static final int PREVIOUS = 45;
    private static final int CLOSE = 49;
    private static final int NEXT = 53;

    private final CaseManager caseManager;
    private final Set<UUID> transfers = new HashSet<>();

    public KeyGiftMenu(CaseManager caseManager) {
        this.caseManager = caseManager;
    }

    public void open(Player sender, Player target) {
        if (transfers.contains(sender.getUniqueId())) return;
        loadAndOpen(sender, target.getUniqueId(), target.getName(), 0);
    }

    public static boolean owns(Object holder) {
        return holder instanceof GiftHolder || holder instanceof PickerHolder;
    }

    /** The list of online players to choose who gets the gift. */
    public void openPicker(Player sender, int requestedPage) {
        if (transfers.contains(sender.getUniqueId())) return;
        List<Player> candidates = Bukkit.getOnlinePlayers().stream()
                .filter(online -> !online.getUniqueId().equals(sender.getUniqueId()))
                .sorted(java.util.Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .map(online -> (Player) online)
                .toList();
        int pages = Math.max(1, (candidates.size() + CONTENT_SLOTS.size() - 1) / CONTENT_SLOTS.size());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        List<UUID> ids = candidates.stream().map(Player::getUniqueId).toList();
        PickerHolder holder = new PickerHolder(sender.getUniqueId(), page, ids);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                caseManager.getPlugin().getUi().text("gift.picker.title", "&0Кому подарить ключи?"));
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
        int start = page * CONTENT_SLOTS.size();
        for (int position = 0; position < CONTENT_SLOTS.size(); position++) {
            int index = start + position;
            if (index >= candidates.size()) break;
            Player candidate = candidates.get(index);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta skull) {
                skull.setOwningPlayer(candidate);
                skull.setDisplayName(ColorUtil.colorize("&#5FD7FF" + candidate.getName()));
                skull.setLore(List.of("", ColorUtil.colorize(" &#FFB347[&#FFA43C Л&#FF9536К&#FF8630М &#FF772A] &fВыбрать игрока"), ""));
                head.setItemMeta(skull);
            }
            inventory.setItem(CONTENT_SLOTS.get(position), head);
        }
        if (candidates.isEmpty()) {
            inventory.setItem(22, item(Material.BARRIER, "&#FC7165Нет других игроков онлайн",
                    List.of("", " &#A6B0BAКлючи можно дарить только тем, кто на сервере", "")));
        }
        inventory.setItem(CLOSE, GuiDesign.head(GuiDesign.BACK, "&#FC7165Закрыть"));
        if (page > 0) inventory.setItem(PREVIOUS, item(Material.ARROW, "&#65D1FCПредыдущая страница", List.of()));
        if (page + 1 < pages) inventory.setItem(NEXT, item(Material.ARROW, "&#65D1FCСледующая страница", List.of()));
        caseManager.getPlugin().openGui(sender, inventory);
        sender.playSound(sender.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.25f, 1.2f);
    }

    @EventHandler
    public void onPickerClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player sender)) return;
        if (!(event.getInventory().getHolder() instanceof PickerHolder holder)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize() || !event.isLeftClick()) return;
        if (!holder.senderId.equals(sender.getUniqueId())
                || !sender.hasPermission(caseManager.getPlugin().giftPermission())) {
            sender.closeInventory();
            sender.sendMessage(caseManager.getPlugin().getUi().get("no-permission"));
            return;
        }
        if (slot == CLOSE) {
            sender.closeInventory();
        } else if (slot == PREVIOUS && holder.page > 0) {
            openPicker(sender, holder.page - 1);
        } else if (slot == NEXT) {
            openPicker(sender, holder.page + 1);
        } else {
            int position = CONTENT_SLOTS.indexOf(slot);
            int index = holder.page * CONTENT_SLOTS.size() + position;
            if (position < 0 || index >= holder.ids.size()) return;
            Player target = Bukkit.getPlayer(holder.ids.get(index));
            if (target == null || !target.isOnline()) {
                sender.sendMessage(caseManager.getPlugin().getUi().get("gift-player-offline"));
                openPicker(sender, holder.page);
                return;
            }
            open(sender, target);
        }
    }

    private List<String> limitLines(Player sender) {
        var limits = caseManager.getPlugin().getGiftLimits();
        var limit = limits.limitFor(sender);
        String perGift = limit.perGiftLimited() ? String.valueOf(limit.perGift()) : "без ограничений";
        String perDay = limit.perDayLimited()
                ? limits.remainingToday(sender) + " из " + limit.perDay() : "без ограничений";
        return List.of("", " &#C2CBD3За один подарок", " &#69747E↳ &#A7F3D0" + perGift,
                " &#C2CBD3Сегодня можно подарить", " &#69747E↳ &#A7F3D0" + perDay, "");
    }

    private void loadAndOpen(Player sender, UUID targetId, String targetName, int requestedPage) {
        UUID senderId = sender.getUniqueId();
        Inventory previous = InventoryViewCompat.topInventory(sender);
        caseManager.getPlugin().getDatabaseExecutor().completeSync(
                caseManager.getPlugin().getDatabaseExecutor().supply(senderId,
                        () -> caseManager.getPlugin().getDatabase().getAllKeys(senderId)),
                balances -> {
                    if (!sender.isOnline() || !sender.hasPermission(caseManager.getPlugin().giftPermission())
                            || InventoryViewCompat.topInventory(sender) != previous) return;
                    balances.forEach((id, amount) -> caseManager.updateCachedKeyBalance(senderId, id, amount));
                    List<String> keyIds = caseManager.getCases().stream()
                            .map(CaseDefinition::name)
                            .filter(caseManager::giftable)
                            .filter(id -> balances.getOrDefault(id, 0) > 0)
                            .toList();
                    int pages = Math.max(1, (keyIds.size() + CONTENT_SLOTS.size() - 1) / CONTENT_SLOTS.size());
                    int page = Math.max(0, Math.min(requestedPage, pages - 1));
                    GiftHolder holder = new GiftHolder(senderId, targetId, targetName, page,
                            keyIds, Map.copyOf(balances), limitLines(sender));
                    Inventory inventory = Bukkit.createInventory(holder, 54,
                            caseManager.getPlugin().getUi().text("gift.title",
                                    "&0Подарить ключи: {player}", "player", targetName));
                    render(inventory, holder, pages);
                    caseManager.getPlugin().openGui(sender, inventory);
                    sender.playSound(sender.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.25f, 1.2f);
                },
                failure -> sender.sendMessage(caseManager.getPlugin().getUi().get("gift-storage-error")));
    }

    private void render(Inventory inventory, GiftHolder holder, int pages) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
        int start = holder.page * CONTENT_SLOTS.size();
        for (int position = 0; position < CONTENT_SLOTS.size(); position++) {
            int index = start + position;
            if (index >= holder.keyIds.size()) break;
            String keyId = holder.keyIds.get(index);
            CaseDefinition definition = caseManager.getCaseByName(keyId);
            if (definition == null) continue;
            int amount = holder.balances.getOrDefault(keyId, 0);
            ItemStack button = definition.openButton().clone();
            button.setAmount(Math.max(1, Math.min(64, amount)));
            ItemMeta meta = button.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(caseManager.getPlugin().getUi().text("gift.case.name",
                        "&#5FD7FF{case}", "case", definition.displayName(), "case_id", keyId));
                meta.setLore(caseManager.getPlugin().getUi().list("gift.case.lore", List.of(
                        "", " &#A6B0BAПередача ключей игроку &#A7F3D0{player}", "",
                        " &#C2CBD3Доступно", " &#69747E↳ &#A7F3D0{amount}", "",
                        " &#FFB347[&#FFA43C Л&#FF9536К&#FF8630М &#FF772A] &fПодарить 1",
                        " &#FFB347[&#FFA43C SHIFT + ЛКМ &#FF772A] &fПодарить 5",
                        " &#FF8A8A[&#FF7979 П&#F96868К&#F25757М &#EB4646] &fПодарить все", ""),
                        "player", holder.targetName, "amount", String.valueOf(amount),
                        "case", definition.displayName(), "case_id", keyId));
                button.setItemMeta(meta);
            }
            inventory.setItem(CONTENT_SLOTS.get(position), button);
        }
        if (holder.keyIds.isEmpty()) {
            // The player may own keys whose cases are all closed for gifting; that is a different message.
            boolean ownsLockedKeys = holder.balances.entrySet().stream()
                    .anyMatch(entry -> entry.getValue() > 0 && caseManager.keyExists(entry.getKey())
                            && !caseManager.giftable(entry.getKey()));
            if (ownsLockedKeys) {
                inventory.setItem(22, item(Material.BARRIER,
                        caseManager.getPlugin().getUi().text("gift.locked.name", "&#FC7165Передача отключена"),
                        caseManager.getPlugin().getUi().list("gift.locked.lore", List.of(
                                "", " &#A6B0BAВаши ключи нельзя передавать другим игрокам", ""))));
            } else {
                inventory.setItem(22, item(Material.CHEST,
                        caseManager.getPlugin().getUi().text("gift.empty.name", "&#5FD7FFНет ключей"),
                        caseManager.getPlugin().getUi().list("gift.empty.lore", List.of(
                                "", " &#A6B0BAЗдесь появятся ваши ключи для подарка", ""))));
            }
        }
        inventory.setItem(LIMITS_INFO, item(Material.PAPER, "&#FFE66DВаши лимиты", holder.limitLines));
        inventory.setItem(CHANGE_PLAYER, item(Material.PLAYER_HEAD, "&#65D1FCСменить игрока",
                List.of("", " &#A6B0BAСейчас: &#A7F3D0" + holder.targetName, "")));
        inventory.setItem(CLOSE, GuiDesign.head(GuiDesign.BACK, "&#FC7165Закрыть"));
        if (holder.page > 0) inventory.setItem(PREVIOUS,
                item(Material.ARROW, "&#65D1FCПредыдущая страница", List.of()));
        if (holder.page + 1 < pages) inventory.setItem(NEXT,
                item(Material.ARROW, "&#65D1FCСледующая страница", List.of()));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player sender)) return;
        if (!(event.getInventory().getHolder() instanceof GiftHolder holder)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;
        if (!holder.senderId.equals(sender.getUniqueId())
                || !sender.hasPermission(caseManager.getPlugin().giftPermission())) {
            sender.closeInventory();
            sender.sendMessage(caseManager.getPlugin().getUi().get("no-permission"));
            return;
        }
        if (!event.isLeftClick() && !event.isRightClick()) return;
        if (transfers.contains(holder.senderId)) return;
        if (slot == CLOSE) {
            sender.closeInventory();
            return;
        }
        if (slot == CHANGE_PLAYER) {
            openPicker(sender, 0);
            return;
        }
        if (slot == PREVIOUS && holder.page > 0) {
            loadAndOpen(sender, holder.targetId, holder.targetName, holder.page - 1);
            return;
        }
        if (slot == NEXT) {
            loadAndOpen(sender, holder.targetId, holder.targetName, holder.page + 1);
            return;
        }
        int position = CONTENT_SLOTS.indexOf(slot);
        if (position < 0) return;
        int index = holder.page * CONTENT_SLOTS.size() + position;
        if (index >= holder.keyIds.size()) return;
        String keyId = holder.keyIds.get(index);
        if (!caseManager.keyExists(keyId)) return;
        int have = holder.balances.getOrDefault(keyId, 0);
        int amount = event.isRightClick() ? have : event.isShiftClick() ? 5 : 1;
        if (amount <= 0) return;
        transfer(sender, holder, keyId, amount);
    }

    private void transfer(Player sender, GiftHolder holder, String keyId, int requested) {
        // Checked again here: the menu may have been opened before the administrator closed the case for gifts.
        if (!caseManager.giftable(keyId)) {
            sender.playSound(sender.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.2f, 1.0f);
            sender.sendMessage(caseManager.getPlugin().getUi().text("gift-case-locked",
                    "&#FC7165Ключи этого кейса нельзя передавать.", "case", keyId));
            return;
        }
        var limits = caseManager.getPlugin().getGiftLimits();
        int amount = limits.allowed(sender, requested);
        if (amount <= 0) {
            var limit = limits.limitFor(sender);
            sender.playSound(sender.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.2f, 1.0f);
            sender.sendMessage(caseManager.getPlugin().getUi().get(
                    limit.perGiftLimited() && limit.perGift() == 0 ? "gift-limit-none" : "gift-limit-day",
                    "used", String.valueOf(limits.sentToday(sender.getUniqueId())),
                    "limit", String.valueOf(limit.perDay())));
            return;
        }
        if (amount < requested) {
            sender.sendMessage(caseManager.getPlugin().getUi().get("gift-limit-clamped",
                    "amount", String.valueOf(amount), "requested", String.valueOf(requested)));
        }
        if (!transfers.add(holder.senderId)) return;
        sender.closeInventory();
        caseManager.getPlugin().getDatabaseExecutor().completeSync(
                caseManager.getPlugin().getDatabaseExecutor().supply(holder.senderId,
                        () -> caseManager.getPlugin().getDatabase().transferKeys(
                                holder.senderId, holder.targetId, keyId, amount)),
                transferred -> {
                    transfers.remove(holder.senderId);
                    if (!transferred) {
                        sender.sendMessage(caseManager.getPlugin().getUi().get("gift-not-enough"));
                        if (sender.isOnline()) loadAndOpen(sender, holder.targetId, holder.targetName, holder.page);
                        return;
                    }
                    limits.record(holder.senderId, amount);
                    String keyName = caseManager.getKeyDisplayName(keyId);
                    sender.sendMessage(caseManager.getPlugin().getUi().get("gift-success-sender",
                            "player", holder.targetName, "amount", String.valueOf(amount), "key_name", keyName));
                    Player target = Bukkit.getPlayer(holder.targetId);
                    if (target != null) target.sendMessage(caseManager.getPlugin().getUi().get("gift-success-target",
                            "player", sender.getName(), "amount", String.valueOf(amount), "key_name", keyName));
                    refreshBalance(holder.targetId, keyId);
                    refreshBalance(holder.senderId, keyId);
                    if (sender.isOnline()) {
                        sender.playSound(sender.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.28f, 1.35f);
                        loadAndOpen(sender, holder.targetId, holder.targetName, holder.page);
                    }
                },
                failure -> {
                    transfers.remove(holder.senderId);
                    sender.sendMessage(caseManager.getPlugin().getUi().get("gift-storage-error"));
                    Log.warning("Не удалось подтвердить передачу ключей " + keyId + ".", failure);
                });
    }

    private void refreshBalance(UUID playerId, String keyId) {
        caseManager.getPlugin().getDatabaseExecutor().completeSync(
                caseManager.getPlugin().getDatabaseExecutor().supply(playerId,
                        () -> caseManager.getPlugin().getDatabase().getKeys(playerId, keyId)),
                balance -> caseManager.updateCachedKeyBalance(playerId, keyId, balance),
                failure -> Log.warning("Передача выполнена, но кэш баланса ещё не обновлён.", failure));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (owns(event.getInventory().getHolder())) event.setCancelled(true);
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize(name));
            meta.setLore(lore.stream().map(ColorUtil::colorize).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private static final class GiftHolder implements InventoryHolder {
        private final UUID senderId;
        private final UUID targetId;
        private final String targetName;
        private final int page;
        private final List<String> keyIds;
        private final Map<String, Integer> balances;
        private final List<String> limitLines;

        private GiftHolder(UUID senderId, UUID targetId, String targetName, int page,
                           List<String> keyIds, Map<String, Integer> balances, List<String> limitLines) {
            this.limitLines = List.copyOf(limitLines);
            this.senderId = senderId;
            this.targetId = targetId;
            this.targetName = targetName;
            this.page = page;
            this.keyIds = List.copyOf(keyIds);
            this.balances = Map.copyOf(balances);
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final class PickerHolder implements InventoryHolder {
        private final UUID senderId;
        private final int page;
        private final List<UUID> ids;

        private PickerHolder(UUID senderId, int page, List<UUID> ids) {
            this.senderId = senderId;
            this.page = page;
            this.ids = List.copyOf(ids);
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }}
