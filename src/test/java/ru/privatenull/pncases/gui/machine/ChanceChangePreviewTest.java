package ru.privatenull.pncases.gui.machine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChanceChangePreviewTest {

    @Test
    void theEditedRewardReachesTheRequestedShare() {
        ChanceChangePreview preview = ChanceChangePreview.compute(List.of(50, 30, 20), 2, 50.0);

        assertEquals(20.0, preview.beforePercent(), 0.001);
        assertEquals(50.0, preview.afterPercent(), 0.5);
    }

    @Test
    void otherRewardsKeepTheirRelativeProportions() {
        ChanceChangePreview preview = ChanceChangePreview.compute(List.of(50, 30, 20), 2, 50.0);

        double first = preview.others().stream().filter(shift -> shift.index() == 0).findFirst().orElseThrow().afterPercent();
        double second = preview.others().stream().filter(shift -> shift.index() == 1).findFirst().orElseThrow().afterPercent();
        assertEquals(50.0 / 30.0, first / second, 0.05);
    }

    @Test
    void sharesAfterTheChangeAddUpToOneHundred() {
        ChanceChangePreview preview = ChanceChangePreview.compute(List.of(10, 20, 30, 40), 1, 5.0);

        double sum = preview.afterPercent() + preview.others().stream().mapToDouble(ChanceChangePreview.Shift::afterPercent).sum();
        assertEquals(100.0, sum, 0.001);
    }

    @Test
    void theBiggestShiftsComeFirst() {
        ChanceChangePreview preview = ChanceChangePreview.compute(List.of(70, 20, 10, 100), 3, 80.0);

        List<ChanceChangePreview.Shift> shifts = preview.others();
        for (int index = 1; index < shifts.size(); index++) {
            assertTrue(shifts.get(index - 1).change() >= shifts.get(index).change());
        }
    }

    @Test
    void aLoneRewardAlwaysHasTheWholePool() {
        ChanceChangePreview preview = ChanceChangePreview.compute(List.of(37), 0, 10.0);

        assertEquals(100, preview.newWeight());
        assertEquals(100.0, preview.afterPercent(), 0.001);
        assertTrue(preview.others().isEmpty());
    }
}
