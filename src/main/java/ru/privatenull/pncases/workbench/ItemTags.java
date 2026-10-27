package ru.privatenull.pncases.workbench;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.pncases.workbench.WorkbenchRecipe.Ingredient;

import java.util.Set;
import java.util.TreeSet;

/** Reads what the recipe cares about from a real item: material, size and PersistentDataContainer tags. */
public final class ItemTags {

    private ItemTags() {
    }

    public static Set<String> tags(ItemStack item) {
        Set<String> tags = new TreeSet<>();
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta == null) return tags;
        for (NamespacedKey key : meta.getPersistentDataContainer().getKeys()) tags.add(key.toString());
        return tags;
    }

    /** The grid view of an item; null for an empty slot. */
    public static RecipeMatcher.Stack read(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) return null;
        return new RecipeMatcher.Stack(item.getType().name(), item.getAmount(), tags(item));
    }

    /** A sample item put by an administrator becomes an ingredient with the same material, size and tags. */
    public static Ingredient ingredient(ItemStack sample) {
        return new Ingredient(sample.getType().name(), sample.getAmount(), tags(sample));
    }
}
