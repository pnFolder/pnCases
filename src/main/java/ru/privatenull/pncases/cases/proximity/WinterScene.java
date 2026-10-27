package ru.privatenull.pncases.cases.proximity;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.privatenull.pncases.cases.animation.effect.AnimationEffects;
import ru.privatenull.pncases.cases.model.ProximitySettings;
import ru.privatenull.pncases.support.compat.BukkitCompat.ParticleCompat;
import ru.privatenull.pncases.support.text.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Winter scene: snow mounds, spruces with a star, snowmen, ice crystals, a glowing garland,
 * orbiting snowflakes, gifts, a waving Ded Moroz, changed ground blocks, snowfall, a wind swirl
 * and a looping carol. Every element can be tuned from the case configuration.
 */
final class WinterScene extends AmbientScene {

    private static final int RING_STEPS = 14;
    private static final double[] PENTATONIC = {0.707, 0.794, 0.891, 1.059, 1.189, 1.414, 1.587, 1.782};
    private static final Material[] GIFT_COLORS = {
            Material.RED_CONCRETE, Material.LIME_CONCRETE, Material.LIGHT_BLUE_CONCRETE,
            Material.PURPLE_CONCRETE, Material.ORANGE_CONCRETE, Material.PINK_CONCRETE
    };

    WinterScene(ProximitySettings settings, SceneMode mode) {
        super(settings, mode, TerrainPalette.WINTER);
    }

    @Override
    void buildDecor() {
        ProximitySettings.Decor decor = settings.decor();
        if (settings.santa().enabled()) buildSanta();
        buildMounds(decor.mounds());
        buildTrees(decor.trees());
        buildSnowmen(decor.snowmen());
        buildCrystals(decor.crystals());
        buildGifts(decor.gifts());
        buildGarland(decor.garland());
        buildSnowflakes(decor.snowflakes());
    }

    @Override
    void spawnEffects() {
        effect(Sound.BLOCK_POWDER_SNOW_BREAK, origin, 0.9f, 0.7f);
        effect(Sound.BLOCK_GLASS_BREAK, origin, 0.25f, 1.9f);
    }

    @Override
    void clearSound() {
        effect(Sound.BLOCK_SNOW_BREAK, origin, 0.5f, 0.8f);
    }

    @Override
    String defaultMessage() {
        return "&bХо-хо-хо!";
    }

    @Override
    int tuneCount() {
        return WinterMusic.tunes();
    }

    @Override
    WinterMusic.Tune tuneAt(int index) {
        return WinterMusic.tune(index);
    }

    @Override
    void playNote(WinterMusic.Note note, float pitch, float volume, Location at) {
        tone(Sound.BLOCK_NOTE_BLOCK_CHIME, at, 0.9f * volume, pitch);
        tone(Sound.BLOCK_NOTE_BLOCK_BELL, at, 0.45f * volume, pitch);
        if (note.length() >= 16) {
            tone(Sound.BLOCK_NOTE_BLOCK_HARP, at, 0.35f * volume, pitch * 0.5f);
        }
    }

    @Override
    void playBass(int step, float volume) {
        tone(Sound.BLOCK_NOTE_BLOCK_BASS, origin, 0.7f * volume, WinterMusic.pitch(step % 2 == 0 ? 7 : 14));
    }

    private void buildMounds(int count) {
        for (int i = 0; i < count; i++) {
            double angle = turn + Math.PI * 2.0 * i / count + layout.nextDouble(-0.18, 0.18);
            double distance = 1.55 + layout.nextDouble() * 0.85;
            double gx = Math.cos(angle) * distance;
            double gz = Math.sin(angle) * distance;
            double width = 0.75 + layout.nextDouble() * 0.55;
            double height = 0.3 + layout.nextDouble() * 0.25;
            double yaw = layout.nextDouble() * Math.PI;
            double delay = 0.02 + i * 0.025 / Math.max(1.0, count / 11.0);
            add(block(Material.SNOW_BLOCK, Kind.MOUND, Anim.STATIC, gx, gz, yaw, delay, 7, true,
                    0, 0, 0, width, height, width, null));
            add(block(Material.SNOW_BLOCK, Kind.MOUND, Anim.STATIC, gx, gz, yaw + 0.6, delay + 0.03, 7, false,
                    0, height, 0, width * 0.55, height * 0.8, width * 0.55, null));
        }
    }

