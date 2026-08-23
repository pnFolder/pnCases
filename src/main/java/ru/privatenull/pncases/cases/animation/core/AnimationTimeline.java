package ru.privatenull.pncases.cases.animation.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Small immutable phase map used by animation scenarios instead of raw tick boundaries. */
public final class AnimationTimeline {

    private final Map<String, Phase> phases;
    private final int duration;

    private AnimationTimeline(Map<String, Phase> phases, int duration) {
        this.phases = Map.copyOf(phases);
        this.duration = duration;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Phase phase(String name) {
        Phase phase = phases.get(name);
        if (phase == null) throw new IllegalArgumentException("unknown animation phase " + name);
        return phase;
    }

    public boolean in(String name, int tick) {
        return phase(name).contains(tick);
    }

    public double progress(String name, int tick) {
        return phase(name).progress(tick);
    }

    public int start(String name) {
        return phase(name).start();
    }

    public int end(String name) {
        return phase(name).end();
    }

    public int duration() {
        return duration;
    }

    public AnimationFrame frame(int tick) {
        if (tick < 1 || tick > duration) {
            throw new IllegalArgumentException("animation tick outside timeline: " + tick);
        }
        for (Phase phase : phases.values()) {
            if (!phase.contains(tick)) continue;
            return new AnimationFrame(
                    tick,
                    phase.name(),
                    tick - phase.start() + 1,
                    phase.end() - phase.start() + 1,
                    phase.progress(tick),
                    tick == phase.start(),
                    tick == phase.end(),
                    tick == duration
            );
        }
        throw new IllegalStateException("animation timeline has a gap at tick " + tick);
    }

    public record Phase(String name, int start, int end) {
        public boolean contains(int tick) {
            return tick >= start && tick <= end;
        }

        public double progress(int tick) {
            int length = Math.max(1, end - start + 1);
            return Math.max(0.0, Math.min(1.0, (tick - start + 1) / (double) length));
        }
    }

    public static final class Builder {
        private final Map<String, Phase> phases = new LinkedHashMap<>();
        private int cursor;

        public Builder phase(String name, int duration) {
            if (name == null || name.isBlank() || duration <= 0 || phases.containsKey(name)) {
                throw new IllegalArgumentException("invalid animation phase " + name);
            }
            phases.put(name, new Phase(name, cursor + 1, cursor + duration));
            cursor += duration;
            return this;
        }

        public AnimationTimeline build() {
            if (phases.isEmpty()) throw new IllegalStateException("animation timeline is empty");
            return new AnimationTimeline(new LinkedHashMap<>(phases), cursor);
        }
    }
}
