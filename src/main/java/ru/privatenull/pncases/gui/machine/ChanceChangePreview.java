package ru.privatenull.pncases.gui.machine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What happens to the whole pool when one reward is given a new drop chance. Rewards share one pool,
 * so changing a single weight changes the percentage of every other reward.
 */
record ChanceChangePreview(int newWeight, double beforePercent, double afterPercent, List<Shift> others) {

    /** How the share of another reward moves. */
    record Shift(int index, double beforePercent, double afterPercent) {
        double change() {
            return Math.abs(afterPercent - beforePercent);
        }
    }

    /**
     * @param weights current weights of all rewards in pool order
     * @param index   the reward being edited
     * @param percent the share the edited reward should get, 0 &lt; percent &le; 100
     */
    static ChanceChangePreview compute(List<Integer> weights, int index, double percent) {
        int otherWeights = 0;
        for (int i = 0; i < weights.size(); i++) {
            if (i != index) otherWeights += Math.max(1, weights.get(i));
        }
        int newWeight = RewardConfigEditor.chanceForPercent(otherWeights, percent);

        double totalBefore = total(weights, -1, 0);
        double totalAfter = total(weights, index, newWeight);
        double beforeSelf = share(weights.get(index), totalBefore);
        double afterSelf = share(newWeight, totalAfter);

        List<Shift> shifts = new ArrayList<>();
        for (int i = 0; i < weights.size(); i++) {
            if (i == index) continue;
            shifts.add(new Shift(i, share(weights.get(i), totalBefore), share(weights.get(i), totalAfter)));
        }
        shifts.sort(Comparator.comparingDouble(Shift::change).reversed().thenComparingInt(Shift::index));
        return new ChanceChangePreview(newWeight, beforeSelf, afterSelf, List.copyOf(shifts));
    }

    private static double total(List<Integer> weights, int replaceIndex, int replacement) {
        double sum = 0;
        for (int i = 0; i < weights.size(); i++) {
            sum += Math.max(0, i == replaceIndex ? replacement : weights.get(i));
        }
        return sum;
    }

    private static double share(int weight, double total) {
        return total <= 0 ? 0 : Math.max(0, weight) * 100.0 / total;
    }
}
