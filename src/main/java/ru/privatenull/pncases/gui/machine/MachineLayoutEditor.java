package ru.privatenull.pncases.gui.machine;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.model.AnimationType;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.gui.GuiDesign;
import ru.privatenull.pncases.gui.GuiSession;
import ru.privatenull.pncases.gui.machine.MachineGuiSupport.SlotRole;
import ru.privatenull.pncases.support.text.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static ru.privatenull.pncases.gui.machine.MachineGuiSupport.section;
import static ru.privatenull.pncases.support.item.ItemFactory.isRealItem;
import static ru.privatenull.pncases.support.item.ItemFactory.writeExactItem;

final class MachineLayoutEditor {

    private static final int ROLE_PREVIEW = 4;
    private static final int ROLE_OPEN = 10;
    private static final int ROLE_HISTORY = 11;
    private static final int ROLE_DECOR = 12;
    private static final int ROLE_ANIMATION = 14;
    private static final int ROLE_EMPTY = 15;
    private static final int ROLE_ICON = 16;
    private static final int ROLE_BACK = 22;

    private final CaseManager caseManager;
    private final MachineGuiSupport support;
    private final MachineItemFactory items;
    private final MachineMenuScreen menuScreen;

    MachineLayoutEditor(CaseManager caseManager, MachineGuiSupport support,
                        MachineItemFactory items, MachineMenuScreen menuScreen) {
        this.caseManager = caseManager;
        this.support = support;
        this.items = items;
        this.menuScreen = menuScreen;
    }

