package ru.privatenull.pncases.cases.animation.core;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * The winter scene around a case (the New Year decoration) is locked until the opening moment and stays open
 * for good after it, so no new patch is needed. The moment is counted in Moscow time.
 */
public final class AnimationSeason {

    private static final Instant NEW_YEAR_OPENS = ZonedDateTime.of(2026, 12, 1, 0, 0, 0, 0, ZoneId.of("Europe/Moscow")).toInstant();

    public static final String NEW_YEAR_TEXT = "откроется 1 декабря 2026";

    private AnimationSeason() {
    }

    /** The winter scene around a case is the New Year decoration: it opens at the same moment. */
    public static boolean newYearOpen() {
        return newYearOpen(Instant.now());
    }

    static boolean newYearOpen(Instant now) {
        return !now.isBefore(NEW_YEAR_OPENS);
    }
}
