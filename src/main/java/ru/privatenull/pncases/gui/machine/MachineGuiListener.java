package ru.privatenull.pncases.gui.machine;

import ru.privatenull.pncases.gui.GuiSession;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.model.CaseDefinition;

public final class MachineGuiListener implements Listener {

    private final CaseManager caseManager;
    private final MachineMainScreen mainScreen;
    private final MachineActionController actions;
    private final MachineTextEditor textEditor;
    private final MachineRewardScreen rewardScreen;
    private final MachineCasesScreen casesScreen;
    private final MachineWorkbenchScreen workbenchScreen;

    public MachineGuiListener(CaseManager caseManager) {
        this.caseManager = caseManager;
        MachineAnvilInput.selfTest(caseManager.getPlugin());

        MachineGuiSupport support = new MachineGuiSupport(caseManager);
        MachineItemFactory itemFactory = new MachineItemFactory(caseManager, support);

        this.mainScreen = new MachineMainScreen(caseManager, support, itemFactory);
        MachineExtrasScreen extrasScreen = new MachineExtrasScreen(caseManager, support, itemFactory);
        this.rewardScreen = new MachineRewardScreen(caseManager, support, mainScreen, itemFactory);
        MachineHologramScreen hologramScreen = new MachineHologramScreen(caseManager, support, itemFactory);
        MachineProximityScreen proximityScreen = new MachineProximityScreen(caseManager, itemFactory);
        MachineBundleScreen bundleScreen = new MachineBundleScreen(caseManager, support, itemFactory);
        MachinePurchaseScreen purchaseScreen = new MachinePurchaseScreen(caseManager, support, itemFactory);
        MachineMenuScreen menuScreen = new MachineMenuScreen(caseManager, itemFactory);
        MachineLayoutEditor layoutEditor = new MachineLayoutEditor(caseManager, support, itemFactory, menuScreen);
        MachineAnimationScreen animationScreen = new MachineAnimationScreen(caseManager, itemFactory);
        this.textEditor = new MachineTextEditor(caseManager, support, mainScreen, menuScreen);
        this.casesScreen = new MachineCasesScreen(caseManager, itemFactory, mainScreen);
        this.workbenchScreen = new MachineWorkbenchScreen(caseManager, support, itemFactory);
        this.actions = new MachineActionController(
                caseManager,
                support,
                layoutEditor,
                mainScreen,
                hologramScreen,
                proximityScreen,
                bundleScreen,
                purchaseScreen,
                menuScreen,
                animationScreen,
                textEditor,
                extrasScreen,
                rewardScreen,
                casesScreen,
                workbenchScreen
        );
    }

    /** The list of cases for /pncases workbench, where a case's key recipes are edited. */
    public void openWorkbench(Player player, String caseName) {
        if (caseName == null) workbenchScreen.openPicker(player, 0);
        else workbenchScreen.openList(player, caseName, false);
    }

    public void openMain(Player player, String caseName) {
        mainScreen.open(player, caseName);
    }

    /** The gift limits menu: plugin-wide settings that belong to no case, opened by /pncases giftlimit. */
    public void openGifts(Player player) {
        casesScreen.openGifts(player);
    }

    /** The list of all cases, where a case is opened or created with a click. */
    public void openCases(Player player) {
        casesScreen.open(player, 0);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof GuiSession holder) || !holder.machine()) return;

        int slot = event.getRawSlot();
        if (slot >= event.getInventory().getSize()) {
            handleOwnInventoryClick(event, player, holder);
            return;
        }

        event.setCancelled(true);
        if (slot < 0) return;

        // The list of cases and the gift limits belong to no single case.
        if (holder.screen() == GuiSession.Screen.MACHINE_WORKBENCH_CASES) {
            caseManager.getPlugin().withGuiTransition(player, slot,
                    () -> workbenchScreen.handlePickerClick(holder, player, slot));
            return;
        }
        if (holder.caseName().isEmpty()) {
            caseManager.getPlugin().withGuiTransition(player, slot,
                    () -> casesScreen.handleClick(holder, player, event, slot));
            return;
        }

        CaseDefinition definition = caseManager.getCaseByName(holder.caseName());
        if (definition == null) {
            player.closeInventory();
            player.sendMessage(caseManager.getPlugin().getUi().get(
                    "machine-case-unloaded", "case", holder.caseName()));
            return;
        }
        caseManager.getPlugin().withGuiTransition(player, slot,
                () -> actions.handle(holder, player, event, definition, slot));
    }

    /**
     * A click in the player's own inventory. On the reward list it adds the clicked item to the case, so
     * rewards are added by clicking items instead of moving them around; nothing is taken from the player.
     * Everywhere else only moves into the Machine window are blocked.
     */
    private void handleOwnInventoryClick(InventoryClickEvent event, Player player, GuiSession holder) {
        if (holder.screen() == GuiSession.Screen.MACHINE_REWARDS
                && event.getClickedInventory() != null
                && event.getClickedInventory() == event.getView().getBottomInventory()) {
            event.setCancelled(true);
            CaseDefinition definition = caseManager.getCaseByName(holder.caseName());
            if (definition == null || event.getCurrentItem() == null) return;
            int slot = event.getRawSlot();
            org.bukkit.inventory.ItemStack clicked = event.getCurrentItem().clone();
            caseManager.getPlugin().withGuiTransition(player, slot,
                    () -> rewardScreen.addFromInventory(player, definition, clicked));
            return;
        }
        event.setCancelled(transfersToTop(event));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiSession holder) || !holder.machine()) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot >= 0 && slot < topSize)) {
            event.setCancelled(true);
        }
    }

    private static boolean transfersToTop(InventoryClickEvent event) {
        if (event.isShiftClick()
                || event.getClick() == ClickType.NUMBER_KEY
                || event.getClick() == ClickType.DOUBLE_CLICK) {
            return true;
        }
        InventoryAction action = event.getAction();
        return action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || action == InventoryAction.HOTBAR_SWAP
                || action == InventoryAction.COLLECT_TO_CURSOR;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (rewardScreen.handleChat(event)) return;
        textEditor.handleChat(event);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        textEditor.cancel(event.getPlayer());
        rewardScreen.cancel(event.getPlayer());
    }
}
