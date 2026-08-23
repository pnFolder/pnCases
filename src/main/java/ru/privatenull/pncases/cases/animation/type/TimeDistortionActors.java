package ru.privatenull.pncases.cases.animation.type;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.privatenull.pncases.cases.animation.core.AnimationSession;
import ru.privatenull.pncases.cases.animation.effect.DisplayAnimator;
import ru.privatenull.pncases.cases.model.AnimationType;
import ru.privatenull.pncases.gui.GuiDesign;

/** Actors, props and poses for the Timekeeper cut-scene. */
final class TimeDistortionActors {

    static final int CASE_SHARDS = 8;

    private TimeDistortionActors() {
    }

    static ArmorStand timekeeper(AnimationSession run, World world, Location location, boolean future) {
        ArmorStand stand = (ArmorStand) world.spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setArms(true);
        stand.setBasePlate(false);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setCollidable(false);
        stand.setCanPickupItems(false);
        stand.setPersistent(false);
        equip(stand, future);
        idlePose(stand, future, 0.0);
        return run.track(stand);
    }

    static void place(ArmorStand stand, Location location, Location target) {
        if (stand == null || stand.isDead()) return;
        Location placed = location.clone();
        Vector direction = target.toVector().subtract(location.toVector()).setY(0.0);
        if (direction.lengthSquared() > 0.001) placed.setDirection(direction.normalize());
        stand.teleport(placed);
        stand.setRotation(placed.getYaw(), 0.0f);
    }

    static void idlePose(ArmorStand stand, boolean future, double sway) {
        double side = future ? 1.0 : -1.0;
        stand.setBodyPose(degrees(0.0, side * 7.0, sway * side));
        stand.setHeadPose(degrees(-4.0, -side * 5.0, -sway * 0.4));
        stand.setRightArmPose(degrees(-72.0, 0.0, side * 18.0));
        stand.setLeftArmPose(degrees(-18.0, 0.0, -side * 12.0));
        stand.setRightLegPose(degrees(3.0, 0.0, 0.0));
        stand.setLeftLegPose(degrees(-3.0, 0.0, 0.0));
    }

    static void rewindPose(ArmorStand stand, double progress, boolean reverse) {
        double motion = Math.sin(progress * Math.PI * 4.0);
        double direction = reverse ? -1.0 : 1.0;
        stand.setBodyPose(degrees(-7.0, direction * 13.0, motion * 4.0));
        stand.setHeadPose(degrees(-12.0, -direction * 9.0, -motion * 3.0));
        stand.setRightArmPose(degrees(-105.0, direction * 8.0,
                direction * (-22.0 - progress * 88.0)));
        stand.setLeftArmPose(degrees(-48.0 + motion * 8.0, 0.0, -direction * 24.0));
    }

    static void paradoxPose(ArmorStand stand, boolean future, double progress) {
        double side = future ? 1.0 : -1.0;
        double pressure = 0.5 + Math.sin(progress * Math.PI * 7.0) * 0.5;
        stand.setBodyPose(degrees(-8.0 - pressure * 5.0, side * 19.0, side * pressure * 7.0));
        stand.setHeadPose(degrees(-10.0, -side * 12.0, -side * pressure * 4.0));
        stand.setRightArmPose(degrees(-88.0 - pressure * 16.0, 0.0, -side * 58.0));
        stand.setLeftArmPose(degrees(-68.0 + pressure * 10.0, 0.0, side * 36.0));
        stand.setRightLegPose(degrees(side * pressure * 9.0, 0.0, 0.0));
        stand.setLeftLegPose(degrees(-side * pressure * 9.0, 0.0, 0.0));
    }

    static void mergePose(ArmorStand stand, boolean future, double progress) {
        double side = future ? 1.0 : -1.0;
        stand.setBodyPose(degrees(progress * 10.0, -side * progress * 28.0, side * progress * 12.0));
        stand.setHeadPose(degrees(-progress * 16.0, side * progress * 20.0, 0.0));
        stand.setRightArmPose(degrees(-92.0 + progress * 70.0, 0.0, -side * (52.0 - progress * 30.0)));
        stand.setLeftArmPose(degrees(-52.0 + progress * 42.0, 0.0, side * 30.0));
    }

