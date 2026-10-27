package ru.privatenull.pncases.cases.proximity;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.privatenull.pncases.cases.animation.effect.AnimationEffects;
import ru.privatenull.pncases.cases.model.ProximitySettings;
import ru.privatenull.pncases.support.compat.BukkitCompat.ParticleCompat;
import ru.privatenull.pncases.support.text.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Autumn scene: heaps of leaves, maples with red, orange and yellow crowns, glowing pumpkins, mushrooms,
 * hay bales, a string of lanterns, drifting leaves, a scarecrow that waves at the player, a harvest basket
 * instead of the case block, falling leaves and a slow minor-key tune.
 */
final class AutumnScene extends AmbientScene {

    private static final int RING_STEPS = 14;
    private static final Material[] CROWNS = {
            Material.ORANGE_CONCRETE, Material.RED_CONCRETE, Material.YELLOW_CONCRETE
    };
    private static final Material[] POWDER = {
            Material.ORANGE_CONCRETE_POWDER, Material.RED_CONCRETE_POWDER,
            Material.YELLOW_CONCRETE_POWDER, Material.BROWN_CONCRETE_POWDER
    };
    private static final Material[] LEAF_ITEMS = leafItems();

    AutumnScene(ProximitySettings settings, SceneMode mode) {
        super(settings, mode, TerrainPalette.AUTUMN);
    }

    private static Material[] leafItems() {
        List<Material> items = new ArrayList<>(List.of(Material.ORANGE_DYE, Material.RED_DYE, Material.YELLOW_DYE));
        // Leaf litter exists since 1.21.5 and looks like a real fallen leaf.
        Material litter = Material.matchMaterial("LEAF_LITTER");
        if (litter != null) items.add(litter);
        return items.toArray(new Material[0]);
    }

    private static Material pick(String name, Material fallback) {
        Material found = Material.matchMaterial(name);
        return found == null ? fallback : found;
    }

    // ---------------------------------------------------------------- hooks

    @Override
    void buildDecor() {
        ProximitySettings.Decor decor = settings.decor();
        if (settings.santa().enabled()) buildScarecrow();
        buildLeafPiles(decor.mounds());
        buildMaples(decor.trees());
        buildPumpkins(decor.snowmen());
        buildMushrooms(decor.crystals());
        buildHay(decor.gifts());
    }

    @Override
    void spawnEffects() {
        effect(Sound.BLOCK_AZALEA_LEAVES_PLACE, origin, 0.9f, 0.7f);
        effect(Sound.BLOCK_GRASS_BREAK, origin, 0.3f, 0.8f);
    }

    @Override
    void clearSound() {
        effect(Sound.BLOCK_AZALEA_LEAVES_BREAK, origin, 0.5f, 0.8f);
    }

    @Override
    String defaultMessage() {
        return "&6Осень на дворе";
    }

    @Override
    int tuneCount() {
        return AutumnMusic.tunes();
    }

    @Override
    WinterMusic.Tune tuneAt(int index) {
        return AutumnMusic.tune(index);
    }

    @Override
    void playNote(WinterMusic.Note note, float pitch, float volume, Location at) {
        tone(Sound.BLOCK_NOTE_BLOCK_GUITAR, at, 1.0f * volume, pitch);
        if (note.length() >= 16) {
            tone(Sound.BLOCK_NOTE_BLOCK_FLUTE, at, 0.5f * volume, pitch);
        }
    }

    @Override
    void playBass(int step, float volume) {
        tone(Sound.BLOCK_NOTE_BLOCK_BASS, origin, 0.7f * volume, WinterMusic.pitch(step % 2 == 0 ? 5 : 12));
    }

    // ---------------------------------------------------------------- decor

    private void buildLeafPiles(int count) {
        for (int i = 0; i < count; i++) {
            double angle = turn + Math.PI * 2.0 * i / count + layout.nextDouble(-0.18, 0.18);
            double distance = 1.55 + layout.nextDouble() * 0.85;
            double gx = Math.cos(angle) * distance;
            double gz = Math.sin(angle) * distance;
            double width = 0.75 + layout.nextDouble() * 0.55;
            double height = 0.16 + layout.nextDouble() * 0.2;
            double yaw = layout.nextDouble() * Math.PI;
            double delay = 0.02 + i * 0.025 / Math.max(1.0, count / 11.0);
            add(block(POWDER[i % POWDER.length], Kind.MOUND, Anim.STATIC, gx, gz, yaw, delay, 8, true,
                    0, 0, 0, width, height, width, null));
            add(block(POWDER[(i + 1) % POWDER.length], Kind.MOUND, Anim.STATIC, gx, gz, yaw + 0.6, delay + 0.03, 8, false,
                    0, height, 0, width * 0.55, height * 0.8, width * 0.55, null));
        }
    }

