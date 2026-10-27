package ru.privatenull.pncases.cases.animation.core;

import org.junit.jupiter.api.Test;
import ru.privatenull.pncases.cases.animation.core.AnimationAvailability.Availability;
import ru.privatenull.pncases.cases.animation.core.AnimationAvailability.Probe;
import ru.privatenull.pncases.cases.model.AnimationType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationAvailabilityTest {

    @Test
    void onlyTheThreeMobAnimationsNeedMobs() {
        for (AnimationType type : AnimationType.values()) {
            boolean expected = type == AnimationType.MOB_HUNT
                    || type == AnimationType.PILLAGER_RAID
                    || type == AnimationType.POISON;
            assertEquals(expected, AnimationRequirements.needsMobs(type), type.name());
        }
        assertFalse(AnimationRequirements.needsMobs(null));
    }

    @Test
    void everyMobAnimationNamesTheMobsItSpawns() {
        assertEquals(2, AnimationRequirements.mobTypes(AnimationType.MOB_HUNT).size());
        assertEquals(1, AnimationRequirements.mobTypes(AnimationType.PILLAGER_RAID).size());
        assertEquals(1, AnimationRequirements.mobTypes(AnimationType.POISON).size());
        assertTrue(AnimationRequirements.mobTypes(AnimationType.PRISM).isEmpty());
    }

    @Test
    void aPeacefulWorldBlocksMobAnimationsWithoutTestingAnything() {
        Availability result = AnimationAvailability.decide(true, List.of());

        assertFalse(result.available());
        assertTrue(result.reason().contains("Мирная"));
    }

    @Test
    void aCancelledSpawnMeansTheRegionForbidsMobs() {
        Availability result = AnimationAvailability.decide(false, List.of(Probe.SPAWNED, Probe.CANCELLED));

        assertFalse(result.available());
        assertTrue(result.reason().contains("запрещён"));
    }

    @Test
    void aFailedSpawnIsReportedSeparately() {
        Availability result = AnimationAvailability.decide(false, List.of(Probe.FAILED));

        assertFalse(result.available());
        assertTrue(result.reason().contains("не удалось"));
    }

    @Test
    void successfulSpawnsOrNoTestAtAllMeanAvailable() {
        assertTrue(AnimationAvailability.decide(false, List.of(Probe.SPAWNED, Probe.SPAWNED)).available());
        assertTrue(AnimationAvailability.decide(false, List.of()).available());
        assertEquals("", AnimationAvailability.Availability.OK.reason());
    }

    @Test
    void animationsWithoutMobsAreAlwaysAvailableAndNeedNoLocation() {
        AnimationAvailability availability = new AnimationAvailability();

        assertTrue(availability.check(AnimationType.PRISM, null).available());
        assertTrue(availability.check(AnimationType.MOB_HUNT, null).available());
    }
}
