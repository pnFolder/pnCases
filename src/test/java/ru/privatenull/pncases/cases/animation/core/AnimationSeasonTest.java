package ru.privatenull.pncases.cases.animation.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationSeasonTest {

    @Test
    void closedBeforeFirstOfDecemberMoscowTime() {
        assertFalse(AnimationSeason.newYearOpen(Instant.parse("2026-10-26T12:00:00Z")));
        assertFalse(AnimationSeason.newYearOpen(Instant.parse("2026-11-30T20:59:59Z")));
    }

    @Test
    void opensOnFirstOfDecemberAndStaysOpenForGood() {
        assertTrue(AnimationSeason.newYearOpen(Instant.parse("2026-11-30T21:00:00Z")));
        assertTrue(AnimationSeason.newYearOpen(Instant.parse("2028-07-01T12:00:00Z")));
    }
}
