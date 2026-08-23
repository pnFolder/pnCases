package ru.privatenull.pncases.cases.animation.core;

import org.bukkit.scheduler.BukkitTask;
import ru.privatenull.pncases.PnCasesPlugin;

/** Base class for deterministic phase-based case animations. */
public abstract class TimelineCaseAnimation extends AbstractCaseAnimation {

    protected TimelineCaseAnimation(PnCasesPlugin plugin) {
        super(plugin);
    }

    protected final BukkitTask startTimeline(AnimationSession session, TimelineTask task) {
        if (session == null || task == null) {
            throw new IllegalArgumentException("animation session and task are required");
        }
        return scheduleTask(session, task, 0L, 1L);
    }
}
