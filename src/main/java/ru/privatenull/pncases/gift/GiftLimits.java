package ru.privatenull.pncases.gift;

import ru.privatenull.pncases.integration.Log;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.privatenull.pncases.PnCasesPlugin;
import ru.privatenull.pncases.cases.config.CaseConfigRepository;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Limits on gifting keys, set in {@code key-gifts.limits} of the main config.
 *
 * <p>There is a default limit for everyone and any number of rules tied to a permission, for example a LuckPerms
 * group ({@code group.donate}). A player gets the most generous of the rules they match, or the default when they
 * match none. Each limit has two numbers: keys per single gift and keys per day. {@code -1} (or a missing value)
 * means no limit. What was sent today is remembered in {@code gift-usage.yml}, so it survives restarts.</p>
 */
public final class GiftLimits {

    private static final String DEFAULT_PATH = "key-gifts.limits.default";
    private static final String RULES_PATH = "key-gifts.limits.rules.";
    /** What a rule added from the Machine starts with; the same numbers as the example config. */
    public static final Limit NEW_RULE_LIMIT = new Limit(10, 30);

    /** The two numbers of a limit; a negative number means unlimited. */
    public record Limit(int perGift, int perDay) {
        public static final Limit UNLIMITED = new Limit(-1, -1);

        public boolean perGiftLimited() {
            return perGift >= 0;
        }

        public boolean perDayLimited() {
            return perDay >= 0;
        }
    }

    /** A limit that applies to players holding a permission. */
    public record Rule(String id, String permission, Limit limit) {
    }

    private record Usage(String date, int sent) {
    }

    private final PnCasesPlugin plugin;
    private final File file;
    private final Map<UUID, Usage> usage = new HashMap<>();
    private Limit defaultLimit = Limit.UNLIMITED;
    private List<Rule> rules = List.of();

