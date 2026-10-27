package ru.privatenull.pncases.cases.animation.core;

import org.bukkit.scheduler.BukkitTask;
import ru.privatenull.pncases.PnCasesPlugin;

/** Base class for deterministic phase-based case animations. */
public abstract class TimelineCaseAnimation extends AbstractCaseAnimation {

    protected TimelineCaseAnimation(PnCasesPlugin plugin) {
        super(plugin);
    }

    protected final BukkitTask startTimeline(AnimationSession session, TimelineTask task) {
        return startScene(session, task);
    }

    /** Starts a composable Cinematic Engine scene inside the current session. */
    protected final BukkitTask startScene(AnimationSession session, CinematicScene scene) {
        if (session == null || scene == null) {
            throw new IllegalArgumentException("animation session and scene are required");
        }
        return scheduleTask(session, scene, 0L, 1L);
    }
}
