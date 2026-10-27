package ru.privatenull.pncases.cases.animation.legacy;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
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
import ru.privatenull.pncases.support.compat.BukkitCompat.MaterialCompat;
import ru.privatenull.pncases.support.compat.BukkitCompat.ParticleCompat;
import ru.privatenull.pncases.support.compat.BukkitCompat.SoundCompat;
import ru.privatenull.pncases.support.entity.VisualEntity;

import java.util.ArrayList;
import java.util.List;

public final class LegacyFortuneRingAnimation extends TimelineCaseAnimation {

    private static final int ITEM_COUNT = 8;
    private static final int FORM_END = 26;
    private static final int SPIN_END = 112;
    private static final int SLOW_END = 168;
    private static final int REVEAL_END = 214;

    public LegacyFortuneRingAnimation(PnCasesPlugin plugin) {
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

        ItemStack rewardVisual = resolveRewardVisual(reward, def);
        List<ItemStack> visuals = buildVisuals(def, rewardVisual);
        Vector rightAxis = rightAxis(player, base);
        run.hide(base.getBlock(), Material.AIR);

        List<VisualEntity> orbitItems = new ArrayList<>();
        Location center = base.clone().add(0.0, 1.50, 0.0);
        for (int i = 0; i < ITEM_COUNT; i++) {
            VisualEntity visual = VisualEntity.item(center, visuals.get(i % visuals.size()));
            orbitItems.add(visual);
            run.track(visual.entity());
        }

        VisualEntity rewardDisplay = VisualEntity.item(base.clone().add(0.0, -4.0, 0.0), rewardVisual);
        rewardDisplay.setScale(0.55f);
        run.track(rewardDisplay.entity());

        RewardLabel label = RewardLabel.create(run, base.clone().add(0.0, -4.0, 0.0),
                resolveRewardName(reward, rewardVisual));

        AnimationTimeline timeline = AnimationTimeline.builder().phase("scene", REVEAL_END).build();
        startTimeline(run, new TimelineTask(run, "legacy-fortune-ring", timeline) {
            int tick;
            double rotation;

            @Override
            protected void render(AnimationFrame frame) {
                tick = frame.tick();

                if (tick <= FORM_END) {
                    playForm();
                    return;
                }

                if (tick <= SPIN_END) {
                    playSpin();
                    return;
                }

                if (tick <= SLOW_END) {
                    playSlow();
                    return;
                }

                if (tick <= REVEAL_END) {
                    playReveal();
                    return;
                }

            }

            @Override
            protected void onComplete() {
                Location rewardLoc = base.clone().add(0.0, 1.86, 0.0);
                spawnFinalBurst(world, rewardLoc);
                SoundCompat.play(world, base, new String[]{"UI_TOAST_CHALLENGE_COMPLETE", "ENTITY_PLAYER_LEVELUP"}, 0.24f, 1.25f);
                SoundCompat.play(player, new String[]{"ENTITY_PLAYER_LEVELUP"}, 0.20f, 1.55f);
            }

            private void playForm() {
                double progress = clamp(tick / (double) FORM_END);
                double eased = easeOut(progress);
                rotation += 0.07 + progress * 0.08;

                updateOrbit(eased, 0.30 + eased * 1.05, 1.48, rotation, 0.52f);
                rewardDisplay.teleport(base.clone().add(0.0, -4.0, 0.0));
                rewardDisplay.setScale(0.0f);
                if (tick % 2 == 0) {
                    drawVerticalRing(world, base, rightAxis, 0.45 + eased * 1.0, 1.48, rotation, new String[]{"END_ROD"});
                }
                if (tick % 4 == 0) {
                    drawHorizontalTrace(world, base, 0.70 + eased * 0.45, 0.82, rotation, new String[]{"ENCHANT", "ENCHANTMENT_TABLE"});
                }

                if (tick == 1) {
                    SoundCompat.play(world, base, new String[]{"BLOCK_AMETHYST_BLOCK_CHIME", "BLOCK_NOTE_BLOCK_PLING"}, 0.18f, 1.35f);
                    SoundCompat.play(world, base, new String[]{"BLOCK_BEACON_ACTIVATE", "BLOCK_END_PORTAL_FRAME_FILL"}, 0.12f, 1.65f);
                }
                if (tick % 8 == 0) {
                    SoundCompat.play(world, base, new String[]{"BLOCK_NOTE_BLOCK_BELL", "BLOCK_NOTE_BLOCK_PLING"}, 0.09f, 1.20f + (float) progress * 0.35f);
                }
            }

            private void playSpin() {
                double progress = clamp((tick - FORM_END) / (double) (SPIN_END - FORM_END));
                rotation += 0.24 + progress * 0.08;

                updateOrbit(1.0, 1.35, 1.52 + Math.sin(tick * 0.10) * 0.03, rotation, 0.62f);
                rewardDisplay.teleport(base.clone().add(0.0, -4.0, 0.0));
                rewardDisplay.setRotation((float) ((tick * 7.0) % 360.0), 0.0f);
                rewardDisplay.setScale(0.0f);

                if (tick % 3 == 0) {
                    drawVerticalRing(world, base, rightAxis, 1.35, 1.52, rotation, new String[]{"END_ROD"});
                }
                if (tick % 6 == 0) {
                    drawHorizontalTrace(world, base, 1.0, 0.86, -rotation * 0.7, new String[]{"ENCHANT", "ENCHANTMENT_TABLE"});
                }

                if (tick % 6 == 0) {
                    SoundCompat.play(world, base, new String[]{"BLOCK_NOTE_BLOCK_PLING"}, 0.07f, 1.45f + (float) Math.sin(tick * 0.08) * 0.18f);
                }
                if (tick % 8 == 0) {
                    ParticleCompat.spawn(world, base.clone().add(0.0, 1.45, 0.0),
                            new String[]{"ENCHANT", "ENCHANTMENT_TABLE"}, 2, 0.34, 0.10, 0.34, 0.12);
                }
            }

            private void playSlow() {
                double progress = clamp((tick - SPIN_END) / (double) (SLOW_END - SPIN_END));
                double speed = 0.25 * Math.pow(1.0 - progress, 2.0) + 0.04;
                double radius = 1.35 - progress * 0.38;
                rotation += speed;

                updateOrbit(1.0, radius, 1.55 + progress * 0.10, rotation, 0.62f);
                rewardDisplay.teleport(base.clone().add(0.0, -4.0, 0.0));
                rewardDisplay.setRotation((float) ((tick * 5.0) % 360.0), 0.0f);
                rewardDisplay.setScale(0.0f);

                if (tick % 3 == 0) {
                    drawVerticalRing(world, base, rightAxis, radius, 1.58, rotation, new String[]{"END_ROD"});
                }
                if (tick % 4 == 0) {
                    ParticleCompat.spawn(world, base.clone().add(0.0, 1.48, 0.0), new String[]{"PORTAL"}, 3, 0.35, 0.14, 0.35, 0.08);
                }

                if (tick % 10 == 0) {
                    SoundCompat.play(world, base, new String[]{"BLOCK_AMETHYST_BLOCK_CHIME", "BLOCK_NOTE_BLOCK_PLING"}, 0.10f, 1.60f - (float) progress * 0.25f);
                }
            }

            private void playReveal() {
                double progress = clamp((tick - SLOW_END) / (double) (REVEAL_END - SLOW_END));
                double eased = easeOutBack(progress);
                if (tick == SLOW_END + 1) {
                    run.protectReward(base.clone().add(0.0, 1.70, 0.0));
                }
                rotation += 0.035;

                for (int i = 0; i < orbitItems.size(); i++) {
                    VisualEntity visual = orbitItems.get(i);
                    double angle = angle(i, orbitItems.size(), rotation);
                    double radius = 0.95 * (1.0 - progress);
                    double centerY = 1.58 + progress * 0.12 + Math.sin(tick * 0.12 + i) * 0.02;
                    visual.teleport(verticalPoint(base, rightAxis, radius, centerY, angle));
                    visual.setScale((float) Math.max(0.0, 0.62 * (1.0 - progress)));
                }

                Location rewardLoc = base.clone().add(0.0, 1.54 + eased * 0.34, 0.0);
                rewardDisplay.teleport(rewardLoc);
                rewardDisplay.setRotation((float) ((tick * 5.5) % 360.0), 0.0f);
                rewardDisplay.setScale((float) Math.min(0.72, 0.18 + eased * 0.54));

                if (tick == SLOW_END + 4) {
                    SoundCompat.play(world, base, new String[]{"BLOCK_END_PORTAL_FRAME_FILL", "ENTITY_PLAYER_LEVELUP"}, 0.16f, 1.50f);
                }
                if (label != null && tick >= SLOW_END + 4) label.showAbove(rewardLoc, 0.88);

                if (tick % 4 == 0) {
                    drawVerticalRing(world, base, rightAxis, 0.35 + progress * 0.20, 1.66, -rotation, new String[]{"FIREWORK", "FIREWORKS_SPARK"});
                }
                if (tick % 4 == 0) {
                    ParticleCompat.spawn(world, rewardLoc, new String[]{"END_ROD"}, 1, 0.16, 0.10, 0.16, 0.01);
                }
            }

            private void updateOrbit(double alpha, double radius, double centerY, double rotation, float scale) {
                for (int i = 0; i < orbitItems.size(); i++) {
                    VisualEntity visual = orbitItems.get(i);
                    double angle = angle(i, orbitItems.size(), rotation);
                    double bob = Math.sin(tick * 0.14 + i * 0.65) * 0.035;
                    visual.teleport(verticalPoint(base, rightAxis, radius, centerY + bob, angle));
                    visual.setRotation((float) Math.toDegrees(angle) + 90.0f, 0.0f);
                    visual.setScale(Math.max(0.0f, scale * (float) alpha));
                }
            }

        });
    }

