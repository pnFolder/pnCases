package ru.privatenull.pncases.cases.animation.type;

import static ru.privatenull.pncases.cases.animation.effect.AnimationEffects.Kind.*;
import static ru.privatenull.pncases.cases.animation.effect.AnimationEffects.particle;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import ru.privatenull.pncases.PnCasesPlugin;
import ru.privatenull.pncases.cases.animation.effect.AnimationParticles;
import static ru.privatenull.pncases.cases.animation.core.AbstractCaseAnimation.playSound;
import ru.privatenull.pncases.cases.animation.core.AnimationFrame;
import ru.privatenull.pncases.cases.animation.core.AnimationSession;
import ru.privatenull.pncases.cases.animation.core.AnimationTimeline;
import ru.privatenull.pncases.cases.animation.core.TimelineCaseAnimation;
import ru.privatenull.pncases.cases.animation.core.TimelineTask;
import ru.privatenull.pncases.cases.animation.effect.RewardLabel;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.Reward;
import static ru.privatenull.pncases.cases.animation.core.AnimationSession.removeEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class PortalAnimation extends TimelineCaseAnimation {

    private static final int ITEM_COUNT = 10;

    public PortalAnimation(PnCasesPlugin plugin) {
        super(plugin);
    }

    @Override
    protected void start(AnimationSession run) {
        Player player = run.player();
        CaseDefinition def = run.definition();
        Reward reward = run.reward();
        Location base = run.base();
        World w = base.getWorld();
        if (w == null) { run.complete(); return; }

        AnimationTimeline timeline = AnimationTimeline.builder()
                .phase("explosion", 50).phase("vortex", 56).phase("suction", 48)
                .phase("collapse", 28).phase("reveal", 80).build();

        Random rng = new Random();
        ItemStack rewardVisual = resolveRewardVisual(reward, def);

        Location centre = base.clone().add(0, 2.0, 0);

        Material[] mats = {
                Material.EMERALD, Material.GOLD_INGOT, Material.AMETHYST_SHARD,
                Material.LAPIS_LAZULI, Material.QUARTZ, Material.PRISMARINE_CRYSTALS,
                Material.NETHERITE_INGOT, Material.DIAMOND, Material.BLAZE_POWDER,
                Material.NETHER_STAR
        };

        double[] px = new double[ITEM_COUNT];
        double[] py = new double[ITEM_COUNT];
        double[] pz = new double[ITEM_COUNT];
        double[] vx = new double[ITEM_COUNT];
        double[] vy = new double[ITEM_COUNT];
        double[] vz = new double[ITEM_COUNT];
        boolean[] alive = new boolean[ITEM_COUNT];

        List<ItemDisplay> items = new ArrayList<>();

        for (int i = 0; i < ITEM_COUNT; i++) {
            ItemDisplay id = (ItemDisplay) w.spawnEntity(base.clone().add(0, 1.0, 0), EntityType.ITEM_DISPLAY);
            id.setItemStack(new ItemStack(mats[i]));
            id.setBillboard(Display.Billboard.VERTICAL);
            setItemScale(id, 0f);
            items.add(id);
            run.track(id);
            alive[i] = false;

            px[i] = base.getX() + 0.5;
            py[i] = base.getY() + 1.0;
            pz[i] = base.getZ() + 0.5;
        }

        BlockDisplay singularity = (BlockDisplay) w.spawnEntity(centre.clone(), EntityType.BLOCK_DISPLAY);
        singularity.setBlock(Material.BLACK_CONCRETE.createBlockData());
        setSingularityScale(singularity, 0f);
        run.track(singularity);

        ItemDisplay rewardDisplay = (ItemDisplay) w.spawnEntity(centre.clone(), EntityType.ITEM_DISPLAY);
        rewardDisplay.setItemStack(rewardVisual);
        rewardDisplay.setBillboard(Display.Billboard.FIXED);
        setItemScale(rewardDisplay, 0f);
        run.track(rewardDisplay);
        RewardLabel rewardLabel = RewardLabel.create(run, base,
                resolveRewardName(reward, rewardVisual));

        startTimeline(run, new TimelineTask(run, "portal-main", timeline) {
            int t = 0;
            float rewardYaw = 0f;

            @Override
            protected void render(AnimationFrame frame) {
                t = frame.tick();
                if (timeline.in("explosion", t)) {

                    if (t == 1) {
                        for (int i = 0; i < ITEM_COUNT; i++) {
                            alive[i] = true;
                            double angle  = (2 * Math.PI * i) / ITEM_COUNT + rng.nextDouble() * 0.5;
                            double hSpeed = 0.08 + rng.nextDouble() * 0.13;
                            vx[i] = Math.cos(angle) * hSpeed;
                            vy[i] = 0.10 + rng.nextDouble() * 0.18;
                            vz[i] = Math.sin(angle) * hSpeed;
                            setItemScale(items.get(i), 0.35f);
                        }

                        Location boom = base.clone().add(0.5, 1.0, 0.5);
                        w.spawnParticle(particle(EXPLOSION),    boom, 2, 0.1, 0.1, 0.1, 0);
                        w.spawnParticle(particle(SMOKE),        boom, 55, 0.45, 0.3, 0.45, 0.09);
                        w.spawnParticle(Particle.LAVA,         boom, 25, 0.3, 0.2, 0.3, 0.0);
                        w.spawnParticle(Particle.ELECTRIC_SPARK, boom, 40, 0.4, 0.2, 0.4, 0.12);
                        for (int ring = 0; ring < 20; ring++) {
                            double a = (2 * Math.PI * ring) / 20;
                            w.spawnParticle(particle(SMOKE),
                                    base.getX() + 0.5 + 0.7 * Math.cos(a), base.getY() + 0.5,
                                    base.getZ() + 0.5 + 0.7 * Math.sin(a),
                                    2, 0, 0.05, 0, 0.07);
                        }
                        playSound(w, base, Sound.ENTITY_GENERIC_EXPLODE, 0.34f, 0.75f);
                        playSound(w, base, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.26f, 0.5f);
                        playSound(player, player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.24f, 0.8f);
                    }

                    double floor = base.getY() + 0.25;
                    for (int i = 0; i < ITEM_COUNT; i++) {
                        if (!alive[i]) continue;
                        vy[i] -= 0.018;
                        px[i] += vx[i]; py[i] += vy[i]; pz[i] += vz[i];

                        if (py[i] < floor) {
                            py[i] = floor;
                            vy[i] = -vy[i] * 0.30;
                            vx[i] *= 0.80; vz[i] *= 0.80;
                            if (Math.abs(vy[i]) < 0.015) vy[i] = 0;
                            AnimationParticles.block(w, particle(BLOCK),
                                    new Location(w, px[i], floor, pz[i]),
                                    5, 0.1, 0, 0.1, 0.03, Material.GRAVEL.createBlockData());
                        }

                        ItemDisplay id = items.get(i);
                        if (!id.isDead()) {
                            id.teleport(new Location(w, px[i], py[i], pz[i]));
                            id.setRotation(id.getLocation().getYaw() + 5f, 0f);
                        }
                    }

                    if (t % 4 == 0)
                        w.spawnParticle(particle(SMOKE), base.clone().add(0.5, 0.8, 0.5), 3, 0.5, 0.1, 0.5, 0.02);
                }

                if (timeline.in("vortex", t)) {
                    double progress = timeline.progress("vortex", t);
                    double eased    = progress * progress;

                    setSingularityScale(singularity, (float)(eased * 0.42));

                    double ringR = 1.45 - eased * 0.55;
                    int pts = (int)(progress * 30 + 10);
                    for (int i = 0; i < pts; i++) {
                        if (rng.nextInt(2) == 0) continue;
                        double a = (2 * Math.PI * i) / pts + t * 0.28;
                        w.spawnParticle(Particle.PORTAL,
                                centre.getX() + ringR * Math.cos(a),
                                centre.getY() + (rng.nextDouble() - 0.5) * 0.12,
                                centre.getZ() + ringR * Math.sin(a),
                                1, 0, 0, 0, 0);
                        if (i % 3 == 0)
                            w.spawnParticle(Particle.REVERSE_PORTAL,
                                    centre.getX() + ringR * 0.65 * Math.cos(a + 0.22),
                                    centre.getY() + (rng.nextDouble() - 0.5) * 0.18,
                                    centre.getZ() + ringR * 0.65 * Math.sin(a + 0.22),
                                    1, 0, 0, 0, 0);
                    }
                    if (t % 2 == 0)
                        w.spawnParticle(particle(SMOKE), centre, 3, 0.12, 0.08, 0.12, 0.015);

                    double earthGrav = 0.018 * (1.0 - eased * 0.92);
                    double pullStr   = 0.002 + eased * 0.026;

                    double floor = base.getY() + 0.25;
                    for (int i = 0; i < ITEM_COUNT; i++) {
                        if (!alive[i]) continue;

                        vy[i] -= earthGrav;
                        if (py[i] < floor) { py[i] = floor; vy[i] = Math.abs(vy[i]) * 0.22; }

                        double dx = centre.getX() - px[i];
                        double dy = centre.getY() - py[i];
                        double dz = centre.getZ() - pz[i];
                        double dist = Math.max(0.01, Math.sqrt(dx*dx + dy*dy + dz*dz));

                        double pull = pullStr / Math.max(0.30, dist);
                        vx[i] += dx/dist * pull;
                        vy[i] += dy/dist * pull;
                        vz[i] += dz/dist * pull;

                        double tang = 0.009 * (1.0 - eased);
                        vx[i] += -dz/dist * tang;
                        vz[i] +=  dx/dist * tang;

                        double speed = Math.sqrt(vx[i]*vx[i] + vy[i]*vy[i] + vz[i]*vz[i]);
                        if (speed > 0.40) { double s = 0.40/speed; vx[i]*=s; vy[i]*=s; vz[i]*=s; }

                        px[i] += vx[i]; py[i] += vy[i]; pz[i] += vz[i];

                        ItemDisplay id = items.get(i);
                        if (!id.isDead()) {
                            id.teleport(new Location(w, px[i], py[i], pz[i]));
                            id.setRotation(id.getLocation().getYaw() + 8f, 0f);
                        }
                    }

                    if (t == timeline.start("vortex")) {
                        playSound(w, base, Sound.ENTITY_WITHER_AMBIENT, 0.18f, 0.4f);
                        playSound(w, base, Sound.BLOCK_PORTAL_AMBIENT, 0.24f, 0.3f);
                    }
                    if (t % 10 == 0)
                        playSound(w, base, Sound.BLOCK_PORTAL_AMBIENT, 0.16f, 0.35f + (float)eased * 0.35f);
                }

                if (timeline.in("suction", t)) {
                    double progress = timeline.progress("suction", t);


                    setSingularityScale(singularity, 0.42f + (float)(0.08 * Math.sin(t * 0.45)));

                    double vortexR = 1.1 - progress * 0.55;
                    for (int i = 0; i < 48; i++) {
                        if (rng.nextInt(2) != 0) continue;
                        double a = (2 * Math.PI * i) / 48 + t * 0.44;
                        w.spawnParticle(Particle.PORTAL,
                                centre.getX() + vortexR * Math.cos(a),
                                centre.getY() + (rng.nextDouble() - 0.5) * 0.10,
                                centre.getZ() + vortexR * Math.sin(a),
                                1, 0, 0, 0, 0.03);
                    }
                    if (t % 2 == 0)
                        w.spawnParticle(particle(SMOKE), centre, 4, 0.15, 0.10, 0.15, 0.02);

                    for (int i = 0; i < ITEM_COUNT; i++) {
                        if (!alive[i]) continue;

                        double dx   = centre.getX() - px[i];
                        double dy   = centre.getY() - py[i];
                        double dz   = centre.getZ() - pz[i];
                        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);

                        if (dist < 0.30) {
                            alive[i] = false;
                            safeRemove(items.get(i));
                            w.spawnParticle(Particle.PORTAL,         centre, 18, 0.12, 0.10, 0.12, 0.18);
                            w.spawnParticle(Particle.REVERSE_PORTAL, centre, 10, 0.10, 0.08, 0.10, 0.12);
                            w.spawnParticle(particle(SMOKE),          centre,  6, 0.08, 0.06, 0.08, 0.03);
                            if (rng.nextInt(3) == 0)
                                playSound(w, base, Sound.ENTITY_ENDERMAN_TELEPORT,
                                        0.18f, 1.4f + rng.nextFloat() * 0.6f);
                            continue;
                        }

                        dist = Math.max(0.01, dist);
                        double pull = 0.05 / Math.max(0.15, dist);
                        vx[i] += dx/dist * pull;
                        vy[i] += dy/dist * pull;
                        vz[i] += dz/dist * pull;

                        double tang = 0.016 / Math.max(0.20, dist);
                        vx[i] += -dz/dist * tang;
                        vz[i] +=  dx/dist * tang;

                        double speed = Math.sqrt(vx[i]*vx[i] + vy[i]*vy[i] + vz[i]*vz[i]);
                        double maxSp = 0.22 + progress * 0.38;
                        if (speed > maxSp) { double s = maxSp/speed; vx[i]*=s; vy[i]*=s; vz[i]*=s; }

                        px[i] += vx[i]; py[i] += vy[i]; pz[i] += vz[i];

                        ItemDisplay id = items.get(i);
                        if (!id.isDead()) {
                            float sc = Math.min(0.35f, (float)(dist * 0.18f));
                            setItemScale(id, sc);
                            id.teleport(new Location(w, px[i], py[i], pz[i]));
                            id.setRotation(id.getLocation().getYaw() + 12f, 0f);
                        }
                    }

                    if (t % 6 == 0)
                        playSound(w, base, Sound.BLOCK_PORTAL_AMBIENT, 0.18f, 0.6f + (float)progress * 0.55f);
                }

                if (timeline.in("collapse", t)) {
                    double progress = timeline.progress("collapse", t);

                    if (t == timeline.start("collapse")) {
                        for (int i = 0; i < ITEM_COUNT; i++)
                            if (alive[i]) { safeRemove(items.get(i)); alive[i] = false; }

                        w.spawnParticle(particle(EXPLOSION),      centre, 1, 0, 0, 0, 0);
                        w.spawnParticle(Particle.PORTAL,         centre, 90, 0.6, 0.4, 0.6, 0.18);
                        w.spawnParticle(Particle.REVERSE_PORTAL, centre, 65, 0.5, 0.3, 0.5, 0.13);
                        w.spawnParticle(Particle.END_ROD,        centre, 55, 0.5, 0.4, 0.5, 0.09);
                        w.spawnParticle(particle(SMOKE),          centre, 45, 0.5, 0.3, 0.5, 0.07);
                        for (int ring = 0; ring < 36; ring++) {
                            double a = (2 * Math.PI * ring) / 36;
                            w.spawnParticle(Particle.END_ROD,
                                    centre.getX() + 0.9 * Math.cos(a), centre.getY(),
                                    centre.getZ() + 0.9 * Math.sin(a),
                                    3, 0, 0.07, 0, 0.10);
                        }
                        playSound(w, base, Sound.ENTITY_WITHER_DEATH, 0.16f, 1.9f);
                        playSound(w, base, Sound.BLOCK_PORTAL_TRAVEL, 0.24f, 1.6f);
                        playSound(w, base, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.14f, 1.8f);
                        playSound(player, player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.20f, 0.6f);
                    }

                    float sc = Math.max(0f, 0.42f - (float)progress * 0.42f);
                    setSingularityScale(singularity, sc);

                    if (t > timeline.start("collapse") + 7) {
                        double sub = (t - timeline.start("collapse") - 7.0) / (timeline.phase("collapse").end() - timeline.phase("collapse").start() - 7.0);
                        setItemScale(rewardDisplay, (float)(sub * 0.9));
                        rewardDisplay.teleport(centre.clone());
                    }

                    if (t % 2 == 0) {
                        double r = 0.5 * (1.0 - progress);
                        for (int i = 0; i < 8; i++) {
                            double a = (2 * Math.PI * i) / 8 + t * 0.5;
                            w.spawnParticle(Particle.PORTAL,
                                    centre.getX() + r * Math.cos(a), centre.getY(),
                                    centre.getZ() + r * Math.sin(a),
                                    1, 0, 0, 0, 0.06);
                        }
                    }
                }

                if (timeline.in("reveal", t)) {
                    double progress = timeline.progress("reveal", t);
                    double eased    = 1.0 - Math.pow(1.0 - progress, 2);

                    if (t == timeline.start("reveal")) {
                        safeRemove(singularity);
                        w.spawnParticle(Particle.END_ROD, centre, 12, 0.4, 0.35, 0.4, 0.045);
                        for (int ring = 0; ring < 12; ring++) {
                            double a = (2 * Math.PI * ring) / 12;
                            w.spawnParticle(Particle.REVERSE_PORTAL,
                                    centre.getX() + 0.8 * Math.cos(a), centre.getY(),
                                    centre.getZ() + 0.8 * Math.sin(a),
                                    1, 0, 0.03, 0, 0.04);
                        }
                        playSound(w, base, Sound.ENTITY_PLAYER_LEVELUP, 0.32f, 1.5f);
                        playSound(w, base, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.30f, 1.2f);
                        playSound(player, player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.32f, 1.1f);
                    }

                    Location rewardLoc = base.clone().add(0, 2.0 + eased * 1.8, 0);
                    rewardDisplay.teleport(rewardLoc);
                    if (rewardLabel != null) rewardLabel.showAbove(rewardLoc, 0.88);
                    rewardDisplay.setBillboard(Display.Billboard.FIXED);

                    rewardYaw += 4.0f + (float)((1.0 - eased) * 5.5f);
                    if (rewardYaw >= 360f) rewardYaw -= 360f;
                    rewardDisplay.setTransformation(new org.bukkit.util.Transformation(
                            new Vector3f(0, 0, 0),
                            new AxisAngle4f(0, 0, 1, 0),
                            new Vector3f(1.0f, 1.0f, 1.0f),
                            new AxisAngle4f((float) Math.toRadians(rewardYaw), 0, 1, 0)
                    ));

                    double auraR = 0.52 + 0.13 * Math.sin(t * 0.21);
                    if (t % 3 == 0) {
                        for (int i = 0; i < 6; i++) {
                            double a  = (2 * Math.PI * i) / 6 + t * 0.11;
                            double py2 = rewardLoc.getY() + 0.18 * Math.sin(t * 0.17 + i);
                            w.spawnParticle(Particle.PORTAL,
                                    rewardLoc.getX() + auraR * Math.cos(a), py2,
                                    rewardLoc.getZ() + auraR * Math.sin(a),
                                    1, 0, 0, 0, 0);
                        }
                    }
                    double outerR = auraR * 1.6;
                    if (t % 4 == 0) {
                        for (int i = 0; i < 4; i++) {
                            double a = (2 * Math.PI * i) / 4 - t * 0.09;
                            w.spawnParticle(Particle.REVERSE_PORTAL,
                                    rewardLoc.getX() + outerR * Math.cos(a),
                                    rewardLoc.getY() + 0.1 * Math.sin(t * 0.21 + i),
                                    rewardLoc.getZ() + outerR * Math.sin(a),
                                    1, 0, 0, 0, 0);
                        }
                    }
                    if (t % 6 == 0)
                        w.spawnParticle(Particle.END_ROD, rewardLoc, 1, 0.15, 0.10, 0.15, 0.025);

                    if (t % 8 == 0)
                        playSound(w, base, Sound.BLOCK_PORTAL_AMBIENT, 0.10f, 1.8f);
                }

                if (t >= timeline.duration()) {
                    Location finalLoc = base.clone().add(0, 3.8, 0);
                    w.spawnParticle(Particle.PORTAL,         finalLoc, 12, 0.45, 0.30, 0.45, 0.06);
                    w.spawnParticle(Particle.END_ROD,        finalLoc, 8, 0.35, 0.28, 0.35, 0.04);
                    w.spawnParticle(Particle.REVERSE_PORTAL, finalLoc, 8, 0.40, 0.25, 0.40, 0.045);
                    playSound(w, base, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.32f, 1.0f);
                    playSound(w, base, Sound.ENTITY_PLAYER_LEVELUP, 0.30f, 1.5f);
                    playSound(player, player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.32f, 1.5f);
                }
            }
        });
    }

    private static void setItemScale(ItemDisplay id, float scale) {
        org.bukkit.util.Transformation tf = id.getTransformation();
        id.setTransformation(new org.bukkit.util.Transformation(
                tf.getTranslation(), tf.getLeftRotation(),
                new Vector3f(scale, scale, scale), tf.getRightRotation()
        ));
    }

    private static void setSingularityScale(BlockDisplay bd, float scale) {
        float half = scale / 2f;
        org.bukkit.util.Transformation tf = bd.getTransformation();
        bd.setTransformation(new org.bukkit.util.Transformation(
                new Vector3f(-half, -half, -half),
                tf.getLeftRotation(),
                new Vector3f(scale, scale, scale),
                tf.getRightRotation()
        ));
    }

    private static void safeRemove(Entity entity) {
        removeEntity(entity);
    }
}
