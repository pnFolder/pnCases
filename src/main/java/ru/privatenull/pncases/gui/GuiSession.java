package ru.privatenull.pncases.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.util.List;
import ru.privatenull.pncases.cases.model.Reward;

public final class GuiSession implements InventoryHolder {

    public enum Screen {
        CASE,
        REWARD_PREVIEW,
        PLAYER_ANIMATIONS,
        BULK_OPEN,
        BATCH_RESULTS,
        MACHINE_MAIN,
        MACHINE_FEATURES,
        MACHINE_REWARDS,
        MACHINE_REWARD,
        MACHINE_REWARD_ICON,
        MACHINE_REWARD_MOVE,
        MACHINE_REWARD_DELETE,
        MACHINE_REWARD_CHANCE,
        MACHINE_CASES,
        MACHINE_REWARD_SIMULATION,
        MACHINE_ANIMATION,
        MACHINE_LAYOUT,
        MACHINE_LAYOUT_ROLE,
        MACHINE_HOLOGRAM,
        MACHINE_MENU,
        MACHINE_PURCHASE,
        MACHINE_WORKBENCH,
        MACHINE_WORKBENCH_RECIPE,
        MACHINE_WORKBENCH_CASES,
        MACHINE_BUNDLE,
        MACHINE_PROXIMITY,
        MACHINE_GIFTS,
        MACHINE_GIFT_DEFAULT,
        MACHINE_GIFT_RULE
    }

    private final Screen screen;
    private final String caseName;
    private int page;
    private int selection = -1;
    private List<Reward> rewards = List.of();

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

    public static GuiSession bulkOpen(String caseName) {
        return new GuiSession(Screen.BULK_OPEN, caseName, 0);
    }

    public static GuiSession batchResults(String caseName, List<Reward> rewards, int page) {
        GuiSession session = new GuiSession(Screen.BATCH_RESULTS, caseName, page);
        session.rewards = List.copyOf(rewards);
        return session;
    }

    public List<Reward> rewards() {
        return rewards;
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

    public static GuiSession machineProximity(String caseName, int section) {
        GuiSession session = new GuiSession(Screen.MACHINE_PROXIMITY, caseName, 0);
        session.selection = section;
        return session;
    }

    /** The recipes of a case; {@code fromPicker} makes "back" return to the list of cases. */
    public static GuiSession machineWorkbenchList(String caseName, boolean fromPicker) {
        GuiSession session = new GuiSession(Screen.MACHINE_WORKBENCH, caseName, 0);
        session.selection = fromPicker ? 1 : 0;
        return session;
    }

    /** The list of cases for /pncases workbench. Belongs to no single case. */
    public static GuiSession machineWorkbenchCases(int page) {
        return new GuiSession(Screen.MACHINE_WORKBENCH_CASES, "", page);
    }

    /** One key recipe of a case; {@code selection} is its position in the case file. */
    public static GuiSession machineWorkbenchRecipe(String caseName, int selection) {
        GuiSession session = new GuiSession(Screen.MACHINE_WORKBENCH_RECIPE, caseName, 0);
        session.selection = selection;
        return session;
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

    public static GuiSession machineRewardChance(String caseName, int selection) {
        GuiSession session = new GuiSession(Screen.MACHINE_REWARD_CHANCE, caseName, 0);
        session.selection = selection;
        return session;
    }

    /** The list of all cases; it belongs to no single case, so its case name is empty. */
    public static GuiSession machineCases(int page) {
        return new GuiSession(Screen.MACHINE_CASES, "", page);
    }

    public static GuiSession machineRewardSimulation(String caseName, int page) {
        return new GuiSession(Screen.MACHINE_REWARD_SIMULATION, caseName, page);
    }

    /** The plugin-wide gift limits: the list of rules, page by page. Belongs to no case. */
    public static GuiSession machineGifts(int page) {
        return new GuiSession(Screen.MACHINE_GIFTS, "", page);
    }

    public static GuiSession machineGiftDefault() {
        return new GuiSession(Screen.MACHINE_GIFT_DEFAULT, "", 0);
    }

    public static GuiSession machineGiftRule(int selection) {
        GuiSession session = new GuiSession(Screen.MACHINE_GIFT_RULE, "", 0);
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
