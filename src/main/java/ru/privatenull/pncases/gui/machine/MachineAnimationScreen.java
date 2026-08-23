package ru.privatenull.pncases.gui.machine;

import ru.privatenull.pncases.gui.GuiSession;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.model.AnimationType;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.gui.GuiDesign;
import ru.privatenull.pnlibrary.compat.ServerCapabilities;
import ru.privatenull.pnlibrary.compat.ServerVersion;

import java.util.Arrays;
import java.util.List;

import static ru.privatenull.pncases.gui.machine.MachineGuiSupport.SLOT_ANIMATION_MODE;
import static ru.privatenull.pncases.gui.machine.MachineGuiSupport.SLOT_BACK;

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

        if (MachineGuiSupport.refreshIfOpen(
                caseManager, player, GuiSession.Screen.MACHINE_ANIMATION, definition.name(),
                inventory -> fill(inventory, definition))) {
            return;
        }

        Inventory inventory = Bukkit.createInventory(
                GuiSession.machineAnimations(caseName),
                54,
                caseManager.getPlugin().getUi().text(
                        "machine.titles.animation",
                        "&0Режиссура открытия",
                        items.replacements(definition)
                )
        );
        fill(inventory, definition);
        caseManager.getPlugin().openGui(player, inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    private void fill(Inventory inventory, CaseDefinition definition) {
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inventory.setItem(4, items.configuredButton(
                "machine.animation.header",
                MachineIcon.ANIMATION_HEADER,
                items.name("Анимации"),
                headerLore(),
                definition
        ));
        inventory.setItem(SLOT_ANIMATION_MODE, modeButton(definition));
        renderAnimations(inventory, definition);
        inventory.setItem(SLOT_BACK, items.backButton(definition));
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
                            && type != AnimationType.STORM_DUEL)
                    .toArray(AnimationType[]::new);
        }
        return AnimationType.values();
    }

    AnimationType typeAtSlot(int slot) {
        AnimationType[] types = availableTypes();
        for (int index = 0; index < types.length && index < GuiDesign.ANIMATION_LIST_SLOTS.size(); index++) {
            if (GuiDesign.ANIMATION_LIST_SLOTS.get(index) == slot) return types[index];
        }
        return null;
    }

    private ItemStack modeButton(CaseDefinition definition) {
        boolean legacy = ServerVersion.current().isReleaseLine(1, 16);
        AnimationType fixed = legacy ? AnimationType.FORTUNE_RING : definition.fixedAnimation();
        boolean selected = fixed == null;
        String state = legacy ? "&eНедоступно на 1.16.5"
                : selected ? "&aВключён" : "&cВыключен";
        return items.builtInButton(MachineIcon.ANIMATION_MODE,
                items.name("Свободный выбор"),
                items.lore(
                        "Игрок сам выбирает анимацию перед открытием",
                        "Состояние", state,
                        legacy ? null : "Включить", null));
    }

    private void renderAnimations(Inventory inventory, CaseDefinition definition) {
        AnimationType[] types = availableTypes();
        for (int index = 0; index < types.length && index < GuiDesign.ANIMATION_LIST_SLOTS.size(); index++) {
            AnimationType type = types[index];
            inventory.setItem(GuiDesign.ANIMATION_LIST_SLOTS.get(index),
                    animationButton(type, definition.fixedAnimation() == type));
        }
    }

    private ItemStack animationButton(AnimationType type, boolean selected) {
        return items.builtInButton(
                GuiDesign.animation(type),
                items.name(GuiDesign.animationSelectionName(type)),
                items.lore(
                        GuiDesign.animationDescription(type),
                        List.of("Состояние", selected ? "&aВыбрана" : "&7Не выбрана"),
                        "Выбрать", null)
        );
    }

    private List<String> headerLore() {
        if (ServerVersion.current().isReleaseLine(1, 16)) {
            return items.lore(List.of("На Minecraft 1.16.5 доступен только Круг фортуны"),
                    List.of(), null, null);
        }
        return items.lore(List.of("Выбор сценария открытия кейса"),
                List.of(), null, null);
    }

}
