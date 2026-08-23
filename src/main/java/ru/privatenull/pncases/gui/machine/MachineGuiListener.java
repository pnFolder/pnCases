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

    public MachineGuiListener(CaseManager caseManager) {
        this.caseManager = caseManager;

        MachineGuiSupport support = new MachineGuiSupport(caseManager);
        MachineItemFactory itemFactory = new MachineItemFactory(caseManager, support);

        this.mainScreen = new MachineMainScreen(caseManager, support, itemFactory);
        MachineFeaturesScreen featuresScreen = new MachineFeaturesScreen(caseManager, mainScreen, itemFactory);
        this.rewardScreen = new MachineRewardScreen(caseManager, support, mainScreen, itemFactory);
        MachineHologramScreen hologramScreen = new MachineHologramScreen(caseManager, support, itemFactory);
        MachinePurchaseScreen purchaseScreen = new MachinePurchaseScreen(caseManager, support, itemFactory);
        MachineMenuScreen menuScreen = new MachineMenuScreen(caseManager, itemFactory);
        MachineLayoutEditor layoutEditor = new MachineLayoutEditor(caseManager, support, itemFactory, menuScreen);
        MachineAnimationScreen animationScreen = new MachineAnimationScreen(caseManager, itemFactory);
        MachineShowcaseScreen showcaseScreen = new MachineShowcaseScreen(caseManager, itemFactory);
        this.textEditor = new MachineTextEditor(caseManager, support, mainScreen, menuScreen);
        this.actions = new MachineActionController(
                caseManager,
                support,
                layoutEditor,
                mainScreen,
                hologramScreen,
                purchaseScreen,
                menuScreen,
                animationScreen,
                showcaseScreen,
                textEditor,
                featuresScreen,
                rewardScreen
        );
    }

    public void openMain(Player player, String caseName) {
        mainScreen.open(player, caseName);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof GuiSession holder) || !holder.machine()) return;

        int slot = event.getRawSlot();
        if (slot >= event.getInventory().getSize()) {
            event.setCancelled(transfersToTop(event));
            return;
        }

        event.setCancelled(true);
        if (slot < 0) return;

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