    /** A tall maple: a flared trunk with two branches, a crown of tilted blobs in three colours, hanging leaves. */
    private void buildMaples(int count) {
        for (int i = 0; i < count; i++) {
            double angle = turn + Math.PI / 4.0 + i * Math.PI * 2.0 / count + layout.nextDouble(-0.15, 0.15);
            double distance = 3.0 + layout.nextDouble() * 0.25;
            double gx = Math.cos(angle) * distance;
            double gz = Math.sin(angle) * distance;
            double s = 1.7 + layout.nextDouble() * 0.5;
            double yaw = layout.nextDouble() * Math.PI;
            double delay = 0.18 + i * 0.05;

            add(block(Material.DARK_OAK_LOG, Kind.TREE, Anim.STATIC, gx, gz, yaw, delay, 6, true,
                    0, 0, 0, 0.3 * s, 0.14 * s, 0.3 * s, null));
            add(block(Material.DARK_OAK_LOG, Kind.TREE, Anim.STATIC, gx, gz, yaw, delay + 0.01, 6, false,
                    0, 0.1 * s, 0, 0.2 * s, 0.75 * s, 0.2 * s, null));
            add(block(Material.DARK_OAK_PLANKS, Kind.TREE, Anim.STATIC, gx, gz, yaw, delay + 0.02, 6, false,
                    0.04 * s, 0.6 * s, 0, 0.5 * s, 0.07 * s, 0.07 * s, new Quaternionf().rotationZ(0.75f)));
            add(block(Material.DARK_OAK_PLANKS, Kind.TREE, Anim.STATIC, gx, gz, yaw, delay + 0.02, 6, false,
                    -0.04 * s, 0.7 * s, 0, 0.5 * s, 0.07 * s, 0.07 * s, new Quaternionf().rotationZ(-0.75f)));

            // The crown: a big middle, a ring of six blobs around it and three on top, colours mixed.
            add(block(CROWNS[i % 3], Kind.TREE, Anim.STATIC, gx, gz, yaw, delay + 0.05, 8, false,
                    0, 0.78 * s, 0, 0.72 * s, 0.55 * s, 0.72 * s, new Quaternionf().rotationY(0.4f)));
            for (int k = 0; k < 6; k++) {
                double a = k * Math.PI / 3.0 + 0.3;
                double r = 0.4 * s;
                double size = (0.42 + 0.1 * ((k + i) % 3)) * s;
                add(block(CROWNS[(i + k) % 3], Kind.TREE, Anim.STATIC, gx, gz, yaw, delay + 0.06 + k * 0.01, 8, false,
                        Math.cos(a) * r, (0.72 + 0.08 * (k % 2)) * s, Math.sin(a) * r, size, 0.4 * s, size,
                        new Quaternionf().rotationY((float) (a + 0.5)).rotateZ((float) (0.12 * (k % 2 == 0 ? 1 : -1)))));
            }
            for (int k = 0; k < 3; k++) {
                double a = k * Math.PI * 2.0 / 3.0 + 0.9;
                double size = (0.4 - 0.06 * k) * s;
                add(block(CROWNS[(i + k + 1) % 3], Kind.TREE, Anim.STATIC, gx, gz, yaw, delay + 0.12 + k * 0.01, 8, false,
                        Math.cos(a) * 0.17 * s, (1.08 + 0.1 * k) * s, Math.sin(a) * 0.17 * s, size, 0.3 * s, size,
                        new Quaternionf().rotationY((float) (a + 0.8))));
            }
            // Leaves hang under the crown edge and lie around the foot.
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 2.0 + 0.7;
                add(block(CROWNS[(i + k) % 3], Kind.TREE, Anim.STATIC, gx, gz, yaw, delay + 0.16, 8, false,
                        Math.cos(a) * 0.52 * s, 0.58 * s, Math.sin(a) * 0.52 * s, 0.12 * s, 0.2 * s, 0.12 * s, null));
                add(block(POWDER[(i + k) % POWDER.length], Kind.TREE, Anim.STATIC, gx, gz, yaw, delay + 0.18, 8, false,
                        Math.cos(a + 0.4) * 0.5 * s, 0, Math.sin(a + 0.4) * 0.5 * s, 0.26 * s, 0.02 * s, 0.26 * s,
                        new Quaternionf().rotationY((float) (a * 2.0))));
            }
        }
    }

    private void buildPumpkins(int count) {
        int placed = 0;
        for (int attempt = 0; attempt < count * 4 && placed < count; attempt++) {
            double angle = turn + -0.5 + attempt * (Math.PI * 2.0 / Math.max(1, count));
            if (settings.santa().enabled() && angularDistance(angle, santaAngle) < 0.8) continue;
            addPumpkins(angle, 0.3 + placed * 0.04);
            placed++;
        }
    }

    private void addPumpkins(double angle, double d) {
        double distance = 2.25;
        double gx = Math.cos(angle) * distance;
        double gz = Math.sin(angle) * distance;
        double yaw = Math.atan2(-gx, -gz);
        // A carved pumpkin faces north by default; turn its face towards the case.
        Quaternionf face = new Quaternionf().rotationY((float) Math.PI);
        add(block(Material.PUMPKIN, Kind.PUMPKIN, Anim.STATIC, gx, gz, yaw, d, 9, true,
                0, 0, 0, 0.5, 0.42, 0.5, null));
        Part lit = block(Material.CARVED_PUMPKIN, Kind.PUMPKIN, Anim.STATIC, gx, gz, yaw, d + 0.03, 15, false,
                0, 0.4, 0, 0.38, 0.34, 0.38, face);
        add(lit);
        crystals.add(lit);
        add(block(Material.PUMPKIN, Kind.PUMPKIN, Anim.STATIC, gx, gz, yaw + 1.0, d + 0.05, 9, false,
                0.34, 0, 0.12, 0.26, 0.22, 0.26, null));
        add(block(Material.STRIPPED_OAK_LOG, Kind.PUMPKIN, Anim.STATIC, gx, gz, yaw, d + 0.06, 6, false,
                0, 0.73, 0, 0.06, 0.1, 0.06, null));
    }

    private void buildMushrooms(int clusters) {
        for (int cluster = 0; cluster < clusters; cluster++) {
            double angle = turn + 0.2 + cluster * Math.PI * 2.0 / clusters + layout.nextDouble(-0.15, 0.15);
            double distance = 1.55 + layout.nextDouble() * 0.2;
            double gx = Math.cos(angle) * distance;
            double gz = Math.sin(angle) * distance;
            for (int i = 0; i < 3; i++) {
                double yaw = layout.nextDouble() * Math.PI * 2.0;
                double height = 0.2 + layout.nextDouble() * 0.25;
                double cap = 0.26 + layout.nextDouble() * 0.16;
                double x = (i - 1) * 0.2;
                double z = layout.nextDouble(-0.1, 0.1);
                double delay = 0.35 + cluster * 0.04 + i * 0.02;
                add(block(Material.MUSHROOM_STEM, Kind.MUSHROOM, Anim.STATIC, gx, gz, yaw, delay, 9, i == 0,
                        x, 0, z, 0.1, height, 0.1, null));
                Part top = block(i % 2 == 0 ? Material.RED_MUSHROOM_BLOCK : Material.BROWN_MUSHROOM_BLOCK,
                        Kind.MUSHROOM, Anim.STATIC, gx, gz, yaw, delay + 0.01, 11, false,
                        x, height - 0.02, z, cap, 0.12, cap, null);
                add(top);
                crystals.add(top);
                add(block(Material.SHROOMLIGHT, Kind.MUSHROOM, Anim.STATIC, gx, gz, yaw, delay + 0.02, 15, false,
                        x, height + 0.1, z, cap * 0.4, 0.04, cap * 0.4, null));
            }
        }
    }

    private void buildHay(int count) {
        for (int i = 0; i < count; i++) {
            double angle = santaAngle + 0.35 + i * 0.55 + layout.nextDouble(-0.1, 0.1);
            double distance = 1.85 + layout.nextDouble() * 0.35;
            double gx = Math.cos(angle) * distance;
            double gz = Math.sin(angle) * distance;
            double size = 0.4 + layout.nextDouble() * 0.1;
            double yaw = layout.nextDouble() * Math.PI;
            double delay = 0.4 + i * 0.03;
            add(block(Material.HAY_BLOCK, Kind.HAY, Anim.STATIC, gx, gz, yaw, delay, 9, true,
                    0, 0, 0, size, size * 0.9, size, null));
            if (i % 2 == 0) {
                add(block(Material.PUMPKIN, Kind.HAY, Anim.STATIC, gx, gz, yaw + 0.5, delay + 0.02, 9, false,
                        0, size * 0.9, 0, size * 0.6, size * 0.5, size * 0.6, null));
            } else {
                add(block(Material.HAY_BLOCK, Kind.HAY, Anim.STATIC, gx, gz, yaw + 0.4, delay + 0.02, 9, false,
                        0.1, size * 0.9, 0, size * 0.7, size * 0.6, size * 0.7, null));
            }
        }
    }


    /** The scarecrow: a hay head under a hat, a patched shirt, a crow on one sleeve and a waving arm. */
    private void buildScarecrow() {
        double distance = 2.2;
        double gx = Math.cos(santaAngle) * distance;
        double gz = Math.sin(santaAngle) * distance;
        double yaw = Math.atan2(-gx, -gz);
        Material shirt = settings.santa().coat();
        double d = 0.28;
        double k = 1.5;
        int firstPart = parts.size();

        add(santaBlock(Material.DARK_OAK_PLANKS, gx, gz, yaw, d, 5, true, 0, 0, 0, 0.08 * k, 1.15 * k, 0.08 * k, null));
        add(santaBlock(Material.DARK_OAK_PLANKS, gx, gz, yaw, d + 0.02, 5, false, 0, 0.82 * k, 0, 0.9 * k, 0.06 * k, 0.06 * k, null));
        add(santaBlock(shirt, gx, gz, yaw, d + 0.04, 7, false, 0, 0.42 * k, 0, 0.38 * k, 0.5 * k, 0.22 * k, null));
        add(santaBlock(Material.DARK_OAK_PLANKS, gx, gz, yaw, d + 0.05, 5, false, 0, 0.55 * k, 0, 0.4 * k, 0.05 * k, 0.24 * k, null));
        add(santaBlock(Material.HAY_BLOCK, gx, gz, yaw, d + 0.05, 9, false, 0, 0.34 * k, 0, 0.34 * k, 0.1 * k, 0.2 * k, null));
        add(santaBlock(Material.HAY_BLOCK, gx, gz, yaw, d + 0.06, 9, false, -0.13 * k, 0.26 * k, 0.02 * k, 0.07 * k, 0.12 * k, 0.07 * k,
                new Quaternionf().rotationZ(0.4f)));
        add(santaBlock(Material.HAY_BLOCK, gx, gz, yaw, d + 0.06, 9, false, 0.13 * k, 0.26 * k, -0.02 * k, 0.07 * k, 0.12 * k, 0.07 * k,
                new Quaternionf().rotationZ(-0.4f)));
        add(santaBlock(Material.RED_WOOL, gx, gz, yaw, d + 0.06, 8, false, 0.07 * k, 0.7 * k, 0.115 * k, 0.12 * k, 0.12 * k, 0.02 * k, null));
        add(santaBlock(Material.YELLOW_WOOL, gx, gz, yaw, d + 0.07, 8, false, -0.08 * k, 0.5 * k, 0.115 * k, 0.1 * k, 0.1 * k, 0.02 * k, null));

        // The still left sleeve and hand, with a crow sitting on it.
        add(santaBlock(shirt, gx, gz, yaw, d + 0.08, 7, false, -0.3 * k, 0.78 * k, 0, 0.3 * k, 0.12 * k, 0.12 * k, null));
        add(santaBlock(Material.HAY_BLOCK, gx, gz, yaw, d + 0.09, 9, false, -0.5 * k, 0.78 * k, 0, 0.1 * k, 0.1 * k, 0.1 * k, null));
        add(crowPart(santaBlock(Material.BLACK_CONCRETE, gx, gz, yaw, d + 0.2, 4, false, -0.3 * k, 0.9 * k, 0, 0.1 * k, 0.09 * k, 0.16 * k, null), 0.0));
        add(crowPart(santaBlock(Material.BLACK_CONCRETE, gx, gz, yaw, d + 0.21, 4, false, -0.3 * k, 0.97 * k, 0.07 * k, 0.065 * k, 0.065 * k, 0.065 * k, null), 0.9));
        add(crowPart(santaBlock(Material.ORANGE_CONCRETE, gx, gz, yaw, d + 0.22, 9, false, -0.3 * k, 0.985 * k, 0.12 * k, 0.03 * k, 0.02 * k, 0.05 * k, null), 0.9));

        // The right arm hangs from the pole and sways slowly in the wind, a scarecrow does not wave.
        Part arm = santaBlock(shirt, gx, gz, yaw, d + 0.12, 7, false, 0.15 * k, 0.78 * k, 0, 0.12 * k, 0.3 * k, 0.12 * k, null);
        arm.anim = Anim.ARM;
        arm.yaw = new Quaternionf().rotationY((float) yaw);
        arm.waveBase = -1.57;
        arm.waveAmp = 0.0;
        arm.waveSpeed = 0.0;
        add(arm);
        Part hand = santaBlock(Material.HAY_BLOCK, gx, gz, yaw, d + 0.13, 9, false, 0.15 * k, 0.78 * k, 0, 0.1 * k, 0.1 * k, 0.1 * k, null);
        hand.anim = Anim.HAND;
        hand.link = arm;
        add(hand);

        // Head, face and hat.
        add(santaBlock(Material.HAY_BLOCK, gx, gz, yaw, d + 0.1, 10, false, 0, 0.95 * k, 0, 0.3 * k, 0.3 * k, 0.3 * k, null));
        add(santaBlock(Material.BLACK_CONCRETE, gx, gz, yaw, d + 0.12, 4, false, -0.07 * k, 1.12 * k, 0.15 * k, 0.045 * k, 0.045 * k, 0.03 * k, null));
        add(santaBlock(Material.BLACK_CONCRETE, gx, gz, yaw, d + 0.12, 4, false, 0.07 * k, 1.12 * k, 0.15 * k, 0.045 * k, 0.045 * k, 0.03 * k, null));
        add(santaBlock(Material.ORANGE_CONCRETE, gx, gz, yaw, d + 0.12, 9, false, 0, 1.05 * k, 0.17 * k, 0.05 * k, 0.05 * k, 0.12 * k, null));
        add(santaBlock(Material.BLACK_CONCRETE, gx, gz, yaw, d + 0.12, 4, false, -0.07 * k, 0.99 * k, 0.15 * k, 0.04 * k, 0.025 * k, 0.02 * k, null));
        add(santaBlock(Material.BLACK_CONCRETE, gx, gz, yaw, d + 0.12, 4, false, 0, 0.975 * k, 0.15 * k, 0.04 * k, 0.025 * k, 0.02 * k, null));
        add(santaBlock(Material.BLACK_CONCRETE, gx, gz, yaw, d + 0.12, 4, false, 0.07 * k, 0.99 * k, 0.15 * k, 0.04 * k, 0.025 * k, 0.02 * k, null));
        add(santaBlock(Material.DARK_OAK_PLANKS, gx, gz, yaw, d + 0.13, 6, false, 0, 1.24 * k, 0, 0.5 * k, 0.03 * k, 0.5 * k, null));
        add(santaBlock(Material.DARK_OAK_PLANKS, gx, gz, yaw, d + 0.14, 6, false, 0, 1.27 * k, 0, 0.26 * k, 0.2 * k, 0.26 * k, null));
        add(santaBlock(Material.ORANGE_CONCRETE, gx, gz, yaw, d + 0.15, 9, false, 0, 1.28 * k, 0, 0.275 * k, 0.04 * k, 0.275 * k, null));
        add(santaBlock(Material.RED_CONCRETE, gx, gz, yaw, d + 0.16, 9, false, 0.12 * k, 1.3 * k, 0.1 * k, 0.06 * k, 0.1 * k, 0.03 * k,
                new Quaternionf().rotationZ(-0.5f)));

        // The speech bubble stands still above the scarecrow and faces the case.
        Quaternionf front = new Quaternionf().rotationY((float) yaw);
        String first = ColorUtil.colorize(nextSantaMessage());
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
        bubble.local = new Vector3f(0f, (float) (1.7 * k) + groundShift(bubble.gx, bubble.gz), 0f);
        bubble.size.set(0.7f, 0.7f, 0.7f);
        bubble.tilt = new Quaternionf(front);
        bubble.delay = d + 0.2;
        parts.add(bubble);
        bubbles.add(bubble);

        // Everything but the bubble leans in the wind together; the hat and the sleeves flutter a bit more.
        for (int i = firstPart; i < parts.size(); i++) {
            Part part = parts.get(i);
            if (part.anim == Anim.TEXT) continue;
            part.sway = true;
            part.axis.set((float) Math.sin(yaw), 0f, (float) Math.cos(yaw));
            part.phase += i * 0.3;
            if (part.local.y > 1.2 * k) part.flutter = 0.01;
        }
    }

    /** The crow on the sleeve: its head dips now and then while the body stays put. */
    private Part crowPart(Part part, double phase) {
        if (phase > 0.0) {
            part.anim = Anim.BOB;
            part.bobAmp = 0.025;
            part.phase = phase;
        }
        return part;
    }

    // ----------------------------------------------------------- case model

    @Override
    void buildCaseModel(ProximitySettings.CaseStyle style) {
        // A winter model style left over in the file is drawn as the basket, the only model of this scene.
        if (style == ProximitySettings.CaseStyle.NONE || style == ProximitySettings.CaseStyle.BLOCK) return;
        Material wicker = pick("BAMBOO_MOSAIC", Material.OAK_PLANKS);
        Material rim = Material.SPRUCE_PLANKS;

        caseBlock(wicker, 0, 0, 0.9, 0.46, 0.9, 0.03, 9, true, Anim.STATIC, 0);
        caseBlock(rim, 0, 0.14, 0.94, 0.05, 0.94, 0.04, 8, false, Anim.STATIC, 0);
        offsetZ(caseBlock(rim, 0, 0.43, 0.98, 0.06, 0.08, 0.05, 9, false, Anim.STATIC, 0), 0.45);
        offsetZ(caseBlock(rim, 0, 0.43, 0.98, 0.06, 0.08, 0.05, 9, false, Anim.STATIC, 0), -0.45);
        caseBlock(rim, 0.45, 0.43, 0.08, 0.06, 0.82, 0.05, 9, false, Anim.STATIC, 0);
        caseBlock(rim, -0.45, 0.43, 0.08, 0.06, 0.82, 0.05, 9, false, Anim.STATIC, 0);

        offsetZ(caseBlock(Material.PUMPKIN, -0.17, 0.43, 0.36, 0.3, 0.36, 0.08, 11, false, Anim.STATIC, 0), 0.06);
        offsetZ(caseBlock(Material.RED_CONCRETE, 0.22, 0.43, 0.16, 0.16, 0.16, 0.1, 11, false, Anim.STATIC, 0), 0.14);
        offsetZ(caseBlock(Material.RED_CONCRETE, 0.06, 0.43, 0.15, 0.15, 0.15, 0.1, 11, false, Anim.STATIC, 0), -0.22);
        offsetZ(caseBlock(Material.GREEN_CONCRETE, 0.27, 0.43, 0.15, 0.15, 0.15, 0.1, 11, false, Anim.STATIC, 0), -0.14);

        double[] lean = {0.3, 0.1, -0.2};
        double[] baseX = {-0.12, 0.0, 0.1};
        double length = 0.52;
        for (int i = 0; i < lean.length; i++) {
            Part stalk = caseBlock(Material.HAY_BLOCK, baseX[i], 0.43, 0.05, length, 0.05, 0.12, 11, false, Anim.STATIC, 0);
            stalk.tilt = new Quaternionf().rotationZ((float) lean[i]);
            offsetZ(stalk, -0.12);
            double topX = baseX[i] - Math.sin(lean[i]) * length;
            double topY = 0.43 + Math.cos(lean[i]) * length;
            Part head = caseBlock(Material.YELLOW_CONCRETE, topX, topY - 0.02, 0.08, 0.14, 0.08, 0.14, 13, false, Anim.STATIC, 0);
            head.tilt = new Quaternionf().rotationZ((float) lean[i]);
            offsetZ(head, -0.12);
        }

        caseBlock(rim, 0.43, 0.46, 0.05, 0.6, 0.05, 0.06, 8, false, Anim.BOB, 0.03);
        caseBlock(rim, -0.43, 0.46, 0.05, 0.6, 0.05, 0.06, 8, false, Anim.BOB, 0.03);
        caseBlock(rim, 0, 1.06, 0.92, 0.05, 0.05, 0.07, 8, false, Anim.BOB, 0.03);

        caseItem(Material.APPLE, 0.3, 1.0, 0.03, 0.16, 0.16);
        caseItem(Material.WHEAT, 0.34, 1.15, -0.035, 0.16, 0.2);
        caseItem(pick("SWEET_BERRIES", Material.APPLE), 0.28, 0.85, 0.04, 0.14, 0.24);
    }

    private void offsetZ(Part part, double z) {
        part.local.z = (float) (z * caseScale);
    }

    // ------------------------------------------------------------ particles

    @Override
    void drawParticles(double eased) {
        ProximitySettings.Particles cfg = settings.particles();
        if (eased <= 0.02 || cfg.density() <= 0.0) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double density = cfg.density();

        if (cfg.snowfall()) {
            int fall = (int) Math.ceil(7 * eased * density);
            for (int i = 0; i < fall; i++) {
                double angle = random.nextDouble(Math.PI * 2.0);
                double distance = Math.sqrt(random.nextDouble()) * 3.4 * spread;
                Location at = origin.clone().add(Math.cos(angle) * distance, 0.8 + random.nextDouble() * 2.8,
                        Math.sin(angle) * distance);
                leaf(at, 1, 0.05);
            }
        }

        if (cfg.swirl()) {
            for (int arm = 0; arm < 3; arm++) {
                double phase = age * 0.1 + arm * Math.PI * 2.0 / 3.0;
                double rise = ((age * 0.018) + arm / 3.0) % 1.0;
                double radius = (1.0 + 1.6 * rise) * eased * spread;
                Location at = origin.clone().add(Math.cos(phase) * radius, 0.15 + rise * 2.4, Math.sin(phase) * radius);
                warm(at, Math.max(1, (int) Math.round(2 * density)), 0.05);
            }
        }

        if (cfg.glints()) {
            if (age % 4 == 0 && !flakes.isEmpty()) {
                Particle glint = ParticleCompat.first("WAX_ON", "END_ROD");
                for (int i = 0; i < Math.max(1, Math.round(2 * density)); i++) {
                    Part flake = flakes.get(random.nextInt(flakes.size()));
                    if (!flake.appeared) continue;
                    Location at = new Location(world, flake.worldX, flake.worldY, flake.worldZ);
                    AnimationEffects.plain(world, glint, at, 1, 0.03, 0.03, 0.03, 0.0);
                }
            }
            if (age % 8 == 0 && !crystals.isEmpty()) {
                Part lit = crystals.get(random.nextInt(crystals.size()));
                if (lit.appeared) {
                    Location at = origin.clone().add(lit.gx + lit.local.x, lit.local.y + lit.size.y + 0.05,
                            lit.gz + lit.local.z);
                    AnimationEffects.plain(world, Particle.FLAME, at, 1, 0.05, 0.02, 0.05, 0.002);
                }
            }
        }

        if (cfg.glints() && age % 6 == 0 && mode == SceneMode.ALL) drawCaseSparkles(eased, density);

        if (ringStep >= 0) {
            drawRing(ringStep, density);
            ringStep = ringStep + 1 >= RING_STEPS ? -1 : ringStep + 1;
        }
    }

    private void leaf(Location at, int count, double spreadAround) {
        BlockData data = POWDER[ThreadLocalRandom.current().nextInt(POWDER.length)].createBlockData();
        AnimationEffects.block(world, Particle.FALLING_DUST, at, count, spreadAround, spreadAround, spreadAround, 0.0, data);
    }

    /** Small warm specks in autumn colours; plain dust shows the same on every server version. */
    private void warm(Location at, int count, double around) {
        Color[] colors = {Color.fromRGB(235, 120, 30), Color.fromRGB(200, 55, 40), Color.fromRGB(240, 190, 50)};
        Color color = colors[ThreadLocalRandom.current().nextInt(colors.length)];
        AnimationEffects.dust(world, AnimationEffects.particle(AnimationEffects.Kind.DUST), at, count,
                around, around, around, 0.0, new Particle.DustOptions(color, 1.2f));
    }

    @Override
    void drawCaseSparkles(double eased, double density) {
        ProximitySettings.CaseStyle style = settings.terrain().caseStyle();
        if (style == ProximitySettings.CaseStyle.NONE || style == ProximitySettings.CaseStyle.BLOCK) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Particle glint = ParticleCompat.first("WAX_ON", "END_ROD");
        int count = Math.max(1, (int) Math.round(2 * density * eased));
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble(Math.PI * 2.0);
            double reach = 0.4 * random.nextDouble();
            Location at = origin.clone().add(Math.cos(angle) * reach, 0.8 + random.nextDouble() * 0.5,
                    Math.sin(angle) * reach);
            AnimationEffects.plain(world, glint, at, 1, 0.05, 0.1, 0.05, 0.01);
        }
    }

    private void drawRing(int step, double density) {
        double radius = (0.5 + step * 0.22) * spread;
        Particle dust = AnimationEffects.particle(AnimationEffects.Kind.DUST);
        Particle.DustOptions warm = new Particle.DustOptions(Color.fromRGB(255, 170, 70), 1.0f);
        int points = (int) Math.max(6, (18 + step * 3) * density);
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points;
            Location at = origin.clone().add(Math.cos(angle) * radius, 0.08, Math.sin(angle) * radius);
            AnimationEffects.dust(world, dust, at, 1, 0, 0, 0, 0, warm);
            if (i % 3 == 0) warm(at.clone().add(0, 0.2, 0), 1, 0.05);
        }
    }

    // --------------------------------------------------------------- sounds

    @Override
    void playAmbient(double progress, boolean rising) {
        if (!rising || progress < 1.0) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (age % 20 == 0 && random.nextInt(5) == 0) {
            Location at = origin.clone().add(random.nextDouble(-2.0, 2.0), 1.0, random.nextDouble(-2.0, 2.0));
            effect(Sound.BLOCK_AZALEA_LEAVES_STEP, at, 0.5f, 0.8f + (float) random.nextDouble(0.4));
        }
        if (age % 160 == 0 && random.nextInt(3) == 0) {
            effect(Sound.ENTITY_FOX_AMBIENT, origin.clone().add(2.5, 0.5, -2.0), 0.25f, 1.1f);
        }
        if (age % 80 == 0) {
            effect(Sound.BLOCK_GRASS_STEP, origin.clone().add(0, 0.5, 0), 0.2f, 0.6f);
        }
    }

    @Override
    void greet() {
        if (!settings.sounds().enabled()) return;
        double[] at = radial(Math.cos(santaAngle) * 2.2, Math.sin(santaAngle) * 2.2);
        Location where = origin.clone().add(at[0], 1.5, at[1]);
        effect(Sound.ENTITY_VILLAGER_AMBIENT, where, 0.8f, 0.6f);
        effect(Sound.BLOCK_NOTE_BLOCK_FLUTE, where, 0.5f, 1.3f);
        effect(Sound.BLOCK_AZALEA_LEAVES_PLACE, where, 0.6f, 0.9f);
    }

    @Override
    void playAppear(Part part) {
        if (!part.leader || soundBudget <= 0) return;
        soundBudget--;
        Location at = origin.clone().add(part.gx, 0.3, part.gz);
        float pitch = 0.75f + (float) ThreadLocalRandom.current().nextDouble(0.4);
        switch (part.kind) {
            case MOUND -> effect(Sound.BLOCK_AZALEA_LEAVES_PLACE, at, 0.6f, pitch);
            case TREE -> {
                effect(Sound.BLOCK_AZALEA_LEAVES_PLACE, at, 0.5f, 0.7f);
                effect(Sound.BLOCK_WOOD_PLACE, at, 0.4f, pitch);
            }
            case PUMPKIN -> {
                effect(Sound.BLOCK_PUMPKIN_CARVE, at, 0.4f, 0.9f);
                effect(Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, at, 0.2f, 1.2f);
            }
            case HAY -> effect(Sound.BLOCK_GRASS_PLACE, at, 0.6f, pitch);
            case MUSHROOM -> effect(Sound.BLOCK_MOSS_PLACE, at, 0.5f, pitch);
            case SANTA -> effect(Sound.ENTITY_PLAYER_LEVELUP, at, 0.3f, 1.3f);
            case CASE -> {
                effect(Sound.BLOCK_WOOD_PLACE, origin.clone().add(0, 0.6, 0), 0.7f, 0.9f);
                effect(Sound.BLOCK_NOTE_BLOCK_CHIME, origin.clone().add(0, 0.6, 0), 0.35f, 1.5f);
            }
            default -> {
            }
        }
    }

    @Override
    void playVanish(Part part) {
        if (!part.leader || soundBudget <= 0) return;
        soundBudget--;
        Location at = origin.clone().add(part.gx, 0.3, part.gz);
        effect(Sound.BLOCK_AZALEA_LEAVES_BREAK, at, 0.4f, 0.9f + (float) ThreadLocalRandom.current().nextDouble(0.3));
    }
}