    private void buildTrees(int count) {
        for (int i = 0; i < count; i++) {
            double angle = turn + Math.PI / 4.0 + i * Math.PI * 2.0 / count + layout.nextDouble(-0.15, 0.15);
            double distance = 2.95 + layout.nextDouble() * 0.2;
            double gx = Math.cos(angle) * distance;
            double gz = Math.sin(angle) * distance;
            double s = 1.05 + layout.nextDouble() * 0.45;
            double yaw = layout.nextDouble() * Math.PI;
            double delay = 0.18 + i * 0.05;
            add(block(Material.SPRUCE_LOG, Kind.TREE, Anim.STATIC, gx, gz, yaw, delay, 6, true,
                    0, 0, 0, 0.18 * s, 0.4 * s, 0.18 * s, null));
            add(block(Material.SPRUCE_LEAVES, Kind.TREE, Anim.STATIC, gx, gz, yaw, delay + 0.02, 6, false,
                    0, 0.3 * s, 0, 0.9 * s, 0.3 * s, 0.9 * s, null));
            add(block(Material.SPRUCE_LEAVES, Kind.TREE, Anim.STATIC, gx, gz, yaw + 0.4, delay + 0.04, 6, false,
                    0, 0.56 * s, 0, 0.66 * s, 0.28 * s, 0.66 * s, null));
            add(block(Material.SPRUCE_LEAVES, Kind.TREE, Anim.STATIC, gx, gz, yaw + 0.8, delay + 0.06, 6, false,
                    0, 0.81 * s, 0, 0.42 * s, 0.26 * s, 0.42 * s, null));
            add(block(Material.SNOW_BLOCK, Kind.TREE, Anim.STATIC, gx, gz, yaw, delay + 0.08, 7, false,
                    0, 1.05 * s, 0, 0.26 * s, 0.07 * s, 0.26 * s, null));
            Quaternionf star = new Quaternionf().rotationX(0.78f).rotateZ(0.78f);
            add(block(Material.GLOWSTONE, Kind.TREE, Anim.TWINKLE, gx, gz, yaw, delay + 0.1, 15, false,
                    0, 1.1 * s, 0, 0.13 * s, 0.13 * s, 0.13 * s, star));
        }
    }

    private void buildSnowmen(int count) {
        int placed = 0;
        for (int attempt = 0; attempt < count * 4 && placed < count; attempt++) {
            double angle = turn + -0.5 + attempt * (Math.PI * 2.0 / Math.max(1, count));
            if (settings.santa().enabled() && angularDistance(angle, santaAngle) < 0.8) continue;
            addSnowman(angle, 0.3 + placed * 0.04);
            placed++;
        }
    }

    private void addSnowman(double angle, double d) {
        double distance = 2.25;
        double gx = Math.cos(angle) * distance;
        double gz = Math.sin(angle) * distance;
        double yaw = Math.atan2(-gx, -gz);
        add(block(Material.SNOW_BLOCK, Kind.SNOWMAN, Anim.STATIC, gx, gz, yaw, d, 7, true,
                0, 0, 0, 0.56, 0.5, 0.56, null));
        add(block(Material.SNOW_BLOCK, Kind.SNOWMAN, Anim.STATIC, gx, gz, yaw, d + 0.03, 7, false,
                0, 0.46, 0, 0.42, 0.4, 0.42, null));
        add(block(Material.SNOW_BLOCK, Kind.SNOWMAN, Anim.STATIC, gx, gz, yaw, d + 0.06, 7, false,
                0, 0.82, 0, 0.32, 0.32, 0.32, null));
        add(block(Material.BLACK_CONCRETE, Kind.SNOWMAN, Anim.STATIC, gx, gz, yaw, d + 0.08, 4, false,
                -0.07, 1.0, 0.155, 0.05, 0.05, 0.03, null));
        add(block(Material.BLACK_CONCRETE, Kind.SNOWMAN, Anim.STATIC, gx, gz, yaw, d + 0.08, 4, false,
                0.07, 1.0, 0.155, 0.05, 0.05, 0.03, null));
        add(block(Material.ORANGE_CONCRETE, Kind.SNOWMAN, Anim.STATIC, gx, gz, yaw, d + 0.09, 9, false,
                0, 0.93, 0.19, 0.05, 0.05, 0.16, null));
        add(block(Material.BLACK_CONCRETE, Kind.SNOWMAN, Anim.STATIC, gx, gz, yaw, d + 0.09, 4, false,
                0, 0.62, 0.215, 0.05, 0.05, 0.03, null));
        add(block(Material.BLACK_CONCRETE, Kind.SNOWMAN, Anim.STATIC, gx, gz, yaw, d + 0.09, 4, false,
                0, 0.5, 0.215, 0.05, 0.05, 0.03, null));
        add(block(Material.RED_WOOL, Kind.SNOWMAN, Anim.STATIC, gx, gz, yaw, d + 0.1, 7, false,
                0, 0.77, 0, 0.38, 0.08, 0.38, null));
        add(block(Material.RED_WOOL, Kind.SNOWMAN, Anim.STATIC, gx, gz, yaw, d + 0.11, 7, false,
                0.1, 0.56, 0.19, 0.08, 0.22, 0.04, null));
        add(block(Material.BLACK_CONCRETE, Kind.SNOWMAN, Anim.STATIC, gx, gz, yaw, d + 0.12, 4, false,
                0, 1.13, 0, 0.4, 0.03, 0.4, null));
        add(block(Material.BLACK_CONCRETE, Kind.SNOWMAN, Anim.STATIC, gx, gz, yaw, d + 0.13, 4, false,
                0, 1.15, 0, 0.24, 0.22, 0.24, null));
        add(block(Material.DARK_OAK_PLANKS, Kind.SNOWMAN, Anim.STATIC, gx, gz, yaw, d + 0.1, 5, false,
                0.36, 0.72, 0, 0.45, 0.04, 0.04, new Quaternionf().rotationZ(0.55f)));
        add(block(Material.DARK_OAK_PLANKS, Kind.SNOWMAN, Anim.STATIC, gx, gz, yaw, d + 0.1, 5, false,
                -0.36, 0.72, 0, 0.45, 0.04, 0.04, new Quaternionf().rotationZ(-0.55f)));
    }

