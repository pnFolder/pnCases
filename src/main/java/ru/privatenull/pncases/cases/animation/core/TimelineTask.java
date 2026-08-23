package ru.privatenull.pncases.cases.animation.core;

import org.bukkit.entity.Player;

/**
 * Standard one-tick driver for timeline animations.
 *
 * <p>It owns player/session validation, phase resolution and normal completion.
 * Concrete animations only render the current frame and optional final burst.</p>
 */
public abstract class TimelineTask extends AnimationTask {

    private final AnimationSession session;
    private final AnimationTimeline timeline;
    private int tick;

    protected TimelineTask(AnimationSession session, String stage, AnimationTimeline timeline) {
        super(session, stage);
        if (timeline == null) throw new IllegalArgumentException("animation timeline is required");
        this.session = session;
        this.timeline = timeline;
    }

    @Override
    protected final void tick() {
        Player player = session.player();
        if (player == null || !player.isOnline()) {
            onCancelled();
            session.cancel();
            cancel();
            return;
        }

        tick++;
        AnimationFrame frame = timeline.frame(tick);
        render(frame);
        if (!frame.timelineEnd() || session.isClosed()) return;

        onComplete();
        if (!session.isClosed()) session.complete();
        cancel();
    }

    protected abstract void render(AnimationFrame frame);

    protected void onComplete() {
    }

    protected void onCancelled() {
    }
}
