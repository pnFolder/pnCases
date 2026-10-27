package ru.privatenull.pncases.cases.animation.type;

import org.bukkit.Color;
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

/** Upright wheel with short cyan and green trails behind eight reward items. */
public final class PrismAnimation extends TimelineCaseAnimation {

    public PrismAnimation(PnCasesPlugin plugin) {
        super(plugin);
    }

    @Override
    protected void start(AnimationSession run) {
        Location base = run.base().getBlock().getLocation().add(0.5, 0.5, 0.5);
        Vector toward = RewardOrbit.towardViewer(run, base);
        Vector right = new Vector(toward.getZ(), 0.0, -toward.getX());
        Location center = base.clone().add(0.0, 2.30, 0.0);
        Location result = base.clone().add(0.0, 1.55, 0.0);
        RewardOrbit ring = new RewardOrbit(run, base, reward -> resolveRewardVisual(reward, run.definition()),
                reward -> resolveRewardName(reward, resolveRewardVisual(reward, run.definition())), false);
        run.hide(base.getBlock(), Material.AIR);

        AnimationTimeline timeline = AnimationTimeline.builder()
                .phase("form", 32).phase("spin", 144).phase("select", 24)
                .phase("gather", 36).phase("reward", 48).build();
        double stop = ring.stopRotation(3);
        Particle dust = AnimationEffects.particle(AnimationEffects.Kind.DUST);
        Particle.DustOptions cyan = new Particle.DustOptions(Color.fromRGB(32, 185, 231), 0.85f);
        Particle.DustOptions green = new Particle.DustOptions(Color.fromRGB(116, 231, 88), 0.75f);

        startTimeline(run, new TimelineTask(run, "prism-wheel", timeline) {
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
                            playSound(run.world(), selectedStart, Sound.BLOCK_NOTE_BLOCK_BELL, 0.20f, 1.6f);
                        }
                    }
                    case "gather" -> {
                        if (frame.phaseStart()) {
                            run.protectReward(result);
                            playSound(run.world(), base, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.17f, 1.25f);
                        }
                        ring.gather(selectedStart, result, frame.progress());
                    }
                    case "reward" -> {
                        ring.showPrize(result, frame.phaseTick() - 1);
                        if (frame.phaseStart()) {
                            playSound(run.world(), result, Sound.ENTITY_PLAYER_LEVELUP, 0.20f, 1.45f);
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
                    ring.pose(i, point(angle, radius), (float) (0.43 * growth),
                            (float) (rotation * 0.4 + i * 0.45), growth > 0.85);
                    if (frame.tick() % 2 != 0 || growth < 0.35) continue;
                    for (int step = 1; step <= 4; step++) {
                        Location trail = point(angle + step * 0.055, radius);
                        AnimationEffects.dust(run.world(), dust, trail, 1, 0.025, 0.025, 0.025, 0.0,
                                (step + i) % 3 == 0 ? green : cyan);
                    }
                }
                if (forming && frame.phaseStart()) {
                    playSound(run.world(), base, Sound.BLOCK_BEACON_ACTIVATE, 0.16f, 1.6f);
                    playSound(run.world(), base, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.18f, 1.3f);
                }
                // Notes follow passing reward items, so they slow down with the wheel itself.
                int note = (int) (rotation / (Math.PI * 2.0 / ring.size()));
                if (!forming && note != lastNote) {
                    lastNote = note;
                    playSound(run.world(), center, Sound.BLOCK_NOTE_BLOCK_PLING, 0.10f,
                            1.65f - (float) frame.progress() * 0.55f);
                }
            }

            private Location point(double angle, double radius) {
                return center.clone().add(right.clone().multiply(Math.cos(angle) * radius))
                        .add(0.0, Math.sin(angle) * radius, 0.0);
            }
        });
    }
}