    private void buildCrystals(int clusters) {
        Material[] materials = {Material.BLUE_ICE, Material.PACKED_ICE, Material.ICE};
        for (int cluster = 0; cluster < clusters; cluster++) {
            double angle = turn + 0.2 + cluster * Math.PI * 2.0 / clusters + layout.nextDouble(-0.15, 0.15);
            double distance = 1.55 + layout.nextDouble() * 0.2;
            double gx = Math.cos(angle) * distance;
            double gz = Math.sin(angle) * distance;
            for (int i = 0; i < 3; i++) {
                double yaw = layout.nextDouble() * Math.PI * 2.0;
                double height = 0.35 + layout.nextDouble() * 0.5;
                Quaternionf tilt = new Quaternionf()
                        .rotationX((float) layout.nextDouble(-0.35, 0.35))
                        .rotateZ((float) layout.nextDouble(-0.35, 0.35));
                Part part = block(materials[(cluster + i) % materials.length], Kind.CRYSTAL, Anim.STATIC,
                        gx, gz, yaw, 0.35 + cluster * 0.04 + i * 0.02, 12, i == 0,
                        (i - 1) * 0.16, 0, layout.nextDouble(-0.1, 0.1), 0.15, height, 0.15, tilt);
                add(part);
                crystals.add(part);
            }
        }
    }

