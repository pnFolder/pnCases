package ru.privatenull.pncases.cases.animation.legacy;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.pncases.PnCasesPlugin;
import static ru.privatenull.pncases.cases.animation.core.AbstractCaseAnimation.playSound;
import ru.privatenull.pncases.cases.animation.core.AnimationFrame;
import ru.privatenull.pncases.cases.animation.core.AnimationSession;
import ru.privatenull.pncases.cases.animation.core.AnimationTimeline;
import ru.privatenull.pncases.cases.animation.core.TimelineCaseAnimation;
import ru.privatenull.pncases.cases.animation.core.TimelineTask;
import ru.privatenull.pncases.cases.animation.effect.RewardLabel;
import ru.privatenull.pncases.cases.model.AnimationType;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pncases.support.compat.BukkitCompat.MaterialCompat;
import ru.privatenull.pncases.support.compat.BukkitCompat.ParticleCompat;
import ru.privatenull.pncases.support.entity.VisualEntity;

import java.util.ArrayList;
import java.util.List;

public final class LegacyCaseAnimation extends TimelineCaseAnimation {

    private final AnimationType type;

    public LegacyCaseAnimation(PnCasesPlugin plugin, AnimationType type) {
        super(plugin);
        this.type = type;
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
        Location centerBase = base.clone().add(0.5, 0.0, 0.5);
        VisualEntity display = VisualEntity.item(centerBase.clone().add(0.0, 1.35, 0.0), rewardVisual);
        RewardLabel label = RewardLabel.create(run, base.clone().add(0.5, 1.35, 0.5),
                resolveRewardName(reward, rewardVisual));
        VisualEntity prop = createMainProp(type, centerBase);
        List<VisualEntity> orbit = createOrbit(type, def, rewardVisual, centerBase);
        run.track(display.entity());
        if (prop != null) {
            run.track(prop.entity());
        }
        for (VisualEntity visual : orbit) {
            run.track(visual.entity());
        }

        AnimationTimeline timeline = AnimationTimeline.builder().phase("scene", 96).build();
        startTimeline(run, new TimelineTask(run, "legacy-main", timeline) {
            int tick;
            float yaw;

            @Override
            protected void render(AnimationFrame frame) {
                tick = frame.tick();
                if (tick == 1) {
                    run.protectReward(centerBase.clone().add(0.0, 1.55, 0.0));
                }
                yaw = (yaw + 7.5f) % 360.0f;

                double progress = Math.min(1.0, tick / 92.0);
                double eased = 1.0 - Math.pow(1.0 - progress, 2.0);
                double bob = Math.sin(tick * 0.22) * 0.10;
                Location rewardCenter = centerBase.clone().add(0.0, 1.15 + eased * 0.75 + bob, 0.0);
                updateMainProp(prop, centerBase, tick, progress, eased);
                updateOrbit(orbit, centerBase, tick, progress);
                display.teleport(rewardCenter);
                display.setRotation(yaw, 0.0f);
                if (label != null) label.showAbove(rewardCenter, 0.88);

                drawRing(world, base.clone().add(0.5, 1.20 + eased * 0.55, 0.5), tick, progress);

                if (tick % 18 == 0) {
                    playSound(world, base, soundForType(), 0.18f, 1.15f + (float) progress * 0.45f);
                }

            }

            @Override
            protected void onComplete() {
                Location rewardCenter = centerBase.clone().add(0.0, 1.90, 0.0);
                ParticleCompat.spawn(world, rewardCenter, new String[]{"FIREWORK", "FIREWORKS_SPARK"}, 8, 0.32, 0.22, 0.32, 0.03);
                ParticleCompat.spawn(world, rewardCenter, new String[]{"END_ROD"}, 6, 0.25, 0.22, 0.25, 0.025);
                playSound(world, base, Sound.ENTITY_PLAYER_LEVELUP, 0.28f, 1.45f);
                playSound(player, player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.24f, 1.55f);
            }
        });
    }

