package ru.privatenull.pncases.cases.proximity;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitTask;
import ru.privatenull.pncases.PnCasesPlugin;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.ProximitySettings;
import ru.privatenull.pncases.support.compat.ServerVersion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Plays an ambient scene around a case while a player is close to it.
 * The scene grows when someone approaches and fades out after everybody left. The case model itself
 * (the gift, the globe, the crystal) can stay where it is when nobody is near.
 */
public final class ProximityAnimationService {

    static final String DISPLAY_TAG = "pncases_proximity_scene";

    private static final int PRESENCE_CHECK_TICKS = 5;
    /** Players this far beyond the trigger radius still see the scene while it fades, so it never vanishes at once. */
    private static final double FADE_MARGIN = 24.0;
    /** How far past the trigger radius a player must walk before the scene starts to leave. */
    private static final double EXIT_MARGIN = 1.5;
    /** How long the scene stays after the last player stepped out of the radius. */
    private static final int LEAVE_DELAY_TICKS = 60;
    /** The scene leaves this much faster when a case opens than when a player simply walks away. */
    private static final double OPENING_FADE = 3.0;
    /** How far away a player still sees the standing case model. */
    private static final double MODEL_RANGE = 40.0;
    /** The model is removed after nobody was within range for this long. */
    private static final int MODEL_IDLE_TICKS = 200;

    private final PnCasesPlugin plugin;
    private final CaseManager caseManager;
    private final List<Entry> entries = new ArrayList<>();

    private BukkitTask task;
    private long tick;

    /**
     * Clicking a case block makes the server resend the real block to that player, which would show the
     * plain block through the model; the fake blocks around the click are sent again right after.
     */
    private final Listener clicks = new Listener() {
        @EventHandler(priority = EventPriority.MONITOR)
        public void onInteract(PlayerInteractEvent event) {
            Block clicked = event.getClickedBlock();
            if (clicked == null) return;
            Player player = event.getPlayer();
            Location at = clicked.getLocation();
            for (Entry entry : entries) {
                if (!at.getWorld().equals(entry.block.getWorld())) continue;
                if (at.distanceSquared(entry.block) > 12 * 12) continue;
                for (ProximityScene scene : new ProximityScene[]{entry.scene, entry.model}) {
                    if (scene == null) continue;
                    for (long delay : new long[]{1L, 3L, 6L}) {
                        plugin.tasks().later(() -> scene.refresh(player, at), delay);
                    }
                }
            }
        }
    };

    public ProximityAnimationService(PnCasesPlugin plugin, CaseManager caseManager) {
        this.plugin = plugin;
        this.caseManager = caseManager;
        plugin.getServer().getPluginManager().registerEvents(clicks, plugin);
    }

    public void syncCases(Collection<CaseDefinition> definitions) {
        List<Entry> previous = new ArrayList<>(entries);
        entries.clear();
        for (CaseDefinition definition : definitions) {
            addEntries(definition, previous);
        }
        for (Entry old : previous) {
            old.discard();
        }
        for (Entry entry : entries) {
            rebuild(entry);
        }
        refreshTask();
    }

    public void refreshCase(CaseDefinition previous, CaseDefinition updated) {
        List<Entry> old = new ArrayList<>();
        for (Entry entry : new ArrayList<>(entries)) {
            boolean same = (previous != null && entry.caseName.equals(previous.name()))
                    || (updated != null && entry.caseName.equals(updated.name()));
            if (same) {
                old.add(entry);
                entries.remove(entry);
            }
        }
        if (updated != null) addEntries(updated, old);
        for (Entry entry : old) {
            entry.discard();
        }
        for (Entry entry : entries) {
            if (entry.restored) rebuild(entry);
        }
        refreshTask();
    }

    public void shutdown() {
        HandlerList.unregisterAll(clicks);
        for (Entry entry : entries) {
            entry.reset(false);
        }
        entries.clear();
        removeOrphanedDisplays();
        stop();
    }

    public void removeWorldDisplays(World world) {
        if (world == null) return;
        for (Entry entry : new ArrayList<>(entries)) {
            if (world.equals(entry.block.getWorld())) entry.reset(true);
        }
        for (Entity entity : world.getEntities()) {
            if (entity.getScoreboardTags().contains(DISPLAY_TAG)) entity.remove();
        }
    }

