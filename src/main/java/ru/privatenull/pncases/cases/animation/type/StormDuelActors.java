package ru.privatenull.pncases.cases.animation.type;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;
import ru.privatenull.pncases.cases.animation.core.AnimationSession;

import static ru.privatenull.pncases.cases.animation.type.StormDuelEffects.*;

final class StormDuelActors {

    static ArmorStand warrior(AnimationSession run, World world, Location location, boolean left) {
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
        stand.setHeadPose(new EulerAngle(0.0, left ? -0.08 : 0.08, 0.0));
        return run.track(stand);
    }

    static void equip(ArmorStand stand, boolean left) {
        EntityEquipment equipment = stand.getEquipment();
        if (equipment == null) return;
        equipment.setHelmet(new ItemStack(left ? Material.SKELETON_SKULL : Material.WITHER_SKELETON_SKULL));
        equipment.setChestplate(new ItemStack(left ? Material.DIAMOND_CHESTPLATE : Material.NETHERITE_CHESTPLATE));
        equipment.setLeggings(new ItemStack(left ? Material.IRON_LEGGINGS : Material.NETHERITE_LEGGINGS));
        equipment.setBoots(new ItemStack(left ? Material.DIAMOND_BOOTS : Material.NETHERITE_BOOTS));
        equipment.setItemInMainHand(new ItemStack(left ? Material.DIAMOND_SWORD : Material.NETHERITE_AXE));
        equipment.setItemInOffHand(new ItemStack(left ? Material.SHIELD : Material.IRON_SWORD));
    }

    static void clearEquipment(ArmorStand stand) {
        EntityEquipment equipment = stand.getEquipment();
        if (equipment != null) equipment.clear();
    }

    static void clearHands(ArmorStand stand) {
        EntityEquipment equipment = stand.getEquipment();
        if (equipment == null) return;
        equipment.setItemInMainHand(null);
        equipment.setItemInOffHand(null);
    }

    static void placeWarrior(ArmorStand stand, Location location, Location opponent) {
        if (stand == null || stand.isDead()) return;
        Location placed = location.clone();
        placed.setDirection(horizontal(opponent.toVector().subtract(location.toVector())));
        stand.teleport(placed);
        stand.setRotation(placed.getYaw(), 0.0f);
    }

    static void readyPose(ArmorStand stand, boolean left, double sway) {
        stand.setBodyPose(new EulerAngle(0.0, 0.0, left ? sway : -sway));
        stand.setRightArmPose(degrees(-72.0, left ? 12.0 : -12.0, left ? -25.0 : 25.0));
        stand.setLeftArmPose(degrees(-18.0, 0.0, left ? 18.0 : -18.0));
    }

    static void walkingPose(ArmorStand stand, boolean left, double progress) {
        double stride = Math.sin(progress * Math.PI * 6.0) * 24.0;
        stand.setRightLegPose(degrees(stride, 0.0, 0.0));
        stand.setLeftLegPose(degrees(-stride, 0.0, 0.0));
        stand.setRightArmPose(degrees(-68.0 - stride * 0.18, 0.0, left ? -24.0 : 24.0));
        stand.setLeftArmPose(degrees(stride * 0.20 - 12.0, 0.0, left ? 15.0 : -15.0));
    }

    static void attackPose(ArmorStand stand, boolean left, double progress) {
        double swing = ease(progress);
        double direction = left ? -1.0 : 1.0;
        stand.setBodyPose(degrees(-6.0 * swing, direction * 18.0 * swing, direction * 8.0 * swing));
        stand.setHeadPose(degrees(3.0 * swing, -direction * 8.0 * swing, 0.0));
        stand.setRightArmPose(degrees(-132.0 + 112.0 * swing, 0.0,
                direction * (14.0 + 74.0 * swing)));
        stand.setLeftArmPose(degrees(-28.0 - 18.0 * swing, 0.0, -direction * 18.0));
        stand.setRightLegPose(degrees(-22.0 * swing, 0.0, 0.0));
        stand.setLeftLegPose(degrees(16.0 * swing, 0.0, 0.0));
    }