    public GiftLimits(PnCasesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "gift-usage.yml");
        load();
        loadUsage();
    }

    /** Reads the limits from the config again (the daily counters are kept). */
    public synchronized void load() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("key-gifts.limits");
        defaultLimit = Limit.UNLIMITED;
        List<Rule> loaded = new ArrayList<>();
        if (section != null) {
            ConfigurationSection def = section.getConfigurationSection("default");
            if (def != null) defaultLimit = new Limit(def.getInt("per-gift", -1), def.getInt("per-day", -1));
            ConfigurationSection rulesSection = section.getConfigurationSection("rules");
            if (rulesSection != null) {
                for (String id : rulesSection.getKeys(false)) {
                    ConfigurationSection rule = rulesSection.getConfigurationSection(id);
                    if (rule == null) continue;
                    String permission = rule.getString("permission", "");
                    if (permission == null || permission.isBlank()) continue;
                    loaded.add(new Rule(id, permission.trim(),
                            new Limit(rule.getInt("per-gift", -1), rule.getInt("per-day", -1))));
                }
            }
        }
        rules = List.copyOf(loaded);
    }

    public Limit defaultLimit() {
        return defaultLimit;
    }

    public List<Rule> rules() {
        return rules;
    }

    /** The limit that applies to this player right now. */
    public Limit limitFor(Player player) {
        List<Limit> matching = new ArrayList<>();
        for (Rule rule : rules) {
            if (player.hasPermission(rule.permission())) matching.add(rule.limit());
        }
        return combine(defaultLimit, matching);
    }

    public synchronized int sentToday(UUID playerId) {
        Usage entry = usage.get(playerId);
        return entry == null || !entry.date().equals(today()) ? 0 : entry.sent();
    }

    /** How many keys this player may still send today; {@code Integer.MAX_VALUE} when there is no daily limit. */
    public int remainingToday(Player player) {
        Limit limit = limitFor(player);
        return limit.perDayLimited() ? Math.max(0, limit.perDay() - sentToday(player.getUniqueId())) : Integer.MAX_VALUE;
    }

    /** How many of the requested keys this player may send now: capped by both limits. */
    public int allowed(Player player, int requested) {
        return allowed(requested, limitFor(player), sentToday(player.getUniqueId()));
    }

    public synchronized void record(UUID playerId, int amount) {
        if (amount <= 0) return;
        usage.put(playerId, new Usage(today(), sentToday(playerId) + amount));
        saveAsync();
    }

    public synchronized void reset(UUID playerId) {
        usage.remove(playerId);
        saveAsync();
    }

    // ---------------------------------------------------------------------- editing from the Machine

    /** Sets the default limit and writes it to the main config. */
    public synchronized boolean setDefault(Limit limit) {
        return writeConfig(limitValues(DEFAULT_PATH, limit));
    }

    /** Sets the numbers of an existing rule; its permission stays as it is. */
    public synchronized boolean setRule(String id, Limit limit) {
        return writeConfig(limitValues(RULES_PATH + id, limit));
    }

    /** Adds a rule for a permission with the example numbers. Returns its id, or null when it could not be added. */
    public synchronized String addRule(String permission) {
        if (!validPermission(permission)) return null;
        String base = ruleIdFor(permission);
        String id = base;
        for (int suffix = 2; containsRule(id); suffix++) id = base + "-" + suffix;
        Map<String, Object> values = limitValues(RULES_PATH + id, NEW_RULE_LIMIT);
        values.put(RULES_PATH + id + ".permission", permission);
        return writeConfig(values) ? id : null;
    }

    public synchronized boolean removeRule(String id) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(RULES_PATH + id, null);
        return writeConfig(values);
    }

    /** Writes the values to the config and keeps the old ones when the file cannot be saved. */
    private boolean writeConfig(Map<String, Object> values) {
        Map<String, Object> previous = new LinkedHashMap<>();
        values.keySet().forEach(path -> previous.put(path, plugin.getConfig().get(path)));
        values.forEach((path, value) -> plugin.getConfig().set(path, value));
        if (new CaseConfigRepository(plugin).saveMainConfig()) {
            load();
            return true;
        }
        previous.forEach((path, value) -> plugin.getConfig().set(path, value));
        return false;
    }

    private static Map<String, Object> limitValues(String prefix, Limit limit) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(prefix + ".per-gift", limit.perGift());
        values.put(prefix + ".per-day", limit.perDay());
        return values;
    }

    private boolean containsRule(String id) {
        return rules.stream().anyMatch(rule -> rule.id().equals(id));
    }

    /**
     * The id a rule gets from its permission: {@code group.donate} becomes {@code group-donate}. A permission with
     * no letters or digits in it (such as {@code *}) gets the id {@code rule}.
     */
    public static String ruleIdFor(String permission) {
        String id = permission.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("^-+|-+$", "");
        return id.isEmpty() ? "rule" : id;
    }

    /** Any permission name can be used; only blank text and spaces are refused. */
    public static boolean validPermission(String permission) {
        return permission != null && !permission.isBlank() && permission.length() <= 128
                && permission.chars().noneMatch(Character::isWhitespace);
    }

    /** One step of a limit editor: a negative value means "no limit", and stepping below zero keeps it at zero. */
    public static int adjust(int current, int delta) {
        if (current < 0) return delta > 0 ? delta : -1;
        return Math.max(0, current + delta);
    }

    // ---------------------------------------------------------------------- rules

    /** The most generous limit among the matching rules, or the default when none match. */
    public static Limit combine(Limit defaultLimit, List<Limit> matching) {
        if (matching.isEmpty()) return defaultLimit;
        int perGift = matching.get(0).perGift();
        int perDay = matching.get(0).perDay();
        for (Limit limit : matching) {
            perGift = moreGenerous(perGift, limit.perGift());
            perDay = moreGenerous(perDay, limit.perDay());
        }
        return new Limit(perGift, perDay);
    }

    private static int moreGenerous(int a, int b) {
        return a < 0 || b < 0 ? -1 : Math.max(a, b);
    }

    /** The requested amount, cut down to the per-gift limit and to what is left of today's. */
    public static int allowed(int requested, Limit limit, int sentToday) {
        int allowed = requested;
        if (limit.perGiftLimited()) allowed = Math.min(allowed, limit.perGift());
        if (limit.perDayLimited()) allowed = Math.min(allowed, Math.max(0, limit.perDay() - sentToday));
        return Math.max(0, allowed);
    }

    // ---------------------------------------------------------------------- storage

    private static String today() {
        return LocalDate.now().toString();
    }

    private void loadUsage() {
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                String[] parts = yaml.getString(key, "").split(":");
                if (parts.length == 2) usage.put(UUID.fromString(key), new Usage(parts[0], Integer.parseInt(parts[1])));
            } catch (IllegalArgumentException ignored) {
                // A damaged line is dropped; counters only matter for the current day anyway.
            }
        }
    }

    private void saveAsync() {
        if (!plugin.isEnabled()) {
            save();
            return;
        }
        plugin.tasks().async(this::save);
    }

    private synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        String today = today();
        for (Map.Entry<UUID, Usage> entry : usage.entrySet()) {
            if (entry.getValue().date().equals(today)) {
                yaml.set(entry.getKey().toString(), entry.getValue().date() + ":" + entry.getValue().sent());
            }
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            Log.warning("Не удалось сохранить счётчик подарков ключей.", exception);
        }
    }
}
