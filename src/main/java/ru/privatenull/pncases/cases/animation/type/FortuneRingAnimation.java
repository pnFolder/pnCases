package ru.privatenull.pncases.cases.animation.type;

import static ru.privatenull.pncases.cases.animation.effect.AnimationEffects.Kind.*;
import static ru.privatenull.pncases.cases.animation.effect.AnimationEffects.particle;
import static ru.privatenull.pncases.cases.animation.effect.AnimationEffects.spawn;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.joml.Vector3f;
import ru.privatenull.pncases.PnCasesPlugin;
import static ru.privatenull.pncases.cases.animation.core.AbstractCaseAnimation.playSound;
import ru.privatenull.pncases.cases.animation.core.AnimationFrame;
import ru.privatenull.pncases.cases.animation.core.AnimationSession;
import ru.privatenull.pncases.cases.animation.core.AnimationTimeline;
import ru.privatenull.pncases.cases.animation.core.TimelineCaseAnimation;
import ru.privatenull.pncases.cases.animation.core.TimelineTask;
import ru.privatenull.pncases.cases.animation.effect.RewardLabel;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.Reward;
import static ru.privatenull.pncases.cases.animation.core.AnimationSession.removeEntity;

import java.util.ArrayList;
import java.util.List;

public final class FortuneRingAnimation extends TimelineCaseAnimation {

    private static final int FULL_ROTATIONS = 5;
    private static final double FINAL_RING_RADIUS = 1.03;

    public FortuneRingAnimation(PnCasesPlugin plugin) {
        super(plugin);
    }