    private List<ItemStack> buildVisuals(CaseDefinition def, ItemStack rewardVisual) {
        List<ItemStack> visuals = new ArrayList<>();
        if (def != null && def.animationItems() != null) {
            for (ItemStack item : def.animationItems()) {
                if (item != null && !item.getType().isAir()) {
                    ItemStack clone = item.clone();
                    clone.setAmount(1);
                    visuals.add(clone);
                }
            }
        }
        if (rewardVisual != null && !rewardVisual.getType().isAir()) {
            ItemStack clone = rewardVisual.clone();
            clone.setAmount(1);
            visuals.add(clone);
        }
        if (visuals.isEmpty()) {
            visuals.add(new ItemStack(Material.EMERALD));
            visuals.add(new ItemStack(Material.DIAMOND));
            visuals.add(new ItemStack(Material.GOLD_INGOT));
            visuals.add(new ItemStack(MaterialCompat.first("AMETHYST_SHARD", "NETHER_STAR")));
        }
        return visuals;
    }

    private static void drawVerticalRing(World world, Location base, Vector rightAxis, double radius, double centerY, double rotation, String[] particles) {
        for (int i = 0; i < 10; i++) {
            double angle = angle(i, 10, rotation);
            ParticleCompat.spawn(world, verticalPoint(base, rightAxis, radius, centerY, angle),
                    particles, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void drawHorizontalTrace(World world, Location base, double radius, double y, double rotation, String[] particles) {
        for (int i = 0; i < 5; i++) {
            double angle = angle(i, 5, rotation);
            Location loc = base.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            ParticleCompat.spawn(world, loc, particles, 1, 0.01, 0.01, 0.01, 0.0);
        }
    }

    private static void spawnFinalBurst(World world, Location loc) {
        ParticleCompat.spawn(world, loc, new String[]{"FIREWORK", "FIREWORKS_SPARK"}, 5, 0.32, 0.18, 0.32, 0.018);
        ParticleCompat.spawn(world, loc, new String[]{"END_ROD"}, 6, 0.28, 0.22, 0.28, 0.016);
        ParticleCompat.spawn(world, loc, new String[]{"ENCHANT", "ENCHANTMENT_TABLE"}, 7, 0.36, 0.24, 0.36, 0.10);
        for (int i = 0; i < 6; i++) {
            double angle = angle(i, 6, 0.0);
            Location spark = loc.clone().add(Math.cos(angle) * 0.82, 0.03, Math.sin(angle) * 0.82);
            ParticleCompat.spawn(world, spark, new String[]{"END_ROD"}, 1, 0.0, 0.0, 0.0, 0.015);
        }
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

    private static double angle(int index, int total, double rotation) {
        return (Math.PI * 2.0 * index / Math.max(1, total)) + rotation;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double easeOut(double value) {
        double x = clamp(value);
        return 1.0 - Math.pow(1.0 - x, 3.0);
    }

    private static double easeOutBack(double value) {
        double x = clamp(value);
        double c1 = 1.70158;
        double c3 = c1 + 1.0;
        return 1.0 + c3 * Math.pow(x - 1.0, 3.0) + c1 * Math.pow(x - 1.0, 2.0);
    }

    private static void safeRemove(VisualEntity visual) {
        if (visual != null) visual.remove();
    }

}
