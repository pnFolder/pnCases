package ru.privatenull.pncases.cases.animation.effect;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/** Type-safe particle entry points used by animation scenarios. */
public final class AnimationParticles {

    private AnimationParticles() {
    }

    public static void plain(World world, Particle particle, Location location, int count,
                             double offsetX, double offsetY, double offsetZ, double extra) {
        if (world == null || particle == null || location == null) return;
        world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
    }

    public static void dust(World world, Particle particle, Location location, int count,
                            double offsetX, double offsetY, double offsetZ, double extra,
                            Particle.DustOptions options) {
        if (world == null || particle == null || location == null || options == null) return;
        world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, options);
    }

    public static void block(World world, Particle particle, Location location, int count,
                             double offsetX, double offsetY, double offsetZ, double extra,
                             BlockData data) {
        if (world == null || particle == null || location == null || data == null) return;
        world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, data);
    }
}
