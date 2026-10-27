package ru.privatenull.pncases.cases.animation.type;

import static ru.privatenull.pncases.cases.animation.effect.AnimationEffects.Kind.*;
import static ru.privatenull.pncases.cases.animation.effect.AnimationEffects.particle;
import static ru.privatenull.pncases.cases.animation.effect.AnimationEffects.spawn;

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
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;
import ru.privatenull.pncases.PnCasesPlugin;
import ru.privatenull.pncases.cases.animation.core.AnimationFrame;
import ru.privatenull.pncases.cases.animation.core.AnimationSession;
import ru.privatenull.pncases.cases.animation.effect.RewardLabel;
import ru.privatenull.pncases.cases.animation.core.AnimationTimeline;
import ru.privatenull.pncases.cases.animation.core.TimelineCaseAnimation;
import ru.privatenull.pncases.cases.animation.core.TimelineTask;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pncases.support.compat.BukkitCompat.MaterialCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * A hungry chest mimic assembled from display entities. The body has independent
 * lower, lid, mouth and eye parts, so the creature can squash, jump, chew and
 * spit out the final reward without spawning AI or a collidable mob.
 */
public final class MimicAnimation extends TimelineCaseAnimation {

    public MimicAnimation(PnCasesPlugin plugin) {
        super(plugin);
    }

