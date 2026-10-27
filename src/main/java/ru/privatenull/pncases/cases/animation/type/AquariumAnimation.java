package ru.privatenull.pncases.cases.animation.type;

import static ru.privatenull.pncases.cases.animation.effect.AnimationEffects.Kind.*;
import static ru.privatenull.pncases.cases.animation.effect.AnimationEffects.particle;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import ru.privatenull.pncases.PnCasesPlugin;
import ru.privatenull.pncases.cases.animation.effect.AnimationEffects;
import static ru.privatenull.pncases.cases.animation.core.AbstractCaseAnimation.playSound;
import ru.privatenull.pncases.cases.animation.core.AnimationSession;
import ru.privatenull.pncases.cases.animation.core.AnimationFrame;
import ru.privatenull.pncases.cases.animation.effect.RewardLabel;
import ru.privatenull.pncases.cases.animation.core.AnimationTimeline;
import ru.privatenull.pncases.cases.animation.core.TimelineCaseAnimation;
import ru.privatenull.pncases.cases.animation.core.TimelineTask;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pncases.gui.GuiDesign;
import ru.privatenull.pncases.support.item.HeadUtil;
import ru.privatenull.pncases.support.compat.BukkitCompat.ParticleCompat;

/** A temporary glass capsule with a water core and a smooth reward reveal. */
public final class AquariumAnimation extends TimelineCaseAnimation {

    public AquariumAnimation(PnCasesPlugin plugin) {
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
                .phase("aquarium", 72).phase("reveal", 78).build();

        Block caseBlock = base.getBlock();
        boolean hidden = run.hide(caseBlock, Material.GLASS);
        Location center = caseBlock.getLocation().add(0.5, 0.0, 0.5);

        ItemDisplay heart = item(world, center.clone().add(0.0, 1.02, 0.0),
                HeadUtil.create(GuiDesign.AQUARIUM_HEART, "&#4FB6FFСердце моря"), 1.18f);
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

        startTimeline(run, new TimelineTask(run, "aquarium-main", timeline) {
            @Override
            protected void render(AnimationFrame frame) {
                int tick = frame.tick();
                if (tick == 1) {
                    playSound(world, center, Sound.BLOCK_GLASS_PLACE, 0.45f, 1.45f);
                    playSound(world, center, Sound.ENTITY_PLAYER_SPLASH, 0.28f, 1.20f);
                }

                if (timeline.in("aquarium", tick)) {
                    double bob = Math.sin(tick * 0.16) * 0.10;
                    heart.teleport(center.clone().add(0.0, 1.02 + bob, 0.0));
                    heart.setRotation((tick * 4.5f) % 360.0f, 0.0f);
                    updateCrystals(crystals, center, tick, 0.52, 1.02, 0.24f);
                    waterParticles(world, center, tick, 0.48, 5);
                    if (tick % 18 == 0) playSound(world, center, Sound.ENTITY_PLAYER_SPLASH, 0.12f, 1.65f);
                } else if (timeline.in("reveal", tick)) {
                    if (tick == timeline.start("reveal")) {
                        run.protectReward(center.clone().add(0.0, 1.35, 0.0));
                    }
                    double progress = easeOut(timeline.progress("reveal", tick));
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
                        AnimationEffects.block(world, particle(BLOCK), center.clone().add(0.0, 0.55, 0.0), 18,
                                0.32, 0.32, 0.32, 0.08, Material.GLASS.createBlockData());
                    }
                    if (tick == 128) {
                        playSound(world, center, Sound.ENTITY_PLAYER_LEVELUP, 0.22f, 1.55f);
                    }
                }
            }

            @Override
            protected void onComplete() {
                ParticleCompat.spawn(world, rewardDisplay.getLocation(),
                        new String[]{"BUBBLE", "WATER_BUBBLE", "BUBBLE_POP"},
                        4, 0.28, 0.28, 0.28, 0.035);
            }
        });
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
