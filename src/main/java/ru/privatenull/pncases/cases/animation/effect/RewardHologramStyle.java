package ru.privatenull.pncases.cases.animation.effect;

import org.bukkit.configuration.ConfigurationSection;
import ru.privatenull.pncases.hologram.HologramTextStyle;

/** Local visual policy for the name shown above a revealed reward. */
public record RewardHologramStyle(
        float scale,
        double heightOffset,
        int visibilityDistance,
        int brightness,
        int movementTicks,
        int revealDelayTicks
) {
    public static RewardHologramStyle read(ConfigurationSection root) {
        ConfigurationSection section = root == null
                ? null : root.getConfigurationSection("animations.reward-hologram");
        if (section == null) return defaults();
        return new RewardHologramStyle(
                clamp((float) section.getDouble("scale", 1.45), 0.5f, 3.0f),
                clamp(section.getDouble("height-offset", 0.16), -0.5, 2.0),
                clamp(section.getInt("visibility-distance", 48), 8, 128),
                clamp(section.getInt("brightness", 15), 0, 15),
                clamp(section.getInt("movement-ticks", 2), 0, 10),
                clamp(section.getInt("reveal-delay-ticks", 6), 0, 40)
        );
    }

    public HologramTextStyle textStyle() {
        return new HologramTextStyle(
                scale, visibilityDistance, brightness, 1, movementTicks);
    }

    private static RewardHologramStyle defaults() {
        return new RewardHologramStyle(1.45f, 0.16, 48, 15, 2, 6);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
