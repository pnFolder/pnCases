package ru.privatenull.pncases.gui.machine;

import java.util.List;

/** Slot numbers shared by the reward screens of the Machine GUI. */
final class RewardScreenLayout {

    static final List<Integer> CONTENT_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    );
    static final int SLOT_BACK = 45;
    static final int SLOT_ADD_ITEM = 46;
    static final int SLOT_ADD_VAULT = 47;
    static final int SLOT_ADD_POINTS = 48;
    static final int SLOT_SIMULATE = 53;
    static final int SLOT_INFO = 53;
    static final int SLOT_ADD_LUCKPERMS = 49;
    static final int SLOT_ADD_PERSONAL = 50;
    static final int SLOT_ADD_PERMISSION = 51;
    static final int SLOT_PREVIOUS = 52;
    static final int SLOT_NEXT = 44;
    static final int SLOT_EQUALIZE = 4;

    private RewardScreenLayout() {
    }

    static int pageCount(int entries) {
        return Math.max(1, (entries + CONTENT_SLOTS.size() - 1) / CONTENT_SLOTS.size());
    }
}
