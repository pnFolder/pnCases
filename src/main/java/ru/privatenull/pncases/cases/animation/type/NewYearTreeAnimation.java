package ru.privatenull.pncases.cases.animation.type;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pncases.support.compat.BukkitCompat.ParticleCompat;
import ru.privatenull.pncases.support.item.HeadUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * New Year scene. Snow starts to fall and drifts settle; a fir tree grows where the case stood and is dressed in a
 * garland of real, glowing bulbs. Eight larger bulbs stand for the possible rewards and are coloured by rarity
 * (neutral when the rarity feature is off). The garland is switched on, a light runs round the eight bulbs, slows
 * down and stops on the winner, which tears loose and turns into the prize in front of the tree.
 *
 * <p>Nothing is spawned up front: every part is created the first time it is needed, so the spawns spread over
 * the scene, and a part is re-sent to the client only when its pose or light changed. Particles are limited to the
 * snowfall and a flash at the end.</p>
 */
public final class NewYearTreeAnimation extends TimelineCaseAnimation {

    private static final int PRIZE_BULBS = 8;
    private static final int STRANDS = 3;
    private static final int BULBS_PER_STRAND = 9;
    private static final int DRAW_TICKS = 64;
    private static final double BULB_LOW = 0.8;
    private static final double BULB_HIGH = 2.85;

    /** Size of a garland bulb and of the smallest prize bulb, as the scale of the head model. */
    private static final float GARLAND_SCALE = 0.42f;
    private static final float PRIZE_SCALE = 0.55f;
    private static final float PRIZE_SCALE_PER_TIER = 0.07f;

    // Skull textures from mc-heads.com (Christmas Light and Christmas Ball Ornament series).
    private static final String LIGHT_RED = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGU3MTdiNDg1ZDk1YjNiZDE0ZGIxMzUwYzU1YTI5ZGE5OWVlMWQzMTk1YzA1MDYxNzA4OGE5Njc3YWM0MiJ9fX0=";
    private static final String LIGHT_ORANGE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjZjNDA2MjQyZjViZjk3ODZkODgzY2I2YTI1ODFjZTI5OWM2NmY0ZWQ4ODk3YzhiODU3NjllNGQyYTM4MzEifX19";
    private static final String LIGHT_GREEN = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDdhOTg1YzMxYWQ0NjFlMzIyYjM5YzQzM2VhZTc5ZmY0NmZiNjQ4MDBjZGVjYWRmMmY2ZDBmOTcyNzI1MiJ9fX0=";
    private static final String BALL_GRAY = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjI0MDcwYzliNjY1OWVkMjViMmNhMTI2OTE1ZjRkODgyMGZhZmNlNDMyNGVkOWE4ZjRiOGE1MDYzNDUzMDdmIn19fQ==";
    private static final String BALL_LIME = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODNhMjc2OGRlMmFmNTJiMjdjN2JjMTQxNmRkNTM1MTk3YjY1MTU4NDIyNjczZGE5ZDNiMGU2MWQ0OWYzMzY2MiJ9fX0=";
    private static final String BALL_BLUE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjdlMTFkYjhmMzkyNzI3YTFjNDU5ZWI1OTFkZDhhMGFhMzg2MWMyNzQ4N2UxMzg0YmIyMmY5MTU3M2JlNyJ9fX0=";
    private static final String BALL_PURPLE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODQ1ZjM5MzE4ODkwYThhYmZlZTViMzIwYjM1Yjk1OWZjNjZlMTI1ZThmOGNmM2I3MDY2ZDI0ZjY1ZGU0YjkifX19";
    private static final String BALL_GOLD = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmVhZWQyMzI1ZDc5NTIyMTIwZjQyMmExZGU3Mjc1NmFmNDRjYWY3NWM3NWRjMjYzM2U1NjczMjM4NDdlZCJ9fX0=";
    private static final String BALL_PINK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTQ2N2U1Nzk1YTZkNGRjZmQ5ZjI5OGM0ODAxMTdhNzYxZWZiZmE0M2E5MTNmZmVjODI1YmQwNzY2MGZhZWZmNCJ9fX0=";

