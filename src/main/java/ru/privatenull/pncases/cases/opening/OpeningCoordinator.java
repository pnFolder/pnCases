package ru.privatenull.pncases.cases.opening;

import ru.privatenull.pncases.integration.Log;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ru.privatenull.pncases.PnCasesPlugin;
import ru.privatenull.pncases.api.event.CaseOpenEvent;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.CaseRegistry;
import ru.privatenull.pncases.cases.animation.core.AbstractCaseAnimation;
import ru.privatenull.pncases.cases.animation.core.AnimationAvailability;
import ru.privatenull.pncases.cases.animation.core.AnimationCatalog;
import ru.privatenull.pncases.cases.animation.core.AnimationSession;
import ru.privatenull.pncases.cases.model.AnimationType;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pncases.cases.player.PlayerAnimationPreferences;
import ru.privatenull.pncases.cases.reward.RewardDeliveryService;
import ru.privatenull.pncases.hologram.CaseHologramManager;
import ru.privatenull.pncases.storage.CasesDatabase;
import ru.privatenull.pncases.storage.PendingBatch;
import ru.privatenull.pncases.support.compat.ServerVersion;
import ru.privatenull.pncases.support.database.DatabaseExecutor;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Runs one case opening from key reservation to reward delivery: reserves the key and reward in
 * storage, plays the animation, delivers the reward and recovers anything left pending.
 */
public final class OpeningCoordinator {

    private static final long ANIMATION_WATCHDOG_TICKS = 20L * 120L;
    private static final double SPECTATOR_REPEL_RADIUS = 5.0D;
    private static final double OPENER_REPEL_RADIUS = 3.0D;

    private final PnCasesPlugin plugin;
    private final CaseRegistry registry;
    private final CaseOpeningLocks openingLocks;
    private final OpeningReservationService reservations;
    private final RewardDeliveryService rewardDelivery;
    private final CasesDatabase database;
    private final DatabaseExecutor databaseExecutor;
    private final AnimationCatalog animations;
    private static final long REPEL_NOTICE_COOLDOWN_MS = 4000L;
    private final java.util.Map<UUID, Long> repelNotices = new java.util.concurrent.ConcurrentHashMap<>();
    private final AnimationAvailability availability;
    private final java.util.Set<String> reportedFallbacks = ConcurrentHashMap.newKeySet();
    private final PlayerAnimationPreferences animationPreferences;
    private final Supplier<CaseManager.View> view;

    private final Map<UUID, ActiveOpening> activeOpenings = new ConcurrentHashMap<>();
    private final Set<UUID> pendingDeliveries = ConcurrentHashMap.newKeySet();
    private volatile boolean shuttingDown;

    public OpeningCoordinator(
            PnCasesPlugin plugin,
            CaseRegistry registry,
            CaseOpeningLocks openingLocks,
            OpeningReservationService reservations,
            RewardDeliveryService rewardDelivery,
            CasesDatabase database,
            DatabaseExecutor databaseExecutor,
            AnimationCatalog animations,
            AnimationAvailability availability,
            PlayerAnimationPreferences animationPreferences,
            Supplier<CaseManager.View> view
    ) {
        this.plugin = plugin;
        this.registry = registry;
        this.openingLocks = openingLocks;
        this.reservations = reservations;
        this.rewardDelivery = rewardDelivery;
        this.database = database;
        this.databaseExecutor = databaseExecutor;
        this.animations = animations;
        this.availability = availability;
        this.animationPreferences = animationPreferences;
        this.view = view;
    }

    /** Stops new openings and reward delivery; call before animations are shut down. */
    public void markShuttingDown() {
        shuttingDown = true;
    }

    public void shutdown() {
        shuttingDown = true;
        for (ActiveOpening opening : new ArrayList<>(activeOpenings.values())) {
            completeOpening(opening, false, null);
        }
        activeOpenings.clear();
    }

    public void onPlayerQuit(UUID playerId) {
        repelNotices.remove(playerId);
        ActiveOpening opening = activeOpenings.get(playerId);
        if (opening != null) cancelAnimationSession(opening, false);
        else openingLocks.unmarkOpening(playerId);
    }

