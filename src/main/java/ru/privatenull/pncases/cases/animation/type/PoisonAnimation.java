package ru.privatenull.pncases.cases.animation.type;

import static ru.privatenull.pncases.cases.animation.effect.AnimationEffects.Kind.*;
import static ru.privatenull.pncases.cases.animation.effect.AnimationEffects.particle;
import static ru.privatenull.pncases.cases.animation.effect.AnimationEffects.spawn;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.joml.Vector3f;
import ru.privatenull.pncases.PnCasesPlugin;
import static ru.privatenull.pncases.cases.animation.core.AbstractCaseAnimation.playSound;
import ru.privatenull.pncases.cases.animation.core.AnimationMobSpawner;
import ru.privatenull.pncases.cases.animation.core.InteractiveAnimationTask;
import ru.privatenull.pncases.cases.animation.core.InteractiveCaseAnimation;
import ru.privatenull.pncases.cases.animation.core.AnimationSession;
import ru.privatenull.pncases.cases.animation.effect.RewardLabel;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pncases.hologram.HologramText;
import static ru.privatenull.pncases.cases.animation.core.AnimationSession.removeEntity;

import java.util.*;

public final class PoisonAnimation extends InteractiveCaseAnimation {

    private static final int REQUIRED_HITS = 6;
    private static final int OPENING_TICKS = 42;
    private static final int EXPLODE_CHARGE_TICKS = 28;
    private static final int REVEAL_TICKS = 68;
    private static final int AUTO_BREAK_TICK = 260;
    private static final int HIT_COOLDOWN_TICKS = 2;

    public PoisonAnimation(PnCasesPlugin plugin) {
        super(plugin);
    }

