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
 * Shared engine of the ambient scenes: parts built from display entities that grow in with the
 * approach of a player, the changed terrain, a looping melody and the sound helpers. A concrete scene
 * only says what to build, which particles to draw and what it sounds like.
 */
abstract class AmbientScene implements ProximityScene {

    static final int INTERPOLATION_TICKS = 3;
    static final int SANTA_MESSAGE_TICKS = 200;

    enum Anim { STATIC, BOB, SPIN, TWINKLE, ORBIT_ITEM, ARM, HAND, TEXT }

    enum Phase { HIDDEN, GROWING, SHOWN, SHRINKING }

    enum Kind { MOUND, TREE, SNOWMAN, CRYSTAL, GARLAND, SNOWFLAKE, SANTA, GIFT, CASE, PUMPKIN, MUSHROOM, HAY }

    final ProximitySettings settings;
    final TerrainOverlay terrain;
    final List<Part> parts = new ArrayList<>();
    final List<Part> crystals = new ArrayList<>();
    final List<Part> flakes = new ArrayList<>();
    List<Player> viewers = List.of();
    SplittableRandom layout;
    World world;
    Location origin;
    Location caseBlock;
    long age;
    double lastProgress = -1.0;
    private double prevProgress;
    int ringStep = -1;
    int soundBudget;
    int tuneIndex;
    int noteCursor;
    long tuneStart = -1L;
    long musicResume;
    int santaMessage;
    final List<Part> bubbles = new ArrayList<>();
    double santaAngle = 2.4;
    /** View angle in radians: the whole scene is turned around the case by this much. */
    double turn;
    double spread = 1.0;

    final SceneMode mode;
    final double caseScale;
    boolean silent;
    boolean instant;


    AmbientScene(ProximitySettings settings, SceneMode mode, TerrainPalette palette) {
        this.settings = settings;
        this.mode = mode;
        this.caseScale = settings.terrain().caseScale();
        this.terrain = new TerrainOverlay(palette);
    }

    // ---------------------------------------------------------------- hooks

    /** Builds everything around the case except the case model: the decor and the character. */
    abstract void buildDecor();

    /** Replaces the plain case block by a model of the given style. */
    abstract void buildCaseModel(ProximitySettings.CaseStyle style);

    /** Sounds and effects played when the scene is built. */
    abstract void spawnEffects();

    abstract void drawParticles(double eased);

    abstract void drawCaseSparkles(double eased, double density);

    abstract void playAmbient(double progress, boolean rising);

    /** The character says its next line. */
    abstract void greet();

    abstract void playAppear(Part part);

    abstract void playVanish(Part part);

    abstract void clearSound();

    abstract String defaultMessage();

    abstract int tuneCount();

    abstract WinterMusic.Tune tuneAt(int index);

    abstract void playNote(WinterMusic.Note note, float pitch, float volume, Location at);

    abstract void playBass(int step, float volume);

    // ---------------------------------------------------------------- build

    @Override
    public void spawn(World world, Location block) {
        this.world = world;
        this.caseBlock = block.clone();
        this.origin = block.clone().add(0.5, 0.0, 0.5);
        this.spread = settings.spread();
        this.turn = Math.toRadians(settings.rotation());
        this.santaAngle = 2.4 + turn;
        this.layout = new SplittableRandom(block.getBlockX() * 73856093L ^ block.getBlockZ() * 19349663L);

        terrain.build(world, block, settings.terrain(), layout, mode != SceneMode.MODEL, mode != SceneMode.DECOR);
        if (mode != SceneMode.DECOR) buildCaseModel(settings.terrain().caseStyle());
        if (mode == SceneMode.MODEL) return;

        buildDecor();
        ringStep = settings.particles().frostRing() ? 0 : -1;
        spawnEffects();
    }

    /**
     * Pushes a decoration away from the case when the case model is larger than a block (and pulls it in
     * when smaller), so everything stays attached to the main object whatever its size.
     */
    double[] radial(double gx, double gz) {
        double r = Math.sqrt(gx * gx + gz * gz);
        if (r < 1.0e-6) return new double[]{0.0, 0.0};
        double scaled = Math.max(0.9, r * spread + (caseScale - 1.0) * 0.5);
        return new double[]{gx / r * scaled, gz / r * scaled};
    }

    double radialRadius(double r) {
        return Math.max(0.9, r * spread + (caseScale - 1.0) * 0.5);
    }

