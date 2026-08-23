package ru.privatenull.pncases.cases.animation.core;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import ru.privatenull.pncases.PnCasesPlugin;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pnlibrary.text.ColorUtil;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractCaseAnimation {

    private static final float MIN_VOLUME = 0.08f;
    private static final float MAX_VOLUME = 0.22f;

    protected final PnCasesPlugin plugin;

    private final Set<AnimationSession> activeRuns = ConcurrentHashMap.newKeySet();

    protected AbstractCaseAnimation(PnCasesPlugin plugin) {
        this.plugin = plugin;
    }

    /** Schedules and registers an animation task in its owning session. */
    protected final BukkitTask scheduleTask(
            AnimationSession session,
            BukkitRunnable task,
            long delay,
            long period
    ) {
        BukkitTask scheduled = task.runTaskTimer(plugin, delay, period);
        return session.track(scheduled);
    }

    /**
     * Template method shared by every animation implementation. It creates the
     * run scope before any world state is touched and guarantees rollback when
     * startup fails.
     */
    public final AnimationSession play(Player player, CaseDefinition def, Reward reward,
                                   Location base, Runnable onFinish) {
        AnimationSession run = new AnimationSession(plugin, player, def, reward, base, onFinish);
        activeRuns.add(run);
        run.onClose(() -> activeRuns.remove(run));
        try {
            start(run);
        } catch (RuntimeException | LinkageError exception) {
            run.cancel();
            throw exception;
        }
        return run;
    }

    /** Starts the concrete animation scenario inside an already managed scope. */
    protected abstract void start(AnimationSession run);

    public static void playSound(World world, Location location, Sound sound,
                                 float requestedVolume, float pitch) {
        if (world == null || location == null || sound == null) return;
        world.playSound(location, sound, normalizeVolume(requestedVolume), normalizePitch(pitch));
    }

    public static void playSound(Player player, Location location, Sound sound,
                                 float requestedVolume, float pitch) {
        if (player == null || sound == null) return;
        player.playSound(location == null ? player.getLocation() : location,
                sound, normalizeVolume(requestedVolume), normalizePitch(pitch));
    }

    private static float normalizeVolume(float requestedVolume) {
        return Math.max(MIN_VOLUME, Math.min(MAX_VOLUME, requestedVolume));
    }

    private static float normalizePitch(float pitch) {
        return Math.max(0.5f, Math.min(2.0f, pitch));
    }

    /** Cancels only runs owned by the unloading world. */
    public void cancelWorld(World world) {
        if (world == null) return;
        for (AnimationSession run : Set.copyOf(activeRuns)) {
            if (run.world() != null && run.world().getUID().equals(world.getUID())) run.cancel();
        }
    }

    protected ItemStack resolveRewardVisual(Reward reward, CaseDefinition def) {
        if (reward != null && reward.visualItem() != null) {
            return reward.visualItem().clone();
        }

        ItemStack matched = findMatchingAnimationItem(reward, def);
        if (matched != null) {
            return matched;
        }

        return buildFallbackRewardVisual(reward);
    }

    protected String resolveRewardName(Reward reward, ItemStack visual) {
        String name = reward == null ? null : reward.displayName();
        String visualName = getCustomDisplayName(visual);
        if (isGenericLuckPermsName(reward, name) && visualName != null) {
            name = visualName;
        }
        if ((name == null || name.isBlank()) && visual != null) {
            name = visualName;
        }
        if (name == null || name.isBlank()) {
            name = visual != null ? "&f" + visual.getType().name() : "&fНаграда";
        }
        return ColorUtil.colorize(name);
    }

    private ItemStack findMatchingAnimationItem(Reward reward, CaseDefinition def) {
        if (reward == null || def == null || def.animationItems() == null) {
            return null;
        }

        String rewardName = normalizeName(reward.displayName());
        String groupName = normalizeName(reward.lpGroup());
        String nodeName = normalizeName(reward.lpNode());

        for (ItemStack item : def.animationItems()) {
            if (item == null) continue;

            String itemName = normalizeName(getDisplayName(item));
            if (matchesName(itemName, rewardName) || matchesName(itemName, groupName) || matchesName(itemName, nodeName)) {
                return item.clone();
            }
        }

        return null;
    }

    private ItemStack buildFallbackRewardVisual(Reward reward) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(resolveRewardName(reward, null));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String getDisplayName(ItemStack item) {
        String customName = getCustomDisplayName(item);
        if (customName != null) {
            return customName;
        }
        return item.getType().name();
    }

    private String getCustomDisplayName(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return null;
    }

    private boolean isGenericLuckPermsName(Reward reward, String value) {
        if (reward == null || reward.type() != Reward.Type.LUCKPERMS || value == null || value.isBlank()) {
            return false;
        }
        String stripped = ChatColor.stripColor(ColorUtil.colorize(value));
        if (stripped == null) {
            return false;
        }
        String normalized = stripped.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
        return normalized.equals("luckperms") || normalized.equals("привилегия");
    }

    private boolean matchesName(String itemName, String rewardName) {
        if (itemName.length() < 2 || rewardName.length() < 2) {
            return false;
        }
        return itemName.equals(rewardName) || itemName.contains(rewardName) || rewardName.contains(itemName);
    }

    private String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String colored = ColorUtil.colorize(value);
        String stripped = ChatColor.stripColor(colored);
        if (stripped == null) {
            stripped = colored;
        }
        return stripped.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    public void cancelAll() {
        for (AnimationSession run : Set.copyOf(activeRuns)) run.cancel();
        activeRuns.clear();
    }

    /**
     * Permanent plugin shutdown hook. Runtime cancellation (for example a
     * world unload) must leave reusable animation listeners registered.
     */
    public void shutdown() {
        cancelAll();
    }

}
