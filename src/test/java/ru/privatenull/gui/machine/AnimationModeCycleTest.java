package ru.privatenull.gui.machine;

import org.junit.jupiter.api.Test;
import ru.privatenull.cases.model.AnimationType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnimationModeCycleTest {

    private static final AnimationType[] MODES = {
            AnimationType.ANVIL,
            AnimationType.DYNAMITE,
            AnimationType.PORTAL
    };

    @Test
    void cyclesForwardThroughPlayerChoiceAndFixedAnimations() {
        assertEquals(AnimationType.ANVIL, MachineAnimationScreen.nextMode(null, MODES, false));
        assertEquals(AnimationType.DYNAMITE, MachineAnimationScreen.nextMode(AnimationType.ANVIL, MODES, false));
        assertNull(MachineAnimationScreen.nextMode(AnimationType.PORTAL, MODES, false));
    }

    @Test
    void cyclesBackwardsInReverseOrder() {
        assertEquals(AnimationType.PORTAL, MachineAnimationScreen.nextMode(null, MODES, true));
        assertEquals(AnimationType.ANVIL, MachineAnimationScreen.nextMode(AnimationType.DYNAMITE, MODES, true));
        assertNull(MachineAnimationScreen.nextMode(AnimationType.ANVIL, MODES, true));
    }

    @Test
    void unknownCurrentModeReturnsToAvailableSequence() {
        assertEquals(AnimationType.ANVIL,
                MachineAnimationScreen.nextMode(AnimationType.PILLAGER_RAID, MODES, false));
    }
}
