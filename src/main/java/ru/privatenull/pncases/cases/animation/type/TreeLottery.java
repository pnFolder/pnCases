package ru.privatenull.pncases.cases.animation.type;

/**
 * Pure maths of the garland lottery: when the running highlight steps from one prize bulb to the next so that it
 * slows down and stops exactly on the winner, and where the prize bulbs hang on the tree.
 */
final class TreeLottery {

    private TreeLottery() {
    }

    /** How many highlight steps run before it stops on {@code winner}: two full laps plus the way to the winner. */
    static int steps(int count, int winner) {
        return 2 * count + winner + 1;
    }

    /** Which prize bulb is highlighted at step {@code step}; the last step is always the winner. */
    static int bulbAt(int count, int step) {
        return step % count;
    }

    /**
     * Ticks (counted from 1) at which each step happens. Early steps are one tick apart, then the gaps widen, and
     * the final step lands on {@code totalTicks}.
     */
    static int[] schedule(int count, int winner, int totalTicks) {
        int steps = steps(count, winner);
        int[] times = new int[steps];
        double span = Math.max(1, steps - 1);
        for (int step = 0; step < steps; step++) {
            double x = step / span;
            int time = 1 + (int) Math.round((totalTicks - 1) * Math.pow(x, 1.8));
            times[step] = step == 0 ? 1 : Math.max(times[step - 1] + 1, time);
        }
        // Rounding up the early steps can push the tail past the end; pull the tail back, keeping the gaps.
        for (int step = steps - 1; step >= 0; step--) {
            int latest = totalTicks - (steps - 1 - step);
            if (times[step] > latest) times[step] = latest;
        }
        return times;
    }

    /** Angle of prize bulb {@code index} round the tree (radians); they spiral all the way round it. */
    static double angle(int index, int count) {
        return 0.4 + index * (Math.PI * 2.0) / Math.max(1, count);
    }

    /** Height of prize bulb {@code index} on the tree, growing with the index. */
    static double height(int index, int count, double low, double high) {
        if (count <= 1) return low;
        return low + (high - low) * index / (count - 1);
    }
}
