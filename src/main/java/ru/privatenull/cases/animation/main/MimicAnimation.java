package ru.privatenull.cases.animation.main;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.cases.animation.AnimationRun;
import ru.privatenull.cases.animation.CaseAnimation;
import ru.privatenull.cases.animation.RewardLabel;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.pnlibrary.compat.BukkitCompat.MaterialCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * A hungry chest mimic assembled from display entities. The body has independent
 * lower, lid, mouth and eye parts, so the creature can squash, jump, chew and
 * spit out the final reward without spawning AI or a collidable mob.
 */
public final class MimicAnimation extends CaseAnimation {

    private static final int WAKE_END = 38;
    private static final int JUMP_END = 82;
    private static final int FEAST_END = 158;
    private static final int COUGH_END = 190;
    private static final int REVEAL_END = 248;
    private static final Particle.DustOptions TOOTH =
            new Particle.DustOptions(Color.fromRGB(245, 242, 220), 0.86f);
    private static final Particle.DustOptions TONGUE =
            new Particle.DustOptions(Color.fromRGB(220, 48, 82), 0.92f);
    private static final Particle.DustOptions HUNGER =
            new Particle.DustOptions(Color.fromRGB(255, 174, 55), 0.74f);

    public MimicAnimation(PnCasesPlugin plugin) {
        super(plugin);
    }

