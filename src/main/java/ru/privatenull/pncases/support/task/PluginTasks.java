package ru.privatenull.pncases.support.task;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * The single place where pnCases schedules work. Tasks go to pnLibrary's task scope once it is bound, so they
 * follow the platform's own execution model; until then (and if it is unavailable) the Bukkit scheduler is used.
 * Every task is a {@link BukkitTask}, so holding and cancelling it looks the same for both backends.
 */
public final class PluginTasks {

    private final JavaPlugin plugin;
    private volatile TaskBackend backend;

    public PluginTasks(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Switches new tasks to {@code backend}; {@code null} returns to the Bukkit scheduler. */
    public void bind(TaskBackend backend) {
        this.backend = backend;
    }

    /** Runs on the server thread on the next tick. */
    public BukkitTask run(Runnable action) {
        return schedule(action, 0L, 0L, false);
    }

    /** Runs on the server thread after {@code delayTicks}. */
    public BukkitTask later(long delayTicks, Runnable action) {
        return schedule(action, delayTicks, 0L, false);
    }

    /** Repeats on the server thread. */
    public BukkitTask timer(long delayTicks, long periodTicks, Runnable action) {
        return schedule(action, delayTicks, Math.max(1L, periodTicks), false);
    }

    /** Same as {@link #later(long, Runnable)}, with the argument order of the Bukkit scheduler. */
    public BukkitTask later(Runnable action, long delayTicks) {
        return later(delayTicks, action);
    }

    /** Same as {@link #timer(long, long, Runnable)}, with the argument order of the Bukkit scheduler. */
    public BukkitTask timer(Runnable action, long delayTicks, long periodTicks) {
        return timer(delayTicks, periodTicks, action);
    }

    /** Runs off the server thread. */
    public BukkitTask async(Runnable action) {
        return schedule(action, 0L, 0L, true);
    }

    private BukkitTask schedule(Runnable action, long delayTicks, long periodTicks, boolean async) {
        long delay = Math.max(0L, delayTicks);
        Runnable guarded = () -> {
            try {
                action.run();
            } catch (Throwable failure) {
                plugin.getLogger().log(Level.WARNING, "Ошибка в задаче pnCases", failure);
            }
        };
        TaskBackend current = backend;
        if (current != null) {
            try {
                return current.schedule(guarded, delay, periodTicks, async);
            } catch (RuntimeException closed) {
                return PluginTask.dead(plugin);
            }
        }
        if (!plugin.isEnabled()) return PluginTask.dead(plugin);
        var scheduler = plugin.getServer().getScheduler();
        BukkitTask task;
        if (async) task = scheduler.runTaskAsynchronously(plugin, guarded);
        else if (periodTicks > 0L) task = scheduler.runTaskTimer(plugin, guarded, delay, periodTicks);
        else task = scheduler.runTaskLater(plugin, guarded, delay);
        return task;
    }

    /** Wraps a pnLibrary-style cancel state for {@link TaskBackend} implementations. */
    public static PluginTask handle(JavaPlugin owner, boolean sync, Runnable canceller,
                                    java.util.function.BooleanSupplier cancelled) {
        AtomicBoolean cancelledHere = new AtomicBoolean();
        return new PluginTask(owner, sync,
                () -> {
                    cancelledHere.set(true);
                    canceller.run();
                },
                () -> cancelledHere.get() || cancelled.getAsBoolean());
    }
}
