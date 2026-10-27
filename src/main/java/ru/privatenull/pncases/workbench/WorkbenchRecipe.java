package ru.privatenull.pncases.workbench;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The key recipe of one case: a 3x3 grid, slots 0..8 left to right and top to bottom. An ingredient is a
 * material plus an optional set of PersistentDataContainer tags ({@code namespace:key}) of a custom item.
 */
public record WorkbenchRecipe(boolean enabled, int resultAmount, Map<Integer, Ingredient> slots) {

    public static final int GRID = 9;
    public static final int MAX_RESULT = 64;

    public record Ingredient(String material, int amount, Set<String> tags) {
        public Ingredient {
            material = material.toUpperCase(Locale.ROOT);
            amount = Math.max(1, Math.min(64, amount));
            tags = Set.copyOf(tags);
        }
    }

    public WorkbenchRecipe {
        resultAmount = Math.max(1, Math.min(MAX_RESULT, resultAmount));
        slots = Map.copyOf(slots);
    }

    public static WorkbenchRecipe empty() {
        return new WorkbenchRecipe(false, 1, Map.of());
    }

    public boolean usable() {
        return enabled && !slots.isEmpty();
    }

    public WorkbenchRecipe withEnabled(boolean value) {
        return new WorkbenchRecipe(value, resultAmount, slots);
    }

    public WorkbenchRecipe withResultAmount(int value) {
        return new WorkbenchRecipe(enabled, value, slots);
    }

    public WorkbenchRecipe withSlot(int slot, Ingredient ingredient) {
        Map<Integer, Ingredient> copy = new LinkedHashMap<>(slots);
        if (ingredient == null) copy.remove(slot);
        else copy.put(slot, ingredient);
        return new WorkbenchRecipe(enabled, resultAmount, copy);
    }

    /** Reads the {@code workbench} section of a case file. A missing or broken section gives an empty recipe. */
    public static WorkbenchRecipe read(ConfigurationSection section) {
        if (section == null) return empty();
        Map<Integer, Ingredient> slots = new LinkedHashMap<>();
        ConfigurationSection raw = section.getConfigurationSection("slots");
        if (raw != null) {
            for (String key : raw.getKeys(false)) {
                int slot;
                try {
                    slot = Integer.parseInt(key);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                ConfigurationSection entry = raw.getConfigurationSection(key);
                if (slot < 0 || slot >= GRID || entry == null) continue;
                String material = entry.getString("material");
                if (material == null || material.isBlank()) continue;
                slots.put(slot, new Ingredient(material, entry.getInt("amount", 1),
                        new LinkedHashSet<>(entry.getStringList("tags"))));
            }
        }
        return new WorkbenchRecipe(section.getBoolean("enabled", false), section.getInt("result-amount", 1), slots);
    }

    public static final int MAX_RECIPES = 14;

    /**
     * Reads all recipes of a case from its {@code workbench} section: {@code recipes.1, recipes.2, ...}. A
     * section written by the first version (one recipe right in {@code workbench}) is read as the only recipe.
     */
    public static List<WorkbenchRecipe> readAll(ConfigurationSection workbench) {
        if (workbench == null) return List.of();
        ConfigurationSection many = workbench.getConfigurationSection("recipes");
        List<WorkbenchRecipe> recipes = new ArrayList<>();
        if (many != null) {
            List<String> keys = new ArrayList<>(many.getKeys(false));
            keys.sort(Comparator.comparingInt(WorkbenchRecipe::numberOf));
            for (String key : keys) {
                if (recipes.size() >= MAX_RECIPES) break;
                recipes.add(read(many.getConfigurationSection(key)));
            }
        } else if (workbench.contains("slots")) {
            recipes.add(read(workbench));
        }
        return List.copyOf(recipes);
    }

    /** Replaces all recipes of the {@code workbench} section. */
    public static void writeAll(ConfigurationSection workbench, List<WorkbenchRecipe> recipes) {
        workbench.set("enabled", null);
        workbench.set("result-amount", null);
        workbench.set("slots", null);
        workbench.set("recipes", null);
        if (recipes.isEmpty()) return;
        ConfigurationSection target = workbench.createSection("recipes");
        for (int i = 0; i < recipes.size(); i++) {
            recipes.get(i).write(target.createSection(String.valueOf(i + 1)));
        }
    }

    private static int numberOf(String key) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    /** Writes the recipe over the {@code workbench} section; empty slots are not written. */
    public void write(ConfigurationSection section) {
        section.set("enabled", enabled);
        section.set("result-amount", resultAmount);
        section.set("slots", null);
        if (slots.isEmpty()) return;
        ConfigurationSection target = section.createSection("slots");
        for (Map.Entry<Integer, Ingredient> entry : new TreeMap<>(slots).entrySet()) {
            ConfigurationSection slot = target.createSection(String.valueOf(entry.getKey()));
            slot.set("material", entry.getValue().material());
            slot.set("amount", entry.getValue().amount());
            if (!entry.getValue().tags().isEmpty()) {
                slot.set("tags", entry.getValue().tags().stream().sorted().toList());
            }
        }
    }
}
