package ru.privatenull.pncases.cases.animation.type;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeLotteryTest {

    @Test
    void theLastStepAlwaysLandsOnTheWinner() {
        for (int count = 1; count <= 8; count++) {
            for (int winner = 0; winner < count; winner++) {
                int steps = TreeLottery.steps(count, winner);
                assertEquals(winner, TreeLottery.bulbAt(count, steps - 1), count + "/" + winner);
            }
        }
    }

    @Test
    void stepsAreOrderedAndSlowDownTowardsTheEnd() {
        for (int winner = 0; winner < 8; winner++) {
            int[] times = TreeLottery.schedule(8, winner, 64);

            assertEquals(1, times[0]);
            assertEquals(64, times[times.length - 1], "winner " + winner);
            for (int i = 1; i < times.length; i++) {
                assertTrue(times[i] > times[i - 1], "strictly increasing at " + i);
            }
            int firstGap = times[1] - times[0];
            int lastGap = times[times.length - 1] - times[times.length - 2];
            assertTrue(lastGap > firstGap, "the highlight must slow down");
        }
    }

    @Test
    void prizeBulbsSpiralRoundTheTreeAndClimb() {
        double previous = -1.0;
        double previousAngle = -1.0;
        for (int i = 0; i < 8; i++) {
            double angle = TreeLottery.angle(i, 8);
            assertTrue(angle > previousAngle && angle < Math.PI * 2.0 + 0.5, "angle " + i);
            previousAngle = angle;
            double height = TreeLottery.height(i, 8, 0.8, 3.0);
            assertTrue(height > previous, "height " + i);
            previous = height;
        }
    }
}