    public void onWorldUnload(World world) {
        UUID unloadingWorldId = world.getUID();
        // Animation instances are shared across worlds. Cleanup is therefore
        // scoped by the tracked world instead of cancelling the whole type.
        animations.cancelWorld(world);
        for (ActiveOpening opening : new ArrayList<>(activeOpenings.values())) {
            if (belongsToWorld(opening.worldId, unloadingWorldId)) {
                completeOpening(opening, true, unloadingWorldId);
            }
        }
    }

    /** Keeps players away from the block while an animation is running. */
    public void repelPlayersFromActiveCases() {
        if (activeOpenings.isEmpty()) return;
        for (ActiveOpening opening : activeOpenings.values()) {
            if (opening.completed.get() || opening.caseBlockLocation == null) continue;
            World world = Bukkit.getWorld(opening.worldId);
            if (world == null) continue;

            Location center = opening.caseBlockLocation.clone().add(0.5D, 0.0D, 0.5D);
            for (Player target : world.getPlayers()) {
                if (!target.isOnline() || target.isDead()) continue;
                double radius = target.getUniqueId().equals(opening.playerId)
                        ? OPENER_REPEL_RADIUS : SPECTATOR_REPEL_RADIUS;
                if (repelIfTooClose(target, center, radius)) warnOpeningInProgress(target);
            }
        }
    }

    /** Tells a pushed-away bystander why, at most once per few seconds. */
    private void warnOpeningInProgress(Player player) {
        long now = System.currentTimeMillis();
        Long last = repelNotices.get(player.getUniqueId());
        if (last != null && now - last < REPEL_NOTICE_COOLDOWN_MS) return;
        repelNotices.put(player.getUniqueId(), now);
        player.sendMessage(plugin.getUi().get("case-opening-in-progress"));
    }

    private boolean repelIfTooClose(Player player, Location center, double radius) {
        Location location = player.getLocation();
        double dx = location.getX() - center.getX();
        double dz = location.getZ() - center.getZ();
        if (dx * dx + dz * dz >= radius * radius) return false;

        Vector outward = new Vector(dx, 0.0D, dz);
        if (outward.lengthSquared() < 0.0001D) {
            outward = player.getLocation().getDirection().multiply(-1.0D);
            outward.setY(0.0D);
        }
        if (outward.lengthSquared() < 0.0001D) outward = new Vector(1.0D, 0.0D, 0.0D);
        outward.normalize().multiply(0.75D).setY(0.12D);
        player.setVelocity(outward);
        player.setFallDistance(0.0F);
        return true;
    }

    public void tryOpenCases(Player player, CaseDefinition definition, int amount) {
        if (shuttingDown || !plugin.isEnabled() || !player.isOnline()) return;
        int normalized = Math.max(1, Math.min(64, amount));
        UUID playerId = player.getUniqueId();
        if (openingLocks.isOpening(playerId) || pendingDeliveries.contains(playerId)) {
            player.sendMessage(plugin.getUi().get("already-opening"));
            return;
        }
        CaseOpenEvent event = new CaseOpenEvent(player, definition.name(), normalized);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;
        tryOpenCaseInternal(player, definition, normalized);
    }

    private void tryOpenCaseInternal(Player p, CaseDefinition def, int amount) {
        UUID playerId = p.getUniqueId();
        if (openingLocks.isOpening(playerId) || pendingDeliveries.contains(playerId)) {
            p.sendMessage(plugin.getUi().get("already-opening"));
            return;
        }
        CaseBlockKey sourceBlock = selectedBlockKey(p, def);
        PreparedOpening prepared = prepareOpening(p, def, sourceBlock);
        if (prepared == null) {
            releaseOpeningState(p.getUniqueId(), sourceBlock);
            return;
        }
        if (!openingLocks.tryLock(p.getUniqueId(), sourceBlock)) {
            p.sendMessage(plugin.getUi().get("case-busy"));
            return;
        }
        openingLocks.markOpening(playerId);
        databaseExecutor.completeSync(
                databaseExecutor.supply(playerId,
                        () -> reservations.reserve(playerId, def, amount)),
                result -> finishOpeningReservation(p, def, sourceBlock, prepared, amount, result),
                failure -> {
                    releaseOpeningState(playerId, sourceBlock);
                    Log.error("Не удалось подготовить storage для открытия игрока " + p.getName() + ".", failure);
                    if (p.isOnline()) p.sendMessage(plugin.getUi().get("case-open-storage-error"));
                });
    }