    private static final String[] PLAIN_HEADS = {LIGHT_RED, LIGHT_ORANGE, LIGHT_GREEN};
    private static final Color[] PLAIN_COLORS = {
            Color.fromRGB(255, 70, 70), Color.fromRGB(255, 160, 50), Color.fromRGB(120, 255, 140)
    };
    private static final java.util.Map<String, ItemStack> HEADS = new java.util.concurrent.ConcurrentHashMap<>();

    private static final Transformation HIDDEN = new Transformation(
            new Vector3f(), new Quaternionf(), new Vector3f(0.0f), new Quaternionf());

    public NewYearTreeAnimation(PnCasesPlugin plugin) {
        super(plugin);
    }

    @Override
    protected void start(AnimationSession run) {
        Location base = run.base().getBlock().getLocation().add(0.5, 0.5, 0.5);
        Vector toward = RewardOrbit.towardViewer(run, base);
        Vector right = new Vector(toward.getZ(), 0.0, -toward.getX());
        float yaw = (float) Math.atan2(toward.getX(), toward.getZ());
        Location ground = base.clone().add(0.0, -0.5, 0.0);
        Location front = ground.clone().add(toward.clone().multiply(1.75)).add(0.0, 1.45, 0.0);

        Mounds mounds = new Mounds(run, ground);
        Tree tree = new Tree(run, ground, yaw);
        List<Bulb> garland = garland(run, tree, ground, right, toward);
        List<Bulb> prizes = prizeBulbs(run, tree, ground, right, toward, plugin.rarityEnabled());
        List<Bulb> byHeight = new ArrayList<>(garland);
        byHeight.addAll(prizes);
        byHeight.sort(Comparator.comparingDouble(bulb -> bulb.height));
        int winner = 0;
        for (int i = 0; i < prizes.size(); i++) if (prizes.get(i).winner) winner = i;
        int[] schedule = TreeLottery.schedule(PRIZE_BULBS, winner, DRAW_TICKS);
        run.hide(base.getBlock(), Material.AIR);

        AnimationTimeline timeline = AnimationTimeline.builder()
                .phase("snow", 24).phase("grow", 56).phase("dress", 36).phase("lights", 16)
                .phase("draw", DRAW_TICKS + 6).phase("reveal", 64).build();
        Particle snow = particle("SNOWFLAKE", "END_ROD");
        Particle rod = particle("END_ROD", "FIREWORK");
        Particle dust = AnimationEffects.particle(AnimationEffects.Kind.DUST);

        startTimeline(run, new TimelineTask(run, "new-year-tree", timeline) {
            private int lastTier = -1;
            private boolean starRang;
            private int nextStep;
            private Bulb running;
            private ItemDisplay prize;
            private Location prizeFrom;
            private RewardLabel label;
            private Vector side = toward.clone();

            @Override
            protected void render(AnimationFrame frame) {
                switch (frame.phase()) {
                    case "snow" -> {
                        if (frame.phaseStart()) {
                            playSound(run.world(), ground, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.18f, 1.5f);
                            playSound(run.world(), ground, Sound.BLOCK_POWDER_SNOW_STEP, 0.18f, 0.8f);
                        }
                        mounds.pose(frame.progress());
                        fall(frame.tick(), 2.0);
                    }
                    case "grow" -> {
                        tree.pose(frame.progress(), frame.tick());
                        int tier = tree.tierAt(frame.progress());
                        if (tier != lastTier) {
                            lastTier = tier;
                            playSound(run.world(), tree.top(0.4 + tier * 0.45), Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                                    0.16f, 0.85f + tier * 0.14f);
                            playSound(run.world(), tree.top(0.4 + tier * 0.45), Sound.BLOCK_MOSS_PLACE, 0.2f, 0.8f);
                        }
                        fall(frame.tick(), 1.2);
                    }
                    case "dress" -> {
                        tree.pose(1.0, frame.tick());
                        for (int i = 0; i < byHeight.size(); i++) {
                            Bulb bulb = byHeight.get(i);
                            double age = frame.phaseTick() - i * (28.0 / byHeight.size());
                            if (age < 1.0) continue;
                            bulb.appear(Tree.easeOutBack(age / 6.0));
                            if (age < 2.0 && i % 4 == 0) {
                                playSound(run.world(), bulb.position, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.08f,
                                        0.8f + (float) bulb.height * 0.3f);
                            }
                        }
                        if (frame.progress() > 0.9 && !starRang) {
                            starRang = true;
                            playSound(run.world(), tree.top(3.4), Sound.ENTITY_PLAYER_LEVELUP, 0.18f, 1.9f);
                            AnimationEffects.plain(run.world(), rod, tree.top(3.45), 24, 0.25, 0.25, 0.25, 0.06);
                        }
                        fall(frame.tick(), 1.0);
                    }
                    case "lights" -> {
                        tree.pose(1.0, frame.tick());
                        if (frame.phaseStart()) {
                            playSound(run.world(), tree.top(1.5), Sound.BLOCK_BEACON_ACTIVATE, 0.18f, 1.7f);
                        }
                        double wave = BULB_HIGH * 1.2 * frame.progress();
                        for (Bulb bulb : byHeight) {
                            bulb.light(bulb.height <= wave && (bulb.prize || garlandPhase(bulb, frame.tick())));
                        }
                        fall(frame.tick(), 1.0);
                    }
                    case "draw" -> {
                        tree.pose(1.0, frame.tick());
                        for (Bulb bulb : garland) bulb.light(garlandPhase(bulb, frame.tick()));
                        draw(frame);
                        fall(frame.tick(), 0.8);
                    }
                    case "reveal" -> {
                        tree.pose(1.0, frame.tick());
                        reveal(frame);
                        fall(frame.tick(), 0.8);
                    }
                    default -> { }
                }
            }

            /** Three groups of garland bulbs take turns, so the strands shimmer instead of burning evenly. */
            private boolean garlandPhase(Bulb bulb, int tick) {
                return (tick / 6 + bulb.group) % 3 != 0;
            }

            // ------------------------------------------------------------------ lottery

            private void draw(AnimationFrame frame) {
                while (nextStep < schedule.length && frame.phaseTick() >= schedule[nextStep]) {
                    if (running != null) running.boost(1.0);
                    running = prizes.get(TreeLottery.bulbAt(PRIZE_BULBS, nextStep));
                    running.boost(1.7);
                    float pitch = 0.7f + 1.1f * nextStep / Math.max(1, schedule.length - 1);
                    playSound(run.world(), running.position, Sound.BLOCK_NOTE_BLOCK_BELL, 0.12f, pitch);
                    if (nextStep == schedule.length - 1) {
                        playSound(run.world(), running.position, Sound.ENTITY_PLAYER_LEVELUP, 0.18f, 1.8f);
                    }
                    nextStep++;
                }
            }

            private void reveal(AnimationFrame frame) {
                Bulb winnerBulb = prizes.get(TreeLottery.bulbAt(PRIZE_BULBS, schedule.length - 1));
                int t = frame.phaseTick();
                if (t < 20) {
                    double pulse = 1.0 + 0.8 * RewardOrbit.smooth(t / 20.0) + 0.15 * Math.sin(t * 1.3);
                    winnerBulb.boost(1.7 * pulse);
                    for (Bulb bulb : prizes) if (bulb != winnerBulb) bulb.light(false);
                    for (Bulb bulb : garland) bulb.light(t % 4 < 2);
                    if (t == 1) {
                        playSound(run.world(), tree.top(3.4), Sound.BLOCK_BEACON_POWER_SELECT, 0.18f, 1.5f);
                    }
                    return;
                }
                if (t == 20) {
                    winnerBulb.discard();
                    prizeFrom = winnerBulb.position.clone();
                    prize = spawnPrize(prizeFrom, resolveRewardVisual(run.reward(), run.definition()));
                    label = RewardLabel.create(run, front,
                            resolveRewardName(run.reward(), resolveRewardVisual(run.reward(), run.definition())));
                    playSound(run.world(), prizeFrom, Sound.BLOCK_GLASS_BREAK, 0.2f, 1.2f);
                    celebrate(winnerBulb.look, prizeFrom);
                    for (Bulb bulb : garland) bulb.light(true);
                }
                Location target = frontNow();
                if ((t - 20) % 8 == 0) run.protectReward(target);
                double fly = RewardOrbit.smooth((t - 20) / 30.0);
                Location at = prizeFrom.clone().add(target.toVector().subtract(prizeFrom.toVector()).multiply(fly));
                at.add(0.0, Math.sin(fly * Math.PI) * 0.45, 0.0);
                float scale = (float) (0.2 + 0.8 * RewardOrbit.smooth((t - 20) / 14.0));
                placePrize(at, scale, (t - 20) * 0.12f);
                if (t > 44 && label != null) label.showAbove(at, 0.68);
                if (t == 52) {
                    playSound(run.world(), at, Sound.ENTITY_PLAYER_LEVELUP, 0.20f, 1.7f);
                    AnimationEffects.plain(run.world(), rod, at, 24, 0.5, 0.4, 0.5, 0.05);
                }
            }

            /**
             * Where the prize hangs: on whatever side of the tree the player is on right now, turning smoothly as
             * the player walks round, so it never shows up behind the tree.
             */
            private Location frontNow() {
                Vector to = run.player().getLocation().toVector().subtract(ground.toVector()).setY(0.0);
                if (to.lengthSquared() > 0.09) {
                    to.normalize();
                    Vector blended = side.clone().multiply(0.88).add(to.multiply(0.12));
                    if (blended.lengthSquared() > 1.0e-4) side = blended.normalize();
                }
                return ground.clone().add(side.clone().multiply(1.75)).add(0.0, 1.45, 0.0);
            }

            /** The bigger the rarity, the bigger the show. */
            private void celebrate(RarityLook look, Location at) {
                Particle.DustOptions options = new Particle.DustOptions(look.color, 1.4f);
                int sparks = 14 + look.tier * 10;
                AnimationEffects.plain(run.world(), rod, at, sparks, 0.35, 0.35, 0.35, 0.06 + look.tier * 0.015);
                AnimationEffects.plain(run.world(), snow, at, sparks, 0.6, 0.5, 0.6, 0.04);
                int points = 16 + look.tier * 6;
                for (int i = 0; i < points; i++) {
                    double a = i * Math.PI * 2.0 / points;
                    double r = 0.5 + look.tier * 0.12;
                    AnimationEffects.dust(run.world(), dust, at.clone().add(Math.cos(a) * r, 0.0, Math.sin(a) * r),
                            1, 0.0, 0.0, 0.0, 0.0, options);
                }
                if (look.tier >= 3) {
                    Particle totem = AnimationEffects.particle(AnimationEffects.Kind.TOTEM);
                    AnimationEffects.plain(run.world(), totem, at, 20 + look.tier * 8, 0.4, 0.5, 0.4, 0.3);
                }
                if (look.tier >= 4) {
                    for (int i = 0; i < 40; i++) {
                        double a = i * 0.35;
                        AnimationEffects.dust(run.world(), dust, at.clone().add(Math.cos(a) * 0.7, i * 0.07,
                                Math.sin(a) * 0.7), 1, 0.0, 0.0, 0.0, 0.0, options);
                    }
                }
                Sound sound = look.tier >= 4 ? Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST
                        : look.tier >= 2 ? Sound.ENTITY_FIREWORK_ROCKET_BLAST : Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
                playSound(run.world(), at, sound, 0.22f, 1.0f + look.tier * 0.05f);
                if (look.tier >= 3) playSound(run.world(), at, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.2f, 1.2f);
                if (look.tier >= 5) playSound(run.world(), at, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.22f, 0.9f);
            }

            private ItemDisplay spawnPrize(Location at, ItemStack item) {
                ItemDisplay display = run.world().spawn(at, ItemDisplay.class, spawned -> {
                    prepare(spawned);
                    spawned.setItemStack(item.clone());
                    spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                    spawned.setTransformation(HIDDEN);
                });
                return run.track(display);
            }

            private void placePrize(Location at, float scale, float spin) {
                if (prize == null) return;
                prize.setInterpolationDelay(0);
                prize.setTransformation(new Transformation(
                        new Vector3f((float) (at.getX() - prizeFrom.getX()), (float) (at.getY() - prizeFrom.getY()),
                                (float) (at.getZ() - prizeFrom.getZ())),
                        new Quaternionf().rotateY(spin), new Vector3f(scale), new Quaternionf()));
            }

            private void fall(int frameTick, double density) {
                if (frameTick % 2 != 0) return;
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int i = 0; i < Math.max(1, (int) Math.round(3 * density)); i++) {
                    Location at = base.clone().add(random.nextDouble(-2.8, 2.8), random.nextDouble(1.2, 4.2),
                            random.nextDouble(-2.8, 2.8));
                    AnimationEffects.plain(run.world(), snow, at, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        });
    }

    // ---------------------------------------------------------------------- layout

    /** Plain bulbs: three strands spiralling up the tree. */
    private static List<Bulb> garland(AnimationSession run, Tree tree, Location ground, Vector right, Vector toward) {
        List<Bulb> result = new ArrayList<>();
        for (int strand = 0; strand < STRANDS; strand++) {
            for (int k = 0; k < BULBS_PER_STRAND; k++) {
                double u = k / (double) (BULBS_PER_STRAND - 1);
                double y = 0.7 + u * 2.45 + strand * 0.05;
                double angle = Math.PI * 2.0 * (u * 3.2 + strand / (double) STRANDS);
                int color = (k + strand) % PLAIN_COLORS.length;
                Bulb bulb = new Bulb(run, tree.surface(ground, right, toward, angle, y),
                        PLAIN_HEADS[color], PLAIN_COLORS[color], GARLAND_SCALE, y, false, false, null);
                bulb.group = (k + strand) % 3;
                result.add(bulb);
            }
        }
        return result;
    }

    /** Eight prize bulbs, one per candidate reward, hung across the player's side of the tree. */
    private List<Bulb> prizeBulbs(AnimationSession run, Tree tree, Location ground, Vector right, Vector toward,
                                  boolean rarityShown) {
        List<Reward> pool = new ArrayList<>();
        for (Reward reward : run.definition().rewards()) {
            if (reward != null && reward != run.reward() && reward.chance() > 0) pool.add(reward);
        }
        Collections.shuffle(pool);
        List<Reward> chosen = new ArrayList<>();
        chosen.add(run.reward());
        for (int i = 0; i < PRIZE_BULBS - 1; i++) chosen.add(pool.isEmpty() ? run.reward() : pool.get(i % pool.size()));
        Collections.shuffle(chosen);

        boolean winnerTaken = false;
        List<Bulb> result = new ArrayList<>();
        for (int i = 0; i < chosen.size(); i++) {
            Reward reward = chosen.get(i);
            boolean winner = !winnerTaken && reward == run.reward();
            winnerTaken |= winner;
            RarityLook look = rarityShown ? RarityLook.of(reward.rarity()) : RarityLook.neutral(i);
            double y = TreeLottery.height(i, chosen.size(), BULB_LOW, BULB_HIGH);
            double angle = TreeLottery.angle(i, chosen.size());
            Location at = tree.surface(ground, right, toward, angle, y);
            result.add(new Bulb(run, at, look.head, look.color, PRIZE_SCALE + PRIZE_SCALE_PER_TIER * look.tier, y,
                    true, winner, look));
        }
        return result;
    }

    // ---------------------------------------------------------------------- helpers

    private static Particle particle(String... names) {
        return ParticleCompat.first(names);
    }

    /**
     * Spawns an invisible block display. The consumer configures it before it enters the world, so the client
     * gets one complete spawn instead of a spawn followed by a burst of metadata updates.
     */
    private static BlockDisplay spawnBlock(AnimationSession run, Location anchor, Material material, boolean lit,
                                           Color glow) {
        BlockDisplay display = run.world().spawn(anchor, BlockDisplay.class, spawned -> {
            spawned.setBlock(material.createBlockData());
            prepare(spawned);
            spawned.setBrightness(new Display.Brightness(lit ? 15 : 5, lit ? 15 : 5));
            if (glow != null) spawned.setGlowColorOverride(glow);
            spawned.setTransformation(HIDDEN);
        });
        return run.track(display);
    }

    /** Spawns an invisible head display that always turns its face to the viewer, like a hanging bulb. */
    private static ItemDisplay spawnHead(AnimationSession run, Location anchor, String texture, boolean lit,
                                         Color glow) {
        ItemStack head = HEADS.computeIfAbsent(texture, key -> HeadUtil.create(key, " ")).clone();
        ItemDisplay display = run.world().spawn(anchor, ItemDisplay.class, spawned -> {
            prepare(spawned);
            spawned.setItemStack(head);
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setBrightness(new Display.Brightness(lit ? 15 : 6, lit ? 15 : 6));
            spawned.setGlowColorOverride(glow);
            spawned.setTransformation(HIDDEN);
        });
        return run.track(display);
    }

    private static void prepare(Display display) {
        display.setPersistent(false);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(2);
        display.setShadowRadius(0.0f);
        display.setDisplayWidth(10.0f);
        display.setDisplayHeight(10.0f);
    }

    /** Puts a block of the given size with its centre at the offset from the entity, turned about that centre. */
    private static void place(BlockDisplay display, double cx, double cy, double cz,
                              double sx, double sy, double sz, Quaternionf rotation) {
        Vector3f translation = new Vector3f((float) (-sx / 2.0), (float) (-sy / 2.0), (float) (-sz / 2.0));
        rotation.transform(translation);
        translation.add((float) cx, (float) cy, (float) cz);
        display.setInterpolationDelay(0);
        display.setTransformation(new Transformation(translation, rotation,
                new Vector3f((float) sx, (float) sy, (float) sz), new Quaternionf()));
    }

    /** How a reward of one rarity looks as a prize bulb. */
    private enum RarityLook {
        COMMON(BALL_GRAY, Color.fromRGB(205, 205, 205), 0),
        UNCOMMON(BALL_LIME, Color.fromRGB(90, 230, 110), 1),
        RARE(BALL_BLUE, Color.fromRGB(80, 140, 255), 2),
        EPIC(BALL_PURPLE, Color.fromRGB(180, 90, 255), 3),
        LEGENDARY(BALL_GOLD, Color.fromRGB(255, 190, 40), 4),
        MYTHIC(BALL_PINK, Color.fromRGB(255, 90, 210), 5),
        FESTIVE_RED(LIGHT_RED, Color.fromRGB(255, 80, 80), 2),
        FESTIVE_GOLD(LIGHT_ORANGE, Color.fromRGB(255, 205, 70), 2),
        FESTIVE_BLUE(BALL_BLUE, Color.fromRGB(95, 175, 255), 2),
        FESTIVE_WHITE(LIGHT_GREEN, Color.fromRGB(255, 255, 255), 2);

        final String head;
        final Color color;
        final int tier;

        RarityLook(String head, Color color, int tier) {
            this.head = head;
            this.color = color;
            this.tier = tier;
        }

        static RarityLook of(Reward.Rarity rarity) {
            return switch (rarity) {
                case COMMON -> COMMON;
                case UNCOMMON -> UNCOMMON;
                case RARE -> RARE;
                case EPIC -> EPIC;
                case LEGENDARY -> LEGENDARY;
                case MYTHIC -> MYTHIC;
            };
        }

        /** Without rarities the prize bulbs carry no hint about what they hold. */
        static RarityLook neutral(int index) {
            return switch (index % 4) {
                case 0 -> FESTIVE_RED;
                case 1 -> FESTIVE_GOLD;
                case 2 -> FESTIVE_BLUE;
                default -> FESTIVE_WHITE;
            };
        }
    }

    /**
     * One garland bulb: a small head (Christmas light or ball) that glows when lit. Spawned on first use; its pose and light are
     * re-sent only when they change.
     */
    private static final class Bulb {

        final AnimationSession run;
        final Location position;
        final String head;
        final Color color;
        final double size;
        final double height;
        final boolean prize;
        final boolean winner;
        final RarityLook look;
        int group;
        private ItemDisplay display;
        private double scale;
        private double boost = 1.0;
        private boolean lit;
        private double lastScale = -1.0;
        private double lastBoost = -1.0;
        private boolean lastLit;
        private boolean discarded;

        Bulb(AnimationSession run, Location position, String head, Color color, double size, double height,
             boolean prize, boolean winner, RarityLook look) {
            this.run = run;
            this.position = position;
            this.head = head;
            this.color = color;
            this.size = size;
            this.height = height;
            this.prize = prize;
            this.winner = winner;
            this.look = look;
        }

        void appear(double value) {
            scale = Math.max(0.0, value);
            push();
        }

        void light(boolean on) {
            lit = on;
            push();
        }

        /** Swells the bulb; the one the lottery light sits on is larger than the rest. */
        void boost(double value) {
            boost = value;
            push();
        }

        void discard() {
            discarded = true;
            if (display == null) return;
            run.untrack(display);
            AnimationSession.removeEntity(display);
            display = null;
        }

        private void push() {
            if (discarded || scale <= 0.0) return;
            if (scale == lastScale && boost == lastBoost && lit == lastLit && display != null) return;
            if (display == null) {
                display = spawnHead(run, position, head, lit, color);
                if (lit) display.setGlowing(true);
                lastLit = lit;
            }
            if (lit != lastLit) {
                display.setGlowing(lit);
                display.setBrightness(new Display.Brightness(lit ? 15 : 6, lit ? 15 : 6));
                lastLit = lit;
            }
            if (scale != lastScale || boost != lastBoost) {
                float s = (float) (size * scale * boost);
                display.setInterpolationDelay(0);
                display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(s),
                        new Quaternionf()));
                lastScale = scale;
                lastBoost = boost;
            }
        }
    }

