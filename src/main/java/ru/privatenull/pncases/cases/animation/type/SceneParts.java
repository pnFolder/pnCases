package ru.privatenull.pncases.cases.animation.type;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.privatenull.pncases.cases.animation.core.AbstractCaseAnimation;
import ru.privatenull.pncases.cases.animation.core.AnimationSession;
import ru.privatenull.pncases.cases.animation.effect.AnimationEffects;
import ru.privatenull.pncases.cases.model.Reward;

/** Building blocks shared by the seasonal scenes: display spawning, posing, the prize and its celebration. */
final class SceneParts {

    static final Transformation HIDDEN = new Transformation(
            new Vector3f(), new Quaternionf(), new Vector3f(0.0f), new Quaternionf());

    private SceneParts() {
    }

    /**
     * How much higher (positive) or lower (negative) the real ground at {@code dx}, {@code dz} from the scene
     * centre is than the bottom of the case block, or NaN when nothing solid lies within four blocks below, so a
     * scene built on a small platform does not hang things in the air around it.
     */
    static double groundShift(org.bukkit.World world, Location ground, double dx, double dz) {
        int x = (int) Math.floor(ground.getX() + dx);
        int z = (int) Math.floor(ground.getZ() + dz);
        if (world == null || !world.isChunkLoaded(x >> 4, z >> 4)) return 0.0;
        int baseY = ground.getBlockY();
        if (x == ground.getBlockX() && z == ground.getBlockZ()) return 0.0;
        for (int y = baseY; y >= baseY - 4; y--) {
            Material type = world.getBlockAt(x, y, z).getType();
            if (!type.isSolid() || type.name().endsWith("_LEAVES")) continue;
            return (y + 1) - baseY;
        }
        return Double.NaN;
    }

    static double easeOutBack(double t) {
        double x = Math.max(0.0, Math.min(1.0, t));
        double c1 = 1.70158;
        double c3 = c1 + 1.0;
        return 1.0 + c3 * Math.pow(x - 1.0, 3.0) + c1 * Math.pow(x - 1.0, 2.0);
    }

    static void prepare(Display display) {
        display.setPersistent(false);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(2);
        display.setShadowRadius(0.0f);
        display.setDisplayWidth(12.0f);
        display.setDisplayHeight(12.0f);
    }

    /**
     * Spawns an invisible block display. The consumer configures it before it enters the world, so the client gets
     * one complete spawn instead of a spawn followed by a burst of metadata updates.
     */
    static BlockDisplay spawnBlock(AnimationSession run, Location anchor, BlockData data, boolean lit, Color glow) {
        BlockDisplay display = run.world().spawn(anchor, BlockDisplay.class, spawned -> {
            spawned.setBlock(data);
            prepare(spawned);
            spawned.setBrightness(new Display.Brightness(lit ? 15 : 7, lit ? 15 : 7));
            if (glow != null) {
                spawned.setGlowColorOverride(glow);
                spawned.setGlowing(true);
            }
            spawned.setTransformation(HIDDEN);
        });
        return run.track(display);
    }

    static BlockDisplay spawnBlock(AnimationSession run, Location anchor, Material material, boolean lit,
                                   Color glow) {
        return spawnBlock(run, anchor, material.createBlockData(), lit, glow);
    }

    /** Puts a block of the given size with its centre at the offset from the entity, turned about that centre. */
    static void place(BlockDisplay display, double cx, double cy, double cz,
                      double sx, double sy, double sz, Quaternionf rotation) {
        Vector3f translation = new Vector3f((float) (-sx / 2.0), (float) (-sy / 2.0), (float) (-sz / 2.0));
        rotation.transform(translation);
        translation.add((float) cx, (float) cy, (float) cz);
        display.setInterpolationDelay(0);
        display.setTransformation(new Transformation(translation, rotation,
                new Vector3f((float) sx, (float) sy, (float) sz), new Quaternionf()));
    }

    static ItemDisplay spawnPrize(AnimationSession run, Location at, ItemStack item) {
        ItemDisplay display = run.world().spawn(at, ItemDisplay.class, spawned -> {
            prepare(spawned);
            spawned.setItemStack(item.clone());
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            spawned.setTransformation(HIDDEN);
        });
        return run.track(display);
    }