    private void finishOpeningReservation(
            Player player,
            CaseDefinition definition,
            CaseBlockKey sourceBlock,
            PreparedOpening prepared,
            int amount,
            OpeningReservation reservedOpening
    ) {
        CasesDatabase.OpeningReservation reservation = reservedOpening.storage();
        UUID playerId = player.getUniqueId();
        if (!player.isOnline() || shuttingDown || !plugin.isEnabled()) {
            // A ready reservation already owns a durable pending reward. Keep it
            // for the join recovery path instead of refunding the consumed key.
            releaseOpeningState(playerId, sourceBlock);
            return;
        }
        if (reservation.pendingReward() != null) {
            releaseOpeningState(playerId, sourceBlock);
            player.sendMessage(plugin.getUi().get("pending-reward-wait"));
            deliverPendingRewardAsync(player, ignored -> { });
            return;
        }
        if (!reservation.reserved()) {
            releaseOpeningState(playerId, sourceBlock);
            player.sendMessage(plugin.getUi().get("not-enough-keys",
                    "need", String.valueOf(amount), "have", String.valueOf(reservation.balance())));
            return;
        }
        CostReceipt cost = new CostReceipt(definition.name(), reservedOpening.batch() != null);
        Reward selectedReward = reservedOpening.reward();
        if (selectedReward == null) {
            cancelReservationAsync(playerId, cost);
            releaseOpeningState(playerId, sourceBlock);
            Log.error("Storage не вернул выбранную награду для кейса '" + definition.name() + "'.");
            return;
        }
        try {
            player.closeInventory();
        } catch (RuntimeException exception) {
            cancelReservationAsync(playerId, cost);
            releaseOpeningState(playerId, sourceBlock);
            Log.warning("Не удалось закрыть GUI перед открытием кейса игроку " + player.getName() + ".", exception);
            return;
        }
        startAnimationAndReward(player, definition, sourceBlock,
                prepared.withReward(selectedReward, reservedOpening.batch()), cost);
    }

    private PreparedOpening prepareOpening(Player p, CaseDefinition def, CaseBlockKey sourceBlock) {
        UUID playerId = p.getUniqueId();
        if (sourceBlock == null || !registry.serves(sourceBlock, def.name())) {
            return null;
        }
        Location sourceLocation = locationFromBlockKey(sourceBlock);
        if (sourceLocation == null) {
            return null;
        }

        Location caseBlockLocation = sourceLocation.clone();
        Location base = sourceLocation.clone().add(0.5, 0.0, 0.5);
        World w = base.getWorld();
        if (w == null) {
            return null;
        }

        AbstractCaseAnimation animation;
        try {
            AnimationType animationType = def.fixedAnimation() == null
                    ? animationPreferences.selectForOpening(playerId, def.name())
                    : def.fixedAnimation();
            if (ServerVersion.current().isReleaseLine(1, 16)) {
                animationType = AnimationType.FORTUNE_RING;
            }
            animationType = usableAnimation(def, animationType, sourceLocation);
            animation = animations.get(animationType);
            if (animation == null) {
                throw new IllegalStateException("Animation is not registered: " + animationType);
            }
        } catch (RuntimeException | LinkageError exception) {
            Log.error("Не удалось подготовить анимацию кейса '" + def.name() + "'.", exception);
            return null;
        }

        return new PreparedOpening(null, animation, base, caseBlockLocation, w.getUID(), null);
    }

