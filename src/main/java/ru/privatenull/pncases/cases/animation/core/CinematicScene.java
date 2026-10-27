package ru.privatenull.pncases.cases.animation.core;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Composable one-tick cinematic engine for deterministic case scenes.
 *
 * <p>A scene has one timeline, any number of always-on and phase-specific
 * tracks, and exact-tick cues. Lifecycle validation and safe completion live
 * here once instead of being repeated by every animation.</p>
 */
public class CinematicScene extends AnimationTask {

    private final AnimationSession session;
    private final AnimationTimeline timeline;
    private final List<Consumer<SceneContext>> tracks = new ArrayList<>();
    private final Map<String, List<Consumer<SceneContext>>> phaseTracks = new HashMap<>();
    private final Map<Integer, List<Consumer<SceneContext>>> cues = new HashMap<>();
    private Runnable completion = () -> { };
    private Runnable cancellation = () -> { };
    private int tick;

    public CinematicScene(AnimationSession session, String stage, AnimationTimeline timeline) {
        super(session, stage);
        if (timeline == null) throw new IllegalArgumentException("animation timeline is required");
        this.session = session;
        this.timeline = timeline;
    }

    /** Runs on every frame. Tracks execute in registration order. */
    public final CinematicScene track(Consumer<SceneContext> track) {
        tracks.add(Objects.requireNonNull(track, "scene track"));
        return this;
    }

    /** Runs only while the named phase is active. */
    public final CinematicScene track(String phase, Consumer<SceneContext> track) {
        if (phase == null || phase.isBlank()) throw new IllegalArgumentException("scene phase is required");
        // Validate the phase while assembling the scene, not halfway through playback.
        timeline.phase(phase);
        phaseTracks.computeIfAbsent(phase, ignored -> new ArrayList<>())
                .add(Objects.requireNonNull(track, "scene phase track"));
        return this;
    }

    /** Runs once at an absolute scene tick. */
    public final CinematicScene at(int sceneTick, Consumer<SceneContext> cue) {
        if (sceneTick < 1 || sceneTick > timeline.duration()) {
            throw new IllegalArgumentException("scene cue is outside timeline: " + sceneTick);
        }
        cues.computeIfAbsent(sceneTick, ignored -> new ArrayList<>())
                .add(Objects.requireNonNull(cue, "scene cue"));
        return this;
    }

    public final CinematicScene onComplete(Runnable action) {
        completion = Objects.requireNonNull(action, "scene completion");
        return this;
    }

    public final CinematicScene onCancel(Runnable action) {
        cancellation = Objects.requireNonNull(action, "scene cancellation");
        return this;
    }

    public final AnimationSession session() {
        return session;
    }

    public final AnimationTimeline timeline() {
        return timeline;
    }

    @Override
    protected final void tick() {
        Player player = session.player();
        if (player == null || !player.isOnline()) {
            onSceneCancelled();
            cancellation.run();
            session.cancel();
            cancel();
            return;
        }

        tick++;
        SceneContext context = new SceneContext(session, timeline.frame(tick));
        renderFrame(context);
        runTracks(tracks, context);
        runTracks(phaseTracks.get(context.frame().phase()), context);
        runTracks(cues.remove(tick), context);

        if (!context.frame().timelineEnd() || session.isClosed()) return;

        onSceneComplete();
        completion.run();
        if (!session.isClosed()) session.complete();
        cancel();
    }

    /** Compatibility hook used by class-based scenes. New scenes normally use tracks. */
    protected void renderFrame(SceneContext context) {
    }

    protected void onSceneComplete() {
    }

    protected void onSceneCancelled() {
    }

    private static void runTracks(List<Consumer<SceneContext>> actions, SceneContext context) {
        if (actions == null) return;
        for (Consumer<SceneContext> action : List.copyOf(actions)) action.accept(context);
    }
}