    @Override
    protected void start(AnimationSession run) {
        Player player = run.player();
        CaseDefinition def = run.definition();
        Reward reward = run.reward();
        Location base = run.base();
        World world = base.getWorld();
        if (world == null) {
            run.complete();
            return;
        }

        AnimationTimeline timeline = AnimationTimeline.builder()
                .phase("form", 28).phase("spin", 90)
                .phase("slow", 60).phase("select", 22)
                .phase("reveal", 44).build();

        List<RingEntry> entries = buildEntries(def, reward);
        List<ItemDisplay> orbitItems = new ArrayList<>();
        int winnerIndex = winnerIndex(entries);
        ItemStack rewardVisual = entries.get(winnerIndex).visual();
        float ringItemScale = ringItemScale(entries.size());
        Vector rightAxis = rightAxis(player, base);
        Location center = base.clone().add(0.0, 1.55, 0.0);
        run.hide(base.getBlock(), Material.AIR);

        for (RingEntry entry : entries) {
            ItemDisplay display = (ItemDisplay) world.spawnEntity(center, EntityType.ITEM_DISPLAY);
            display.setItemStack(entry.visual());
            display.setBillboard(Display.Billboard.VERTICAL);
            setScale(display, 0.0f);
            orbitItems.add(display);
            run.track(display);
        }

        ItemDisplay rewardDisplay = orbitItems.get(winnerIndex);
        RewardLabel rewardLabel = RewardLabel.create(run, base,
                resolveRewardName(reward, rewardVisual));

        double winnerBaseAngle = angle(winnerIndex, orbitItems.size(), 0.0);
        double alignment = positiveAngle(Math.PI / 2.0 - winnerBaseAngle);
        double targetRotation = FULL_ROTATIONS * Math.PI * 2.0 + alignment;

        startTimeline(run, new TimelineTask(run, "fortune-ring-main", timeline) {
            int tick = 0;
            double rotation = 0.0;

            @Override
            protected void render(AnimationFrame frame) {
                tick = frame.tick();
                if (tick <= timeline.end("slow")) {
                    double spinProgress = tick / (double) timeline.end("slow");
                    rotation = targetRotation * easeOutCubic(spinProgress);
                }

                if (timeline.in("form", tick)) {
                    playForm();
                    return;
                }

                if (timeline.in("spin", tick)) {
                    playSpin();
                    return;
                }

                if (timeline.in("slow", tick)) {
                    playSlow();
                    return;
                }

                if (timeline.in("select", tick)) {
                    playSelect();
                    return;
                }

                if (timeline.in("reveal", tick)) {
                    playReveal();
                    return;
                }

            }

            @Override
            protected void onComplete() {
                Location rewardLoc = base.clone().add(0.0, 1.83, 0.0);
                spawnFinalBurst(world, rewardLoc);
                playSound(world, base, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.26f, 1.20f);
                playSound(world, base, Sound.ENTITY_PLAYER_LEVELUP, 0.22f, 1.55f);
                playSound(player, player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.20f, 1.55f);
            }

            private void playForm() {
                double progress = timeline.progress("form", tick);
                double eased = easeOut(progress);

                updateOrbit(eased, 0.35 + eased * 1.10, 1.55, rotation, ringItemScale);
                if (tick % 2 == 0) {
                    drawRing(world, base, rightAxis, 0.55 + eased * 1.05, 1.55, rotation, Particle.END_ROD);
                }

                if (tick == 1) {
                    playSound(world, base, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.20f, 1.35f);
                    playSound(world, base, Sound.BLOCK_BEACON_ACTIVATE, 0.15f, 1.85f);
                }
                if (tick % 7 == 0) {
                    playSound(world, base, Sound.BLOCK_NOTE_BLOCK_BELL, 0.10f, 1.2f + (float) progress * 0.35f);
                }
            }

            private void playSpin() {
                updateOrbit(1.0, 1.45, 1.55 + Math.sin(tick * 0.10) * 0.03,
                        rotation, ringItemScale);
                if (tick % 2 == 0) {
                    drawRing(world, base, rightAxis, 1.45, 1.55, rotation, Particle.ELECTRIC_SPARK);
                }

                if (tick % 5 == 0) {
                    playSound(world, base, Sound.BLOCK_NOTE_BLOCK_PLING, 0.08f, 1.45f + (float) Math.sin(tick * 0.07) * 0.18f);
                }
                if (tick % 6 == 0) {
                    spawn(world, particle(ENCHANT), center, 3, 0.42, 0.12, 0.42, 0.14);
                }
            }

            private void playSlow() {
                double progress = timeline.progress("slow", tick);
                double radius = 1.45 - progress * (1.45 - FINAL_RING_RADIUS);

                updateOrbit(1.0, radius, 1.55 + progress * 0.10,
                        rotation, ringItemScale);
                if (tick % 2 == 0) {
                    drawRing(world, base, rightAxis, radius, 1.60, rotation, Particle.END_ROD);
                }
                if (tick % 4 == 0) {
                    spawn(world, Particle.PORTAL, center, 4, 0.38, 0.14, 0.38, 0.10);
                }

                if (tick % 10 == 0) {
                    playSound(world, base, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.11f, 1.65f - (float) progress * 0.35f);
                }
            }

            private void playSelect() {
                double progress = timeline.progress("select", tick);
                Location selected = selectedLocation();

                for (int i = 0; i < orbitItems.size(); i++) {
                    ItemDisplay display = orbitItems.get(i);
                    double itemAngle = angle(i, orbitItems.size(), targetRotation);
                    Location position = verticalPoint(base, rightAxis, FINAL_RING_RADIUS,
                            1.65, itemAngle);
                    display.teleport(position);
                    display.setRotation((float) Math.toDegrees(itemAngle) + 90.0f, 0.0f);
                    if (i == winnerIndex) {
                        float pulse = (float) (0.08 * Math.sin(progress * Math.PI * 4.0));
                        setScale(display, Math.min(0.72f, ringItemScale + 0.16f + pulse));
                    } else {
                        setScale(display, ringItemScale * 0.72f);
                    }
                }

                if (rewardLabel != null) rewardLabel.showAbove(selected, 0.62);
                drawWinnerMarker(world, selected, rightAxis, progress);

                if (tick == timeline.start("select")) {
                    playSound(world, selected, Sound.BLOCK_NOTE_BLOCK_BELL, 0.18f, 1.75f);
                    playSound(world, selected, Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.18f, 1.35f);
                }
                if (tick % 5 == 0) {
                    playSound(world, selected, Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                            0.10f, 1.55f + (float) progress * 0.25f);
                }
            }

            private void playReveal() {
                double progress = timeline.progress("reveal", tick);
                Location selectedStart = selectedLocation();
                Location selectedTarget = base.clone().add(0.0, 1.83, 0.0);

                for (int i = 0; i < orbitItems.size(); i++) {
                    ItemDisplay display = orbitItems.get(i);
                    if (i == winnerIndex) {
                        Location selected = lerp(selectedStart, selectedTarget, easeOut(progress));
                        display.teleport(selected);
                        display.setRotation((float) ((tick * 5.0) % 360.0), 0.0f);
                        setScale(display, (float) (ringItemScale
                                + (1.0f - ringItemScale) * easeOut(progress)));
                        continue;
                    }
                    double itemAngle = angle(i, orbitItems.size(), targetRotation);
                    double radius = FINAL_RING_RADIUS + progress * 0.58;
                    double centerY = 1.65 + Math.sin(tick * 0.12 + i) * 0.02;
                    display.teleport(verticalPoint(base, rightAxis, radius, centerY, itemAngle));
                    setScale(display, (float) Math.max(0.0,
                            ringItemScale * 0.72 * Math.pow(1.0 - progress, 1.6)));
                }

                Location rewardLoc = rewardDisplay.getLocation();
                if (rewardLabel != null) rewardLabel.showAbove(rewardLoc, 0.88);

                if (tick == timeline.start("reveal")) {
                    run.protectReward(base.clone().add(0.0, 1.83, 0.0));
                    playSound(world, selectedStart, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.18f, 1.35f);
                }

                if (tick % 4 == 0) {
                    drawRing(world, base, rightAxis, 0.42 + progress * 0.22, 1.65, -rotation, particle(FIREWORK));
                }
                if (tick % 4 == 0) {
                    spawn(world, Particle.END_ROD, rewardLoc, 1, 0.18, 0.12, 0.18, 0.012);
                }
            }

            private Location selectedLocation() {
                return verticalPoint(base, rightAxis, FINAL_RING_RADIUS,
                        1.65, angle(winnerIndex, orbitItems.size(), targetRotation));
            }

            private void updateOrbit(double alpha, double radius, double centerY, double rotation, float scale) {
                for (int i = 0; i < orbitItems.size(); i++) {
                    ItemDisplay display = orbitItems.get(i);
                    double angle = angle(i, orbitItems.size(), rotation);
                    double bob = Math.sin(tick * 0.15 + i * 0.70) * 0.035;
                    Location loc = verticalPoint(base, rightAxis, radius, centerY + bob, angle);

                    display.teleport(loc);
                    display.setRotation((float) Math.toDegrees(angle) + 90.0f, 0.0f);
                    setScale(display, Math.max(0.0f, scale * (float) alpha));
                }
            }

        });
    }

