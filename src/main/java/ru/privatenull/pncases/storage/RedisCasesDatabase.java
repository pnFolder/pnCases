package ru.privatenull.pncases.storage;

import org.bukkit.inventory.ItemStack;
import redis.clients.jedis.JedisPooled;
import ru.privatenull.pncases.cases.model.AnimationType;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pnlibrary.database.DatabaseType;
import ru.privatenull.pnlibrary.database.RedisDatabaseManager;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Stores all pnCases runtime state in the Redis connection owned by DatabaseRouter. */
public final class RedisCasesDatabase implements CasesDatabase {
    private static final String TAKE_KEYS_SCRIPT = """
            local current = tonumber(redis.call('HGET', KEYS[1], ARGV[1]) or '0')
            local amount = tonumber(ARGV[2])
            if current < amount then return 0 end
            local remaining = current - amount
            if remaining <= 0 then
              redis.call('HDEL', KEYS[1], ARGV[1])
            else
              redis.call('HSET', KEYS[1], ARGV[1], remaining)
            end
            return 1
            """;
    private static final String RESERVE_OPENING_SCRIPT = """
            if redis.call('EXISTS', KEYS[2]) == 1 then return {-1, 0} end
            local current = tonumber(redis.call('HGET', KEYS[1], ARGV[1]) or '0')
            if current < 1 then return {0, current} end
            redis.call('HSET', KEYS[2], unpack(ARGV, 4))
            redis.call('SADD', KEYS[3], ARGV[3])
            local remaining = current - 1
            if remaining <= 0 then redis.call('HDEL', KEYS[1], ARGV[1])
            else redis.call('HSET', KEYS[1], ARGV[1], remaining) end
            return {1, remaining}
            """;
    private static final String CANCEL_OPENING_SCRIPT = """
            if redis.call('EXISTS', KEYS[2]) == 0 then return 0 end
            redis.call('DEL', KEYS[2])
            redis.call('SREM', KEYS[3], ARGV[2])
            return redis.call('HINCRBY', KEYS[1], ARGV[1], 1)
            """;

    private final JedisPooled redis;
    private final String keyPrefix;
    private final String preferencePrefix;
    private final String historyPrefix;
    private final String pendingPrefix;
    private final String pendingPlayers;

    public RedisCasesDatabase(RedisDatabaseManager database) {
        this.redis = database.client();
        this.keyPrefix = database.key("keys:");
        this.preferencePrefix = database.key("preferences:");
        this.historyPrefix = database.key("history:");
        this.pendingPrefix = database.key("pending:");
        this.pendingPlayers = database.key("pending:players");
    }

    @Override
    public DatabaseType type() {
        return DatabaseType.REDIS;
    }

    @Override
    public void initialize() {
        // DatabaseRouter has already opened and verified the Redis connection.
    }

    @Override
    public void addKeys(UUID uuid, String keyId, int amount) {
        if (uuid == null || amount <= 0) return;
        redis.hincrBy(keys(uuid), normalize(keyId), amount);
    }

    @Override
    public int getKeys(UUID uuid, String keyId) {
        return integer(redis.hget(keys(uuid), normalize(keyId)), 0);
    }

    @Override
    public boolean takeKeys(UUID uuid, String keyId, int amount) {
        if (uuid == null || amount <= 0) return amount <= 0;
        Object result = redis.eval(TAKE_KEYS_SCRIPT, List.of(keys(uuid)),
                List.of(normalize(keyId), Integer.toString(amount)));
        return result instanceof Number number && number.longValue() == 1L;
    }

