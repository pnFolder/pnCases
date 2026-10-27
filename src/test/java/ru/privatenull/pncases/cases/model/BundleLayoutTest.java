package ru.privatenull.pncases.cases.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BundleLayoutTest {

    private static final int OPEN = 22;
    private static final int SIZE = 54;

    @Test
    void oneButtonStaysWhereTheOpenButtonWas() {
        assertArrayEquals(new int[]{22}, BundleLayout.slots(1, OPEN, SIZE));
    }

    @Test
    void upToFourButtonsKeepAGap() {
        assertArrayEquals(new int[]{21, 23}, BundleLayout.slots(2, OPEN, SIZE));
        assertArrayEquals(new int[]{20, 22, 24}, BundleLayout.slots(3, OPEN, SIZE));
        assertArrayEquals(new int[]{19, 21, 23, 25}, BundleLayout.slots(4, OPEN, SIZE));
    }

    @Test
    void fiveOrMoreStandSideBySide() {
        assertArrayEquals(new int[]{20, 21, 22, 23, 24}, BundleLayout.slots(5, OPEN, SIZE));
        assertArrayEquals(new int[]{19, 20, 21, 22, 23, 24, 25}, BundleLayout.slots(7, OPEN, SIZE));
    }

    @Test
    void moreThanSevenUseASecondRow() {
        assertArrayEquals(new int[]{20, 21, 22, 23, 24, 29, 30, 31, 32, 33}, BundleLayout.slots(10, OPEN, SIZE));
    }

    @Test
    void buttonsBeyondTheLimitAreCut() {
        assertEquals(BundleLayout.MAX_BUTTONS, BundleLayout.slots(40, OPEN, SIZE).length);
        assertEquals(0, BundleLayout.slots(0, OPEN, SIZE).length);
    }

    @Test
    void anOffCentreOpenSlotNeverReachesTheBorderColumns() {
        for (int slot : BundleLayout.slots(7, 18, SIZE)) {
            assertEquals(true, slot % 9 >= 1 && slot % 9 <= 7);
        }
    }
}
