package ru.privatenull.pncases.cases.animation.type;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import ru.privatenull.pncases.cases.animation.effect.AnimationParticles;

import static ru.privatenull.pncases.cases.animation.effect.AnimationEffects.Kind.DUST;
import static ru.privatenull.pncases.cases.animation.effect.AnimationEffects.particle;

final class StormDuelEffects {

    static final Particle.DustOptions BLUE = new Particle.DustOptions(Color.fromRGB(74, 190, 255), 0.86f);
    static final Particle.DustOptions VIOLET = new Particle.DustOptions(Color.fromRGB(172, 104, 255), 0.86f);

    static void stormCanopy(World world, Location center, double progress, int tick) {
        double formed = ease(Math.min(1.0, progress * 2.1));
        double pulse = 0.08 * Math.sin(tick * 0.55);
        double outerRadius = 0.45 + formed * 1.10 + pulse;
        double innerRadius = 0.25 + formed * 0.62 - pulse * 0.35;

        for (int point = 0; point < 16; point++) {
            double angle = tick * 0.09 + point * Math.PI * 2.0 / 16.0;
            Location edge = center.clone().add(
                    Math.cos(angle) * outerRadius,
                    Math.sin(angle * 3.0 + tick * 0.16) * 0.10,
                    Math.sin(angle) * outerRadius
            );
            dustPoint(world, edge, point % 2 == 0 ? BLUE : VIOLET);
            if (point % 4 == 0) {
                world.spawnParticle(Particle.ELECTRIC_SPARK, edge,
                        1, 0.025, 0.025, 0.025, 0.018);
            }
        }
        for (int point = 0; point < 10; point++) {
            double angle = -tick * 0.13 + point * Math.PI * 2.0 / 10.0;
            Location edge = center.clone().add(
                    Math.cos(angle) * innerRadius,
                    Math.cos(angle * 2.0) * 0.07,
                    Math.sin(angle) * innerRadius
            );
            dustPoint(world, edge, point % 2 == 0 ? VIOLET : BLUE);
        }

        int falling = Math.max(1, (int) Math.ceil(formed * 4.0));
        world.spawnParticle(Particle.END_ROD, center.clone().subtract(0.0, 0.22, 0.0),
                falling, outerRadius * 0.38, 0.30, outerRadius * 0.38, 0.012);
    }

    static void summonSeal(
            Location center,
            Particle.DustOptions color,
            int rotationTick,
            double progress,
            boolean reverse
    ) {
        World world = center.getWorld();
        if (world == null) return;
        double formed = ease(Math.min(1.0, progress * 2.8));
        double radius = 0.22 + formed * 0.58;
        double direction = reverse ? -1.0 : 1.0;

        for (int point = 0; point < 14; point++) {
            double angle = direction * rotationTick * 0.12 + point * Math.PI * 2.0 / 14.0;
            Location edge = center.clone().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            dustPoint(world, edge, color);
            if (point % 7 == 0) {
                world.spawnParticle(Particle.ELECTRIC_SPARK, edge,
                        1, 0.01, 0.01, 0.01, 0.008);
            }
        }

        for (int spoke = 0; spoke < 4; spoke++) {
            double angle = direction * rotationTick * 0.07 + spoke * Math.PI / 2.0;
            Location inner = center.clone().add(Math.cos(angle) * radius * 0.18, 0.01, Math.sin(angle) * radius * 0.18);
            Location outer = center.clone().add(Math.cos(angle) * radius * 0.72, 0.01, Math.sin(angle) * radius * 0.72);
            dustLine(world, inner, outer, color, 0.15);
        }
    }

    static void summonColumn(
            Location base,
            Particle.DustOptions color,
            int tick,
            double progress,
            boolean reverse
    ) {
        World world = base.getWorld();
        if (world == null) return;
        double height = 0.35 + ease(Math.min(1.0, progress * 1.75)) * 2.20;
        double direction = reverse ? -1.0 : 1.0;
        for (int point = 0; point < 7; point++) {
            double vertical = height * point / 6.0;
            double angle = direction * (tick * 0.24 + point * 0.82);
            double radius = 0.30 - point * 0.022;
            Location spiral = base.clone().add(
                    Math.cos(angle) * radius,
                    vertical,
                    Math.sin(angle) * radius
            );
            dustPoint(world, spiral, color);
            if ((tick + point) % 5 == 0) {
                world.spawnParticle(Particle.ELECTRIC_SPARK, spiral,
                        1, 0.015, 0.025, 0.015, 0.012);
            }
        }
    }