    @Override
    protected void start(AnimationSession run) {
        Player player = run.player();
        CaseDefinition definition = run.definition();
        Reward reward = run.reward();
        Location rawBase = run.base();
        World world = run.world();
        if (player == null || rawBase == null || world == null) {
            run.complete();
            return;
        }

        AnimationTimeline timeline = AnimationTimeline.builder()
                .phase("wake", 38).phase("jump", 44).phase("feast", 76)
                .phase("cough", 32).phase("reveal", 58).build();

        Location center = rawBase.getBlock().getLocation().add(0.5, 0.0, 0.5);
        Vector forward = horizontal(player.getLocation().toVector().subtract(center.toVector()));
        Vector right = right(forward);
        float yaw = (float) Math.toDegrees(Math.atan2(-forward.getX(), forward.getZ()));

        MimicRig mimic = MimicRig.create(run, world, center, right, forward, yaw);
        ItemStack rewardVisual = resolveRewardVisual(reward, definition);
        ItemDisplay rewardDisplay = item(run, world, center.clone().add(0.0, -3.0, 0.0), rewardVisual, 0.01f);
        RewardLabel rewardLabel = RewardLabel.create(run, center, resolveRewardName(reward, rewardVisual));

        List<ItemDisplay> bait = new ArrayList<>();
        for (ItemStack preview : previewItems(definition, rewardVisual)) {
            bait.add(item(run, world, center.clone().add(0.0, -3.0, 0.0), preview, 0.01f));
        }

        startTimeline(run, new TimelineTask(run, "mimic-main", timeline) {
            private int tick;

            @Override
            protected void render(AnimationFrame frame) {
                tick = frame.tick();
                if (tick == 1) {
                    run.hide(rawBase.getBlock(), Material.AIR);
                    playSound(world, center, Sound.BLOCK_CHEST_OPEN, 0.16f, 0.58f);
                }

                if (timeline.in("wake", tick)) {
                    wake();
                } else if (timeline.in("jump", tick)) {
                    jump();
                } else if (timeline.in("feast", tick)) {
                    feast();
                } else if (timeline.in("cough", tick)) {
                    cough();
                } else if (timeline.in("reveal", tick)) {
                    reveal();
                }
            }

            @Override
            protected void onComplete() {
                finalBurst();
            }

            private void wake() {
                double progress = timeline.progress("wake", tick);
                double tension = ease(progress);
                double tremble = Math.sin(tick * 1.45) * 0.035 * tension;
                Location body = center.clone().add(tremble, 0.66 + Math.abs(Math.sin(tick * 0.42)) * 0.025, -tremble);
                float squash = (float) (1.0 - Math.abs(Math.sin(tick * 0.55)) * 0.12 * tension);
                float stretch = (float) (Math.sin(tick * 0.55) * 0.08 * tension);
                float mouth = progress < 0.55 ? 0.0f
                        : (float) ((progress - 0.55) / 0.45 * (0.20 + Math.abs(Math.sin(tick * 0.7)) * 0.18));
                mimic.update(body, squash, stretch, mouth, (float) Math.max(0.0, (progress - 0.38) / 0.40));
                mimic.drawFace(world, mouth, false);

                if (tick == 18) {
                    playSound(world, center, Sound.BLOCK_CHEST_CLOSE, 0.12f, 0.45f);
                }
                if (tick == 30) {
                    playSound(world, center, Sound.ENTITY_RAVAGER_ROAR, 0.18f, 1.45f);
                    spawn(world, particle(POOF), body, 5, 0.42, 0.24, 0.42, 0.02);
                }
            }

            private void jump() {
                double progress = timeline.progress("jump", tick);
                double hops = Math.abs(Math.sin(progress * Math.PI * 4.0));
                double side = Math.sin(progress * Math.PI * 2.0) * 0.48;
                Location body = center.clone()
                        .add(right.clone().multiply(side))
                        .add(0.0, 0.66 + hops * (0.68 - progress * 0.12), 0.0);
                float squash = (float) (0.88 + hops * 0.24);
                float mouth = (float) (0.22 + Math.abs(Math.sin(progress * Math.PI * 4.0)) * 0.48);
                mimic.update(body, squash, (float) ((1.0 - hops) * 0.13), mouth, 1.0f);
                mimic.drawFace(world, mouth, false);
                updateOrbit(body, 1.55, tick * 0.085, 0.46f);

                if ((tick - timeline.end("wake")) % 11 == 1) {
                    playSound(world, body, Sound.BLOCK_WOOD_PLACE, 0.14f, 0.72f + (float) progress * 0.35f);
                    spawn(world, Particle.CLOUD, body.clone().subtract(0.0, 0.48, 0.0),
                            3, 0.28, 0.05, 0.28, 0.01);
                }
            }

            private void feast() {
                double progress = timeline.progress("feast", tick);
                double bob = Math.sin(tick * 0.22) * 0.06;
                Location body = center.clone().add(0.0, 0.70 + bob, 0.0);
                float chomp = (float) (0.46 + Math.abs(Math.sin(tick * 0.43)) * 0.42);
                mimic.update(body, 0.94f, (float) (Math.sin(tick * 0.43) * 0.08), chomp, 1.0f);
                mimic.drawFace(world, chomp, true);

                for (int index = 0; index < bait.size(); index++) {
                    int swallowStart = timeline.end("jump") + 4 + index * 14;
                    int swallowEnd = swallowStart + 12;
                    ItemDisplay display = bait.get(index);
                    Location orbit = orbit(body, index, bait.size(), tick * 0.09, 1.55);
                    if (tick < swallowStart) {
                        display.teleport(orbit);
                        display.setRotation((tick * 8.0f + index * 55.0f) % 360.0f, 0.0f);
                        setItemScale(display, 0.46f);
                    } else if (tick <= swallowEnd) {
                        double pull = ease((tick - swallowStart) / (double) (swallowEnd - swallowStart));
                        Location target = mimic.mouth().clone().add(forward.clone().multiply(0.18));
                        Location position = lerp(orbit, target, pull);
                        display.teleport(position);
                        display.setRotation((tick * 18.0f) % 360.0f, 0.0f);
                        setItemScale(display, (float) (0.46 * (1.0 - pull)));
                        mimic.drawTongue(world, position);
                    } else {
                        setItemScale(display, 0.0f);
                    }

                    if (tick == swallowEnd) {
                        playSound(world, mimic.mouth(), Sound.ENTITY_GENERIC_EAT, 0.15f, 0.72f + index * 0.11f);
                        spawn(world, particle(POOF), mimic.mouth(), 3, 0.16, 0.12, 0.16, 0.01);
                    }
                }

                if (tick % 18 == 0) {
                    playSound(world, body, Sound.BLOCK_CHEST_CLOSE, 0.10f, 0.72f);
                }
            }

            private void cough() {
                double progress = timeline.progress("cough", tick);
                double swell = Math.sin(progress * Math.PI);
                double hop = Math.abs(Math.sin(progress * Math.PI * 3.0)) * 0.24;
                Location body = center.clone().add(0.0, 0.68 + hop, 0.0);
                float squash = (float) (0.88 + swell * 0.34);
                float stretch = (float) (swell * 0.30);
                float mouth = (float) (0.18 + Math.abs(Math.sin(progress * Math.PI * 3.0)) * 0.34);
                mimic.update(body, squash, stretch, mouth, 1.0f);
                mimic.drawFace(world, mouth, false);

                if (tick % 7 == 0) {
                    playSound(world, body, Sound.ENTITY_PLAYER_BURP, 0.12f, 0.52f + (float) progress * 0.30f);
                    spawn(world, particle(SMOKE), mimic.mouth(), 2, 0.12, 0.10, 0.12, 0.01);
                }
            }

            private void reveal() {
                double progress = timeline.progress("reveal", tick);
                double launch = Math.min(1.0, progress / 0.48);
                float mimicScale = (float) Math.max(0.05, 1.0 - Math.max(0.0, progress - 0.36) / 0.64);
                Location body = center.clone().add(0.0, 0.68 - Math.max(0.0, progress - 0.55) * 0.70, 0.0);
                mimic.update(body, 0.92f, 0.08f, (float) (1.0 - progress * 0.55), mimicScale);
                mimic.drawFace(world, (float) (1.0 - progress * 0.55), false);

                Location launchEnd = center.clone().add(forward.clone().multiply(0.72)).add(0.0, 2.75, 0.0);
                Location rewardLocation;
                if (progress < 0.48) {
                    Location from = mimic.mouth().clone().add(forward.clone().multiply(0.22));
                    rewardLocation = lerp(from, launchEnd, ease(launch));
                } else {
                    double settle = ease((progress - 0.48) / 0.52);
                    Location hover = center.clone().add(0.0, 2.12 + Math.sin(tick * 0.16) * 0.06, 0.0);
                    rewardLocation = lerp(launchEnd, hover, settle);
                }

                rewardDisplay.teleport(rewardLocation);
                rewardDisplay.setRotation((tick * 10.0f) % 360.0f, 0.0f);
                setItemScale(rewardDisplay, (float) Math.min(1.0, 0.08 + progress * 1.45));
                if (progress > 0.42 && rewardLabel != null) {
                    rewardLabel.showAbove(rewardLocation, 0.88);
                }

                if (tick == timeline.start("reveal")) {
                    run.protectReward(center.clone().add(0.0, 1.35, 0.0));
                    playSound(world, center, Sound.ENTITY_FOX_SPIT, 0.20f, 0.70f);
                    playSound(world, center, Sound.BLOCK_CHEST_OPEN, 0.16f, 1.55f);
                    spawn(world, particle(POOF), mimic.mouth(), 8, 0.28, 0.22, 0.28, 0.03);
                }
                if (tick == timeline.end("reveal") - 12) {
                    playSound(world, rewardLocation, Sound.ENTITY_PLAYER_LEVELUP, 0.20f, 1.55f);
                }
            }

            private void updateOrbit(Location body, double radius, double rotation, float scale) {
                for (int index = 0; index < bait.size(); index++) {
                    ItemDisplay display = bait.get(index);
                    Location position = orbit(body, index, bait.size(), rotation, radius);
                    display.teleport(position);
                    display.setRotation((tick * 7.0f + index * 45.0f) % 360.0f, 0.0f);
                    setItemScale(display, scale);
                }
            }

            private void finalBurst() {
                Location rewardLocation = rewardDisplay.getLocation();
                spawn(world, Particle.END_ROD, rewardLocation, 7, 0.30, 0.30, 0.30, 0.02);
                spawn(world, particle(FIREWORK), rewardLocation, 4, 0.25, 0.25, 0.25, 0.02);
                playSound(world, rewardLocation, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.22f, 1.20f);
            }

        });
    }