    static void recoilPose(ArmorStand stand, boolean left, double impact, boolean fast) {
        double direction = left ? -1.0 : 1.0;
        double recoil = ease(impact) * (fast ? 1.18 : 1.0);
        stand.setBodyPose(degrees(recoil * 9.0, direction * recoil * 12.0, direction * recoil * 10.0));
        stand.setHeadPose(degrees(-recoil * 8.0, -direction * recoil * 10.0, direction * recoil * 5.0));
        stand.setRightArmPose(degrees(-74.0, 0.0, -direction * (42.0 + recoil * 14.0)));
        stand.setLeftArmPose(degrees(-92.0 + recoil * 16.0, 0.0, direction * (30.0 + recoil * 12.0)));
        stand.setRightLegPose(degrees(recoil * 16.0, 0.0, 0.0));
        stand.setLeftLegPose(degrees(-recoil * 12.0, 0.0, 0.0));
    }

    static void windupPose(ArmorStand stand, boolean left, double progress) {
        stand.setBodyPose(degrees(0.0, (left ? -1.0 : 1.0) * 18.0 * progress, 0.0));
        stand.setHeadPose(degrees(-4.0 * progress, (left ? 1.0 : -1.0) * 8.0 * progress, 0.0));
        stand.setRightArmPose(degrees(-110.0 + 36.0 * progress, 0.0,
                (left ? -1.0 : 1.0) * (18.0 + 34.0 * progress)));
        stand.setLeftArmPose(degrees(-32.0, 0.0, left ? 16.0 : -16.0));
        stand.setRightLegPose(degrees(-10.0 * progress, 0.0, 0.0));
        stand.setLeftLegPose(degrees(10.0 * progress, 0.0, 0.0));
    }

    static void crossedPose(ArmorStand stand, boolean left, double progress) {
        double shake = Math.sin(progress * Math.PI * 9.0) * 4.0;
        stand.setBodyPose(degrees(0.0, left ? -13.0 : 13.0, left ? shake : -shake));
        stand.setHeadPose(degrees(-5.0, left ? 7.0 : -7.0, left ? shake * 0.35 : -shake * 0.35));
        stand.setRightArmPose(degrees(-58.0, 0.0, (left ? -1.0 : 1.0) * (52.0 + shake)));
        stand.setLeftArmPose(degrees(-42.0, 0.0, left ? 18.0 : -18.0));
        stand.setRightLegPose(degrees(-8.0, 0.0, 0.0));
        stand.setLeftLegPose(degrees(8.0, 0.0, 0.0));
    }

    static void knockedPose(ArmorStand stand, boolean left, double progress) {
        double direction = left ? -1.0 : 1.0;
        stand.setBodyPose(degrees(progress * 58.0, direction * progress * 34.0, direction * progress * 48.0));
        stand.setHeadPose(degrees(progress * 24.0, direction * progress * 28.0, direction * progress * 22.0));
        stand.setRightArmPose(degrees(-20.0 + progress * 115.0, 0.0, direction * progress * 74.0));
        stand.setLeftArmPose(degrees(-16.0 + progress * 88.0, 0.0, -direction * progress * 58.0));
    }

    static ItemDisplay item(AnimationSession run, World world, Location location, ItemStack stack, float scale) {
        ItemDisplay display = (ItemDisplay) world.spawnEntity(location, EntityType.ITEM_DISPLAY);
        display.setItemStack(stack == null ? new ItemStack(Material.NETHER_STAR) : stack.clone());
        display.setBillboard(Display.Billboard.CENTER);
        display.setPersistent(false);
        display.setInvulnerable(true);
        setScale(display, scale);
        return run.track(display);
    }

    static void setScale(ItemDisplay display, float scale) {
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
}
