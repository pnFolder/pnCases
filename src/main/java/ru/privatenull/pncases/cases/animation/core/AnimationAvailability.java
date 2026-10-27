package ru.privatenull.pncases.cases.animation.core;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import ru.privatenull.pncases.cases.model.AnimationType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides whether an animation can run where a case stands. Animations that only use display entities and
 * particles always can. Animations with real mobs need a world that is not on Peaceful and a point where
 * mobs may appear; the second is tested by spawning one mob there and removing it at once, so a region
 * rule of WorldGuard or of any other plugin is honoured without knowing the plugin.
 */
public final class AnimationAvailability {

    /** The answer for one animation at one place. {@code reason} explains a refusal and is empty otherwise. */
    public record Availability(boolean available, String reason) {
        public static final Availability OK = new Availability(true, "");

        static Availability blocked(String reason) {
            return new Availability(false, reason);
        }
    }

    /** What happened when a mob was spawned at the case. */
    public enum Probe { SPAWNED, CANCELLED, FAILED }

    private record Entry(long expiresAt, Availability value) { }

    private static final long CACHE_MILLIS = 60_000L;

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    /**
     * Checks {@code type} for the point {@code caseBlock}. Call from the main thread; from another thread the
     * mob test cannot run and the animation is reported as available.
     */
    public Availability check(AnimationType type, Location caseBlock) {
        if (!AnimationRequirements.needsMobs(type)) return Availability.OK;
        World world = caseBlock == null ? null : caseBlock.getWorld();
        if (world == null) return Availability.OK;

        String key = world.getUID() + ":" + caseBlock.getBlockX() + ":" + caseBlock.getBlockY()
                + ":" + caseBlock.getBlockZ() + ":" + type;
        long now = System.currentTimeMillis();
        Entry cached = cache.get(key);
        if (cached != null && cached.expiresAt() > now) return cached.value();

        boolean peaceful = world.getDifficulty() == Difficulty.PEACEFUL;
        List<Probe> probes = new ArrayList<>();
        if (!peaceful && Bukkit.isPrimaryThread()) {
            Location above = caseBlock.clone().add(0.5, 1.0, 0.5);
            for (Class<? extends Mob> mob : AnimationRequirements.mobTypes(type)) {
                probes.add(AnimationMobSpawner.probe(world, above, mob));
            }
        }
        Availability result = decide(peaceful, probes);
        cache.put(key, new Entry(now + CACHE_MILLIS, result));
        return result;
    }

    /** Forgets earlier answers, for example after the case moved or the difficulty was changed. */
    public void clear() {
        cache.clear();
    }

    /** Pure decision from what was observed. */
    static Availability decide(boolean peaceful, List<Probe> probes) {
        if (peaceful) {
            return Availability.blocked("сложность мира «Мирная»: мобы там не появятся");
        }
        if (probes.contains(Probe.CANCELLED)) {
            return Availability.blocked("спавн мобов запрещён в этой точке (регион или другой плагин)");
        }
        if (probes.contains(Probe.FAILED)) {
            return Availability.blocked("не удалось создать моба в этой точке");
        }
        return Availability.OK;
    }
}
