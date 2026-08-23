package ru.privatenull.pncases.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class GuiSession implements InventoryHolder {

    public enum Screen {
        CASE,
        REWARD_PREVIEW,
        PLAYER_ANIMATIONS,
        MACHINE_MAIN,
        MACHINE_FEATURES,
        MACHINE_REWARDS,
        MACHINE_REWARD,
        MACHINE_REWARD_ICON,
        MACHINE_REWARD_MOVE,
        MACHINE_REWARD_DELETE,
        MACHINE_ANIMATION,
        MACHINE_LAYOUT,
        MACHINE_LAYOUT_ROLE,
        MACHINE_HOLOGRAM,
        MACHINE_SHOWCASE,
        MACHINE_MENU,
        MACHINE_PURCHASE
    }

    private final Screen screen;
    private final String caseName;
    private int page;
    private int selection = -1;

    private GuiSession(Screen screen, String caseName, int page) {
        this.screen = screen;
        this.caseName = caseName;
        this.page = page;
    }

    public static GuiSession caseMenu(String caseName) {
        return new GuiSession(Screen.CASE, caseName, 0);
    }

    public static GuiSession rewardPreview(String caseName, int page) {
        return new GuiSession(Screen.REWARD_PREVIEW, caseName, page);
    }

    public static GuiSession playerAnimations(String caseName) {
        return new GuiSession(Screen.PLAYER_ANIMATIONS, caseName, 0);
    }

    public static GuiSession machine(Screen screen, String caseName) {
        if (screen == null || !screen.name().startsWith("MACHINE_")) {
            throw new IllegalArgumentException("Not a machine screen: " + screen);
        }
        return new GuiSession(screen, caseName, 0);
    }

    public static GuiSession machineAnimations(String caseName) {
        return new GuiSession(Screen.MACHINE_ANIMATION, caseName, 0);
    }

    public static GuiSession machineRewards(String caseName, int page) {
        return new GuiSession(Screen.MACHINE_REWARDS, caseName, page);
    }

    public static GuiSession machineLayout(String caseName) {
        return new GuiSession(Screen.MACHINE_LAYOUT, caseName, 0);
    }

    public static GuiSession machineLayoutRole(String caseName, int selection) {
        GuiSession session = new GuiSession(Screen.MACHINE_LAYOUT_ROLE, caseName, 0);
        session.selection = selection;
        return session;
    }

    public static GuiSession machineReward(String caseName, int selection) {
        GuiSession session = new GuiSession(Screen.MACHINE_REWARD, caseName, 0);
        session.selection = selection;
        return session;
    }

    public static GuiSession machineRewardIcon(String caseName, int selection) {
        GuiSession session = new GuiSession(Screen.MACHINE_REWARD_ICON, caseName, 0);
        session.selection = selection;
        return session;
    }

    public static GuiSession machineRewardMove(String caseName, int selection, int page) {
        GuiSession session = new GuiSession(Screen.MACHINE_REWARD_MOVE, caseName, page);
        session.selection = selection;
        return session;
    }

    public static GuiSession machineRewardDelete(String caseName, int selection) {
        GuiSession session = new GuiSession(Screen.MACHINE_REWARD_DELETE, caseName, 0);
        session.selection = selection;
        return session;
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

    public int selection() {
        return selection;
    }

    public boolean machine() {
        return screen.name().startsWith("MACHINE_");
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
