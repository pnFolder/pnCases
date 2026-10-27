package ru.privatenull.pncases.workbench;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import ru.privatenull.pncases.workbench.RecipeMatcher.Stack;
import ru.privatenull.pncases.workbench.WorkbenchRecipe.Ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeMatcherTest {

    private static final WorkbenchRecipe RECIPE = new WorkbenchRecipe(true, 2, Map.of(
            0, new Ingredient("diamond", 2, Set.of()),
            4, new Ingredient("NETHER_STAR", 1, Set.of("myplugin:shard"))));

    private static List<Stack> grid(Stack first, Stack center) {
        List<Stack> grid = new ArrayList<>();
        for (int i = 0; i < 9; i++) grid.add(null);
        grid.set(0, first);
        grid.set(4, center);
        return grid;
    }

    private static Stack diamonds(int amount) {
        return new Stack("DIAMOND", amount, Set.of());
    }

    private static Stack shard() {
        return new Stack("NETHER_STAR", 1, Set.of("myplugin:shard", "other:x"));
    }

    @Test
    void matchesRecipeAndCountsCrafts() {
        assertEquals(1, RecipeMatcher.craftable(RECIPE, grid(diamonds(2), shard())));
        assertEquals(3, RecipeMatcher.craftable(RECIPE, grid(diamonds(7), new Stack("NETHER_STAR", 5,
                Set.of("myplugin:shard")))));
    }

    @Test
    void rejectsNotEnoughItems() {
        assertEquals(0, RecipeMatcher.craftable(RECIPE, grid(diamonds(1), shard())));
    }

    @Test
    void customIngredientNeedsItsTag() {
        assertEquals(0, RecipeMatcher.craftable(RECIPE, grid(diamonds(2), new Stack("NETHER_STAR", 1, Set.of()))));
    }

    @Test
    void customItemDoesNotPassForPlainOne() {
        assertEquals(0, RecipeMatcher.craftable(RECIPE, grid(new Stack("DIAMOND", 2, Set.of("a:b")), shard())));
    }

    @Test
    void extraItemOutsideRecipeIsRejected() {
        List<Stack> grid = grid(diamonds(2), shard());
        grid.set(8, diamonds(1));
        assertEquals(0, RecipeMatcher.craftable(RECIPE, grid));
    }

    @Test
    void emptySlotAndDisabledRecipeAreRejected() {
        assertEquals(0, RecipeMatcher.craftable(RECIPE, grid(diamonds(2), null)));
        assertEquals(0, RecipeMatcher.craftable(RECIPE.withEnabled(false), grid(diamonds(2), shard())));
        assertEquals(0, RecipeMatcher.craftable(WorkbenchRecipe.empty().withEnabled(true), grid(null, null)));
    }

    @Test
    void recipeSurvivesWriteAndRead() {
        YamlConfiguration yaml = new YamlConfiguration();
        RECIPE.write(yaml.createSection("workbench"));
        WorkbenchRecipe read = WorkbenchRecipe.read(yaml.getConfigurationSection("workbench"));
        assertEquals(RECIPE, read);
        assertTrue(read.usable());
    }

    @Test
    void severalRecipesSurviveWriteAndRead() {
        YamlConfiguration yaml = new YamlConfiguration();
        WorkbenchRecipe second = new WorkbenchRecipe(false, 3, Map.of(8, new Ingredient("GOLD_INGOT", 4, Set.of())));
        WorkbenchRecipe.writeAll(yaml.createSection("workbench"), List.of(RECIPE, second));
        assertEquals(List.of(RECIPE, second), WorkbenchRecipe.readAll(yaml.getConfigurationSection("workbench")));
        WorkbenchRecipe.writeAll(yaml.getConfigurationSection("workbench"), List.of());
        assertEquals(List.of(), WorkbenchRecipe.readAll(yaml.getConfigurationSection("workbench")));
    }

    @Test
    void firstVersionSingleRecipeIsStillRead() {
        YamlConfiguration yaml = new YamlConfiguration();
        RECIPE.write(yaml.createSection("workbench"));
        assertEquals(List.of(RECIPE), WorkbenchRecipe.readAll(yaml.getConfigurationSection("workbench")));
    }

    @Test
    void brokenSectionGivesEmptyRecipe() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("workbench.slots.99.material", "STONE");
        yaml.set("workbench.slots.x.material", "STONE");
        yaml.set("workbench.slots.1.amount", 3);
        assertEquals(0, WorkbenchRecipe.read(yaml.getConfigurationSection("workbench")).slots().size());
        assertEquals(WorkbenchRecipe.empty(), WorkbenchRecipe.read(null));
    }
}
