package ru.privatenull.gui.machine;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.gui.GuiSession;
import ru.privatenull.modules.ModuleId;
import ru.privatenull.modules.ModuleManager;
import ru.privatenull.pnlibrary.text.ColorUtil;

import java.util.List;
import java.util.Map;

final class MachineModulesScreen {

    private static final int SLOT_BACK = 40;
    private static final Map<Integer, ModuleId> MODULES = Map.of(
            11, ModuleId.RARITY,
            13, ModuleId.PREVIEW,
            15, ModuleId.GUI_ANIMATION,
            22, ModuleId.TITLE
    );

    private final CaseManager caseManager;
    private final MachineMainScreen mainScreen;
    private final MachineItemFactory items;

    MachineModulesScreen(CaseManager caseManager, MachineMainScreen mainScreen, MachineItemFactory items) {
        this.caseManager = caseManager;
        this.mainScreen = mainScreen;
        this.items = items;
    }

    void open(Player player, String caseName) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) return;
        Inventory inventory = Bukkit.createInventory(
                GuiSession.machine(GuiSession.Screen.MACHINE_MODULES, caseName),
                45,
                ColorUtil.colorize("&0Модули pnCases")
        );
        fill(inventory, definition);
        caseManager.getPlugin().openGui(player, inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.2f);
    }

    void handleClick(Player player, InventoryClickEvent event, CaseDefinition definition, int slot) {
        if (slot == SLOT_BACK) {
            mainScreen.open(player, definition.name());
            return;
        }
        ModuleId id = MODULES.get(slot);
        if (id == null) return;

        ModuleManager modules = caseManager.getPlugin().getModules();
        boolean next = !modules.isEnabled(id);
        if (!modules.setEnabled(id, next)) {
            player.sendMessage(ColorUtil.colorize("&#FC7165Не удалось сохранить состояние модуля."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.2f, 1.0f);
            return;
        }
        if (id == ModuleId.GUI_ANIMATION && !next) {
            caseManager.getPlugin().getGuiOpenAnimations().shutdown();
        }
        open(player, definition.name());
    }

    private void fill(Inventory inventory, CaseDefinition definition) {
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inventory.setItem(4, items.builtInButton(Material.COMPARATOR,
                "&#429F91Управление модулями", List.of(
                        "", "&#9EFC65 «Описание»",
                        " &7- &fНастройки действуют сразу для всех кейсов.",
                        " &7- &fЛКМ переключает выбранный модуль.", "")));
        for (Map.Entry<Integer, ModuleId> entry : MODULES.entrySet()) {
            inventory.setItem(entry.getKey(), moduleButton(definition, entry.getValue()));
        }
        inventory.setItem(SLOT_BACK, items.backButton(definition));
    }

    private ItemStack moduleButton(CaseDefinition definition, ModuleId id) {
        boolean enabled = caseManager.getPlugin().getModules().isEnabled(id);
        Material material = switch (id) {
            case RARITY -> Material.NETHER_STAR;
            case PREVIEW -> Material.ENDER_EYE;
            case GUI_ANIMATION -> Material.CLOCK;
            case TITLE -> Material.NAME_TAG;
        };
        String name = switch (id) {
            case RARITY -> "Редкости";
            case PREVIEW -> "Содержимое кейса";
            case GUI_ANIMATION -> "Анимация GUI";
            case TITLE -> "Анимация заголовка";
        };
        String description = switch (id) {
            case RARITY -> "Оформление наград по редкости.";
            case PREVIEW -> "Просмотр списка наград перед открытием.";
            case GUI_ANIMATION -> "Плавное появление предметов в меню.";
            case TITLE -> "Плавное появление названия меню.";
        };
        return items.selectable("machine.modules." + id.configKey(), material, enabled,
                name, List.of(
                        "", "&#9EFC65 «Назначение»",
                        " &7- &f" + description,
                        "", "&#65D1FC «Сейчас»",
                        " &7- &fСтатус: " + (enabled ? "&aвключён" : "&cвыключен"),
                        "", "&#FCA965 «Управление»",
                        " &7- &fЛКМ — " + (enabled ? "выключить" : "включить"), ""),
                definition);
    }
}
