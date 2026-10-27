package ru.privatenull.pncases.gui.machine;

import ru.privatenull.pncases.gui.GuiSession;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.animation.core.AnimationRequirements;
import ru.privatenull.pncases.cases.model.AnimationType;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.gui.AnimationCategory;
import ru.privatenull.pncases.gui.GuiDesign;
import ru.privatenull.pncases.support.compat.BukkitCompat.InventoryViewCompat;
import ru.privatenull.pncases.gui.machine.MachineItemFactory.Hint;
import ru.privatenull.pncases.support.compat.ServerCapabilities;
import ru.privatenull.pncases.support.compat.ServerVersion;

import java.util.Arrays;
import java.util.List;

import static ru.privatenull.pncases.gui.machine.MachineGuiSupport.SLOT_ANIMATION_MODE;
import static ru.privatenull.pncases.gui.machine.MachineGuiSupport.SLOT_ANIMATION_CATEGORY;
import static ru.privatenull.pncases.gui.machine.MachineGuiSupport.SLOT_BACK;

/** Choice of the opening animation: one fixed animation for the case, or free choice by the player. */
final class MachineAnimationScreen {

    private final CaseManager caseManager;
    private final MachineItemFactory items;

    MachineAnimationScreen(CaseManager caseManager, MachineItemFactory items) {
        this.caseManager = caseManager;
        this.items = items;
    }

