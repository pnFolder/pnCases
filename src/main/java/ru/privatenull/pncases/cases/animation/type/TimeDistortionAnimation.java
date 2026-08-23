package ru.privatenull.pncases.cases.animation.type;

import static ru.privatenull.pncases.cases.animation.core.AbstractCaseAnimation.playSound;
import static ru.privatenull.pncases.cases.animation.type.TimeDistortionActors.*;
import static ru.privatenull.pncases.cases.animation.type.TimeDistortionEffects.*;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import ru.privatenull.pncases.PnCasesPlugin;
import ru.privatenull.pncases.cases.animation.core.AnimationFrame;
import ru.privatenull.pncases.cases.animation.core.AnimationSession;
import ru.privatenull.pncases.cases.animation.core.AnimationTimeline;
import ru.privatenull.pncases.cases.animation.core.TimelineCaseAnimation;
import ru.privatenull.pncases.cases.animation.core.TimelineTask;
import ru.privatenull.pncases.cases.animation.effect.RewardLabel;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.Reward;

/** The Timekeeper rewinds a destroyed case and confronts his own future echo. */
public final class TimeDistortionAnimation extends TimelineCaseAnimation {

    public TimeDistortionAnimation(PnCasesPlugin plugin) {
        super(plugin);
    }

    @Override
    protected void start(AnimationSession run) {
        Player player = run.player();
        World world = run.world();
        Location rawBase = run.base();
        CaseDefinition definition = run.definition();
        Reward reward = run.reward();
        if (player == null || world == null || rawBase == null) {
            run.complete();
            return;
        }

        AnimationTimeline timeline = AnimationTimeline.builder()
                .phase("summon", 34)
                .phase("rewind-case", 48)
                .phase("split", 26)
                .phase("paradox", 62)
                .phase("reverse", 40)
                .phase("return", 30)
                .phase("void", 20)
                .phase("reveal", 48)
                .build();

        Block caseBlock = rawBase.getBlock();
        Material caseMaterial = caseBlock.getType().isBlock() && !caseBlock.getType().isAir()
                ? caseBlock.getType() : Material.CHEST;
        Location caseCenter = caseBlock.getLocation().add(0.5, 0.50, 0.5);
        Location dialCenter = caseCenter.clone().add(0.0, 1.30, 0.0);
        Vector right = horizontalRight(dialCenter, player.getLocation());
        Vector towardViewer = player.getLocation().toVector().subtract(caseCenter.toVector()).setY(0.0);
        if (towardViewer.lengthSquared() < 0.001) towardViewer.setZ(1.0);
        towardViewer.normalize();
        Location keeperStage = caseCenter.clone().add(right.clone().multiply(1.72))
                .subtract(towardViewer.clone().multiply(0.18));
        Location pastSide = caseCenter.clone().subtract(right.clone().multiply(1.68));
        Location futureSide = caseCenter.clone().add(right.clone().multiply(1.68));

        run.hide(caseBlock, Material.AIR);
        List<BlockDisplay> fragments = shards(run, world, caseCenter, caseMaterial);
        ArmorStand keeper = timekeeper(run, world, keeperStage.clone().subtract(0.0, 4.5, 0.0), false);
        ArmorStand future = timekeeper(run, world, futureSide.clone().subtract(0.0, 5.0, 0.0), true);
        ItemStack rewardVisual = resolveRewardVisual(reward, definition);
        ItemDisplay rewardDisplay = item(run, world, caseCenter.clone().subtract(0.0, 6.0, 0.0),
                rewardVisual, 0.01f);
        RewardLabel rewardLabel = RewardLabel.create(run, caseCenter,
                resolveRewardName(reward, rewardVisual));

        startTimeline(run, new TimelineTask(run, "timekeeper-cutscene", timeline) {
            private int tick;
            private boolean splitStarted;
            private boolean cleared;

            @Override
            protected void render(AnimationFrame frame) {
                tick = frame.tick();
                switch (frame.phase()) {
                    case "summon" -> summon();
                    case "rewind-case" -> rewindCase();
                    case "split" -> split();
                    case "paradox" -> paradox();
                    case "reverse" -> reverse();
                    case "return" -> returnKeeper();
                    case "void" -> voidPhase();
                    case "reveal" -> reveal();
                    default -> throw new IllegalStateException("Unknown Timekeeper phase " + frame.phase());
                }
            }

            @Override
            protected void onComplete() {
                Location rewardLocation = caseCenter.clone().add(0.0, 1.52, 0.0);
                playSound(world, rewardLocation, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.22f, 1.18f);
                playSound(world, rewardLocation, Sound.ENTITY_PLAYER_LEVELUP, 0.22f, 1.58f);
                playSound(player, player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.18f, 1.58f);
            }

            private void summon() {
                int local = local("summon");
                double progress = ease(timeline.progress("summon", tick));
                Location entry = keeperStage.clone().subtract(0.0, (1.0 - progress) * 3.2, 0.0);
                place(keeper, entry, caseCenter);
                idlePose(keeper, false, Math.sin(local * 0.24) * 4.0);
                arrivalGate(world, keeperStage, local, progress);
                dial(world, dialCenter, right, local * 0.055, 0.42 + progress * 0.88, tick);

                if (local == 1) {
                    playSound(world, keeperStage, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.15f, 0.58f);
                    playSound(world, keeperStage, Sound.BLOCK_PORTAL_TRIGGER, 0.12f, 1.62f);
                }
                if (local == 14) {
                    world.spawnParticle(Particle.FLASH, entry.clone().add(0.0, 1.1, 0.0),
                            1, 0.0, 0.0, 0.0, 0.0);
                    playSound(world, entry, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.18f, 0.82f);
                }
                if (local % 7 == 0) {
                    playSound(world, entry, Sound.BLOCK_NOTE_BLOCK_HAT,
                            0.09f, 0.72f + (float) progress * 0.65f);
                }
            }

            private void rewindCase() {
                int local = local("rewind-case");
                double progress = ease(timeline.progress("rewind-case", tick));
                place(keeper, keeperStage, caseCenter);
                rewindPose(keeper, progress, true);
                dial(world, dialCenter, right, 1.87 - local * (0.11 + progress * 0.06), 1.30, tick);

                for (int i = 0; i < fragments.size(); i++) {
                    Location location = lerp(scatteredShard(caseCenter, i), shardTarget(caseCenter, i), progress);
                    move(fragments.get(i), location, 0.43f, (1.0 - progress) * (i % 2 == 0 ? 5.2 : -5.2));
                    if (local % 2 == 0) rewindTrail(world, location, i, tick);
                }

                int beat = Math.max(3, 9 - (int) (progress * 6.0));
                if (local % beat == 0) {
                    playSound(world, caseCenter, Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                            0.10f, 1.82f - (float) progress * 1.02f);
                }
                if (local == 1) playSound(world, caseCenter, Sound.ENTITY_ENDERMAN_TELEPORT, 0.15f, 0.58f);
                if (local == 42) playSound(world, caseCenter, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.18f, 0.72f);
            }

            private void split() {
                int local = local("split");
                double progress = ease(timeline.progress("split", tick));
                Location keeperLocation = lerp(keeperStage, pastSide, progress);
                Location futureLocation = lerp(caseCenter.clone().subtract(0.0, 3.2, 0.0), futureSide, progress);
                place(keeper, keeperLocation, caseCenter);
                place(future, futureLocation, caseCenter);
                idlePose(keeper, false, Math.sin(local * 0.34) * 4.0);
                idlePose(future, true, -Math.sin(local * 0.34) * 4.0);
                dial(world, dialCenter, right, -local * 0.12, 1.30 + Math.sin(local * 0.3) * 0.06, tick);

                if (!splitStarted) {
                    splitStarted = true;
                    temporalSkip(world, keeperStage, futureSide);
                    playSound(world, caseCenter, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.20f, 1.58f);
                    playSound(world, caseCenter, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.18f, 0.72f);
                }
                if (local == 18) {
                    playSound(world, futureLocation, Sound.BLOCK_CONDUIT_ACTIVATE, 0.14f, 1.72f);
                }
            }

            private void paradox() {
                int local = local("paradox");
                double progress = timeline.progress("paradox", tick);
                int jump = local / 16;
                double angle = progress * Math.PI * 2.0 + jump * Math.PI / 2.0;
                Location pastLocation = orbit(angle + Math.PI, 1.70, local);
                Location futureLocation = orbit(-angle, 1.70, -local);
                place(keeper, pastLocation, caseCenter);
                place(future, futureLocation, caseCenter);
                paradoxPose(keeper, false, progress);
                paradoxPose(future, true, progress);
                paradoxBeam(world, pastLocation, futureLocation, dialCenter, tick);
                dial(world, dialCenter, right, Math.sin(local * 0.16) * 1.35, 1.28, tick);

                double tug = 0.5 + Math.sin(progress * Math.PI * 7.0) * 0.5;
                for (int i = 0; i < fragments.size(); i++) {
                    Location location = lerp(shardTarget(caseCenter, i), scatteredShard(caseCenter, i), tug * 0.48);
                    move(fragments.get(i), location, 0.43f, tug * (i % 2 == 0 ? 2.2 : -2.2));
                }

                if (local == 16 || local == 32 || local == 48) {
                    temporalSkip(world, pastLocation, futureLocation);
                    playSound(world, caseCenter, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.16f,
                            0.72f + local / 64.0f);
                    playSound(world, caseCenter, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.14f,
                            0.82f + local / 52.0f);
                }
                if (local % 8 == 0) {
                    playSound(world, dialCenter, Sound.BLOCK_NOTE_BLOCK_HAT, 0.09f,
                            local % 16 == 0 ? 0.62f : 1.72f);
                }
            }

            private void reverse() {
                int local = local("reverse");
                double progress = ease(timeline.progress("reverse", tick));
                Location pastStart = orbit(Math.PI * 3.5, 1.70, 62);
                Location futureStart = orbit(-Math.PI * 2.5, 1.70, -62);
                Location merge = caseCenter.clone().subtract(towardViewer.clone().multiply(0.28));
                Location pastLocation = lerp(pastStart, merge.clone().subtract(right.clone().multiply(0.14)), progress);
                Location futureLocation = lerp(futureStart, merge.clone().add(right.clone().multiply(0.14)), progress);
                place(keeper, pastLocation, caseCenter);
                place(future, futureLocation, caseCenter);
                mergePose(keeper, false, progress);
                mergePose(future, true, progress);
                mergeField(world, dialCenter, local, progress);
                dial(world, dialCenter, right, 4.2 - local * 0.20, 1.28 * (1.0 - progress * 0.58), tick);

                for (int i = 0; i < fragments.size(); i++) {
                    double rewind = 0.48 * (1.0 - progress);
                    move(fragments.get(i), lerp(shardTarget(caseCenter, i), scatteredShard(caseCenter, i), rewind),
                            0.43f, rewind * (i % 2 == 0 ? 2.2 : -2.2));
                }
                if (local % 5 == 0) {
                    playSound(world, dialCenter, Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                            0.10f, 1.86f - (float) progress * 1.18f);
                }
                if (local == 35) {
                    world.spawnParticle(Particle.FLASH, merge.clone().add(0.0, 1.0, 0.0),
                            1, 0.0, 0.0, 0.0, 0.0);
                    playSound(world, merge, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.20f, 0.54f);
                }
            }

            private void returnKeeper() {
                int local = local("return");
                double progress = ease(timeline.progress("return", tick));
                if (local == 1) future.remove();
                Location returned = caseCenter.clone().subtract(towardViewer.clone().multiply(0.28));
                Location exit = keeperStage.clone().subtract(0.0, 3.4, 0.0);
                Location keeperLocation = lerp(returned, exit, progress);
                place(keeper, keeperLocation, caseCenter);
                rewindPose(keeper, 1.0 - progress, false);

                for (int i = 0; i < fragments.size(); i++) {
                    Location target = shardTarget(caseCenter, i);
                    Location destination = dialCenter.clone().add(direction(i).multiply(0.08));
                    move(fragments.get(i), lerp(target, destination, progress),
                            (float) Math.max(0.01, 0.43 * (1.0 - progress)), progress * 7.0);
                }
                if (local < 23) dial(world, dialCenter, right, -local * 0.18,
                        Math.max(0.18, 0.74 * (1.0 - progress)), tick);
                if (local % 6 == 0) playSound(world, keeperLocation,
                        Sound.BLOCK_NOTE_BLOCK_HAT, 0.09f, 1.72f - (float) progress * 1.02f);
                if (local == 24) {
                    world.spawnParticle(Particle.FLASH, keeperLocation.clone().add(0.0, 1.0, 0.0),
                            1, 0.0, 0.0, 0.0, 0.0);
                    playSound(world, keeperLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 0.16f, 0.52f);
                }
            }

            private void voidPhase() {
                if (cleared) return;
                cleared = true;
                keeper.remove();
                future.remove();
                fragments.forEach(BlockDisplay::remove);
                rewardLabel.hide();
                playSound(world, caseCenter, Sound.BLOCK_BEACON_DEACTIVATE, 0.14f, 0.50f);
            }

            private void reveal() {
                int local = local("reveal");
                double progress = ease(timeline.progress("reveal", tick));
                Location rewardLocation = caseCenter.clone().add(0.0, 0.18 + progress * 1.34, 0.0);
                move(rewardDisplay, rewardLocation, (float) (0.18 + progress * 0.86),
                        progress * Math.PI * 1.20);
                rewardLabel.showAbove(rewardLocation, 0.94);
                if (local == 1) {
                    playSound(world, rewardLocation, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.17f, 1.24f);
                    playSound(world, rewardLocation, Sound.BLOCK_BEACON_POWER_SELECT, 0.14f, 1.68f);
                }
                if (local == 18 || local == 34) {
                    playSound(world, rewardLocation, Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                            0.09f, local == 18 ? 1.38f : 1.72f);
                }
            }

            private Location orbit(double angle, double radius, int local) {
                return caseCenter.clone().add(Math.cos(angle) * radius,
                        Math.abs(Math.sin(local * 0.21)) * 0.08, Math.sin(angle) * radius);
            }

            private int local(String phase) {
                return tick - timeline.start(phase) + 1;
            }
        });
    }
}
