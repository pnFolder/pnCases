package ru.privatenull.pncases.cases.animation.type;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.privatenull.pncases.PnCasesPlugin;
import ru.privatenull.pncases.cases.animation.core.AnimationFrame;
import ru.privatenull.pncases.cases.animation.core.AnimationSession;
import ru.privatenull.pncases.cases.animation.core.AnimationTimeline;
import ru.privatenull.pncases.cases.animation.core.TimelineCaseAnimation;
import ru.privatenull.pncases.cases.animation.core.TimelineTask;
import ru.privatenull.pncases.cases.animation.effect.AnimationEffects;
import ru.privatenull.pncases.cases.animation.effect.RewardOrbit;
import ru.privatenull.pncases.support.compat.BukkitCompat.ParticleCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Santa's sleigh. The reindeer fly in across the sky with bells ringing, a present drops from the sleigh over the
 * case, lands in the snow with a bounce and starts to shake. Its ribbons light up in the colour of the rarity, the
 * lid flies off and the prize comes out.
 *
 * <p>The sleigh is one rigid model of block displays. It moves far from its entity, so the parts have no culling box,
 * and its pose is sent every second tick: the client's interpolation fills the gap.</p>
 */
public final class SantaSleighAnimation extends TimelineCaseAnimation {

    private static final int FLIGHT_TICKS = 70;
    private static final double FLIGHT_REACH = 13.0;
    private static final double FLIGHT_LOW = 4.6;

    public SantaSleighAnimation(PnCasesPlugin plugin) {
        super(plugin);
    }

