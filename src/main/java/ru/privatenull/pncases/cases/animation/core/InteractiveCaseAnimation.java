package ru.privatenull.pncases.cases.animation.core;

import org.bukkit.scheduler.BukkitTask;
import ru.privatenull.pncases.PnCasesPlugin;

/** Base for animations driven by player interaction instead of a fixed timeline. */
public abstract class InteractiveCaseAnimation extends AbstractCaseAnimation {

    protected InteractiveCaseAnimation(PnCasesPlugin plugin) {
        super(plugin);
    }

    protected final BukkitTask startInteractive(AnimationSession session, InteractiveAnimationTask task) {
        return startInteractive(session, task, 0L, 1L);
    }

    protected final BukkitTask startInteractive(
            AnimationSession session,
            InteractiveAnimationTask task,
            long delay,
            long period
    ) {
        return scheduleTask(session, task, delay, period);
    }
}
