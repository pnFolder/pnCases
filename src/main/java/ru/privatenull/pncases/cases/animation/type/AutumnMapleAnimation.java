package ru.privatenull.pncases.cases.animation.type;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
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
import ru.privatenull.pncases.cases.animation.effect.RewardLabel;
import ru.privatenull.pncases.cases.animation.effect.RewardOrbit;
import ru.privatenull.pncases.cases.animation.type.CanopyLayout.Card;
import ru.privatenull.pncases.cases.animation.type.CanopyLayout.Segment;
import ru.privatenull.pncases.cases.animation.type.CanopyLayout.Shape;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pncases.support.compat.BukkitCompat.ParticleCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static ru.privatenull.pncases.cases.animation.type.SceneParts.easeOutBack;
import static ru.privatenull.pncases.cases.animation.type.SceneParts.place;
import static ru.privatenull.pncases.cases.animation.type.SceneParts.spawnBlock;

/**
 * Golden autumn, played like a short film.
 * <ol>
 *   <li>The case cracks and a carpet of leaves rolls out from the middle.</li>
 *   <li>A maple grows: trunk, roots, boughs, branches and twigs, each from the end of the one before.</li>
 *   <li>Its buds open as green leaves, which turn yellow, orange and red before your eyes, from the top down.</li>
 *   <li>A gust of wind sways the canopy and spins leaves round the tree.</li>
 *   <li>Eight fruits hang at the edge of the canopy, one per possible reward (their kind shows the rarity). A glowing
 *       firefly flies from fruit to fruit, slows down and settles on the winner.</li>
 *   <li>The winner drops from its branch, bursts on the ground, and the prize rises on the side of the tree the
 *       player is standing on, with a show that grows with its rarity.</li>
 * </ol>
 * No mobs. Nothing is spawned up front and a part is re-sent to the client only when its pose changed.
 */
public final class AutumnMapleAnimation extends TimelineCaseAnimation {

    private static final int PRIZE_FRUITS = 8;
    private static final int DRAW_TICKS = 64;
    private static final int CARDS_PER_LOBE = 24;
    private static final int FILL_PER_LOBE = 8;
    private static final int LITTER_CARDS = 90;
    private static final int FALLING_LEAVES = 20;
    private static final double FRUIT_LOW = 2.5;
    private static final double FRUIT_HIGH = 4.0;

    /** Materials of the leaf cards, by shade: yellow, orange, red, brown. */
    private static final Material[][] SHADES = {
            {Material.YELLOW_CONCRETE_POWDER, Material.YELLOW_WOOL},
            {Material.ORANGE_CONCRETE_POWDER, Material.ORANGE_WOOL},
            {Material.RED_CONCRETE_POWDER, Material.RED_WOOL},
            {Material.BROWN_CONCRETE_POWDER}
    };

    public AutumnMapleAnimation(PnCasesPlugin plugin) {
        super(plugin);
    }

