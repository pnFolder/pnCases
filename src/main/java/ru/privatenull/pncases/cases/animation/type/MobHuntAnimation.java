package ru.privatenull.pncases.cases.animation.type;

import static ru.privatenull.pncases.cases.animation.effect.AnimationEffects.Kind.*;
import static ru.privatenull.pncases.cases.animation.effect.AnimationEffects.particle;
import static ru.privatenull.pncases.cases.animation.effect.AnimationEffects.spawn;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import ru.privatenull.pncases.PnCasesPlugin;
import static ru.privatenull.pncases.cases.animation.core.AbstractCaseAnimation.playSound;
import ru.privatenull.pncases.cases.animation.core.AnimationMobSpawner;
import ru.privatenull.pncases.cases.animation.core.AnimationSession;
import ru.privatenull.pncases.cases.animation.core.InteractiveAnimationTask;
import ru.privatenull.pncases.cases.animation.core.InteractiveCaseAnimation;
import ru.privatenull.pncases.cases.animation.reveal.LegacyRewardReveal;
import ru.privatenull.pncases.cases.animation.reveal.RewardReveal;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pncases.support.compat.BukkitCompat.ParticleCompat;
import ru.privatenull.pncases.support.compat.ServerCapabilities;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** A choice animation: stationary mobs are options and one hit selects the reward source. */
public final class MobHuntAnimation extends InteractiveCaseAnimation implements Listener {
    private static final int MOB_COUNT = 6;
    private static final int TIMEOUT_TICKS = 20 * 90;
    private static final double MAX_DISTANCE_SQUARED = 12.0 * 12.0;

    private final Map<UUID, Session> sessionsByPlayer = new HashMap<>();
    private final Map<UUID, Session> sessionsByMob = new HashMap<>();
    private final Map<UUID, Session> sessionsByProjectile = new HashMap<>();