    /** Replaces the plain case block by a model; the block itself becomes an invisible, clickable barrier. */
    @Override
    void buildCaseModel(ProximitySettings.CaseStyle style) {
        Material color = settings.terrain().caseColor();
        switch (style) {
            case GIFT, BASKET -> {
                Material ribbon = Material.WHITE_CONCRETE;
                caseBlock(color, 0, 0, 1.02, 0.86, 1.02, 0.03, 9, true, Anim.STATIC, 0);
                caseBlock(ribbon, 0, 0, 0.17, 0.88, 1.04, 0.04, 12, false, Anim.STATIC, 0);
                caseBlock(ribbon, 0, 0, 1.04, 0.88, 0.17, 0.04, 12, false, Anim.STATIC, 0);
                caseBlock(color, 0, 0.8, 1.06, 0.24, 1.06, 0.07, 9, false, Anim.BOB, 0.04);
                caseBlock(ribbon, 0, 0.8, 0.18, 0.25, 1.08, 0.08, 12, false, Anim.BOB, 0.04);
                caseBlock(ribbon, 0, 0.8, 1.08, 0.25, 0.18, 0.08, 12, false, Anim.BOB, 0.04);
                caseBlock(Material.YELLOW_CONCRETE, 0, 1.03, 0.16, 0.12, 0.16, 0.1, 15, false, Anim.BOB, 0.04);
                Part left = caseBlock(Material.YELLOW_CONCRETE, -0.15, 1.05, 0.3, 0.1, 0.14, 0.11, 15, false, Anim.BOB, 0.04);
                left.tilt = new Quaternionf().rotationZ(0.65f);
                Part right = caseBlock(Material.YELLOW_CONCRETE, 0.15, 1.05, 0.3, 0.1, 0.14, 0.11, 15, false, Anim.BOB, 0.04);
                right.tilt = new Quaternionf().rotationZ(-0.65f);
            }
            case GLOBE -> {
                caseBlock(Material.DARK_OAK_PLANKS, 0, 0, 1.02, 0.14, 1.02, 0.03, 7, true, Anim.STATIC, 0);
                caseBlock(Material.SPRUCE_PLANKS, 0, 0.14, 0.92, 0.06, 0.92, 0.04, 8, false, Anim.STATIC, 0);
                caseBlock(Material.SNOW_BLOCK, 0, 0.2, 0.8, 0.1, 0.8, 0.05, 14, false, Anim.STATIC, 0);
                caseBlock(Material.SPRUCE_LOG, 0, 0.3, 0.08, 0.12, 0.08, 0.06, 8, false, Anim.STATIC, 0);
                caseBlock(Material.SPRUCE_LEAVES, 0, 0.38, 0.36, 0.14, 0.36, 0.07, 8, false, Anim.STATIC, 0);
                caseBlock(Material.SPRUCE_LEAVES, 0, 0.5, 0.26, 0.13, 0.26, 0.08, 8, false, Anim.STATIC, 0);
                caseBlock(Material.SPRUCE_LEAVES, 0, 0.61, 0.16, 0.12, 0.16, 0.09, 8, false, Anim.STATIC, 0);
                caseBlock(Material.GLOWSTONE, 0, 0.72, 0.07, 0.07, 0.07, 0.1, 15, false, Anim.STATIC, 0);
                Part gift = caseBlock(Material.RED_CONCRETE, 0.22, 0.3, 0.15, 0.14, 0.15, 0.08, 10, false, Anim.STATIC, 0);
                gift.local.z = (float) (0.12 * caseScale);
                Part bow = caseBlock(Material.YELLOW_CONCRETE, 0.22, 0.44, 0.06, 0.04, 0.06, 0.09, 15, false, Anim.STATIC, 0);
                bow.local.z = (float) (0.12 * caseScale);
                caseBlock(Material.LIGHT_BLUE_STAINED_GLASS, 0, 0.22, 0.9, 0.62, 0.9, 0.12, 12, false, Anim.STATIC, 0);
                caseBlock(Material.LIGHT_BLUE_STAINED_GLASS, 0, 0.84, 0.64, 0.16, 0.64, 0.13, 12, false, Anim.STATIC, 0);
                caseBlock(Material.LIGHT_BLUE_STAINED_GLASS, 0, 0.98, 0.34, 0.08, 0.34, 0.14, 12, false, Anim.STATIC, 0);
                for (int i = 0; i < 3; i++) {
                    caseItem(Material.SNOWBALL, 0.3, 0.55 + i * 0.12, 0.03 + i * 0.012, 0.16, 0.16 + i * 0.03);
                }
            }
            case CRYSTAL -> {
                caseBlock(Material.SNOW_BLOCK, 0, 0, 0.92, 0.14, 0.92, 0.03, 12, true, Anim.STATIC, 0);
                caseBlock(Material.PACKED_ICE, 0, 0.14, 0.72, 0.1, 0.72, 0.05, 12, false, Anim.STATIC, 0);
                caseBlock(Material.BLUE_ICE, 0, 0.24, 0.5, 0.08, 0.5, 0.07, 14, false, Anim.STATIC, 0);
                Part core = caseBlock(Material.BLUE_ICE, 0, 0.7, 0.36, 0.36, 0.36, 0.1, 15, false, Anim.SPIN, 0.06);
                core.tilt = new Quaternionf().rotationX(0.78f).rotateZ(0.78f);
                core.speed = 0.05;
                Part spike = caseBlock(Material.LIGHT_BLUE_CONCRETE, 0, 0.7, 0.16, 0.8, 0.16, 0.12, 15, false, Anim.SPIN, 0.06);
                spike.speed = 0.05;
                for (int i = 0; i < 4; i++) {
                    double angle = turn + i * Math.PI / 2.0 + 0.4;
                    Part shard = caseBlock(Material.ICE, Math.cos(angle) * 0.36, 0.5 + (i % 2) * 0.18,
                            0.14, 0.3, 0.14, 0.14 + i * 0.01, 14, false, Anim.SPIN, 0.05);
                    shard.local.z = (float) (Math.sin(angle) * 0.36 * caseScale);
                    shard.tilt = new Quaternionf().rotationZ((float) (0.4 * (i % 2 == 0 ? 1 : -1)));
                    shard.speed = -0.04;
                    shard.phase = i;
                }
            }
            default -> {
            }
        }
    }

