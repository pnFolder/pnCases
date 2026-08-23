package ru.privatenull.pncases.cases.animation.type;

import static ru.privatenull.pncases.cases.animation.effect.AnimationEffects.Kind.DUST;
import static ru.privatenull.pncases.cases.animation.effect.AnimationEffects.Kind.ENCHANT;
import static ru.privatenull.pncases.cases.animation.effect.AnimationEffects.Kind.EXPLOSION;
import static ru.privatenull.pncases.cases.animation.effect.AnimationEffects.Kind.FIREWORK;
import static ru.privatenull.pncases.cases.animation.effect.AnimationEffects.Kind.TOTEM;
import static ru.privatenull.pncases.cases.animation.effect.AnimationEffects.particle;
import static ru.privatenull.pncases.cases.animation.type.StormDuelActors.*;
import static ru.privatenull.pncases.cases.animation.type.StormDuelEffects.*;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;
import ru.privatenull.pncases.PnCasesPlugin;
import ru.privatenull.pncases.cases.animation.effect.AnimationParticles;
import ru.privatenull.pncases.cases.animation.core.AnimationSession;
import ru.privatenull.pncases.cases.animation.core.AnimationFrame;
import ru.privatenull.pncases.cases.animation.core.AnimationTimeline;
import ru.privatenull.pncases.cases.animation.core.TimelineCaseAnimation;
import ru.privatenull.pncases.cases.animation.core.TimelineTask;
import ru.privatenull.pncases.cases.animation.effect.RewardLabel;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.Reward;

