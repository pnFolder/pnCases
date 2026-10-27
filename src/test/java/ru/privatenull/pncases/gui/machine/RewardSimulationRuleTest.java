package ru.privatenull.pncases.gui.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardSimulationRuleTest {

    @Test
    void aCaseWithoutRewardsOrWithOneRewardCannotBeSimulated() {
        assertFalse(RewardSimulationScreen.canSimulate(0));
        assertFalse(RewardSimulationScreen.canSimulate(1));
    }

    @Test
    void twoOrMoreRewardsCanBeSimulated() {
        assertTrue(RewardSimulationScreen.canSimulate(2));
        assertTrue(RewardSimulationScreen.canSimulate(30));
    }
}