    private static List<ItemStack> previewItems(CaseDefinition definition, ItemStack rewardVisual) {
        List<ItemStack> result = new ArrayList<>();
        if (definition != null && definition.animationItems() != null) {
            for (ItemStack source : definition.animationItems()) {
                if (source == null || source.getType().isAir()) continue;
                ItemStack item = source.clone();
                item.setAmount(1);
                result.add(item);
                if (result.size() == 5) break;
            }
        }
        Material[] fallback = {
                Material.EMERALD,
                Material.DIAMOND,
                Material.GOLD_INGOT,
                MaterialCompat.first("AMETHYST_SHARD", "NETHER_STAR"),
                Material.NETHER_STAR
        };
        for (Material material : fallback) {
            if (result.size() >= 5) break;
            result.add(new ItemStack(material));
        }
        if (result.isEmpty() && rewardVisual != null) result.add(rewardVisual.clone());
        return result;
    }

    private static Location orbit(
            Location center,
            int index,
            int count,
            double rotation,
            double radius
    ) {
        double angle = rotation + index * Math.PI * 2.0 / Math.max(1, count);
        return center.clone().add(
                Math.cos(angle) * radius,
                0.92 + Math.sin(angle * 1.7) * 0.22,
                Math.sin(angle) * radius
        );
    }

    private static ItemDisplay item(
            AnimationSession run,
            World world,
            Location location,
            ItemStack stack,
            float scale
    ) {
        ItemDisplay display = (ItemDisplay) world.spawnEntity(location, EntityType.ITEM_DISPLAY);
        display.setItemStack(stack == null ? new ItemStack(Material.NETHER_STAR) : stack.clone());
        display.setBillboard(Display.Billboard.CENTER);
        display.setPersistent(false);
        display.setInvulnerable(true);
        setItemScale(display, scale);
        return run.track(display);
    }

    private static void setItemScale(ItemDisplay display, float scale) {
        if (display == null || display.isDead()) return;
        Transformation current = display.getTransformation();
        display.setTransformation(new Transformation(
                current.getTranslation(),
                current.getLeftRotation(),
                new Vector3f(scale, scale, scale),
                current.getRightRotation()
        ));
        display.setInterpolationDuration(1);
    }

    private static Vector horizontal(Vector vector) {
        Vector result = vector == null ? new Vector() : vector.clone();
        result.setY(0.0);
        if (result.lengthSquared() < 0.0001) return new Vector(0.0, 0.0, 1.0);
        return result.normalize();
    }

    private static Vector right(Vector forward) {
        Vector horizontal = horizontal(forward);
        return new Vector(-horizontal.getZ(), 0.0, horizontal.getX()).normalize();
    }

    private static Location lerp(Location from, Location to, double progress) {
        double clamped = Math.max(0.0, Math.min(1.0, progress));
        return from.clone().add(to.toVector().subtract(from.toVector()).multiply(clamped));
    }

    private static double ease(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

}
