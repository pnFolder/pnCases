package ru.privatenull.pncases.support.task;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** A scheduled task of any backend, shaped as a {@link BukkitTask} so that callers do not depend on the scheduler. */
public final class PluginTask implements BukkitTask {

    private static final AtomicInteger IDS = new AtomicInteger();

    private final Plugin owner;
    private final boolean sync;
    private final Runnable canceller;
    private final BooleanSupplier cancelled;
    private final int id = IDS.incrementAndGet();

    public PluginTask(Plugin owner, boolean sync, Runnable canceller, BooleanSupplier cancelled) {
        this.owner = owner;
        this.sync = sync;
        this.canceller = canceller;
        this.cancelled = cancelled;
    }

    /** A task that never runs: used once the plugin is shutting down. */
    static PluginTask dead(Plugin owner) {
        return new PluginTask(owner, true, () -> { }, () -> true);
    }

    @Override
    public int getTaskId() {
        return id;
    }

    @Override
    public Plugin getOwner() {
        return owner;
    }

    @Override
    public boolean isSync() {
        return sync;
    }

    @Override
    public boolean isCancelled() {
        return cancelled.getAsBoolean();
    }

    @Override
    public void cancel() {
        canceller.run();
    }
}
