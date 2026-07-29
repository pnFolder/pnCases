package ru.privatenull.cases.animation.main;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.cases.animation.AnimationRun;
import ru.privatenull.cases.animation.CaseAnimation;
import ru.privatenull.cases.animation.RewardLabel;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.Reward;

import static ru.privatenull.cases.animation.CaseAnimation.playSound;

/**
 * A particle-only dragon that circles the case, dives and breathes into it.
 * No mob is spawned: the shape is visible to every nearby player while never
 * creating AI, collision, damage or region-protection side effects.
 */
public final class DragonAnimation extends CaseAnimation {

    private static final int CIRCLE_END = 100;
    private static final int DESCEND_END = 145;
    private static final int BREATH_END = 190;
    private static final int REVEAL_END = 245;
    private static final Particle.DustOptions BODY =
            new Particle.DustOptions(Color.fromRGB(116, 58, 214), 1.15f);
    private static final Particle.DustOptions EDGE =
            new Particle.DustOptions(Color.fromRGB(221, 102, 255), 0.82f);

    public DragonAnimation(PnCasesPlugin plugin) {
        super(plugin);
    }

    @Override
    protected void start(AnimationRun run) {
        Player player = run.player();
        CaseDefinition definition = run.definition();
        Reward reward = run.reward();
        Location rawBase = run.base();
        Runnable completion = run.completion();
        World world = rawBase.getWorld();
        if (world == null) {
            completion.run();
            return;
        }

        Location center = rawBase.getBlock().getLocation().add(0.5, 0.0, 0.5);
        Vector audience = horizontal(player.getLocation().toVector().subtract(center.toVector()));
        Location attackPosition = center.clone()
                .subtract(audience.clone().multiply(4.1))
                .add(0.0, 3.05, 0.0);
        double circleEndAngle = CIRCLE_END * 0.055;
        Location circleEndPosition = orbitPosition(center, circleEndAngle);
        Vector circleEndForward = orbitForward(circleEndAngle);

        ItemStack rewardVisual = resolveRewardVisual(reward, definition);
        ItemDisplay rewardDisplay = (ItemDisplay) world.spawnEntity(
                center.clone().add(0.0, -2.5, 0.0), EntityType.ITEM_DISPLAY);
        rewardDisplay.setItemStack(rewardVisual);
        rewardDisplay.setBillboard(Display.Billboard.VERTICAL);
        scale(rewardDisplay, 0.01f);
        run.track(rewardDisplay);
        RewardLabel rewardLabel = RewardLabel.create(run, center,
                resolveRewardName(reward, rewardVisual));

        BukkitTask task = new BukkitRunnable() {
            private int tick;
            private boolean finished;

            @Override
            public void run() {
                if (finished) {
                    cancel();
                    return;
                }
                if (!player.isOnline()) {
                    finish();
                    return;
                }

                tick++;
                if (tick == 1) {
                    playSound(world, center, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.20f, 0.72f);
                    world.spawnParticle(Particle.PORTAL, center.clone().add(0.0, 2.2, 0.0),
                            34, 2.4, 1.2, 2.4, 0.10);
                }

                if (tick <= CIRCLE_END) {
                    circle();
                } else if (tick <= DESCEND_END) {
                    descend();
                } else if (tick <= BREATH_END) {
                    breathe();
                } else if (tick <= REVEAL_END) {
                    reveal();
                } else {
                    finalBurst();
                    finish();
                }
            }

            private void circle() {
                double angle = tick * 0.055;
                Location dragon = orbitPosition(center, angle);
                Vector forward = orbitForward(angle);
                if (tick % 2 == 0) drawDragon(world, dragon, forward, tick, 0.0);
                trail(world, local(dragon, forward, 0.0, 0.05, -2.7), 3);
                if (tick % 24 == 0) {
                    playSound(world, dragon, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.16f, 0.82f);
                }
            }

            private void descend() {
                double progress = ease((tick - CIRCLE_END) / (double) (DESCEND_END - CIRCLE_END));
                Location dragon = lerp(circleEndPosition, attackPosition, progress);
                Vector targetForward = horizontal(center.toVector().subtract(attackPosition.toVector()));
                Vector forward = normalizedLerp(circleEndForward, targetForward, progress);
                if (tick % 2 == 0) drawDragon(world, dragon, forward, tick, 0.0);
                trail(world, local(dragon, forward, 0.0, 0.05, -2.7), 4);
                if (tick == CIRCLE_END + 1) {
                    playSound(world, center, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.22f, 0.88f);
                }
                if (tick % 16 == 0) {
                    playSound(world, dragon, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.14f, 0.95f);
                }
            }

            private void breathe() {
                Vector forward = horizontal(center.toVector().subtract(attackPosition.toVector()));
                Location dragon = attackPosition.clone().add(0.0, Math.sin(tick * 0.16) * 0.08, 0.0);
                if (tick % 2 == 0) drawDragon(world, dragon, forward, tick, 0.0);
                Location mouth = local(dragon, forward, 0.0, 0.20, 1.62);
                Location impact = center.clone().add(0.0, 0.62, 0.0);
                drawBreath(world, mouth, impact, tick);
                if (tick % 2 == 0) drawImpactRune(world, center, tick);

                if (tick == DESCEND_END + 1) {
                    run.hide(rawBase.getBlock(), Material.AIR);
                    playSound(world, center, Sound.ENTITY_ENDER_DRAGON_SHOOT, 0.22f, 0.72f);
                }
                if (tick % 12 == 0) {
                    playSound(world, center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.12f, 1.32f);
                }
            }

            private void reveal() {
                double progress = ease((tick - BREATH_END) / (double) (REVEAL_END - BREATH_END));
                Vector forward = horizontal(center.toVector().subtract(attackPosition.toVector()));
                Location dragon = attackPosition.clone()
                        .subtract(forward.clone().multiply(progress * 2.0))
                        .add(0.0, progress * 2.6, 0.0);
                if (tick % 2 == 0) drawDragon(world, dragon, forward, tick, progress);

                double rise = 0.58 + progress * 1.24;
                Location rewardLocation = center.clone().add(0.0, rise, 0.0);
                rewardDisplay.teleport(rewardLocation);
                rewardDisplay.setRotation((tick * 7.0f) % 360.0f, 0.0f);
                scale(rewardDisplay, (float) (0.12 + progress * 0.88));
                if (rewardLabel != null) rewardLabel.showAbove(rewardLocation, 0.88);

                if (tick % 3 == 0) {
                    world.spawnParticle(Particle.DRAGON_BREATH, rewardLocation,
                            2, 0.22, 0.18, 0.22, 0.012);
                    world.spawnParticle(Particle.END_ROD, rewardLocation,
                            1, 0.20, 0.18, 0.20, 0.01);
                }
                if (tick == BREATH_END + 1) {
                    playSound(world, center, Sound.BLOCK_END_PORTAL_SPAWN, 0.18f, 1.55f);
                }
                if (tick == REVEAL_END - 12) {
                    playSound(world, center, Sound.ENTITY_PLAYER_LEVELUP, 0.20f, 1.55f);
                }
            }

            private void finalBurst() {
                Location rewardLocation = rewardDisplay.getLocation();
                world.spawnParticle(Particle.DRAGON_BREATH, rewardLocation,
                        10, 0.42, 0.42, 0.42, 0.045);
                world.spawnParticle(Particle.END_ROD, rewardLocation,
                        7, 0.35, 0.35, 0.35, 0.03);
                world.spawnParticle(Particle.FIREWORK, rewardLocation,
                        4, 0.28, 0.28, 0.28, 0.025);
                playSound(world, center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.22f, 1.18f);
            }

            private void finish() {
                if (finished) return;
                finished = true;
                completion.run();
            }
        }.runTaskTimer(plugin, 0L, 1L);
        run.track(task);
    }

