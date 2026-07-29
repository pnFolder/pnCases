package ru.privatenull.gui.caseview;

import ru.privatenull.gui.GuiSession;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.AnimationType;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.CaseGuiLayout;
import ru.privatenull.cases.reward.RewardPresentationService;
import ru.privatenull.storage.CasesDatabase.HistoryEntry;
import ru.privatenull.pnlibrary.text.ColorUtil;
import ru.privatenull.gui.GuiDesign;
import ru.privatenull.util.ServerCompatibility;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CaseMenuService implements CaseManager.View {

    private static final DateTimeFormatter HISTORY_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    private final PnCasesPlugin plugin;
    private final CaseManager caseManager;
    private final RewardPresentationService rewardPresentation;

    public CaseMenuService(
            PnCasesPlugin plugin,
            CaseManager caseManager
    ) {
        this.plugin = plugin;
        this.caseManager = caseManager;
        this.rewardPresentation = caseManager.getRewardPresentation();
    }

    @Override
    public ItemStack buildOpenButton(Player player, CaseDefinition definition) {
        ItemStack item = definition.openButton().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        int buyExp = Math.max(0, definition.buyKeyWithXpLevels());
        int need = 1;
        String keyId = definition.name();
        int have = plugin.getDatabase().getKeys(player.getUniqueId(), keyId);

        String keyName = color(definition.displayName());
        String buyHint = "&fЛКМ — купить ключ за &#FCA865" + buyExp + " уровней";
        String previewHint = "&fЛКМ — посмотреть содержимое";
        String openLeftHint = "&fЛКМ — открыть кейс";
        boolean previewEnabled = definition.guiLayout().previewEnabled();
        String[] replacements = {
                "case", color(definition.displayName()), "case_id", definition.name(),
                "display-name", color(definition.displayName()), "title", definition.guiTitle(),
                "key", keyId == null ? "" : keyId, "key_name", keyName,
                "have", String.valueOf(have), "need", String.valueOf(need), "levels", String.valueOf(buyExp),
                "left-click", previewEnabled ? (buyExp > 0 ? buyHint : previewHint) : openLeftHint,
                "right-click", "&fПКМ — открыть кейс"
        };
        meta.setDisplayName(formatText(meta.getDisplayName(), replacements));
        List<String> configured = meta.hasLore() && meta.getLore() != null
                ? meta.getLore()
                : List.of(
                "", "&#9EFC65 «Текущие параметры»",
                " &7- &fКлючи: &#FCA865{have} &7/ &#FCA865{need}",
                " &7- &fТип ключа: {key_name}",
                "", "&#65D1FC «Действия»",
                " &7- {left-click}", " &7- {right-click}", ""
        );
        meta.setLore(formatLore(configured, replacements));
        GuiDesign.hideAttributes(meta);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack buildAnimationButton(Player player, CaseDefinition definition) {
        AnimationType current = caseManager.getPlayerAnimation(
                player.getUniqueId(), definition == null ? "" : definition.name());
        ItemStack item = GuiDesign.animation(current);
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String[] replacements = {
                "animation", GuiDesign.animationName(current),
                "case", definition == null ? "" : color(definition.displayName()),
                "case_id", definition == null ? "" : definition.name()
        };
        meta.setDisplayName(plugin.getUi().text("case.animation-button.name",
                "&fАнимация: {animation}", replacements));
        meta.setLore(plugin.getUi().list("case.animation-button.lore", List.of(
                "",
                "&#9EFC65 «Текущие параметры»",
                " &7- &fАнимация: {animation}",
                "",
                "&#65D1FC «Действие»",
                " &7- &fЛКМ — открыть выбор",
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
                && !ServerCompatibility.useMinecraft1165AnimationMode()) {
            inventory.setItem(layout.animationSlot(), buildAnimationButton(player, definition));
        }
        // Opening is the highest-priority action. Keep it last as an additional
        // safety net for CaseGuiLayout instances built outside the YAML parser.
        inventory.setItem(layout.openSlot(), buildOpenButton(player, definition));
    }

    @Override
    public void open(Player player, CaseDefinition definition) {
        Inventory inventory = Bukkit.createInventory(
                GuiSession.caseMenu(definition.name()),
                definition.guiLayout().size(),
                color(definition.guiTitle())
        );
        fill(inventory, player, definition);
        plugin.getGuiOpenAnimations().open(player, inventory);
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
        List<HistoryEntry> history = caseManager.getOpenHistory(definition.name());
        List<Integer> slots = new ArrayList<>(definition.guiLayout().historySlots());
        int animationSlot = definition.guiLayout().animationSlot();
        boolean animationSelectionHidden = definition.fixedAnimation() != null
                || ServerCompatibility.useMinecraft1165AnimationMode();
        if (animationSelectionHidden
                && animationSlot >= 0
                && animationSlot < inventory.getSize()
                && !slots.contains(animationSlot)) {
            slots.add(animationSlot);
            slots.sort(Integer::compareTo);
        }
        for (int index = 0; index < slots.size(); index++) {
            int slot = slots.get(index);
            if (slot < 0 || slot >= inventory.getSize()) continue;
            inventory.setItem(slot, index < history.size()
                    ? historyItem(definition, history.get(index))
                    : emptyHistoryItem(definition));
        }
    }

    private ItemStack historyItem(CaseDefinition definition, HistoryEntry entry) {
        ItemStack item = historyRewardItem(definition, entry.rewardName());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        String[] replacements = {
                "case", color(definition.displayName()), "case_id", definition.name(),
                "player", entry.playerName(), "reward", color(entry.rewardName()), "time", formatTime(entry.openedAt())
        };
        meta.setDisplayName(plugin.getUi().text("case.history.item.name", "&#A0EFA1◆ {reward}", replacements));
        meta.setLore(plugin.getUi().list("case.history.item.lore", List.of(
                "", "&#9EFC65 «Основная информация»", " &7- &fИгрок: &#FBCA08{player}",
                " &7- &fНаграда: {reward}", "", "&#65D1FC «Время открытия»", " &7- &f{time}", ""
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
            meta.setDisplayName(plugin.getUi().text("case.history.empty.name", "&8История пуста", replacements));
        }
        if (!meta.hasLore()) {
            meta.setLore(plugin.getUi().list("case.history.empty.lore", List.of(
                    "", "&#9EFC65 «Основная информация»", " &7- &fПоследние открытия кейса",
                    " &7- &fбудут отображаться здесь.", ""
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