    /** Snow drifts that pop up in a ring around the tree; each is spawned when its turn comes. */
    private static final class Mounds {

        private final AnimationSession run;
        private final Location ground;
        private final BlockDisplay[] displays;
        private final double[][] shape;
        private final double[] lift;
        private boolean settled;

        Mounds(AnimationSession run, Location ground) {
            this.run = run;
            this.ground = ground.clone();
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int count = 16;
            displays = new BlockDisplay[count];
            shape = new double[count][6];
            lift = new double[count];
            for (int i = 0; i < count; i++) {
                double angle = i * Math.PI * 2.0 / count + random.nextDouble(-0.15, 0.15);
                double radius = random.nextDouble(2.1, 2.7);
                shape[i][0] = Math.cos(angle) * radius;
                shape[i][1] = Math.sin(angle) * radius;
                shape[i][2] = random.nextDouble(0.5, 0.95);
                shape[i][3] = random.nextDouble(0.16, 0.34);
                shape[i][4] = random.nextDouble(Math.PI);
                shape[i][5] = random.nextDouble(0.0, 0.5);
                // A drift stands on the real ground and is left out where there is none.
                lift[i] = SceneParts.groundShift(run.world(), ground, shape[i][0], shape[i][1]);
            }
        }

        void pose(double progress) {
            if (settled) return;
            for (int i = 0; i < displays.length; i++) {
                double[] s = shape[i];
                double f = Tree.easeOutBack((progress - s[5]) / 0.5);
                if (f <= 0.0 || Double.isNaN(lift[i])) continue;
                if (displays[i] == null) displays[i] = spawnBlock(run, ground, Material.SNOW_BLOCK, true, null);
                place(displays[i], s[0], s[3] * f / 2.0 + lift[i], s[1], s[2] * f, s[3] * f, s[2] * 0.9 * f,
                        new Quaternionf().rotateY((float) s[4]));
            }
            if (progress >= 1.0) settled = true;
        }
    }