    /**
     * An animation that cannot run at the case (peaceful world, mobs forbidden there) is replaced by the ring of
     * fortune, so an opening never breaks. The reason is logged once per case and animation.
     */
    private AnimationType usableAnimation(CaseDefinition def, AnimationType wanted, Location caseBlock) {
        AnimationAvailability.Availability result = availability.check(wanted, caseBlock);
        if (result.available()) return wanted;
        if (reportedFallbacks.add(def.name() + ":" + wanted)) {
            Log.warning("Анимация " + wanted + " не может запуститься у кейса '"
                    + def.name() + "': " + result.reason() + ". Вместо неё играет " + AnimationType.FORTUNE_RING + ".");
        }
        return AnimationType.FORTUNE_RING;
    }

    static PendingBatch selectBatch(CaseDefinition definition, int progress, int amount) {
        return OpeningReservationService.selectBatch(definition, progress, amount);
    }

    private boolean startAnimationAndReward(
            Player p,
            CaseDefinition def,
            CaseBlockKey sourceBlock,
            PreparedOpening prepared,
            CostReceipt cost
    ) {
        UUID playerId = p.getUniqueId();
        ActiveOpening opening = new ActiveOpening(
                playerId,
                p,
                def,
                prepared.reward,
                sourceBlock,
                prepared.caseBlockLocation,
                prepared.worldId,
                prepared.batch
        );
        ActiveOpening previous = activeOpenings.putIfAbsent(playerId, opening);
        if (previous != null) {
            Log.error("Повторный активный сеанс открытия для игрока " + p.getName()
                    + " был отклонён.");
            cancelReservationAsync(playerId, cost);
            if (!Objects.equals(previous.sourceBlock, sourceBlock)) {
                openingLocks.clearSelection(playerId, sourceBlock);
                openingLocks.unlock(playerId, sourceBlock);
            }
            p.sendMessage(plugin.getUi().get("already-opening"));
            return false;
        }
        openingLocks.markOpening(playerId);
        opening.pendingStored = true;
        Runnable completion = () -> completeOpening(opening, true, null);

        try {
            var holograms = plugin.getHolograms();
            if (holograms != null) {
                String hologramOwner = registry.ownerOf(sourceBlock);
                CaseDefinition hologramCase = hologramOwner == null ? null : registry.byName(hologramOwner);
                holograms.hideCase(hologramCase == null ? def : hologramCase, prepared.caseBlockLocation);
                opening.hologramHidden = true;
            }
            opening.watchdog = plugin.tasks().later(() -> {
                if (opening.completed.get()) return;
                Log.warning("Анимация кейса '" + opening.caseName
                        + "' не завершилась вовремя; выполняется безопасная отмена.");
                cancelAnimationSession(opening, true);
            }, ANIMATION_WATCHDOG_TICKS);
        } catch (RuntimeException exception) {
            Log.error("Не удалось запустить окружение анимации кейса '" + def.name() + "'.", exception);
            completeOpening(opening, true, null);
            return true;
        }

        try {
            opening.animationRun = prepared.animation.play(
                    p, def, prepared.reward, prepared.base, completion);
        } catch (RuntimeException | LinkageError exception) {
            Log.error("Анимация кейса '" + def.name() + "' аварийно остановлена.", exception);
            cancelAnimationSession(opening, true);
        }
        return true;
    }

