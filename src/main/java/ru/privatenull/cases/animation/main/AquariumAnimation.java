package ru.privatenull.cases.animation.main;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import ru.privatenull.PnCasesPlugin;
import static ru.privatenull.cases.animation.CaseAnimation.playSound;
import ru.privatenull.cases.animation.CaseAnimation;
import ru.privatenull.cases.animation.AnimationRun;
import ru.privatenull.cases.animation.RewardLabel;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.pnlibrary.item.HeadUtil;
import ru.privatenull.pnlibrary.compat.BukkitCompat.ParticleCompat;

/** A temporary glass capsule with a water core and a smooth reward reveal. */
public final class AquariumAnimation extends CaseAnimation {

    private static final String HEART_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTExM2VlNjEwODQxZGVkMjE1YWNkMmI0Y2FhZWVmODdkZmQ2ZTNkNDc2OGU3YWI0ZTE5ZWI3NmIzZDgxMjFjZiJ9fX0=";

    public AquariumAnimation(PnCasesPlugin plugin) {
        super(plugin);
    }

    @Override
    protected void start(AnimationRun run) {
        Player player = run.player();
        CaseDefinition def = run.definition();
        Reward reward = run.reward();
        Location base = run.base();
        Runnable onFinish = run.completion();
        World world = base.getWorld();
        if (world == null) {
            onFinish.run();
            return;
        }

        Block caseBlock = base.getBlock();
        boolean hidden = run.hide(caseBlock, Material.GLASS);
        Location center = caseBlock.getLocation().add(0.5, 0.0, 0.5);

        ItemDisplay heart = item(world, center.clone().add(0.0, 1.02, 0.0),
                HeadUtil.create(HEART_TEXTURE, "&#4FB6FFСердце моря"), 1.18f);
        ItemStack rewardVisual = resolveRewardVisual(reward, def);
        ItemDisplay rewardDisplay = item(world, center.clone().add(0.0, 0.60, 0.0), rewardVisual, 0.01f);
        RewardLabel rewardLabel = RewardLabel.create(run, center,
                resolveRewardName(reward, rewardVisual));
        ItemDisplay[] crystals = new ItemDisplay[4];
        for (int index = 0; index < crystals.length; index++) {
            crystals[index] = item(world, center.clone().add(0.0, 1.05, 0.0),
                    new ItemStack(Material.PRISMARINE_CRYSTALS), 0.24f);
            run.track(crystals[index]);
        }
        run.track(heart);
        run.track(rewardDisplay);

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
                    playSound(world, center, Sound.BLOCK_GLASS_PLACE, 0.45f, 1.45f);
                    playSound(world, center, Sound.ENTITY_PLAYER_SPLASH, 0.28f, 1.20f);
                }