    private static void drawDragon(World world, Location origin, Vector forward,
                                   int tick, double dissolve) {
        Vector right = right(forward);
        int stride = dissolve > 0.72 ? 4 : dissolve > 0.36 ? 3 : 2;
        int index = 0;

        for (double z = -1.65; z <= 1.05; z += 0.24) {
            if (index++ % stride == 0) dust(world,
                    local(origin, right, forward, Math.sin(tick * 0.10 + z * 2.0) * 0.06,
                            0.12 + Math.cos(z * 2.2) * 0.08, z), BODY);
        }

        for (double z : new double[]{-0.75, -0.25, 0.28}) {
            for (int ring = 0; ring < 8; ring++) {
                if (index++ % stride != 0) continue;
                double angle = Math.PI * 2.0 * ring / 8.0;
                dust(world, local(origin, right, forward,
                        Math.cos(angle) * 0.34,
                        0.12 + Math.sin(angle) * 0.28, z), BODY);
            }
        }

        drawHead(world, origin, right, forward, tick, stride);
        drawTail(world, origin, right, forward, tick, stride);
        drawWings(world, origin, right, forward, tick, stride);
    }

    private static void drawHead(World world, Location origin, Vector right, Vector forward,
                                 int tick, int stride) {
        int index = 0;
        for (int ring = 0; ring < 10; ring++) {
            if (index++ % stride != 0) continue;
            double angle = Math.PI * 2.0 * ring / 10.0;
            dust(world, local(origin, right, forward,
                    Math.cos(angle) * 0.38,
                    0.22 + Math.sin(angle) * 0.31, 1.23), EDGE);
        }
        line(world, local(origin, right, forward, -0.25, 0.45, 1.18),
                local(origin, right, forward, -0.42, 0.78, 0.78), EDGE, 0.20 * stride);
        line(world, local(origin, right, forward, 0.25, 0.45, 1.18),
                local(origin, right, forward, 0.42, 0.78, 0.78), EDGE, 0.20 * stride);
        dust(world, local(origin, right, forward, -0.18, 0.29, 1.52), Particle.ELECTRIC_SPARK);
        dust(world, local(origin, right, forward, 0.18, 0.29, 1.52), Particle.ELECTRIC_SPARK);
        if (tick % 2 == 0) {
            dust(world, local(origin, right, forward, -0.13, 0.10, 1.62), Particle.SMOKE);
            dust(world, local(origin, right, forward, 0.13, 0.10, 1.62), Particle.SMOKE);
        }
    }