    private void refreshTask() {
        if (entries.isEmpty()) {
            stop();
        } else if (task == null || task.isCancelled()) {
            tick = 0L;
            task = plugin.tasks().timer(this::tick, 1L, 1L);
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
            World world = entry.block.getWorld();
            if (world == null) continue;
            double step = 1.0 / Math.max(10.0, entry.settings.fadeSeconds() * 20.0);
            boolean updateNow = (tick + entry.offset) % 2 == 0;

            if ((tick + entry.offset) % PRESENCE_CHECK_TICKS == 0) {
                boolean loaded = world.isChunkLoaded(entry.block.getBlockX() >> 4, entry.block.getBlockZ() >> 4);
                boolean busy = loaded && caseManager.isCaseBlockBusy(entry.block);
                // A player who is already inside has to walk a bit further to leave, so standing on the edge
                // of the radius does not switch the scene back and forth.
                double reach = entry.settings.radius() + (entry.near ? EXIT_MARGIN : 0.0);
                List<Player> trigger = loaded ? playersNear(world, entry, reach) : List.of();
                // The scene fades out for everyone who can still see it, not only for those inside the trigger
                // radius, otherwise the blocks of a player who just stepped out would disappear at once.
                entry.viewers = loaded && entry.progress > 0.0
                        ? playersNear(world, entry, entry.settings.radius() + FADE_MARGIN) : trigger;
                // An opening animation hides the real case block and puts it back afterwards; with
                // "keep during opening" the scene simply carries on and the fake block is resent on top.
                entry.opening = busy && !entry.settings.terrain().keepDuringOpening();
                if (!trigger.isEmpty()) entry.lastNearTick = tick;
                // Stepping out and back in must not make the scene shrink and grow again: it only starts to
                // leave after nobody was near for a while.
                boolean lingering = entry.progress > 0.0 && tick - entry.lastNearTick < LEAVE_DELAY_TICKS;
                entry.near = loaded && (!trigger.isEmpty() || lingering) && !entry.opening;
                if (entry.keepsModel()) {
                    entry.audience = loaded ? playersNear(world, entry, MODEL_RANGE) : List.of();
                    entry.modelWanted = loaded && !entry.audience.isEmpty()
                            && (!busy || entry.settings.terrain().keepDuringOpening());
                    entry.modelIdle = entry.audience.isEmpty() ? entry.modelIdle + PRESENCE_CHECK_TICKS : 0;
                }
            }

            tickScene(entry, world, step, updateNow);
            if (entry.keepsModel()) tickModel(entry, world, step, updateNow);
        }
    }

    private void tickScene(Entry entry, World world, double step, boolean updateNow) {
        double previous = entry.progress;
        double fall = entry.opening ? step * OPENING_FADE : step;
        entry.progress = Math.max(0.0, Math.min(1.0, previous + (entry.near ? step : -fall)));
        if (entry.progress <= 0.0) {
            if (entry.scene != null) {
                entry.scene.clear(false);
                entry.scene = null;
            }
            return;
        }

        if (entry.scene != null && !entry.scene.valid()) {
            entry.scene.clear(true);
            entry.scene = null;
            entry.progress = Math.max(previous, step);
        }
        if (entry.scene == null) {
            entry.scene = createScene(entry.settings, entry.keepsModel() ? SceneMode.DECOR : SceneMode.ALL);
            entry.scene.spawn(world, entry.block.clone());
        }
        if (updateNow) entry.scene.update(entry.progress, entry.near, entry.viewers);
    }

    private void tickModel(Entry entry, World world, double step, boolean updateNow) {
        double previous = entry.modelProgress;
        entry.modelProgress = Math.max(0.0, Math.min(1.0, previous + (entry.modelWanted ? step : -step)));

        boolean gone = entry.modelProgress <= 0.0 && !entry.modelWanted;
        if (gone || entry.modelIdle >= MODEL_IDLE_TICKS) {
            if (entry.model != null) {
                entry.model.clear(entry.modelIdle >= MODEL_IDLE_TICKS);
                entry.model = null;
            }
            return;
        }
        if (entry.model != null && !entry.model.valid()) {
            entry.model.clear(true);
            entry.model = null;
        }
        if (entry.model == null) {
            if (!entry.modelWanted) return;
            entry.model = createScene(entry.settings, SceneMode.MODEL);
            entry.model.spawn(world, entry.block.clone());
        }
        if (updateNow) entry.model.update(entry.modelProgress, entry.modelWanted, entry.audience);
    }

