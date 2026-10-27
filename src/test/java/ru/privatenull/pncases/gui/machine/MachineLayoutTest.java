package ru.privatenull.pncases.gui.machine;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.privatenull.pncases.gui.machine.MachineGuiSupport.*;

class MachineLayoutTest {

    private static void assertDistinctAndInside(List<Integer> slots, int inventorySize) {
        Set<Integer> used = new HashSet<>();
        for (int slot : slots) {
            assertTrue(used.add(slot), "slot " + slot + " is used twice");
            assertTrue(slot >= 0 && slot < inventorySize, "slot " + slot + " is outside the inventory");
        }
    }

    @Test
    void homeScreenHasSectionsInTheFirstRowToolsBelowAndCaseActionsAfterThat() {
        List<Integer> sections = List.of(SLOT_MAIN_REWARDS, SLOT_MAIN_ANIMATION, SLOT_MAIN_MENU, SLOT_MAIN_FEATURES);
        List<Integer> tools = List.of(SLOT_MAIN_CHECK, SLOT_MAIN_PREVIEW, SLOT_MAIN_UNDO);
        List<Integer> actions = List.of(SLOT_MAIN_BIND, SLOT_MAIN_KEY);

        for (int slot : sections) assertEquals(1, slot / 9, "section " + slot + " must be in the first row");
        for (int slot : tools) assertEquals(2, slot / 9, "tool " + slot + " must be in the second row");
        for (int slot : actions) assertEquals(3, slot / 9, "action " + slot + " must be in the third row");
        assertDistinctAndInside(List.of(SLOT_MAIN_REWARDS, SLOT_MAIN_ANIMATION, SLOT_MAIN_MENU, SLOT_MAIN_FEATURES,
                SLOT_MAIN_CHECK, SLOT_MAIN_PREVIEW, SLOT_MAIN_UNDO,
                SLOT_MAIN_BIND, SLOT_MAIN_KEY, SLOT_MAIN_CASES, SLOT_MAIN_CLOSE), 54);
    }

    @Test
    void toolsSitInTheSameColumnsAsTheSectionsAboveThem() {
        assertEquals(SLOT_MAIN_REWARDS % 9, SLOT_MAIN_CHECK % 9);
        assertEquals(SLOT_MAIN_ANIMATION % 9, SLOT_MAIN_PREVIEW % 9);
        assertEquals(SLOT_MAIN_MENU % 9, SLOT_MAIN_UNDO % 9);
    }

    @Test
    void additionalScreenFitsItsFiveRowInventoryAndKeepsRoomForBack() {
        assertDistinctAndInside(List.of(SLOT_EXTRA_HOLOGRAM, SLOT_EXTRA_SHOWCASE, SLOT_EXTRA_PURCHASE, SLOT_EXTRA_GIFT,
                SLOT_EXTRA_RARITY, SLOT_EXTRA_HISTORY, SLOT_EXTRA_PREVIEW, SLOT_EXTRA_WORKBENCH,
                MachineExtrasScreen.backSlot()), 45);
        List<Integer> workbench = new java.util.ArrayList<>();
        for (int slot : MachineWorkbenchScreen.GRID) workbench.add(slot);
        workbench.addAll(List.of(SLOT_WORKBENCH_TOGGLE, SLOT_WORKBENCH_AMOUNT, SLOT_WORKBENCH_CLEAR,
                SLOT_WORKBENCH_DELETE, SLOT_BACK));
        assertDistinctAndInside(workbench, 54);
        List<Integer> workbenchList = new java.util.ArrayList<>(MachineWorkbenchScreen.LIST_SLOTS);
        workbenchList.addAll(List.of(SLOT_WORKBENCH_ADD, SLOT_BACK));
        assertDistinctAndInside(workbenchList, 54);
    }

    @Test
    void detailScreensKeepTheirControlsApartFromBack() {
        assertDistinctAndInside(List.of(SLOT_HOLOGRAM_TOGGLE, SLOT_HOLOGRAM_HEIGHT, SLOT_HOLOGRAM_LINES, SLOT_BACK), 54);
        assertDistinctAndInside(List.of(SLOT_XP_BUY, SLOT_XP_LEVELS, SLOT_BACK), 54);
    }

    @Test
    void menuScreenControlsDoNotOverlap() {
        assertDistinctAndInside(List.of(SLOT_GUI_SIZE, SLOT_GUI_TITLE, SLOT_LAYOUT, SLOT_OPEN_ITEM, SLOT_DECOR_ITEM,
                SLOT_EMPTY_HISTORY_ITEM, SLOT_CONTENT_TOGGLE, SLOT_BACK), 54);
    }

    @Test
    void onlyTheThreeGlobalSwitchesMapToFeatures() {
        assertEquals("rarity", MachineExtrasScreen.featureAt(SLOT_EXTRA_RARITY));
        assertEquals("preview", MachineExtrasScreen.featureAt(SLOT_EXTRA_PREVIEW));
        assertEquals("history", MachineExtrasScreen.featureAt(SLOT_EXTRA_HISTORY));
        assertNull(MachineExtrasScreen.featureAt(SLOT_EXTRA_HOLOGRAM));
        assertNull(MachineExtrasScreen.featureAt(0));
    }

    @Test
    void theCasesListKeepsItsControlsInTheBottomRowAwayFromTheCaseSlots() {
        assertDistinctAndInside(List.of(MachineCasesScreen.SLOT_PREVIOUS, MachineCasesScreen.SLOT_NEW,
                MachineCasesScreen.SLOT_NEXT, MachineCasesScreen.SLOT_CLOSE), 54);
        for (int slot : RewardScreenLayout.CONTENT_SLOTS) {
            assertTrue(slot / 9 < 5, "case slot " + slot + " reaches the control row");
        }
    }

    @Test
    void theEqualizeButtonDoesNotCoverARewardSlot() {
        assertTrue(!RewardScreenLayout.CONTENT_SLOTS.contains(RewardScreenLayout.SLOT_EQUALIZE));
    }
}