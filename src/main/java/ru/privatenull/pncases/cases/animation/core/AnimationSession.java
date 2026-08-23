package ru.privatenull.pncases.cases.animation.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;
import ru.privatenull.pncases.PnCasesPlugin;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pncases.cases.animation.effect.DisplayAnimator;
import ru.privatenull.pncases.hologram.CaseHologramManager;
import ru.privatenull.pnlibrary.compat.ServerCapabilities;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Owns the complete lifecycle of one animation invocation.
 *
 * <p>Every entity, task, listener and temporary block registered in this scope
 * is released exactly once on normal completion, external cancellation, world
 * unload or plugin shutdown. Concrete animations only describe their scenario;
 * they do not own global cleanup rules.</p>
 */
public final class AnimationSession {

    private final PnCasesPlugin plugin;
    private final Player player;
    private final CaseDefinition definition;
    private final Reward reward;
    private final Location base;
    private final World world;
    private final Runnable externalCompletion;
    private final Runnable completion = this::complete;
    private final Set<Entity> entities = ConcurrentHashMap.newKeySet();
    private final Set<BukkitTask> tasks = ConcurrentHashMap.newKeySet();
    private final Set<Listener> listeners = ConcurrentHashMap.newKeySet();
    private final Set<HiddenBlock> hiddenBlocks = ConcurrentHashMap.newKeySet();
    private final Set<Runnable> closeHooks = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    public AnimationSession(PnCasesPlugin plugin, Player player, CaseDefinition definition,
                        Reward reward, Location base, Runnable externalCompletion) {
        this.plugin = plugin;
        this.player = player;
        this.definition = definition;
        this.reward = reward;
        this.base = base == null ? null : base.clone();
        this.world = base == null ? null : base.getWorld();
        this.externalCompletion = externalCompletion;
    }

    public Player player() {
        return player;
    }

    public CaseDefinition definition() {
        return definition;
    }

    public Reward reward() {
        return reward;
    }

    public Location base() {
        return base == null ? null : base.clone();
    }

    public World world() {
        return world;
    }

    public CaseHologramManager holograms() {
        return plugin.getHolograms();
    }

    public PnCasesPlugin plugin() {
        return plugin;
    }

    /** Stable callback passed into old-style scenario bodies and used as run identity. */
    public Runnable completion() {
        return completion;
    }

    public boolean isClosed() {
        return closed.get();
    }

    public <T extends Entity> T track(T entity) {
        if (entity == null) return null;
        if (ServerCapabilities.hasDisplayEntities()) DisplayAnimator.prepare(entity);
        if (closed.get()) {
            removeEntity(entity);
        } else {
            entities.add(entity);
            if (closed.get() && entities.remove(entity)) removeEntity(entity);
        }
        return entity;
    }

    public <T extends BukkitTask> T track(T task) {
        if (task == null) return null;
        if (closed.get()) {
            safeCancel(task);
        } else {
            tasks.add(task);
            if (closed.get() && tasks.remove(task)) safeCancel(task);
        }
        return task;
    }

    public <T extends Listener> T track(T listener) {
        if (listener == null) return null;
        if (closed.get()) {
            HandlerList.unregisterAll(listener);
        } else {
            listeners.add(listener);
            if (closed.get() && listeners.remove(listener)) HandlerList.unregisterAll(listener);
        }
        return listener;
    }

    public void untrack(Entity entity) {
        if (entity != null) entities.remove(entity);
    }

    public void untrack(BukkitTask task) {
        if (task != null) tasks.remove(task);
    }

    /**
     * Temporarily replaces a block and restores its full original BlockState
     * automatically when this run closes.
     */
    public boolean hide(Block block, Material disguise) {
        HiddenBlock hidden = HiddenBlock.disguise(block, disguise);
        if (hidden != null) {
            if (closed.get()) hidden.restore();
            else {
                hiddenBlocks.add(hidden);
                if (closed.get() && hiddenBlocks.remove(hidden)) hidden.restore();
            }
        }
        return hidden != null;
    }

    /** Registers animation-specific state cleanup, such as removing a session map entry. */
    public void onClose(Runnable hook) {
        if (hook == null) return;
        if (closed.get()) {
            safeRun(hook, "animation close hook");
        } else {
            closeHooks.add(hook);
            if (closed.get() && closeHooks.remove(hook)) safeRun(hook, "animation close hook");
        }
    }

    /** Normal completion: cleanup first, then invoke the CaseManager callback once. */
    public void complete() {
        close(true);
    }

    /** External cancellation: cleanup without invoking the reward completion callback. */
    public void cancel() {
        close(false);
    }

    /** Completes a reserved opening after an unexpected visual-stage failure. */
    public void fail(String stage, Throwable failure) {
        String caseName = definition == null ? "unknown" : definition.name();
        String playerName = player == null ? "unknown" : player.getName();
        plugin.getLogger().log(Level.SEVERE,
                "Анимация кейса '" + caseName + "' аварийно завершена на этапе '"
                        + stage + "' для игрока " + playerName
                        + ". Награда будет выдана безопасно.", failure);
        complete();
    }

    private void close(boolean notifyCompletion) {
        if (!closed.compareAndSet(false, true)) return;

        for (BukkitTask task : tasks) safeCancel(task);
        tasks.clear();

        for (Listener listener : listeners) HandlerList.unregisterAll(listener);
        listeners.clear();

        for (Entity entity : entities) removeEntity(entity);
        entities.clear();

        for (HiddenBlock hidden : hiddenBlocks) hidden.restore();
        hiddenBlocks.clear();

        for (Runnable hook : closeHooks) safeRun(hook, "animation close hook");
        closeHooks.clear();

        if (notifyCompletion && externalCompletion != null) {
            safeRun(externalCompletion, "animation completion callback");
        }
    }

    private void safeCancel(BukkitTask task) {
        try {
            task.cancel();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.FINE, "Could not cancel animation task", exception);
        }
    }

    private void safeRun(Runnable action, String operation) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not execute " + operation, exception);
        }
    }

    public static void removeEntity(Entity entity) {
        if (entity == null || entity.isDead()) return;
        try {
            entity.remove();
        } catch (RuntimeException exception) {
            Bukkit.getLogger().log(Level.FINE, "pnCases could not remove animation entity", exception);
        }
    }

    private record HiddenBlock(Block block, BlockState original, Material disguise) {

        private static HiddenBlock disguise(Block block, Material disguise) {
            if (block == null || block.getType().isAir() || disguise == null) return null;
            HiddenBlock hidden = new HiddenBlock(block, block.getState(), disguise);
            block.setType(disguise, false);
            return hidden;
        }

        private void restore() {
            if (block == null || original == null) return;
            if (block.getType() != disguise && !block.getType().isAir()) return;
            original.update(true, false);
        }
    }
}