    /** Fir tree of display blocks, turned to face the player. Tiers are spawned as they start to grow. */
    private static final class Tree {

        private static final int TIERS = 6;
        private static final double TRUNK = 0.55;
        private static final double STEP = 0.45;

        private final AnimationSession run;
        private final Location anchor;
        private final Quaternionf facing;
        private BlockDisplay trunk;
        private final BlockDisplay[] boughsA = new BlockDisplay[TIERS];
        private final BlockDisplay[] boughsB = new BlockDisplay[TIERS];
        private final BlockDisplay[] caps = new BlockDisplay[TIERS];
        private final double[] lastTier = new double[TIERS];
        private double lastTrunk = -1.0;
        private BlockDisplay[] star;
        private double lastStar = -1.0;
        private boolean settled;

        Tree(AnimationSession run, Location anchor, float yaw) {
            this.run = run;
            this.anchor = anchor.clone();
            this.facing = new Quaternionf().rotateY(yaw);
            java.util.Arrays.fill(lastTier, -1.0);
        }

        private static double bottom(int tier) {
            return TRUNK - 0.1 + tier * STEP;
        }

        private static double width(int tier) {
            return 1.9 - 0.32 * tier;
        }

        static double easeOutBack(double t) {
            double x = Math.max(0.0, Math.min(1.0, t));
            double c1 = 1.70158;
            double c3 = c1 + 1.0;
            return 1.0 + c3 * Math.pow(x - 1.0, 3.0) + c1 * Math.pow(x - 1.0, 2.0);
        }