    private VisualEntity createMainProp(AnimationType type, Location centerBase) {
        return switch (type) {
            case ANVIL -> VisualEntity.block(centerBase.clone().add(0.0, 3.6, 0.0), Material.ANVIL);
            case DYNAMITE -> VisualEntity.block(centerBase.clone().add(-1.45, 1.75, -1.45), Material.TNT);
            case PORTAL -> VisualEntity.item(centerBase.clone().add(0.0, 2.05, 0.0), new ItemStack(Material.ENDER_EYE));
            case POISON -> VisualEntity.item(centerBase.clone().add(0.0, 1.65, 0.0), new ItemStack(Material.SLIME_BALL));
            case FORTUNE_RING, PILLAGER_RAID, AQUARIUM, MOB_HUNT, DRAGON, MIMIC, STORM_DUEL, TIME_DISTORTION, PRISM, STAR_CAROUSEL, NEW_YEAR_TREE, SANTA_SLEIGH, ICE_BLOCK, AUTUMN_MAPLE -> VisualEntity.item(centerBase.clone().add(0.0, 2.15, 0.0), new ItemStack(Material.NETHER_STAR));
        };
    }

    private List<VisualEntity> createOrbit(AnimationType type, CaseDefinition def, ItemStack rewardVisual, Location centerBase) {
        List<VisualEntity> visuals = new ArrayList<>();
        if (type != AnimationType.FORTUNE_RING) {
            return visuals;
        }

        List<ItemStack> source = new ArrayList<>();
        if (def.animationItems() != null) {
            for (ItemStack item : def.animationItems()) {
                if (item != null && !item.getType().isAir()) {
                    source.add(item.clone());
                }
            }
        }
        if (source.isEmpty()) {
            source.add(new ItemStack(Material.EMERALD));
            source.add(new ItemStack(Material.DIAMOND));
            source.add(new ItemStack(Material.GOLD_INGOT));
            source.add(new ItemStack(MaterialCompat.first("AMETHYST_SHARD", "NETHER_STAR")));
        }

        for (int i = 0; i < 6; i++) {
            ItemStack item = source.get(i % source.size()).clone();
            item.setAmount(1);
            VisualEntity visual = VisualEntity.item(centerBase.clone().add(0.0, 1.7, 0.0), item);
            visuals.add(visual);
        }
        visuals.add(VisualEntity.item(centerBase.clone().add(0.0, 2.15, 0.0), rewardVisual));
        return visuals;
    }

    private void updateMainProp(VisualEntity prop, Location centerBase, int tick, double progress, double eased) {
        if (prop == null) {
            return;
        }

        switch (type) {
            case ANVIL -> {
                double y = 3.8 - Math.min(2.75, eased * 3.2);
                if (progress > 0.72) {
                    y += Math.sin(tick * 0.45) * 0.035;
                }
                prop.teleport(centerBase.clone().add(0.0, y, 0.0));
                prop.setRotation((tick * 4.0f) % 360.0f, 0.0f);
            }
            case DYNAMITE -> {
                double fly = Math.min(1.0, tick / 54.0);
                double arc = Math.sin(fly * Math.PI) * 0.75;
                double x = -1.45 + 1.45 * fly;
                double z = -1.45 + 1.45 * fly;
                prop.teleport(centerBase.clone().add(x, 1.1 + arc, z));
                prop.setRotation((tick * 13.0f) % 360.0f, (tick * 6.0f) % 360.0f);
            }
            case FORTUNE_RING, PILLAGER_RAID, AQUARIUM, MOB_HUNT, STORM_DUEL -> {
                prop.teleport(centerBase.clone().add(0.0, 2.12 + Math.sin(tick * 0.16) * 0.08, 0.0));
                prop.setRotation((tick * 9.0f) % 360.0f, 0.0f);
            }
            default -> {
                prop.teleport(centerBase.clone().add(0.0, 1.9 + Math.sin(tick * 0.12) * 0.12, 0.0));
                prop.setRotation((tick * 6.5f) % 360.0f, 0.0f);
            }
        }
    }

