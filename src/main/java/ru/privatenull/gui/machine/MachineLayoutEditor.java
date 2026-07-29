package ru.privatenull.gui.machine;

import ru.privatenull.gui.GuiSession;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.AnimationType;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.gui.GuiDesign;
import ru.privatenull.gui.machine.MachineGuiSupport.SlotRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static ru.privatenull.gui.machine.MachineGuiSupport.section;
import static ru.privatenull.util.ItemFactory.isRealItem;
import static ru.privatenull.util.ItemFactory.writeExactItem;
import static ru.privatenull.util.ItemFactory.readableItemName;

final class MachineLayoutEditor {

    private final CaseManager caseManager;
    private final MachineGuiSupport support;
    private final MachineItemFactory items;

    MachineLayoutEditor(CaseManager caseManager, MachineGuiSupport support, MachineItemFactory items) {
        this.caseManager = caseManager;
        this.support = support;
        this.items = items;
    }

    void open(Player player, String caseName) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) return;
        if (MachineGuiSupport.refreshIfOpen(
                caseManager, player, GuiSession.Screen.MACHINE_LAYOUT, definition.name(),
                inventory -> fill(inventory, definition))) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(
                GuiSession.machine(GuiSession.Screen.MACHINE_LAYOUT, caseName),
                54,
                caseManager.getPlugin().getUi().text(
                        "machine.titles.layout", "&0Разметка меню кейса", items.replacements(definition))
        );
        fill(inventory, definition);
        caseManager.getPlugin().getGuiOpenAnimations().open(player, inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.15f);
    }

    void handleClick(Player player, InventoryClickEvent event, CaseDefinition definition, int slot) {
        SlotRole current = support.roleAt(definition, slot);
        ItemStack cursor = event.getCursor();
        if (isRealItem(cursor)) {
            applyCursorItem(player, definition, slot, current, cursor);
            return;
        }

        SlotRole next = support.stepRole(current, event.isRightClick());
        if (isRequired(current) && isRequired(next)) {
            swapRequiredRoles(player, definition, slot, current, next);
            return;
        }
        if (isRequired(current) && next != current) {
            player.sendMessage(caseManager.getPlugin().getUi().get(
                    "machine-layout-required-slot", "slot", current.displayName));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.18f, 1.0f);
            return;
        }
        if (current == SlotRole.ANIMATION && next != current) {
            support.update(player, definition.name(), root -> {
                ConfigurationSection gui = section(root, "gui");
                gui.set("animation_slot", -1);
                setRole(gui, definition, slot, next);
            });
            refresh(player, definition.name());
            return;
        }
        applyRole(player, definition, slot, next);
    }

    private void fill(Inventory inventory, CaseDefinition definition) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, slotItem(definition, slot));
        }
    }

    private void refresh(Player player, String caseName) {
        CaseDefinition updated = caseManager.getCaseByName(caseName);
        if (updated == null) {
            player.closeInventory();
            return;
        }
        if (!MachineGuiSupport.refreshIfOpen(
                caseManager, player, GuiSession.Screen.MACHINE_LAYOUT, caseName,
                inventory -> fill(inventory, updated))) {
            open(player, caseName);
        }
    }

    private void applyCursorItem(Player player, CaseDefinition definition, int slot,
                                 SlotRole role, ItemStack cursor) {
        if (role == SlotRole.EMPTY) {
            support.update(player, definition.name(), root -> {
                ConfigurationSection gui = section(root, "gui");
                writeExactItem(section(gui, "decor"), "item", cursor);
                setRole(gui, definition, slot, SlotRole.DECOR);
            });
        } else if (role == SlotRole.DECOR) {
            support.update(player, definition.name(), root ->
                    writeExactItem(section(section(root, "gui"), "decor"), "item", cursor));
        } else if (role == SlotRole.OPEN) {
            support.update(player, definition.name(), root ->
                    writeExactItem(section(root, "gui"), "button", cursor));
        } else if (role == SlotRole.HISTORY) {
            support.update(player, definition.name(), root -> writeExactItem(
                    section(section(root, "gui"), "history"), "empty", cursor));
        }
        refresh(player, definition.name());
    }

    private void swapRequiredRoles(Player player, CaseDefinition definition, int clickedSlot,
                                   SlotRole current, SlotRole next) {
        int nextSlot = requiredSlot(definition, next);
        support.update(player, definition.name(), root -> {
            ConfigurationSection gui = section(root, "gui");
            Set<Integer> decor = readSlots(gui.getConfigurationSection("decor"), definition.guiLayout().decorSlots());
            Set<Integer> history = readSlots(gui.getConfigurationSection("history"), definition.guiLayout().historySlots());
            decor.remove(clickedSlot);
            decor.remove(nextSlot);
            history.remove(clickedSlot);
            history.remove(nextSlot);
            setRequiredSlot(gui, next, clickedSlot);
            setRequiredSlot(gui, current, nextSlot);
            section(gui, "decor").set("slots", new ArrayList<>(decor));
            section(gui, "history").set("slots", new ArrayList<>(history));
        });
        refresh(player, definition.name());
    }

    private void applyRole(Player player, CaseDefinition definition, int slot, SlotRole role) {
        support.update(player, definition.name(), root ->
                setRole(section(root, "gui"), definition, slot, role));
        refresh(player, definition.name());
    }

    private void setRole(ConfigurationSection gui, CaseDefinition definition, int slot, SlotRole role) {
        Set<Integer> decor = readSlots(gui.getConfigurationSection("decor"), definition.guiLayout().decorSlots());
        Set<Integer> history = readSlots(gui.getConfigurationSection("history"), definition.guiLayout().historySlots());
        decor.remove(slot);
        history.remove(slot);

        switch (role) {
            case OPEN, ANIMATION -> setRequiredSlot(gui, role, slot);
            case DECOR -> decor.add(slot);
            case HISTORY -> history.add(slot);
            case EMPTY -> { }
        }
        section(gui, "decor").set("slots", new ArrayList<>(decor));
        section(gui, "history").set("slots", new ArrayList<>(history));
    }

    private static boolean isRequired(SlotRole role) {
        return role == SlotRole.OPEN;
    }

    private static void setRequiredSlot(ConfigurationSection gui, SlotRole role, int slot) {
        switch (role) {
            case OPEN -> gui.set("open_slot", slot);
            case ANIMATION -> gui.set("animation_slot", slot);
            default -> throw new IllegalArgumentException("Not a required slot role: " + role);
        }
    }

    private static int requiredSlot(CaseDefinition definition, SlotRole role) {
        return switch (role) {
            case OPEN -> definition.guiLayout().openSlot();
            case ANIMATION -> definition.guiLayout().animationSlot();
            default -> throw new IllegalArgumentException("Not a required slot role: " + role);
        };
    }

    private Set<Integer> readSlots(ConfigurationSection section, List<Integer> fallback) {
        if (section == null || !section.isList("slots")) return new TreeSet<>(fallback);
        Set<Integer> slots = new TreeSet<>();
        for (Object raw : section.getList("slots", List.of())) {
            if (raw instanceof Number number) {
                slots.add(number.intValue());
                continue;
            }
            try {
                slots.add(Integer.parseInt(String.valueOf(raw)));
            } catch (NumberFormatException ex) {
                caseManager.getPlugin().getLogger().warning(
                        "Machine GUI: пропущен неверный номер слота: " + raw);
            }
        }
        slots.removeIf(slot -> slot == null || slot < 0 || slot >= 54);
        return slots;
    }

    private ItemStack slotItem(CaseDefinition definition, int slot) {
        SlotRole role = support.roleAt(definition, slot);
        ItemStack item = roleItem(definition, role);
        String roleName = roleName(role);
        String itemName = role == SlotRole.EMPTY ? "" : readableItemName(item);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("&#65D1FC «Сейчас»");
        lore.add(" &7- &fСлот: &#FCA865" + slot);
        lore.add(" &7- &fРоль: " + role.color + roleName);
        if (role != SlotRole.EMPTY) lore.add(" &7- &fПредмет: &#FCA865" + itemName);
        if (slot >= definition.guiLayout().size()) {
            lore.add("");
            lore.add("&#FC7165 «Ограничение»");
            lore.add(" &7- &fСлот появится после увеличения меню.");
        }
        if (role == SlotRole.ANIMATION && definition.fixedAnimation() != null) {
            lore.add("");
            lore.add("&#FC7165 «Ограничение»");
            lore.add(" &7- &fАнимация кейса зафиксирована.");
        }
        if (role == SlotRole.ANIMATION) {
            lore.add(" &7- &fСмените роль, чтобы убрать кнопку.");
        }
        lore.add("");
        lore.add("&#FCA965 «Управление»");
        lore.add(" &7- &fЛКМ — следующая роль");
        lore.add(" &7- &fПКМ — предыдущая роль");
        if (role == SlotRole.EMPTY || role == SlotRole.DECOR
                || role == SlotRole.OPEN || role == SlotRole.HISTORY) {
            lore.add(" &7- &fПредмет на курсоре — заменить иконку");
        }
        lore.add("");
        return items.configuredButton("machine.layout.slot", item, role.color + roleName, lore, definition,
                "slot", String.valueOf(slot),
                "role", roleName,
                "role_color", role.color,
                "role-color", role.color,
                "item", itemName,
                "available", slot >= definition.guiLayout().size() ? "&cнет" : "&aда");
    }

    private String roleName(SlotRole role) {
        String key = role.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        return caseManager.getPlugin().getUi().text(
                "machine.layout.roles." + key + ".name", role.displayName);
    }

    private ItemStack roleItem(CaseDefinition definition, SlotRole role) {
        ItemStack item = switch (role) {
            case OPEN -> definition.openButton();
            case ANIMATION -> GuiDesign.animation(AnimationType.FORTUNE_RING);
            case HISTORY -> definition.guiLayout().emptyHistoryItem();
            case DECOR -> definition.guiLayout().decorItem();
            case EMPTY -> new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        };
        Material fallback = switch (role) {
            case OPEN -> Material.CHEST;
            case ANIMATION -> Material.CLOCK;
            case HISTORY -> Material.BARRIER;
            case DECOR -> Material.GRAY_STAINED_GLASS_PANE;
            case EMPTY -> Material.BLACK_STAINED_GLASS_PANE;
        };
        if (item == null || item.getType().isAir()) return new ItemStack(fallback);
        ItemStack clone = item.clone();
        clone.setAmount(1);
        return clone;
    }

}
