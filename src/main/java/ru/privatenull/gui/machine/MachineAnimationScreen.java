package ru.privatenull.gui.machine;

import ru.privatenull.gui.GuiSession;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.AnimationType;
import ru.privatenull.cases.model.AnimationType.Category;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.pnlibrary.compat.BukkitCompat.InventoryViewCompat;
import ru.privatenull.gui.GuiDesign;
import ru.privatenull.pnlibrary.compat.ServerCapabilities;
import ru.privatenull.pnlibrary.compat.ServerVersion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static ru.privatenull.gui.machine.MachineGuiSupport.SLOT_ANIMATION_MODE;
import static ru.privatenull.gui.machine.MachineGuiSupport.SLOT_BACK;

final class MachineAnimationScreen {

    private static final int STANDARD_CATEGORY_SLOT = 20;
    private static final int MOBS_CATEGORY_SLOT = 24;
    private static final int[] ANIMATION_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final CaseManager caseManager;
    private final MachineItemFactory items;

    MachineAnimationScreen(CaseManager caseManager, MachineItemFactory items) {
        this.caseManager = caseManager;
        this.items = items;
    }

    void open(Player player, String caseName) {
        open(player, caseName, null);
    }

    void openCategory(Player player, String caseName, Category category) {
        open(player, caseName, category);
    }

