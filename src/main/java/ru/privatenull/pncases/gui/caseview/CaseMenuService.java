package ru.privatenull.pncases.gui.caseview;

import ru.privatenull.pncases.gui.GuiSession;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.pncases.PnCasesPlugin;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.model.AnimationType;
import ru.privatenull.pncases.cases.model.BundleLayout;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pncases.cases.model.CaseGuiLayout;
import ru.privatenull.pncases.cases.reward.RewardPresentationService;
import ru.privatenull.pncases.storage.CasesDatabase.HistoryEntry;
import ru.privatenull.pncases.support.text.ColorUtil;
import ru.privatenull.pncases.gui.GuiDesign;
import ru.privatenull.pncases.support.compat.ServerVersion;
import ru.privatenull.pncases.support.compat.BukkitCompat.InventoryViewCompat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class CaseMenuService implements CaseManager.View {

    private static final DateTimeFormatter HISTORY_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    private final PnCasesPlugin plugin;
    private final CaseManager caseManager;
    private final RewardPresentationService rewardPresentation;
    private final Map<PlayerCase, Integer> keyBalances = new ConcurrentHashMap<>();
    private final Map<String, List<HistoryLine>> histories = new ConcurrentHashMap<>();

    public CaseMenuService(
            PnCasesPlugin plugin,
            CaseManager caseManager
    ) {
        this.plugin = plugin;
        this.caseManager = caseManager;
        this.rewardPresentation = caseManager.getRewardPresentation();
    }

    @Override
    public void showBatchResults(Player player, String caseId, List<Reward> rewards) {
        BulkOpenMenu.showResults(caseManager, player, caseId, rewards, 0);
    }

    @Override
    public ItemStack buildOpenButton(Player player, CaseDefinition definition) {
        ItemStack item = definition.openButton().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        int buyExp = Math.max(0, definition.buyKeyWithXpLevels());
        int need = 1;
        String keyId = definition.name();
        int have = keyBalances.getOrDefault(new PlayerCase(player.getUniqueId(), keyId), 0);

        String keyName = color(definition.displayName());
        boolean previewEnabled = plugin.previewEnabled()
                && definition.guiLayout().previewEnabled();
        String leftAction = previewEnabled
                ? (buyExp > 0 ? "Купить за " + buyExp + " уровней" : "Посмотреть награды")
                : "Открыть кейс";
        String[] replacements = {
                "case", color(definition.displayName()), "case_id", definition.name(),
                "display-name", color(definition.displayName()), "title", definition.guiTitle(),
                "key", keyId == null ? "" : keyId, "key_name", keyName,
                "have", String.valueOf(have), "need", String.valueOf(need), "levels", String.valueOf(buyExp),
                "left-click", "&fЛевая кнопка  &#9EFC65" + leftAction,
                "right-click", "&fПравая кнопка &#9EFC65Открыть кейс",
                "bulk-click", "&fShift + ПКМ &#9EFC65Выбрать количество",
                "left_action", leftAction,
                "right_action", "Открыть кейс",
                "bulk_action", "Выбрать количество"
        };
        meta.setDisplayName(formatText(meta.getDisplayName(), replacements));
        List<String> configured = meta.hasLore() && meta.getLore() != null
                ? meta.getLore()
                : List.of(
                "", " &#A6B0BAОткрытие и просмотр содержимого кейса",
                "", " &#C2CBD3Ключи", " &#69747E↳ &#A7F3D0{have} / {need}",
                "", " &#FFB347[&#FFA43C Л&#FF9536К&#FF8630М &#FF772A] &f{left_action}",
                " &#FF8A8A[&#FF7979 П&#F96868К&#F25757М &#EB4646] &f{right_action}",
                " &#FFB347[&#FFA43C SHIFT + ПКМ &#FF772A] &f{bulk_action}", ""
        );
        meta.setLore(formatLore(configured, replacements));
        GuiDesign.hideAttributes(meta);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void updateKeyBalance(UUID playerId, String caseId, int balance) {
        if (playerId == null || caseId == null) return;
        keyBalances.put(new PlayerCase(playerId, caseId), Math.max(0, balance));
    }

    @Override
    public void clearPlayer(UUID playerId) {
        if (playerId == null) return;
        keyBalances.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    @Override
    public void clearCase(String caseId) {
        if (caseId == null) return;
        histories.remove(caseId);
        keyBalances.keySet().removeIf(key -> key.caseId().equals(caseId));
    }

    @Override
    public void clearAll() {
        keyBalances.clear();
        histories.clear();
    }

    public ItemStack buildAnimationButton(Player player, CaseDefinition definition) {
        AnimationType current = caseManager.getPlayerAnimation(
                player.getUniqueId(), definition == null ? "" : definition.name());
        Set<AnimationType> favorites = caseManager.getPlayerFavoriteAnimations(
                player.getUniqueId(), definition == null ? "" : definition.name());
        ItemStack item = GuiDesign.animation(current);
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String[] replacements = {
                "animation", favorites.isEmpty()
                        ? GuiDesign.animationName(current)
                        : "Случайная из " + favorites.size(),
                "favorites", String.valueOf(favorites.size()),
                "case", definition == null ? "" : color(definition.displayName()),
                "case_id", definition == null ? "" : definition.name()
        };
        meta.setDisplayName(plugin.getUi().text("case.animation-button.name",
                "&#5FD7FFАнимация открытия", replacements));
        meta.setLore(plugin.getUi().list("case.animation-button.lore", List.of(
                "",
                " &#A6B0BAНастройка отображения кейса",
                "",
                " &#C2CBD3Анимация",
                " &#69747E↳ &#A7F3D0{animation}",
                "",
                " &#FFB347[&#FFA43C Л&#FF9536К&#FF8630М &#FF772A] &fИзменить",
                " &#FF8A8A[&#FF7979 П&#F96868К&#F25757М &#EB4646] &fСбросить",
                ""
        ), replacements));
        GuiDesign.hideAttributes(meta);
        item.setItemMeta(meta);
        return item;
    }

    public void fill(Inventory inventory, Player player, CaseDefinition definition) {
        inventory.clear();
        CaseGuiLayout layout = definition.guiLayout();
        fillDecor(inventory, layout);
        fillHistory(inventory, definition);
        if (layout.animationSlot() >= 0
                && definition.fixedAnimation() == null
                && !ServerVersion.current().isReleaseLine(1, 16)) {
            inventory.setItem(layout.animationSlot(), buildAnimationButton(player, definition));
        }
        // Opening is the highest-priority action. Keep it last as an additional
        // safety net for CaseGuiLayout instances built outside the YAML parser.
        List<CaseDefinition> inside = caseManager.bundleCases(definition);
        if (inside.isEmpty()) {
            inventory.setItem(layout.openSlot(), buildOpenButton(player, definition));
            return;
        }
        // A case with cases inside shows one ordinary open button for each of them instead.
        int[] slots = BundleLayout.slots(inside.size(), layout.openSlot(), inventory.getSize());
        for (int index = 0; index < slots.length; index++) {
            inventory.setItem(slots[index], buildOpenButton(player, inside.get(index)));
        }
    }

    @Override
    public void open(Player player, CaseDefinition definition) {
        UUID playerId = player.getUniqueId();
        Inventory previous = InventoryViewCompat.topInventory(player);
        boolean loadHistory = plugin.historyEnabled();
        List<CaseDefinition> inside = caseManager.bundleCases(definition);
        plugin.getDatabaseExecutor().completeSync(
                plugin.getDatabaseExecutor().supply(playerId, () -> {
                    Map<String, Integer> keys = new java.util.HashMap<>();
                    keys.put(definition.name(), plugin.getDatabase().getKeys(playerId, definition.name()));
                    List<HistoryLine> history = new ArrayList<>();
                    if (loadHistory) {
                        for (CaseDefinition source : inside.isEmpty() ? List.of(definition) : inside) {
                            for (HistoryEntry entry : plugin.getDatabase().getHistory(source.name(), 9)) {
                                history.add(new HistoryLine(source.name(), entry));
                            }
                        }
                        history.sort((first, second) -> Long.compare(second.entry().openedAt(), first.entry().openedAt()));
                    }
                    for (CaseDefinition child : inside) {
                        keys.put(child.name(), plugin.getDatabase().getKeys(playerId, child.name()));
                    }
                    return new MenuData(keys, history.size() > 9 ? history.subList(0, 9) : history,
                            plugin.getDatabase().getAnimation(playerId, definition.name()),
                            plugin.getDatabase().getFavoriteAnimations(playerId, definition.name()));
                }),
                data -> {
                    if (!player.isOnline() || InventoryViewCompat.topInventory(player) != previous
                            || caseManager.getCaseByName(definition.name()) != definition) return;
                    data.keys().forEach((caseId, count) -> keyBalances.put(new PlayerCase(playerId, caseId), count));
                    histories.put(definition.name(), data.history());
                    caseManager.cacheAnimationPreferences(playerId, definition.name(),
                            data.animation(), data.favorites());
                    Inventory inventory = Bukkit.createInventory(
                            GuiSession.caseMenu(definition.name()),
                            definition.guiLayout().size(),
                            color(definition.guiTitle()));
                    fill(inventory, player, definition);
                    plugin.openGui(player, inventory);
                },
                failure -> player.sendMessage(plugin.getUi().get("case-open-storage-error")));
    }

    private List<String> formatLore(List<String> source, String[] replacements) {
        List<String> lines = new ArrayList<>();
        for (String raw : source) {
            String formatted = raw == null ? "" : raw;
            for (int i = 0; i < replacements.length; i += 2) {
                formatted = formatted.replace("{" + replacements[i] + "}", replacements[i + 1]);
            }
            for (String line : formatted.split("\\R", -1)) {
                lines.add(color(line));
            }
        }
        return lines;
    }

    private String formatText(String source, String[] replacements) {
        String formatted = source == null ? "" : source;
        for (int index = 0; index < replacements.length; index += 2) {
            formatted = formatted.replace(
                    "{" + replacements[index] + "}", replacements[index + 1]);
        }
        return color(formatted);
    }

    private void fillDecor(Inventory inventory, CaseGuiLayout layout) {
        ItemStack pane = layout.decorItem() == null
                ? new ItemStack(Material.GRAY_STAINED_GLASS_PANE)
                : layout.decorItem().clone();
        hideAttributes(pane);
        for (int slot : layout.decorSlots()) {
            if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, pane.clone());
        }
    }

    private void fillHistory(Inventory inventory, CaseDefinition definition) {
        List<HistoryLine> history = histories.getOrDefault(definition.name(), List.of());
        List<Integer> slots = new ArrayList<>(definition.guiLayout().historySlots());
        int animationSlot = definition.guiLayout().animationSlot();
        boolean animationSelectionHidden = definition.fixedAnimation() != null
                || ServerVersion.current().isReleaseLine(1, 16);
        if (animationSelectionHidden
                && animationSlot >= 0
                && animationSlot < inventory.getSize()
                && !slots.contains(animationSlot)) {
            slots.add(animationSlot);
            slots.sort(Integer::compareTo);
        }
        // With the history switched off these slots are plain decoration, not "history is empty" markers.
        boolean historyOn = plugin.historyEnabled();
        ItemStack decor = definition.guiLayout().decorItem() == null
                ? new ItemStack(Material.GRAY_STAINED_GLASS_PANE)
                : definition.guiLayout().decorItem().clone();
        hideAttributes(decor);
        for (int index = 0; index < slots.size(); index++) {
            int slot = slots.get(index);
            if (slot < 0 || slot >= inventory.getSize()) continue;
            if (!historyOn) {
                inventory.setItem(slot, decor.clone());
                continue;
            }
            inventory.setItem(slot, index < history.size()
                    ? historyItem(definition, history.get(index))
                    : emptyHistoryItem(definition));
        }
    }

    private ItemStack historyItem(CaseDefinition shown, HistoryLine line) {
        HistoryEntry entry = line.entry();
        // In the menu of a bundle every line belongs to the case it was opened from.
        CaseDefinition definition = caseManager.getCaseByName(line.caseId());
        if (definition == null) definition = shown;
        ItemStack item = historyRewardItem(definition, entry.rewardName());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        String[] replacements = {
                "case", color(definition.displayName()), "case_id", definition.name(),
                "player", entry.playerName(), "reward", color(entry.rewardName()), "time", formatTime(entry.openedAt())
        };
        meta.setDisplayName(plugin.getUi().text("case.history.item.name", "&#5FD7FF{reward}", replacements));
        meta.setLore(plugin.getUi().list("case.history.item.lore", List.of(
                "", " &#A6B0BAПоследнее открытие этого кейса", "",
                " &#C2CBD3Игрок", " &#69747E↳ &#A7F3D0{player}",
                " &#C2CBD3Награда", " &#69747E↳ &#A7F3D0{reward}",
                " &#C2CBD3Время", " &#69747E↳ &#A7F3D0{time}", ""
        ), replacements));
        GuiDesign.hideAttributes(meta);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack historyRewardItem(CaseDefinition definition, String rewardName) {
        ItemStack item = rewardPresentation.findDisplayItem(definition, rewardName);
        if (item != null) return item;

        ItemStack fallback = new ItemStack(Material.CLOCK);
        ItemMeta meta = fallback.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(rewardName == null || rewardName.isBlank() ? "&fНаграда" : rewardName));
            fallback.setItemMeta(meta);
        }
        return fallback;
    }

    private record PlayerCase(UUID playerId, String caseId) {
    }

    private record HistoryLine(String caseId, HistoryEntry entry) {
    }

    private record MenuData(
            Map<String, Integer> keys,
            List<HistoryLine> history,
            AnimationType animation,
            Set<AnimationType> favorites
    ) {
        private MenuData {
            keys = Map.copyOf(keys);
            history = List.copyOf(history);
            favorites = favorites == null ? Set.of() : Set.copyOf(favorites);
        }
    }

    private ItemStack emptyHistoryItem(CaseDefinition definition) {
        ItemStack configured = definition.guiLayout().emptyHistoryItem();
        ItemStack item = configured == null || configured.getType().isAir()
                ? new ItemStack(Material.BARRIER)
                : configured.clone();
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String[] replacements = {
                "case", color(definition.displayName()), "case_id", definition.name()
        };
        if (!meta.hasDisplayName()) {
            meta.setDisplayName(plugin.getUi().text("case.history.empty.name", "&#5FD7FFИстория открытий", replacements));
        }
        if (!meta.hasLore()) {
            meta.setLore(plugin.getUi().list("case.history.empty.lore", List.of(
                    "", " &#A6B0BAЗдесь появятся последние",
                    " &#A6B0BAполученные игроками награды", ""
            ), replacements));
        }
        item.setItemMeta(meta);
        hideAttributes(item);
        return item;
    }

    private void hideAttributes(ItemStack item) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        GuiDesign.hideAttributes(meta);
        item.setItemMeta(meta);
    }

    private String formatTime(long epochSeconds) {
        if (epochSeconds <= 0L) return "неизвестно";
        long diff = System.currentTimeMillis() / 1000L - epochSeconds;
        if (diff < 60) return "только что";
        if (diff < 3600) {
            int minutes = (int) (diff / 60);
            return minutes + " " + wordForm(minutes, "минуту", "минуты", "минут") + " назад";
        }
        if (diff < 86400) {
            int hours = (int) (diff / 3600);
            return hours + " " + wordForm(hours, "час", "часа", "часов") + " назад";
        }
        int days = (int) (diff / 86400);
        if (days < 7) return days + " " + wordForm(days, "день", "дня", "дней") + " назад";
        LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault());
        return HISTORY_TIME_FORMAT.format(time);
    }

    private String wordForm(int number, String one, String few, String many) {
        int normalized = Math.abs(number) % 100;
        int last = normalized % 10;
        if (normalized > 10 && normalized < 20) return many;
        if (last > 1 && last < 5) return few;
        return last == 1 ? one : many;
    }

    private String color(String value) {
        return ColorUtil.colorize(value);
    }
}