        int tierAt(double growth) {
            return Math.max(-1, Math.min(TIERS - 1, (int) ((growth - 0.08) / 0.12)));
        }

        Location top(double height) {
            return anchor.clone().add(0.0, height, 0.0);
        }

        /** A point on the tree wall at {@code height}; {@code angle} 0 is towards the player, growing to the right. */
        Location surface(Location ground, Vector right, Vector toward, double angle, double height) {
            int tier = Math.max(0, Math.min(TIERS - 1, (int) ((height - bottom(0)) / STEP)));
            // The two layers of a tier form a rounded shape about 1.37 times the half width out.
            double radius = 0.685 * width(tier) + 0.05;
            double sideways = Math.sin(angle);
            double forward = Math.cos(angle);
            return ground.clone().add(right.clone().multiply(sideways * radius))
                    .add(toward.clone().multiply(forward * radius)).add(0.0, height, 0.0);
        }

        private Quaternionf turned(float extraYaw) {
            return new Quaternionf(facing).rotateY(extraYaw);
        }

        void pose(double growth, int tick) {
            if (settled) return;
            double trunkGrowth = easeOutBack(growth / 0.12);
            if (trunkGrowth > 0.0 && trunkGrowth != lastTrunk) {
                if (trunk == null) trunk = spawnBlock(run, anchor, Material.DARK_OAK_LOG, true, null);
                lastTrunk = trunkGrowth;
                place(trunk, 0, 0.3 * trunkGrowth, 0, 0.32, 0.6 * trunkGrowth, 0.32, turned(0.0f));
            }
            boolean finished = growth >= 1.0;
            for (int i = 0; i < TIERS; i++) {
                double f = easeOutBack((growth - 0.08 - i * 0.12) / 0.29);
                if (f != 1.0) finished = false;
                if (f <= 0.0 || f == lastTier[i]) continue;
                if (boughsA[i] == null) {
                    boughsA[i] = spawnBlock(run, anchor, Material.MOSS_BLOCK, true, null);
                    boughsB[i] = spawnBlock(run, anchor, Material.GREEN_WOOL, true, null);
                    caps[i] = spawnBlock(run, anchor, Material.SNOW_BLOCK, true, null);
                }
                lastTier[i] = f;
                double w = width(i) * f;
                double thickness = 0.5 * f;
                double y = bottom(i);
                place(boughsA[i], 0, y + thickness / 2.0, 0, w, thickness, w, turned(0.0f));
                place(boughsB[i], 0, y + 0.02 * f + thickness * 0.45, 0, w * 0.94, thickness * 0.9, w * 0.94,
                        turned((float) (Math.PI / 4.0)));
                place(caps[i], 0, y + thickness + 0.02, 0, w * 0.88, 0.1 * f, w * 0.88,
                        turned((float) (Math.PI / 8.0)));
            }
            if (growth > 0.85) placeStar(easeOutBack((growth - 0.87) / 0.13));
            if (finished) settled = true;
        }