    private List<RingEntry> buildEntries(CaseDefinition definition, Reward winner) {
        List<RingEntry> entries = new ArrayList<>();
        if (definition != null && definition.rewards() != null) {
            for (Reward candidate : definition.rewards()) {
                if (candidate == null) continue;
                ItemStack visual = resolveRewardVisual(candidate, definition);
                if (visual != null && !visual.getType().isAir()) {
                    entries.add(new RingEntry(visual.clone(), candidate == winner));
                }
            }
        }
        if (entries.isEmpty()) {
            entries.add(new RingEntry(new ItemStack(Material.AMETHYST_SHARD), true));
        } else if (entries.stream().noneMatch(RingEntry::winner)) {
            ItemStack visual = resolveRewardVisual(winner, definition);
            entries.add(new RingEntry(visual == null
                    ? new ItemStack(Material.AMETHYST_SHARD) : visual.clone(), true));
        }
        return entries;
    }

    private static int winnerIndex(List<RingEntry> entries) {
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).winner()) return index;
        }
        return 0;
    }

    private static float ringItemScale(int itemCount) {
        return Math.max(0.24f, Math.min(0.48f,
                (float) (1.50 / Math.sqrt(Math.max(1, itemCount)))));
    }

    private static void drawRing(World world, Location base, Vector rightAxis, double radius, double centerY, double rotation, Particle particle) {
        for (int i = 0; i < 12; i++) {
            double angle = angle(i, 12, rotation);
            Location loc = verticalPoint(base, rightAxis, radius, centerY, angle);
            spawn(world, particle, loc, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void spawnFinalBurst(World world, Location loc) {
        spawn(world, particle(FIREWORK), loc, 5, 0.34, 0.20, 0.34, 0.02);
        spawn(world, Particle.END_ROD, loc, 7, 0.30, 0.26, 0.30, 0.018);
        spawn(world, particle(ENCHANT), loc, 8, 0.42, 0.30, 0.42, 0.12);
        for (int i = 0; i < 6; i++) {
            double angle = angle(i, 6, 0.0);
            spawn(world, Particle.ELECTRIC_SPARK,
                    loc.clone().add(Math.cos(angle) * 0.9, 0.05, Math.sin(angle) * 0.9),
                    1, 0.0, 0.0, 0.0, 0.02);
        }
    }

    private static void drawWinnerMarker(World world, Location selected, Vector rightAxis,
                                         double progress) {
        double pulse = 0.12 + Math.sin(progress * Math.PI * 4.0) * 0.04;
        Location pointer = selected.clone().add(0.0, 0.72 + pulse, 0.0);
        spawn(world, Particle.END_ROD, selected, 2, 0.18, 0.18, 0.18, 0.01);
        spawn(world, Particle.ELECTRIC_SPARK, pointer, 3, 0.10, 0.08, 0.10, 0.02);
        for (int side : new int[]{-1, 1}) {
            for (int point = 0; point <= 4; point++) {
                double step = point / 4.0;
                Location marker = pointer.clone()
                        .add(rightAxis.clone().multiply(side * step * 0.24))
                        .add(0.0, -step * 0.30, 0.0);
                spawn(world, Particle.END_ROD, marker, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private static void setScale(ItemDisplay display, float scale) {
        org.bukkit.util.Transformation tf = display.getTransformation();
        display.setTransformation(new org.bukkit.util.Transformation(
                tf.getTranslation(),
                tf.getLeftRotation(),
                new Vector3f(scale, scale, scale),
                tf.getRightRotation()
        ));
    }

    private static double angle(int index, int total, double rotation) {
        return (Math.PI * 2.0 * index / Math.max(1, total)) + rotation;
    }

    private static double positiveAngle(double angle) {
        double full = Math.PI * 2.0;
        double normalized = angle % full;
        return normalized < 0.0 ? normalized + full : normalized;
    }

    private static Location lerp(Location from, Location to, double progress) {
        return from.clone().add(to.toVector().subtract(from.toVector()).multiply(clamp(progress)));
    }

    private static Location verticalPoint(Location base, Vector rightAxis, double radius, double centerY, double angle) {
        return base.clone()
                .add(rightAxis.clone().multiply(Math.cos(angle) * radius))
                .add(0.0, centerY + Math.sin(angle) * radius, 0.0);
    }

    private static Vector rightAxis(Player player, Location base) {
        Vector fromCaseToPlayer = player.getLocation().toVector().subtract(base.toVector());
        fromCaseToPlayer.setY(0.0);
        if (fromCaseToPlayer.lengthSquared() < 0.0001) {
            fromCaseToPlayer = player.getLocation().getDirection().multiply(-1.0);
            fromCaseToPlayer.setY(0.0);
        }
        if (fromCaseToPlayer.lengthSquared() < 0.0001) {
            return new Vector(1.0, 0.0, 0.0);
        }
        fromCaseToPlayer.normalize();
        return new Vector(-fromCaseToPlayer.getZ(), 0.0, fromCaseToPlayer.getX()).normalize();
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double easeOut(double value) {
        double x = clamp(value);
        return 1.0 - Math.pow(1.0 - x, 3.0);
    }

    private static double easeOutCubic(double value) {
        double x = clamp(value);
        return 1.0 - Math.pow(1.0 - x, 3.0);
    }

    private static void safeRemove(Entity entity) {
        removeEntity(entity);
    }

    private record RingEntry(ItemStack visual, boolean winner) {
    }

}
