package ru.privatenull.pncases.gui.machine;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.gui.GuiSession;
import ru.privatenull.pnlibrary.text.ColorUtil;

import java.util.List;
import java.util.Map;

/** Two ordinary optional features; GUI and title animations are always active. */
final class MachineFeaturesScreen {
    private static final int SLOT_BACK = 40;
    private static final Map<Integer, String> FEATURES = Map.of(12, "rarity", 14, "preview");

    private final CaseManager caseManager;
    private final MachineMainScreen mainScreen;
    private final MachineItemFactory items;

    MachineFeaturesScreen(CaseManager caseManager, MachineMainScreen mainScreen, MachineItemFactory items) {
        this.caseManager = caseManager;
        this.mainScreen = mainScreen;
        this.items = items;
    }

    void open(Player player, String caseName) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) return;
        Inventory inventory = Bukkit.createInventory(
                GuiSession.machine(GuiSession.Screen.MACHINE_FEATURES, caseName), 45,
                ColorUtil.colorize("&0Возможности pnCases"));
        fill(inventory, definition);
        caseManager.getPlugin().openGui(player, inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.2f);
    }

    void handleClick(Player player, InventoryClickEvent event, CaseDefinition definition, int slot) {
        if (slot == SLOT_BACK) {
            mainScreen.open(player, definition.name());
            return;
        }
        String feature = FEATURES.get(slot);
        if (feature == null) return;
        boolean enabled = enabled(feature);
        if (!caseManager.getPlugin().setFeatureEnabled(feature, !enabled)) {
            player.sendMessage(ColorUtil.colorize("&#FC7165Не удалось сохранить настройку функции."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.2f, 1.0f);
            return;
        }
        open(player, definition.name());
    }

    private void fill(Inventory inventory, CaseDefinition definition) {
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inventory.setItem(4, items.builtInButton(MachineIcon.FEATURES_HEADER,
                items.name("Функции"), items.lore(
                        List.of("Дополнительные возможности pnCases"),
                        List.of(), null, null)));
        inventory.setItem(12, featureButton(definition, "rarity", MachineIcon.FEATURES_RARITY,
                "Редкости наград", "Цвет и обозначение ценности каждой награды."));
        inventory.setItem(14, featureButton(definition, "preview", MachineIcon.FEATURES_PREVIEW,
                "Каталог содержимого", "Просмотр полного пула до начала открытия."));
        inventory.setItem(SLOT_BACK, items.backButton(definition));
    }

    private ItemStack featureButton(CaseDefinition definition, String feature, MachineIcon icon,
                                    String name, String description) {
        boolean enabled = enabled(feature);
        return items.selectable("machine.features." + feature, icon, enabled, items.name(name), items.lore(
                description,
                "Состояние", enabled ? "&aВключено" : "&cВыключено",
                enabled ? "Выключить" : "Включить", null), definition);
    }

    private boolean enabled(String feature) {
        return "rarity".equals(feature)
                ? caseManager.getPlugin().rarityEnabled()
                : caseManager.getPlugin().previewEnabled();
    }
}
