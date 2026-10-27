package ru.privatenull.pncases.cases.animation.core;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.Reward;

/**
 * Immutable frame context passed to cinematic tracks.
 *
 * <p>The context exposes the run scope instead of making every scene carry the
 * same player/world/reward fields and cleanup plumbing.</p>
 */
public record SceneContext(AnimationSession session, AnimationFrame frame) {

    public SceneContext {
        if (session == null || frame == null) {
            throw new IllegalArgumentException("scene session and frame are required");
        }
    }

    public Player player() {
        return session.player();
    }

    public World world() {
        return session.world();
    }

    public Location base() {
        return session.base();
    }

    public CaseDefinition definition() {
        return session.definition();
    }

    public Reward reward() {
        return session.reward();
    }

    public int tick() {
        return frame.tick();
    }

    public int phaseTick() {
        return frame.phaseTick();
    }

    public double progress() {
        return frame.progress();
    }

    public boolean phaseStart() {
        return frame.phaseStart();
    }

    public boolean phaseEnd() {
        return frame.phaseEnd();
    }

    public boolean is(String phase) {
        return frame.is(phase);
    }

    public <T extends Entity> T track(T entity) {
        return session.track(entity);
    }

    public <T extends BukkitTask> T track(T task) {
        return session.track(task);
    }
}