    /**
     * Delivers a reward left by a previous interrupted opening. A new opening
     * must never overwrite that single pending-reward slot.
     */
    public void deliverPendingRewardAsync(Player player, Consumer<Boolean> completion) {
        if (player == null || !player.isOnline() || shuttingDown || !plugin.isEnabled()) {
            completion.accept(false);
            return;
        }
        UUID playerId = player.getUniqueId();
        if (pendingDeliveryBlocked(activeOpenings.containsKey(playerId), openingLocks.isOpening(playerId))
                || !pendingDeliveries.add(playerId)) {
            completion.accept(false);
            return;
        }
        databaseExecutor.completeSync(
                databaseExecutor.supply(playerId, () -> new PendingRecovery(
                        database.loadPending(playerId), database.loadBatch(playerId))),
                recovery -> {
                    Reward pending = recovery.reward();
                    if (pending == null && recovery.batch() != null) {
                        PendingBatch batch = recovery.batch();
                        deliverBatch(player, registry.byName(batch.caseId()), batch, batch.nextIndex(),
                                () -> {
                                    showBatchResults(player, batch);
                                    completion.accept(true);
                                }, () -> completion.accept(false));
                        return;
                    }
                    if (pending == null) {
                        pendingDeliveries.remove(playerId);
                        completion.accept(true);
                        return;
                    }
                    boolean delivered;
                    try {
                        delivered = player.isOnline() && rewardDelivery.deliver(player, null, pending);
                    } catch (RuntimeException exception) {
                        delivered = false;
                        Log.error("Не удалось выдать отложенную награду игроку " + player.getName() + ".", exception);
                    }
                    if (!delivered) {
                        pendingDeliveries.remove(playerId);
                        completion.accept(false);
                        return;
                    }
                    clearDeliveredPending(playerId, player.getName(),
                            () -> deliverPendingRewardAsync(player, completion),
                            () -> completion.accept(false), 0);
                },
                failure -> {
                    pendingDeliveries.remove(playerId);
                    Log.error("Не удалось проверить отложенную награду игрока " + player.getName() + ".", failure);
                    completion.accept(false);
                });
    }

    static boolean pendingDeliveryBlocked(boolean activeOpening, boolean markedOpening) {
        return activeOpening || markedOpening;
    }

    static boolean belongsToWorld(UUID sessionWorldId, UUID targetWorldId) {
        return sessionWorldId != null && sessionWorldId.equals(targetWorldId);
    }

    private void cancelAnimationSession(ActiveOpening opening, boolean deliverReward) {
        if (opening == null) return;
        try {
            if (opening.animationRun != null) opening.animationRun.cancel();
        } catch (RuntimeException exception) {
            Log.error("Не удалось полностью отменить анимацию кейса.", exception);
        }
        completeOpening(opening, deliverReward, null);
    }

    private void completeOpening(ActiveOpening opening, boolean deliverReward, UUID unloadingWorldId) {
        if (opening == null || !opening.completed.compareAndSet(false, true)) return;

        if (opening.batch != null) {
            if (deliverReward && !shuttingDown && plugin.isEnabled() && opening.player.isOnline()) {
                pendingDeliveries.add(opening.playerId);
                deliverBatch(opening.player, opening.definition, opening.batch, opening.batch.nextIndex(),
                        () -> {
                            finalizeOpening(opening, unloadingWorldId);
                            if (unloadingWorldId == null) showBatchResults(opening.player, opening.batch);
                        }, () -> finalizeOpening(opening, unloadingWorldId));
            } else {
                finalizeOpening(opening, unloadingWorldId);
            }
            return;
        }
        boolean delivered = false;
        try {
            boolean mayDeliver = deliverReward
                    && !shuttingDown
                    && plugin.isEnabled()
                    && opening.player.isOnline();
            delivered = mayDeliver && rewardDelivery.deliver(opening.player, opening.definition, opening.reward);
        } catch (RuntimeException exception) {
            Log.error("Не удалось завершить выдачу награды игроку " + opening.player.getName()
                            + "; награда оставлена в pending storage.", exception);
        }
        if (delivered && opening.pendingStored && !shuttingDown && plugin.isEnabled()) {
            pendingDeliveries.add(opening.playerId);
            clearDeliveredPending(opening.playerId, opening.player.getName(),
                    () -> finalizeOpening(opening, unloadingWorldId),
                    () -> finalizeOpening(opening, unloadingWorldId), 0);
            return;
        }
        finalizeOpening(opening, unloadingWorldId);
    }