                if (tick <= 72) {
                    double bob = Math.sin(tick * 0.16) * 0.10;
                    heart.teleport(center.clone().add(0.0, 1.02 + bob, 0.0));
                    heart.setRotation((tick * 4.5f) % 360.0f, 0.0f);
                    updateCrystals(crystals, center, tick, 0.52, 1.02, 0.24f);
                    waterParticles(world, center, tick, 0.48, 5);
                    if (tick % 18 == 0) playSound(world, center, Sound.ENTITY_PLAYER_SPLASH, 0.12f, 1.65f);
                } else if (tick <= 150) {
                    double progress = easeOut((tick - 72) / 78.0);
                    float heartScale = (float) (1.18 * (1.0 - progress));
                    scale(heart, Math.max(0.01f, heartScale));
                    heart.teleport(center.clone().add(0.0, 1.02 + progress * 0.28, 0.0));
                    updateCrystals(crystals, center, tick, 0.52 + progress * 1.10, 1.02 + progress * 0.65,
                            (float) (0.24 * (1.0 - progress)));

                    double rise = 0.60 + progress * 1.65;
                    rewardDisplay.teleport(center.clone().add(0.0, rise, 0.0));
                    rewardDisplay.setRotation((tick * 6.0f) % 360.0f, 0.0f);
                    scale(rewardDisplay, (float) (0.18 + progress * 0.82));
                    if (rewardLabel != null) rewardLabel.showAbove(rewardDisplay.getLocation(), 0.88);
                    if (tick % 3 == 0) {
                        waterParticles(world, center.clone().add(0.0, progress * 0.65, 0.0), tick, 0.56, 3);
                    }
                    if (tick == 86 && hidden && caseBlock.getType() == Material.GLASS) {
                        playSound(world, center, Sound.BLOCK_GLASS_BREAK, 0.35f, 1.70f);
                        caseBlock.setType(Material.AIR, false);
                        world.spawnParticle(Particle.BLOCK, center.clone().add(0.0, 0.55, 0.0), 18,
                                0.32, 0.32, 0.32, 0.08, Material.GLASS.createBlockData());
                    }
                    if (tick == 128) {
                        playSound(world, center, Sound.ENTITY_PLAYER_LEVELUP, 0.22f, 1.55f);
                    }
                } else {
                    ParticleCompat.spawn(world, rewardDisplay.getLocation(),
                            new String[]{"BUBBLE", "WATER_BUBBLE", "BUBBLE_POP"},
                            4, 0.28, 0.28, 0.28, 0.035);
                    finish();
                }
            }

            private void finish() {
                if (finished) return;
                finished = true;
                onFinish.run();
            }
        }.runTaskTimer(plugin, 0L, 1L);
        run.track(task);
    }

    private static ItemDisplay item(World world, Location location, ItemStack stack, float scale) {
        ItemDisplay display = (ItemDisplay) world.spawnEntity(location, EntityType.ITEM_DISPLAY);
        display.setItemStack(stack);
        display.setBillboard(Display.Billboard.FIXED);
        scale(display, scale);
        return display;
    }

    private static void scale(ItemDisplay display, float scale) {
        Transformation current = display.getTransformation();
        display.setTransformation(new Transformation(
                current.getTranslation(), current.getLeftRotation(),
                new Vector3f(scale, scale, scale), current.getRightRotation()));
    }

    private static void waterParticles(World world, Location center, int tick, double radius, int amount) {
        for (int index = 0; index < amount; index++) {
            double angle = tick * 0.24 + (Math.PI * 2.0 * index / amount);
            Location point = center.clone().add(Math.cos(angle) * radius,
                    0.30 + Math.sin(tick * 0.12 + index) * 0.40, Math.sin(angle) * radius);
            ParticleCompat.spawn(world, point, new String[]{"BUBBLE", "WATER_BUBBLE", "BUBBLE_POP"},
                    1, 0.02, 0.04, 0.02, 0.015);
            if (tick % 3 == 0) {
                ParticleCompat.spawn(world, point, new String[]{"DRIPPING_WATER", "DRIP_WATER", "WATER_DROP"},
                        1, 0.03, 0.04, 0.03, 0.0);
            }
        }
    }

    private static void updateCrystals(ItemDisplay[] crystals, Location center, int tick,
                                       double radius, double height, float scale) {
        for (int index = 0; index < crystals.length; index++) {
            ItemDisplay crystal = crystals[index];
            if (crystal == null || crystal.isDead()) continue;
            double angle = tick * 0.18 + (Math.PI * 2.0 * index / crystals.length);
            double bob = Math.sin(tick * 0.20 + index) * 0.18;
            crystal.teleport(center.clone().add(Math.cos(angle) * radius, height + bob, Math.sin(angle) * radius));
            crystal.setRotation((float) Math.toDegrees(angle), 0.0f);
            scale(crystal, Math.max(0.01f, scale));
        }
    }

    private static double easeOut(double progress) {
        double clamped = Math.max(0.0, Math.min(1.0, progress));
        return 1.0 - Math.pow(1.0 - clamped, 3.0);
    }

}
