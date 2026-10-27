package ru.privatenull.pncases.workbench;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Compares the grid with a recipe. Works on plain values, so it needs no server. */
public final class RecipeMatcher {

    /** What lies in one grid slot: the material name, the stack size and the item's PDC tags. */
    public record Stack(String material, int amount, Set<String> tags) {
        public Stack {
            material = material.toUpperCase(Locale.ROOT);
            tags = Set.copyOf(tags);
        }
    }

    private RecipeMatcher() {
    }

    /**
     * How many times the recipe can be crafted from this grid: 0 when it does not match. Every slot of the
     * recipe must hold the right item, and every slot outside it must be empty.
     */
    public static int craftable(WorkbenchRecipe recipe, List<Stack> grid) {
        if (recipe == null || !recipe.usable() || grid.size() != WorkbenchRecipe.GRID) return 0;
        int times = Integer.MAX_VALUE;
        for (int slot = 0; slot < WorkbenchRecipe.GRID; slot++) {
            WorkbenchRecipe.Ingredient need = recipe.slots().get(slot);
            Stack have = grid.get(slot);
            if (need == null) {
                if (have != null) return 0;
                continue;
            }
            if (have == null || !have.material().equals(need.material()) || have.amount() < need.amount()) return 0;
            if (need.tags().isEmpty() ? !have.tags().isEmpty() : !have.tags().containsAll(need.tags())) return 0;
            times = Math.min(times, have.amount() / need.amount());
        }
        return times == Integer.MAX_VALUE ? 0 : times;
    }
}
