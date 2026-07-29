package ru.privatenull.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import ru.privatenull.cases.model.AnimationType.Category;

public final class GuiSession implements InventoryHolder {

    public enum Screen {
        CASE,
        REWARD_PREVIEW,
        PLAYER_ANIMATIONS,
        MACHINE_MAIN,
        MACHINE_ANIMATION,
        MACHINE_LAYOUT,
        MACHINE_HOLOGRAM,
        MACHINE_SHOWCASE,
        MACHINE_MENU,
        MACHINE_PURCHASE
    }

    private final Screen screen;
    private final String caseName;
    private int page;
    private Category animationCategory;

    private GuiSession(Screen screen, String caseName, int page, Category animationCategory) {
        this.screen = screen;
        this.caseName = caseName;
        this.page = page;
        this.animationCategory = animationCategory;
    }

    public static GuiSession caseMenu(String caseName) {
        return new GuiSession(Screen.CASE, caseName, 0, null);
    }

    public static GuiSession rewardPreview(String caseName, int page) {
        return new GuiSession(Screen.REWARD_PREVIEW, caseName, page, null);
    }

    public static GuiSession playerAnimations(String caseName, Category category) {
        return new GuiSession(Screen.PLAYER_ANIMATIONS, caseName, 0, category);
    }

    public static GuiSession machine(Screen screen, String caseName) {
        if (screen == null || !screen.name().startsWith("MACHINE_")) {
            throw new IllegalArgumentException("Not a machine screen: " + screen);
        }
        return new GuiSession(screen, caseName, 0, null);
    }

    public static GuiSession machineAnimations(String caseName, Category category) {
        return new GuiSession(Screen.MACHINE_ANIMATION, caseName, 0, category);
    }

    public Screen screen() {
        return screen;
    }

    public String caseName() {
        return caseName;
    }

    public int page() {
        return page;
    }

    public void page(int page) {
        this.page = Math.max(0, page);
    }

    public Category animationCategory() {
        return animationCategory;
    }

    public void animationCategory(Category category) {
        this.animationCategory = category;
    }

    public boolean machine() {
        return screen.name().startsWith("MACHINE_");
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
