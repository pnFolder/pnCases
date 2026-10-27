package ru.privatenull.pncases.gui.machine;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.gui.GuiDesign;
import ru.privatenull.pncases.gui.GuiSession;
import ru.privatenull.pncases.gui.machine.MachineItemFactory.Hint;
import ru.privatenull.pncases.support.text.ColorUtil;
import ru.privatenull.pncases.workbench.ItemTags;
import ru.privatenull.pncases.workbench.WorkbenchRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static ru.privatenull.pncases.gui.machine.MachineGuiSupport.*;

/**
 * The key recipes of a case. A case may have several ways to make its key: the list shows them, a click opens
 * one. In the editor the administrator clicks a grid slot with a sample item on the cursor; its material, stack
 * size and PersistentDataContainer tags become the ingredient. Right click clears a slot.
 */
final class MachineWorkbenchScreen {

    static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    static final List<Integer> LIST_SLOTS = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25);

    private final CaseManager caseManager;
    private final MachineGuiSupport state;
    private final MachineItemFactory items;

    MachineWorkbenchScreen(CaseManager caseManager, MachineGuiSupport state, MachineItemFactory items) {
        this.caseManager = caseManager;
        this.state = state;
        this.items = items;
    }

    // --- list of recipes ---

    // --- picking a case (/pncases workbench) ---

    void openPicker(Player player, int requestedPage) {
        List<CaseDefinition> cases = caseManager.getCases();
        int pages = RewardScreenLayout.pageCount(cases.size());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        if (MachineGuiSupport.refreshIfOpen(caseManager, player, GuiSession.Screen.MACHINE_WORKBENCH_CASES, "",
                page, -1, inventory -> fillPicker(inventory, cases, page, pages))) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(GuiSession.machineWorkbenchCases(page), 54,
                ColorUtil.colorize("&0Крафт ключей: выберите кейс"));
        fillPicker(inventory, cases, page, pages);
        caseManager.getPlugin().openGui(player, inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.22f, 1.15f);
    }

    void handlePickerClick(GuiSession holder, Player player, int slot) {
        int page = holder.page();
        if (slot == MachineCasesScreen.SLOT_CLOSE) {
            player.closeInventory();
        } else if (slot == MachineCasesScreen.SLOT_PREVIOUS) {
            openPicker(player, page - 1);
        } else if (slot == MachineCasesScreen.SLOT_NEXT) {
            openPicker(player, page + 1);
        } else {
            int position = RewardScreenLayout.CONTENT_SLOTS.indexOf(slot);
            List<CaseDefinition> cases = caseManager.getCases();
            int index = page * RewardScreenLayout.CONTENT_SLOTS.size() + position;
            if (position >= 0 && index < cases.size()) openList(player, cases.get(index).name(), true);
        }
    }

    private void fillPicker(Inventory inventory, List<CaseDefinition> cases, int page, int pages) {
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        int start = page * RewardScreenLayout.CONTENT_SLOTS.size();
        for (int position = 0; position < RewardScreenLayout.CONTENT_SLOTS.size(); position++) {
            int index = start + position;
            if (index >= cases.size()) break;
            CaseDefinition definition = cases.get(index);
            int count = caseManager.workbenchRecipes(definition.name()).size();
            ItemStack source = definition.openButton();
            ItemStack icon = source == null || source.getType().isAir() ? new ItemStack(Material.CHEST) : source;
            inventory.setItem(RewardScreenLayout.CONTENT_SLOTS.get(position), items.builtInButton(icon,
                    items.name(definition.displayName()), items.hintLore(
                            List.of("Рецепты ключа этого кейса для крафта в верстаке"),
                            List.of("Рецептов", String.valueOf(count)),
                            Hint.left("рецепты"))));
        }
        if (page > 0) {
            inventory.setItem(MachineCasesScreen.SLOT_PREVIOUS,
                    items.builtInButton(MachineIcon.REWARDS_PREVIOUS, "&#65D1FCПредыдущая", List.of()));
        }
        if (page + 1 < pages) {
            inventory.setItem(MachineCasesScreen.SLOT_NEXT,
                    items.builtInButton(MachineIcon.REWARDS_NEXT, "&#65D1FCСледующая", List.of()));
        }
        inventory.setItem(MachineCasesScreen.SLOT_CLOSE, items.closeButton(null));
        if (cases.isEmpty()) {
            inventory.setItem(22, items.builtInButton(GuiDesign.INFO, items.name("Кейсов пока нет"), items.hintLore(
                    List.of("Сначала создайте кейс командой /pncases createcase"), List.of())));
        }
    }

    // --- list of recipes ---

    void openList(Player player, String caseName) {
        openList(player, caseName, false);
    }

    void openList(Player player, String caseName, boolean fromPicker) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) return;
        if (MachineGuiSupport.refreshIfOpen(
                caseManager, player, GuiSession.Screen.MACHINE_WORKBENCH, definition.name(),
                inventory -> fillList(inventory, definition))) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(
                GuiSession.machineWorkbenchList(caseName, fromPicker), 54,
                ColorUtil.colorize("&0Рецепты ключа | " + definition.displayName()));
        fillList(inventory, definition);
        caseManager.getPlugin().openGui(player, inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    void handleListClick(GuiSession holder, Player player, InventoryClickEvent event, CaseDefinition definition,
                         int slot, MachineExtrasScreen extrasScreen) {
        String name = definition.name();
        boolean fromPicker = holder.selection() == 1;
        if (slot == SLOT_BACK) {
            if (fromPicker) openPicker(player, 0);
            else extrasScreen.open(player, name);
            return;
        }
        List<WorkbenchRecipe> recipes = caseManager.workbenchRecipes(name);
        if (slot == SLOT_WORKBENCH_ADD) {
            if (recipes.size() >= WorkbenchRecipe.MAX_RECIPES) return;
            List<WorkbenchRecipe> next = new ArrayList<>(recipes);
            next.add(WorkbenchRecipe.empty());
            if (saveAll(player, name, next)) openRecipe(player, name, next.size() - 1, fromPicker);
            return;
        }
        int index = LIST_SLOTS.indexOf(slot);
        if (index < 0 || index >= recipes.size()) return;
        if (event.isRightClick()) {
            List<WorkbenchRecipe> next = new ArrayList<>(recipes);
            next.set(index, next.get(index).withEnabled(!next.get(index).enabled()));
            saveAll(player, name, next);
            openList(player, name, fromPicker);
        } else {
            openRecipe(player, name, index, fromPicker);
        }
    }

    private void fillList(Inventory inventory, CaseDefinition definition) {
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        List<WorkbenchRecipe> recipes = caseManager.workbenchRecipes(definition.name());
        for (int i = 0; i < recipes.size() && i < LIST_SLOTS.size(); i++) {
            WorkbenchRecipe recipe = recipes.get(i);
            inventory.setItem(LIST_SLOTS.get(i), items.selectable("machine.workbench.entry",
                    Material.TRIPWIRE_HOOK, recipe.usable(), items.name("Рецепт " + (i + 1)), items.hintLore(
                            List.of("Один из способов создать ключ этого кейса"),
                            List.of("Состояние", recipe.enabled() ? "&aвключён" : "&cвыключен",
                                    "Предметов", String.valueOf(recipe.slots().size()),
                                    "Ключей за крафт", String.valueOf(recipe.resultAmount())),
                            Hint.left("настроить"), Hint.right(recipe.enabled() ? "выключить" : "включить")),
                    definition));
        }
        if (recipes.size() < WorkbenchRecipe.MAX_RECIPES) {
            inventory.setItem(SLOT_WORKBENCH_ADD, items.configuredButton("machine.workbench.add",
                    Material.LIME_DYE, items.name("Добавить рецепт"), items.hintLore(
                            List.of("Ещё один способ создать ключ этого кейса"),
                            List.of("Рецептов", recipes.size() + " из " + WorkbenchRecipe.MAX_RECIPES),
                            Hint.left("добавить")), definition));
        }
        inventory.setItem(SLOT_BACK, items.backButton(definition));
    }

    // --- one recipe ---

    void openRecipe(Player player, String caseName, int index, boolean fromPicker) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) return;
        if (index < 0 || index >= caseManager.workbenchRecipes(caseName).size()) {
            openList(player, caseName, fromPicker);
            return;
        }
        if (MachineGuiSupport.refreshIfOpen(
                caseManager, player, GuiSession.Screen.MACHINE_WORKBENCH_RECIPE, definition.name(), -1, index,
                inventory -> fillRecipe(inventory, definition, index))) {
            return;
        }
        GuiSession session = GuiSession.machineWorkbenchRecipe(caseName, index);
        session.page(fromPicker ? 1 : 0);
        Inventory inventory = Bukkit.createInventory(session, 54,
                ColorUtil.colorize("&0Рецепт " + (index + 1) + " | " + definition.displayName()));
        fillRecipe(inventory, definition, index);
        caseManager.getPlugin().openGui(player, inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    void handleRecipeClick(GuiSession holder, Player player, InventoryClickEvent event, CaseDefinition definition,
                           int slot) {
        String name = definition.name();
        int index = holder.selection();
        boolean fromPicker = holder.page() == 1;
        List<WorkbenchRecipe> recipes = new ArrayList<>(caseManager.workbenchRecipes(name));
        if (index < 0 || index >= recipes.size() || slot == SLOT_BACK) {
            openList(player, name, fromPicker);
            return;
        }
        WorkbenchRecipe recipe = recipes.get(index);
        int gridSlot = gridIndex(slot);
        if (gridSlot >= 0) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                recipes.set(index, recipe.withSlot(gridSlot, ItemTags.ingredient(cursor)));
            } else if (event.isRightClick()) {
                recipes.set(index, recipe.withSlot(gridSlot, null));
            } else {
                return;
            }
        } else if (slot == SLOT_WORKBENCH_TOGGLE) {
            recipes.set(index, recipe.withEnabled(!recipe.enabled()));
        } else if (slot == SLOT_WORKBENCH_AMOUNT) {
            int step = event.isShiftClick() ? 8 : 1;
            recipes.set(index, recipe.withResultAmount(recipe.resultAmount() + (event.isRightClick() ? -step : step)));
        } else if (slot == SLOT_WORKBENCH_CLEAR) {
            recipes.set(index, new WorkbenchRecipe(recipe.enabled(), recipe.resultAmount(), Map.of()));
        } else if (slot == SLOT_WORKBENCH_DELETE) {
            recipes.remove(index);
            if (saveAll(player, name, recipes)) openList(player, name, fromPicker);
            return;
        } else {
            return;
        }
        if (saveAll(player, name, recipes)) openRecipe(player, name, index, fromPicker);
    }

    private boolean saveAll(Player player, String caseName, List<WorkbenchRecipe> recipes) {
        return state.update(player, caseName,
                root -> WorkbenchRecipe.writeAll(section(root, "workbench"), recipes));
    }

    private void fillRecipe(Inventory inventory, CaseDefinition definition, int index) {
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        List<WorkbenchRecipe> recipes = caseManager.workbenchRecipes(definition.name());
        WorkbenchRecipe recipe = index < recipes.size() ? recipes.get(index) : WorkbenchRecipe.empty();
        for (int i = 0; i < GRID.length; i++) {
            inventory.setItem(GRID[i], ingredientItem(recipe.slots().get(i)));
        }
        inventory.setItem(SLOT_WORKBENCH_TOGGLE, items.selectable("machine.workbench.toggle",
                recipe.enabled() ? Material.LIME_DYE : Material.GRAY_DYE, recipe.enabled(),
                items.name("Рецепт включён"), items.hintLore(
                        List.of("Игрок видит этот рецепт в /pncases workbench"),
                        List.of("Состояние", recipe.enabled() ? "&aвключён" : "&cвыключен",
                                "Предметов в рецепте", String.valueOf(recipe.slots().size())),
                        Hint.left(recipe.enabled() ? "выключить" : "включить")), definition));
        inventory.setItem(SLOT_WORKBENCH_AMOUNT, items.configuredButton("machine.workbench.amount",
                Material.TRIPWIRE_HOOK, items.name("Ключей за крафт"), items.hintLore(
                        List.of("Сколько ключей игрок получает за один крафт"),
                        List.of("Ключей", String.valueOf(recipe.resultAmount())),
                        Hint.left("+1"), Hint.right("-1"), Hint.shiftLeft("+8"), Hint.shiftRight("-8")), definition));
        inventory.setItem(SLOT_WORKBENCH_CLEAR, items.configuredButton("machine.workbench.clear",
                Material.BARRIER, items.name("Очистить рецепт"), items.hintLore(
                        List.of("Убирает все предметы из рецепта"), List.of(), Hint.left("очистить")), definition));
        inventory.setItem(SLOT_WORKBENCH_DELETE, items.configuredButton("machine.workbench.delete",
                Material.TNT, items.name("Удалить рецепт"), items.hintLore(
                        List.of("Рецепт пропадёт совсем"), List.of(), Hint.left("удалить")), definition));
        inventory.setItem(SLOT_BACK, items.backButton(definition));
    }

    private ItemStack ingredientItem(WorkbenchRecipe.Ingredient ingredient) {
        if (ingredient == null) {
            return items.pane(Material.GRAY_STAINED_GLASS_PANE, "&#A6B0BAПустой слот", List.of(
                    "", " &#A6B0BAВозьмите предмет-образец в руку (на курсор)", " &#A6B0BAи нажмите на этот слот", ""));
        }
        Material material = Material.matchMaterial(ingredient.material());
        ItemStack stack = new ItemStack(material == null || material.isAir() ? Material.BARRIER : material);
        stack.setAmount(Math.min(ingredient.amount(), stack.getMaxStackSize()));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ColorUtil.colorize(" &#C2CBD3Нужно: &#A7F3D0" + ingredient.amount() + " шт."));
            if (ingredient.tags().isEmpty()) {
                lore.add(ColorUtil.colorize(" &#69747E↳ &#A6B0BAОбычный предмет"));
            } else {
                lore.add(ColorUtil.colorize(" &#C2CBD3Кастомный предмет, теги:"));
                for (String tag : ingredient.tags().stream().sorted().toList()) {
                    lore.add(ColorUtil.colorize(" &#69747E↳ &#A7F3D0" + tag));
                }
            }
            lore.add("");
            lore.add(ColorUtil.colorize(" &#FFB347[&#FFA43C Предмет на курсоре &#FF772A] &fзаменить"));
            lore.add(ColorUtil.colorize(" &#FF8A8A[&#FF7979 П&#F96868К&#F25757М &#EB4646] &fочистить слот"));
            lore.add("");
            meta.setDisplayName(ColorUtil.colorize("&#5FD7FF" + ingredient.material()));
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static int gridIndex(int slot) {
        for (int i = 0; i < GRID.length; i++) if (GRID[i] == slot) return i;
        return -1;
    }
}