    private static void drawTail(World world, Location origin, Vector right, Vector forward,
                                 int tick, int stride) {
        for (int point = 0; point < 15; point++) {
            if (point % stride != 0) continue;
            double length = point * 0.25;
            double wave = Math.sin(tick * 0.10 - point * 0.42) * (0.07 + point * 0.025);
            dust(world, local(origin, right, forward, wave,
                    0.10 + Math.sin(point * 0.35) * 0.04, -1.55 - length), BODY);
        }
    }

    private static void drawWings(World world, Location origin, Vector right, Vector forward,
                                  int tick, int stride) {
        double flap = Math.sin(tick * 0.23) * 0.62;
        for (int side : new int[]{-1, 1}) {
            Location shoulder = local(origin, right, forward, side * 0.28, 0.25, -0.25);
            Location elbow = local(origin, right, forward, side * 1.55, 0.72 + flap, -0.48);
            Location tip = local(origin, right, forward, side * 3.05, 0.42 + flap * 1.45, -1.08);
            Location rear = local(origin, right, forward, side * 1.72, -0.02 + flap * 0.35, -1.48);
            double spacing = 0.20 * stride;
            line(world, shoulder, elbow, EDGE, spacing);
            line(world, elbow, tip, EDGE, spacing);
            line(world, tip, rear, BODY, spacing);
            line(world, rear, shoulder, BODY, spacing);
            line(world, shoulder, tip, BODY, spacing * 1.15);
            line(world, elbow, rear, BODY, spacing * 1.15);
        }
    }