    public MobHuntAnimation(PnCasesPlugin plugin) {
        super(plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    protected void start(AnimationSession run) {
        Player player = run.player();
        CaseDefinition definition = run.definition();
        Reward reward = run.reward();
        Location base = run.base();
        World world = base.getWorld();
        if (world == null) {
            run.complete();
            return;
        }

        Session previous = sessionsByPlayer.remove(player.getUniqueId());
        if (previous != null) previous.run.cancel();

        Session session = new Session(run, player, base.clone(), definition, reward);
        sessionsByPlayer.put(player.getUniqueId(), session);
        run.onClose(() -> forgetSession(session));

        for (int index = 0; index < MOB_COUNT; index++) {
            double angle = Math.PI * 2.0 * index / MOB_COUNT;
            Location spawn = findStandingLocation(base, angle, 3.4);
            Mob mob = spawnGuardian(world, spawn, index);
            session.mobs.add(mob);
            session.spawnLocations.put(mob.getUniqueId(), spawn.clone());
            sessionsByMob.put(mob.getUniqueId(), session);
            run.track(mob);
        }

        player.sendMessage(plugin.getUi().get("mob-hunt-start"));
        playSound(world, base, Sound.ENTITY_ZOMBIE_AMBIENT, 0.65f, 0.82f);
        playSound(world, base, Sound.ENTITY_SKELETON_AMBIENT, 0.55f, 1.08f);
        ParticleCompat.spawn(world, base.clone().add(0.0, 1.0, 0.0),
                new String[]{"SMOKE", "SMOKE_NORMAL", "CLOUD"}, 36, 2.4, 0.7, 2.4, 0.03);

        session.monitorTask = startInteractive(run, new InteractiveAnimationTask(run, "mob-hunt-monitor") {
            int elapsed;

            @Override
            protected boolean update(int tick) {
                if (session.completed || session.revealing) {
                    cancel();
                    return false;
                }
                elapsed += 10;
                if (!player.isOnline() || player.isDead()) {
                    beginReveal(session, session.base.clone().add(0.0, 1.0, 0.0), true);
                    return false;
                }

                for (Mob mob : new ArrayList<>(session.mobs)) {
                    if (!mob.isValid() || mob.isDead()) {
                        forgetMob(session, mob);
                        continue;
                    }
                    if (mob.getWorld() != session.base.getWorld()
                            || mob.getLocation().distanceSquared(session.base) > MAX_DISTANCE_SQUARED) {
                        Location spawn = session.spawnLocations.getOrDefault(mob.getUniqueId(), session.base);
                        mob.teleport(spawn);
                    }
                }

                if (session.mobs.isEmpty()) {
                    beginReveal(session, session.base.clone().add(0.0, 1.0, 0.0), false);
                } else if (elapsed >= TIMEOUT_TICKS) {
                    player.sendMessage(plugin.getUi().get("mob-hunt-timeout"));
                    beginReveal(session, session.base.clone().add(0.0, 1.0, 0.0), true);
                }
                return false;
            }
        }, 10L, 10L);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        Session victimSession = sessionsByMob.get(event.getEntity().getUniqueId());
        if (victimSession != null) {
            if (!(event instanceof EntityDamageByEntityEvent byEntity)
                    || !isOwnerAttack(byEntity.getDamager(), victimSession)) {
                event.setCancelled(true);
            } else {
                event.setDamage(1000.0);
            }
            return;
        }

        if (!(event instanceof EntityDamageByEntityEvent byEntity)) return;
        Session attackerSession = sessionForAttacker(byEntity.getDamager());
        if (attackerSession == null) return;
        if (!(event.getEntity() instanceof Player victim)
                || !victim.getUniqueId().equals(attackerSession.playerId)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent event) {
        Session session = sessionsByMob.get(event.getEntity().getUniqueId());
        if (session == null) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onCombust(EntityCombustEvent event) {
        if (sessionsByMob.containsKey(event.getEntity().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        Session session = sessionsByMob.get(event.getEntity().getUniqueId());
        if (session == null) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        forgetMob(session, (Mob) event.getEntity());

        if (session.selectionMade) return;
        session.selectionMade = true;
        Location selectedLocation = event.getEntity().getLocation().add(0.0, 0.55, 0.0);
        playSound(session.player, session.player.getLocation(),
                Sound.ENTITY_PLAYER_LEVELUP, 0.45f, 1.35f);
        startDeathSequence(session, selectedLocation);
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        ProjectileSource shooter = projectile.getShooter();
        if (!(shooter instanceof Entity entity)) return;
        Session session = sessionsByMob.get(entity.getUniqueId());
        if (session == null) return;
        sessionsByProjectile.put(projectile.getUniqueId(), session);
        session.projectiles.add(projectile);
        session.run.track(projectile);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        Session session = sessionsByProjectile.remove(projectile.getUniqueId());
        if (session == null) return;
        session.projectiles.remove(projectile);
        session.run.untrack(projectile);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        HandlerList.unregisterAll(this);
    }

    private Mob spawnGuardian(World world, Location location, int index) {
        boolean zombie = ThreadLocalRandom.current().nextBoolean();
        Mob mob;
        if (zombie) {
            Zombie entity = AnimationMobSpawner.spawn(world, location, Zombie.class);
            entity.setBaby(false);
            mob = entity;
        } else {
            mob = AnimationMobSpawner.spawn(world, location, Skeleton.class);
        }
        mob.setCustomName((zombie ? "§cЗомби" : "§fСкелет") + " §8| §7Страж кейса");
        mob.setCustomNameVisible(true);
        mob.setAI(false);
        mob.setSilent(true);
        mob.setCanPickupItems(false);
        mob.setPersistent(false);
        mob.setRemoveWhenFarAway(false);

        EntityEquipment equipment = mob.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(new ItemStack(Material.LEATHER_HELMET));
            equipment.setHelmetDropChance(0.0f);
            equipment.setItemInMainHandDropChance(0.0f);
            equipment.setItemInOffHandDropChance(0.0f);
            equipment.setChestplateDropChance(0.0f);
            equipment.setLeggingsDropChance(0.0f);
            equipment.setBootsDropChance(0.0f);
        }
        mob.addScoreboardTag("pncases_mob_hunt");
        return mob;
    }

    private Location findStandingLocation(Location center, double angle, double radius) {
        World world = center.getWorld();
        Location candidate = center.clone().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
        if (world == null) return candidate;
        int x = candidate.getBlockX();
        int z = candidate.getBlockZ();
        int startY = center.getBlockY() + 2;
        for (int y = startY; y >= center.getBlockY() - 3; y--) {
            if (world.getBlockAt(x, y - 1, z).getType().isSolid()
                    && world.getBlockAt(x, y, z).isPassable()
                    && world.getBlockAt(x, y + 1, z).isPassable()) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        return center.clone().add(Math.cos(angle) * 2.2, 1.0, Math.sin(angle) * 2.2);
    }

    private boolean isOwnerAttack(Entity damager, Session session) {
        if (damager instanceof Player player) return player.getUniqueId().equals(session.playerId);
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player.getUniqueId().equals(session.playerId);
        }
        return false;
    }

    private Session sessionForAttacker(Entity damager) {
        Session direct = sessionsByMob.get(damager.getUniqueId());
        if (direct != null) return direct;
        if (damager instanceof Projectile projectile) {
            Session tracked = sessionsByProjectile.get(projectile.getUniqueId());
            if (tracked != null) return tracked;
            if (projectile.getShooter() instanceof Entity shooter) {
                return sessionsByMob.get(shooter.getUniqueId());
            }
        }
        return null;
    }

    private void forgetMob(Session session, Mob mob) {
        session.mobs.remove(mob);
        session.spawnLocations.remove(mob.getUniqueId());
        sessionsByMob.remove(mob.getUniqueId());
        session.run.untrack(mob);
    }

    private void startDeathSequence(Session session, Location selectedLocation) {
        cancelTask(session, session.monitorTask);
        session.monitorTask = null;
        List<Mob> dying = new ArrayList<>(session.mobs);
        dying.sort((first, second) -> Double.compare(
                angleFromCenter(session.base, first.getLocation()),
                angleFromCenter(session.base, second.getLocation())));

        session.deathTask = scheduleTask(session.run,
                new ru.privatenull.pncases.cases.animation.core.AnimationTask(session.run, "mob-hunt-death") {
            int index;

            @Override
            protected void tick() {
                if (session.completed || session.revealing) {
                    cancel();
                    return;
                }
                while (index < dying.size()) {
                    Mob mob = dying.get(index++);
                    if (!mob.isValid() || mob.isDead()) continue;
                    Location death = mob.getLocation().add(0.0, 0.9, 0.0);
                    playSound(mob.getWorld(), death, mob instanceof Zombie
                            ? Sound.ENTITY_ZOMBIE_DEATH : Sound.ENTITY_SKELETON_DEATH, 0.38f, 0.9f + index * 0.05f);
                    ParticleCompat.spawn(mob.getWorld(), death,
                            new String[]{"SOUL", "SMOKE", "SMOKE_NORMAL"}, 16, 0.30, 0.55, 0.30, 0.035);
                    mob.setHealth(0.0);
                    return;
                }
                cancelTask(session, session.deathTask);
                session.deathTask = null;
                beginReveal(session, selectedLocation, false);
            }
        }, 7L, 8L);
    }

    private static double angleFromCenter(Location center, Location point) {
        double angle = Math.atan2(point.getZ() - center.getZ(), point.getX() - center.getX());
        return angle < 0.0 ? angle + Math.PI * 2.0 : angle;
    }

    private void beginReveal(Session session, Location location, boolean forced) {
        if (session.completed || session.revealing) return;
        session.revealing = true;
        cancelTask(session, session.monitorTask);
        cancelTask(session, session.deathTask);
        session.monitorTask = null;
        session.deathTask = null;

        for (Mob mob : new ArrayList<>(session.mobs)) {
            forgetMob(session, mob);
            mob.remove();
        }
        removeProjectiles(session);

        World world = location.getWorld();
        if (world == null) {
            cleanup(session, true);
            return;
        }
        ItemStack visual = resolveRewardVisual(session.reward, session.definition);
        Location frameCenter = location.clone().add(0.0, 0.55, 0.0);
        session.run.protectReward(frameCenter);
        session.rewardReveal = createRewardReveal(session.run, frameCenter, visual,
                resolveRewardName(session.reward, visual));
        for (Entity entity : session.rewardReveal.entities()) session.run.track(entity);
        playSound(world, location, Sound.ENTITY_PLAYER_LEVELUP, 0.65f, forced ? 1.10f : 1.55f);
        spawn(world, particle(TOTEM), frameCenter, 8, 0.35, 0.45, 0.35, 0.045);
        session.revealTask = scheduleTask(session.run,
                new ru.privatenull.pncases.cases.animation.core.AnimationTask(session.run, "mob-hunt-reveal") {
            int tick;

            @Override
            protected void tick() {
                tick++;
                if (session.completed) {
                    cancel();
                    return;
                }
                session.rewardReveal.tick(frameCenter, tick);

                if (tick % 5 == 0) {
                    ParticleCompat.spawn(world, frameCenter.clone().add(0.0, 0.18, 0.0),
                            new String[]{"END_ROD", "ENCHANT"}, 1, 0.24, 0.30, 0.24, 0.01);
                }
                if (tick == 30) {
                    playSound(world, frameCenter, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.45f, 1.55f);
                    ParticleCompat.spawn(world, frameCenter, new String[]{"TOTEM_OF_UNDYING", "END_ROD"},
                            6, 0.35, 0.45, 0.35, 0.025);
                }
                if (tick >= 65) cleanup(session, true);
            }
        }, 1L, 1L);
    }

    private void cleanup(Session session, boolean finish) {
        if (session.completed) return;
        session.completed = true;
        if (finish) session.run.complete();
        else session.run.cancel();
    }

    private void forgetSession(Session session) {
        sessionsByPlayer.remove(session.playerId, session);
        for (Mob mob : session.mobs) sessionsByMob.remove(mob.getUniqueId(), session);
        for (Projectile projectile : session.projectiles) {
            sessionsByProjectile.remove(projectile.getUniqueId(), session);
        }
        session.mobs.clear();
        session.projectiles.clear();
    }

    private void removeProjectiles(Session session) {
        for (Projectile projectile : new ArrayList<>(session.projectiles)) {
            sessionsByProjectile.remove(projectile.getUniqueId());
            projectile.remove();
            session.run.untrack(projectile);
        }
        session.projectiles.clear();
    }

    private void cancelTask(Session session, BukkitTask task) {
        if (task == null) return;
        task.cancel();
        session.run.untrack(task);
    }

    private RewardReveal createRewardReveal(AnimationSession run, Location center, ItemStack item, String name) {
        if (!ServerCapabilities.hasDisplayEntities()) {
            return new LegacyRewardReveal(run, center, item, name);
        }
        try {
            Class<?> type = Class.forName("ru.privatenull.pncases.cases.animation.reveal.DisplayRewardReveal");
            Object instance = type.getConstructor(AnimationSession.class, Location.class, ItemStack.class, String.class)
                    .newInstance(run, center, item, name);
            if (instance instanceof RewardReveal visual) return visual;
            throw new IllegalStateException(type.getName() + " does not implement RewardReveal");
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException("Could not create modern mob reward animation", exception);
        }
    }

    private static final class Session {
        private final AnimationSession run;
        private final UUID playerId;
        private final Player player;
        private final Location base;
        private final CaseDefinition definition;
        private final Reward reward;
        private final Set<Mob> mobs = new HashSet<>();
        private final Set<Projectile> projectiles = new HashSet<>();
        private final Map<UUID, Location> spawnLocations = new HashMap<>();
        private BukkitTask monitorTask;
        private BukkitTask deathTask;
        private BukkitTask revealTask;
        private RewardReveal rewardReveal;
        private boolean revealing;
        private boolean selectionMade;
        private boolean completed;

        private Session(AnimationSession run, Player player, Location base,
                        CaseDefinition definition, Reward reward) {
            this.run = run;
            this.playerId = player.getUniqueId();
            this.player = player;
            this.base = base;
            this.definition = definition;
            this.reward = reward;
        }
    }
}
