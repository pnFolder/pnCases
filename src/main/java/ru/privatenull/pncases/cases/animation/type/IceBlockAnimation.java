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
 * Ice block. Frost runs over the ground and the case freezes into a block of ice with shards around it. Blow after
 * blow, faster and faster, cracks spread over the three sides the player can see, and the ice starts to glow in the
 * colour of the rarity. Then it bursts into pieces and the prize comes out.
 *
 * <p>Cracks and pieces are spawned the moment they are needed. Cracks never move; the pieces are posed every second
 * tick, the client's interpolation fills the gap.</p>
 */
public final class IceBlockAnimation extends TimelineCaseAnimation {

    private static final double WIDTH = 2.0;
    private static final double HEIGHT = 2.2;
    private static final int SHARDS = 8;
    private static final int FRAGMENTS = 16;
    private static final int FRAGMENT_TICKS = 24;

    /** The ticks of the crack phase on which the ice is hit. */
    private static final int[] HITS = {6, 18, 28, 36, 42, 46, 49};

    public IceBlockAnimation(PnCasesPlugin plugin) {
        super(plugin);
    }

    @Override
    protected void start(AnimationSession run) {
        Location base = run.base().getBlock().getLocation().add(0.5, 0.5, 0.5);
        Location ground = base.clone().add(0.0, -0.5, 0.0);
        Vector toward = RewardOrbit.towardViewer(run, base);
        Vector right = new Vector(toward.getZ(), 0.0, -toward.getX());
        float facing = (float) Math.atan2(toward.getX(), toward.getZ());
        boolean rarity = plugin.rarityEnabled();
        int tier = SceneParts.rarityTier(run.reward(), rarity);
        Color color = SceneParts.rarityColor(run.reward(), rarity);

        List<Crack> cracks = new ArrayList<>();
        addFace(cracks, toward);
        addFace(cracks, right);
        addFace(cracks, right.clone().multiply(-1.0));
        List<Shard> shards = shards(run, ground);

        Particle snow = ParticleCompat.first("SNOWFLAKE", "END_ROD");
        Particle rod = ParticleCompat.first("END_ROD", "FIREWORK");
        Particle poof = AnimationEffects.particle(AnimationEffects.Kind.POOF);
        Particle dust = AnimationEffects.particle(AnimationEffects.Kind.DUST);
        Particle.DustOptions frost = new Particle.DustOptions(Color.fromRGB(190, 235, 255), 1.1f);

        AnimationTimeline timeline = AnimationTimeline.builder()
                .phase("frost", 18).phase("freeze", 36).phase("crack", 54).phase("reveal", 76).build();

        startTimeline(run, new TimelineTask(run, "ice-block", timeline) {
            private BlockDisplay ice;
            private int nextHit;
            private int lastHit = -10;
            private int cracksShown;
            private final List<Fragment> fragments = new ArrayList<>();
            private RisingPrize prize;

            @Override
            protected void render(AnimationFrame frame) {
                switch (frame.phase()) {
                    case "frost" -> {
                        if (frame.phaseStart()) {
                            playSound(run.world(), ground, Sound.BLOCK_POWDER_SNOW_STEP, 0.25f, 0.7f);
                            playSound(run.world(), ground, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.18f, 0.6f);
                        }
                        drawFrost(frame.progress());
                    }
                    case "freeze" -> {
                        if (frame.phaseStart()) {
                            ice = SceneParts.spawnBlock(run, ground, Material.ICE, true, null);
                            playSound(run.world(), ground, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.3f, 0.7f);
                        }
                        double p = frame.progress();
                        placeIce(HEIGHT * SceneParts.easeOutBack(p), 0.0);
                        for (Shard shard : shards) shard.grow(p);
                        if (frame.phaseTick() % 7 == 0) {
                            playSound(run.world(), ground, Sound.BLOCK_GLASS_PLACE, 0.2f, 0.7f + (float) p * 0.8f);
                        }
                        AnimationEffects.plain(run.world(), snow, base, 2, 0.8, 0.8, 0.8, 0.0);
                    }
                    case "crack" -> crack(frame);
                    case "reveal" -> reveal(frame);
                    default -> { }
                }
            }

            private void drawFrost(double progress) {
                double radius = 0.5 + 2.2 * progress;
                int points = 12 + (int) (radius * 8.0);
                for (int i = 0; i < points; i++) {
                    double a = i * Math.PI * 2.0 / points;
                    Location at = ground.clone().add(Math.cos(a) * radius, 0.08, Math.sin(a) * radius);
                    AnimationEffects.dust(run.world(), dust, at, 1, 0.0, 0.0, 0.0, 0.0, frost);
                }
                AnimationEffects.plain(run.world(), snow, base, 2, 1.2, 0.5, 1.2, 0.0);
            }

            private void placeIce(double height, double jolt) {
                if (ice == null) return;
                SceneParts.place(ice, toward.getX() * jolt, height / 2.0, toward.getZ() * jolt, WIDTH, height, WIDTH,
                        new Quaternionf().rotateY(facing));
            }

            private void crack(AnimationFrame frame) {
                int t = frame.phaseTick();
                if (frame.phaseStart()) {
                    placeIce(HEIGHT, 0.0);
                    ice.setGlowColorOverride(color);
                }
                while (nextHit < HITS.length && t >= HITS[nextHit]) {
                    nextHit++;
                    lastHit = t;
                    int shown = cracks.size() * nextHit / HITS.length;
                    for (; cracksShown < shown; cracksShown++) cracks.get(cracksShown).show(run, ground);
                    float pitch = 0.7f + 1.1f * nextHit / HITS.length;
                    playSound(run.world(), base, Sound.BLOCK_GLASS_BREAK, 0.16f, pitch);
                    playSound(run.world(), base, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.2f, pitch);
                    AnimationEffects.plain(run.world(), snow, base.clone().add(toward).add(0.0, 0.6, 0.0), 8,
                            0.5, 0.5, 0.5, 0.03);
                }
                int since = t - lastHit;
                if (since <= 2) placeIce(HEIGHT, since == 2 ? 0.0 : (since == 0 ? 0.04 : -0.04));
                // The ice flickers in the rarity colour from the middle of the phase, and burns before it bursts.
                if (nextHit > HITS.length / 2) ice.setGlowing(nextHit == HITS.length || t % 6 < 3);
            }

            private void reveal(AnimationFrame frame) {
                int t = frame.phaseTick();
                if (frame.phaseStart()) burst();
                if (t % 2 == 0 && t <= FRAGMENT_TICKS) {
                    for (Fragment fragment : fragments) fragment.step(t);
                }
                if (t == FRAGMENT_TICKS + 1) {
                    for (Fragment fragment : fragments) fragment.remove(run);
                    fragments.clear();
                }
                if (t <= 12) for (Shard shard : shards) shard.melt(t / 12.0);
                if (t == 13) for (Shard shard : shards) shard.remove(run);
                if (t == 4) {
                    Location center = ground.clone().add(0.0, 0.9, 0.0);
                    ItemStack visual = resolveRewardVisual(run.reward(), run.definition());
                    prize = new RisingPrize(run, visual, resolveRewardName(run.reward(), visual), center, ground, toward);
                }
                if (prize == null) return;
                Location at = prize.step(t - 4);
                if (t == 40) {
                    playSound(run.world(), at, Sound.ENTITY_PLAYER_LEVELUP, 0.2f, 1.7f);
                    AnimationEffects.plain(run.world(), rod, at, 24, 0.5, 0.4, 0.5, 0.05);
                }
                if (t % 2 == 0) AnimationEffects.plain(run.world(), snow, base.clone().add(0.0, 1.5, 0.0), 2, 1.6, 1.0, 1.6, 0.0);
            }

            private void burst() {
                run.untrack(ice);
                AnimationSession.removeEntity(ice);
                ice = null;
                for (Crack crack : cracks) crack.remove(run);
                run.hide(base.getBlock(), Material.AIR);
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int i = 0; i < FRAGMENTS; i++) {
                    fragments.add(new Fragment(run, ground, i % 3 == 0 ? Material.PACKED_ICE : Material.ICE, random));
                }
                Location center = ground.clone().add(0.0, 1.1, 0.0);
                playSound(run.world(), center, Sound.BLOCK_GLASS_BREAK, 0.4f, 0.6f);
                playSound(run.world(), center, Sound.BLOCK_GLASS_BREAK, 0.3f, 1.0f);
                playSound(run.world(), center, Sound.ENTITY_GENERIC_EXPLODE, 0.12f, 1.6f);
                AnimationEffects.plain(run.world(), poof, center, 18, 0.9, 0.9, 0.9, 0.06);
                AnimationEffects.plain(run.world(), snow, center, 40, 1.0, 1.0, 1.0, 0.08);
                SceneParts.celebrate(run, tier, color, center, rod, snow);
            }
        });
    }

    // ---------------------------------------------------------------------- layout

    /** Cracks on one side of the block: lines breaking away from a point of impact, nearest segments first. */
    private static void addFace(List<Crack> cracks, Vector normal) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vector tangent = new Vector(normal.getZ(), 0.0, -normal.getX());
        float yaw = (float) Math.atan2(normal.getX(), normal.getZ());
        double half = WIDTH / 2.0;
        double centerU = random.nextDouble(-0.3, 0.3);
        double centerV = random.nextDouble(0.9, 1.4);
        List<List<Crack>> branches = new ArrayList<>();
        for (int branch = 0; branch < 3; branch++) {
            double angle = branch * Math.PI * 2.0 / 3.0 + random.nextDouble(-0.5, 0.5);
            double u = centerU;
            double v = centerV;
            List<Crack> line = new ArrayList<>();
            for (int segment = 0; segment < 3; segment++) {
                double length = random.nextDouble(0.35, 0.55);
                double du = Math.cos(angle) * length;
                double dv = Math.sin(angle) * length;
                double nextU = Math.max(-half + 0.05, Math.min(half - 0.05, u + du));
                double nextV = Math.max(0.05, Math.min(HEIGHT - 0.05, v + dv));
                double midU = (u + nextU) / 2.0;
                double midV = (v + nextV) / 2.0;
                Vector at = normal.clone().multiply(half + 0.015).add(tangent.clone().multiply(midU)).setY(midV);
                double drawn = Math.hypot(nextU - u, nextV - v);
                float tilt = (float) Math.atan2(nextV - v, nextU - u);
                line.add(new Crack(at, drawn, new Quaternionf().rotateY(yaw).rotateZ(tilt)));
                u = nextU;
                v = nextV;
                angle += random.nextDouble(-0.7, 0.7);
            }
            branches.add(line);
        }
        for (int segment = 0; segment < 3; segment++) {
            for (List<Crack> line : branches) cracks.add(line.get(segment));
        }
    }

    private static List<Shard> shards(AnimationSession run, Location ground) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Shard> result = new ArrayList<>();
        for (int i = 0; i < SHARDS; i++) {
            double angle = i * Math.PI * 2.0 / SHARDS + random.nextDouble(-0.2, 0.2);
            double radius = random.nextDouble(1.7, 2.3);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            double lift = SceneParts.groundShift(run.world(), ground, x, z);
            if (Double.isNaN(lift)) continue;
            result.add(new Shard(run, ground, x, lift, z, angle, random));
        }
        return result;
    }

    /** One straight piece of a crack, a thin white line on the surface of the ice. */
    private static final class Crack {

        private final Vector at;
        private final double length;
        private final Quaternionf rotation;
        private BlockDisplay display;

        Crack(Vector at, double length, Quaternionf rotation) {
            this.at = at;
            this.length = length;
            this.rotation = rotation;
        }

        void show(AnimationSession run, Location ground) {
            display = SceneParts.spawnBlock(run, ground, Material.WHITE_CONCRETE, true, null);
            SceneParts.place(display, at.getX(), at.getY(), at.getZ(), length, 0.04, 0.01, new Quaternionf(rotation));
        }

        void remove(AnimationSession run) {
            if (display == null) return;
            run.untrack(display);
            AnimationSession.removeEntity(display);
            display = null;
        }
    }

    /** An ice spike that grows out of the ground beside the block, leaning outwards. */
    private static final class Shard {

        private final AnimationSession run;
        private final Location ground;
        private final double x;
        private final double lift;
        private final double z;
        private final double wait;
        private final double height;
        private final double width;
        private final Quaternionf rotation;
        private final Material material;
        private BlockDisplay display;
        private double lastGrowth = -1.0;

        Shard(AnimationSession run, Location ground, double x, double lift, double z, double angle,
              ThreadLocalRandom random) {
            this.run = run;
            this.ground = ground;
            this.x = x;
            this.lift = lift;
            this.z = z;
            this.wait = random.nextDouble(0.0, 0.5);
            this.height = random.nextDouble(0.9, 1.7);
            this.width = random.nextDouble(0.22, 0.34);
            this.rotation = new Quaternionf().rotateY((float) -angle).rotateZ((float) -random.nextDouble(0.15, 0.35));
            this.material = random.nextBoolean() ? Material.PACKED_ICE : Material.BLUE_ICE;
        }

        void grow(double progress) {
            pose(SceneParts.easeOutBack((progress - wait) / 0.5));
        }

        void melt(double progress) {
            if (display != null) pose(1.0 - progress);
        }

        private void pose(double growth) {
            if (growth <= 0.0 && display == null) return;
            if (growth == lastGrowth) return;
            lastGrowth = growth;
            if (display == null) display = SceneParts.spawnBlock(run, ground, material, true, null);
            double h = height * Math.max(0.0, growth);
            SceneParts.place(display, x, lift + h * 0.45, z, width * Math.max(0.0, growth), h,
                    width * Math.max(0.0, growth), new Quaternionf(rotation));
        }

        void remove(AnimationSession run) {
            if (display == null) return;
            run.untrack(display);
            AnimationSession.removeEntity(display);
            display = null;
        }
    }

    /** A piece of the burst block: it flies out, tumbles, falls and shrinks away. */
    private static final class Fragment {

        private final Vector start;
        private final Vector velocity;
        private final Vector axis;
        private final double spin;
        private final double size;
        private BlockDisplay display;

        Fragment(AnimationSession run, Location ground, Material material, ThreadLocalRandom random) {
            double angle = random.nextDouble(Math.PI * 2.0);
            double out = random.nextDouble(0.1, 0.24);
            start = new Vector(Math.cos(angle) * WIDTH * 0.4, random.nextDouble(0.3, HEIGHT - 0.3),
                    Math.sin(angle) * WIDTH * 0.4);
            velocity = new Vector(Math.cos(angle) * out, random.nextDouble(0.08, 0.3), Math.sin(angle) * out);
            axis = new Vector(random.nextDouble(-1.0, 1.0), random.nextDouble(0.2, 1.0), random.nextDouble(-1.0, 1.0))
                    .normalize();
            spin = random.nextDouble(0.2, 0.5);
            size = random.nextDouble(0.25, 0.55);
            display = SceneParts.spawnBlock(run, ground, material, true, null);
            display.setDisplayWidth(0.0f);
            display.setDisplayHeight(0.0f);
        }

        void step(int tick) {
            if (display == null) return;
            double x = start.getX() + velocity.getX() * tick;
            double y = Math.max(0.1, start.getY() + velocity.getY() * tick - 0.016 * tick * tick);
            double z = start.getZ() + velocity.getZ() * tick;
            double s = size * Math.min(1.0, (FRAGMENT_TICKS - tick) / 8.0);
            Quaternionf rotation = new Quaternionf().rotateAxis((float) (spin * tick),
                    (float) axis.getX(), (float) axis.getY(), (float) axis.getZ());
            SceneParts.place(display, x, y, z, s, s, s, rotation);
        }

        void remove(AnimationSession run) {
            if (display == null) return;
            run.untrack(display);
            AnimationSession.removeEntity(display);
            display = null;
        }
    }
}