    @Override
    protected void start(AnimationSession run) {
        Location base = run.base().getBlock().getLocation().add(0.5, 0.5, 0.5);
        Vector toward = RewardOrbit.towardViewer(run, base);
        Location ground = base.clone().add(0.0, -0.5, 0.0);
        Location front = ground.clone().add(toward.clone().multiply(1.9)).add(0.0, 1.45, 0.0);

        long seed = ThreadLocalRandom.current().nextLong();
        Shape shape = CanopyLayout.tree(seed);
        Parts floor = new Parts(run, ground);
        buildGround(floor, ground, seed);
        Parts wood = new Parts(run, ground);
        buildWood(wood, shape);
        Leaves leaves = new Leaves(run, ground, canopyCards(shape, seed));
        Parts lanterns = new Parts(run, ground);
        buildLanterns(lanterns, shape);
        Falling falling = new Falling(run, ground);
        Firefly firefly = new Firefly(run, ground);
        List<Fruit> fruits = fruits(run, ground, shape, plugin.rarityEnabled());
        int winner = 0;
        for (int i = 0; i < fruits.size(); i++) if (fruits.get(i).winner) winner = i;
        int[] schedule = TreeLottery.schedule(PRIZE_FRUITS, winner, DRAW_TICKS);
        run.hide(base.getBlock(), Material.AIR);

        AnimationTimeline timeline = AnimationTimeline.builder()
                .phase("awaken", 30).phase("grow", 64).phase("turn", 40).phase("breeze", 22)
                .phase("draw", DRAW_TICKS + 6).phase("reveal", 70).build();
        Particle leafBit = particle("FALLING_DUST", "END_ROD");
        Particle rod = particle("END_ROD", "FIREWORK");

        startTimeline(run, new TimelineTask(run, "autumn-maple", timeline) {
            private int nextStep;
            private int lastStepTime;
            private Location lastFlyPos = ground.clone().add(0.0, 4.6, 0.0);
            private Fruit running;
            private ItemDisplay prize;
            private Location prizeFrom;
            private RewardLabel label;
            private Vector side = toward.clone();

            @Override
            protected void render(AnimationFrame frame) {
                switch (frame.phase()) {
                    case "awaken" -> {
                        if (frame.phaseStart()) {
                            playSound(run.world(), ground, Sound.BLOCK_ROOTED_DIRT_BREAK, 0.24f, 0.7f);
                            playSound(run.world(), ground, Sound.BLOCK_AZALEA_LEAVES_PLACE, 0.22f, 0.9f);
                            AnimationEffects.block(run.world(), leafBit, ground.clone().add(0.0, 0.1, 0.0), 40, 0.5, 0.1,
                                    0.5, 0.15, Material.COARSE_DIRT.createBlockData());
                        }
                        floor.pose(frame.progress());
                        if (frame.tick() % 8 == 0) {
                            playSound(run.world(), ground, Sound.BLOCK_AZALEA_LEAVES_PLACE, 0.12f,
                                    0.8f + (float) frame.progress() * 0.5f);
                        }
                    }
                    case "grow" -> {
                        floor.pose(1.0);
                        wood.pose(frame.progress());
                        leaves.grow(frame.progress());
                        if (frame.tick() % 8 == 0) {
                            playSound(run.world(), ground, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.1f,
                                    0.7f + (float) frame.progress() * 0.9f);
                        }
                        falling.update(frame.tick(), (int) (frame.progress() * 8), 0.0);
                    }
                    case "turn" -> {
                        wood.pose(1.0);
                        leaves.grow(1.0);
                        leaves.turn(frame.progress());
                        lanterns.pose(frame.progress() * 1.6);
                        if (frame.phaseStart()) {
                            playSound(run.world(), ground, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.14f, 1.1f);
                        }
                        if (frame.tick() % 6 == 0) {
                            playSound(run.world(), ground, Sound.BLOCK_AZALEA_LEAVES_PLACE, 0.12f,
                                    1.2f - (float) frame.progress() * 0.5f);
                        }
                        for (int i = 0; i < fruits.size(); i++) {
                            Fruit fruit = fruits.get(i);
                            int age = frame.phaseTick() - 16 - i * 2;
                            if (age < 1) continue;
                            if (age == 1) {
                                playSound(run.world(), fruit.position, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.14f,
                                        fruit.chimePitch());
                                scatter(fruit.position, 6, 0.2, 0.2, 0.2, 0.01);
                            }
                            fruit.appear(easeOutBack(age / 8.0));
                        }
                        falling.update(frame.tick(), 8 + (int) (frame.progress() * 6), 0.0);
                    }
                    case "breeze" -> {
                        if (frame.phaseStart()) {
                            playSound(run.world(), ground, Sound.ITEM_ELYTRA_FLYING, 0.12f, 1.4f);
                            playSound(run.world(), ground, Sound.BLOCK_BEACON_ACTIVATE, 0.12f, 1.9f);
                        }
                        double strength = Math.sin(frame.progress() * Math.PI);
                        leaves.sway(frame.tick(), strength);
                        if (frame.phaseEnd()) leaves.restore();
                        for (Fruit fruit : fruits) fruit.light(true);
                        falling.update(frame.tick(), FALLING_LEAVES, strength);
                    }
                    case "draw" -> {
                        draw(frame);
                        sparkle(frame.tick());
                        falling.update(frame.tick(), FALLING_LEAVES, 0.0);
                    }
                    case "reveal" -> {
                        reveal(frame);
                        falling.update(frame.tick(), FALLING_LEAVES, 0.0);
                    }
                    default -> { }
                }
            }

            // ------------------------------------------------------------------ lottery

            /** A firefly flies to the next fruit and lands on it just as the schedule says; it never lands twice. */
            private void draw(AnimationFrame frame) {
                int t = frame.phaseTick();
                while (nextStep < schedule.length && t >= schedule[nextStep]) {
                    if (running != null) running.boost(1.0);
                    running = fruits.get(TreeLottery.bulbAt(PRIZE_FRUITS, nextStep));
                    running.boost(1.5);
                    float pitch = 0.7f + 1.1f * nextStep / Math.max(1, schedule.length - 1);
                    playSound(run.world(), running.position, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.14f, pitch);
                    scatter(running.position, 3, 0.15, 0.15, 0.15, 0.02);
                    if (nextStep == schedule.length - 1) {
                        playSound(run.world(), running.position, Sound.ENTITY_PLAYER_LEVELUP, 0.18f, 1.8f);
                    }
                    lastFlyPos = running.position.clone().add(0.0, 0.2, 0.0);
                    lastStepTime = schedule[nextStep];
                    nextStep++;
                }
                Location at;
                if (nextStep < schedule.length) {
                    Fruit next = fruits.get(TreeLottery.bulbAt(PRIZE_FRUITS, nextStep));
                    double span = Math.max(1, schedule[nextStep] - lastStepTime);
                    double f = RewardOrbit.smooth((t - lastStepTime) / span);
                    Location to = next.position.clone().add(0.0, 0.2, 0.0);
                    at = lastFlyPos.clone().add(to.toVector().subtract(lastFlyPos.toVector()).multiply(f));
                    at.add(0.0, Math.sin(f * Math.PI) * 0.3, 0.0);
                } else {
                    at = lastFlyPos.clone().add(0.0, Math.sin(t * 0.4) * 0.04, 0.0);
                }
                firefly.at(at, 1.0);
                AnimationEffects.plain(run.world(), rod, at, 1, 0.0, 0.0, 0.0, 0.0);
            }

            /** The rarer fruits shimmer in their colour so they stand out before the firefly reaches them. */
            private void sparkle(int tick) {
                if (tick % 6 != 0) return;
                Particle dust = AnimationEffects.particle(AnimationEffects.Kind.DUST);
                for (Fruit fruit : fruits) {
                    if (fruit.look.tier < 3 || fruit.discarded) continue;
                    AnimationEffects.dust(run.world(), dust, fruit.position, 1, 0.2, 0.2, 0.2, 0.0,
                            new Particle.DustOptions(fruit.look.color, 1.0f));
                }
            }

            private void reveal(AnimationFrame frame) {
                Fruit winnerFruit = fruits.get(TreeLottery.bulbAt(PRIZE_FRUITS, schedule.length - 1));
                int t = frame.phaseTick();
                if (t < 8) {
                    firefly.at(winnerFruit.position.clone().add(0.0, 0.25 + 0.04 * Math.sin(t), 0.0), 1.0);
                    winnerFruit.boost(1.5 + 0.3 * Math.sin(t * 1.6));
                    for (Fruit fruit : fruits) if (fruit != winnerFruit) fruit.light(false);
                    if (t == 1) playSound(run.world(), winnerFruit.position, Sound.BLOCK_BEACON_POWER_SELECT, 0.16f, 1.6f);
                    return;
                }
                if (t < 18) {
                    if (t == 8) {
                        winnerFruit.snapStem();
                        firefly.discard();
                        playSound(run.world(), winnerFruit.position, Sound.BLOCK_AZALEA_LEAVES_BREAK, 0.22f, 1.3f);
                        scatter(winnerFruit.position, 12, 0.2, 0.2, 0.2, 0.05);
                    }
                    winnerFruit.fall((t - 8) / 10.0);
                    return;
                }
                if (t == 18) {
                    prizeFrom = winnerFruit.landing();
                    winnerFruit.discard();
                    prize = SceneParts.spawnPrize(run, prizeFrom, resolveRewardVisual(run.reward(), run.definition()));
                    label = RewardLabel.create(run, front,
                            resolveRewardName(run.reward(), resolveRewardVisual(run.reward(), run.definition())));
                    playSound(run.world(), prizeFrom, Sound.BLOCK_AZALEA_LEAVES_BREAK, 0.28f, 0.8f);
                    scatter(prizeFrom, 70, 0.6, 0.4, 0.6, 0.1);
                    SceneParts.celebrate(run, winnerFruit.look.tier, winnerFruit.look.color, prizeFrom, rod, rod);
                    for (Fruit fruit : fruits) fruit.light(true);
                }
                side = SceneParts.followSide(side, ground, run.player());
                Location target = ground.clone().add(side.clone().multiply(1.9)).add(0.0, 1.45, 0.0);
                if ((t - 18) % 8 == 0) run.protectReward(target);
                double fly = RewardOrbit.smooth((t - 18) / 30.0);
                Location at = prizeFrom.clone().add(target.toVector().subtract(prizeFrom.toVector()).multiply(fly));
                at.add(0.0, Math.sin(fly * Math.PI) * 0.45, 0.0);
                float scale = (float) (0.2 + 0.8 * RewardOrbit.smooth((t - 18) / 14.0));
                SceneParts.placePrize(prize, prizeFrom, at, scale, (t - 18) * 0.12f);
                if (t > 42 && label != null) label.showAbove(at, 0.68);
                if (t == 50) {
                    playSound(run.world(), at, Sound.ENTITY_PLAYER_LEVELUP, 0.20f, 1.7f);
                    scatter(at, 40, 0.6, 0.5, 0.6, 0.05);
                }
            }

            /** A burst of leaf bits in the colours of the tree, for the few moments that call for one. */
            private void scatter(Location at, int count, double ox, double oy, double oz, double extra) {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                int groups = Math.max(1, Math.min(count, 4));
                for (int i = 0; i < groups; i++) {
                    Material[] shade = SHADES[random.nextInt(3)];
                    BlockData data = shade[random.nextInt(shade.length)].createBlockData();
                    AnimationEffects.block(run.world(), leafBit, at, Math.max(1, count / groups), ox, oy, oz, extra,
                            data);
                }
            }
        });
    }