    @Override
    protected void start(AnimationSession run) {
        Player player = run.player();
        CaseDefinition def = run.definition();
        Reward reward = run.reward();
        Location base = run.base();
        World world = base.getWorld();
        if (world == null) {
            run.complete();
            return;
        }

        Random random = new Random();
        Location cubeBase = base.clone().add(0, 0.15, 0);
        applySlimeFacing(cubeBase, def, player);
        float slimeYaw = cubeBase.getYaw();
        float slimePitch = cubeBase.getPitch();
        ItemStack rewardVisual = buildRewardVisual(reward, def);

        Slime cube = AnimationMobSpawner.spawn(world, cubeBase, Slime.class);
        cube.setAI(false);
        cube.setGravity(false);
        cube.setSilent(true);
        cube.setCollidable(false);
        cube.setCanPickupItems(false);
        cube.setRemoveWhenFarAway(false);
        cube.setPersistent(false);
        cube.setSize(1);
        cube.setHealth(Math.min(cube.getMaxHealth(), 1.0));
        cube.setGlowing(true);
        cube.setCustomNameVisible(false);
        run.track(cube);

        ItemDisplay rewardDisplay = (ItemDisplay) world.spawnEntity(base.clone().add(0, 1.1, 0), org.bukkit.entity.EntityType.ITEM_DISPLAY);
        rewardDisplay.setItemStack(rewardVisual);
        rewardDisplay.setBillboard(Display.Billboard.VERTICAL);
        setScale(rewardDisplay, 0f);
        run.track(rewardDisplay);
        RewardLabel rewardLabel = RewardLabel.create(run, base,
                resolveRewardName(reward, rewardVisual));
        Location statusAnchor = cubeBase.clone().add(0.0, 3.15, 0.0);
        HologramText statusText = run.holograms().createTransientText(
                statusAnchor, buildStatusLines(0));
        run.onClose(statusText::remove);

        List<SlimeGlob> globs = new ArrayList<>();

        PoisonState state = new PoisonState();

        UUID ownerId = player.getUniqueId();
        UUID cubeId = cube.getUniqueId();

        Listener listener = new Listener() {
            @EventHandler
            public void onDamage(EntityDamageEvent event) {
                if (!event.getEntity().getUniqueId().equals(cubeId)) return;

                event.setCancelled(true);

                if (!(event instanceof EntityDamageByEntityEvent byEntity)) return;
                if (!(byEntity.getDamager() instanceof Player damager)) return;

                if (!damager.getUniqueId().equals(ownerId)) {
                    damager.sendMessage(plugin.getUi().get("poison-cube-protected"));
                    return;
                }

                if (state.phase != PoisonPhase.BATTLE) return;
                if (state.tick - state.lastHitTick < HIT_COOLDOWN_TICKS) return;

                state.lastHitTick = state.tick;
                state.hits++;
                statusText.update(statusAnchor, buildStatusLines(state.hits));

                Location hitLoc = cube.getLocation().clone().add(0, 0.9, 0);
                spawn(world, particle(SLIME), hitLoc, 16, 0.35, 0.22, 0.35, 0.05);
                spawn(world, particle(SLIME), hitLoc, 10, 0.25, 0.18, 0.25, 0.04);
                spawn(world, particle(WITCH), hitLoc, 8, 0.25, 0.18, 0.25, 0.02);

                playSound(world, cube.getLocation(), Sound.ENTITY_SLIME_HURT, 0.36f, 0.7f + state.hits * 0.08f);
                playSound(world, cube.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.24f, 1.2f);

                if (state.hits >= REQUIRED_HITS) {
                    state.phase = PoisonPhase.CHARGE;
                    state.phaseStartedAt = state.tick;
                    playSound(world, cube.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.28f, 0.65f);
                }
            }
        };
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        run.track(listener);

        startInteractive(run, new InteractiveAnimationTask(run, "poison-main") {
            @Override
            protected boolean update(int tick) {
                state.tick = tick;

                updateGlobs(run, world, base, globs);

                if (state.phase == PoisonPhase.OPENING) {
                    double progress = state.tick / (double) OPENING_TICKS;
                    double eased = easeOutBack(Math.min(1.0, progress));

                    int size;
                    if (progress < 0.25) size = 1;
                    else if (progress < 0.35) size = 2;
                    else if (progress < 0.45) size = 3;
                    else size = 4;

                    cube.setSize(size);

                    double bob = Math.sin(state.tick * 0.22) * 0.05;
                    cube.teleport(oriented(cubeBase.clone().add(0, bob, 0), slimeYaw, slimePitch));
                    spawn(world, particle(SLIME), cube.getLocation().clone().add(0, 0.5, 0), 8, 0.30, 0.15, 0.30, 0.03);
                    spawn(world, particle(SLIME), cube.getLocation().clone().add(0, 0.5, 0), 6, 0.22, 0.10, 0.22, 0.02);

                    spawnGroundRing(world, base.clone().add(0, 0.02, 0), 0.5 + eased * 0.9, state.tick * 0.08);

                    if (state.tick % 8 == 0) {
                        playSound(world, cube.getLocation(), Sound.ENTITY_SLIME_SQUISH, 0.24f, 0.6f + (float) progress * 0.25f);
                    }

                    if (state.tick >= OPENING_TICKS) {
                        state.phase = PoisonPhase.BATTLE;
                        cube.setSize(4);
                        playSound(world, cube.getLocation(), Sound.ENTITY_SLIME_JUMP, 0.28f, 0.8f);
                    }
                    return false;
                }

                if (state.phase == PoisonPhase.BATTLE) {
                    cube.setSize(4);

                    double bob = Math.sin(state.tick * 0.18) * 0.08;
                    cube.teleport(oriented(cubeBase.clone().add(0, bob, 0), slimeYaw, slimePitch));
                    spawn(world, particle(SLIME), cube.getLocation().clone().add(0, 0.75, 0), 5, 0.30, 0.20, 0.30, 0.02);
                    if (state.tick % 4 == 0) {
                        spawn(world, particle(WITCH), cube.getLocation().clone().add(0, 0.75, 0), 4, 0.28, 0.18, 0.28, 0.01);
                    }

                    int spitInterval = Math.max(10, 24 - state.hits * 2);
                    if (state.tick % spitInterval == 0) {
                        spitSlime(run, world, cube.getLocation().clone().add(0, 0.95, 0), random, globs);
                        playSound(world, cube.getLocation(), Sound.ENTITY_SLIME_ATTACK, 0.28f, 0.85f);
                    }

                    if (state.tick >= AUTO_BREAK_TICK) {
                        state.phase = PoisonPhase.CHARGE;
                        state.phaseStartedAt = state.tick;
                    }
                    return false;
                }

                if (state.phase == PoisonPhase.CHARGE) {
                    statusText.remove();
                    int dt = state.tick - state.phaseStartedAt;

                    if (dt < 6) cube.setSize(5);
                    else if (dt < 12) cube.setSize(6);
                    else cube.setSize(6);

                    double shakeX = (random.nextDouble() - 0.5) * 0.08;
                    double shakeZ = (random.nextDouble() - 0.5) * 0.08;
                    cube.teleport(oriented(cubeBase.clone().add(shakeX, 0.05, shakeZ), slimeYaw, slimePitch));

                    Location loc = cube.getLocation().clone().add(0, 1.0, 0);
                    spawn(world, particle(SLIME), loc, 20, 0.45, 0.25, 0.45, 0.05);
                    spawn(world, particle(SLIME), loc, 14, 0.35, 0.18, 0.35, 0.04);
                    spawn(world, particle(WITCH), loc, 12, 0.30, 0.22, 0.30, 0.02);
                    spawn(world, particle(SMOKE), loc, 6, 0.22, 0.12, 0.22, 0.01);

                    if (dt % 4 == 0) {
                        playSound(world, cube.getLocation(), Sound.ENTITY_SLIME_SQUISH, 0.30f, 0.45f + dt * 0.03f);
                    }

                    if (dt >= EXPLODE_CHARGE_TICKS) {
                        Location explodeLoc = cube.getLocation().clone().add(0, 1.0, 0);

                        for (SlimeGlob glob : globs) {
                            safeRemove(glob.display);
                            run.untrack(glob.display);
                        }
                        globs.clear();

                        spawn(world, particle(EXPLOSION), explodeLoc, 4, 0.35, 0.25, 0.35, 0.0);
                        spawn(world, particle(SLIME), explodeLoc, 42, 0.60, 0.35, 0.60, 0.09);
                        spawn(world, particle(SLIME), explodeLoc, 32, 0.50, 0.28, 0.50, 0.06);
                        spawn(world, particle(WITCH), explodeLoc, 24, 0.40, 0.24, 0.40, 0.03);
                        spawn(world, particle(SMOKE), explodeLoc, 18, 0.28, 0.16, 0.28, 0.03);

                        playSound(world, cube.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.28f, 1.2f);
                        playSound(world, cube.getLocation(), Sound.BLOCK_SLIME_BLOCK_BREAK, 0.32f, 0.8f);
                        playSound(world, cube.getLocation(), Sound.ENTITY_SPLASH_POTION_BREAK, 0.32f, 0.7f);

                        safeRemove(cube);
                        run.untrack(cube);

                        state.phaseStartedAt = state.tick;
                        state.phase = PoisonPhase.REVEAL;
                        run.protectReward(base.clone().add(0.0, 1.75, 0.0));
                    }
                    return false;
                }

                if (state.phase == PoisonPhase.REVEAL) {
                    int dt = state.tick - state.phaseStartedAt;
                    double progress = Math.min(1.0, dt / (double) REVEAL_TICKS);
                    double eased = easeOutBack(progress);
                    double bob = Math.sin(dt * 0.22) * 0.06 * (1.0 - progress * 0.35);

                    Location rewardLoc = base.clone().add(0, 1.1 + eased * 1.75 + bob, 0);
                    rewardDisplay.teleport(rewardLoc);
                    if (rewardLabel != null) rewardLabel.showAbove(rewardLoc, 0.88);

                    float scale = (float) (0.2 + eased * 0.95);
                    setScale(rewardDisplay, scale);

                    state.rewardYaw += 4.8f;
                    if (state.rewardYaw >= 360f) state.rewardYaw -= 360f;
                    rewardDisplay.setRotation(state.rewardYaw, 0f);

                    if (dt % 3 == 0) {
                        spawnRewardAura(world, rewardLoc, dt);
                    }

                    if (dt % 8 == 0) {
                        playSound(world, base, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.24f, 0.8f + (float) progress * 0.5f);
                    }

                    if (dt >= REVEAL_TICKS) {
                        spawn(world, particle(SLIME), rewardLoc, 5, 0.25, 0.16, 0.25, 0.02);
                        spawn(world, particle(SLIME), rewardLoc, 4, 0.22, 0.12, 0.22, 0.02);
                        playSound(world, base, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.32f, 1.0f);
                        playSound(world, base, Sound.ENTITY_PLAYER_LEVELUP, 0.28f, 1.25f);
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private void spitSlime(AnimationSession run, World world, Location from,
                           Random random, List<SlimeGlob> globs) {
        int count = 3 + random.nextInt(2);

        spawn(world, particle(SLIME), from, 10, 0.16, 0.12, 0.16, 0.03);
        spawn(world, particle(SLIME), from, 8, 0.12, 0.10, 0.12, 0.02);

        for (int i = 0; i < count; i++) {
            ItemDisplay glob = (ItemDisplay) world.spawnEntity(from, org.bukkit.entity.EntityType.ITEM_DISPLAY);
            glob.setItemStack(new ItemStack(Material.SLIME_BALL));
            glob.setBillboard(Display.Billboard.VERTICAL);
            setScale(glob, 0.28f);
            run.track(glob);

            double angle = random.nextDouble() * Math.PI * 2D;
            double speed = 0.12 + random.nextDouble() * 0.10;

            Vector velocity = new Vector(
                    Math.cos(angle) * speed,
                    0.18 + random.nextDouble() * 0.08,
                    Math.sin(angle) * speed
            );

            globs.add(new SlimeGlob(glob, velocity, 14 + random.nextInt(8)));
        }
    }

    private void updateGlobs(AnimationSession run, World world, Location base, List<SlimeGlob> globs) {
        Iterator<SlimeGlob> iterator = globs.iterator();

        while (iterator.hasNext()) {
            SlimeGlob glob = iterator.next();
            glob.age++;

            Location current = glob.display.getLocation().clone().add(glob.velocity);
            glob.velocity.multiply(0.96);
            glob.velocity.setY(glob.velocity.getY() - 0.018);

            glob.display.teleport(current);

            float scale = (float) (0.20 + Math.sin(glob.age * 0.30) * 0.03);
            setScale(glob.display, scale);

            spawn(world, particle(SLIME), current, 1, 0.01, 0.01, 0.01, 0.0);

            if (glob.age >= glob.maxAge || current.getY() <= base.getY() + 0.18) {
                spawn(world, particle(SLIME), current, 6, 0.10, 0.06, 0.10, 0.02);
                spawn(world, particle(SLIME), current, 5, 0.08, 0.05, 0.08, 0.02);
                playSound(world, current, Sound.BLOCK_SLIME_BLOCK_BREAK, 0.16f, 1.5f);

                safeRemove(glob.display);
                run.untrack(glob.display);
                iterator.remove();
            }
        }
    }

    private static void spawnGroundRing(World world, Location center, double radius, double rotation) {
        for (int i = 0; i < 18; i++) {
            double angle = rotation + (Math.PI * 2D * i) / 18D;
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            spawn(world, particle(SLIME), x, center.getY(), z, 1, 0, 0, 0, 0.0);
        }
    }

    private static void spawnRewardAura(World world, Location rewardLoc, int tick) {
        double radius = 0.42 + Math.sin(tick * 0.16) * 0.05;

        for (int i = 0; i < 6; i++) {
            double angle = (Math.PI * 2D * i) / 6D + tick * 0.10;
            double x = rewardLoc.getX() + Math.cos(angle) * radius;
            double z = rewardLoc.getZ() + Math.sin(angle) * radius;
            double y = rewardLoc.getY() + Math.sin(angle * 2.0 + tick * 0.12) * 0.10;

            spawn(world, particle(SLIME), x, y, z, 1, 0, 0, 0, 0.0);

            if (i % 3 == 0) {
                spawn(world, particle(WITCH), x, y, z, 1, 0, 0, 0, 0.0);
            }
        }
    }

    private static List<String> buildStatusLines(int hits) {
        int left = Math.max(0, REQUIRED_HITS - hits);
        return List.of(
                "§x§9§6§F§B§8§A«Ядовитый куб»",
                " §8- §fУдаров: §a" + hits + "§7/§a" + REQUIRED_HITS,
                " §8- §fОсталось сломать: §x§F§B§C§A§0§8" + left,
                " §8- §fБей куб, пока он плюётся слизью"
        );
    }

    private static double easeOutBack(double x) {
        double c1 = 1.70158;
        double c3 = c1 + 1.0;
        return 1.0 + c3 * Math.pow(x - 1.0, 3.0) + c1 * Math.pow(x - 1.0, 2.0);
    }

    private static void setScale(ItemDisplay display, float scale) {
        org.bukkit.util.Transformation tf = display.getTransformation();
        display.setTransformation(new org.bukkit.util.Transformation(
                tf.getTranslation(),
                tf.getLeftRotation(),
                new Vector3f(scale, scale, scale),
                tf.getRightRotation()
        ));
    }

    private void applySlimeFacing(Location loc, CaseDefinition def, Player player) {
        ConfigurationSection caseSection = plugin.getCaseManager() == null ? null : plugin.getCaseManager().getCaseSection(def.name());
        ConfigurationSection animation = caseSection == null ? null : caseSection.getConfigurationSection("animation");
        ConfigurationSection poison = animation == null ? null : animation.getConfigurationSection("poison");
        if (poison == null) {
            poison = plugin.getConfig().getConfigurationSection("cases." + def.name() + ".animation.poison");
        }
        if (poison == null) {
            loc.setYaw(0f);
            loc.setPitch(0f);
            return;
        }

        loc.setPitch((float) poison.getDouble("slime-pitch", 0.0));

        if (poison.contains("slime-yaw")) {
            loc.setYaw((float) poison.getDouble("slime-yaw", 0.0));
            return;
        }

        String facing = poison.getString("slime-facing", "SOUTH");
        if (facing == null) facing = "SOUTH";

        switch (facing.trim().toUpperCase(Locale.ROOT)) {
            case "NORTH" -> loc.setYaw(180f);
            case "EAST" -> loc.setYaw(-90f);
            case "WEST" -> loc.setYaw(90f);
            case "PLAYER" -> faceLocation(loc, player.getLocation());
            case "SOUTH" -> loc.setYaw(0f);
            default -> {
                try {
                    loc.setYaw((float) Double.parseDouble(facing));
                } catch (NumberFormatException ignored) {
                    loc.setYaw(0f);
                }
            }
        }
    }

    private static void faceLocation(Location loc, Location target) {
        Vector direction = target.toVector().subtract(loc.toVector());
        if (direction.lengthSquared() < 0.0001) {
            loc.setYaw(0f);
            return;
        }
        loc.setDirection(direction);
    }

    private static Location oriented(Location loc, float yaw, float pitch) {
        loc.setYaw(yaw);
        loc.setPitch(pitch);
        return loc;
    }

    private ItemStack buildRewardVisual(Reward reward, CaseDefinition def) {
        return resolveRewardVisual(reward, def);
    }

    private static void safeRemove(Entity e) {
        removeEntity(e);
    }

    private enum PoisonPhase {
        OPENING,
        BATTLE,
        CHARGE,
        REVEAL
    }

    private static final class PoisonState {
        private PoisonPhase phase = PoisonPhase.OPENING;
        private int tick;
        private int hits;
        private int lastHitTick = -100;
        private int phaseStartedAt = -1;
        private float rewardYaw;
    }

    private static final class SlimeGlob {
        private final ItemDisplay display;
        private final Vector velocity;
        private final int maxAge;
        private int age;

        private SlimeGlob(ItemDisplay display, Vector velocity, int maxAge) {
            this.display = display;
            this.velocity = velocity;
            this.maxAge = maxAge;
            this.age = 0;
        }
    }
}
