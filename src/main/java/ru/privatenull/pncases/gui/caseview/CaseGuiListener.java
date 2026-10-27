package ru.privatenull.pncases.gui.caseview;

import ru.privatenull.pncases.gui.GuiSession;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.pncases.support.task.PluginRunnable;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.model.BundleLayout;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.gui.GuiDesign;
import ru.privatenull.pncases.support.item.HeadUtil;
import ru.privatenull.pncases.support.text.ColorUtil;
import ru.privatenull.pncases.support.compat.BukkitCompat.InventoryViewCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CaseGuiListener implements Listener {

    private final CaseManager caseManager;
    private final RewardPreviewMenu rewardPreviewMenu;
    private final AnimationSelectionMenu animationMenu;
    private final BulkOpenMenu bulkOpenMenu;
    private final Set<UUID> clickLock = ConcurrentHashMap.newKeySet();

    public CaseGuiListener(CaseManager caseManager) {
        this.caseManager = caseManager;
        this.rewardPreviewMenu = new RewardPreviewMenu(caseManager);
        this.animationMenu = new AnimationSelectionMenu(caseManager);
        this.bulkOpenMenu = new BulkOpenMenu(caseManager);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (!(event.getInventory().getHolder() instanceof GuiSession holder) || holder.machine()) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;
        caseManager.getPlugin().withGuiTransition(player, slot,
                () -> handleClick(player, event, holder, slot));
    }

    private void handleClick(Player player, InventoryClickEvent event, GuiSession holder, int slot) {
        if (holder.screen() == GuiSession.Screen.BATCH_RESULTS) {
            if (slot == 49) player.closeInventory();
            else if (slot == 45 || slot == 53) {
                BulkOpenMenu.showResults(caseManager, player, holder.caseName(), holder.rewards(),
                        holder.page() + (slot == 45 ? -1 : 1));
            }
            return;
        }
        if (holder.screen() == GuiSession.Screen.PLAYER_ANIMATIONS) {
            animationMenu.handleClick(player, holder, slot, event.getClick());
            return;
        }
        if (holder.screen() == GuiSession.Screen.REWARD_PREVIEW) {
            rewardPreviewMenu.handleClick(player, holder, slot);
            return;
        }
        if (holder.screen() == GuiSession.Screen.BULK_OPEN) {
            if (event.isLeftClick()) bulkOpenMenu.handleClick(player, holder, slot);
            return;
        }
        if (holder.screen() != GuiSession.Screen.CASE) return;

        CaseDefinition definition = caseManager.getCaseByName(holder.caseName());
        if (definition == null) return;
        CaseDefinition opened = null;
        List<CaseDefinition> inside = caseManager.bundleCases(definition);
        if (inside.isEmpty()) {
            if (slot == definition.guiLayout().openSlot()) opened = definition;
        } else {
            // A case with cases inside has one ordinary open button for each of them.
            int[] slots = BundleLayout.slots(inside.size(), definition.guiLayout().openSlot(),
                    event.getInventory().getSize());
            for (int index = 0; index < slots.length; index++) {
                if (slots[index] == slot) opened = inside.get(index);
            }
        }
        if (opened != null) {
            handleOpenButton(player, event, holder, opened, slot);
            return;
        }
        if (definition.guiLayout().animationSlot() >= 0
                && slot == definition.guiLayout().animationSlot()
                && definition.fixedAnimation() == null) {
            if (event.getClick().isRightClick()) {
                caseManager.clearPlayerFavoriteAnimations(player.getUniqueId(), holder.caseName());
                caseManager.setPlayerAnimation(player.getUniqueId(), holder.caseName(),
                        ru.privatenull.pncases.cases.model.AnimationType.ANVIL);
                caseManager.openCaseGui(player, definition);
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.18f, 0.9f);
            } else if (event.getClick().isLeftClick()) {
                animationMenu.open(player, holder.caseName());
            }
        }
    }

    /** A click on an open button: rewards, quantity or opening of {@code target}, which may sit inside a bundle. */
    private void handleOpenButton(Player player, InventoryClickEvent event, GuiSession holder,
                                  CaseDefinition target, int slot) {
        if (event.getClick() == ClickType.SHIFT_RIGHT) {
            bulkOpenMenu.open(player, target.name());
            return;
        }
        if (caseManager.getPlugin().previewEnabled()
                && target.guiLayout().previewEnabled()
                && (event.getClick() == ClickType.MIDDLE
                || (event.getClick().isLeftClick() && target.buyKeyWithXpLevels() <= 0))) {
            rewardPreviewMenu.open(player, target.name(), 0);
            return;
        }
        if (event.getClick().isLeftClick() || event.getClick().isRightClick()) {
            openOrBuy(player, event, holder, target, slot);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        bulkOpenMenu.clear(event.getPlayer());
        clickLock.remove(event.getPlayer().getUniqueId());
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

    private void openOrBuy(Player player, InventoryClickEvent event, GuiSession holder, CaseDefinition definition,
                           int slot) {
        UUID playerId = player.getUniqueId();
        if (!clickLock.add(playerId)) return;

        String keyId = definition.name();
        int need = 1;
        int buyLevels = Math.max(0, definition.buyKeyWithXpLevels());
        boolean wantsBuy = event.getClick().isLeftClick();
        Inventory inventory = event.getInventory();
        caseManager.getPlugin().getDatabaseExecutor().completeSync(
                caseManager.getPlugin().getDatabaseExecutor().supply(playerId,
                        () -> caseManager.getPlugin().getDatabase().getKeys(playerId, keyId)),
                have -> {
                    caseManager.updateCachedKeyBalance(playerId, keyId, have);
                    if (!player.isOnline()) {
                        clickLock.remove(playerId);
                        return;
                    }
                    if (have >= need) {
                        clickLock.remove(playerId);
                        caseManager.tryOpenCase(player, definition);
                        return;
                    }
                    if (wantsBuy && buyLevels > 0 && player.getLevel() >= buyLevels) {
                        buyKeys(player, inventory, holder, definition, slot, keyId, need - have, buyLevels);
                    } else {
                        showInsufficientPayment(player, inventory, definition, slot, wantsBuy, have, need, buyLevels);
                        scheduleRefresh(player, holder.caseName(), definition.name(), slot, playerId, 30L);
                    }
                },
                failure -> {
                    clickLock.remove(playerId);
                    player.sendMessage(caseManager.getPlugin().getUi().get("case-open-storage-error"));
                });
    }

    private void buyKeys(
            Player player,
            Inventory inventory,
            GuiSession holder,
            CaseDefinition definition,
            int slot,
            String keyId,
            int amount,
        int levels
    ) {
        player.setLevel(player.getLevel() - levels);
        UUID playerId = player.getUniqueId();
        caseManager.getPlugin().getDatabaseExecutor().completeSync(
                caseManager.getPlugin().getDatabaseExecutor().supply(playerId, () -> {
                    caseManager.getPlugin().getDatabase().addKeys(playerId, keyId, amount);
                    return caseManager.getPlugin().getDatabase().getKeys(playerId, keyId);
                }),
                balance -> {
                    caseManager.updateCachedKeyBalance(playerId, keyId, balance);
                    inventory.setItem(slot, pane(
                            Material.LIME_STAINED_GLASS_PANE,
                            "&aПокупка завершена",
                            Collections.emptyList()));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.28f, 1.2f);
                    scheduleRefresh(player, holder.caseName(), definition.name(), slot, playerId, 30L);
                },
                failure -> {
                    player.setLevel(player.getLevel() + levels);
                    clickLock.remove(playerId);
                    player.sendMessage(caseManager.getPlugin().getUi().get("case-open-storage-error"));
                });
    }

    private void showInsufficientPayment(
            Player player,
            Inventory inventory,
            CaseDefinition definition,
            int slot,
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
                    " &#A6B0BAДля открытия нужен ещё один ключ",
                    "",
                    " &#C2CBD3Ключи",
                    " &#69747E↳ &#A7F3D0{have} / {need}",
                    ""
            );
            for (String line : configuredLore) {
                lore.add(replace(line, replacements));
            }
            player.sendMessage(caseManager.getPlugin().getUi().get("not-enough-keys",
                    "have", String.valueOf(have), "need", String.valueOf(need)));
        } else {
            title = "&#FF5A64Недостаточно уровней";
            lore.addAll(List.of(
                    "",
                    " &#A6B0BAДля покупки не хватает уровней опыта",
                    "",
                    " &#C2CBD3Уровни",
                    ColorUtil.paint(" &#69747E↳ &#A7F3D0", player.getLevel()) + " / " + buyLevels,
                    ""
            ));
        }
        inventory.setItem(slot, texturedPane(GuiDesign.NO_KEYS, title, lore));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.24f, 1.0f);
    }

    private void scheduleRefresh(Player player, String menuCase, String buttonCase, int slot, UUID playerId, long delay) {
        new PluginRunnable() {
            @Override
            public void run() {
                clickLock.remove(playerId);
                if (!player.isOnline()) return;
                Inventory top = InventoryViewCompat.topInventory(player);
                if (top == null || !(top.getHolder() instanceof GuiSession holder)) return;
                if (holder.screen() != GuiSession.Screen.CASE || !Objects.equals(holder.caseName(), menuCase)) return;
                CaseDefinition current = caseManager.getCaseByName(buttonCase);
                if (current == null) return;
                caseManager.getPlugin().getGuiUpdates().setTopSlot(
                        player,
                        top,
                        slot,
                        caseManager.buildGuiOpenItem(player, current)
                );
            }
        }.runTaskLater(caseManager.getPlugin().tasks(), delay);
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
        ItemStack item = HeadUtil.create(texture, name);
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