    static List<BlockDisplay> shards(AnimationSession run, World world, Location center, Material material) {
        List<BlockDisplay> result = new ArrayList<>(CASE_SHARDS);
        for (int i = 0; i < CASE_SHARDS; i++) {
            BlockDisplay display = (BlockDisplay) world.spawnEntity(
                    center.clone().subtract(0.0, 5.0, 0.0), EntityType.BLOCK_DISPLAY);
            display.setBlock(material.createBlockData());
            blockTransform(display, 0.01f, 0.0);
            result.add(run.track(display));
        }
        return result;
    }

    static ItemDisplay item(AnimationSession run, World world, Location location,
                            ItemStack item, float scale) {
        ItemDisplay display = (ItemDisplay) world.spawnEntity(location, EntityType.ITEM_DISPLAY);
        display.setItemStack(item.clone());
        display.setBillboard(Display.Billboard.CENTER);
        itemTransform(display, scale, 0.0);
        return run.track(display);
    }

    static void move(ItemDisplay display, Location location, float scale, double rotation) {
        DisplayAnimator.move(display, location);
        itemTransform(display, scale, rotation);
    }

    static void move(BlockDisplay display, Location location, float scale, double rotation) {
        DisplayAnimator.move(display, location);
        blockTransform(display, scale, rotation);
    }

    static Location scatteredShard(Location center, int index) {
        return center.clone().add(direction(index).multiply(2.35 + (index % 3) * 0.32))
                .add(0.0, 0.28 + (index % 4) * 0.48, 0.0);
    }

    static Location shardTarget(Location center, int index) {
        return center.clone().add((index & 1) == 0 ? -0.23 : 0.23,
                (index & 2) == 0 ? -0.23 : 0.23, (index & 4) == 0 ? -0.23 : 0.23);
    }

    static Vector direction(int index) {
        double angle = index * Math.PI * 2.0 / CASE_SHARDS + 0.31;
        return new Vector(Math.cos(angle), 0.15 + (index % 2) * 0.14, Math.sin(angle)).normalize();
    }

    private static void equip(ArmorStand stand, boolean future) {
        EntityEquipment equipment = stand.getEquipment();
        if (equipment == null) return;
        equipment.setHelmet(GuiDesign.animation(AnimationType.TIME_DISTORTION));
        equipment.setChestplate(leather(Material.LEATHER_CHESTPLATE,
                future ? Color.fromRGB(44, 126, 160) : Color.fromRGB(111, 74, 31)));
        equipment.setLeggings(leather(Material.LEATHER_LEGGINGS,
                future ? Color.fromRGB(24, 66, 91) : Color.fromRGB(52, 38, 26)));
        equipment.setBoots(new ItemStack(future ? Material.DIAMOND_BOOTS : Material.GOLDEN_BOOTS));
        equipment.setItemInMainHand(new ItemStack(future ? Material.RECOVERY_COMPASS : Material.CLOCK));
        equipment.setItemInOffHand(new ItemStack(future ? Material.ECHO_SHARD : Material.AMETHYST_SHARD));
    }

    private static ItemStack leather(Material material, Color color) {
        ItemStack item = new ItemStack(material);
        if (item.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(color);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static EulerAngle degrees(double x, double y, double z) {
        return new EulerAngle(Math.toRadians(x), Math.toRadians(y), Math.toRadians(z));
    }

    private static void itemTransform(ItemDisplay display, float scale, double rotation) {
        display.setTransformation(new Transformation(new Vector3f(),
                new Quaternionf().rotateY((float) rotation), new Vector3f(scale, scale, scale), new Quaternionf()));
    }

    private static void blockTransform(BlockDisplay display, float scale, double rotation) {
        display.setTransformation(new Transformation(
                new Vector3f(-scale / 2.0f, -scale / 2.0f, -scale / 2.0f),
                new Quaternionf().rotateXYZ((float) rotation * 0.4f, (float) rotation, (float) rotation * 0.25f),
                new Vector3f(scale, scale, scale), new Quaternionf()));
    }
}
