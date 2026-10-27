package ru.privatenull.pncases.gui.machine;

import ru.privatenull.pncases.gui.GuiDesign;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.gui.GuiSession;
import ru.privatenull.pncases.gui.machine.MachineItemFactory.Hint;
import ru.privatenull.pncases.support.text.ColorUtil;

import java.util.List;

import static ru.privatenull.pncases.gui.machine.MachineGuiSupport.*;

/**
 * "Additional": everything that is optional for a case. Hologram, showcase and open-for-experience are cards
 * of this case (left click switches them, right click opens their settings); rarities and the content
 * preview are switches for the whole plugin. A card that is switched on glows.
 */
final class MachineExtrasScreen {

    private static final int SLOT_BACK_EXTRAS = 40;

    private final CaseManager caseManager;
    private final MachineGuiSupport state;
    private final MachineItemFactory items;

    MachineExtrasScreen(CaseManager caseManager, MachineGuiSupport state, MachineItemFactory items) {
        this.caseManager = caseManager;
        this.state = state;
        this.items = items;
    }

    static int backSlot() {
        return SLOT_BACK_EXTRAS;
    }

    void open(Player player, String caseName) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) return;
        if (MachineGuiSupport.refreshIfOpen(
                caseManager, player, GuiSession.Screen.MACHINE_FEATURES, definition.name(),
                inventory -> fill(inventory, definition))) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(
                GuiSession.machine(GuiSession.Screen.MACHINE_FEATURES, caseName), 45,
                ColorUtil.colorize("&0Дополнительно | " + definition.displayName()));
        fill(inventory, definition);
        caseManager.getPlugin().openGui(player, inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.2f);
    }

    /** Switches a plugin-wide feature ("rarity" or "preview"). Returns false when it could not be saved. */
    boolean toggleFeature(Player player, String feature) {
        boolean enabled = featureEnabled(feature);
        if (caseManager.getPlugin().setFeatureEnabled(feature, !enabled)) return true;
        player.sendMessage(ColorUtil.colorize("&#FC7165Не удалось сохранить настройку функции."));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.2f, 1.0f);
        return false;
    }

    static String featureAt(int slot) {
        return switch (slot) {
            case SLOT_EXTRA_RARITY -> "rarity";
            case SLOT_EXTRA_PREVIEW -> "preview";
            case SLOT_EXTRA_HISTORY -> "history";
            case SLOT_EXTRA_WHOLE -> "whole-numbers";
            default -> null;
        };
    }

    private void fill(Inventory inventory, CaseDefinition definition) {
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));

        boolean hologram = state.hologramEnabled(definition);
        inventory.setItem(SLOT_EXTRA_HOLOGRAM, card(hologram, MachineIcon.MAIN_HOLOGRAM,
                "machine.extras.hologram", "Голограмма",
                "Текст над кейсом с названием и подсказками для игроков",
                List.of("Состояние", state(hologram), "Высота", String.valueOf(state.hologramHeight(definition))),
                hologram, definition));

        boolean showcase = definition.idleParticles().enabled();
        var theme = definition.idleParticles().theme();
        List<String> showcaseLore = new java.util.ArrayList<>(List.of("Постоянные частицы над кейсом"));
        showcaseLore.addAll(theme.description());
        inventory.setItem(SLOT_EXTRA_SHOWCASE, items.selectable("machine.extras.showcase", MachineIcon.MAIN_SHOWCASE,
                showcase, items.name("Витрина"), items.hintLore(
                        showcaseLore,
                        List.of("Состояние", state(showcase), "Тема", theme.displayName()),
                        Hint.left(showcase ? "выключить" : "включить"), Hint.right("сменить тему"),
                        Hint.shiftRight("предыдущая тема")), definition));

        var proximity = definition.proximity();
        inventory.setItem(SLOT_EXTRA_PROXIMITY, items.selectable("machine.extras.proximity", Material.SNOWBALL,
                proximity.enabled(), items.name("Анимация у кейса"), items.hintLore(
                        List.of("Когда игрок подходит к кейсу, вокруг него разворачивается сцена",
                                "Музыка, Дед Мороз, снег, смена блоков — всё настраивается"),
                        List.of("Состояние", state(proximity.enabled()), "Сцена", proximity.scene().displayName()),
                        Hint.left(proximity.enabled() ? "выключить" : "включить"), Hint.right("настройки")), definition));

        int inside = caseManager.bundleCases(definition).size();
        inventory.setItem(SLOT_EXTRA_BUNDLE, items.selectable("machine.extras.bundle", Material.ENDER_CHEST,
                inside > 0, items.name("Кейсы внутри"), items.hintLore(
                        List.of("В меню этого кейса вместо одной кнопки открытия будет по кнопке",
                                "на каждый кейс внутри: свои ключи, награды и выбор количества. Остальное меню общее"),
                        List.of("Внутри", inside == 0 ? "ничего, кейс обычный" : inside + " кейсов"),
                        Hint.left("выбрать кейсы")), definition));

        boolean purchase = state.xpBuyEnabled(definition);
        inventory.setItem(SLOT_EXTRA_PURCHASE, card(purchase, MachineIcon.MAIN_PURCHASE,
                "machine.extras.purchase", "Открытие за опыт",
                "Игрок может открыть кейс за уровни опыта, если у него нет ключа",
                List.of("Состояние", state(purchase), "Стоимость", Math.max(0, definition.buyKeyWithXpLevels()) + " уровней"),
                purchase, definition));

        var recipes = caseManager.workbenchRecipes(definition.name());
        boolean crafting = recipes.stream().anyMatch(ru.privatenull.pncases.workbench.WorkbenchRecipe::usable);
        inventory.setItem(SLOT_EXTRA_WORKBENCH, items.selectable("machine.extras.workbench", Material.CRAFTING_TABLE,
                crafting, items.name("Крафт ключа"), items.hintLore(
                        List.of("Игрок собирает ключ этого кейса в обычном верстаке",
                                "Способов может быть несколько. Тот же список: /pncases workbench"),
                        List.of("Состояние", state(crafting), "Рецептов", String.valueOf(recipes.size())),
                        Hint.left("рецепты")), definition));

        boolean giftable = caseManager.giftable(definition.name());
        inventory.setItem(SLOT_EXTRA_GIFT, items.selectable("machine.extras.gift", GuiDesign.REWARD_ITEM,
                giftable, items.name("Передача ключей"), items.hintLore(
                        List.of("Игроки могут дарить ключи этого кейса другим игрокам",
                                "Выключите, чтобы ключи нельзя было передать"),
                        List.of("Состояние", state(giftable)),
                        Hint.left(giftable ? "запретить" : "разрешить")), definition));

        boolean rarity = featureEnabled("rarity");
        inventory.setItem(SLOT_EXTRA_RARITY, items.selectable("machine.extras.rarity", MachineIcon.FEATURES_RARITY,
                rarity, items.name("Редкости наград"), items.hintLore(
                        List.of("Цвет и обозначение ценности каждой награды", "Настройка для всех кейсов"),
                        List.of("Состояние", state(rarity)),
                        Hint.left(rarity ? "выключить" : "включить")), definition));

        boolean preview = featureEnabled("preview");
        inventory.setItem(SLOT_EXTRA_PREVIEW, items.selectable("machine.extras.preview", MachineIcon.FEATURES_PREVIEW,
                preview, items.name("Каталог содержимого"), items.hintLore(
                        List.of("Игрок видит весь пул наград до открытия", "Настройка для всех кейсов"),
                        List.of("Состояние", state(preview)),
                        Hint.left(preview ? "выключить" : "включить")), definition));

        boolean history = featureEnabled("history");
        inventory.setItem(SLOT_EXTRA_HISTORY, items.selectable("machine.extras.history", GuiDesign.CONTENT,
                history, items.name("История открытий"), items.hintLore(
                        List.of("Последние выпавшие награды в меню кейса",
                                "Выключите, чтобы история не записывалась и не показывалась",
                                "Настройка для всех кейсов"),
                        List.of("Состояние", state(history)),
                        Hint.left(history ? "выключить" : "включить")), definition));

        boolean whole = featureEnabled("whole-numbers");
        inventory.setItem(SLOT_EXTRA_WHOLE, items.selectable("machine.extras.whole-numbers", GuiDesign.REWARD_COINS,
                whole, items.name("Целые числа валюты"), items.hintLore(
                        List.of("Суммы пишутся как 500 монет, а не 500.0 монет",
                                "Выключите, чтобы показывать их как отдаёт экономика",
                                "Настройка для всех кейсов"),
                        List.of("Состояние", state(whole)),
                        Hint.left(whole ? "выключить" : "включить")), definition));

        inventory.setItem(SLOT_BACK_EXTRAS, items.backButton(definition));
    }

    /** A case card: left click switches it, right click opens its settings. */
    private ItemStack card(boolean on, MachineIcon icon, String path, String name, String description,
                           List<String> parameters, boolean enabled, CaseDefinition definition) {
        return items.selectable(path, icon, on, items.name(name), items.hintLore(
                List.of(description), parameters,
                Hint.left(enabled ? "выключить" : "включить"), Hint.right("настройки")), definition);
    }

    private static String state(boolean enabled) {
        return enabled ? "&aвключено" : "&cвыключено";
    }

    private boolean featureEnabled(String feature) {
        return switch (feature) {
            case "rarity" -> caseManager.getPlugin().rarityEnabled();
            case "history" -> caseManager.getPlugin().historyEnabled();
            case "whole-numbers" -> caseManager.getPlugin().wholeNumbers();
            default -> caseManager.getPlugin().previewEnabled();
        };
    }
}
