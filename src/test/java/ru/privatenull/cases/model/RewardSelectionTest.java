package ru.privatenull.cases.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RewardSelectionTest {

    @Test
    void selectsRewardAtWeightBoundaries() {
        Reward first = reward(10, "first");
        Reward second = reward(20, "second");
        Reward third = reward(30, "third");
        List<Reward> rewards = List.of(first, second, third);

        assertEquals(first, Reward.selectByRoll(rewards, 1));
        assertEquals(first, Reward.selectByRoll(rewards, 10));
        assertEquals(second, Reward.selectByRoll(rewards, 11));
        assertEquals(second, Reward.selectByRoll(rewards, 30));
        assertEquals(third, Reward.selectByRoll(rewards, 60));
    }

    @Test
    void rejectsEmptyRewardList() {
        assertThrows(IllegalArgumentException.class, () -> Reward.select(List.of()));
    }

    private Reward reward(int chance, String name) {
        return new Reward(chance, Reward.Type.VAULT, null, null, null, null,
                1.0, 0, null, name);
    }
}