        /**
         * The star on top: a golden core with three crossing spikes, one per axis, so it looks the same from every
         * side. It never turns towards the player; it only grows once and then stays put.
         */
        private void placeStar(double grown) {
            double g = Math.max(0.0, grown);
            if (g <= 0.0 || g == lastStar) return;
            lastStar = g;
            if (star == null) {
                star = new BlockDisplay[4];
                for (int i = 0; i < star.length; i++) {
                    star[i] = spawnBlock(run, anchor, Material.GOLD_BLOCK, true, Color.fromRGB(255, 220, 90));
                    star[i].setGlowing(true);
                }
            }
            double y = bottom(TIERS - 1) + 0.5 + 0.32;
            float quarter = (float) (Math.PI / 4.0);
            place(star[0], 0, y, 0, 0.26 * g, 0.26 * g, 0.26 * g, new Quaternionf().rotateY(quarter).rotateX(quarter));
            place(star[1], 0, y, 0, 0.11 * g, 0.78 * g, 0.11 * g, new Quaternionf().rotateY(quarter));
            place(star[2], 0, y, 0, 0.78 * g, 0.11 * g, 0.11 * g, new Quaternionf().rotateX(quarter));
            place(star[3], 0, y, 0, 0.11 * g, 0.11 * g, 0.78 * g, new Quaternionf().rotateZ(quarter));
        }    }
}