    void open(Player player, String caseName) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) return;
        Inventory inventory = Bukkit.createInventory(
                GuiSession.machineLayout(caseName), definition.guiLayout().size(),
                caseManager.getPlugin().getUi().text(
                        "machine.titles.layout", "&0Раскладка | {case}", items.replacements(definition))
        );
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, slotItem(definition, slot));
        }
        caseManager.getPlugin().openGui(player, inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.15f);
    }

    void handleClick(Player player, InventoryClickEvent event, CaseDefinition definition, int slot) {
        if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
            menuScreen.open(player, definition.name());
            return;
        }
        if (slot < 0 || slot >= definition.guiLayout().size()) return;
        openRolePicker(player, definition, slot);
    }

    void handleRoleClick(GuiSession holder, Player player, InventoryClickEvent event,
                         CaseDefinition definition, int clickedSlot) {
        int targetSlot = holder.selection();
        if (targetSlot < 0 || targetSlot >= definition.guiLayout().size()) {
            open(player, definition.name());
            return;
        }
        if (clickedSlot == ROLE_BACK) {
            open(player, definition.name());
            return;
        }

        SlotRole current = support.roleAt(definition, targetSlot);
        if (clickedSlot == ROLE_ICON) {
            ItemStack cursor = event.getCursor();
            if (!isRealItem(cursor)) {
                player.sendMessage(ColorUtil.colorize("&#FC7165Положите нужный предмет на курсор и нажмите кнопку ещё раз."));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.18f, 1.0f);
                return;
            }
            applyCursorItem(player, definition, targetSlot, current, cursor);
            CaseDefinition updated = caseManager.getCaseByName(definition.name());
            if (updated != null) openRolePicker(player, updated, targetSlot);
            return;
        }

        SlotRole selected = roleAtButton(clickedSlot);
        if (selected == null) return;
        if (current == SlotRole.OPEN && selected != SlotRole.OPEN) {
            player.sendMessage(ColorUtil.colorize("&#FC7165Сначала перенесите «Открытие» в другой слот. После этого текущий слот можно изменить."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.18f, 1.0f);
            openRolePicker(player, definition, targetSlot);
            return;
        }
        if (current == selected) {
            openRolePicker(player, definition, targetSlot);
            return;
        }
        support.update(player, definition.name(), root ->
                setRole(section(root, "gui"), definition, targetSlot, current, selected));
        open(player, definition.name());
    }

    private void openRolePicker(Player player, CaseDefinition definition, int slot) {
        SlotRole current = support.roleAt(definition, slot);
        Inventory inventory = Bukkit.createInventory(
                GuiSession.machineLayoutRole(definition.name(), slot), 27,
                ColorUtil.colorize("&0Архитектор слота №" + slot));
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        inventory.setItem(ROLE_PREVIEW, slotItem(definition, slot));
        inventory.setItem(ROLE_OPEN, roleButton(definition, SlotRole.OPEN, current));
        inventory.setItem(ROLE_HISTORY, roleButton(definition, SlotRole.HISTORY, current));
        inventory.setItem(ROLE_DECOR, roleButton(definition, SlotRole.DECOR, current));
        inventory.setItem(ROLE_ANIMATION, roleButton(definition, SlotRole.ANIMATION, current));
        inventory.setItem(ROLE_EMPTY, roleButton(definition, SlotRole.EMPTY, current));
        inventory.setItem(ROLE_ICON, items.builtInButton(MachineIcon.LAYOUT_ICON,
                items.name("Иконка слота"), items.lore(
                        "Замена иконки предметом с курсора",
                        "Роль", roleName(current),
                        "Применить", null)));
        inventory.setItem(ROLE_BACK, items.backButton(definition));
        caseManager.getPlugin().openGui(player, inventory);
    }

    private ItemStack roleButton(CaseDefinition definition, SlotRole role, SlotRole current) {
        boolean selected = role == current;
        List<String> description = new ArrayList<>();
        description.add(selected ? "Эта роль уже назначена слоту" : roleDescription(role));
        if (role == SlotRole.OPEN) description.add("Кнопка открытия будет перемещена сюда");
        boolean locked = current == SlotRole.OPEN && role != SlotRole.OPEN;
        if (locked) description.add("&cСначала перенесите роль «Открытие»");
        List<String> lore = items.lore(description,
                List.of("Состояние", selected ? "&aВыбрано" : locked ? "&cНедоступно" : "&7Не выбрано"),
                selected || locked ? null : "Выбрать", null);
        return items.builtInButton(roleIcon(role),
                items.name(roleName(role)), lore);
    }

    private SlotRole roleAtButton(int slot) {
        return switch (slot) {
            case ROLE_OPEN -> SlotRole.OPEN;
            case ROLE_HISTORY -> SlotRole.HISTORY;
            case ROLE_DECOR -> SlotRole.DECOR;
            case ROLE_ANIMATION -> SlotRole.ANIMATION;
            case ROLE_EMPTY -> SlotRole.EMPTY;
            default -> null;
        };
    }

    private void applyCursorItem(Player player, CaseDefinition definition, int slot,
                                 SlotRole role, ItemStack cursor) {
        support.update(player, definition.name(), root -> {
            ConfigurationSection gui = section(root, "gui");
            switch (role) {
                case EMPTY -> {
                    writeExactItem(section(gui, "decor"), "item", cursor);
                    setRole(gui, definition, slot, role, SlotRole.DECOR);
                }
                case DECOR -> writeExactItem(section(gui, "decor"), "item", cursor);
                case OPEN -> writeExactItem(gui, "button", cursor);
                case HISTORY -> writeExactItem(section(gui, "history"), "empty", cursor);
                case ANIMATION -> { }
            }
        });
    }

    private void setRole(ConfigurationSection gui, CaseDefinition definition, int slot,
                         SlotRole current, SlotRole role) {
        Set<Integer> decor = readSlots(gui.getConfigurationSection("decor"),
                definition.guiLayout().decorSlots(), definition.guiLayout().size());
        Set<Integer> history = readSlots(gui.getConfigurationSection("history"),
                definition.guiLayout().historySlots(), definition.guiLayout().size());
        decor.remove(slot);
        history.remove(slot);
        if (current == SlotRole.ANIMATION && role != SlotRole.ANIMATION) gui.set("animation_slot", -1);
        switch (role) {
            case OPEN -> {
                int previousOpen = definition.guiLayout().openSlot();
                if (previousOpen != slot) {
                    decor.remove(previousOpen);
                    history.remove(previousOpen);
                    if (gui.getInt("animation_slot", definition.guiLayout().animationSlot()) == previousOpen) {
                        gui.set("animation_slot", -1);
                    }
                }
                gui.set("open_slot", slot);
            }
            case ANIMATION -> gui.set("animation_slot", slot);
            case DECOR -> decor.add(slot);
            case HISTORY -> history.add(slot);
            case EMPTY -> { }
        }
        section(gui, "decor").set("slots", new ArrayList<>(decor));
        section(gui, "history").set("slots", new ArrayList<>(history));
    }

    private Set<Integer> readSlots(ConfigurationSection source, List<Integer> fallback, int size) {
        if (source == null || !source.isList("slots")) return new TreeSet<>(fallback);
        Set<Integer> slots = new TreeSet<>();
        for (Object raw : source.getList("slots", List.of())) {
            try {
                int slot = raw instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(raw));
                if (slot >= 0 && slot < size) slots.add(slot);
            } catch (NumberFormatException ignored) {
            }
        }
        return slots;
    }

    private ItemStack slotItem(CaseDefinition definition, int slot) {
        SlotRole role = support.roleAt(definition, slot);
        ItemStack item = roleItem(definition, role);
        String roleName = roleName(role);
        String itemName = role == SlotRole.EMPTY ? ""
                : caseManager.getPlugin().getItemLocalization().getPlainName(item);
        List<String> parameters = new ArrayList<>(List.of(
                "Номер", String.valueOf(slot),
                "Роль", role.color + roleName));
        if (!itemName.isEmpty()) parameters.addAll(List.of("Иконка", itemName));
        List<String> lore = items.lore(
                List.of("Настройка роли и иконки слота", "Q закрывает редактор"),
                parameters, "Изменить", null);
        return items.configuredButton("machine.layout.slot", item, items.name(roleName), lore, definition,
                "slot", String.valueOf(slot), "role", roleName, "role_color", role.color,
                "role-color", role.color, "item", itemName, "available", "&aда");
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
            case EMPTY -> new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        };
        if (item == null || item.getType().isAir()) return new ItemStack(Material.STONE_BUTTON);
        ItemStack clone = item.clone();
        clone.setAmount(1);
        return clone;
    }

    private MachineIcon roleIcon(SlotRole role) {
        return switch (role) {
            case OPEN -> MachineIcon.LAYOUT_OPEN;
            case HISTORY -> MachineIcon.LAYOUT_HISTORY;
            case DECOR -> MachineIcon.LAYOUT_DECOR;
            case ANIMATION -> MachineIcon.LAYOUT_ANIMATION;
            case EMPTY -> MachineIcon.LAYOUT_EMPTY;
        };
    }

    private String roleDescription(SlotRole role) {
        return switch (role) {
            case OPEN -> "Запускает открытие кейса";
            case HISTORY -> "Показывает последние выигрыши";
            case DECOR -> "Формирует рамку и фон меню";
            case ANIMATION -> "Открывает выбор анимации";
            case EMPTY -> "Оставляет слот свободным";
        };
    }
}