/** Two armed warriors clash over the case until a lightning blast reveals the reward. */
public final class StormDuelAnimation extends TimelineCaseAnimation {

public StormDuelAnimation(PnCasesPlugin plugin) {
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
                .phase("summon", 34)
                .phase("approach", 30)
                .phase("duel", 72)
                .phase("clash", 24)
                .phase("blast", 20)
                .phase("reveal", 52)
                .build();

        Location center = rawBase.getBlock().getLocation().add(0.5, 1.02, 0.5);
        Vector forward = horizontal(player.getLocation().toVector().subtract(center.toVector()));
        Vector right = new Vector(-forward.getZ(), 0.0, forward.getX()).normalize();
        Location leftStart = center.clone().subtract(right.clone().multiply(3.25));
        Location rightStart = center.clone().add(right.clone().multiply(3.25));
        Location leftClash = center.clone().subtract(right.clone().multiply(0.82));
        Location rightClash = center.clone().add(right.clone().multiply(0.82));

        ArmorStand left = warrior(run, world, leftStart.clone().subtract(0.0, 5.0, 0.0), true);
        ArmorStand rightWarrior = warrior(run, world, rightStart.clone().subtract(0.0, 5.0, 0.0), false);
        clearEquipment(left);
        clearEquipment(rightWarrior);

        ItemDisplay leftWeapon = item(run, world, center.clone().subtract(0.0, 5.0, 0.0),
                new ItemStack(Material.DIAMOND_SWORD), 0.01f);
        ItemDisplay rightWeapon = item(run, world, center.clone().subtract(0.0, 5.0, 0.0),
                new ItemStack(Material.NETHERITE_AXE), 0.01f);

        ItemStack rewardVisual = resolveRewardVisual(reward, definition);
        ItemDisplay rewardDisplay = item(run, world, center.clone().subtract(0.0, 5.0, 0.0), rewardVisual, 0.01f);
        RewardLabel rewardLabel = RewardLabel.create(run, center, resolveRewardName(reward, rewardVisual));

        startTimeline(run, new TimelineTask(run, "storm-duel-main", timeline) {
            private int tick;
            private boolean appeared;
            private boolean blasted;

            @Override
            protected void render(AnimationFrame frame) {
                tick = frame.tick();
                if (timeline.in("summon", tick)) summon();
                else if (timeline.in("approach", tick)) approach();
                else if (timeline.in("duel", tick)) duel();
                else if (timeline.in("clash", tick)) clash();
                else if (timeline.in("blast", tick)) blast();
                else if (timeline.in("reveal", tick)) reveal();
            }

            @Override
            protected void onComplete() {
                finalBurst();
            }

            private void summon() {
                double progress = timeline.progress("summon", tick);
                int local = tick - timeline.start("summon") + 1;
                Location storm = center.clone().add(0.0, 2.75, 0.0);
                Location leftSeal = leftStart.clone().add(0.0, 0.05, 0.0);
                Location rightSeal = rightStart.clone().add(0.0, 0.05, 0.0);

                stormCanopy(world, storm, progress, local);
                summonSeal(leftSeal, BLUE, local, progress, false);
                summonSeal(rightSeal, VIOLET, -local, progress, true);

                if (local <= 16) {
                    summonColumn(leftSeal, BLUE, local, progress, false);
                    summonColumn(rightSeal, VIOLET, local, progress, true);
                }

                if (local == 1) {
                    playSound(world, center, Sound.BLOCK_BEACON_AMBIENT, 0.10f, 0.62f);
                    playSound(world, center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.12f, 0.72f);
                }
                if (local == 7) {
                    world.strikeLightningEffect(center);
                    playSound(world, center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.28f, 1.18f);
                    playSound(world, center, Sound.ITEM_TRIDENT_THUNDER, 0.20f, 1.32f);
                    playSound(world, center, Sound.BLOCK_CONDUIT_ACTIVATE, 0.13f, 1.48f);
                }
                if (local >= 9 && local <= 16) {
                    double channel = ease((local - 8) / 8.0);
                    Location source = center.clone().add(0.0, 1.65 + channel * 0.70, 0.0);
                    lightningArc(source, leftSeal.clone().add(0.0, 0.45 + channel * 1.25, 0.0),
                            BLUE, local * 3);
                    lightningArc(source, rightSeal.clone().add(0.0, 0.45 + channel * 1.25, 0.0),
                            VIOLET, local * 3 + 11);
                    electricRing(world, center.clone().add(0.0, 0.22 + channel * 0.38, 0.0),
                            0.42 + channel * 0.72, local * 2);
                }
                if (local == 14) {
                    world.strikeLightningEffect(leftStart);
                    world.strikeLightningEffect(rightStart);
                    world.spawnParticle(Particle.FLASH, leftStart.clone().add(0.0, 1.0, 0.0),
                            1, 0.02, 0.10, 0.02, 0.0);
                    world.spawnParticle(Particle.FLASH, rightStart.clone().add(0.0, 1.0, 0.0),
                            1, 0.02, 0.10, 0.02, 0.0);
                    playSound(world, leftStart, Sound.ITEM_TRIDENT_THUNDER, 0.15f, 1.62f);
                    playSound(world, rightStart, Sound.ITEM_TRIDENT_THUNDER, 0.15f, 1.38f);
                }
                if (local == 17 && !appeared) {
                    appeared = true;
                    equip(left, true);
                    equip(rightWarrior, false);
                    placeWarrior(left, leftStart, rightStart);
                    placeWarrior(rightWarrior, rightStart, leftStart);
                    world.spawnParticle(Particle.CLOUD, leftStart.clone().add(0.0, 0.8, 0.0), 10, 0.28, 0.55, 0.28, 0.02);
                    world.spawnParticle(Particle.CLOUD, rightStart.clone().add(0.0, 0.8, 0.0), 10, 0.28, 0.55, 0.28, 0.02);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, leftStart.clone().add(0.0, 0.72, 0.0),
                            8, 0.24, 0.52, 0.24, 0.015);
                    world.spawnParticle(Particle.REVERSE_PORTAL, rightStart.clone().add(0.0, 0.82, 0.0),
                            10, 0.26, 0.55, 0.26, 0.025);
                    dustBurst(world, leftStart.clone().add(0.0, 1.0, 0.0), BLUE, 12);
                    dustBurst(world, rightStart.clone().add(0.0, 1.0, 0.0), VIOLET, 12);
                    playSound(world, leftStart, Sound.ITEM_ARMOR_EQUIP_DIAMOND, 0.16f, 1.08f);
                    playSound(world, rightStart, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.18f, 0.82f);
                    playSound(world, center, Sound.BLOCK_BEACON_POWER_SELECT, 0.13f, 1.42f);
                }
                if (appeared) {
                    readyPose(left, true, 0.05 * Math.sin(tick * 0.45));
                    readyPose(rightWarrior, false, 0.05 * Math.sin(tick * 0.45 + Math.PI));
                    double fade = 1.0 - ease((local - 17) / 17.0);
                    summonAura(leftStart, BLUE, local, fade, false);
                    summonAura(rightStart, VIOLET, local, fade, true);
                    if (local == 24) {
                        playSound(world, leftStart, Sound.ITEM_SHIELD_BLOCK, 0.12f, 1.26f);
                        playSound(world, rightStart, Sound.BLOCK_CHAIN_PLACE, 0.11f, 0.86f);
                    }
                }
            }

            private void approach() {
                double progress = ease(timeline.progress("approach", tick));
                Location leftLocation = lerp(leftStart, leftClash, progress);
                Location rightLocation = lerp(rightStart, rightClash, progress);
                double step = Math.abs(Math.sin(progress * Math.PI * 6.0)) * 0.07;
                leftLocation.add(0.0, step, 0.0);
                rightLocation.add(0.0, step, 0.0);
                placeWarrior(left, leftLocation, rightLocation);
                placeWarrior(rightWarrior, rightLocation, leftLocation);
                walkingPose(left, true, progress);
                walkingPose(rightWarrior, false, progress);

                int local = tick - timeline.start("approach");
                if (local % 8 == 1) {
                    playSound(world, center, Sound.BLOCK_STONE_STEP, 0.10f, 0.75f + (float) progress * 0.15f);
                    world.spawnParticle(Particle.CLOUD, leftLocation, 2, 0.13, 0.03, 0.13, 0.005);
                    world.spawnParticle(Particle.CLOUD, rightLocation, 2, 0.13, 0.03, 0.13, 0.005);
                }
            }

            private void duel() {
                int local = tick - timeline.start("duel");
                if (local < 18) {
                    exchange(true, local / 17.0, false);
                } else if (local < 36) {
                    exchange(false, (local - 18) / 17.0, false);
                } else if (local < 45) {
                    exchange(true, (local - 36) / 8.0, true);
                } else if (local < 54) {
                    exchange(false, (local - 45) / 8.0, true);
                } else {
                    double charge = ease((local - 54) / 17.0);
                    double circle = Math.sin(charge * Math.PI) * 0.24;
                    Location leftLocation = leftClash.clone().add(forward.clone().multiply(circle));
                    Location rightLocation = rightClash.clone().subtract(forward.clone().multiply(circle));
                    placeWarrior(left, leftLocation, rightLocation);
                    placeWarrior(rightWarrior, rightLocation, leftLocation);
                    windupPose(left, true, charge);
                    windupPose(rightWarrior, false, charge);
                    electricRing(world, center.clone().add(0.0, 1.18, 0.0), 0.34 + charge * 0.42, tick);
                    slashTrail(leftLocation, true, charge);
                    slashTrail(rightLocation, false, charge);
                }

                if (local == 7 || local == 25 || local == 39 || local == 48) {
                    boolean blueHit = local == 7 || local == 39;
                    Location impact = center.clone().add(0.0, blueHit ? 1.40 : 1.26, 0.0)
                            .add(forward.clone().multiply(local >= 36 ? (blueHit ? 0.12 : -0.12) : 0.0));
                    metalHit(impact, blueHit ? BLUE : VIOLET);
                }
                if (local == 2 || local == 20 || local == 37 || local == 46) {
                    boolean blueSwing = local == 2 || local == 37;
                    Location attacker = blueSwing ? left.getLocation() : rightWarrior.getLocation();
                    playSound(world, attacker, Sound.ITEM_TRIDENT_THROW,
                            local >= 36 ? 0.17f : 0.13f, local >= 36 ? 1.52f : 1.24f);
                }
                if (local == 57 || local == 64) {
                    playSound(world, center, Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                            0.17f, 1.18f + (local - 57) * 0.045f);
                    playSound(world, center, Sound.ITEM_TRIDENT_RETURN,
                            0.12f, 1.36f + (local - 57) * 0.035f);
                }
            }

            private void exchange(boolean leftAttacks, double rawProgress, boolean fast) {
                double progress = Math.max(0.0, Math.min(1.0, rawProgress));
                double lunge = Math.sin(progress * Math.PI);
                double attackDistance = fast ? 0.34 : 0.28;
                double recoilDistance = fast ? 0.20 : 0.14;

                Location leftLocation = leftClash.clone();
                Location rightLocation = rightClash.clone();
                if (leftAttacks) {
                    leftLocation.add(right.clone().multiply(lunge * attackDistance));
                    rightLocation.add(right.clone().multiply(lunge * recoilDistance));
                } else {
                    rightLocation.subtract(right.clone().multiply(lunge * attackDistance));
                    leftLocation.subtract(right.clone().multiply(lunge * recoilDistance));
                }

                double sideStep = Math.sin(progress * Math.PI * 2.0) * (fast ? 0.10 : 0.06);
                leftLocation.add(forward.clone().multiply(sideStep));
                rightLocation.subtract(forward.clone().multiply(sideStep));
                placeWarrior(left, leftLocation, rightLocation);
                placeWarrior(rightWarrior, rightLocation, leftLocation);

                double slash = slashProgress(progress);
                if (leftAttacks) {
                    attackPose(left, true, slash);
                    recoilPose(rightWarrior, false, lunge, fast);
                    slashTrail(leftLocation, true, slash);
                } else {
                    attackPose(rightWarrior, false, slash);
                    recoilPose(left, true, lunge, fast);
                    slashTrail(rightLocation, false, slash);
                }

                if (lunge > 0.72) {
                    Location feet = (leftAttacks ? leftLocation : rightLocation).clone().add(0.0, 0.03, 0.0);
                    world.spawnParticle(Particle.CLOUD, feet, fast ? 2 : 1, 0.14, 0.02, 0.14, 0.006);
                }
            }

            private void slashTrail(Location fighter, boolean blue, double progress) {
                double direction = blue ? 1.0 : -1.0;
                Location shoulder = fighter.clone().add(0.0, 1.45, 0.0);
                Location from = shoulder.clone()
                        .add(right.clone().multiply(direction * (0.34 + progress * 0.18)))
                        .add(forward.clone().multiply(direction * (0.42 - progress * 0.82)))
                        .add(0.0, 0.28 - progress * 0.36, 0.0);
                Location to = shoulder.clone()
                        .add(right.clone().multiply(direction * (0.58 + progress * 0.22)))
                        .add(forward.clone().multiply(direction * (0.18 - progress * 0.36)))
                        .add(0.0, 0.12 - progress * 0.18, 0.0);
                dustLine(world, from, to, blue ? BLUE : VIOLET, 0.13);
                world.spawnParticle(blue ? Particle.SOUL_FIRE_FLAME : particle(ENCHANT), to,
                        1, 0.045, 0.045, 0.045, 0.01);
                if (progress > 0.42 && progress < 0.88) {
                    world.spawnParticle(Particle.SWEEP_ATTACK, to, 1, 0.03, 0.03, 0.03, 0.0);
                }
            }

            private void clash() {
                double progress = timeline.progress("clash", tick);
                double compression = Math.sin(Math.min(1.0, progress * 1.45) * Math.PI * 0.5) * 0.14;
                Location leftLocation = leftClash.clone().add(right.clone().multiply(compression));
                Location rightLocation = rightClash.clone().subtract(right.clone().multiply(compression));
                placeWarrior(left, leftLocation, rightLocation);
                placeWarrior(rightWarrior, rightLocation, leftLocation);
                crossedPose(left, true, progress);
                crossedPose(rightWarrior, false, progress);
                slashTrail(leftLocation, true, 0.72 + Math.sin(progress * Math.PI * 5.0) * 0.08);
                slashTrail(rightLocation, false, 0.72 + Math.sin(progress * Math.PI * 5.0) * 0.08);

                Location impact = center.clone().add(0.0, 1.34, 0.0);
                electricRing(world, impact, 0.28 + progress * 0.68, tick * 2);
                if (tick == timeline.start("clash")) metalHit(impact, BLUE);
                if ((tick - timeline.start("clash")) % 6 == 0) {
                    world.spawnParticle(Particle.ELECTRIC_SPARK, impact, 7, 0.20, 0.18, 0.20, 0.08);
                    world.spawnParticle(Particle.END_ROD, impact, 3, 0.15, 0.15, 0.15, 0.025);
                    playSound(world, impact, Sound.BLOCK_CHAIN_HIT, 0.15f, 0.92f + (float) progress * 0.46f);
                    playSound(world, impact, Sound.ITEM_SHIELD_BLOCK, 0.10f, 1.18f + (float) progress * 0.35f);
                }
            }

            private void blast() {
                double progress = ease(timeline.progress("blast", tick));
                if (!blasted) {
                    blasted = true;
                    run.hide(rawBase.getBlock(), Material.AIR);
                    world.strikeLightningEffect(center.clone().add(0.0, 0.35, 0.0));
                    world.spawnParticle(particle(EXPLOSION), center.clone().add(0.0, 1.15, 0.0), 3, 0.32, 0.32, 0.32, 0.02);
                    world.spawnParticle(Particle.FLASH, center.clone().add(0.0, 1.20, 0.0), 2, 0.08, 0.08, 0.08, 0.0);
                    world.spawnParticle(Particle.SONIC_BOOM, center.clone().add(0.0, 1.18, 0.0), 1, 0.0, 0.0, 0.0, 0.0);
                    world.spawnParticle(particle(TOTEM), center.clone().add(0.0, 1.12, 0.0),
                            22, 0.45, 0.52, 0.45, 0.10);
                    dustBurst(world, center.clone().add(0.0, 1.20, 0.0), BLUE, 20);
                    dustBurst(world, center.clone().add(0.0, 1.20, 0.0), VIOLET, 20);
                    playSound(world, center, Sound.ITEM_TRIDENT_THUNDER, 0.30f, 0.82f);
                    playSound(world, center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.27f, 1.24f);
                    playSound(world, center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.17f, 1.46f);
                    clearHands(left);
                    clearHands(rightWarrior);
                }

                Location leftLocation = leftClash.clone()
                        .subtract(right.clone().multiply(progress * 3.15))
                        .add(0.0, Math.sin(progress * Math.PI) * 1.10 - progress * 0.42, 0.0);
                Location rightLocation = rightClash.clone()
                        .add(right.clone().multiply(progress * 3.15))
                        .add(0.0, Math.sin(progress * Math.PI) * 1.10 - progress * 0.42, 0.0);
                placeWarrior(left, leftLocation, rightLocation);
                placeWarrior(rightWarrior, rightLocation, leftLocation);
                knockedPose(left, true, progress);
                knockedPose(rightWarrior, false, progress);

                Location bladeLeft = center.clone().add(0.0, 1.30, 0.0)
                        .subtract(right.clone().multiply(0.35 + progress * 2.35))
                        .add(forward.clone().multiply(0.28))
                        .add(0.0, Math.sin(progress * Math.PI) * 1.35, 0.0);
                Location bladeRight = center.clone().add(0.0, 1.30, 0.0)
                        .add(right.clone().multiply(0.35 + progress * 2.35))
                        .subtract(forward.clone().multiply(0.28))
                        .add(0.0, Math.sin(progress * Math.PI) * 1.35, 0.0);
                leftWeapon.teleport(bladeLeft);
                rightWeapon.teleport(bladeRight);
                leftWeapon.setRotation((float) (tick * 29.0), (float) (tick * 17.0));
                rightWeapon.setRotation((float) (-tick * 31.0), (float) (tick * 19.0));
                setScale(leftWeapon, 0.78f);
                setScale(rightWeapon, 0.78f);
            }

            private void reveal() {
                double progress = timeline.progress("reveal", tick);
                if (tick == timeline.start("reveal")) {
                    left.remove();
                    rightWarrior.remove();
                    leftWeapon.remove();
                    rightWeapon.remove();
                    playSound(world, center, Sound.BLOCK_CONDUIT_ACTIVATE, 0.18f, 1.38f);
                }

                double rise = ease(Math.min(1.0, progress / 0.62));
                Location rewardLocation = center.clone().add(0.0, 0.18 + rise * 1.42 + Math.sin(tick * 0.15) * 0.05, 0.0);
                rewardDisplay.teleport(rewardLocation);
                rewardDisplay.setRotation((tick * 8.5f) % 360.0f, 0.0f);
                setScale(rewardDisplay, (float) Math.min(1.0, 0.08 + progress * 1.30));
                if (rewardLabel != null && progress > 0.25) rewardLabel.showAbove(rewardLocation, 0.88);

                electricRing(world, center.clone().add(0.0, 0.24 + rise * 0.52, 0.0), 0.38 + rise * 0.72, tick);
                if (tick == timeline.end("reveal") - 10) {
                    playSound(world, rewardLocation, Sound.ENTITY_PLAYER_LEVELUP, 0.22f, 1.52f);
                }
            }

            private void metalHit(Location location, Particle.DustOptions color) {
                world.spawnParticle(Particle.SWEEP_ATTACK, location, 1, 0.03, 0.03, 0.03, 0.0);
                world.spawnParticle(Particle.ELECTRIC_SPARK, location, 9, 0.22, 0.18, 0.22, 0.12);
                world.spawnParticle(Particle.END_ROD, location, 4, 0.16, 0.14, 0.16, 0.025);
                dustBurst(world, location, color, 8);
                playSound(world, location, Sound.BLOCK_CHAIN_HIT, 0.18f, 1.34f);
                playSound(world, location, Sound.ITEM_TRIDENT_HIT, 0.15f, 1.12f);
                playSound(world, location, Sound.ITEM_SHIELD_BLOCK, 0.11f, 1.46f);
            }

            private void finalBurst() {
                Location location = rewardDisplay.getLocation();
                world.spawnParticle(Particle.END_ROD, location, 10, 0.34, 0.32, 0.34, 0.025);
                world.spawnParticle(particle(FIREWORK), location, 7, 0.30, 0.28, 0.30, 0.025);
                playSound(world, location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.22f, 1.20f);
            }

        });
    }
}