    private void buildGifts(int count) {
        for (int i = 0; i < count; i++) {
            double angle = santaAngle + 0.35 + i * 0.55 + layout.nextDouble(-0.1, 0.1);
            double distance = 1.85 + layout.nextDouble() * 0.35;
            double gx = Math.cos(angle) * distance;
            double gz = Math.sin(angle) * distance;
            double size = 0.2 + layout.nextDouble() * 0.14;
            double yaw = layout.nextDouble() * Math.PI;
            double delay = 0.4 + i * 0.03;
            Material color = GIFT_COLORS[i % GIFT_COLORS.length];
            add(block(color, Kind.GIFT, Anim.STATIC, gx, gz, yaw, delay, 8, true,
                    0, 0, 0, size, size, size, null));
            add(block(Material.WHITE_CONCRETE, Kind.GIFT, Anim.STATIC, gx, gz, yaw, delay + 0.01, 10, false,
                    0, -0.003, 0, 0.045, size + 0.012, size + 0.012, null));
            add(block(Material.WHITE_CONCRETE, Kind.GIFT, Anim.STATIC, gx, gz, yaw, delay + 0.01, 10, false,
                    0, -0.003, 0, size + 0.012, size + 0.012, 0.045, null));
            add(block(Material.YELLOW_CONCRETE, Kind.GIFT, Anim.STATIC, gx, gz, yaw, delay + 0.02, 12, false,
                    0, size + 0.01, 0, 0.09, 0.07, 0.09, new Quaternionf().rotationY(0.78f)));
        }
    }

