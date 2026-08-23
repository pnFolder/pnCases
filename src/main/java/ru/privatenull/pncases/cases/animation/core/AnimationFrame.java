package ru.privatenull.pncases.cases.animation.core;

/** Immutable position inside one named animation phase. */
public record AnimationFrame(
        int tick,
        String phase,
        int phaseTick,
        int phaseDuration,
        double progress,
        boolean phaseStart,
        boolean phaseEnd,
        boolean timelineEnd
) {

    public boolean is(String expectedPhase) {
        return expectedPhase != null && expectedPhase.equals(phase);
    }
}