    /** One part of the case model. For STATIC and BOB y is the bottom, for SPIN it is the centre. */
    Part caseBlock(Material material, double x, double y, double sx, double sy, double sz,
                           double delay, int light, boolean leader, Anim anim, double bob) {
        double s = caseScale;
        Part part = block(material, Kind.CASE, anim, 0, 0, 0, delay * 0.5, light, leader,
                x * s, y * s, 0, sx * s, sy * s, sz * s, null);
        part.bobAmp = bob * s;
        part.span = 0.15;
        parts.add(part);
        return part;
    }

    void caseItem(Material material, double radius, double height, double speed, double size, double delay) {
        ItemDisplay display = world.spawn(origin, ItemDisplay.class, spawned -> {
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setBrightness(new Display.Brightness(14, 15));
            prepare(spawned);
        });
        display.setItemStack(new ItemStack(material));
        Part part = new Part(display, Kind.CASE, Anim.ORBIT_ITEM);
        part.size.set((float) (size * caseScale), (float) (size * caseScale), (float) (size * caseScale));
        part.radius = radius * caseScale;
        part.height = height * caseScale;
        part.speed = speed;
        part.phase = layout.nextDouble() * Math.PI * 2.0;
        part.delay = delay * 0.5;
        part.span = 0.15;
        parts.add(part);
    }

    String nextSantaMessage() {
        List<String> messages = settings.santa().messages();
        if (messages.isEmpty()) return defaultMessage();
        return messages.get(Math.floorMod(santaMessage++, messages.size()));
    }

    Part santaBlock(Material material, double gx, double gz, double yaw, double delay, int light,
                            boolean leader, double lx, double ly, double lz, double sx, double sy, double sz,
                            Quaternionf extra) {
        Part part = block(material, Kind.SANTA, Anim.STATIC, gx, gz, yaw, delay, light, leader,
                lx, ly, lz, sx, sy, sz, extra);
        part.yaw = new Quaternionf().rotationY((float) yaw);
        return part;
    }

    Part block(Material material, Kind kind, Anim anim, double gx, double gz, double yaw,
                       double delay, int light, boolean leader,
                       double lx, double ly, double lz, double sx, double sy, double sz, Quaternionf extra) {
        BlockDisplay display = world.spawn(origin, BlockDisplay.class, spawned -> {
            spawned.setBlock(material.createBlockData());
            spawned.setBrightness(new Display.Brightness(light, 15));
            prepare(spawned);
        });
        Part part = new Part(display, kind, anim);
        Quaternionf rotation = new Quaternionf().rotationY((float) yaw);
        double[] placed = radial(gx, gz);
        part.gx = placed[0];
        part.gz = placed[1];
        part.local = rotation.transform(new Vector3f((float) lx, (float) ly, (float) lz));
        if (kind != Kind.CASE && kind != Kind.GARLAND) {
            double shift = groundDelta(part.gx, part.gz);
            if (Double.isNaN(shift)) part.noGround = true;
            else part.local.y += (float) shift;
        }
        part.size.set((float) sx, (float) sy, (float) sz);
        part.tilt = extra == null ? rotation : new Quaternionf(rotation).mul(extra);
        part.delay = delay;
        part.leader = leader;
        return part;
    }

    /**
     * How much higher (or lower) the real ground under a decoration is than the ground at the case. A
     * decoration over a hole has no ground at all and is not built, so nothing hangs in the air.
     *
     * @return the shift in blocks, 0 on level ground, or NaN when no ground lies within four blocks below
     */
    double groundDelta(double gx, double gz) {
        int x = (int) Math.floor(origin.getX() + gx);
        int z = (int) Math.floor(origin.getZ() + gz);
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return 0.0;
        int baseY = caseBlock.getBlockY();
        boolean caseColumn = x == caseBlock.getBlockX() && z == caseBlock.getBlockZ();
        // One block above the case is checked too: ground that is a step higher must carry the decoration.
        for (int y = baseY + 1; y >= baseY - 4; y--) {
            if (caseColumn && y == baseY) return 0.0;
            org.bukkit.block.Block at = world.getBlockAt(x, y, z);
            Material type = at.getType();
            if (!type.isSolid() || type.name().endsWith("_LEAVES")) continue;
            if (y > baseY && world.getBlockAt(x, y + 1, z).getType().isSolid()) continue;
            return (y + 1) - baseY + terrain.coverHeight();
        }
        return Double.NaN;
    }

    /** Lift of a part that stands at the given spot, for objects that are built by hand rather than with {@link #block}. */
    float groundShift(double gx, double gz) {
        double shift = groundDelta(gx, gz);
        return Double.isNaN(shift) ? 0f : (float) shift;
    }