    // ---------------------------------------------------------------------- layout

    private static BlockData shadeBlock(int shade, ThreadLocalRandom random) {
        Material[] materials = SHADES[Math.max(0, Math.min(SHADES.length - 1, shade))];
        return materials[random.nextInt(materials.length)].createBlockData();
    }

    /** Turns a leaf card into a rotation: a thin square facing the way the layout says, turned about that direction. */
    private static Quaternionf cardRotation(Card card) {
        return new Quaternionf()
                .rotationTo(new Vector3f(0.0f, 1.0f, 0.0f),
                        new Vector3f((float) card.nx(), (float) card.ny(), (float) card.nz()))
                .rotateY((float) card.roll());
    }

    private static List<Card> canopyCards(Shape shape, long seed) {
        List<Card> cards = new ArrayList<>(CanopyLayout.foliage(shape.lobes(), CARDS_PER_LOBE, seed + 1));
        cards.addAll(CanopyLayout.fill(shape.lobes(), FILL_PER_LOBE, seed + 2));
        return cards;
    }

    /** Wood from point to point: a box as long as the segment, turned along it. */
    private static void addWood(Parts parts, BlockData wood, Segment segment, double start, double duration) {
        double dx = segment.x1() - segment.x0();
        double dy = segment.y1() - segment.y0();
        double dz = segment.z1() - segment.z0();
        Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0.0f, 1.0f, 0.0f),
                new Vector3f((float) dx, (float) dy, (float) dz).normalize());
        parts.add(wood, (segment.x0() + segment.x1()) / 2.0, (segment.y0() + segment.y1()) / 2.0,
                (segment.z0() + segment.z1()) / 2.0, segment.width(), segment.length(), segment.width(), rotation,
                start, duration, false, true, null);
    }

    /** A carpet of fallen leaves that rolls out from the middle, with a few heaps; nothing else on the ground. */
    private static void buildGround(Parts parts, Location ground, long seed) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Card card : CanopyLayout.litter(LITTER_CARDS, 0.5, 3.2, seed)) {
            // Leaves lie on the real ground and are left out where there is none.
            double lift = SceneParts.groundShift(parts.run.world(), ground, card.x(), card.z());
            if (Double.isNaN(lift)) continue;
            double distance = Math.sqrt(card.x() * card.x() + card.z() * card.z());
            double start = Math.min(0.62, distance / 3.6 * 0.62) + random.nextDouble(0.05);
            parts.add(shadeBlock(card.shade(), random), card.x(), card.y() + lift, card.z(), card.size(), 0.02, card.size(),
                    cardRotation(card), start, 0.3, false, true, null);
        }
    }

    /** Each piece of wood starts when the one it grows from is under way: trunk, then boughs, branches, spokes. */
    private static void buildWood(Parts parts, Shape shape) {
        BlockData wood = Material.DARK_OAK_LOG.createBlockData();
        for (int i = 0; i < shape.trunk().size(); i++) addWood(parts, wood, shape.trunk().get(i), i * 0.035, 0.12);
        for (Segment root : shape.roots()) addWood(parts, wood, root, 0.0, 0.12);
        for (int i = 0; i < shape.boughs().size(); i++) {
            addWood(parts, wood, shape.boughs().get(i), 0.22 + (i % 3) * 0.06 + (i / 3) * 0.04, 0.14);
        }
        for (int i = 0; i < shape.branches().size(); i++) addWood(parts, wood, shape.branches().get(i), 0.38 + i * 0.01, 0.14);
        for (int i = 0; i < shape.twigs().size(); i++) addWood(parts, wood, shape.twigs().get(i), 0.5 + i * 0.01, 0.12);
    }

    /** Lanterns hanging from a few branch tips; they are lit, so they glow warm among the leaves. */
    private static void buildLanterns(Parts parts, Shape shape) {
        BlockData lantern = Material.LANTERN.createBlockData("[hanging=true]");
        for (int i = 1; i < shape.branches().size(); i += 4) {
            Segment tip = shape.branches().get(i);
            parts.add(lantern, tip.x1(), tip.y1() - 0.45, tip.z1(), 0.8, 0.8, 0.8, new Quaternionf(), i * 0.04, 0.4, false,
                    true, null);
        }
    }

    /** Eight fruits, one per candidate reward, hanging just outside the canopy all the way round. */
    private List<Fruit> fruits(AnimationSession run, Location ground, Shape shape, boolean rarityShown) {
        List<Reward> pool = new ArrayList<>();
        for (Reward reward : run.definition().rewards()) {
            if (reward != null && reward != run.reward() && reward.chance() > 0) pool.add(reward);
        }
        Collections.shuffle(pool);
        List<Reward> chosen = new ArrayList<>();
        chosen.add(run.reward());
        for (int i = 0; i < PRIZE_FRUITS - 1; i++) chosen.add(pool.isEmpty() ? run.reward() : pool.get(i % pool.size()));
        Collections.shuffle(chosen);

        boolean winnerTaken = false;
        List<Fruit> result = new ArrayList<>();
        for (int i = 0; i < chosen.size(); i++) {
            Reward reward = chosen.get(i);
            boolean winner = !winnerTaken && reward == run.reward();
            winnerTaken |= winner;
            FruitLook look = rarityShown ? FruitLook.of(reward.rarity()) : FruitLook.neutral(i);
            double[] point = CanopyLayout.fruitPoint(shape.lobes(), TreeLottery.angle(i, chosen.size()),
                    TreeLottery.height(i, chosen.size(), FRUIT_LOW, FRUIT_HIGH));
            Location at = ground.clone().add(point[0], point[1] - 0.25, point[2]);
            double lift = SceneParts.groundShift(run.world(), ground, point[0], point[2]);
            result.add(new Fruit(run, at, ground.getY() + 0.12 + (Double.isNaN(lift) ? 0.0 : lift), look, winner));
        }
        return result;
    }

    private static Particle particle(String... names) {
        return ParticleCompat.first(names);
    }

    // ---------------------------------------------------------------------- types

    /** The fruit that stands for a rarity: a plainer one for common rewards, a precious one for the rarest. */
    private enum FruitLook {
        COMMON(Material.SWEET_BERRIES, Color.fromRGB(205, 205, 205), 0, 0.5),
        UNCOMMON(Material.APPLE, Color.fromRGB(90, 230, 110), 1, 0.55),
        RARE(Material.GLOW_BERRIES, Color.fromRGB(80, 140, 255), 2, 0.6),
        EPIC(Material.GOLDEN_CARROT, Color.fromRGB(180, 90, 255), 3, 0.65),
        LEGENDARY(Material.GOLDEN_APPLE, Color.fromRGB(255, 190, 40), 4, 0.75),
        MYTHIC(Material.ENCHANTED_GOLDEN_APPLE, Color.fromRGB(255, 90, 210), 5, 0.9),
        FESTIVE_APPLE(Material.APPLE, Color.fromRGB(255, 90, 70), 2, 0.6),
        FESTIVE_BERRIES(Material.SWEET_BERRIES, Color.fromRGB(255, 120, 90), 2, 0.6),
        FESTIVE_GLOW(Material.GLOW_BERRIES, Color.fromRGB(255, 190, 80), 2, 0.6),
        FESTIVE_CARROT(Material.GOLDEN_CARROT, Color.fromRGB(255, 210, 90), 2, 0.6);

        final Material item;
        final Color color;
        final int tier;
        final double size;

        FruitLook(Material item, Color color, int tier, double size) {
            this.item = item;
            this.color = color;
            this.tier = tier;
            this.size = size;
        }

        static FruitLook of(Reward.Rarity rarity) {
            return switch (rarity) {
                case COMMON -> COMMON;
                case UNCOMMON -> UNCOMMON;
                case RARE -> RARE;
                case EPIC -> EPIC;
                case LEGENDARY -> LEGENDARY;
                case MYTHIC -> MYTHIC;
            };
        }

        /** Without rarities the fruit carries no hint about what it holds. */
        static FruitLook neutral(int index) {
            return switch (index % 4) {
                case 0 -> FESTIVE_APPLE;
                case 1 -> FESTIVE_BERRIES;
                case 2 -> FESTIVE_GLOW;
                default -> FESTIVE_CARROT;
            };
        }
    }

    /** One prize fruit on a thin stem; it swells under the firefly and, if it wins, drops to the ground. */
    private static final class Fruit {

        final AnimationSession run;
        final Location position;
        final double landingY;
        final FruitLook look;
        final boolean winner;
        boolean discarded;
        private ItemDisplay item;
        private BlockDisplay stem;
        private double scale;
        private double boost = 1.0;
        private double dropped;
        private boolean lit = true;
        private double lastScale = -1.0;
        private double lastBoost = -1.0;
        private double lastDropped = -1.0;
        private boolean lastLit = true;

        Fruit(AnimationSession run, Location position, double landingY, FruitLook look, boolean winner) {
            this.run = run;
            this.position = position;
            this.landingY = landingY;
            this.look = look;
            this.winner = winner;
        }

        float chimePitch() {
            return 0.9f + look.tier * 0.22f;
        }

        /** Where the fruit lands when it drops: straight below its branch. */
        Location landing() {
            return new Location(position.getWorld(), position.getX(), landingY, position.getZ());
        }

        void appear(double value) {
            scale = Math.max(0.0, value);
            push();
        }

        void light(boolean on) {
            lit = on;
            push();
        }

        void boost(double value) {
            boost = value;
            push();
        }

        /** 0 hangs on the branch, 1 lies on the ground; it speeds up on the way down like a falling fruit. */
        void fall(double progress) {
            dropped = Math.max(0.0, Math.min(1.0, progress));
            push();
        }

        void snapStem() {
            if (stem == null) return;
            run.untrack(stem);
            AnimationSession.removeEntity(stem);
            stem = null;
        }

        void discard() {
            discarded = true;
            if (item != null) {
                run.untrack(item);
                AnimationSession.removeEntity(item);
                item = null;
            }
            snapStem();
        }

        private void push() {
            if (discarded || scale <= 0.0) return;
            if (scale == lastScale && boost == lastBoost && lit == lastLit && dropped == lastDropped && item != null) return;
            if (item == null) {
                item = SceneParts.spawnHangingItem(run, position, new ItemStack(look.item));
                stem = spawnBlock(run, position, Material.DARK_OAK_LOG.createBlockData(), true, null);
                lastLit = true;
            }
            if (lit != lastLit) {
                item.setBrightness(new Display.Brightness(lit ? 15 : 7, lit ? 15 : 7));
                lastLit = lit;
            }
            if (scale != lastScale || boost != lastBoost || dropped != lastDropped) {
                float s = (float) (look.size * scale * boost);
                double drop = (position.getY() - landingY) * dropped * dropped;
                item.setInterpolationDelay(0);
                item.setTransformation(new Transformation(new Vector3f(0.0f, (float) -drop, 0.0f), new Quaternionf(),
                        new Vector3f(s), new Quaternionf()));
                if (scale != lastScale && stem != null) {
                    place(stem, 0, 0.24 * scale, 0, 0.035 * scale, 0.5 * scale, 0.035 * scale, new Quaternionf());
                }
                lastScale = scale;
                lastBoost = boost;
                lastDropped = dropped;
            }
        }
    }

    /** The small glowing light that picks the winner: it flies from fruit to fruit and leaves a thin trail. */
    private static final class Firefly {

        private final AnimationSession run;
        private final Location anchor;
        private BlockDisplay display;
        private boolean gone;

        Firefly(AnimationSession run, Location anchor) {
            this.run = run;
            this.anchor = anchor.clone();
        }

        void at(Location position, double scale) {
            if (gone) return;
            if (display == null) {
                display = spawnBlock(run, anchor, Material.GLOWSTONE.createBlockData(), true, Color.fromRGB(255, 210, 100));
            }
            double size = 0.13 * scale;
            place(display, position.getX() - anchor.getX(), position.getY() - anchor.getY(),
                    position.getZ() - anchor.getZ(), size, size, size, new Quaternionf().rotateY(0.6f).rotateX(0.78f));
        }

        void discard() {
            gone = true;
            if (display == null) return;
            run.untrack(display);
            AnimationSession.removeEntity(display);
            display = null;
        }
    }

    /**
     * The canopy's leaves. Each starts as a small green card, then turns yellow and finally takes its own autumn
     * colour, the top of the canopy first. In a gust they sway; everything is re-sent only when it changes.
     */
    private static final class Leaves {

        private final AnimationSession run;
        private final Location anchor;
        private final List<Leaf> leaves = new ArrayList<>();
        private boolean swayed;

        Leaves(AnimationSession run, Location anchor, List<Card> cards) {
            this.run = run;
            this.anchor = anchor.clone();
            ThreadLocalRandom random = ThreadLocalRandom.current();
            BlockData[] greens = {Material.LIME_CONCRETE_POWDER.createBlockData(),
                    Material.GREEN_CONCRETE_POWDER.createBlockData()};
            BlockData yellow = Material.YELLOW_CONCRETE_POWDER.createBlockData();
            for (Card card : cards) {
                double yFraction = Math.max(0.0, Math.min(1.0,
                        (card.y() - CanopyLayout.CANOPY_LOW) / (CanopyLayout.CANOPY_HIGH - CanopyLayout.CANOPY_LOW)));
                BlockData[] stages = {greens[random.nextInt(greens.length)], yellow, shadeBlock(card.shade(), random)};
                leaves.add(new Leaf(card, 0.35 + 0.5 * yFraction + random.nextDouble(0.05),
                        (1.0 - yFraction) * 0.5 + random.nextDouble(0.2), stages, cardRotation(card)));
            }
        }

        /** Buds open from the bottom of the canopy upwards, as small green leaves. */
        void grow(double progress) {
            for (Leaf leaf : leaves) {
                double f = easeOutBack((progress - leaf.appearAt) / 0.1);
                if (f <= 0.0 || f == leaf.lastScale) continue;
                if (leaf.display == null) {
                    leaf.display = spawnBlock(run, anchor, leaf.stages[0], true, null);
                    leaf.stage = 0;
                }
                leaf.lastScale = f;
                place(leaf.display, leaf.card.x(), leaf.card.y(), leaf.card.z(), leaf.card.size() * f, 0.02 * f,
                        leaf.card.size() * f, new Quaternionf(leaf.rotation));
            }
        }

        /** Green, then yellow, then the leaf's own colour; the top of the canopy turns first. */
        void turn(double progress) {
            for (Leaf leaf : leaves) {
                if (leaf.display == null) continue;
                int target = progress >= leaf.turnAt + 0.28 ? 2 : progress >= leaf.turnAt ? 1 : 0;
                if (target > leaf.stage) {
                    leaf.stage = target;
                    leaf.display.setBlock(leaf.stages[target]);
                }
            }
        }

        /** A wave of wind: a sixth of the leaves are re-posed each tick, tilting with a ripple across the canopy. */
        void sway(int tick, double strength) {
            if (strength <= 0.0) return;
            swayed = true;
            for (int i = 0; i < leaves.size(); i++) {
                if ((i + tick) % 6 != 0) continue;
                Leaf leaf = leaves.get(i);
                if (leaf.display == null) continue;
                if (!leaf.swaying) {
                    leaf.display.setInterpolationDuration(6);
                    leaf.swaying = true;
                }
                double angle = Math.sin(tick * 0.35 + leaf.card.y() * 1.8 + leaf.card.x() * 0.9) * 0.4 * strength;
                place(leaf.display, leaf.card.x(), leaf.card.y(), leaf.card.z(), leaf.card.size(), 0.02, leaf.card.size(),
                        new Quaternionf(leaf.rotation).rotateX((float) angle));
            }
        }

        /** Puts every swayed leaf back exactly where it hangs. */
        void restore() {
            if (!swayed) return;
            swayed = false;
            for (Leaf leaf : leaves) {
                if (leaf.display == null || !leaf.swaying) continue;
                place(leaf.display, leaf.card.x(), leaf.card.y(), leaf.card.z(), leaf.card.size(), 0.02, leaf.card.size(),
                        new Quaternionf(leaf.rotation));
            }
        }
    }

    private static final class Leaf {
        final Card card;
        final double appearAt;
        final double turnAt;
        final BlockData[] stages;
        final Quaternionf rotation;
        BlockDisplay display;
        int stage = -1;
        double lastScale = -1.0;
        boolean swaying;

        Leaf(Card card, double appearAt, double turnAt, BlockData[] stages, Quaternionf rotation) {
            this.card = card;
            this.appearAt = appearAt;
            this.turnAt = turnAt;
            this.stages = stages;
            this.rotation = rotation;
        }
    }

    /** Leaf cards that drift down from the canopy; in a gust they swirl round the tree before they land. */
    private static final class Falling {

        private static final int PERIOD = 3;

        private final AnimationSession run;
        private final Location ground;
        private final List<Drifter> drifters = new ArrayList<>();

        Falling(AnimationSession run, Location ground) {
            this.run = run;
            this.ground = ground.clone();
        }

        /** Moves the first {@code active} leaves, every few ticks; {@code swirl} 0..1 turns them round the trunk. */
        void update(int tick, int active, double swirl) {
            if (tick % PERIOD != 0) return;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            while (drifters.size() < Math.min(active, FALLING_LEAVES)) drifters.add(new Drifter(random));
            for (Drifter drifter : drifters) drifter.step(tick, random, swirl);
        }

        private final class Drifter {

            private BlockDisplay display;
            private double x;
            private double y;
            private double z;
            private final double speed;
            private final double phase;
            private final double size;
            private final int shade;
            private int stage;

            Drifter(ThreadLocalRandom random) {
                respawn(random);
                y = random.nextDouble(0.5, 3.8);
                speed = random.nextDouble(0.05, 0.09);
                phase = random.nextDouble(Math.PI * 2.0);
                size = random.nextDouble(0.22, 0.36);
                shade = random.nextInt(3);
            }

            private void respawn(ThreadLocalRandom random) {
                double a = random.nextDouble(Math.PI * 2.0);
                double r = random.nextDouble(0.3, 2.4);
                x = Math.cos(a) * r;
                z = Math.sin(a) * r;
                y = random.nextDouble(3.4, 4.2);
            }

            void step(int tick, ThreadLocalRandom random, double swirl) {
                if (display == null) {
                    Material[] materials = SHADES[shade];
                    display = spawnBlock(run, ground, materials[random.nextInt(materials.length)].createBlockData(), true,
                            null);
                    display.setInterpolationDuration(PERIOD);
                }
                if (stage == 1) {
                    // Hidden at the foot of the tree: move to the top while invisible, then show again.
                    respawn(random);
                    pose(tick, 0.0);
                    stage = 2;
                    return;
                }
                if (stage == 2) {
                    pose(tick, 1.0);
                    stage = 0;
                    return;
                }
                y -= speed * PERIOD * (1.0 - 0.5 * swirl);
                if (swirl > 0.0) {
                    double turn = 0.35 * swirl;
                    double cos = Math.cos(turn);
                    double sin = Math.sin(turn);
                    double nx = x * cos - z * sin;
                    double nz = x * sin + z * cos;
                    x = nx;
                    z = nz;
                    y += 0.05 * swirl;
                } else {
                    x += Math.sin(phase + tick * 0.09) * 0.03;
                    z += Math.cos(phase + tick * 0.07) * 0.03;
                }
                if (y <= 0.06) {
                    pose(tick, 0.0);
                    stage = 1;
                    return;
                }
                pose(tick, 1.0);
            }

            private void pose(int tick, double visible) {
                double s = size * visible;
                Quaternionf turn = new Quaternionf().rotateY((float) (tick * 0.11 + phase))
                        .rotateX((float) (Math.sin(tick * 0.13 + phase) * 0.9))
                        .rotateZ((float) (Math.PI / 4.0));
                place(display, x, y, z, s, 0.02, s, turn);
            }
        }
    }

    /** A group of block parts that grow in turn over one phase; each is spawned when its turn comes. */
    private static final class Parts {

        private final AnimationSession run;
        private final Location anchor;
        private final List<Part> parts = new ArrayList<>();
        private boolean settled;

        Parts(AnimationSession run, Location anchor) {
            this.run = run;
            this.anchor = anchor.clone();
        }

        void add(BlockData data, double cx, double cy, double cz, double sx, double sy, double sz, Quaternionf rotation,
                 double start, double duration, boolean fromBottom, boolean lit, Color glow) {
            parts.add(new Part(data, cx, cy, cz, sx, sy, sz, rotation, start, duration, fromBottom, lit, glow));
        }

        /** {@code progress} runs 0..1 over the phase; parts start at their own fraction of it. */
        void pose(double progress) {
            if (settled) return;
            boolean finished = progress >= 1.0;
            for (Part part : parts) {
                double f = easeOutBack((progress - part.start) / part.duration);
                if (f != 1.0) finished = false;
                if (f <= 0.0 || f == part.last) continue;
                if (part.display == null) {
                    part.display = spawnBlock(run, anchor, part.data, part.lit, part.glow);
                }
                part.last = f;
                double sy = part.sy * f;
                double cy = part.fromBottom ? part.cy + sy / 2.0 : part.cy;
                place(part.display, part.cx, cy, part.cz, part.sx * f, sy, part.sz * f, new Quaternionf(part.rotation));
            }
            if (finished) settled = true;
        }
    }

    private static final class Part {
        final BlockData data;
        final double cx;
        final double cy;
        final double cz;
        final double sx;
        final double sy;
        final double sz;
        final Quaternionf rotation;
        final double start;
        final double duration;
        final boolean fromBottom;
        final boolean lit;
        final Color glow;
        BlockDisplay display;
        double last = -1.0;

        Part(BlockData data, double cx, double cy, double cz, double sx, double sy, double sz, Quaternionf rotation,
             double start, double duration, boolean fromBottom, boolean lit, Color glow) {
            this.data = data;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.rotation = rotation;
            this.start = start;
            this.duration = duration;
            this.fromBottom = fromBottom;
            this.lit = lit;
            this.glow = glow;
        }
    }
}