    private static void drawBreath(World world, Location mouth, Location impact, int tick) {
        Vector ray = impact.toVector().subtract(mouth.toVector());
        for (int step = 0; step <= 16; step++) {
            double progress = step / 16.0;
            Location point = mouth.clone().add(ray.clone().multiply(progress));
            double spread = 0.015 + progress * 0.10;
            world.spawnParticle(Particle.DRAGON_BREATH, point, 2,
                    spread, spread, spread, 0.01);
            if ((step + tick) % 3 == 0) {
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, point, 1,
                        spread * 0.5, spread * 0.5, spread * 0.5, 0.005);
            }
        }
        world.spawnParticle(Particle.DRAGON_BREATH, impact,
                8, 0.30, 0.22, 0.30, 0.04);
    }

    private static void drawImpactRune(World world, Location center, int tick) {
        double rotation = tick * 0.08;
        for (int point = 0; point < 14; point++) {
            double angle = Math.PI * 2.0 * point / 14.0 + rotation;
            Location location = center.clone().add(
                    Math.cos(angle) * 1.05, 0.08, Math.sin(angle) * 1.05);
            dust(world, location, point % 2 == 0 ? EDGE : BODY);
        }
    }

    private static void trail(World world, Location location, int count) {
        world.spawnParticle(Particle.DRAGON_BREATH, location,
                count, 0.14, 0.14, 0.14, 0.02);
    }

    private static Location orbitPosition(Location center, double angle) {
        return center.clone().add(Math.cos(angle) * 4.4,
                3.65 + Math.sin(angle * 2.0) * 0.30,
                Math.sin(angle) * 4.4);
    }

    private static Vector orbitForward(double angle) {
        return new Vector(-Math.sin(angle), 0.04 * Math.cos(angle * 2.0), Math.cos(angle)).normalize();
    }

    private static Vector right(Vector forward) {
        Vector horizontal = horizontal(forward);
        return new Vector(-horizontal.getZ(), 0.0, horizontal.getX()).normalize();
    }

    private static Location local(Location origin, Vector forward,
                                  double x, double y, double z) {
        return local(origin, right(forward), forward, x, y, z);
    }

    private static Location local(Location origin, Vector right, Vector forward,
                                  double x, double y, double z) {
        return origin.clone()
                .add(right.clone().multiply(x))
                .add(0.0, y, 0.0)
                .add(forward.clone().multiply(z));
    }

    private static void line(World world, Location from, Location to,
                             Particle.DustOptions dust, double spacing) {
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        int points = Math.max(1, (int) Math.ceil(length / Math.max(0.16, spacing)));
        for (int point = 0; point <= points; point++) {
            dust(world, from.clone().add(delta.clone().multiply(point / (double) points)), dust);
        }
    }

    private static void dust(World world, Location location, Particle.DustOptions dust) {
        world.spawnParticle(Particle.DUST, location, 1, 0.0, 0.0, 0.0, 0.0, dust);
    }

    private static void dust(World world, Location location, Particle particle) {
        world.spawnParticle(particle, location, 1, 0.0, 0.0, 0.0, 0.0);
    }

    private static Vector horizontal(Vector vector) {
        Vector result = vector == null ? new Vector() : vector.clone();
        result.setY(0.0);
        if (result.lengthSquared() < 0.0001) return new Vector(0.0, 0.0, 1.0);
        return result.normalize();
    }

    private static Vector normalizedLerp(Vector from, Vector to, double progress) {
        Vector result = from.clone().multiply(1.0 - progress).add(to.clone().multiply(progress));
        return result.lengthSquared() < 0.0001 ? to.clone().normalize() : result.normalize();
    }

    private static Location lerp(Location from, Location to, double progress) {
        return from.clone().add(to.toVector().subtract(from.toVector()).multiply(progress));
    }

    private static double ease(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    private static void scale(ItemDisplay display, float value) {
        Transformation current = display.getTransformation();
        display.setTransformation(new Transformation(
                current.getTranslation(),
                current.getLeftRotation(),
                new Vector3f(value, value, value),
                current.getRightRotation()
        ));
        display.setInterpolationDuration(1);
    }
}
