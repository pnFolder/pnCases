package ru.privatenull.pncases.cases.animation.core;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Marks the synchronous creature-spawn event produced for an animation actor.
 *
 * <p>The marker exists only while Bukkit handles the exact {@link World#spawn}
 * call. It lets the runtime listener restore a spawn cancelled by WorldGuard
 * without changing the rules for regular mobs or creatures from other plugins.</p>
 */
public final class AnimationMobSpawner {

    private static final double LOCATION_TOLERANCE_SQUARED = 0.0001;
    private static final ThreadLocal<Predicate<CreatureSpawnEvent>> ACTIVE_SPAWN = new ThreadLocal<>();

    private AnimationMobSpawner() {
    }

    public static <T extends Mob> T spawn(World world, Location location, Class<T> type) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(type, "type");

        Predicate<CreatureSpawnEvent> previous = ACTIVE_SPAWN.get();
        ACTIVE_SPAWN.set(event -> type.isInstance(event.getEntity())
                && world.equals(event.getLocation().getWorld())
                && event.getLocation().distanceSquared(location) <= LOCATION_TOLERANCE_SQUARED);
        try {
            return world.spawn(location, type);
        } finally {
            if (previous == null) {
                ACTIVE_SPAWN.remove();
            } else {
                ACTIVE_SPAWN.set(previous);
            }
        }
    }

    /**
     * Spawns one mob exactly like a real animation would and removes it in the same tick, to learn whether the
     * place allows it. Main thread only.
     */
    public static AnimationAvailability.Probe probe(World world, Location location, Class<? extends Mob> type) {
        try {
            return probeOf(world, location, type);
        } catch (IllegalArgumentException cancelled) {
            return AnimationAvailability.Probe.CANCELLED;
        } catch (RuntimeException failure) {
            return AnimationAvailability.Probe.FAILED;
        }
    }

    private static <T extends Mob> AnimationAvailability.Probe probeOf(World world, Location location, Class<T> type) {
        T mob = spawn(world, location, type);
        if (mob == null || !mob.isValid()) return AnimationAvailability.Probe.CANCELLED;
        mob.remove();
        return AnimationAvailability.Probe.SPAWNED;
    }

    public static boolean owns(CreatureSpawnEvent event) {
        Predicate<CreatureSpawnEvent> active = ACTIVE_SPAWN.get();
        return active != null && active.test(event);
    }
}