    static void summonAura(
            Location base,
            Particle.DustOptions color,
            int tick,
            double intensity,
            boolean reverse
    ) {
        if (intensity <= 0.02) return;
        World world = base.getWorld();
        if (world == null) return;
        double direction = reverse ? -1.0 : 1.0;
        int points = Math.max(3, (int) Math.ceil(8.0 * intensity));
        for (int point = 0; point < points; point++) {
            double angle = direction * tick * 0.19 + point * Math.PI * 2.0 / points;
            double height = 0.18 + ((tick * 0.055 + point / (double) points) % 1.0) * 1.72;
            double radius = 0.32 + Math.sin(angle * 2.0) * 0.08;
            Location mote = base.clone().add(
                    Math.cos(angle) * radius,
                    height,
                    Math.sin(angle) * radius
            );
            dustPoint(world, mote, color);
        }
    }

    static void lightningArc(
            Location from,
            Location to,
            Particle.DustOptions color,
            int seed
    ) {
        World world = from.getWorld();
        if (world == null || to.getWorld() != world) return;
        Location previous = from;
        for (int point = 1; point <= 8; point++) {
            double progress = point / 8.0;
            Location next = lerp(from, to, progress);
            if (point < 8) {
                double envelope = Math.sin(progress * Math.PI);
                next.add(
                        Math.sin(seed * 1.71 + point * 2.37) * 0.13 * envelope,
                        Math.cos(seed * 1.29 + point * 1.83) * 0.09 * envelope,
                        Math.sin(seed * 1.43 - point * 2.11) * 0.13 * envelope
                );
            }
            dustLine(world, previous, next, color, 0.16);
            world.spawnParticle(Particle.ELECTRIC_SPARK, next,
                    1, 0.018, 0.018, 0.018, 0.018);
            previous = next;
        }
    }

    static void dustPoint(World world, Location location, Particle.DustOptions color) {
        AnimationParticles.dust(world, particle(DUST), location, 1,
                0.0, 0.0, 0.0, 0.0, color);
    }

    static void electricRing(World world, Location center, double radius, int rotationTick) {
        for (int point = 0; point < 12; point++) {
            double angle = rotationTick * 0.16 + point * Math.PI * 2.0 / 12.0;
            Location location = center.clone().add(
                    Math.cos(angle) * radius,
                    Math.sin(angle * 2.0) * 0.10,
                    Math.sin(angle) * radius
            );
            world.spawnParticle(Particle.ELECTRIC_SPARK, location, 1, 0.01, 0.01, 0.01, 0.01);
            if (point % 3 == 0) {
                world.spawnParticle(Particle.END_ROD, location, 1, 0.01, 0.01, 0.01, 0.005);
            }
            AnimationParticles.dust(world, particle(DUST), location, 1, 0.0, 0.0, 0.0, 0.0,
                    point % 2 == 0 ? BLUE : VIOLET);
        }
    }

    static void dustBurst(World world, Location location, Particle.DustOptions color, int amount) {
        AnimationParticles.dust(world, particle(DUST), location, amount, 0.34, 0.34, 0.34, 0.02, color);
    }

    static Location lerp(Location from, Location to, double progress) {
        double clamped = Math.max(0.0, Math.min(1.0, progress));
        return from.clone().add(to.toVector().subtract(from.toVector()).multiply(clamped));
    }

    static Vector horizontal(Vector vector) {
        Vector result = vector == null ? new Vector() : vector.clone();
        result.setY(0.0);
        if (result.lengthSquared() < 0.0001) return new Vector(0.0, 0.0, 1.0);
        return result.normalize();
    }

    static EulerAngle degrees(double x, double y, double z) {
        return new EulerAngle(Math.toRadians(x), Math.toRadians(y), Math.toRadians(z));
    }

    static double ease(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    static double slashProgress(double progress) {
        double clamped = Math.max(0.0, Math.min(1.0, progress));
        if (clamped <= 0.68) return ease(clamped / 0.68);
        return 1.0 - ease((clamped - 0.68) / 0.32) * 0.55;
    }

    static void dustLine(
            World world,
            Location from,
            Location to,
            Particle.DustOptions color,
            double spacing
    ) {
        Vector delta = to.toVector().subtract(from.toVector());
        int points = Math.max(1, (int) Math.ceil(delta.length() / Math.max(0.08, spacing)));
        for (int point = 0; point <= points; point++) {
            Location location = from.clone().add(delta.clone().multiply(point / (double) points));
            AnimationParticles.dust(world, particle(DUST), location, 1,
                    0.0, 0.0, 0.0, 0.0, color);
        }
    }
}
