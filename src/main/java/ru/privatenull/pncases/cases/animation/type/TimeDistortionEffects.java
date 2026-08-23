package ru.privatenull.pncases.cases.animation.type;

import static ru.privatenull.pncases.cases.animation.effect.AnimationEffects.Kind.DUST;
import static ru.privatenull.pncases.cases.animation.effect.AnimationEffects.particle;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;
import ru.privatenull.pncases.cases.animation.effect.AnimationParticles;

/** Controlled particle geometry for the Timekeeper cut-scene. */
final class TimeDistortionEffects {

    private static final Particle.DustOptions GOLD = dust(255, 205, 91, 0.82f);
    private static final Particle.DustOptions CYAN = dust(77, 221, 255, 0.82f);
    private static final Particle.DustOptions VIOLET = dust(174, 91, 255, 0.88f);
    private static final Particle.DustOptions WHITE = dust(236, 250, 255, 0.70f);

    private TimeDistortionEffects() {
    }

    static Vector horizontalRight(Location center, Location viewer) {
        Vector forward = viewer.toVector().subtract(center.toVector()).setY(0.0);
        if (forward.lengthSquared() < 0.001) forward.setX(0.0).setZ(1.0);
        forward.normalize();
        return new Vector(-forward.getZ(), 0.0, forward.getX()).normalize();
    }

    static void arrivalGate(World world, Location base, int tick, double progress) {
        double radius = 0.35 + ease(progress) * 1.10;
        for (int i = 0; i < 12; i++) {
            double angle = tick * 0.09 + i * Math.PI / 6.0;
            Location point = base.clone().add(Math.cos(angle) * radius, 0.05, Math.sin(angle) * radius);
            point(world, point, i % 2 == 0 ? GOLD : VIOLET);
        }
        if (tick % 4 == 0) world.spawnParticle(Particle.REVERSE_PORTAL,
                base.clone().add(0.0, 0.85, 0.0), 3, 0.28, 0.68, 0.28, 0.05);
    }

    static void dial(World world, Location center, Vector right, double rotation, double radius, int tick) {
        for (int i = 0; i < 24; i++) {
            double angle = rotation + i * Math.PI / 12.0;
            Location mark = clockPoint(center, right, angle, radius);
            point(world, mark, i % 6 == 0 ? WHITE : i % 2 == 0 ? GOLD : CYAN);
        }
        double minute = rotation * 2.35 - Math.PI / 2.0;
        double hour = rotation * 0.46 - Math.PI / 2.0;
        line(world, center, clockPoint(center, right, minute, radius * 0.68), CYAN, 8);
        line(world, center, clockPoint(center, right, hour, radius * 0.44), GOLD, 6);
        if (tick % 5 == 0) world.spawnParticle(Particle.ELECTRIC_SPARK,
                center, 2, 0.08, 0.08, 0.08, 0.015);
    }

    static void rewindTrail(World world, Location block, int index, int tick) {
        point(world, block, index % 2 == 0 ? GOLD : CYAN);
        if ((tick + index) % 4 == 0) {
            world.spawnParticle(Particle.REVERSE_PORTAL, block, 1, 0.05, 0.05, 0.05, 0.025);
        }
    }

    static void paradoxBeam(World world, Location past, Location future, Location center, int tick) {
        Location pastHand = past.clone().add(0.0, 1.28, 0.0);
        Location futureHand = future.clone().add(0.0, 1.28, 0.0);
        line(world, pastHand, center, GOLD, 7);
        line(world, futureHand, center, CYAN, 7);
        double pulse = 0.24 + Math.sin(tick * 0.42) * 0.07;
        for (int i = 0; i < 8; i++) {
            double angle = -tick * 0.13 + i * Math.PI / 4.0;
            Location point = center.clone().add(Math.cos(angle) * pulse,
                    Math.sin(angle * 2.0) * 0.08, Math.sin(angle) * pulse);
            point(world, point, i % 2 == 0 ? VIOLET : WHITE);
        }
    }

    static void temporalSkip(World world, Location from, Location to) {
        world.spawnParticle(Particle.FLASH, from.clone().add(0.0, 1.0, 0.0), 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticle(Particle.FLASH, to.clone().add(0.0, 1.0, 0.0), 1, 0.0, 0.0, 0.0, 0.0);
        line(world, from.clone().add(0.0, 1.0, 0.0), to.clone().add(0.0, 1.0, 0.0), VIOLET, 9);
    }

    static void mergeField(World world, Location center, int tick, double progress) {
        double radius = 1.45 * (1.0 - ease(progress)) + 0.16;
        for (int i = 0; i < 10; i++) {
            double angle = tick * -0.18 + i * Math.PI / 5.0;
            Location point = center.clone().add(Math.cos(angle) * radius,
                    0.15 + Math.sin(angle * 2.0) * radius * 0.25, Math.sin(angle) * radius);
            point(world, point, i % 2 == 0 ? GOLD : CYAN);
        }
    }

    static Location lerp(Location from, Location to, double progress) {
        double p = Math.max(0.0, Math.min(1.0, progress));
        return from.clone().add(to.toVector().subtract(from.toVector()).multiply(p));
    }

    static double ease(double progress) {
        double p = Math.max(0.0, Math.min(1.0, progress));
        return p * p * (3.0 - 2.0 * p);
    }

    private static Location clockPoint(Location center, Vector right, double angle, double radius) {
        return center.clone().add(right.clone().multiply(Math.cos(angle) * radius))
                .add(0.0, Math.sin(angle) * radius, 0.0);
    }

    private static void line(World world, Location from, Location to,
                             Particle.DustOptions color, int segments) {
        Vector step = to.toVector().subtract(from.toVector()).multiply(1.0 / Math.max(1, segments));
        Location cursor = from.clone();
        for (int i = 0; i <= segments; i++) {
            point(world, cursor, color);
            cursor.add(step);
        }
    }

    private static void point(World world, Location location, Particle.DustOptions color) {
        AnimationParticles.dust(world, particle(DUST), location, 1,
                0.0, 0.0, 0.0, 0.0, color);
    }

    private static Particle.DustOptions dust(int red, int green, int blue, float size) {
        return new Particle.DustOptions(Color.fromRGB(red, green, blue), size);
    }
}
