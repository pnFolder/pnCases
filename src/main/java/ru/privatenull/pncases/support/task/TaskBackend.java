package ru.privatenull.pncases.support.task;

/** Where scheduled work actually runs: pnLibrary's task scope, or the Bukkit scheduler before it is ready. */
public interface TaskBackend {

    /**
     * @param periodTicks repeat interval, 0 or less for a one-shot task
     * @param async       run off the server thread
     */
    PluginTask schedule(Runnable action, long delayTicks, long periodTicks, boolean async);
}
