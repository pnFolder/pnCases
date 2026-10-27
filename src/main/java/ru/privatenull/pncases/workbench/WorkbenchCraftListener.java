package ru.privatenull.pncases.workbench;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.support.text.ColorUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Crafting of case keys in the ordinary crafting table. When the 3x3 grid matches a recipe set in the Machine,
 * the result slot shows a key; taking it adds a virtual key to the player's balance instead of giving an item.
 */
public final class WorkbenchCraftListener implements Listener {

    private static final int GRID = 9;

    private final CaseManager caseManager;
    private final Set<UUID> crafting = new HashSet<>();

    private final NamespacedKey marker;

    public WorkbenchCraftListener(CaseManager caseManager) {
        this.caseManager = caseManager;
        this.marker = new NamespacedKey(caseManager.getPlugin(), "workbench_key_icon");
    }

    private record Match(CaseDefinition definition, WorkbenchRecipe recipe, int times) {
    }

    /** The first case recipe that the grid matches, or null. */
    private Match find(ItemStack[] matrix) {
        if (matrix == null || matrix.length != GRID) return null;
        List<RecipeMatcher.Stack> grid = new ArrayList<>(GRID);
        for (ItemStack stack : matrix) grid.add(ItemTags.read(stack));
        for (CaseDefinition definition : caseManager.getCases()) {
            for (WorkbenchRecipe recipe : caseManager.workbenchRecipes(definition.name())) {
                int times = RecipeMatcher.craftable(recipe, grid);
                if (times > 0) return new Match(definition, recipe, times);
            }
        }
        return null;
    }

    private ItemStack keyIcon(Match match) {
        ItemStack item = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize(caseManager.getPlugin().getUi().text("workbench.result",
                    "&#A7F3D0Ключ: {key}", "key", caseManager.getKeyDisplayName(match.definition().name()))));
            meta.setLore(List.of(
                    "",
                    ColorUtil.colorize(" &#C2CBD3Ключ зачислится на ваш счёт"),
                    ColorUtil.colorize(" &#69747E↳ &#A7F3D0" + match.recipe().resultAmount() + " шт."),
                    ""));
            meta.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        item.setAmount(Math.min(64, match.recipe().resultAmount()));
        return item;
    }

    @EventHandler
    public void onPrepare(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        if (inventory.getMatrix().length != GRID) return;
        Match match = find(inventory.getMatrix());
        if (match != null) inventory.setResult(keyIcon(match));
    }

    private boolean isKeyIcon(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(marker, PersistentDataType.BYTE);
    }

    /**
     * The result slot of a crafting table holds only a picture of the key. Every click on it is taken over
     * here, so the picture never reaches the cursor or the inventory: the key goes to the virtual balance.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onResultClick(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.WORKBENCH) return;
        if (event.getSlotType() != InventoryType.SlotType.RESULT || event.getRawSlot() != 0) return;
        if (!(event.getInventory() instanceof CraftingInventory inventory)) return;
        if (!isKeyIcon(inventory.getResult()) && !isKeyIcon(event.getCurrentItem())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.SHIFT_LEFT) return;
        craft(player, inventory, click == ClickType.SHIFT_LEFT);
    }

    /** A safety net: if the server still starts a craft with the key picture, nothing is taken. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(CraftItemEvent event) {
        if (isKeyIcon(event.getInventory().getResult())) event.setCancelled(true);
    }

    private void craft(Player player, CraftingInventory inventory, boolean maximum) {
        if (inventory.getMatrix().length != GRID) return;
        Match match = find(inventory.getMatrix());
        if (match == null) return;
        UUID id = player.getUniqueId();
        if (!crafting.add(id)) return;

        int times = maximum
                ? Math.min(match.times(), Math.max(1, 640 / match.recipe().resultAmount())) : 1;
        int total = match.recipe().resultAmount() * times;
        String caseName = match.definition().name();

        // Take the ingredients first and remember them, so a failed key issue can return them.
        ItemStack[] matrix = inventory.getMatrix();
        List<ItemStack> taken = new ArrayList<>();
        for (Map.Entry<Integer, WorkbenchRecipe.Ingredient> entry : match.recipe().slots().entrySet()) {
            ItemStack stack = matrix[entry.getKey()];
            if (stack == null) continue;
            int used = entry.getValue().amount() * times;
            ItemStack copy = stack.clone();
            copy.setAmount(used);
            taken.add(copy);
            if (stack.getAmount() <= used) matrix[entry.getKey()] = null;
            else stack.setAmount(stack.getAmount() - used);
        }
        inventory.setMatrix(matrix);
        updateResult(inventory, player);

        caseManager.giveKey(player, caseName, total,
                () -> {
                    crafting.remove(id);
                    player.sendMessage(ColorUtil.colorize(caseManager.getPlugin().getUi().text("workbench.crafted",
                            "&#EFF7B9pnCases &8| &fВы создали ключ &#A7F3D0{key}&f (&#A7F3D0{amount} шт.&f). "
                                    + "Он превращён в виртуальный и добавлен на ваш счёт.",
                            "key", caseManager.getKeyDisplayName(caseName), "amount", String.valueOf(total))));
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.28f, 1.35f);
                },
                failure -> {
                    crafting.remove(id);
                    for (ItemStack stack : taken) {
                        player.getInventory().addItem(stack).values()
                                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                    }
                    player.sendMessage(ColorUtil.colorize(caseManager.getPlugin().getUi().text("workbench.failed",
                            "&#FC7165Не удалось создать ключ, предметы возвращены.")));
                });
    }

    /** After the ingredients are taken the result is recalculated by hand: the server does not do it. */
    private void updateResult(CraftingInventory inventory, Player player) {
        caseManager.getPlugin().tasks().run(() -> {
            Match next = find(inventory.getMatrix());
            inventory.setResult(next == null ? null : keyIcon(next));
            player.updateInventory();
        });
    }
}
