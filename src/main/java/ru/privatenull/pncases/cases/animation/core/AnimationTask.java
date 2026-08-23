package ru.privatenull.pncases.cases.animation.core;

import org.bukkit.scheduler.BukkitRunnable;

/**
 * Guards one scheduled animation stage. A broken visual tick must never leave
 * the case locked or prevent the already reserved reward from completing.
 */
public abstract class AnimationTask extends BukkitRunnable {

    private final AnimationSession run;
    private final String stage;

    protected AnimationTask(AnimationSession run, String stage) {
        if (run == null) throw new IllegalArgumentException("animation run is required");
        this.run = run;
        this.stage = stage == null || stage.isBlank() ? "scene" : stage;
    }

    @Override
    public final void run() {
        if (run.isClosed()) {
            cancel();
            return;
        }
        try {
            tick();
        } catch (Throwable failure) {
            try {
                cancel();
            } finally {
                run.fail(stage, failure);
            }
        }
    }

    protected abstract void tick();
}