    /**
     * A delivered reward must not become deliverable again just because the
     * database had a short outage while acknowledging it. The player remains
     * locked until the acknowledgement succeeds or the plugin is restarted.
     */
    private void deliverBatch(Player player, CaseDefinition definition, PendingBatch batch, int index,
                              Runnable success, Runnable failure) {
        UUID playerId = player.getUniqueId();
        if (index >= batch.rewards().size()) {
            pendingDeliveries.remove(playerId);
            if (!shuttingDown && plugin.isEnabled()) {
                rewardDelivery.announceBatch(player, definition, batch.caseId(), batch.rewards());
            }
            success.run();
            return;
        }
        boolean delivered = false;
        try {
            delivered = !shuttingDown && plugin.isEnabled() && player.isOnline()
                    && rewardDelivery.deliverSilently(player, definition, batch.rewards().get(index));
        } catch (RuntimeException exception) {
            Log.error("Не удалось выдать награду из серии " + batch.id(), exception);
        }
        if (!delivered) {
            pendingDeliveries.remove(playerId);
            failure.run();
            return;
        }
        acknowledgePending(playerId, player.getName(),
                () -> database.acknowledgeBatchReward(playerId, batch.id(), index),
                () -> deliverBatch(player, definition, batch, index + 1, success, failure), failure, 0);
    }

    private void showBatchResults(Player player, PendingBatch batch) {
        if (!shuttingDown && plugin.isEnabled() && player.isOnline()) {
            view.get().showBatchResults(player, batch.caseId(), batch.rewards());
        }
    }

    private void clearDeliveredPending(
            UUID playerId,
            String playerName,
            Runnable success,
            Runnable exhausted,
            int attempt
    ) {
        acknowledgePending(playerId, playerName, () -> database.clearPending(playerId), () -> {
            pendingDeliveries.remove(playerId);
            success.run();
        }, exhausted, attempt);
    }

    private void acknowledgePending(UUID playerId, String playerName, Runnable write,
                                    Runnable success, Runnable exhausted, int attempt) {
        databaseExecutor.completeSync(
                databaseExecutor.run(playerId, write),
                ignored -> success.run(),
                failure -> {
                    int nextAttempt = attempt + 1;
                    if (nextAttempt < 4 && !shuttingDown && plugin.isEnabled()) {
                        long delay = switch (nextAttempt) {
                            case 1 -> 20L;
                            case 2 -> 60L;
                            default -> 200L;
                        };
                        plugin.tasks().later(() -> acknowledgePending(playerId, playerName, write, success, exhausted, nextAttempt),
                                delay);
                        return;
                    }
                    Log.error("Награда уже выдана, но pending-запись не удалось очистить для "
                                    + playerName + " после " + (attempt + 1) + " попыток. "
                                    + "Повторная выдача заблокирована до перезапуска.", failure);
                    exhausted.run();
                });
    }

    private void finalizeOpening(ActiveOpening opening, UUID unloadingWorldId) {
        BukkitTask watchdog = opening.watchdog;
        if (watchdog != null) {
            try {
                watchdog.cancel();
            } catch (RuntimeException ignored) {
            }
        }
        activeOpenings.remove(opening.playerId, opening);
        releaseOpeningState(opening.playerId, opening.sourceBlock);
        restoreOpeningHologram(opening, unloadingWorldId);
    }

    private void restoreOpeningHologram(ActiveOpening opening, UUID unloadingWorldId) {
        if (!opening.hologramHidden || shuttingDown) return;
        if (unloadingWorldId != null && unloadingWorldId.equals(opening.worldId)) return;
        World loadedWorld = Bukkit.getWorld(opening.worldId);
        if (loadedWorld == null) return;

        String hologramOwner = registry.ownerOf(opening.sourceBlock);
        CaseDefinition current = hologramOwner == null ? null : registry.byName(hologramOwner);
        if (current == null || !registry.serves(opening.sourceBlock, opening.caseName)) return;

        var holograms = plugin.getHolograms();
        if (holograms == null) return;
        try {
            Location currentLocation = new Location(loadedWorld,
                    opening.sourceBlock.x(), opening.sourceBlock.y(), opening.sourceBlock.z());
            holograms.showCase(current, currentLocation);
        } catch (RuntimeException exception) {
            Log.warning("Не удалось восстановить голограмму кейса '" + opening.caseName + "'.", exception);
        }
    }