    private void updateOrbit(List<VisualEntity> orbit, Location centerBase, int tick, double progress) {
        if (orbit.isEmpty()) {
            return;
        }

        int rewardIndex = orbit.size() - 1;
        double radius = 1.05 - Math.min(0.35, progress * 0.25);
        double speed = 0.20 - Math.min(0.10, progress * 0.08);
        for (int i = 0; i < orbit.size(); i++) {
            VisualEntity visual = orbit.get(i);
            if (i == rewardIndex) {
                visual.teleport(centerBase.clone().add(0.0, 2.05 + Math.sin(tick * 0.13) * 0.08, 0.0));
                visual.setRotation((tick * 7.0f) % 360.0f, 0.0f);
                continue;
            }

            double angle = tick * speed + (Math.PI * 2.0 * i) / rewardIndex;
            double wave = Math.sin(angle * 1.7) * 0.20;
            visual.teleport(centerBase.clone().add(Math.cos(angle) * radius, 1.75 + wave, Math.sin(angle) * radius));
            visual.setRotation((float) Math.toDegrees(angle), 0.0f);
        }
    }

    private void drawRing(World world, Location center, int tick, double progress) {
        double radius = 0.48 + progress * 0.55;
        double rotation = tick * 0.16;
        String[] primary = primaryParticle();
        String[] secondary = secondaryParticle();

        for (int i = 0; i < 8; i++) {
            double angle = rotation + (Math.PI * 2.0 * i) / 8.0;
            Location loc = center.clone().add(Math.cos(angle) * radius, Math.sin(angle * 1.3) * 0.14, Math.sin(angle) * radius);
            ParticleCompat.spawn(world, loc, i % 2 == 0 ? primary : secondary, 1, 0.01, 0.01, 0.01, 0.0);
        }
    }

    private String[] primaryParticle() {
        return switch (type) {
            case DYNAMITE -> new String[]{"FLAME"};
            case PORTAL -> new String[]{"PORTAL"};
            case POISON -> new String[]{"WITCH", "SPELL_WITCH"};
            case FORTUNE_RING, PILLAGER_RAID, AQUARIUM, MOB_HUNT, DRAGON, MIMIC, STORM_DUEL, TIME_DISTORTION, PRISM, STAR_CAROUSEL, NEW_YEAR_TREE, SANTA_SLEIGH, ICE_BLOCK, AUTUMN_MAPLE -> new String[]{"END_ROD"};
            case ANVIL -> new String[]{"CRIT"};
        };
    }

    private String[] secondaryParticle() {
        return switch (type) {
            case DYNAMITE -> new String[]{"SMOKE", "SMOKE_NORMAL"};
            case PORTAL -> new String[]{"REVERSE_PORTAL", "PORTAL"};
            case POISON -> new String[]{"ENCHANT", "ENCHANTMENT_TABLE"};
            case FORTUNE_RING, PILLAGER_RAID, AQUARIUM, MOB_HUNT, DRAGON, MIMIC, STORM_DUEL, TIME_DISTORTION, PRISM, STAR_CAROUSEL, NEW_YEAR_TREE, SANTA_SLEIGH, ICE_BLOCK, AUTUMN_MAPLE -> new String[]{"ELECTRIC_SPARK", "END_ROD"};
            case ANVIL -> new String[]{"CLOUD"};
        };
    }

    private Sound soundForType() {
        return switch (type) {
            case DYNAMITE -> Sound.ENTITY_TNT_PRIMED;
            case PORTAL -> Sound.ENTITY_ENDERMAN_TELEPORT;
            case POISON -> Sound.ENTITY_SPIDER_AMBIENT;
            case FORTUNE_RING, PILLAGER_RAID, AQUARIUM, MOB_HUNT, DRAGON, MIMIC, STORM_DUEL, TIME_DISTORTION, PRISM, STAR_CAROUSEL, NEW_YEAR_TREE, SANTA_SLEIGH, ICE_BLOCK, AUTUMN_MAPLE -> Sound.BLOCK_NOTE_BLOCK_PLING;
            case ANVIL -> Sound.BLOCK_ANVIL_USE;
        };
    }

    private static void safeRemove(VisualEntity visual) {
        if (visual != null) visual.remove();
    }
}
