package ru.privatenull.pncases.cases.animation.core;

/**
 * Standard one-tick driver for timeline animations.
 *
 * <p>It owns player/session validation, phase resolution and normal completion.
 * Concrete animations only render the current frame and optional final burst.</p>
 */
public abstract class TimelineTask extends CinematicScene {

    protected TimelineTask(AnimationSession session, String stage, AnimationTimeline timeline) {
        super(session, stage, timeline);
    }

    @Override
    protected final void renderFrame(SceneContext context) {
        render(context.frame());
    }

    @Override
    protected final void onSceneComplete() {
        onComplete();
    }

    @Override
    protected final void onSceneCancelled() {
        onCancelled();
    }

    protected abstract void render(AnimationFrame frame);

    protected void onComplete() {
    }

    protected void onCancelled() {
    }
}
