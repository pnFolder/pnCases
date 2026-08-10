package ru.privatenull.cases;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.IdleParticleSettings;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.pnlibrary.compat.BukkitCompat.ParticleCompat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class IdleParticleService {

    private static final String DISPLAY_TAG = "pncases_idle_showcase";
    private static final Particle.DustOptions ARCANE = dust(126, 75, 255, 0.92f);
    private static final Particle.DustOptions ARCANE_EDGE = dust(226, 143, 255, 0.72f);
    private static final Particle.DustOptions VOID = dust(70, 20, 125, 1.02f);
    private static final Particle.DustOptions VOID_EDGE = dust(203, 65, 255, 0.76f);
    private static final Particle.DustOptions STORM = dust(45, 205, 255, 0.80f);
    private static final Particle.DustOptions STORM_EDGE = dust(238, 252, 255, 0.64f);
    private static final Particle.DustOptions FIRE = dust(255, 70, 18, 1.02f);
    private static final Particle.DustOptions FIRE_EDGE = dust(255, 214, 65, 0.76f);
    private static final Particle.DustOptions TOXIC = dust(65, 225, 86, 0.92f);
    private static final Particle.DustOptions TOXIC_EDGE = dust(210, 255, 74, 0.68f);
    private static final Particle.DustOptions FROST = dust(98, 205, 255, 0.88f);
    private static final Particle.DustOptions FROST_EDGE = dust(235, 252, 255, 0.68f);
    private static final Particle.DustOptions SOUL = dust(39, 198, 190, 0.92f);
    private static final Particle.DustOptions SOUL_EDGE = dust(142, 255, 239, 0.68f);
    private static final Particle.DustOptions OCEAN = dust(20, 112, 232, 0.94f);
    private static final Particle.DustOptions OCEAN_EDGE = dust(65, 235, 255, 0.70f);
    private static final Particle.DustOptions WING = dust(219, 231, 255, 0.86f);
    private static final Particle.DustOptions WING_EDGE = dust(126, 184, 255, 0.66f);
    private static final Particle.DustOptions DRAGON = dust(116, 58, 214, 0.90f);
    private static final Particle.DustOptions DRAGON_EDGE = dust(221, 102, 255, 0.68f);

    private final PnCasesPlugin plugin;
    private final CaseManager caseManager;
    private final List<Entry> entries = new ArrayList<>();

    private org.bukkit.scheduler.BukkitTask task;
    private long tick;

    public IdleParticleService(PnCasesPlugin plugin, CaseManager caseManager) {
        this.plugin = plugin;
        this.caseManager = caseManager;
    }

    public void syncCases(Collection<CaseDefinition> definitions) {
        removeOrphanedDisplays();
        entries.clear();

        for (CaseDefinition definition : definitions) {
            addEntries(definition);
        }

        if (entries.isEmpty()) {
            stop();
            return;
        }

        if (task == null || task.isCancelled()) {
            tick = 0L;
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    public void shutdown() {
        entries.clear();
        removeOrphanedDisplays();
        stop();
    }

    /** Updates the showcase of just one case after a Machine GUI edit. */
    public void refreshCase(CaseDefinition previous, CaseDefinition updated) {
        String previousName = previous == null ? null : previous.name();
        String updatedName = updated == null ? null : updated.name();
        if (previousName != null) {
            removeCaseEntries(previousName);
        }
        if (updatedName != null && !updatedName.equals(previousName)) {
            removeCaseEntries(updatedName);
        }
        if (updated != null) {
            addEntries(updated);
        }

        if (entries.isEmpty()) {
            stop();
        } else if (task == null || task.isCancelled()) {
            tick = 0L;
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    /** Removes showcase entities left by older builds before their world disappears. */
    public void removeWorldDisplays(World world) {
        if (world == null) {
            return;
        }
        for (Entity entity : world.getEntities()) {
            if (entity.getScoreboardTags().contains(DISPLAY_TAG)) {
                entity.remove();
            }
        }
    }

    private void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        tick++;
        for (Entry entry : entries) {
            Location block = entry.blockLocation();
            World world = block.getWorld();
            if (world == null) {
                continue;
            }

            IdleParticleSettings settings = entry.settings();
            Location base = block.clone().add(0.5, 0.08, 0.5);
            if (caseManager.isCaseBlockBusy(block) || !hasViewer(world, base, settings.viewDistance())) {
                continue;
            }

            int renderInterval = Math.max(3, settings.intervalTicks());
            if (tick % renderInterval == 0) {
                spawnPattern(world, base, sceneLocation(base, settings), settings);
            }
        }
    }

    private Location sceneLocation(Location base, IdleParticleSettings settings) {
        double y = 0.98 + Math.min(0.65, settings.height() * 0.28);
        return base.clone().add(0.0, y, 0.0);
    }

    private void spawnPattern(World world, Location base, Location displayLocation, IdleParticleSettings settings) {
        double rotation = tick * settings.speed();
        drawThemeAnimation(world, base, displayLocation, settings, rotation);
    }

    private void drawThemeAnimation(
            World world,
            Location base,
            Location anchor,
            IdleParticleSettings settings,
            double rotation
    ) {
        Location center = animatedThemeCenter(anchor, settings.theme(), rotation);
        switch (settings.theme()) {
            case MAGIC -> drawMagicSeal(world, center, settings, rotation);
            case PORTAL -> drawPortalRift(world, center, settings, rotation);
            case ELECTRIC -> drawElectricArcs(world, center, settings, rotation);
            case FIRE -> {
                drawMovementTrail(world, anchor, settings.theme(), rotation, FIRE);
                drawPhoenix(world, base, center, settings, rotation);
            }
            case TOXIC -> drawCobra(world, center, settings, rotation);
            case FROST -> drawSnowflake(world, center, settings, rotation);
            case SOUL -> drawSoulGuardian(world, center, settings, rotation);
            case OCEAN -> drawLeviathan(world, center, settings, rotation);
            case WINGS -> drawCelestialWings(world, center, settings, rotation);
            case DRAGON -> drawShowcaseDragon(world, center, settings, rotation);
        }
    }

    private Location animatedThemeCenter(
            Location anchor,
            IdleParticleSettings.Theme theme,
        double rotation
    ) {
        return switch (theme) {
            case MAGIC, PORTAL, ELECTRIC, FROST, WINGS -> anchor.clone();
            case FIRE -> anchor.clone().add(
                    Math.sin(rotation * 0.28) * 1.05,
                    0.20 + Math.sin(rotation * 0.56) * 0.34,
                    Math.sin(rotation * 0.56) * 0.55);
            case TOXIC -> anchor.clone().add(
                    Math.sin(rotation * 0.62) * 0.07,
                    Math.sin(rotation * 0.88) * 0.04,
                    0.0);
            case SOUL -> anchor.clone().add(0.0, Math.sin(rotation * 0.42) * 0.08, 0.0);
            case OCEAN, DRAGON -> anchor.clone();
        };
    }

    private void drawMovementTrail(
            World world,
            Location anchor,
            IdleParticleSettings.Theme theme,
            double rotation,
            Particle.DustOptions color
    ) {
        for (int point = 1; point <= 4; point++) {
            Location trail = animatedThemeCenter(anchor, theme, rotation - point * 0.22);
            dust(world, trail, color);
        }
    }

    private void drawMagicSeal(World world, Location center, IdleParticleSettings settings, double rotation) {
        Location seal = center.clone().add(0.0, -0.62, 0.0);
        double spin = rotation * 0.34;
        drawHorizontalRing(world, seal, 0.72, 18, spin, ARCANE);

        Location[] star = new Location[5];
        for (int point = 0; point < star.length; point++) {
            double angle = spin + point * Math.PI * 2.0 / 5.0 - Math.PI / 2.0;
            star[point] = seal.clone().add(Math.cos(angle) * 0.58, 0.012, Math.sin(angle) * 0.58);
            dust(world, star[point], ARCANE_EDGE);
        }
        for (int point = 0; point < star.length; point++) {
            dustLine(world, star[point], star[(point + 2) % star.length], ARCANE_EDGE, 0.24);
        }

        for (int rune = 0; rune < 3; rune++) {
            double angle = -spin * 0.7 + rune * Math.PI * 2.0 / 3.0;
            double rise = ((tick * 0.018) + rune / 3.0) % 1.0;
            Location from = seal.clone().add(Math.cos(angle) * 0.43, 0.06, Math.sin(angle) * 0.43);
            Location to = from.clone().add(0.0, 0.34 + rise * 0.45, 0.0);
            dustLine(world, from, to, rune == 0 ? ARCANE_EDGE : ARCANE, 0.22);
        }
        ParticleCompat.spawn(world, center, new String[]{"ENCHANT", "ENCHANTMENT_TABLE"},
                1, 0.04, 0.06, 0.04, 0.02);
    }

    private void drawPortalRift(World world, Location center, IdleParticleSettings settings, double rotation) {
        double facing = facingYaw(world, center);
        double pulse = 1.0 + Math.sin(rotation * 1.7) * 0.08;
        for (int layer = 0; layer < 2; layer++) {
            double width = (layer == 0 ? 0.55 : 0.40) * pulse;
            double height = layer == 0 ? 0.92 : 0.72;
            double phase = rotation * (layer == 0 ? 0.24 : -0.31);
            for (int point = 0; point < 20; point++) {
                double angle = point * Math.PI * 2.0 / 20.0 + phase;
                Location edge = rotated(center, Math.cos(angle) * width, Math.sin(angle) * height,
                        Math.sin(angle * 3.0 + rotation) * 0.035, facing);
                dust(world, edge, layer == 0 ? VOID_EDGE : VOID);
            }
        }
        for (int ray = 0; ray < 3; ray++) {
            double angle = rotation * 0.8 + ray * Math.PI * 2.0 / 3.0;
            Location from = rotated(center, Math.cos(angle) * 0.86, Math.sin(angle) * 0.48, 0.0, facing);
            Location to = rotated(center, Math.cos(angle) * 0.18, Math.sin(angle) * 0.12, 0.0, facing);
            dustLine(world, from, to, VOID_EDGE, 0.25);
        }
        ParticleCompat.spawn(world, center, new String[]{"REVERSE_PORTAL", "PORTAL"},
                1, 0.03, 0.05, 0.03, 0.0);
    }

    private void drawElectricArcs(World world, Location center, IdleParticleSettings settings, double rotation) {
        double spin = rotation * 0.26;
        Location lower = center.clone().add(0.0, -0.52, 0.0);
        Location upper = center.clone().add(0.0, 0.57, 0.0);
        drawHorizontalRing(world, lower, 0.62, 14, spin, STORM);
        drawHorizontalRing(world, upper, 0.49, 12, -spin, STORM_EDGE);
        for (int tower = 0; tower < 4; tower++) {
            double angle = spin + tower * Math.PI / 2.0;
            Location from = lower.clone().add(Math.cos(angle) * 0.62, 0.0, Math.sin(angle) * 0.62);
            Location to = upper.clone().add(Math.cos(angle + 0.20) * 0.49, 0.0, Math.sin(angle + 0.20) * 0.49);
            drawJaggedLine(world, from, to, STORM_EDGE, tower);
        }
        for (int bolt = 0; bolt < 2; bolt++) {
            double angle = -spin * 1.8 + bolt * Math.PI;
            Location from = center.clone().add(Math.cos(angle) * 0.56, 0.22, Math.sin(angle) * 0.56);
            drawJaggedLine(world, from, center, STORM_EDGE, bolt + 7);
        }
        ParticleCompat.spawn(world, center, new String[]{"ELECTRIC_SPARK", "FIREWORKS_SPARK"},
                1, 0.03, 0.05, 0.03, 0.0);
    }

    private void drawPhoenix(
            World world,
            Location base,
            Location center,
            IdleParticleSettings settings,
            double rotation
    ) {
        double facing = facingYaw(world, center);
        double flap = Math.sin(rotation * 2.6) * 0.25;
        double bank = Math.cos(rotation * 0.31) * 0.16;
        Location chest = rotated(center, 0.0, 0.04, 0.0, facing);
        Location head = rotated(center, 0.0, 0.58, 0.02, facing);
        for (int point = 0; point < 12; point++) {
            double angle = point * Math.PI * 2.0 / 12.0;
            dust(world, rotated(chest, Math.cos(angle) * 0.25,
                    Math.sin(angle) * 0.38, 0.0, facing), FIRE);
        }
        for (int point = 0; point < 10; point++) {
            double angle = point * Math.PI * 2.0 / 10.0;
            dust(world, rotated(head, Math.cos(angle) * 0.20,
                    Math.sin(angle) * 0.17, 0.0, facing), FIRE_EDGE);
        }
        dustLine(world, rotated(head, 0.0, -0.02, 0.04, facing),
                rotated(head, 0.0, -0.16, 0.24, facing), FIRE_EDGE, 0.10);
        for (int eye = -1; eye <= 1; eye += 2) {
            dust(world, rotated(head, eye * 0.08, 0.045, 0.09, facing), STORM_EDGE);
        }
        for (int crest = -1; crest <= 1; crest++) {
            dustLine(world, rotated(head, crest * 0.08, 0.12, 0.0, facing),
                    rotated(head, crest * 0.15, 0.38 + Math.abs(crest) * 0.05, -0.03, facing),
                    FIRE_EDGE, 0.12);
        }

        for (int side = -1; side <= 1; side += 2) {
            Location shoulder = rotated(center, side * 0.20, 0.18, 0.02, facing);
            Location elbow = rotated(center, side * 0.92, 0.68 + flap + side * bank, 0.04, facing);
            Location tip = rotated(center, side * 1.62,
                    0.34 + flap * 1.45 + side * bank * 1.55, 0.12, facing);
            Location rear = rotated(center, side * 1.04,
                    -0.42 + flap * 0.25 + side * bank * 0.45, 0.16, facing);
            dustLine(world, shoulder, elbow, FIRE_EDGE, 0.17);
            dustLine(world, elbow, tip, FIRE_EDGE, 0.17);
            dustLine(world, tip, rear, FIRE, 0.17);
            dustLine(world, rear, chest, FIRE, 0.17);
            for (int feather = 1; feather <= 5; feather++) {
                double progress = feather / 6.0;
                Location root = lerp(shoulder, elbow, progress);
                Location end = lerp(tip, rear, progress)
                        .add(0.0, -Math.sin(progress * Math.PI) * 0.18, 0.0);
                dustLine(world, root, end, feather == 1 || feather == 5 ? FIRE_EDGE : FIRE, 0.22);
            }
        }

        for (int tail = 0; tail < 5; tail++) {
            double side = tail - 2.0;
            Location from = rotated(center, side * 0.07, -0.24, 0.04, facing);
            Location to = rotated(center, side * 0.22 + Math.sin(rotation * 1.8 + tail) * 0.10,
                    -1.04 - Math.abs(side) * 0.08, 0.14, facing);
            dustLine(world, from, to, tail == 2 ? FIRE_EDGE : FIRE, 0.17);
        }
        if (tick % 9 == 0) {
            ParticleCompat.spawn(world, base.clone().add(0.0, 0.24, 0.0),
                    new String[]{"FLAME"}, 1, 0.05, 0.02, 0.05, 0.0);
        }
    }

    private void drawCobra(World world, Location center, IdleParticleSettings settings, double rotation) {
        double facing = facingYaw(world, center);
        double sway = Math.sin(rotation * 0.88) * 0.08;
        Location coil = center.clone().add(0.0, -0.62, 0.0);

        drawHorizontalRing(world, coil, 0.72, 20, rotation * 0.10, TOXIC);
        drawHorizontalRing(world, coil.clone().add(0.0, 0.13, 0.0),
                0.53, 18, -rotation * 0.13, TOXIC_EDGE);

        Location neckLeftBottom = rotated(center, -0.19, -0.42, 0.0, facing);
        Location neckRightBottom = rotated(center, 0.19, -0.42, 0.0, facing);
        Location neckLeftTop = rotated(center, -0.14 + sway, 0.34, 0.0, facing);
        Location neckRightTop = rotated(center, 0.14 + sway, 0.34, 0.0, facing);
        dustLine(world, neckLeftBottom, neckLeftTop, TOXIC, 0.14);
        dustLine(world, neckRightBottom, neckRightTop, TOXIC, 0.14);
        for (int rib = 1; rib <= 4; rib++) {
            double progress = rib / 5.0;
            dustLine(world, lerp(neckLeftBottom, neckLeftTop, progress),
                    lerp(neckRightBottom, neckRightTop, progress), TOXIC_EDGE, 0.14);
        }

        Location hoodLeft = rotated(center, -0.55 + sway, 0.52, 0.0, facing);
        Location hoodLeftTop = rotated(center, -0.32 + sway, 0.88, 0.0, facing);
        Location hoodRightTop = rotated(center, 0.32 + sway, 0.88, 0.0, facing);
        Location hoodRight = rotated(center, 0.55 + sway, 0.52, 0.0, facing);
        dustLine(world, neckLeftTop, hoodLeft, TOXIC_EDGE, 0.14);
        dustLine(world, hoodLeft, hoodLeftTop, TOXIC_EDGE, 0.14);
        dustLine(world, hoodRightTop, hoodRight, TOXIC_EDGE, 0.14);
        dustLine(world, hoodRight, neckRightTop, TOXIC_EDGE, 0.14);
        dustLine(world, hoodLeft, neckLeftTop, TOXIC, 0.18);
        dustLine(world, hoodRight, neckRightTop, TOXIC, 0.18);

        Location head = rotated(center, sway, 0.73, 0.03, facing);
        for (int point = 0; point < 12; point++) {
            double angle = point * Math.PI * 2.0 / 12.0;
            dust(world, rotated(head, Math.cos(angle) * 0.24,
                    Math.sin(angle) * 0.19, 0.0, facing), TOXIC_EDGE);
        }
        for (int eye = -1; eye <= 1; eye += 2) {
            dust(world, rotated(head, eye * 0.095, 0.045, 0.10, facing), FIRE_EDGE);
        }
        Location mouth = rotated(head, 0.0, -0.12, 0.10, facing);
        Location tongueFork = rotated(head, 0.0, -0.25, 0.16, facing);
        dustLine(world, mouth, tongueFork, FIRE, 0.10);
        dustLine(world, tongueFork, rotated(head, -0.08, -0.31, 0.17, facing), FIRE, 0.08);
        dustLine(world, tongueFork, rotated(head, 0.08, -0.31, 0.17, facing), FIRE, 0.08);

        if (tick % 12 == 0) {
            ParticleCompat.spawn(world, mouth, new String[]{"WITCH", "SPELL_WITCH"},
                    1, 0.02, 0.03, 0.02, 0.0);
        }
    }

    private void drawSnowflake(World world, Location center, IdleParticleSettings settings, double rotation) {
        double facing = facingYaw(world, center);
        double spin = rotation * 0.15;
        for (int arm = 0; arm < 6; arm++) {
            double angle = spin + arm * Math.PI / 3.0;
            Location tip = rotated(center, Math.cos(angle) * 0.72, Math.sin(angle) * 0.72, 0.0, facing);
            dustLine(world, center, tip, arm % 2 == 0 ? FROST_EDGE : FROST, 0.18);
            for (int branch = 1; branch <= 2; branch++) {
                double distance = 0.31 + branch * 0.14;
                Location joint = rotated(center, Math.cos(angle) * distance, Math.sin(angle) * distance, 0.0, facing);
                for (int side = -1; side <= 1; side += 2) {
                    double branchAngle = angle + side * Math.PI / 4.0;
                    Location branchTip = rotated(joint, Math.cos(branchAngle) * 0.20,
                            Math.sin(branchAngle) * 0.20, 0.0, facing);
                    dustLine(world, joint, branchTip, FROST_EDGE, 0.16);
                }
            }
        }
        if (tick % 9 == 0) {
            ParticleCompat.spawn(world, center, new String[]{"SNOWFLAKE", "CLOUD"},
                    1, 0.04, 0.05, 0.04, 0.0);
        }
    }

    private void drawSoulGuardian(World world, Location center, IdleParticleSettings settings, double rotation) {
        double facing = facingYaw(world, center);
        double sway = Math.sin(rotation * 1.1) * 0.10;
        Location hood = rotated(center, sway, 0.48, 0.0, facing);
        for (int point = 0; point < 16; point++) {
            double angle = point * Math.PI * 2.0 / 16.0;
            dust(world, rotated(hood, Math.cos(angle) * 0.38,
                    Math.sin(angle) * 0.43, 0.0, facing), SOUL_EDGE);
        }
        for (int eye = -1; eye <= 1; eye += 2) {
            dust(world, rotated(hood, eye * 0.14, 0.07, 0.10, facing), STORM_EDGE);
        }
        dustLine(world, rotated(hood, -0.11, -0.13, 0.08, facing),
                rotated(hood, 0.11, -0.13, 0.08, facing), SOUL, 0.10);

        Location leftShoulder = rotated(center, -0.66 + sway, 0.00, 0.0, facing);
        Location rightShoulder = rotated(center, 0.66 + sway, 0.00, 0.0, facing);
        dustLine(world, leftShoulder, rightShoulder, SOUL_EDGE, 0.17);
        Location leftHand = rotated(center, -1.00 + sway, -0.22 + Math.sin(rotation) * 0.08, 0.06, facing);
        Location rightHand = rotated(center, 1.00 + sway, -0.22 - Math.sin(rotation) * 0.08, 0.06, facing);
        dustLine(world, leftShoulder, leftHand, SOUL_EDGE, 0.17);
        dustLine(world, rightShoulder, rightHand, SOUL_EDGE, 0.17);
        for (int trail = -2; trail <= 2; trail++) {
            Location from = rotated(center, trail * 0.25, -0.22, 0.0, facing);
            Location to = rotated(center, trail * 0.31 + Math.sin(rotation + trail) * 0.12,
                    -1.02 - Math.abs(trail) * 0.05, 0.08, facing);
            dustLine(world, from, to, trail == 0 ? SOUL_EDGE : SOUL, 0.18);
        }
        ParticleCompat.spawn(world, rotated(hood, 0.0, 0.0, -0.05, facing),
                new String[]{"SOUL_FIRE_FLAME", "FLAME"}, 1, 0.03, 0.03, 0.03, 0.0);
    }

    private void drawLeviathan(World world, Location center, IdleParticleSettings settings, double rotation) {
        double orbit = rotation * 0.20;
        Location creature = center.clone().add(Math.cos(orbit) * 0.88,
                Math.sin(orbit * 2.0) * 0.18, Math.sin(orbit) * 0.88);
        double facing = orbit + Math.PI / 2.0;
        Location previous = null;
        for (int segment = 0; segment < 24; segment++) {
            double progress = segment / 23.0;
            Location body = rotated(creature,
                    Math.sin(rotation * 1.2 - segment * 0.38) * (0.05 + progress * 0.15),
                    Math.sin(segment * 0.28) * 0.045,
                    -0.78 + segment * 0.075, facing);
            dust(world, body, segment > 19 ? OCEAN_EDGE : OCEAN);
            if (previous != null && segment % 2 == 0) dustLine(world, previous, body, OCEAN, 0.16);
            previous = body;
        }
        for (double z : new double[]{-0.30, 0.12, 0.48}) {
            for (int point = 0; point < 8; point++) {
                double angle = point * Math.PI / 4.0;
                dust(world, rotated(creature, Math.cos(angle) * 0.22,
                        Math.sin(angle) * 0.16, z, facing), OCEAN);
            }
        }
        Location head = rotated(creature, 0.0, 0.06, 1.02, facing);
        for (int point = 0; point < 12; point++) {
            double angle = point * Math.PI * 2.0 / 12.0;
            dust(world, rotated(head, Math.cos(angle) * 0.29,
                    Math.sin(angle) * 0.22, 0.0, facing), OCEAN_EDGE);
        }
        for (int eye = -1; eye <= 1; eye += 2) {
            dust(world, rotated(head, eye * 0.13, 0.08, 0.13, facing), STORM_EDGE);
        }
        for (int side = -1; side <= 1; side += 2) {
            Location finRoot = rotated(creature, side * 0.14, 0.0, 0.24, facing);
            Location finTip = rotated(creature, side * 0.67,
                    -0.14 + Math.sin(rotation * 2.0) * 0.10, -0.03, facing);
            dustLine(world, finRoot, finTip, OCEAN_EDGE, 0.16);
            Location tailRoot = rotated(creature, 0.0, 0.0, -0.76, facing);
            Location tailTip = rotated(creature, side * 0.46, 0.04, -1.12, facing);
            dustLine(world, tailRoot, tailTip, OCEAN_EDGE, 0.15);
        }
        if (tick % 9 == 0) {
            ParticleCompat.spawn(world, center, new String[]{"BUBBLE_POP", "WATER_BUBBLE", "BUBBLE"},
                    1, 0.05, 0.08, 0.05, 0.0);
        }
    }

    private void drawCelestialWings(World world, Location center, IdleParticleSettings settings, double rotation) {
        double flap = Math.sin(rotation * 2.4) * 0.32;
        double facing = facingYaw(world, center);
        for (int side = -1; side <= 1; side += 2) {
            Location root = rotated(center, side * 0.12, 0.18, 0.08, facing);
            Location elbow = rotated(center, side * 1.02, 0.78 + flap, 0.12, facing);
            Location highTip = rotated(center, side * 1.78,
                    0.46 + flap * 1.45, 0.18, facing);
            Location lowTip = rotated(center, side * 1.48,
                    -0.78 + flap * 0.20, 0.20, facing);
            dustLine(world, root, elbow, WING_EDGE, 0.16);
            dustLine(world, elbow, highTip, WING_EDGE, 0.16);
            dustLine(world, highTip, lowTip, WING, 0.16);
            dustLine(world, lowTip, root, WING, 0.16);
            for (int feather = 1; feather <= 7; feather++) {
                double progress = feather / 8.0;
                Location featherRoot = lerp(root, elbow, progress);
                Location featherTip = lerp(highTip, lowTip, progress)
                        .add(0.0, -Math.sin(progress * Math.PI) * 0.24, 0.0);
                dustLine(world, featherRoot, featherTip,
                        feather == 1 || feather == 7 ? WING_EDGE : WING, 0.20);
            }
        }
        ParticleCompat.spawn(world, center.clone().add(0.0, 0.08, 0.0),
                new String[]{"END_ROD", "FIREWORKS_SPARK"}, 1, 0.03, 0.05, 0.03, 0.0);
    }

    private void drawShowcaseDragon(World world, Location center, IdleParticleSettings settings, double rotation) {
        double orbit = rotation * 0.20;
        Location dragonCenter = center.clone().add(Math.cos(orbit) * 1.02,
                0.26 + Math.sin(orbit * 2.0) * 0.20, Math.sin(orbit) * 1.02);
        double facing = orbit + Math.PI / 2.0;

        for (int segment = 0; segment < 19; segment++) {
            double progress = segment / 18.0;
            double curve = Math.sin(rotation * 1.3 - segment * 0.48) * (0.04 + progress * 0.13);
            Location body = rotated(dragonCenter, curve, -segment * 0.035,
                    -0.70 + segment * 0.085, facing);
            dust(world, body, segment > 15 ? DRAGON_EDGE : DRAGON);
        }
        for (double z : new double[]{-0.28, 0.16, 0.50}) {
            for (int ring = 0; ring < 8; ring++) {
                double angle = ring * Math.PI / 4.0;
                dust(world, rotated(dragonCenter, Math.cos(angle) * 0.24,
                        0.06 + Math.sin(angle) * 0.20, z, facing), DRAGON);
            }
        }

        Location head = rotated(dragonCenter, 0.0, 0.08, 0.98, facing);
        for (int ring = 0; ring < 12; ring++) {
            double angle = ring * Math.PI * 2.0 / 12.0;
            dust(world, rotated(head, Math.cos(angle) * 0.27,
                    Math.sin(angle) * 0.21, 0.0, facing), DRAGON_EDGE);
        }
        for (int eye = -1; eye <= 1; eye += 2) {
            dust(world, rotated(head, eye * 0.12, 0.08, 0.14, facing), STORM_EDGE);
        }
        dustLine(world, head, rotated(head, 0.0, -0.05, 0.32, facing), DRAGON_EDGE, 0.11);

        double flap = Math.sin(rotation * 3.0) * 0.30;
        for (int side = -1; side <= 1; side += 2) {
            Location shoulder = rotated(dragonCenter, side * 0.14, 0.15, 0.04, facing);
            Location elbow = rotated(dragonCenter, side * 0.78, 0.55 + flap, -0.10, facing);
            Location tip = rotated(dragonCenter, side * 1.42, 0.30 + flap * 1.40, -0.48, facing);
            Location rear = rotated(dragonCenter, side * 0.84, -0.22 + flap * 0.24, -0.66, facing);
            dustLine(world, shoulder, elbow, DRAGON_EDGE, 0.17);
            dustLine(world, elbow, tip, DRAGON_EDGE, 0.17);
            dustLine(world, tip, rear, DRAGON, 0.17);
            dustLine(world, rear, shoulder, DRAGON, 0.17);
            dustLine(world, shoulder, tip, DRAGON, 0.22);
            Location horn = rotated(head, side * 0.11, 0.12, -0.05, facing);
            Location hornTip = rotated(head, side * 0.20, 0.30, -0.13, facing);
            dustLine(world, horn, hornTip, DRAGON_EDGE, 0.13);
        }

        if (tick % 9 == 0) {
            Location breath = rotated(head, 0.0, -0.02, 0.22, facing);
            ParticleCompat.spawn(world, breath, new String[]{"DRAGON_BREATH", "SPELL_WITCH"},
                    1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    private Location rotated(Location center, double x, double y, double z, double yaw) {
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        return center.clone().add(x * cos - z * sin, y, x * sin + z * cos);
    }

    private double facingYaw(World world, Location center) {
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Player player : world.getPlayers()) {
            double distance = player.getLocation().distanceSquared(center);
            if (distance < nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        if (nearest == null) {
            return 0.0;
        }
        double dx = nearest.getLocation().getX() - center.getX();
        double dz = nearest.getLocation().getZ() - center.getZ();
        return Math.atan2(-dx, dz);
    }

    private void drawHorizontalRing(
            World world,
            Location center,
            double radius,
            int points,
            double rotation,
            Particle.DustOptions color
    ) {
        for (int point = 0; point < points; point++) {
            double angle = rotation + point * Math.PI * 2.0 / points;
            dust(world, center.clone().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius), color);
        }
    }

    private void drawJaggedLine(
            World world,
            Location from,
            Location to,
            Particle.DustOptions color,
            int seed
    ) {
        Location previous = from;
        for (int point = 1; point <= 5; point++) {
            double progress = point / 5.0;
            Location next = lerp(from, to, progress);
            if (point < 5) {
                next.add(
                        Math.sin(tick * 1.37 + seed * 2.4 + point) * 0.055,
                        Math.cos(tick * 1.11 + seed + point * 2.0) * 0.045,
                        Math.sin(tick * 1.53 + seed * 3.1 - point) * 0.055
                );
            }
            dustLine(world, previous, next, color, 0.17);
            previous = next;
        }
    }

    private void dustLine(
            World world,
            Location from,
            Location to,
            Particle.DustOptions color,
            double spacing
    ) {
        double distance = from.distance(to);
        int points = Math.max(1, (int) Math.ceil(distance / Math.max(0.12, spacing)));
        for (int point = 0; point <= points; point++) {
            dust(world, lerp(from, to, point / (double) points), color);
        }
    }

    private Location lerp(Location from, Location to, double progress) {
        return from.clone().add(
                (to.getX() - from.getX()) * progress,
                (to.getY() - from.getY()) * progress,
                (to.getZ() - from.getZ()) * progress
        );
    }

    private void dust(World world, Location location, Particle.DustOptions color) {
        Particle particle = ParticleCompat.first("DUST", "REDSTONE");
        if (particle == null) {
            return;
        }
        try {
            world.spawnParticle(particle, location, 1, 0.0, 0.0, 0.0, 0.0, color);
        } catch (Throwable ignored) {
            ParticleCompat.spawn(world, location, new String[]{"END_ROD", "FIREWORKS_SPARK"},
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static Particle.DustOptions dust(int red, int green, int blue, float size) {
        return new Particle.DustOptions(Color.fromRGB(red, green, blue), size);
    }

    private boolean hasViewer(World world, Location base, double viewDistance) {
        double maxDistanceSquared = viewDistance * viewDistance;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(base) <= maxDistanceSquared) {
                return true;
            }
        }
        return false;
    }

    private void removeCaseEntries(String caseName) {
        for (Entry entry : new ArrayList<>(entries)) {
            if (entry.caseName().equals(caseName)) {
                entries.remove(entry);
            }
        }
    }

    private void addEntries(CaseDefinition definition) {
        IdleParticleSettings settings = definition.idleParticles();
        if (settings == null || !settings.enabled()) {
            return;
        }

        for (Location location : definition.blockLocations()) {
            if (location != null && location.getWorld() != null) {
                entries.add(new Entry(definition.name(), location.clone(), settings));
            }
        }
    }

    private void removeOrphanedDisplays() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(DISPLAY_TAG)) {
                    entity.remove();
                }
            }
        }
    }

    private record Entry(
            String caseName,
            Location blockLocation,
            IdleParticleSettings settings
    ) {
    }
}