    @Override
    protected void start(AnimationRun run) {
        Player player = run.player();
        CaseDefinition definition = run.definition();
        Reward reward = run.reward();
        Location rawBase = run.base();
        World world = run.world();
        if (player == null || rawBase == null || world == null) {
            run.complete();
            return;
        }

        Location center = rawBase.getBlock().getLocation().add(0.5, 0.0, 0.5);
        Vector forward = horizontal(player.getLocation().toVector().subtract(center.toVector()));
        Vector right = right(forward);
        float yaw = (float) Math.toDegrees(Math.atan2(-forward.getX(), forward.getZ()));

        MimicRig mimic = MimicRig.create(run, world, center, right, forward, yaw);
        ItemStack rewardVisual = resolveRewardVisual(reward, definition);
        ItemDisplay rewardDisplay = item(run, world, center.clone().add(0.0, -3.0, 0.0), rewardVisual, 0.01f);
        RewardLabel rewardLabel = RewardLabel.create(run, center, resolveRewardName(reward, rewardVisual));

        List<ItemDisplay> bait = new ArrayList<>();
        for (ItemStack preview : previewItems(definition, rewardVisual)) {
            bait.add(item(run, world, center.clone().add(0.0, -3.0, 0.0), preview, 0.01f));
        }

        BukkitTask task = new BukkitRunnable() {
            private int tick;
            private boolean finished;

            @Override
            public void run() {
                if (finished) {
                    cancel();
                    return;
                }
                if (!player.isOnline() || run.isClosed()) {
                    finish(false);
                    return;
                }

                tick++;
                if (tick == 1) {
                    run.hide(rawBase.getBlock(), Material.AIR);
                    playSound(world, center, Sound.BLOCK_CHEST_OPEN, 0.16f, 0.58f);
                }

                if (tick <= WAKE_END) {
                    wake();
                } else if (tick <= JUMP_END) {
                    jump();
                } else if (tick <= FEAST_END) {
                    feast();
                } else if (tick <= COUGH_END) {
                    cough();
                } else if (tick <= REVEAL_END) {
                    reveal();
                } else {
                    finalBurst();
                    finish(true);
                }
            }

            private void wake() {
                double progress = tick / (double) WAKE_END;
                double tension = ease(progress);
                double tremble = Math.sin(tick * 1.45) * 0.035 * tension;
                Location body = center.clone().add(tremble, 0.66 + Math.abs(Math.sin(tick * 0.42)) * 0.025, -tremble);
                float squash = (float) (1.0 - Math.abs(Math.sin(tick * 0.55)) * 0.12 * tension);
                float stretch = (float) (Math.sin(tick * 0.55) * 0.08 * tension);
                float mouth = progress < 0.55 ? 0.0f
                        : (float) ((progress - 0.55) / 0.45 * (0.20 + Math.abs(Math.sin(tick * 0.7)) * 0.18));
                mimic.update(body, squash, stretch, mouth, (float) Math.max(0.0, (progress - 0.38) / 0.40));
                mimic.drawFace(world, mouth, false);

                if (tick == 18) {
                    playSound(world, center, Sound.BLOCK_CHEST_CLOSE, 0.12f, 0.45f);
                }
                if (tick == 30) {
                    playSound(world, center, Sound.ENTITY_RAVAGER_ROAR, 0.18f, 1.45f);
                    world.spawnParticle(Particle.POOF, body, 5, 0.42, 0.24, 0.42, 0.02);
                }
            }

            private void jump() {
                double progress = (tick - WAKE_END) / (double) (JUMP_END - WAKE_END);
                double hops = Math.abs(Math.sin(progress * Math.PI * 4.0));
                double side = Math.sin(progress * Math.PI * 2.0) * 0.48;
                Location body = center.clone()
                        .add(right.clone().multiply(side))
                        .add(0.0, 0.66 + hops * (0.68 - progress * 0.12), 0.0);
                float squash = (float) (0.88 + hops * 0.24);
                float mouth = (float) (0.22 + Math.abs(Math.sin(progress * Math.PI * 4.0)) * 0.48);
                mimic.update(body, squash, (float) ((1.0 - hops) * 0.13), mouth, 1.0f);
                mimic.drawFace(world, mouth, false);
                updateOrbit(body, 1.55, tick * 0.085, 0.46f);

                if ((tick - WAKE_END) % 11 == 1) {
                    playSound(world, body, Sound.BLOCK_WOOD_PLACE, 0.14f, 0.72f + (float) progress * 0.35f);
                    world.spawnParticle(Particle.CLOUD, body.clone().subtract(0.0, 0.48, 0.0),
                            3, 0.28, 0.05, 0.28, 0.01);
                }
            }

            private void feast() {
                double progress = (tick - JUMP_END) / (double) (FEAST_END - JUMP_END);
                double bob = Math.sin(tick * 0.22) * 0.06;
                Location body = center.clone().add(0.0, 0.70 + bob, 0.0);
                float chomp = (float) (0.46 + Math.abs(Math.sin(tick * 0.43)) * 0.42);
                mimic.update(body, 0.94f, (float) (Math.sin(tick * 0.43) * 0.08), chomp, 1.0f);
                mimic.drawFace(world, chomp, true);

                for (int index = 0; index < bait.size(); index++) {
                    int swallowStart = JUMP_END + 4 + index * 14;
                    int swallowEnd = swallowStart + 12;
                    ItemDisplay display = bait.get(index);
                    Location orbit = orbit(body, index, bait.size(), tick * 0.09, 1.55);
                    if (tick < swallowStart) {
                        display.teleport(orbit);
                        display.setRotation((tick * 8.0f + index * 55.0f) % 360.0f, 0.0f);
                        setItemScale(display, 0.46f);
                    } else if (tick <= swallowEnd) {
                        double pull = ease((tick - swallowStart) / (double) (swallowEnd - swallowStart));
                        Location target = mimic.mouth().clone().add(forward.clone().multiply(0.18));
                        Location position = lerp(orbit, target, pull);
                        display.teleport(position);
                        display.setRotation((tick * 18.0f) % 360.0f, 0.0f);
                        setItemScale(display, (float) (0.46 * (1.0 - pull)));
                        mimic.drawTongue(world, position);
                    } else {
                        setItemScale(display, 0.0f);
                    }

                    if (tick == swallowEnd) {
                        playSound(world, mimic.mouth(), Sound.ENTITY_GENERIC_EAT, 0.15f, 0.72f + index * 0.11f);
                        world.spawnParticle(Particle.POOF, mimic.mouth(), 3, 0.16, 0.12, 0.16, 0.01);
                    }
                }

                if (tick % 18 == 0) {
                    playSound(world, body, Sound.BLOCK_CHEST_CLOSE, 0.10f, 0.72f);
                }
            }

            private void cough() {
                double progress = (tick - FEAST_END) / (double) (COUGH_END - FEAST_END);
                double swell = Math.sin(progress * Math.PI);
                double hop = Math.abs(Math.sin(progress * Math.PI * 3.0)) * 0.24;
                Location body = center.clone().add(0.0, 0.68 + hop, 0.0);
                float squash = (float) (0.88 + swell * 0.34);
                float stretch = (float) (swell * 0.30);
                float mouth = (float) (0.18 + Math.abs(Math.sin(progress * Math.PI * 3.0)) * 0.34);
                mimic.update(body, squash, stretch, mouth, 1.0f);
                mimic.drawFace(world, mouth, false);

                if (tick % 7 == 0) {
                    playSound(world, body, Sound.ENTITY_PLAYER_BURP, 0.12f, 0.52f + (float) progress * 0.30f);
                    world.spawnParticle(Particle.SMOKE, mimic.mouth(), 2, 0.12, 0.10, 0.12, 0.01);
                }
            }

            private void reveal() {
                double progress = (tick - COUGH_END) / (double) (REVEAL_END - COUGH_END);
                double launch = Math.min(1.0, progress / 0.48);
                float mimicScale = (float) Math.max(0.05, 1.0 - Math.max(0.0, progress - 0.36) / 0.64);
                Location body = center.clone().add(0.0, 0.68 - Math.max(0.0, progress - 0.55) * 0.70, 0.0);
                mimic.update(body, 0.92f, 0.08f, (float) (1.0 - progress * 0.55), mimicScale);
                mimic.drawFace(world, (float) (1.0 - progress * 0.55), false);

                Location launchEnd = center.clone().add(forward.clone().multiply(0.72)).add(0.0, 2.75, 0.0);
                Location rewardLocation;
                if (progress < 0.48) {
                    Location from = mimic.mouth().clone().add(forward.clone().multiply(0.22));
                    rewardLocation = lerp(from, launchEnd, ease(launch));
                } else {
                    double settle = ease((progress - 0.48) / 0.52);
                    Location hover = center.clone().add(0.0, 2.12 + Math.sin(tick * 0.16) * 0.06, 0.0);
                    rewardLocation = lerp(launchEnd, hover, settle);
                }

                rewardDisplay.teleport(rewardLocation);
                rewardDisplay.setRotation((tick * 10.0f) % 360.0f, 0.0f);
                setItemScale(rewardDisplay, (float) Math.min(1.0, 0.08 + progress * 1.45));
                if (progress > 0.42 && rewardLabel != null) {
                    rewardLabel.showAbove(rewardLocation, 0.88);
                }

                if (tick == COUGH_END + 1) {
                    playSound(world, center, Sound.ENTITY_FOX_SPIT, 0.20f, 0.70f);
                    playSound(world, center, Sound.BLOCK_CHEST_OPEN, 0.16f, 1.55f);
                    world.spawnParticle(Particle.POOF, mimic.mouth(), 8, 0.28, 0.22, 0.28, 0.03);
                }
                if (tick == REVEAL_END - 12) {
                    playSound(world, rewardLocation, Sound.ENTITY_PLAYER_LEVELUP, 0.20f, 1.55f);
                }
            }

            private void updateOrbit(Location body, double radius, double rotation, float scale) {
                for (int index = 0; index < bait.size(); index++) {
                    ItemDisplay display = bait.get(index);
                    Location position = orbit(body, index, bait.size(), rotation, radius);
                    display.teleport(position);
                    display.setRotation((tick * 7.0f + index * 45.0f) % 360.0f, 0.0f);
                    setItemScale(display, scale);
                }
            }

            private void finalBurst() {
                Location rewardLocation = rewardDisplay.getLocation();
                world.spawnParticle(Particle.END_ROD, rewardLocation, 7, 0.30, 0.30, 0.30, 0.02);
                world.spawnParticle(Particle.FIREWORK, rewardLocation, 4, 0.25, 0.25, 0.25, 0.02);
                playSound(world, rewardLocation, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.22f, 1.20f);
            }

            private void finish(boolean complete) {
                if (finished) return;
                finished = true;
                if (complete) run.complete();
                else run.cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
        run.track(task);
    }

    private static List<ItemStack> previewItems(CaseDefinition definition, ItemStack rewardVisual) {
        List<ItemStack> result = new ArrayList<>();
        if (definition != null && definition.animationItems() != null) {
            for (ItemStack source : definition.animationItems()) {
                if (source == null || source.getType().isAir()) continue;
                ItemStack item = source.clone();
                item.setAmount(1);
                result.add(item);
                if (result.size() == 5) break;
            }
        }
        Material[] fallback = {
                Material.EMERALD,
                Material.DIAMOND,
                Material.GOLD_INGOT,
                MaterialCompat.first("AMETHYST_SHARD", "NETHER_STAR"),
                Material.NETHER_STAR
        };
        for (Material material : fallback) {
            if (result.size() >= 5) break;
            result.add(new ItemStack(material));
        }
        if (result.isEmpty() && rewardVisual != null) result.add(rewardVisual.clone());
        return result;
    }

    private static Location orbit(
            Location center,
            int index,
            int count,
            double rotation,
            double radius
    ) {
        double angle = rotation + index * Math.PI * 2.0 / Math.max(1, count);
        return center.clone().add(
                Math.cos(angle) * radius,
                0.92 + Math.sin(angle * 1.7) * 0.22,
                Math.sin(angle) * radius
        );
    }

    private static ItemDisplay item(
            AnimationRun run,
            World world,
            Location location,
            ItemStack stack,
            float scale
    ) {
        ItemDisplay display = (ItemDisplay) world.spawnEntity(location, EntityType.ITEM_DISPLAY);
        display.setItemStack(stack == null ? new ItemStack(Material.NETHER_STAR) : stack.clone());
        display.setBillboard(Display.Billboard.CENTER);
        display.setPersistent(false);
        display.setInvulnerable(true);
        setItemScale(display, scale);
        return run.track(display);
    }

    private static void setItemScale(ItemDisplay display, float scale) {
        if (display == null || display.isDead()) return;
        Transformation current = display.getTransformation();
        display.setTransformation(new Transformation(
                current.getTranslation(),
                current.getLeftRotation(),
                new Vector3f(scale, scale, scale),
                current.getRightRotation()
        ));
        display.setInterpolationDuration(1);
    }

    private static void setBlockTransform(
            BlockDisplay display,
            float scaleX,
            float scaleY,
            float scaleZ,
            float pitch
    ) {
        if (display == null || display.isDead()) return;
        display.setTransformation(new Transformation(
                new Vector3f(-scaleX / 2.0f, -scaleY / 2.0f, -scaleZ / 2.0f),
                new Quaternionf().rotateX(pitch),
                new Vector3f(scaleX, scaleY, scaleZ),
                new Quaternionf()
        ));
        display.setInterpolationDuration(1);
    }

    private static void setModelTransform(
            ItemDisplay display,
            float scaleX,
            float scaleY,
            float scaleZ,
            float pitch
    ) {
        if (display == null || display.isDead()) return;
        display.setTransformation(new Transformation(
                new Vector3f(),
                new Quaternionf().rotateX(pitch),
                new Vector3f(scaleX, scaleY, scaleZ),
                new Quaternionf()
        ));
        display.setInterpolationDuration(1);
    }

    private static Location local(
            Location origin,
            Vector right,
            Vector forward,
            double x,
            double y,
            double z
    ) {
        return origin.clone()
                .add(right.clone().multiply(x))
                .add(0.0, y, 0.0)
                .add(forward.clone().multiply(z));
    }

    private static Vector horizontal(Vector vector) {
        Vector result = vector == null ? new Vector() : vector.clone();
        result.setY(0.0);
        if (result.lengthSquared() < 0.0001) return new Vector(0.0, 0.0, 1.0);
        return result.normalize();
    }

    private static Vector right(Vector forward) {
        Vector horizontal = horizontal(forward);
        return new Vector(-horizontal.getZ(), 0.0, horizontal.getX()).normalize();
    }

    private static Location lerp(Location from, Location to, double progress) {
        double clamped = Math.max(0.0, Math.min(1.0, progress));
        return from.clone().add(to.toVector().subtract(from.toVector()).multiply(clamped));
    }

    private static double ease(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    private static void dust(World world, Location location, Particle.DustOptions color) {
        world.spawnParticle(Particle.DUST, location, 1, 0.0, 0.0, 0.0, 0.0, color);
    }

    private static void dustLine(
            World world,
            Location from,
            Location to,
            Particle.DustOptions color,
            double spacing
    ) {
        Vector delta = to.toVector().subtract(from.toVector());
        int points = Math.max(1, (int) Math.ceil(delta.length() / Math.max(0.08, spacing)));
        for (int point = 0; point <= points; point++) {
            dust(world, from.clone().add(delta.clone().multiply(point / (double) points)), color);
        }
    }

    private static final class MimicRig {

        private static final double[] TOOTH_X = {-0.30, -0.10, 0.10, 0.30};

        private final ItemDisplay body;
        private final BlockDisplay mouth;
        private final BlockDisplay leftEye;
        private final BlockDisplay rightEye;
        private final BlockDisplay leftPupil;
        private final BlockDisplay rightPupil;
        private final BlockDisplay leftFoot;
        private final BlockDisplay rightFoot;
        private final List<BlockDisplay> upperTeeth;
        private final List<BlockDisplay> lowerTeeth;
        private final Vector right;
        private final Vector forward;
        private final float yaw;
        private Location center;

        private MimicRig(
                ItemDisplay body,
                BlockDisplay mouth,
                BlockDisplay leftEye,
                BlockDisplay rightEye,
                BlockDisplay leftPupil,
                BlockDisplay rightPupil,
                BlockDisplay leftFoot,
                BlockDisplay rightFoot,
                List<BlockDisplay> upperTeeth,
                List<BlockDisplay> lowerTeeth,
                Vector right,
                Vector forward,
                float yaw,
                Location center
        ) {
            this.body = body;
            this.mouth = mouth;
            this.leftEye = leftEye;
            this.rightEye = rightEye;
            this.leftPupil = leftPupil;
            this.rightPupil = rightPupil;
            this.leftFoot = leftFoot;
            this.rightFoot = rightFoot;
            this.upperTeeth = upperTeeth;
            this.lowerTeeth = lowerTeeth;
            this.right = right;
            this.forward = forward;
            this.yaw = yaw;
            this.center = center.clone();
        }

        private static MimicRig create(
                AnimationRun run,
                World world,
                Location center,
                Vector right,
                Vector forward,
                float yaw
        ) {
            ItemDisplay body = model(run, world, center, Material.CHEST);
            BlockDisplay mouth = block(run, world, center, Material.BLACK_CONCRETE);
            BlockDisplay leftEye = block(run, world, center, Material.YELLOW_CONCRETE);
            BlockDisplay rightEye = block(run, world, center, Material.YELLOW_CONCRETE);
            BlockDisplay leftPupil = block(run, world, center, Material.BLACK_CONCRETE);
            BlockDisplay rightPupil = block(run, world, center, Material.BLACK_CONCRETE);
            BlockDisplay leftFoot = block(run, world, center, Material.DARK_OAK_PLANKS);
            BlockDisplay rightFoot = block(run, world, center, Material.DARK_OAK_PLANKS);
            List<BlockDisplay> upperTeeth = new ArrayList<>();
            List<BlockDisplay> lowerTeeth = new ArrayList<>();
            for (int ignored = 0; ignored < TOOTH_X.length; ignored++) {
                upperTeeth.add(block(run, world, center, Material.BONE_BLOCK));
                lowerTeeth.add(block(run, world, center, Material.BONE_BLOCK));
            }
            return new MimicRig(
                    body, mouth,
                    leftEye, rightEye, leftPupil, rightPupil,
                    leftFoot, rightFoot, upperTeeth, lowerTeeth,
                    right, forward, yaw, center
            );
        }

        private static ItemDisplay model(
                AnimationRun run,
                World world,
                Location location,
                Material material
        ) {
            ItemDisplay display = (ItemDisplay) world.spawnEntity(location, EntityType.ITEM_DISPLAY);
            display.setItemStack(new ItemStack(material));
            display.setBillboard(Display.Billboard.FIXED);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setPersistent(false);
            display.setInvulnerable(true);
            return run.track(display);
        }

        private static BlockDisplay block(
                AnimationRun run,
                World world,
                Location location,
                Material material
        ) {
            BlockDisplay display = (BlockDisplay) world.spawnEntity(location, EntityType.BLOCK_DISPLAY);
            display.setBlock(material.createBlockData());
            display.setPersistent(false);
            display.setInvulnerable(true);
            return run.track(display);
        }

        private void update(
                Location bodyCenter,
                float squash,
                float stretch,
                float mouthOpen,
                float visibility
        ) {
            center = bodyCenter.clone();
            float visible = Math.max(0.01f, visibility);
            float open = Math.min(1.0f, Math.max(0.0f, mouthOpen));
            float bodyWidth = (1.30f + stretch * 0.12f) * visible;
            float bodyHeight = 1.24f * Math.max(0.78f, squash) * visible;
            float bodyDepth = 1.24f * visible;
            float mouthHeight = (0.035f + open * 0.34f) * visible;
            float faceForward = 0.53f * visible;

            Location mouthLocation = local(bodyCenter, right, forward, 0.0, 0.03 * visible, faceForward);
            body.teleport(bodyCenter);
            mouth.teleport(mouthLocation);
            body.setRotation(yaw, 0.0f);
            mouth.setRotation(yaw, 0.0f);

            setModelTransform(body, bodyWidth, bodyHeight, bodyDepth, -open * 0.06f);
            setBlockTransform(mouth, 0.88f * visible, mouthHeight, 0.045f * visible, 0.0f);

            updateEyes(bodyCenter, visible, open, faceForward);
            updateFeet(bodyCenter, visible, squash);
            updateTeeth(bodyCenter, visible, open, faceForward, mouthHeight);
        }

        private void updateEyes(
                Location bodyCenter,
                float visible,
                float open,
                float faceForward
        ) {
            double eyeY = (0.31 + open * 0.08) * visible;
            double pupilForward = faceForward + 0.038 * visible;
            Location left = local(bodyCenter, right, forward, -0.28 * visible, eyeY, faceForward);
            Location rightLocation = local(bodyCenter, right, forward, 0.28 * visible, eyeY, faceForward);
            Location leftDot = local(bodyCenter, right, forward, -0.28 * visible, eyeY, pupilForward);
            Location rightDot = local(bodyCenter, right, forward, 0.28 * visible, eyeY, pupilForward);

            placeBlock(leftEye, left, 0.18f * visible, 0.14f * visible, 0.045f * visible);
            placeBlock(rightEye, rightLocation, 0.18f * visible, 0.14f * visible, 0.045f * visible);
            placeBlock(leftPupil, leftDot, 0.065f * visible, 0.075f * visible, 0.025f * visible);
            placeBlock(rightPupil, rightDot, 0.065f * visible, 0.075f * visible, 0.025f * visible);
        }

        private void updateFeet(Location bodyCenter, float visible, float squash) {
            double step = Math.sin(bodyCenter.getY() * 5.0) * 0.025;
            Location left = local(bodyCenter, right, forward,
                    -0.35 * visible, (-0.55 + step) * visible, -0.02);
            Location rightLocation = local(bodyCenter, right, forward,
                    0.35 * visible, (-0.55 - step) * visible, -0.02);
            float footHeight = 0.22f * Math.max(0.76f, squash) * visible;
            placeBlock(leftFoot, left, 0.18f * visible, footHeight, 0.25f * visible);
            placeBlock(rightFoot, rightLocation, 0.18f * visible, footHeight, 0.25f * visible);
        }

        private void updateTeeth(
                Location bodyCenter,
                float visible,
                float open,
                float faceForward,
                float mouthHeight
        ) {
            float toothWidth = 0.075f * visible;
            float toothHeight = open < 0.10f ? 0.01f : 0.105f * visible;
            float toothDepth = 0.055f * visible;
            double upperY = 0.03 * visible + mouthHeight * 0.50 - toothHeight * 0.45;
            double lowerY = 0.03 * visible - mouthHeight * 0.50 + toothHeight * 0.45;
            double teethForward = faceForward + 0.040 * visible;
            for (int index = 0; index < TOOTH_X.length; index++) {
                Location upper = local(bodyCenter, right, forward,
                        TOOTH_X[index] * visible, upperY, teethForward);
                Location lower = local(bodyCenter, right, forward,
                        (TOOTH_X[index] + 0.05) * visible, lowerY, teethForward);
                placeBlock(upperTeeth.get(index), upper, toothWidth, toothHeight, toothDepth);
                placeBlock(lowerTeeth.get(index), lower, toothWidth, toothHeight, toothDepth);
            }
        }

        private void placeBlock(
                BlockDisplay display,
                Location location,
                float scaleX,
                float scaleY,
                float scaleZ
        ) {
            display.teleport(location);
            display.setRotation(yaw, 0.0f);
            setBlockTransform(display, scaleX, scaleY, scaleZ, 0.0f);
        }

        private void drawFace(World world, float mouthOpen, boolean hungry) {
            if (hungry) {
                for (int point = 0; point < 4; point++) {
                    double angle = point * Math.PI / 2.0 + System.nanoTime() * 0.000000004;
                    dust(world, center.clone().add(Math.cos(angle) * 0.72,
                            0.45, Math.sin(angle) * 0.72), HUNGER);
                }
            }
        }

        private void drawTongue(World world, Location target) {
            Location from = mouth().clone().add(forward.clone().multiply(0.10));
            dustLine(world, from, target, TONGUE, 0.13);
        }

        private Location mouth() {
            return local(center, right, forward, 0.0, 0.03, 0.64);
        }
    }
}