    @Override
    protected void start(AnimationSession run) {
        Location base = run.base().getBlock().getLocation().add(0.5, 0.5, 0.5);
        Location ground = base.clone().add(0.0, -0.5, 0.0);
        Vector toward = RewardOrbit.towardViewer(run, base);
        Vector course = new Vector(toward.getZ(), 0.0, -toward.getX());
        Vector lane = toward.clone().multiply(-1.2);
        float heading = (float) Math.atan2(-course.getZ(), course.getX());
        boolean rarity = plugin.rarityEnabled();
        int tier = SceneParts.rarityTier(run.reward(), rarity);
        Color color = SceneParts.rarityColor(run.reward(), rarity);

        Rig sleigh = sleigh();
        Rig box = box();
        Rig lid = lid();
        Particle snow = ParticleCompat.first("SNOWFLAKE", "END_ROD");
        Particle rod = ParticleCompat.first("END_ROD", "FIREWORK");
        Particle poof = AnimationEffects.particle(AnimationEffects.Kind.POOF);

        AnimationTimeline timeline = AnimationTimeline.builder()
                .phase("snow", 14).phase("approach", 30).phase("fall", 18).phase("bounce", 22)
                .phase("shake", 34).phase("open", 70).build();

        startTimeline(run, new TimelineTask(run, "santa-sleigh", timeline) {
            private int flown;
            private boolean flying;
            private Vector sleighAt = new Vector();
            private Vector dropAt;
            private RisingPrize prize;

            @Override
            protected void render(AnimationFrame frame) {
                if (flying) fly();
                switch (frame.phase()) {
                    case "snow" -> {
                        sleigh.spawnNext(run, ground, 3);
                        if (frame.phaseStart()) {
                            playSound(run.world(), ground, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.18f, 1.6f);
                        }
                        fall(frame.phaseTick(), 2.0);
                    }
                    case "approach" -> {
                        flying = true;
                        box.spawnNext(run, ground, 1);
                        lid.spawnNext(run, ground, 1);
                        fall(frame.phaseTick(), 1.2);
                    }
                    case "fall" -> {
                        if (frame.phaseStart()) {
                            dropAt = sleighAt.clone().add(new Vector(0.0, 0.4, 0.0));
                            playSound(run.world(), at(dropAt), Sound.ENTITY_ITEM_PICKUP, 0.22f, 0.6f);
                        }
                        double p = frame.progress();
                        Vector gift = new Vector(dropAt.getX() * (1.0 - p), dropAt.getY() * (1.0 - p * p),
                                dropAt.getZ() * (1.0 - p));
                        Quaternionf turn = new Quaternionf().rotateY(heading + (float) (p * 5.0));
                        pose(gift, turn, 1.0, 1.0, frame.phaseTick());
                        fall(frame.phaseTick(), 1.0);
                    }
                    case "bounce" -> {
                        if (frame.phaseStart()) land(1.0);
                        double p = frame.progress();
                        double hop = 0.7 * (1.0 - p) * Math.abs(Math.sin(p * Math.PI * 2.0));
                        double squash = 1.0 - 0.32 * Math.max(0.0, 1.0 - frame.phaseTick() / 5.0);
                        if (frame.phaseTick() == 11) land(0.5);
                        pose(new Vector(0.0, hop, 0.0), new Quaternionf().rotateY(heading), 1.0, squash,
                                frame.phaseTick());
                        fall(frame.phaseTick(), 0.8);
                    }
                    case "shake" -> {
                        if (frame.phaseStart()) sleigh.remove(run);
                        double p = frame.progress();
                        if (p > 0.4) box.glow(color);
                        float wobble = (float) (Math.sin(frame.phaseTick() * 1.6) * (0.05 + p * 0.22));
                        pose(new Vector(0.0, frame.phaseTick() % 8 < 2 ? 0.06 + p * 0.12 : 0.0, 0.0),
                                new Quaternionf().rotateY(heading).rotateX(wobble), 1.0, 1.0, frame.phaseTick());
                        if (frame.phaseTick() % 8 == 0) {
                            playSound(run.world(), ground, Sound.BLOCK_WOOL_PLACE, 0.2f, 0.8f + (float) p * 0.7f);
                            playSound(run.world(), ground, Sound.BLOCK_NOTE_BLOCK_BELL, 0.1f, 0.8f + (float) p);
                        }
                        fall(frame.phaseTick(), 0.8);
                    }
                    case "open" -> open(frame);
                    default -> { }
                }
            }

            private Location at(Vector offset) {
                return ground.clone().add(offset);
            }

            private void fly() {
                flown++;
                if (flown > FLIGHT_TICKS) return;
                double s = -FLIGHT_REACH + 2.0 * FLIGHT_REACH * flown / FLIGHT_TICKS;
                double climb = s < 0.0 ? 0.014 : 0.04;
                sleighAt = course.clone().multiply(s).add(lane).setY(FLIGHT_LOW + climb * s * s);
                if (flown % 2 == 0) {
                    float pitch = (float) Math.atan(2.0 * climb * s);
                    double size = Math.min(1.0, Math.min(flown / 12.0, (FLIGHT_TICKS - flown) / 12.0));
                    sleigh.pose(sleighAt, new Quaternionf().rotateY(heading).rotateZ(pitch), size, 1.0, flown);
                }
                if (flown % 5 == 0) {
                    playSound(run.world(), at(sleighAt), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.2f,
                            flown % 10 == 0 ? 1.4f : 1.7f);
                }
                Location tail = at(sleighAt).add(course.clone().multiply(-1.2)).add(0.0, 0.3, 0.0);
                AnimationEffects.plain(run.world(), rod, tail, 2, 0.1, 0.1, 0.1, 0.0);
                AnimationEffects.plain(run.world(), snow, tail, 2, 0.2, 0.15, 0.2, 0.0);
            }

            private void pose(Vector position, Quaternionf turn, double size, double squash, int tick) {
                box.pose(position, turn, size, squash, tick);
                lid.pose(position, turn, size, squash, tick);
            }

            /** The present touches the snow. */
            private void land(double force) {
                run.hide(base.getBlock(), Material.AIR);
                Location at = ground.clone().add(0.0, 0.1, 0.0);
                playSound(run.world(), at, Sound.BLOCK_SNOW_FALL, 0.3f, 0.7f);
                playSound(run.world(), at, Sound.BLOCK_WOOL_FALL, 0.25f, 0.7f);
                int puffs = (int) Math.round(16 * force);
                for (int i = 0; i < puffs; i++) {
                    double a = i * Math.PI * 2.0 / puffs;
                    Location ring = at.clone().add(Math.cos(a) * 0.75, 0.0, Math.sin(a) * 0.75);
                    AnimationEffects.plain(run.world(), poof, ring, 1, 0.05, 0.02, 0.05, 0.02);
                    AnimationEffects.plain(run.world(), snow, ring, 2, 0.15, 0.2, 0.15, 0.03);
                }
            }

            private void open(AnimationFrame frame) {
                int t = frame.phaseTick();
                double gone = RewardOrbit.smooth(t / 16.0);
                if (t <= 18) {
                    box.pose(new Vector(), new Quaternionf().rotateY(heading), 1.0 - gone, 1.0, t);
                    lid.pose(new Vector(0.3 * t, 0.9 + t * 0.32 - 0.008 * t * t, 0.0).rotateAroundY(heading),
                            new Quaternionf().rotateY(heading).rotateZ(-t * 0.28f), 1.0 - gone, 1.0, t);
                } else if (t == 19) {
                    box.remove(run);
                    lid.remove(run);
                }
                if (t == 1) {
                    Location center = ground.clone().add(0.0, 0.7, 0.0);
                    ItemStack visual = resolveRewardVisual(run.reward(), run.definition());
                    prize = new RisingPrize(run, visual, resolveRewardName(run.reward(), visual), center, ground, toward);
                    playSound(run.world(), center, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.22f, 1.2f);
                    SceneParts.celebrate(run, tier, color, center, rod, snow);
                }
                if (prize == null) return;
                Location at = prize.step(t);
                if (t == 34) {
                    playSound(run.world(), at, Sound.ENTITY_PLAYER_LEVELUP, 0.2f, 1.7f);
                    AnimationEffects.plain(run.world(), rod, at, 24, 0.5, 0.4, 0.5, 0.05);
                }
                fall(t, 0.8);
            }

            private void fall(int tick, double density) {
                if (tick % 2 != 0) return;
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int i = 0; i < Math.max(1, (int) Math.round(3 * density)); i++) {
                    Location at = base.clone().add(random.nextDouble(-3.0, 3.0), random.nextDouble(1.2, 4.6),
                            random.nextDouble(-3.0, 3.0));
                    AnimationEffects.plain(run.world(), snow, at, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        });
    }

    // ---------------------------------------------------------------------- models

    /** The sleigh in its own frame: x forward, y up, z sideways, the origin under the middle of the runners. */
    private static Rig sleigh() {
        Rig rig = new Rig();
        for (int side = -1; side <= 1; side += 2) {
            rig.add(Material.GOLD_BLOCK, 0.0, 0.04, side * 0.42, 2.0, 0.07, 0.08);
            rig.add(Material.GOLD_BLOCK, 1.0, 0.16, side * 0.42, 0.08, 0.25, 0.08);
            rig.add(Material.GOLD_BLOCK, side * 0.6, 0.17, 0.0, 0.08, 0.2, 0.9);
        }
        rig.add(Material.RED_CONCRETE, -0.1, 0.55, 0.0, 1.5, 0.55, 0.9);
        rig.add(Material.GOLD_BLOCK, -0.1, 0.85, 0.0, 1.56, 0.06, 0.96);
        rig.add(Material.RED_CONCRETE, -0.8, 0.85, 0.0, 0.2, 0.7, 0.9);
        rig.add(Material.RED_CONCRETE, 0.75, 0.62, 0.0, 0.3, 0.4, 0.8);
        // Santa with his bag.
        rig.add(Material.RED_WOOL, -0.2, 1.15, 0.0, 0.55, 0.6, 0.55);
        rig.add(Material.BLACK_CONCRETE, -0.2, 1.0, 0.0, 0.58, 0.1, 0.58);
        rig.add(Material.WHITE_TERRACOTTA, -0.2, 1.6, 0.0, 0.36, 0.34, 0.36);
        rig.add(Material.WHITE_WOOL, -0.08, 1.5, 0.0, 0.2, 0.24, 0.38);
        rig.add(Material.RED_WOOL, -0.2, 1.84, 0.0, 0.4, 0.14, 0.4);
        rig.add(Material.WHITE_WOOL, -0.2, 1.96, 0.0, 0.16, 0.14, 0.16);
        rig.add(Material.BROWN_WOOL, -0.62, 1.25, 0.0, 0.45, 0.5, 0.6);
        // The harness and the reindeer in front of it.
        rig.add(Material.GOLD_BLOCK, 2.4, 0.95, 0.0, 3.0, 0.04, 0.04);
        reindeer(rig, 2.1, false);
        reindeer(rig, 3.5, true);
        return rig;
    }

    private static void reindeer(Rig rig, double x, boolean lead) {
        Material coat = Material.BROWN_TERRACOTTA;
        rig.addBobbing(coat, x, 0.9, 0.0, 0.8, 0.36, 0.3);
        rig.addBobbing(coat, x + 0.42, 1.12, 0.0, 0.18, 0.42, 0.18);
        rig.addBobbing(coat, x + 0.56, 1.4, 0.0, 0.32, 0.2, 0.2);
        rig.addBobbing(Material.SPRUCE_LOG, x + 0.5, 1.62, 0.0, 0.05, 0.3, 0.3);
        rig.addBobbing(lead ? Material.REDSTONE_BLOCK : Material.BLACK_CONCRETE, x + 0.74, 1.38, 0.0, 0.08, 0.08, 0.1);
        rig.addLeg(coat, x + 0.3, 0.58, 0.0, 0.1, 0.38, 0.26);
        rig.addLeg(coat, x - 0.3, 0.58, 0.0, 0.1, 0.38, 0.26);
    }

    private static Rig box() {
        Rig rig = new Rig();
        rig.add(Material.RED_CONCRETE, 0.0, 0.4, 0.0, 0.9, 0.8, 0.9);
        rig.addRibbon(0.0, 0.4, 0.0, 0.94, 0.82, 0.16);
        rig.addRibbon(0.0, 0.4, 0.0, 0.16, 0.82, 0.94);
        return rig;
    }

    private static Rig lid() {
        Rig rig = new Rig();
        rig.add(Material.RED_CONCRETE, 0.0, 0.87, 0.0, 0.98, 0.14, 0.98);
        rig.addRibbon(0.0, 0.87, 0.0, 1.0, 0.16, 0.16);
        rig.addRibbon(0.0, 0.87, 0.0, 0.16, 0.16, 1.0);
        rig.addRibbon(0.14, 1.0, 0.0, 0.22, 0.12, 0.1);
        rig.addRibbon(-0.14, 1.0, 0.0, 0.22, 0.12, 0.1);
        return rig;
    }

    /** Block displays that move as one solid model around a shared origin. */
    private static final class Rig {

        private enum Motion { STILL, BOB, LEG, RIBBON }

        private record Spec(Material material, float x, float y, float z, float w, float h, float d, Motion motion) {
        }

        private final List<Spec> specs = new ArrayList<>();
        private final List<BlockDisplay> parts = new ArrayList<>();
        private boolean glowing;

        void add(Material material, double x, double y, double z, double w, double h, double d) {
            add(material, x, y, z, w, h, d, Motion.STILL);
        }

        void addBobbing(Material material, double x, double y, double z, double w, double h, double d) {
            add(material, x, y, z, w, h, d, Motion.BOB);
        }

        void addLeg(Material material, double x, double y, double z, double w, double h, double d) {
            add(material, x, y, z, w, h, d, Motion.LEG);
        }

        void addRibbon(double x, double y, double z, double w, double h, double d) {
            add(Material.GOLD_BLOCK, x, y, z, w, h, d, Motion.RIBBON);
        }

        private void add(Material material, double x, double y, double z, double w, double h, double d, Motion motion) {
            specs.add(new Spec(material, (float) x, (float) y, (float) z, (float) w, (float) h, (float) d, motion));
        }

        /** Spawns the next hidden parts, so the whole model does not enter the world in one tick. */
        void spawnNext(AnimationSession run, Location anchor, int count) {
            for (int i = 0; i < count && parts.size() < specs.size(); i++) {
                BlockDisplay part = SceneParts.spawnBlock(run, anchor, specs.get(parts.size()).material(), true, null);
                part.setDisplayWidth(0.0f);
                part.setDisplayHeight(0.0f);
                parts.add(part);
            }
        }

        void pose(Vector position, Quaternionf turn, double size, double squash, int tick) {
            for (int i = 0; i < parts.size(); i++) {
                Spec spec = specs.get(i);
                float bob = spec.motion() == Motion.BOB || spec.motion() == Motion.LEG
                        ? (float) (Math.sin(tick * 0.6) * 0.06) : 0.0f;
                Vector3f local = new Vector3f(spec.x(), (spec.y() + bob) * (float) squash, spec.z());
                local.mul((float) size);
                turn.transform(local);
                Quaternionf rotation = new Quaternionf(turn);
                if (spec.motion() == Motion.LEG) rotation.rotateZ((float) (Math.sin(tick * 0.6 + i) * 0.5));
                SceneParts.place(parts.get(i), position.getX() + local.x, position.getY() + local.y,
                        position.getZ() + local.z, spec.w() * size, spec.h() * size * squash, spec.d() * size,
                        rotation);
            }
        }

        /** Lights the ribbons in the colour of the rarity. */
        void glow(Color color) {
            if (glowing) return;
            glowing = true;
            for (int i = 0; i < parts.size(); i++) {
                if (specs.get(i).motion() != Motion.RIBBON) continue;
                parts.get(i).setGlowColorOverride(color);
                parts.get(i).setGlowing(true);
            }
        }

        void remove(AnimationSession run) {
            for (BlockDisplay part : parts) {
                run.untrack(part);
                AnimationSession.removeEntity(part);
            }
            parts.clear();
        }
    }
}