    private void open(Player player, String caseName, Category category) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) return;

        Inventory current = InventoryViewCompat.topInventory(player);
        if (current != null && current.getHolder() instanceof GuiSession holder
                && holder.screen() == GuiSession.Screen.MACHINE_ANIMATION
                && holder.caseName().equalsIgnoreCase(definition.name())) {
            holder.animationCategory(category);
        }
        if (MachineGuiSupport.refreshIfOpen(
                caseManager, player, GuiSession.Screen.MACHINE_ANIMATION, definition.name(),
                inventory -> fill(inventory, definition, category))) {
            return;
        }

        Inventory inventory = Bukkit.createInventory(
                GuiSession.machineAnimations(caseName, category),
                54,
                caseManager.getPlugin().getUi().text(
                        "machine.titles.animation",
                        "&0Настройка анимации",
                        items.replacements(definition)
                )
        );
        fill(inventory, definition, category);
        caseManager.getPlugin().openGui(player, inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    private void fill(Inventory inventory, CaseDefinition definition, Category category) {
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inventory.setItem(4, items.configuredButton(
                "machine.animation.header",
                GuiDesign.ANIMATION,
                "&#82DCFF&lНастройка анимации",
                headerLore(),
                definition
        ));
        inventory.setItem(SLOT_ANIMATION_MODE, modeButton(definition));
        if (category == null) {
            inventory.setItem(STANDARD_CATEGORY_SLOT, categoryButton(Category.STANDARD));
            inventory.setItem(MOBS_CATEGORY_SLOT, categoryButton(Category.MOBS));
        } else {
            renderCategory(inventory, definition, category, ANIMATION_SLOTS);
        }
        inventory.setItem(SLOT_BACK, items.backButton(definition));
    }

    AnimationType[] availableTypes() {
        if (ServerVersion.current().isReleaseLine(1, 16)) {
            return new AnimationType[]{AnimationType.FORTUNE_RING};
        }
        if (!ServerVersion.current().isAtLeast(1, 21, 0)
                || !ServerCapabilities.hasDisplayEntities()) {
            return Arrays.stream(AnimationType.values())
                    .filter(type -> type != AnimationType.PILLAGER_RAID
                            && type != AnimationType.DRAGON
                            && type != AnimationType.MIMIC)
                    .toArray(AnimationType[]::new);
        }
        return AnimationType.values();
    }

    AnimationType nextMode(AnimationType current, boolean backwards) {
        return nextMode(current, availableTypes(), backwards);
    }

    static AnimationType nextMode(AnimationType current, AnimationType[] available, boolean backwards) {
        int optionCount = available.length + 1;
        int currentIndex = 0;
        if (current != null) {
            for (int index = 0; index < available.length; index++) {
                if (available[index] == current) {
                    currentIndex = index + 1;
                    break;
                }
            }
        }
        int nextIndex = backwards
                ? (currentIndex - 1 + optionCount) % optionCount
                : (currentIndex + 1) % optionCount;
        return nextIndex == 0 ? null : available[nextIndex - 1];
    }

    Category categoryAtSlot(int slot) {
        if (slot == STANDARD_CATEGORY_SLOT) return Category.STANDARD;
        if (slot == MOBS_CATEGORY_SLOT) return Category.MOBS;
        return null;
    }

    AnimationType typeAtSlot(int slot, Category category) {
        return typeAtSlot(slot, category, ANIMATION_SLOTS);
    }

    private ItemStack modeButton(CaseDefinition definition) {
        boolean legacy = ServerVersion.current().isReleaseLine(1, 16);
        AnimationType fixed = legacy ? AnimationType.FORTUNE_RING : definition.fixedAnimation();
        boolean selected = fixed == null;
        String state = legacy ? "&eОграничено версией 1.16.5"
                : selected ? "&aВыбрано сейчас" : "&7Нажмите, чтобы разрешить выбор игроку";
        return items.builtInButton(GuiDesign.MENU,
                "&#82DCFF&lВыбор игрока",
                List.of("", "&#9EFC65 «Основная информация»",
                        " &7- &fИгрок сам выбирает анимацию",
                        " &7- &fперед открытием этого кейса.",
                        "", "&#65D1FC «Текущее состояние»",
                        " &7- &fСтатус: " + state,
                        "", "&#FCA965 «Управление»",
                        " &7- &fЛКМ — выбрать", ""));
    }

    void refreshMode(Inventory inventory, CaseDefinition definition) {
        if (inventory != null && definition != null) {
            inventory.setItem(SLOT_ANIMATION_MODE, modeButton(definition));
        }
    }

    void refreshMode(Player player, CaseDefinition definition) {
        if (definition != null) {
            Inventory inventory = InventoryViewCompat.topInventory(player);
            if (inventory != null) {
                caseManager.getPlugin().getGuiUpdates().setTopSlot(
                        player, inventory, SLOT_ANIMATION_MODE, modeButton(definition));
            }
        }
    }

    private void renderCategory(Inventory inventory, CaseDefinition definition,
                                Category category, int[] slots) {
        AnimationType[] types = Arrays.stream(availableTypes())
                .filter(type -> type.category() == category)
                .toArray(AnimationType[]::new);
        for (int index = 0; index < types.length && index < slots.length; index++) {
            inventory.setItem(slots[index], animationButton(types[index], definition.fixedAnimation() == types[index]));
        }
    }

    private AnimationType typeAtSlot(int slot, Category category, int[] slots) {
        AnimationType[] types = Arrays.stream(availableTypes())
                .filter(type -> type.category() == category)
                .toArray(AnimationType[]::new);
        for (int index = 0; index < types.length && index < slots.length; index++) {
            if (slots[index] == slot) return types[index];
        }
        return null;
    }

    private ItemStack categoryButton(Category category) {
        return items.builtInButton(
                GuiDesign.head(GuiDesign.categoryTexture(category), GuiDesign.categoryName(category)),
                GuiDesign.categoryName(category),
                List.of(
                "",
                "&#9EFC65 «Что находится внутри»",
                category == Category.MOBS
                        ? " &7- &fСценарии с существами"
                        : " &7- &fЭффектные визуальные сценарии",
                category == Category.MOBS
                        ? " &7- &fи интерактивным открытием."
                        : " &7- &fвокруг самого кейса.",
                "",
                "&#FCA965 «Управление»",
                " &7- &fЛКМ — открыть раздел",
                ""
        ));
    }

    private ItemStack animationButton(AnimationType type, boolean selected) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("&#9EFC65 «Как выглядит»");
        for (String line : GuiDesign.animationDescription(type)) {
            lore.add(" &7- &f" + line);
        }
        lore.add("");
        lore.add(selected ? "&#65FC83 «Текущее состояние»" : "&#FCA965 «Управление»");
        lore.add(selected
                ? " &7- &fАнимация закреплена за кейсом."
                : " &7- &fЛКМ — закрепить за кейсом");
        lore.add("");
        return items.builtInButton(
                GuiDesign.animation(type),
                GuiDesign.animationSelectionName(type),
                lore
        );
    }

    private List<String> headerLore() {
        if (ServerVersion.current().isReleaseLine(1, 16)) {
            return List.of(
                    "",
                    "&#9EFC65 «Совместимость»",
                    " &7- &fДля Minecraft 1.16.5 доступен",
                    " &7- &fтолько Круг фортуны.",
                    ""
            );
        }
        return List.of(
                "",
                "&#9EFC65 «Основная информация»",
                " &7- &fВыберите постоянную анимацию",
                " &7- &fили разрешите выбор игроку.",
                ""
        );
    }

}