    /** Builds the scenes of an entry at once, at the progress it took over, so a rebuild does not blink. */
    private void rebuild(Entry entry) {
        World world = entry.block.getWorld();
        entry.restored = false;
        if (world == null || !world.isChunkLoaded(entry.block.getBlockX() >> 4, entry.block.getBlockZ() >> 4)) {
            return;
        }
        if (entry.progress > 0.0) {
            entry.scene = createScene(entry.settings, entry.keepsModel() ? SceneMode.DECOR : SceneMode.ALL);
            entry.scene.spawn(world, entry.block.clone());
            entry.scene.snap(entry.progress, entry.near, entry.viewers);
        }
        if (entry.keepsModel() && (entry.modelProgress > 0.0 || entry.modelWanted)) {
            entry.model = createScene(entry.settings, SceneMode.MODEL);
            entry.model.spawn(world, entry.block.clone());
            entry.model.snap(Math.max(entry.modelProgress, 0.0001), true, entry.audience);
        }
    }

    private ProximityScene createScene(ProximitySettings settings, SceneMode mode) {
        return switch (settings.scene()) {
            case WINTER -> new WinterScene(settings, mode);
            case AUTUMN -> new AutumnScene(settings, mode);
        };
    }

    private List<Player> playersNear(World world, Entry entry, double range) {
        double maxSquared = range * range;
        double x = entry.block.getX() + 0.5;
        double y = entry.block.getY() + 0.5;
        double z = entry.block.getZ() + 0.5;
        List<Player> near = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            Location at = player.getLocation();
            double dx = at.getX() - x;
            double dy = at.getY() - y;
            double dz = at.getZ() - z;
            if (dx * dx + dy * dy + dz * dz <= maxSquared) near.add(player);
        }
        return near;
    }

    /**
     * Adds the entries of a case. A rebuilt entry takes over the progress of the entry it replaces, so a
     * settings change swaps the scene in place instead of fading it out and growing it again.
     */
    private void addEntries(CaseDefinition definition, List<Entry> replaced) {
        ProximitySettings settings = definition.proximity();
        if (settings == null || !settings.enabled()) return;
        // The scenes are built from display entities, which exist since 1.19.4.
        if (!ServerVersion.current().isAtLeast(1, 19, 4)) return;
        for (Location location : definition.blockLocations()) {
            if (location == null || location.getWorld() == null) continue;
            Entry entry = new Entry(definition.name(), location.clone(), settings, entries.size());
            for (Entry old : replaced) {
                if (old.caseName.equals(entry.caseName) && old.block.equals(entry.block)) {
                    entry.takeOver(old);
                }
            }
            entries.add(entry);
        }
    }

    private void removeOrphanedDisplays() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(DISPLAY_TAG)) entity.remove();
            }
        }
    }

    private static final class Entry {
        private final String caseName;
        private final Location block;
        private final ProximitySettings settings;
        private final int offset;

        private ProximityScene scene;
        private ProximityScene model;
        private double progress;
        private double modelProgress;
        private boolean near;
        private long lastNearTick;
        private boolean opening;
        private boolean modelWanted;
        private boolean restored;
        private int modelIdle;
        private List<Player> viewers = List.of();
        private List<Player> audience = List.of();

        private Entry(String caseName, Location block, ProximitySettings settings, int offset) {
            this.caseName = caseName;
            this.block = block;
            this.settings = settings;
            this.offset = offset;
        }

        /** The model stands on its own only when it is kept and there is something to show. */
        private boolean keepsModel() {
            return settings.terrain().keepCase()
                    && settings.terrain().caseStyle() != ProximitySettings.CaseStyle.NONE;
        }

        private void takeOver(Entry old) {
            progress = old.progress;
            modelProgress = old.modelProgress;
            near = old.near;
            modelWanted = old.modelWanted;
            viewers = old.viewers;
            audience = old.audience;
            restored = old.progress > 0.0 || old.modelProgress > 0.0;
        }

        /** Removes the old scene entities without any sound; the entry that took over rebuilds them. */
        private void discard() {
            if (scene != null) scene.clear(true);
            if (model != null) model.clear(true);
            scene = null;
            model = null;
        }

        private void reset(boolean quiet) {
            if (scene != null) scene.clear(quiet);
            if (model != null) model.clear(true);
            scene = null;
            model = null;
            progress = 0.0;
            modelProgress = 0.0;
        }
    }
}
