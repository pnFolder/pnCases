package ru.privatenull.pncases.cases.animation.core;

import org.bukkit.entity.Player;

/**
 * Driver for scenes whose duration depends on player input or spawned actors.
 * Returning {@code true} from {@link #update(int)} completes the animation.
 */
public abstract class InteractiveAnimationTask extends AnimationTask {

    private final AnimationSession session;
    private int tick;

    protected InteractiveAnimationTask(AnimationSession session, String stage) {
        super(session, stage);
        this.session = session;
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

        if (!update(++tick) || session.isClosed()) return;

        onComplete();
        if (!session.isClosed()) session.complete();
        cancel();
    }

    protected abstract boolean update(int tick);

    protected void onComplete() {
    }

    protected void onCancelled() {
    }
}
