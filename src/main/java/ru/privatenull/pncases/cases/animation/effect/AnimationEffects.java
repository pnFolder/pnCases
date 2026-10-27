package ru.privatenull.pncases.cases.animation.effect;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.pncases.support.compat.BukkitCompat.ParticleCompat;

/** Version-neutral visual primitives used only by pnCases animations. */
public final class AnimationEffects {

    private static final float MIN_VOLUME = 0.08f;
    private static final float MAX_VOLUME = 0.22f;

    public enum Kind {
        BLOCK("BLOCK", "BLOCK_CRACK", "BLOCK_DUST"),
        DUST("DUST", "REDSTONE"),
        ENCHANT("ENCHANT", "ENCHANTMENT_TABLE"),
        EXPLOSION("EXPLOSION", "EXPLOSION_NORMAL"),
        FIREWORK("FIREWORK", "FIREWORKS_SPARK"),
        SLIME("ITEM_SLIME", "SLIME"),
        LARGE_SMOKE("LARGE_SMOKE", "SMOKE_LARGE"),
        POOF("POOF", "EXPLOSION_NORMAL"),
        SMOKE("SMOKE", "SMOKE_NORMAL"),
        TOTEM("TOTEM_OF_UNDYING", "TOTEM"),
        WITCH("WITCH", "SPELL_WITCH");

        private final String[] names;

        Kind(String... names) {
            this.names = names;
        }
    }

    private AnimationEffects() {
    }

    public static Particle particle(Kind kind) {
        return ParticleCompat.first(kind.names);
    }

    /**
     * Safe entry point for data-less particles. If a newer Paper release adds
     * mandatory particle data, pnCases supplies it here instead of crashing a
     * whole animation stage.
     */
    public static void spawn(World world, Particle particle, Location location, int count,
                             double offsetX, double offsetY, double offsetZ, double extra) {
        if (world == null || particle == null || location == null) return;
        Class<?> dataType = dataType(particle);
        if (dataType == null || dataType == Void.class) {
            trySpawn(world, particle, location, count, offsetX, offsetY, offsetZ, extra, null, false);
            return;
        }

        Object defaultData = defaultData(dataType);
        if (defaultData == null) {
            fallback(world, particle, location, count, offsetX, offsetY, offsetZ, extra);
            return;
        }
        trySpawn(world, particle, location, count, offsetX, offsetY, offsetZ, extra, defaultData, true);
    }

    /** Coordinate overload retained for geometric particle tracks. */
    public static void spawn(World world, Particle particle, double x, double y, double z, int count,
                             double offsetX, double offsetY, double offsetZ, double extra) {
        if (world == null) return;
        spawn(world, particle, new Location(world, x, y, z), count, offsetX, offsetY, offsetZ, extra);
    }

    /** Type-checked entry point for particles that carry Bukkit data. */
    public static <T> void spawn(World world, Particle particle, Location location, int count,
                                 double offsetX, double offsetY, double offsetZ, double extra, T data) {
        if (world == null || particle == null || location == null) return;
        Class<?> dataType = dataType(particle);
        if (dataType == null || dataType == Void.class) {
            trySpawn(world, particle, location, count, offsetX, offsetY, offsetZ, extra, null, false);
            return;
        }

        Object safeData = dataType.isInstance(data) ? data : defaultData(dataType);
        if (safeData == null) {
            fallback(world, particle, location, count, offsetX, offsetY, offsetZ, extra);
            return;
        }
        trySpawn(world, particle, location, count, offsetX, offsetY, offsetZ, extra, safeData, true);
    }

    public static void plain(World world, Particle particle, Location location, int count,
                             double offsetX, double offsetY, double offsetZ, double extra) {
        spawn(world, particle, location, count, offsetX, offsetY, offsetZ, extra);
    }

    public static void dust(World world, Particle particle, Location location, int count,
                            double offsetX, double offsetY, double offsetZ, double extra,
                            Particle.DustOptions options) {
        spawn(world, particle, location, count, offsetX, offsetY, offsetZ, extra, options);
    }

    public static void block(World world, Particle particle, Location location, int count,
                             double offsetX, double offsetY, double offsetZ, double extra,
                             BlockData data) {
        spawn(world, particle, location, count, offsetX, offsetY, offsetZ, extra, data);
    }

    public static void flash(World world, Location location, int count,
                             double offsetX, double offsetY, double offsetZ, double extra) {
        spawn(world, Particle.FLASH, location, count, offsetX, offsetY, offsetZ, extra);
    }

    public static void sound(World world, Location location, Sound sound, float volume, float pitch) {
        if (world == null || location == null || sound == null) return;
        world.playSound(location, sound, normalizeVolume(volume), normalizePitch(pitch));
    }

    public static void sound(Player player, Location location, Sound sound, float volume, float pitch) {
        if (player == null || sound == null) return;
        player.playSound(location == null ? player.getLocation() : location,
                sound, normalizeVolume(volume), normalizePitch(pitch));
    }

    private static Class<?> dataType(Particle particle) {
        return ParticleCompat.dataType(particle);
    }

    private static Object defaultData(Class<?> dataType) {
        return ParticleCompat.defaultData(dataType);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void trySpawn(World world, Particle particle, Location location, int count,
                                 double offsetX, double offsetY, double offsetZ, double extra,
                                 Object data, boolean hasData) {
        try {
            if (hasData) {
                world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, data);
            } else {
                world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
            }
        } catch (IllegalArgumentException | LinkageError incompatible) {
            fallback(world, particle, location, count, offsetX, offsetY, offsetZ, extra);
        }
    }

    private static void fallback(World world, Particle requested, Location location, int count,
                                 double offsetX, double offsetY, double offsetZ, double extra) {
        Particle fallback = ParticleCompat.first("END_ROD", "CLOUD");
        if (fallback == null || fallback == requested) return;
        ParticleCompat.spawn(world, fallback, location, count, offsetX, offsetY, offsetZ, extra);
    }

    private static float normalizeVolume(float volume) {
        return Math.max(MIN_VOLUME, Math.min(MAX_VOLUME, volume));
    }

    private static float normalizePitch(float pitch) {
        return Math.max(0.5f, Math.min(2.0f, pitch));
    }

}