    @Override
    public Map<String, Integer> getAllKeys(UUID uuid) {
        Map<String, Integer> result = new LinkedHashMap<>();
        redis.hgetAll(keys(uuid)).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    int amount = integer(entry.getValue(), 0);
                    if (amount > 0) result.put(entry.getKey(), amount);
                });
        return result;
    }

    @Override
    public AnimationType getAnimation(UUID uuid, String caseId) {
        return parseAnimation(redis.hget(preferences(uuid), normalize(caseId)));
    }

    @Override
    public void setAnimation(UUID uuid, String caseId, AnimationType type) {
        if (uuid == null || type == null) return;
        redis.hset(preferences(uuid), normalize(caseId), type.name());
    }

    @Override
    public void addHistory(String caseName, String playerName, String rewardName, long openedAt) {
        String key = history(caseName);
        redis.lpush(key, encodeHistory(playerName, rewardName, openedAt));
        redis.ltrim(key, 0, 8);
    }

    @Override
    public List<HistoryEntry> getHistory(String caseName, int limit) {
        if (limit <= 0) return List.of();
        List<HistoryEntry> result = new ArrayList<>();
        for (String value : redis.lrange(history(caseName), 0, limit - 1L)) {
            HistoryEntry entry = decodeHistory(value);
            if (entry != null) result.add(entry);
        }
        return result;
    }

    @Override
    public void savePending(UUID uuid, Reward reward) {
        if (uuid == null || reward == null) return;
        redis.hset(pending(uuid), rewardValues(reward));
        redis.sadd(pendingPlayers, uuid.toString());
    }

    @Override
    public Reward loadPending(UUID uuid) {
        Map<String, String> values = redis.hgetAll(pending(uuid));
        if (values.isEmpty()) return null;
        ItemStack item = CasesDatabase.decodeItem(blankToNull(values.get("item")));
        return new Reward(
                integer(values.get("chance"), 100),
                parseType(values.get("type")),
                item,
                blankToNull(values.get("lpGroup")),
                blankToNull(values.get("lpNode")),
                blankToNull(values.get("lpDuration")),
                decimal(values.get("vaultAmount"), 0.0D),
                integer(values.get("playerPointsAmount"), 0),
                blankToNull(values.get("message")),
                blankToNull(values.get("displayName")),
                blankToNull(values.get("rarity")),
                null,
                blankToNull(values.get("command")));
    }

    @Override
    public void clearPending(UUID uuid) {
        if (uuid == null) return;
        redis.del(pending(uuid));
        redis.srem(pendingPlayers, uuid.toString());
    }

    @Override
    public Set<UUID> getPendingPlayers() {
        Set<UUID> result = new LinkedHashSet<>();
        for (String value : redis.smembers(pendingPlayers)) {
            try {
                result.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }

    @Override
    public OpeningReservation reserveOpening(UUID uuid, String keyId, Reward reward) {
        if (uuid == null || reward == null) throw new IllegalArgumentException("uuid and reward are required");
        Map<String, String> values = rewardValues(reward);
        List<String> arguments = new ArrayList<>(3 + values.size() * 2);
        arguments.add(normalize(keyId));
        arguments.add("1");
        arguments.add(uuid.toString());
        values.forEach((field, value) -> {
            arguments.add(field);
            arguments.add(value);
        });
        Object raw = redis.eval(RESERVE_OPENING_SCRIPT,
                List.of(keys(uuid), pending(uuid), pendingPlayers), arguments);
        if (!(raw instanceof List<?> result) || result.size() < 2) {
            throw new IllegalStateException("Unexpected Redis reservation response: " + raw);
        }
        long state = ((Number) result.get(0)).longValue();
        if (state > 0) return OpeningReservation.success();
        if (state < 0) return OpeningReservation.pending(loadPending(uuid));
        return OpeningReservation.insufficient(((Number) result.get(1)).intValue());
    }

    @Override
    public void cancelOpening(UUID uuid, String keyId) {
        if (uuid == null) return;
        redis.eval(CANCEL_OPENING_SCRIPT,
                List.of(keys(uuid), pending(uuid), pendingPlayers),
                List.of(normalize(keyId), uuid.toString()));
    }

    @Override
    public void close() {
        // DatabaseRouter owns the shared Redis pool.
    }

    private String keys(UUID uuid) {
        return keyPrefix + uuid;
    }

    private String preferences(UUID uuid) {
        return preferencePrefix + uuid;
    }

    private String history(String caseName) {
        return historyPrefix + normalize(caseName);
    }

    private String pending(UUID uuid) {
        return pendingPrefix + uuid;
    }

    private static Map<String, String> rewardValues(Reward reward) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("type", reward.type().name());
        values.put("chance", Integer.toString(reward.chance()));
        values.put("rarity", value(reward.rarityId()));
        values.put("item", value(CasesDatabase.encodeItem(reward.item())));
        values.put("lpGroup", value(reward.lpGroup()));
        values.put("lpNode", value(reward.lpNode()));
        values.put("lpDuration", value(reward.lpDuration()));
        values.put("vaultAmount", Double.toString(reward.vaultAmount()));
        values.put("playerPointsAmount", Integer.toString(reward.playerPointsAmount()));
        values.put("message", value(reward.message()));
        values.put("displayName", value(reward.displayName()));
        values.put("command", value(reward.command()));
        return values;
    }

    private static String encodeHistory(String playerName, String rewardName, long openedAt) {
        return openedAt + "|" + encode(playerName == null ? "Unknown" : playerName)
                + "|" + encode(rewardName == null ? "Reward" : rewardName);
    }

    private static HistoryEntry decodeHistory(String value) {
        if (value == null) return null;
        String[] parts = value.split("\\|", 3);
        if (parts.length != 3) return null;
        try {
            return new HistoryEntry(decode(parts[1]), decode(parts[2]), Long.parseLong(parts[0]));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double decimal(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static AnimationType parseAnimation(String value) {
        try {
            return AnimationType.valueOf(value == null ? "" : value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return AnimationType.ANVIL;
        }
    }

    private static Reward.Type parseType(String value) {
        try {
            return Reward.Type.valueOf(value == null ? "" : value);
        } catch (IllegalArgumentException ignored) {
            return Reward.Type.ITEM;
        }
    }
}
