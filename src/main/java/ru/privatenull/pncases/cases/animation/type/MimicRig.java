package ru.privatenull.pncases.cases.animation.type;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.privatenull.pncases.cases.animation.core.AnimationSession;
import ru.privatenull.pncases.cases.animation.effect.AnimationParticles;

import java.util.ArrayList;
import java.util.List;

import static ru.privatenull.pncases.cases.animation.effect.AnimationEffects.Kind.DUST;
import static ru.privatenull.pncases.cases.animation.effect.AnimationEffects.particle;

final class MimicRig {

    private static final double[] TOOTH_X = {-0.30, -0.10, 0.10, 0.30};
    private static final Particle.DustOptions TONGUE =
            new Particle.DustOptions(Color.fromRGB(220, 48, 82), 0.92f);
    private static final Particle.DustOptions HUNGER =
            new Particle.DustOptions(Color.fromRGB(255, 174, 55), 0.74f);

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

    static MimicRig create(
            AnimationSession run,
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
            AnimationSession run,
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
            AnimationSession run,
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

    void update(
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

    void drawFace(World world, float mouthOpen, boolean hungry) {
        if (hungry) {
            for (int point = 0; point < 4; point++) {
                double angle = point * Math.PI / 2.0 + System.nanoTime() * 0.000000004;
                dust(world, center.clone().add(Math.cos(angle) * 0.72,
                        0.45, Math.sin(angle) * 0.72), HUNGER);
            }
        }
    }

    void drawTongue(World world, Location target) {
        Location from = mouth().clone().add(forward.clone().multiply(0.10));
        dustLine(world, from, target, TONGUE, 0.13);
    }

    Location mouth() {
        return local(center, right, forward, 0.0, 0.03, 0.64);
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

    private static void dust(World world, Location location, Particle.DustOptions color) {
        AnimationParticles.dust(world, particle(DUST), location, 1,
                0.0, 0.0, 0.0, 0.0, color);
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
}