    void add(Part part) {
        if (part.noGround) {
            part.display.remove();
            return;
        }
        parts.add(part);
    }

    static void prepare(Display display) {
        display.addScoreboardTag(ProximityAnimationService.DISPLAY_TAG);
        display.setPersistent(false);
        display.setViewRange(0.5f);
        display.setDisplayWidth(0f);
        display.setDisplayHeight(0f);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(INTERPOLATION_TICKS);
        display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                new Vector3f(0.001f, 0.001f, 0.001f), new Quaternionf()));
    }

    static double angularDistance(double a, double b) {
        double diff = Math.abs(a - b) % (Math.PI * 2.0);
        return diff > Math.PI ? Math.PI * 2.0 - diff : diff;
    }

    // --------------------------------------------------------------- update

    @Override
    public void update(double progress, boolean rising, List<Player> viewers) {
        this.viewers = viewers;
        age += 2;
        soundBudget = 3;
        // Parts do not follow the progress frame by frame: each one gets a single target per phase and the
        // client interpolates it, so nothing is retargeted while it moves and nothing shakes.
        double rate = Math.max(1.0e-4, Math.abs(progress - prevProgress) / 2.0);
        prevProgress = progress;
        lastProgress = progress;

        terrain.update(progress, viewers);
        if (!bubbles.isEmpty() && progress >= 1.0 && age % SANTA_MESSAGE_TICKS == 100) {
            String message = ColorUtil.colorize(nextSantaMessage());
            for (Part bubble : bubbles) {
                if (bubble.display.isValid()) ((TextDisplay) bubble.display).setText(message);
            }
            greet();
        }
        for (Part part : parts) {
            step(part, progress, rising, rate);
        }

        double eased = progress * progress * (3.0 - 2.0 * progress);
        if (mode == SceneMode.MODEL) {
            if (settings.particles().glints() && settings.particles().density() > 0.0 && age % 6 == 0 && eased > 0.02) {
                drawCaseSparkles(eased, settings.particles().density());
            }
            return;
        }
        drawParticles(eased);
        playMusic(progress, rising);
        playAmbient(progress, rising);
    }

    @Override
    public void snap(double progress, boolean rising, List<Player> viewers) {
        silent = true;
        instant = true;
        update(progress, rising, viewers);
        silent = false;
        instant = false;
        ringStep = -1;
    }

    /** Moves a part through its phases; a phase starts once, with the time the client needs to play it. */
    private void step(Part part, double progress, boolean rising, double rate) {
        if (!part.display.isValid()) return;
        int ticks = (int) Math.round(Math.min(120.0, Math.max(2.0, part.span / rate)));

        if (instant) {
            boolean on = progress >= part.delay;
            part.state = on ? Phase.SHOWN : Phase.HIDDEN;
            part.appeared = on;
            show(part, on ? 1.0 : 0.0, 0);
            return;
        }

        if (rising) {
            if ((part.state == Phase.HIDDEN || part.state == Phase.SHRINKING) && progress >= part.delay) {
                part.state = Phase.GROWING;
                part.stateEnd = age + ticks;
                if (!part.appeared) {
                    part.appeared = true;
                    playAppear(part);
                }
                show(part, 1.0, ticks);
            }
        } else if ((part.state == Phase.GROWING || part.state == Phase.SHOWN) && progress <= part.delay + part.span) {
            part.state = Phase.SHRINKING;
            part.stateEnd = age + ticks;
            if (part.appeared) {
                part.appeared = false;
                playVanish(part);
            }
            show(part, 0.0, ticks);
        }

        if (part.state == Phase.GROWING && age >= part.stateEnd) part.state = Phase.SHOWN;
        if (part.state == Phase.SHRINKING && age >= part.stateEnd) part.state = Phase.HIDDEN;
        if (part.state == Phase.SHOWN && moves(part) && age % 4 == 0) show(part, 1.0, 4);
    }

    /** Parts that keep moving while shown: hovering, spinning, twinkling, the arm and the sway in the wind. */
    private static boolean moves(Part part) {
        return part.sway || (part.anim != Anim.STATIC && part.anim != Anim.TEXT);
    }

    private void show(Part part, double e, int ticks) {
        part.display.setInterpolationDelay(0);
        part.display.setInterpolationDuration(ticks);
        part.display.setTransformation(transformFor(part, e));
    }

    /** The transformation of a part at size {@code e}: 0 is hidden, 1 is fully grown. */
    private Transformation transformFor(Part part, double e) {
        float ef = (float) Math.max(e, 0.0001);
        Vector3f scale = new Vector3f(part.size).mul(ef);
        Vector3f translation;
        Quaternionf rotation = part.tilt;

        switch (part.anim) {
            case STATIC -> {
                Vector3f anchor = new Vector3f(
                        (float) part.gx + part.local.x * ef,
                        part.local.y * ef,
                        (float) part.gz + part.local.z * ef);
                translation = anchor.sub(rotation.transform(new Vector3f(scale.x / 2f, 0f, scale.z / 2f)));
            }
            case BOB -> {
                double lift = Math.sin(age * 0.12 + part.phase) * part.bobAmp * ef;
                Vector3f anchor = new Vector3f(
                        (float) part.gx + part.local.x * ef,
                        part.local.y * ef + (float) lift,
                        (float) part.gz + part.local.z * ef);
                translation = anchor.sub(rotation.transform(new Vector3f(scale.x / 2f, 0f, scale.z / 2f)));
            }
            case SPIN -> {
                double lift = Math.sin(age * 0.07 + part.phase) * part.bobAmp;
                Vector3f anchor = new Vector3f(
                        (float) part.gx + part.local.x * ef,
                        (part.local.y + (float) lift) * ef,
                        (float) part.gz + part.local.z * ef);
                rotation = new Quaternionf().rotationY((float) (age * part.speed)).mul(part.tilt);
                translation = anchor.sub(rotation.transform(new Vector3f(scale).mul(0.5f)));
            }
            case TWINKLE -> {
                double pulse = 0.78 + 0.22 * Math.sin(age * 0.22 + part.phase);
                scale.mul((float) pulse);
                double bob = Math.sin(age * 0.06 + part.phase) * 0.05;
                Vector3f anchor = new Vector3f(
                        (float) part.gx + part.local.x * ef,
                        (part.local.y + (float) bob) * ef,
                        (float) part.gz + part.local.z * ef);
                translation = part.kind == Kind.GARLAND
                        ? anchor.sub(scale.x / 2f, scale.y / 2f, scale.z / 2f)
                        : anchor.sub(rotation.transform(new Vector3f(scale.x / 2f, 0f, scale.z / 2f)));
            }
            case ARM -> {
                rotation = new Quaternionf(part.yaw).rotateZ((float) part.angle(age));
                Vector3f anchor = new Vector3f(
                        (float) part.gx + part.local.x * ef,
                        part.local.y * ef,
                        (float) part.gz + part.local.z * ef);
                translation = anchor.sub(rotation.transform(new Vector3f(scale.x / 2f, 0f, scale.z / 2f)));
            }
            case HAND -> {
                Part arm = part.link;
                rotation = new Quaternionf(arm.yaw).rotateZ((float) arm.angle(age));
                Vector3f anchor = new Vector3f(
                        (float) part.gx + arm.local.x * ef,
                        arm.local.y * ef,
                        (float) part.gz + arm.local.z * ef);
                anchor.add(rotation.transform(new Vector3f(0f, arm.size.y * ef, 0f)));
                translation = anchor.sub(rotation.transform(new Vector3f(scale.x / 2f, 0f, scale.z / 2f)));
            }
            case TEXT -> {
                translation = new Vector3f(
                        (float) part.gx + part.local.x * ef,
                        part.local.y * ef,
                        (float) part.gz + part.local.z * ef);
                rotation = part.tilt;
            }
            case ORBIT_ITEM -> {
                double angle = part.phase + age * part.speed;
                double y = part.height + Math.sin(age * 0.07 + part.phase) * 0.12;
                double grow = Math.min(e, 1.0);
                translation = new Vector3f(
                        (float) (Math.cos(angle) * part.radius * grow),
                        (float) (y * grow),
                        (float) (Math.sin(angle) * part.radius * grow));
                rotation = new Quaternionf();
                part.worldX = origin.getX() + translation.x;
                part.worldY = origin.getY() + translation.y;
                part.worldZ = origin.getZ() + translation.z;
            }
            default -> translation = new Vector3f();
        }

        if (part.sway) {
            // The whole figure leans in the wind around the foot of its pole; light parts flutter on top.
            double gust = Math.sin(age * 0.07) * 0.032 + Math.sin(age * 0.17 + 1.3) * 0.008
                    + Math.sin(age * 0.23 + part.phase) * part.flutter;
            Quaternionf lean = new Quaternionf().rotateAxis((float) (gust * ef), part.axis.x, 0f, part.axis.z);
            Vector3f half = new Vector3f(scale.x / 2f, 0f, scale.z / 2f);
            Vector3f anchor = rotation.transform(new Vector3f(half)).add(translation);
            Vector3f pivot = new Vector3f((float) part.gx, 0f, (float) part.gz);
            anchor = lean.transform(anchor.sub(pivot)).add(pivot);
            rotation = new Quaternionf(lean).mul(rotation);
            translation = anchor.sub(rotation.transform(new Vector3f(half)));
        }

        return new Transformation(translation, rotation, scale, new Quaternionf());
    }

    /** Plays the carols in a loop, with a pause between them, while someone stands near the case. */
    void playMusic(double progress, boolean rising) {
        ProximitySettings.Music music = settings.music();
        if (!music.enabled() || music.volume() <= 0.0) return;
        if (!rising || progress < 0.35) {
            tuneStart = -1L;
            return;
        }

        if (tuneStart < 0L) {
            if (age < musicResume) return;
            tuneStart = age;
            noteCursor = 0;
        }

        WinterMusic.Tune tune = tuneAt(tuneIndex);
        long elapsed = age - tuneStart;
        float volume = (float) (music.volume() * Math.min(1.0, (progress - 0.35) / 0.4));
        List<WinterMusic.Note> notes = tune.notes();
        while (noteCursor < notes.size() && notes.get(noteCursor).tick() <= elapsed) {
            WinterMusic.Note note = notes.get(noteCursor++);
            float pitch = WinterMusic.pitch(note.semitone());
            playNote(note, pitch, volume, origin.clone().add(0, 1.4, 0));
        }
        if (elapsed % 32 < 2 && elapsed < tune.length()) {
            int step = (int) (elapsed / 32);
            playBass(step, volume);
        }
        if (elapsed >= tune.length() + 8) {
            tuneStart = -1L;
            tuneIndex = (tuneIndex + 1) % tuneCount();
            musicResume = age + (long) (music.pauseSeconds() * 20.0);
        }
    }

    /** One-shot effect sound, heard by nearby players when the effect sounds are enabled. */
    void effect(Sound sound, Location at, float volume, float pitch) {
        if (silent || !settings.sounds().enabled()) return;
        broadcast(sound, SoundCategory.AMBIENT, at, volume * (float) settings.sounds().volume(), pitch);
    }

    /** Music note, in the jukebox/note block volume category so players can turn it down themselves. */
    void tone(Sound sound, Location at, float volume, float pitch) {
        broadcast(sound, SoundCategory.RECORDS, at, volume, pitch);
    }

    void broadcast(Sound sound, SoundCategory category, Location at, float volume, float pitch) {
        if (silent || volume <= 0.001f) return;
        float clamped = Math.max(0.5f, Math.min(2.0f, pitch));
        for (Player viewer : viewers) {
            try {
                viewer.playSound(at, sound, category, volume, clamped);
            } catch (Throwable ignored) {
                // A sound missing on this server version only mutes that one effect.
            }
        }
    }

    // ----------------------------------------------------------------- misc

    @Override
    public void refresh(Player player, Location clicked) {
        terrain.resendNear(player, clicked);
    }

    @Override
    public boolean valid() {
        return parts.isEmpty() || parts.get(0).display.isValid();
    }

    @Override
    public void clear(boolean quiet) {
        if (!quiet && world != null && origin != null && lastProgress > 0.1 && mode != SceneMode.MODEL) {
            clearSound();
        }
        terrain.clear();
        for (Part part : parts) {
            part.display.remove();
        }
        parts.clear();
        crystals.clear();
        flakes.clear();
        bubbles.clear();
    }

    static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    static final class Part {
        final Display display;
        final Kind kind;
        Anim anim;
        final Vector3f size = new Vector3f(1f, 1f, 1f);
        Vector3f local = new Vector3f();
        Quaternionf tilt = new Quaternionf();
        Quaternionf yaw = new Quaternionf();
        Part link;
        double gx;
        double gz;
        double delay;
        double phase;
        double radius;
        double height;
        double speed;
        double waveBase;
        double waveAmp;
        double waveSpeed;
        double bobAmp;
        double span = 0.4;
        double worldX;
        double worldY;
        double worldZ;
        boolean leader;
        boolean appeared;
        Phase state = Phase.HIDDEN;
        long stateEnd;
        boolean noGround;
        boolean sway;
        double flutter;
        final Vector3f axis = new Vector3f(0f, 0f, 1f);

        Part(Display display, Kind kind, Anim anim) {
            this.display = display;
            this.kind = kind;
            this.anim = anim;
        }

        double angle(long age) {
            return waveBase + Math.sin(age * waveSpeed) * waveAmp;
        }
    }
}