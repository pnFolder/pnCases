package ru.privatenull.pncases.cases.animation.effect;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.bukkit.Particle;
import org.junit.jupiter.api.Test;
import ru.privatenull.pncases.cases.model.AnimationType;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pncases.gui.GuiDesign;

import static org.junit.jupiter.api.Assertions.*;

class RewardOrbitTest {

    @Test
    void reservedRewardAndItsLabelOccupyTheExactStoppingSlot() {
        List<Reward> source = IntStream.range(0, 16).mapToObj(i -> reward("reward-" + i, 10)).toList();
        Reward winner = source.get(11);
        for (int index = 0; index < 8; index++) {
            List<Reward> candidates = RewardOrbit.candidates(source, winner, index);
            assertEquals(8, candidates.size());
            assertSame(winner, candidates.get(index));
            assertEquals(8, candidates.stream().distinct().count());
            assertTrue(source.containsAll(candidates));
            double rotation = RewardOrbit.stopRotation(index, candidates.size(), 3);
            double angle = RewardOrbit.angle(index, candidates.size(), rotation);
            assertEquals(0.0, Math.cos(angle), 1.0e-12);
            assertEquals(1.0, Math.sin(angle), 1.0e-12);
        }
        assertSame(winner, source.get(11));
    }

    @Test
    void smallPoolsRepeatOnlyRealRewardsAndIgnoreDisabledCandidates() {
        Reward winner = reward("winner", 10);
        Reward other = reward("other", 10);
        Reward disabled = reward("disabled", 0);
        List<Reward> source = new ArrayList<>(List.of(winner, other, disabled));
        source.add(null);
        List<Reward> candidates = RewardOrbit.candidates(source, winner, 4);
        assertSame(winner, candidates.get(4));
        assertEquals(7, candidates.stream().filter(candidate -> candidate == other).count());
        assertEquals(4, source.size());
        assertEquals(List.of(winner, winner, winner, winner, winner, winner, winner, winner),
                RewardOrbit.candidates(List.of(winner), winner, 7));
    }

    @Test
    void reservedWinnerIsRetainedEvenWhenNotInThePreviewPool() {
        Reward winner = reward("reserved", 0);
        List<Reward> candidates = RewardOrbit.candidates(List.of(reward("other", 10)), winner, 0);
        assertSame(winner, candidates.get(0));
        assertEquals(8, candidates.size());
        assertTrue(RewardOrbit.candidates(List.of(), winner, 0).stream().allMatch(r -> r == winner));
    }

    @Test
    void rotationAcceleratesThenSlowsToAnExactStopWithoutReversing() {
        assertEquals(0.0, RewardOrbit.spinProgress(-1.0), 1.0e-12);
        assertEquals(1.0, RewardOrbit.spinProgress(2.0), 1.0e-12);
        double previousSpeed = 0.0;
        for (int tick = 1; tick <= 1000; tick++) {
            double from = (tick - 1) / 1000.0;
            double to = tick / 1000.0;
            double speed = RewardOrbit.spinProgress(to) - RewardOrbit.spinProgress(from);
            assertTrue(speed >= -1.0e-12);
            if (tick <= 160) assertTrue(speed >= previousSpeed - 1.0e-12);
            if (tick > 161) assertTrue(speed <= previousSpeed + 1.0e-12);
            previousSpeed = speed;
        }
        assertTrue(previousSpeed < 1.0e-6);
    }

    @Test
    void newParticlesUseTheRequiredDataTypes() {
        assertEquals(Particle.DustOptions.class,
                AnimationEffects.particle(AnimationEffects.Kind.DUST).getDataType());
        assertEquals(Void.class, AnimationEffects.particle(AnimationEffects.Kind.FIREWORK).getDataType());
    }

    @Test
    void bothAnimationsHaveDistinctNamesAndFitTheExistingGui() {
        assertTrue(AnimationType.values().length <= GuiDesign.ANIMATION_LIST_SLOTS.size());
        assertNotEquals(GuiDesign.animationName(AnimationType.PRISM),
                GuiDesign.animationName(AnimationType.STAR_CAROUSEL));
        for (AnimationType type : List.of(AnimationType.PRISM, AnimationType.STAR_CAROUSEL)) {
            assertEquals(type, AnimationType.valueOf(type.name()));
            assertFalse(GuiDesign.animationDescription(type).isEmpty());
            assertTrue(GuiDesign.animationSelectionName(type).startsWith(GuiDesign.animationPlainName(type)));
        }
    }

    private static Reward reward(String name, int chance) {
        return new Reward(chance, Reward.Type.PERSONAL, null, null, null, null, null, name);
    }
}
