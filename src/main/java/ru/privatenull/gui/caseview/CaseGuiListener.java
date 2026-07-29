package ru.privatenull.gui.caseview;

import ru.privatenull.gui.GuiSession;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.pnlibrary.text.ColorUtil;
import ru.privatenull.util.BukkitCompat.InventoryViewCompat;
import ru.privatenull.util.SkullUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CaseGuiListener implements Listener {

    private static final String NO_KEYS_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzc1YjM2YzM5NDVmZGFhMGM3NDA4MTg1MzFhOGY0NWJhY2QyNjk2Y2UyYWI4NWE5ZWE0NTI5MGFjY2YxODBjYyJ9fX0=";

    private final CaseManager caseManager;
    private final RewardPreviewMenu rewardPreviewMenu;
    private final AnimationSelectionMenu animationMenu;
    private final Set<UUID> clickLock = ConcurrentHashMap.newKeySet();

    public CaseGuiListener(CaseManager caseManager) {
        this.caseManager = caseManager;
        this.rewardPreviewMenu = new RewardPreviewMenu(caseManager);
        this.animationMenu = new AnimationSelectionMenu(caseManager);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (!(event.getInventory().getHolder() instanceof GuiSession holder) || holder.machine()) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (holder.screen() == GuiSession.Screen.PLAYER_ANIMATIONS) {
            animationMenu.handleClick(player, holder, slot);
            return;
        }
        if (holder.screen() == GuiSession.Screen.REWARD_PREVIEW) {
            rewardPreviewMenu.handleClick(player, holder, slot);
            return;
        }
        if (holder.screen() != GuiSession.Screen.CASE) return;

        CaseDefinition definition = caseManager.getCaseByName(holder.caseName());
        if (definition == null) return;
        if (slot == definition.guiLayout().openSlot()) {
            if (definition.guiLayout().previewEnabled()
                    && (event.getClick() == ClickType.MIDDLE
                    || (event.getClick().isLeftClick() && definition.buyKeyWithXpLevels() <= 0))) {
                rewardPreviewMenu.open(player, holder.caseName(), 0);
                return;
            }
            if (event.getClick().isLeftClick() || event.getClick().isRightClick()) {
                openOrBuy(player, event, holder, definition);
            }
            return;
        }
        if (definition.guiLayout().animationSlot() >= 0
                && slot == definition.guiLayout().animationSlot()
                && definition.fixedAnimation() == null) {
            animationMenu.open(player, holder.caseName());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Object holder = event.getInventory().getHolder();
        if (!(holder instanceof GuiSession session) || session.machine()) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot >= 0 && slot < topSize)) {
            event.setCancelled(true);
        }
    }

    private void openOrBuy(Player player, InventoryClickEvent event, GuiSession holder, CaseDefinition definition) {
        UUID playerId = player.getUniqueId();
        if (!clickLock.add(playerId)) return;

        String keyId = definition.name();
        int need = 1;
        if (!caseManager.keyExists(keyId)) {
            showInvalidKey(player, event.getInventory(), definition);
            scheduleRefresh(player, holder.caseName(), playerId, 30L);
            return;
        }

        int have = caseManager.getPlugin().getDatabase().getKeys(playerId, keyId);
        if (have >= need) {
            clickLock.remove(playerId);
            caseManager.tryOpenCase(player, definition);
            return;
        }

        int buyLevels = Math.max(0, definition.buyKeyWithXpLevels());
        boolean wantsBuy = event.getClick().isLeftClick();
        if (wantsBuy && buyLevels > 0 && player.getLevel() >= buyLevels) {
            buyKeys(player, event.getInventory(), definition, keyId, need - have, buyLevels);
        } else {
            showInsufficientPayment(player, event.getInventory(), definition, wantsBuy, have, need, buyLevels);
        }
        scheduleRefresh(player, holder.caseName(), playerId, 30L);
    }

    private void showInvalidKey(Player player, Inventory inventory, CaseDefinition definition) {
        String title = "&cКлюч не настроен";
        inventory.setItem(definition.guiLayout().openSlot(), pane(Material.BARRIER, title, List.of(
                        "",
                        "&#FC7165 «Ошибка настройки»",
                        " &7- &fДля кейса не создан виртуальный ключ.",
                        " &7- &fПересоздайте кейс или проверьте config.yml.",
                        ""
                )));
        player.sendMessage(caseManager.getPlugin().getUi().get("key-not-configured"));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.24f, 1.0f);
    }

    private void buyKeys(
            Player player,
            Inventory inventory,
            CaseDefinition definition,
            String keyId,
            int amount,
            int levels
    ) {
        player.setLevel(player.getLevel() - levels);
        caseManager.getPlugin().getDatabase().addKeys(player.getUniqueId(), keyId, amount);
        inventory.setItem(definition.guiLayout().openSlot(), pane(
                Material.LIME_STAINED_GLASS_PANE,
                "&aПокупка завершена",
                Collections.emptyList()
        ));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.28f, 1.2f);
    }

    private void showInsufficientPayment(
            Player player,
            Inventory inventory,
            CaseDefinition definition,
            boolean wantsBuy,
            int have,
            int need,
            int buyLevels
    ) {
        String title;
        List<String> lore = new ArrayList<>();
        if (!wantsBuy || buyLevels <= 0) {
            ConfigurationSection root = caseManager.getCaseSection(definition.name());
            ConfigurationSection configured = root == null
                    ? null
                    : root.getConfigurationSection("gui.insufficient-keys");
            String keyId = definition.name();
            String keyName = caseManager.getKeyDisplayName(keyId);
            String[] replacements = {
                    "display-name", definition.displayName(),
                    "case", definition.displayName(),
                    "case_id", definition.name(),
                    "key", keyId == null ? "" : keyId,
                    "key_name", keyName,
                    "have", String.valueOf(have),
                    "need", String.valueOf(need)
            };
            title = replace(configured == null
                    ? "&#FF5A64Недостаточно ключей"
                    : configured.getString("name", "&#FF5A64Недостаточно ключей"), replacements);
            List<String> configuredLore = configured != null && configured.isList("lore")
                    ? configured.getStringList("lore")
                    : List.of(
                    "",
                    "&#FC7165 «Недостаточно ключей»",
                    " &7- &fДоступно: &#FCA865{have}",
                    " &7- &fТребуется: &#FCA865{need}",
                    "",
                    "&#65D1FC «Что делать»",
                    " &7- &fПолучите ключ и попробуйте снова.",
                    ""
            );
            for (String line : configuredLore) {
                lore.add(replace(line, replacements));
            }
            player.sendMessage(caseManager.getPlugin().getUi().get("not-enough-keys",
                    "have", String.valueOf(have), "need", String.valueOf(need)));
        } else {
            title = "&cУ вас недостаточно уровней";
            lore.addAll(List.of(
                    "",
                    "&#FC7165 «Недостаточно опыта»",
                    " &7- &fДоступно: &#FCA865" + player.getLevel() + " уровней",
                    " &7- &fТребуется: &#FCA865" + buyLevels + " уровней",
                    "",
                    "&#65D1FC «Что делать»",
                    " &7- &fПолучите опыт и попробуйте снова.",
                    ""
            ));
        }
        inventory.setItem(definition.guiLayout().openSlot(), texturedPane(NO_KEYS_TEXTURE, title, lore));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.24f, 1.0f);
    }

    private void scheduleRefresh(Player player, String caseName, UUID playerId, long delay) {
        new BukkitRunnable() {
            @Override
            public void run() {
                clickLock.remove(playerId);
                if (!player.isOnline()) return;
                Inventory top = InventoryViewCompat.topInventory(player);
                if (top == null || !(top.getHolder() instanceof GuiSession holder)) return;
                if (holder.screen() != GuiSession.Screen.CASE || !Objects.equals(holder.caseName(), caseName)) return;
                CaseDefinition current = caseManager.getCaseByName(caseName);
                if (current == null) return;
                caseManager.getPlugin().getGuiUpdates().setTopSlot(
                        player,
                        top,
                        current.guiLayout().openSlot(),
                        caseManager.buildGuiOpenItem(player, current)
                );
            }
        }.runTaskLater(caseManager.getPlugin(), delay);
    }

    private static ItemStack pane(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize(name));
            meta.setLore(lore.stream().map(ColorUtil::colorize).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack texturedPane(String texture, String name, List<String> lore) {
        ItemStack item = SkullUtil.fromBase64(texture, name);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize(name));
            meta.setLore(lore.stream().map(ColorUtil::colorize).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String replace(String source, String... replacements) {
        String result = source == null ? "" : source;
        for (int index = 0; index < replacements.length; index += 2) {
            result = result.replace(
                    "{" + replacements[index] + "}", replacements[index + 1]);
        }
        return result;
    }
}