    private void buildGarland(int count) {
        Material[] bulbs = {
                Material.RED_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE,
                Material.LIGHT_BLUE_CONCRETE, Material.MAGENTA_CONCRETE, Material.ORANGE_CONCRETE
        };
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count;
            double radius = 2.45;
            double sag = 1.0 + Math.sin(angle * 6.0) * 0.08;
            Part part = block(bulbs[i % bulbs.length], Kind.GARLAND, Anim.TWINKLE,
                    Math.cos(angle) * radius, Math.sin(angle) * radius, 0, 0.45 + i * 0.012, 15, false,
                    0, sag, 0, 0.1, 0.1, 0.1, null);
            part.phase = i * 0.9;
            add(part);
        }
    }

    private void buildSnowflakes(int count) {
        Material[] materials = {Material.SNOWBALL, Material.PRISMARINE_SHARD, Material.SNOWBALL, Material.QUARTZ};
        for (int i = 0; i < count; i++) {
            ItemDisplay display = world.spawn(origin, ItemDisplay.class, spawned -> {
                spawned.setBillboard(Display.Billboard.CENTER);
                spawned.setBrightness(new Display.Brightness(12, 15));
                prepare(spawned);
            });
            display.setItemStack(new ItemStack(materials[i % materials.length]));
            Part part = new Part(display, Kind.SNOWFLAKE, Anim.ORBIT_ITEM);
            part.size.set(0.34f, 0.34f, 0.34f);
            part.radius = radialRadius(1.05 + layout.nextDouble() * 0.9);
            part.height = 0.7 + layout.nextDouble() * 1.4;
            part.speed = (layout.nextBoolean() ? 1 : -1) * (0.03 + layout.nextDouble() * 0.03);
            part.phase = layout.nextDouble() * Math.PI * 2.0;
            part.delay = 0.5 + i * 0.02;
            parts.add(part);
            flakes.add(part);
        }
    }

    /** Ded Moroz: long coat, white fur trim, beard, hat, a staff and a mitten that waves at the player. */
    private void buildSanta() {
        double distance = 2.2;
        double gx = Math.cos(santaAngle) * distance;
        double gz = Math.sin(santaAngle) * distance;
        double yaw = Math.atan2(-gx, -gz);
        Material coat = settings.santa().coat();
        double d = 0.28;
        double k = 1.15;

        add(santaBlock(Material.BLACK_CONCRETE, gx, gz, yaw, d, 4, true, -0.1 * k, 0, 0.03 * k, 0.17 * k, 0.14 * k, 0.26 * k, null));
        add(santaBlock(Material.BLACK_CONCRETE, gx, gz, yaw, d, 4, false, 0.1 * k, 0, 0.03 * k, 0.17 * k, 0.14 * k, 0.26 * k, null));
        add(santaBlock(coat, gx, gz, yaw, d + 0.02, 7, false, 0, 0.12 * k, 0, 0.46 * k, 0.5 * k, 0.32 * k, null));
        add(santaBlock(Material.WHITE_WOOL, gx, gz, yaw, d + 0.03, 8, false, 0, 0.1 * k, 0, 0.5 * k, 0.07 * k, 0.36 * k, null));
        add(santaBlock(Material.WHITE_WOOL, gx, gz, yaw, d + 0.04, 8, false, 0, 0.17 * k, 0.165 * k, 0.07 * k, 0.45 * k, 0.025 * k, null));
        add(santaBlock(coat, gx, gz, yaw, d + 0.05, 7, false, 0, 0.55 * k, 0, 0.44 * k, 0.4 * k, 0.3 * k, null));
        add(santaBlock(Material.BLACK_CONCRETE, gx, gz, yaw, d + 0.06, 4, false, 0, 0.6 * k, 0, 0.47 * k, 0.07 * k, 0.33 * k, null));
        add(santaBlock(Material.GOLD_BLOCK, gx, gz, yaw, d + 0.07, 12, false, 0, 0.598 * k, 0.165 * k, 0.1 * k, 0.075 * k, 0.02 * k, null));
        add(santaBlock(Material.WHITE_WOOL, gx, gz, yaw, d + 0.06, 8, false, 0, 0.92 * k, 0, 0.5 * k, 0.07 * k, 0.34 * k, null));
        add(santaBlock(Material.PINK_TERRACOTTA, gx, gz, yaw, d + 0.08, 8, false, 0, 0.97 * k, 0, 0.3 * k, 0.28 * k, 0.28 * k, null));
        add(santaBlock(Material.WHITE_WOOL, gx, gz, yaw, d + 0.09, 10, false, 0, 0.7 * k, 0.15 * k, 0.32 * k, 0.4 * k, 0.07 * k, null));
        add(santaBlock(Material.WHITE_WOOL, gx, gz, yaw, d + 0.1, 10, false, 0, 1.1 * k, 0.15 * k, 0.24 * k, 0.05 * k, 0.06 * k, null));
        add(santaBlock(Material.BLUE_CONCRETE, gx, gz, yaw, d + 0.1, 9, false, -0.07 * k, 1.15 * k, 0.145 * k, 0.045 * k, 0.045 * k, 0.03 * k, null));
        add(santaBlock(Material.BLUE_CONCRETE, gx, gz, yaw, d + 0.1, 9, false, 0.07 * k, 1.15 * k, 0.145 * k, 0.045 * k, 0.045 * k, 0.03 * k, null));
        add(santaBlock(Material.RED_TERRACOTTA, gx, gz, yaw, d + 0.11, 9, false, 0, 1.06 * k, 0.15 * k, 0.05 * k, 0.05 * k, 0.04 * k, null));
        add(santaBlock(coat, gx, gz, yaw, d + 0.12, 7, false, 0, 1.22 * k, 0, 0.34 * k, 0.24 * k, 0.34 * k, null));
        add(santaBlock(Material.WHITE_WOOL, gx, gz, yaw, d + 0.13, 10, false, 0, 1.2 * k, 0, 0.37 * k, 0.07 * k, 0.37 * k, null));
        add(santaBlock(Material.WHITE_WOOL, gx, gz, yaw, d + 0.14, 12, false, 0, 1.46 * k, 0, 0.09 * k, 0.09 * k, 0.09 * k, null));

        add(santaBlock(coat, gx, gz, yaw, d + 0.1, 7, false, -0.28 * k, 0.55 * k, 0, 0.1 * k, 0.34 * k, 0.1 * k,
                new Quaternionf().rotationZ(0.18f)));
        add(santaBlock(Material.WHITE_WOOL, gx, gz, yaw, d + 0.11, 10, false, -0.3 * k, 0.5 * k, 0, 0.11 * k, 0.11 * k, 0.11 * k, null));

        Part arm = santaBlock(coat, gx, gz, yaw, d + 0.12, 7, false, 0.26 * k, 0.88 * k, 0, 0.1 * k, 0.32 * k, 0.1 * k, null);
        arm.anim = Anim.ARM;
        arm.yaw = new Quaternionf().rotationY((float) yaw);
        arm.waveBase = -2.45;
        arm.waveAmp = 0.38;
        arm.waveSpeed = 0.32;
        add(arm);
        Part hand = santaBlock(Material.WHITE_WOOL, gx, gz, yaw, d + 0.13, 10, false, 0.26 * k, 0.88 * k, 0, 0.12 * k, 0.12 * k, 0.12 * k, null);
        hand.anim = Anim.HAND;
        hand.link = arm;
        add(hand);

        Quaternionf lean = new Quaternionf().rotationZ(-0.1f);
        add(santaBlock(Material.DARK_OAK_PLANKS, gx, gz, yaw, d + 0.14, 5, false, -0.42 * k, 0, 0.1 * k, 0.045 * k, 1.35 * k, 0.045 * k, lean));
        add(santaBlock(Material.LIGHT_BLUE_CONCRETE, gx, gz, yaw, d + 0.15, 15, false, -0.5 * k, 1.31 * k, 0.1 * k, 0.12 * k, 0.12 * k, 0.12 * k,
                new Quaternionf().rotationY(0.78f)));

        // The speech bubble stands still above Ded Moroz and faces the case; it does not turn after the player.
        Quaternionf front = new Quaternionf().rotationY((float) yaw);
        String first = ColorUtil.colorize(nextSantaMessage());
        for (int side = 0; side < 1; side++) {
            TextDisplay text = world.spawn(origin, TextDisplay.class, spawned -> {
                spawned.setBillboard(Display.Billboard.FIXED);
                spawned.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
                spawned.setShadowed(true);
                spawned.setBrightness(new Display.Brightness(15, 15));
                prepare(spawned);
            });
            text.setText(first);
            Part bubble = new Part(text, Kind.SANTA, Anim.TEXT);
            double[] placedBubble = radial(gx, gz);
            bubble.gx = placedBubble[0];
            bubble.gz = placedBubble[1];
            bubble.local = new Vector3f(0f, (float) (1.95 * k) + groundShift(bubble.gx, bubble.gz), 0f);
            bubble.size.set(0.7f, 0.7f, 0.7f);
            bubble.tilt = new Quaternionf(front);
            bubble.delay = d + 0.2;
            parts.add(bubble);
            bubbles.add(bubble);
        }
    }

    // ------------------------------------------------------------ particles

    @Override
    void drawParticles(double eased) {
        ProximitySettings.Particles cfg = settings.particles();
        if (eased <= 0.02 || cfg.density() <= 0.0) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double density = cfg.density();

        if (cfg.snowfall()) {
            int fall = (int) Math.ceil(9 * eased * density);
            for (int i = 0; i < fall; i++) {
                double angle = random.nextDouble(Math.PI * 2.0);
                double distance = Math.sqrt(random.nextDouble()) * 3.4 * spread;
                Location at = origin.clone().add(Math.cos(angle) * distance, 0.6 + random.nextDouble() * 2.8,
                        Math.sin(angle) * distance);
                AnimationEffects.plain(world, Particle.SNOWFLAKE, at, 1, 0.04, 0.06, 0.04, 0.0);
            }
        }

        if (cfg.swirl()) {
            for (int arm = 0; arm < 3; arm++) {
                double phase = age * 0.11 + arm * Math.PI * 2.0 / 3.0;
                double rise = ((age * 0.02) + arm / 3.0) % 1.0;
                double radius = (1.0 + 1.5 * rise) * eased * spread;
                Location at = origin.clone().add(Math.cos(phase) * radius, 0.15 + rise * 2.4, Math.sin(phase) * radius);
                if (random.nextDouble() < density) {
                    AnimationEffects.plain(world, AnimationEffects.particle(AnimationEffects.Kind.POOF), at,
                            1, 0.02, 0.02, 0.02, 0.0);
                }
                AnimationEffects.plain(world, Particle.SNOWFLAKE, at, Math.max(1, (int) Math.round(2 * density)),
                        0.05, 0.05, 0.05, 0.0);
            }
        }

        if (cfg.glints()) {
            if (age % 4 == 0 && !flakes.isEmpty()) {
                Particle glint = ParticleCompat.first("END_ROD", "FIREWORKS_SPARK");
                for (int i = 0; i < Math.max(1, Math.round(3 * density)); i++) {
                    Part flake = flakes.get(random.nextInt(flakes.size()));
                    if (!flake.appeared) continue;
                    Location at = new Location(world, flake.worldX, flake.worldY, flake.worldZ);
                    AnimationEffects.plain(world, glint == null ? Particle.SNOWFLAKE : glint, at, 1, 0.02, 0.02, 0.02, 0.0);
                }
            }
            if (age % 10 == 0 && !crystals.isEmpty()) {
                Part crystal = crystals.get(random.nextInt(crystals.size()));
                if (crystal.appeared) {
                    Location at = origin.clone().add(crystal.gx + crystal.local.x, crystal.size.y * 0.8,
                            crystal.gz + crystal.local.z);
                    AnimationEffects.plain(world, AnimationEffects.particle(AnimationEffects.Kind.FIREWORK), at,
                            Math.max(1, (int) Math.round(2 * density)), 0.08, 0.1, 0.08, 0.01);
                }
            }
        }

        if (cfg.glints() && age % 6 == 0 && mode == SceneMode.ALL) drawCaseSparkles(eased, density);

        if (ringStep >= 0) {
            drawRing(ringStep, density);
            ringStep = ringStep + 1 >= RING_STEPS ? -1 : ringStep + 1;
        }
    }

    @Override
    void drawCaseSparkles(double eased, double density) {
        ProximitySettings.CaseStyle style = settings.terrain().caseStyle();
        if (style == ProximitySettings.CaseStyle.NONE || style == ProximitySettings.CaseStyle.BLOCK) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Particle glint = ParticleCompat.first("END_ROD", "FIREWORKS_SPARK");
        int count = Math.max(1, (int) Math.round(2 * density * eased));
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble(Math.PI * 2.0);
            double reach = style == ProximitySettings.CaseStyle.GLOBE ? 0.3 : 0.42;
            Location at = origin.clone().add(Math.cos(angle) * reach * random.nextDouble(),
                    style == ProximitySettings.CaseStyle.GIFT ? 1.0 : 0.5 + random.nextDouble() * 0.5,
                    Math.sin(angle) * reach * random.nextDouble());
            if (style == ProximitySettings.CaseStyle.GLOBE) {
                AnimationEffects.plain(world, Particle.SNOWFLAKE, at, 1, 0.08, 0.1, 0.08, 0.0);
            } else {
                AnimationEffects.plain(world, glint == null ? Particle.SNOWFLAKE : glint, at, 1, 0.05, 0.12, 0.05, 0.01);
            }
        }
    }

    private void drawRing(int step, double density) {
        double radius = (0.5 + step * 0.22) * spread;
        Particle dust = AnimationEffects.particle(AnimationEffects.Kind.DUST);
        Particle.DustOptions frost = new Particle.DustOptions(Color.fromRGB(190, 235, 255), 1.0f);
        int points = (int) Math.max(6, (18 + step * 3) * density);
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points;
            Location at = origin.clone().add(Math.cos(angle) * radius, 0.08, Math.sin(angle) * radius);
            AnimationEffects.dust(world, dust, at, 1, 0, 0, 0, 0, frost);
            if (i % 3 == 0) {
                AnimationEffects.plain(world, Particle.SNOWFLAKE, at.clone().add(0, 0.2, 0), 1, 0.05, 0.12, 0.05, 0.02);
            }
        }
    }

    @Override
    void playAmbient(double progress, boolean rising) {
        if (!rising || progress < 1.0) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (age % 20 == 0 && random.nextInt(7) == 0) {
            double pitch = PENTATONIC[random.nextInt(PENTATONIC.length)];
            effect(Sound.BLOCK_AMETHYST_BLOCK_CHIME, origin.clone().add(0, 1.2, 0), 0.5f, (float) pitch);
        }
        if (age % 80 == 0) {
            effect(Sound.BLOCK_POWDER_SNOW_STEP, origin.clone().add(0, 0.5, 0), 0.25f, 0.6f);
        }
    }

    @Override
    void greet() {
        if (!settings.sounds().enabled()) return;
        double[] santaAt = radial(Math.cos(santaAngle) * 2.2, Math.sin(santaAngle) * 2.2);
        Location at = origin.clone().add(santaAt[0], 1.6, santaAt[1]);
        effect(Sound.ENTITY_VILLAGER_YES, at, 0.8f, 0.55f);
        effect(Sound.BLOCK_NOTE_BLOCK_BELL, at, 0.5f, 1.7f);
        effect(Sound.BLOCK_AMETHYST_BLOCK_CHIME, at, 0.6f, 1.5f);
    }

    @Override
    void playAppear(Part part) {
        if (!part.leader || soundBudget <= 0) return;
        soundBudget--;
        Location at = origin.clone().add(part.gx, 0.3, part.gz);
        float pitch = 0.75f + (float) ThreadLocalRandom.current().nextDouble(0.4);
        switch (part.kind) {
            case MOUND, GIFT -> effect(Sound.BLOCK_SNOW_PLACE, at, 0.6f, pitch);
            case TREE -> {
                effect(Sound.BLOCK_GRASS_PLACE, at, 0.5f, 0.7f);
                effect(Sound.BLOCK_SNOW_PLACE, at, 0.4f, pitch);
            }
            case SNOWMAN -> {
                effect(Sound.BLOCK_SNOW_PLACE, at, 0.7f, 0.6f);
                effect(Sound.BLOCK_NOTE_BLOCK_BELL, at, 0.3f, 1.5f);
            }
            case SANTA -> effect(Sound.ENTITY_PLAYER_LEVELUP, at, 0.35f, 1.6f);
            case CASE -> {
                effect(Sound.BLOCK_AMETHYST_BLOCK_CHIME, origin.clone().add(0, 0.6, 0), 0.7f, 1.3f);
                effect(Sound.BLOCK_NOTE_BLOCK_BELL, origin.clone().add(0, 0.6, 0), 0.35f, 1.8f);
            }
            case CRYSTAL -> effect(Sound.BLOCK_AMETHYST_CLUSTER_PLACE, at, 0.6f,
                    (float) PENTATONIC[ThreadLocalRandom.current().nextInt(PENTATONIC.length)]);
            default -> {
            }
        }
    }

    @Override
    void playVanish(Part part) {
        if (!part.leader || soundBudget <= 0) return;
        soundBudget--;
        Location at = origin.clone().add(part.gx, 0.3, part.gz);
        if (part.kind == Kind.CRYSTAL) {
            effect(Sound.BLOCK_GLASS_BREAK, at, 0.2f, 1.8f);
        } else {
            effect(Sound.BLOCK_SNOW_BREAK, at, 0.4f, 0.9f + (float) ThreadLocalRandom.current().nextDouble(0.3));
        }
    }

}
