package ru.privatenull.pncases.cases.animation.effect;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import ru.privatenull.pncases.support.compat.ServerVersion;

/** Shared movement defaults for every display entity owned by an animation. */
public final class DisplayAnimator {

    private static final int DEFAULT_INTERPOLATION_TICKS = 2;
    private static final int DEFAULT_TELEPORT_TICKS = 2;

    private DisplayAnimator() {
    }

    public static <T extends Entity> T prepare(T entity) {
        if (!(entity instanceof Display display)) return entity;
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(DEFAULT_INTERPOLATION_TICKS);
        // 1.20/1.20.1 support transformation interpolation, but not teleport duration.
        if (ServerVersion.current().isAtLeast(1, 20, 2)) {
            display.setTeleportDuration(DEFAULT_TELEPORT_TICKS);
        }
        return entity;
    }

    public static void move(Display display, Location location) {
        if (display == null || location == null || !display.isValid()) return;
        prepare(display);
        display.teleport(location);
    }
}
