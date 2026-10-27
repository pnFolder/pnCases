package ru.privatenull.pncases.cases.animation.type;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.util.Vector;
import ru.privatenull.pncases.PnCasesPlugin;
import ru.privatenull.pncases.cases.animation.core.AnimationFrame;
import ru.privatenull.pncases.cases.animation.core.AnimationSession;
import ru.privatenull.pncases.cases.animation.core.AnimationTimeline;
import ru.privatenull.pncases.cases.animation.core.TimelineCaseAnimation;
import ru.privatenull.pncases.cases.animation.core.TimelineTask;
import ru.privatenull.pncases.cases.animation.effect.AnimationEffects;
import ru.privatenull.pncases.cases.animation.effect.AnimationEffects;
import ru.privatenull.pncases.cases.animation.effect.RewardOrbit;

/** Low horizontal carousel with white star trails; the center stays free of particles. */
public final class StarCarouselAnimation extends TimelineCaseAnimation {

    public StarCarouselAnimation(PnCasesPlugin plugin) {
        super(plugin);
    }

    @Override
    protected void start(AnimationSession run) {
        Location base = run.base().getBlock().getLocation().add(0.5, 0.5, 0.5);
        Vector toward = RewardOrbit.towardViewer(run, base);
        Vector right = new Vector(toward.getZ(), 0.0, -toward.getX());
        Location center = base.clone().add(0.0, 0.22, 0.0);
        Location result = base.clone().add(0.0, 1.55, 0.0);
        RewardOrbit ring = new RewardOrbit(run, base, reward -> resolveRewardVisual(reward, run.definition()),
                reward -> resolveRewardName(reward, resolveRewardVisual(reward, run.definition())), false);
        run.hide(base.getBlock(), Material.AIR);

        AnimationTimeline timeline = AnimationTimeline.builder()
                .phase("form", 28).phase("spin", 136).phase("select", 28)
                .phase("gather", 36).phase("reward", 48).build();
        double stop = ring.stopRotation(3);
        Particle stars = AnimationEffects.particle(AnimationEffects.Kind.FIREWORK);

        startTimeline(run, new TimelineTask(run, "star-carousel", timeline) {
            private Location selectedStart;
            private int lastNote = -1;

            @Override
            protected void render(AnimationFrame frame) {
                switch (frame.phase()) {
                    case "form", "spin" -> orbit(frame);
                    case "select" -> {
                        ring.select(frame.progress());
                        if (frame.phaseStart()) {
                            selectedStart = ring.winnerPosition();
                            playSound(run.world(), selectedStart, Sound.BLOCK_NOTE_BLOCK_BELL, 0.19f, 1.85f);
                        }
                    }
                    case "gather" -> {
                        if (frame.phaseStart()) {
                            run.protectReward(result);
                            playSound(run.world(), base, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.16f, 1.25f);
                        }
                        ring.gather(selectedStart, result, frame.progress());
                    }
                    case "reward" -> {
                        ring.showPrize(result, frame.phaseTick() - 1);
                        if (frame.phaseStart()) {
                            playSound(run.world(), result, Sound.ENTITY_PLAYER_LEVELUP, 0.20f, 1.65f);
                        }
                    }
                }
            }

            private void orbit(AnimationFrame frame) {
                boolean forming = frame.is("form");
                double growth = forming ? RewardOrbit.smooth(frame.progress()) : 1.0;
                double rotation = forming ? 0.0 : stop * RewardOrbit.spinProgress(frame.progress());
                double radius = 2.30 * growth;
                for (int i = 0; i < ring.size(); i++) {
                    double angle = ring.angle(i, rotation);
                    Location cube = point(angle, radius).add(0.0, Math.sin(angle * 2.0) * 0.06, 0.0);
                    ring.pose(i, cube, (float) (0.46 * growth), (float) (rotation * 0.45 + i * 0.5), false);
                    if (frame.tick() % 3 != 0 || growth < 0.7) continue;
                    for (int step = 1; step <= 3; step++) {
                        Location trail = point(angle + step * 0.13, radius);
                        AnimationEffects.plain(run.world(), stars, trail, 1, 0.06, 0.09, 0.06, 0.008);
                    }
                }
                if (forming && frame.phaseStart()) {
                    playSound(run.world(), base, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.14f, 1.6f);
                    playSound(run.world(), base, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.16f, 1.4f);
                }
                int note = (int) (rotation / (Math.PI * 2.0 / ring.size()));
                if (!forming && note != lastNote) {
                    lastNote = note;
                    playSound(run.world(), center, Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, 0.11f,
                            1.7f - (float) frame.progress() * 0.65f);
                    if (note % 8 == 0) {
                        playSound(run.world(), center, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.10f, 1.8f);
                    }
                }
            }

            private Location point(double angle, double radius) {
                return center.clone().add(right.clone().multiply(Math.cos(angle) * radius))
                        .add(toward.clone().multiply(Math.sin(angle) * radius));
            }
        });
    }
}