    void open(Player player, String caseName) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) return;

        int page = currentPage(player, definition.name());
        if (MachineGuiSupport.refreshIfOpen(
                caseManager, player, GuiSession.Screen.MACHINE_ANIMATION, definition.name(),
                inventory -> fill(inventory, definition, page))) {
            return;
        }

        Inventory inventory = Bukkit.createInventory(
                GuiSession.machineAnimations(caseName),
                54,
                caseManager.getPlugin().getUi().text(
                        "machine.titles.animation",
                        "&0Анимация открытия",
                        items.replacements(definition)
                )
        );
        fill(inventory, definition, 0);
        caseManager.getPlugin().openGui(player, inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    private static int currentPage(Player player, String caseName) {
        Inventory top = InventoryViewCompat.topInventory(player);
        if (top != null && top.getHolder() instanceof GuiSession holder
                && holder.screen() == GuiSession.Screen.MACHINE_ANIMATION
                && holder.caseName().equalsIgnoreCase(caseName)) {
            return holder.page();
        }
        return 0;
    }

    /** Switches the group shown in the list: forward on left click, back on right click. */
    void cycleCategory(Player player, String caseName, boolean forward) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) return;
        int size = AnimationCategory.present(Arrays.asList(availableTypes())).size();
        int page = AnimationCategory.cycle(currentPage(player, caseName), size, forward);
        MachineGuiSupport.refreshIfOpen(caseManager, player, GuiSession.Screen.MACHINE_ANIMATION,
                definition.name(), page, -1, inventory -> fill(inventory, definition, page));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, forward ? 1.35f : 1.05f);
    }

    private void fill(Inventory inventory, CaseDefinition definition, int page) {
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inventory.setItem(SLOT_ANIMATION_MODE, modeButton(definition));
        renderAnimations(inventory, definition, page);
        inventory.setItem(SLOT_ANIMATION_CATEGORY, categoryButton(page));
        inventory.setItem(SLOT_BACK, items.backButton(definition));
    }

    private ItemStack categoryButton(int page) {
        List<AnimationCategory> present = AnimationCategory.present(Arrays.asList(availableTypes()));
        AnimationCategory current = AnimationCategory.at(present, page);
        return items.builtInButton(GuiDesign.MENU,
                items.name("Категория: " + current.label()),
                items.hintLore(AnimationCategory.selectorLines(present, current),
                        List.of(),
                        Hint.left("следующая"), Hint.right("предыдущая")));
    }

    private List<AnimationType> visibleTypes(int page) {
        List<AnimationType> all = Arrays.asList(availableTypes());
        AnimationCategory current = AnimationCategory.at(AnimationCategory.present(all), page);
        return all.stream().filter(current::contains).toList();
    }

    AnimationType[] availableTypes() {
        if (ServerVersion.current().isReleaseLine(1, 16)) {
            return new AnimationType[]{AnimationType.FORTUNE_RING};
        }
        if (!ServerVersion.current().isAtLeast(1, 20, 0)
                || !ServerCapabilities.hasDisplayEntities()) {
            return Arrays.stream(AnimationType.values())
                    .filter(type -> type != AnimationType.PILLAGER_RAID
                            && type != AnimationType.DRAGON
                            && type != AnimationType.MIMIC
                            && type != AnimationType.STORM_DUEL
                            && type != AnimationType.PRISM
                            && type != AnimationType.STAR_CAROUSEL
                            && type != AnimationType.NEW_YEAR_TREE
                            && type != AnimationType.AUTUMN_MAPLE)
                    .toArray(AnimationType[]::new);
        }
        return AnimationType.values();
    }

    AnimationType typeAtSlot(int page, int slot) {
        List<AnimationType> types = visibleTypes(page);
        for (int index = 0; index < types.size() && index < GuiDesign.ANIMATION_LIST_SLOTS.size(); index++) {
            if (GuiDesign.ANIMATION_LIST_SLOTS.get(index) == slot) return types.get(index);
        }
        return null;
    }

    private ItemStack modeButton(CaseDefinition definition) {
        boolean legacy = ServerVersion.current().isReleaseLine(1, 16);
        AnimationType fixed = legacy ? AnimationType.FORTUNE_RING : definition.fixedAnimation();
        boolean selected = fixed == null;
        String state = legacy ? "&eНедоступно на 1.16.5"
                : selected ? "&aВключён" : "&cВыключен";
        ItemStack item = items.builtInButton(MachineIcon.ANIMATION_MODE,
                items.name("Свободный выбор"),
                items.hintLore(
                        List.of("Игрок сам выбирает анимацию перед открытием",
                                legacy ? "На 1.16.5 доступен только Круг фортуны" : "Или выберите одну ниже для всех"),
                        List.of("Состояние", state),
                        legacy || selected ? new Hint[0] : new Hint[]{Hint.left("включить")}));
        if (selected && !legacy) MachineItemFactory.addGlow(item);
        return item;
    }

    private void renderAnimations(Inventory inventory, CaseDefinition definition, int page) {
        List<AnimationType> types = visibleTypes(page);
        if (types.isEmpty()) {
            inventory.setItem(22, items.builtInButton(GuiDesign.INFO, items.name("Скоро"),
                    List.of("&#A6B0BAВ этой категории пока нет анимаций.")));
        }
        for (int index = 0; index < types.size() && index < GuiDesign.ANIMATION_LIST_SLOTS.size(); index++) {
            AnimationType type = types.get(index);
            inventory.setItem(GuiDesign.ANIMATION_LIST_SLOTS.get(index),
                    animationButton(type, definition.fixedAnimation() == type, definition));
        }
    }

    private ItemStack animationButton(AnimationType type, boolean selected, CaseDefinition definition) {
        var availability = caseManager.animationAvailability(definition, type);
        if (!availability.available()) {
            return items.builtInButton(MachineIcon.ANIMATION_BLOCKED,
                    items.name(GuiDesign.animationSelectionName(type)),
                    items.hintLore(
                            List.of("У вас проблема с регионом или миром кейса",
                                    "Исправьте её, чтобы визуальные сущности могли появиться",
                                    "Игрокам эта анимация сейчас не показывается"),
                            List.of("Причина", "&c" + availability.reason()),
                            new Hint[0]));
        }
        List<String> parameters = new java.util.ArrayList<>(List.of(
                "Состояние", selected ? "&aВыбрана для всех игроков" : "&7Не выбрана"));
        if (AnimationRequirements.needsMobs(type)) {
            parameters.add("Место кейса");
            parameters.add("&aсущности могут появиться");
        }
        ItemStack item = items.builtInButton(
                GuiDesign.animation(type),
                items.name(GuiDesign.animationSelectionName(type)),
                items.hintLore(
                        GuiDesign.animationDescription(type),
                        parameters,
                        selected ? new Hint[0] : new Hint[]{Hint.left("выбрать для всех")})
        );
        if (selected) MachineItemFactory.addGlow(item);
        return item;
    }
}
