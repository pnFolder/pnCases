package ru.privatenull.pncases.hologram;

/** Rendering and movement settings for short-lived animation text. */
public record HologramTextStyle(
        float scale,
        int visibilityDistance,
        int brightness,
        int interpolationTicks,
        int movementTicks
) {
    public static final HologramTextStyle DEFAULT = new HologramTextStyle(1.0f, 48, 15, 1, 2);

    public HologramTextStyle normalized() {
        return new HologramTextStyle(
                Math.max(0.25f, Math.min(4.0f, scale)),
                Math.max(1, Math.min(256, visibilityDistance)),
                Math.max(0, Math.min(15, brightness)),
                Math.max(0, Math.min(59, interpolationTicks)),
                Math.max(0, Math.min(59, movementTicks)));
    }

    public HologramTextStyle withScale(float value) {
        return new HologramTextStyle(value, visibilityDistance, brightness,
                interpolationTicks, movementTicks).normalized();
    }
}