    /** An item that always turns its face to the viewer, such as a fruit hanging in a tree. */
    static ItemDisplay spawnHangingItem(AnimationSession run, Location at, ItemStack item) {
        ItemDisplay display = run.world().spawn(at, ItemDisplay.class, spawned -> {
            prepare(spawned);
            spawned.setItemStack(item.clone());
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setTransformation(HIDDEN);
        });
        return run.track(display);
    }

    static void placePrize(ItemDisplay prize, Location from, Location at, float scale, float spin) {
        if (prize == null) return;
        prize.setInterpolationDelay(0);
        prize.setTransformation(new Transformation(
                new Vector3f((float) (at.getX() - from.getX()), (float) (at.getY() - from.getY()),
                        (float) (at.getZ() - from.getZ())),
                new Quaternionf().rotateY(spin), new Vector3f(scale), new Quaternionf()));
    }

    /**
     * The side of the scene the player is on, as a unit vector that turns smoothly while the player walks round,
     * so a prize shown on that side never ends up behind the scene.
     */
    static Vector followSide(Vector side, Location center, Player player) {
        Vector to = player.getLocation().toVector().subtract(center.toVector()).setY(0.0);
        if (to.lengthSquared() <= 0.09) return side;
        to.normalize();
        Vector blended = side.clone().multiply(0.88).add(to.multiply(0.12));
        return blended.lengthSquared() > 1.0e-4 ? blended.normalize() : side;
    }

    /** The size of the celebration for a reward; a middle one when rarities are switched off. */
    static int rarityTier(Reward reward, boolean rarityShown) {
        return rarityShown ? reward.rarity().ordinal() : 2;
    }

    /** The colour of a reward's rarity; a neutral ice blue when rarities are switched off. */
    static Color rarityColor(Reward reward, boolean rarityShown) {
        if (!rarityShown) return Color.fromRGB(150, 215, 255);
        return switch (reward.rarity()) {
            case COMMON -> Color.fromRGB(205, 205, 205);
            case UNCOMMON -> Color.fromRGB(90, 230, 110);
            case RARE -> Color.fromRGB(80, 140, 255);
            case EPIC -> Color.fromRGB(180, 90, 255);
            case LEGENDARY -> Color.fromRGB(255, 190, 40);
            case MYTHIC -> Color.fromRGB(255, 90, 210);
        };
    }

    /** The bigger the rarity tier, the bigger the show. */
    static void celebrate(AnimationSession run, int tier, Color color, Location at, Particle spark, Particle soft) {
        Particle dust = AnimationEffects.particle(AnimationEffects.Kind.DUST);
        Particle.DustOptions options = new Particle.DustOptions(color, 1.4f);
        int sparks = 14 + tier * 10;
        AnimationEffects.plain(run.world(), spark, at, sparks, 0.35, 0.35, 0.35, 0.06 + tier * 0.015);
        AnimationEffects.plain(run.world(), soft, at, sparks, 0.6, 0.5, 0.6, 0.04);
        int points = 16 + tier * 6;
        for (int i = 0; i < points; i++) {
            double a = i * Math.PI * 2.0 / points;
            double r = 0.5 + tier * 0.12;
            AnimationEffects.dust(run.world(), dust, at.clone().add(Math.cos(a) * r, 0.0, Math.sin(a) * r),
                    1, 0.0, 0.0, 0.0, 0.0, options);
        }
        if (tier >= 3) {
            Particle totem = AnimationEffects.particle(AnimationEffects.Kind.TOTEM);
            AnimationEffects.plain(run.world(), totem, at, 20 + tier * 8, 0.4, 0.5, 0.4, 0.3);
        }
        if (tier >= 4) {
            for (int i = 0; i < 40; i++) {
                double a = i * 0.35;
                AnimationEffects.dust(run.world(), dust, at.clone().add(Math.cos(a) * 0.7, i * 0.07,
                        Math.sin(a) * 0.7), 1, 0.0, 0.0, 0.0, 0.0, options);
            }
        }
        Sound sound = tier >= 4 ? Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST
                : tier >= 2 ? Sound.ENTITY_FIREWORK_ROCKET_BLAST : Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        AbstractCaseAnimation.playSound(run.world(), at, sound, 0.22f, 1.0f + tier * 0.05f);
        if (tier >= 3) AbstractCaseAnimation.playSound(run.world(), at, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.2f, 1.2f);
        if (tier >= 5) AbstractCaseAnimation.playSound(run.world(), at, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.22f, 0.9f);
    }
}
