package ru.privatenull.pncases.cases.model;

/**
 * Where the open buttons of the cases inside a bundle case stand in its menu: centred on the row of the
 * ordinary open button, between the first and the last inner column.
 */
public final class BundleLayout {

    /** Buttons that fit: seven in a row, two rows. */
    public static final int MAX_BUTTONS = 14;

    private static final int WIDTH = 9;
    private static final int FIRST_COLUMN = 1;
    private static final int LAST_COLUMN = 7;
    private static final int PER_ROW = LAST_COLUMN - FIRST_COLUMN + 1;

    private BundleLayout() {
    }

    /**
     * Slots of {@code count} buttons around {@code openSlot}. Up to four buttons stand with a gap between
     * them, five or more stand side by side; more than seven use a second row.
     */
    public static int[] slots(int count, int openSlot, int size) {
        int rows = Math.max(1, size / WIDTH);
        int total = Math.min(Math.max(0, count), Math.min(MAX_BUTTONS, PER_ROW * rows));
        if (total == 0) return new int[0];

        int rowsNeeded = (total + PER_ROW - 1) / PER_ROW;
        int openRow = Math.max(0, Math.min(rows - 1, openSlot / WIDTH));
        int firstRow = Math.max(0, Math.min(rows - rowsNeeded, openRow - (rowsNeeded - 1) / 2));
        int center = Math.max(FIRST_COLUMN, Math.min(LAST_COLUMN, openSlot % WIDTH));

        int[] slots = new int[total];
        int placed = 0;
        for (int row = 0; row < rowsNeeded; row++) {
            int inRow = total / rowsNeeded + (row < total % rowsNeeded ? 1 : 0);
            boolean spaced = inRow <= 4;
            int step = spaced ? 2 : 1;
            int span = (inRow - 1) * step;
            int start = spaced ? center - (inRow - 1) : center - inRow / 2;
            start = Math.max(FIRST_COLUMN, Math.min(LAST_COLUMN - span, start));
            for (int index = 0; index < inRow; index++) {
                slots[placed++] = (firstRow + row) * WIDTH + start + index * step;
            }
        }
        return slots;
    }
}
