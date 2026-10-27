package ru.privatenull.pncases.support.task;

import org.bukkit.scheduler.BukkitTask;

/** A repeating or delayed job that can cancel itself, scheduled through {@link PluginTasks}. */
public abstract class PluginRunnable implements Runnable {

    private volatile BukkitTask task;
    private volatile boolean cancelled;

    public final BukkitTask runTaskTimer(PluginTasks tasks, long delayTicks, long periodTicks) {
        BukkitTask scheduled = tasks.timer(delayTicks, periodTicks, this);
        return remember(scheduled);
    }

    public final BukkitTask runTaskLater(PluginTasks tasks, long delayTicks) {
        BukkitTask scheduled = tasks.later(delayTicks, this);
        return remember(scheduled);
    }

    private BukkitTask remember(BukkitTask scheduled) {
        task = scheduled;
        if (cancelled) scheduled.cancel();
        return scheduled;
    }

    /** Stops future runs. Safe to call from {@link #run()} and before the job is scheduled. */
    public void cancel() {
        cancelled = true;
        BukkitTask current = task;
        if (current != null) current.cancel();
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