    /** Hides holograms of cases that are mid-animation after a registry refresh recreated them. */
    public void hideActiveOpeningHolograms(CaseHologramManager holograms, String onlyCaseName) {
        if (holograms == null) return;
        for (ActiveOpening opening : new ArrayList<>(activeOpenings.values())) {
            if (opening.completed.get() || !opening.hologramHidden) continue;
            String owner = registry.ownerOf(opening.sourceBlock);
            if (owner == null || !registry.serves(opening.sourceBlock, opening.caseName)) continue;
            if (onlyCaseName != null && !onlyCaseName.equals(owner) && !onlyCaseName.equals(opening.caseName)) continue;

            CaseDefinition current = registry.byName(owner);
            World world = Bukkit.getWorld(opening.worldId);
            if (current == null || world == null) continue;
            Location location = new Location(world,
                    opening.sourceBlock.x(), opening.sourceBlock.y(), opening.sourceBlock.z());
            holograms.hideCase(current, location);
        }
    }

    private void releaseOpeningState(UUID playerId, CaseBlockKey sourceBlock) {
        openingLocks.release(playerId, sourceBlock);
    }

    private CaseBlockKey selectedBlockKey(Player player, CaseDefinition def) {
        CaseBlockKey selected = openingLocks.selected(player.getUniqueId());
        if (selected != null && registry.serves(selected, def.name())) {
            return selected;
        }
        if (selected != null) openingLocks.clearSelection(player.getUniqueId(), selected);
        CaseBlockKey fallback = def.blockLocation() == null ? null : CaseBlockKey.of(def.blockLocation());
        return fallback != null && registry.serves(fallback, def.name()) ? fallback : null;
    }

    private Location locationFromBlockKey(CaseBlockKey key) {
        if (key == null) {
            return null;
        }
        World world = Bukkit.getWorld(key.world());
        return world == null ? null : new Location(world, key.x(), key.y(), key.z());
    }

    private void cancelReservationAsync(UUID playerId, CostReceipt receipt) {
        // A batch already has all rewards durably reserved. Keep it for recovery, never refund one key.
        if (receipt != null && receipt.batch) return;
        if (playerId == null || receipt == null || !receipt.refunded.compareAndSet(false, true)) return;
        databaseExecutor.completeSync(
                databaseExecutor.run(playerId, () -> {
                    database.cancelOpening(playerId, receipt.keyId);
                }),
                ignored -> { },
                failure -> Log.error("Не удалось отменить резерв открытия игрока " + playerId + ".", failure));
    }

    private record PreparedOpening(
            Reward reward,
            AbstractCaseAnimation animation,
            Location base,
            Location caseBlockLocation,
            UUID worldId,
            PendingBatch batch
    ) {
        private PreparedOpening withReward(Reward selectedReward, PendingBatch reservedBatch) {
            return new PreparedOpening(selectedReward, animation, base, caseBlockLocation, worldId, reservedBatch);
        }
    }

    private record PendingRecovery(Reward reward, PendingBatch batch) { }

    private static final class CostReceipt {
        private final String keyId;
        private final boolean batch;
        private final AtomicBoolean refunded = new AtomicBoolean();

        private CostReceipt(String keyId, boolean batch) {
            this.keyId = keyId;
            this.batch = batch;
        }
    }

    private static final class ActiveOpening {
        private final UUID playerId;
        private final Player player;
        private final CaseDefinition definition;
        private final String caseName;
        private final Reward reward;
        private final PendingBatch batch;
        private final CaseBlockKey sourceBlock;
        private final Location caseBlockLocation;
        private final UUID worldId;
        private final AtomicBoolean completed = new AtomicBoolean();
        private volatile BukkitTask watchdog;
        private volatile AnimationSession animationRun;
        private volatile boolean pendingStored;
        private volatile boolean hologramHidden;

        private ActiveOpening(
                UUID playerId,
                Player player,
                CaseDefinition definition,
                Reward reward,
                CaseBlockKey sourceBlock,
                Location caseBlockLocation,
                UUID worldId,
                PendingBatch batch
        ) {
            this.playerId = playerId;
            this.player = player;
            this.definition = definition;
            this.caseName = definition.name();
            this.reward = reward;
            this.batch = batch;
            this.sourceBlock = sourceBlock;
            this.caseBlockLocation = caseBlockLocation;
            this.worldId = worldId;
        }
    }
}
