package ru.privatenull.pncases.gui.caseview;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pncases.gui.GuiSession;
import ru.privatenull.pncases.gui.GuiDesign;
import ru.privatenull.pncases.support.compat.BukkitCompat.InventoryViewCompat;
import ru.privatenull.pncases.support.text.ColorUtil;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

final class BulkOpenMenu {

    private static final List<Integer> OPTION_SLOTS = List.of(10, 12, 14, 16);
    private static final int BACK = 22;

    private final CaseManager caseManager;
    private final Map<PlayerCase, Map<Integer, Integer>> slotAmounts = new LinkedHashMap<>();
    private final Set<UUID> loading = new HashSet<>();

    BulkOpenMenu(CaseManager caseManager) {
        this.caseManager = caseManager;
    }

    void open(Player player, String caseName) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) return;
        UUID playerId = player.getUniqueId();
        Inventory previous = InventoryViewCompat.topInventory(player);
        caseManager.getPlugin().getDatabaseExecutor().completeSync(
                caseManager.getPlugin().getDatabaseExecutor().supply(playerId,
                        () -> caseManager.getPlugin().getDatabase().getKeys(playerId, definition.name())),
                balance -> {
                    if (!player.isOnline() || InventoryViewCompat.topInventory(player) != previous
                            || caseManager.getCaseByName(caseName) != definition) return;
                    clear(player);
                    caseManager.updateCachedKeyBalance(playerId, definition.name(), balance);
                    Inventory inventory = Bukkit.createInventory(GuiSession.bulkOpen(caseName), 27,
                            caseManager.getPlugin().getUi().text("bulk-open.title",
                                    "&0Количество открытий"));
                    render(inventory, playerId, definition, balance);
                    caseManager.getPlugin().openGui(player, inventory);
                    player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.25f, 1.15f);
                },
                failure -> player.sendMessage(caseManager.getPlugin().getUi().get("case-open-storage-error")));
    }

    private void render(Inventory inventory, UUID playerId, CaseDefinition definition, int balance) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
        List<Integer> configured = caseManager.getPlugin().getConfig()
                .getIntegerList("bulk-opening.amounts");
        List<Integer> amounts = configured.stream().filter(value -> value > 0 && value <= 64)
                .distinct().limit(4).toList();
        if (amounts.isEmpty()) amounts = List.of(1, 3, 5, 10);
        Map<Integer, Integer> mapping = new LinkedHashMap<>();
        for (int index = 0; index < amounts.size(); index++) {
            int amount = amounts.get(index);
            int slot = OPTION_SLOTS.get(index);
            mapping.put(slot, amount);
            Material material = balance >= amount ? Material.LIME_DYE : Material.GRAY_DYE;
            inventory.setItem(slot, item(material,
                    caseManager.getPlugin().getUi().text("bulk-open.option.name",
                            "&#5FD7FFОткрыть {amount}", "amount", String.valueOf(amount)),
                    caseManager.getPlugin().getUi().list("bulk-open.option.lore", List.of(
                            "", " &#A6B0BAОдна анимация — все награды сразу", "",
                            " &#C2CBD3Будет списано", " &#69747E↳ &#A7F3D0{amount}",
                            " &#C2CBD3Доступно", " &#69747E↳ &#A7F3D0{have}", "",
                            " &#FFB347[&#FFA43C Л&#FF9536К&#FF8630М &#FF772A] &fНачать", ""),
                            "amount", String.valueOf(amount), "have", String.valueOf(balance),
                            "case", definition.displayName(), "case_id", definition.name())));
        }
        slotAmounts.put(new PlayerCase(playerId, definition.name()), Map.copyOf(mapping));
        inventory.setItem(BACK, GuiDesign.head(GuiDesign.BACK, "&#FC7165Вернуться"));
    }

    void handleClick(Player player, GuiSession holder, int slot) {
        CaseDefinition definition = caseManager.getCaseByName(holder.caseName());
        if (definition == null) return;
        if (slot == BACK) {
            clear(player);
            caseManager.openCaseGui(player, definition);
            return;
        }
        PlayerCase menuKey = new PlayerCase(player.getUniqueId(), holder.caseName());
        Map<Integer, Integer> choices = slotAmounts.getOrDefault(menuKey, Map.of());
        Integer amount = choices.get(slot);
        if (amount == null) return;
        UUID playerId = player.getUniqueId();
        if (!loading.add(playerId)) return;
        caseManager.getPlugin().getDatabaseExecutor().completeSync(
                caseManager.getPlugin().getDatabaseExecutor().supply(playerId,
                        () -> caseManager.getPlugin().getDatabase().getKeys(playerId, definition.name())),
                balance -> {
                    loading.remove(playerId);
                    Inventory top = InventoryViewCompat.topInventory(player);
                    if (!player.isOnline() || top == null || top.getHolder() != holder
                            || slotAmounts.get(menuKey) != choices
                            || caseManager.getCaseByName(holder.caseName()) != definition) return;
                    caseManager.updateCachedKeyBalance(playerId, definition.name(), balance);
                    if (balance < amount) {
                        player.sendMessage(caseManager.getPlugin().getUi().get("not-enough-keys",
                                "need", String.valueOf(amount), "have", String.valueOf(balance)));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.25f, 1.0f);
                        open(player, definition.name());
                        return;
                    }
                    slotAmounts.remove(new PlayerCase(playerId, definition.name()));
                    caseManager.tryOpenCases(player, definition, amount);
                },
                failure -> {
                    loading.remove(playerId);
                    player.sendMessage(caseManager.getPlugin().getUi().get("case-open-storage-error"));
                });
    }

    void clear(Player player) {
        if (player == null) return;
        loading.remove(player.getUniqueId());
        slotAmounts.keySet().removeIf(key -> key.playerId.equals(player.getUniqueId()));
    }

    static void showResults(CaseManager manager, Player player, String caseId, List<Reward> rewards, int page) {
        int pages = Math.max(1, (rewards.size() + 44) / 45);
        page = Math.max(0, Math.min(pages - 1, page));
        var ui = manager.getPlugin().getUi();
        Inventory inventory = Bukkit.createInventory(GuiSession.batchResults(caseId, rewards, page), 54,
                ui.text("bulk-open.results.title", "&0Награды серии · {amount}",
                        "amount", String.valueOf(rewards.size())));
        CaseDefinition definition = manager.getCaseByName(caseId);
        for (int slot = 0; slot < 45; slot++) {
            int index = page * 45 + slot;
            if (index >= rewards.size()) break;
            Reward reward = rewards.get(index);
            ItemStack visual = manager.getRewardPresentation().buildDisplayItem(definition, reward).clone();
            ItemMeta meta = visual.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ColorUtil.colorize(manager.getRewardPresentation().resolveViewName(reward, visual)));
                meta.setLore(ui.list("bulk-open.results.lore", List.of(
                        "", " &aНаграда уже выдана", " &7Результат {index} из {amount}", ""),
                        "index", String.valueOf(index + 1), "amount", String.valueOf(rewards.size()))
                        .stream().map(ColorUtil::colorize).toList());
                GuiDesign.hideAttributes(meta);
                visual.setItemMeta(meta);
            }
            inventory.setItem(slot, visual);
        }
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        }
        if (page > 0) inventory.setItem(45, item(Material.ARROW, "&fПредыдущая страница", List.of()));
        if (page + 1 < pages) inventory.setItem(53, item(Material.ARROW, "&fСледующая страница", List.of()));
        inventory.setItem(49, GuiDesign.head(GuiDesign.BACK, "&#FC7165Закрыть"));
        manager.getPlugin().openGui(player, inventory);
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

    private record PlayerCase(UUID playerId, String caseId) { }
}
