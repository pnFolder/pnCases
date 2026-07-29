package ru.privatenull.cases.animation.main;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Vector3f;
import ru.privatenull.PnCasesPlugin;
import static ru.privatenull.cases.animation.CaseAnimation.playSound;
import ru.privatenull.cases.animation.CaseAnimation;
import ru.privatenull.cases.animation.AnimationRun;
import ru.privatenull.cases.animation.RewardLabel;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.Reward;
import static ru.privatenull.cases.animation.AnimationRun.removeEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AnvilAnimation extends CaseAnimation {

    public AnvilAnimation(PnCasesPlugin plugin) {
        super(plugin);
    }

    @Override
    protected void start(AnimationRun run) {
        Player player = run.player();
        CaseDefinition def = run.definition();
        Reward reward = run.reward();
        Location base = run.base();
        Runnable onFinish = run.completion();
        World w = base.getWorld();
        if (w == null) { onFinish.run(); return; }

        final double ANVIL_START_Y = 6.0;
        final double ANVIL_LAND_Y  = 1.01;
        final int PHASE1_END = 28;
        final int PHASE2_END = 92;
        final int PHASE3_END = 142;

        ItemStack rewardVisual = resolveRewardVisual(reward, def);

        BlockDisplay anvil = (BlockDisplay) w.spawnEntity(base.clone().add(0, ANVIL_START_Y, 0), EntityType.BLOCK_DISPLAY);
        anvil.setBlock(Material.ANVIL.createBlockData());
        org.bukkit.util.Transformation tf = anvil.getTransformation();
        anvil.setTransformation(new org.bukkit.util.Transformation(
                new Vector3f(-0.5f, 0f, -0.5f),
                tf.getLeftRotation(),
                new Vector3f(1.0f, 1.0f, 1.0f),
                tf.getRightRotation()
        ));
        run.track(anvil);

        ItemDisplay rewardDisplay = (ItemDisplay) w.spawnEntity(base.clone().add(0, ANVIL_LAND_Y + 0.3, 0), EntityType.ITEM_DISPLAY);
        rewardDisplay.setItemStack(rewardVisual);
        rewardDisplay.setBillboard(Display.Billboard.VERTICAL);
        org.bukkit.util.Transformation rtf = rewardDisplay.getTransformation();
        rewardDisplay.setTransformation(new org.bukkit.util.Transformation(
                rtf.getTranslation(), rtf.getLeftRotation(),
                new Vector3f(0f, 0f, 0f),
                rtf.getRightRotation()
        ));
        run.track(rewardDisplay);
        RewardLabel rewardLabel = RewardLabel.create(run, base,
                resolveRewardName(reward, rewardVisual));

        Random rng = new Random();
        List<Integer> hitTicks = new ArrayList<>();
        for (int t = PHASE1_END + 2; t < PHASE2_END; t += 5 + rng.nextInt(5)) hitTicks.add(t);

        BukkitTask task = new BukkitRunnable() {
            int t = 0;
            float rewardYaw = 0f;

            @Override
            public void run() {
                if (!player.isOnline()) { cleanup(); return; }
                t++;

                if (t <= PHASE1_END) {
                    double progress = (double) t / PHASE1_END;
                    double eased = easeInOut(progress);
                    double currentY = ANVIL_START_Y + (ANVIL_LAND_Y - ANVIL_START_Y) * eased;
                    anvil.teleport(base.clone().add(0, currentY, 0));
                    if (t % 6 == 0) playSound(w, base, Sound.ENTITY_ARROW_SHOOT, 0.14f, 0.5f + (float) eased);
                }

                if (t == PHASE1_END) {
                    anvil.teleport(base.clone().add(0, ANVIL_LAND_Y, 0));
                    playSound(w, base, Sound.BLOCK_ANVIL_LAND, 0.55f, 0.8f);
                    playSound(w, base, Sound.ENTITY_GENERIC_EXPLODE, 0.16f, 1.8f);
                    playSound(w, base, Sound.BLOCK_STONE_BREAK, 0.32f, 0.6f);
                    w.spawnParticle(Particle.BLOCK, base.clone().add(0, ANVIL_LAND_Y, 0),
                            60, 0.4, 0.1, 0.4, 0.15, Material.STONE.createBlockData());
                    w.spawnParticle(Particle.CLOUD, base.clone().add(0, ANVIL_LAND_Y, 0),
                            20, 0.5, 0.1, 0.5, 0.05);
                    playSound(player, player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.38f, 0.7f);
                }

                if (t > PHASE1_END && t <= PHASE2_END) {
                    Location anvilTop = base.clone().add(0, ANVIL_LAND_Y + 0.5, 0);
                    if (hitTicks.contains(t)) {
                        playSound(w, base, Sound.BLOCK_ANVIL_USE, 0.28f, 0.9f + rng.nextFloat() * 0.3f);
                        w.spawnParticle(Particle.LAVA, anvilTop, 8, 0.3, 0.1, 0.3, 0.0);
                        w.spawnParticle(Particle.ELECTRIC_SPARK, anvilTop, 15, 0.25, 0.15, 0.25, 0.05);
                        w.spawnParticle(Particle.CRIT, anvilTop, 10, 0.3, 0.2, 0.3, 0.1);
                        double jitter = (rng.nextDouble() - 0.5) * 0.018;
                        anvil.teleport(base.clone().add(jitter, ANVIL_LAND_Y, jitter));
                    } else {
                        anvil.teleport(base.clone().add(0, ANVIL_LAND_Y, 0));
                    }
                    if (t % 3 == 0) w.spawnParticle(Particle.SMOKE, anvilTop, 2, 0.15, 0.05, 0.15, 0.01);

                    if (t > PHASE2_END - 8) {
                        float glowScale = (float)(t - (PHASE2_END - 8)) / 8f * 0.4f;
                        org.bukkit.util.Transformation gt = rewardDisplay.getTransformation();
                        rewardDisplay.setTransformation(new org.bukkit.util.Transformation(
                                gt.getTranslation(), gt.getLeftRotation(),
                                new Vector3f(glowScale, glowScale, glowScale),
                                gt.getRightRotation()
                        ));
                        w.spawnParticle(Particle.ENCHANT, anvilTop, 5, 0.1, 0.1, 0.1, 0.3);
                    }
                }

                if (t > PHASE2_END && t <= PHASE3_END) {
                    double phaseProgress = (double)(t - PHASE2_END) / (PHASE3_END - PHASE2_END);
                    double eased = 1.0 - Math.pow(1.0 - phaseProgress, 2);
                    double rewardY = ANVIL_LAND_Y + 0.3 + eased * 1.5;
                    rewardYaw += 4f + (float)((1.0 - eased) * 6f);
                    if (rewardYaw >= 360f) rewardYaw -= 360f;

                    Location rewardLoc = base.clone().add(0, rewardY, 0);
                    rewardDisplay.teleport(rewardLoc);
                    rewardDisplay.setRotation(rewardYaw, 0f);
                    if (rewardLabel != null) rewardLabel.showAbove(rewardLoc, 0.88);

                    float sc = Math.min(1.0f, 0.4f + (float) eased * 0.6f);
                    org.bukkit.util.Transformation st = rewardDisplay.getTransformation();
                    rewardDisplay.setTransformation(new org.bukkit.util.Transformation(
                            st.getTranslation(), st.getLeftRotation(),
                            new Vector3f(sc, sc, sc),
                            st.getRightRotation()
                    ));

                    if (t % 4 == 0) {
                        w.spawnParticle(Particle.ENCHANT, rewardLoc, 2, 0.1, 0.1, 0.1, 0.12);
                        w.spawnParticle(Particle.ELECTRIC_SPARK, rewardLoc, 1, 0.15, 0.1, 0.15, 0.015);
                    }
                    if (t % 10 == 0) playSound(w, base, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.22f, 0.8f + (float) eased * 0.6f);
                }

                if (t >= PHASE3_END) {
                    Location rewardLoc = base.clone().add(0, ANVIL_LAND_Y + 1.8, 0);
                    spawnFinalParticles(w, rewardLoc);
                    playSound(w, base, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.34f, 1.1f);
                    playSound(w, base, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.34f, 1.0f);
                    playSound(w, base, Sound.ENTITY_PLAYER_LEVELUP, 0.30f, 1.5f);
                    playSound(player, player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.26f, 1.5f);
                    cleanup();
                }
            }

            private void cleanup() {
                onFinish.run();
            }
        }.runTaskTimer(plugin, 0L, 1L);
        run.track(task);
    }

    private static void spawnFinalParticles(World w, Location loc) {
        w.spawnParticle(Particle.LAVA, loc, 6, 0.4, 0.3, 0.4, 0.0);
        w.spawnParticle(Particle.ELECTRIC_SPARK, loc, 12, 0.4, 0.3, 0.4, 0.04);
        w.spawnParticle(Particle.END_ROD, loc, 8, 0.3, 0.3, 0.3, 0.025);
        w.spawnParticle(Particle.ENCHANT, loc, 10, 0.5, 0.4, 0.5, 0.18);
        w.spawnParticle(Particle.FIREWORK, loc, 5, 0.3, 0.3, 0.3, 0.02);
        for (int ring = 0; ring < 8; ring++) {
            double angle = (2 * Math.PI * ring) / 8;
            w.spawnParticle(Particle.ELECTRIC_SPARK,
                    loc.clone().add(0.6 * Math.cos(angle), 0, 0.6 * Math.sin(angle)), 1, 0, 0, 0, 0.015);
        }
    }

    private static double easeInOut(double value) {
        double x = Math.max(0.0, Math.min(1.0, value));
        return x < 0.5 ? 2.0 * x * x : 1.0 - Math.pow(-2.0 * x + 2.0, 2.0) / 2.0;
    }

    private static void safeRemove(Entity entity) {
        removeEntity(entity);
    }
}
