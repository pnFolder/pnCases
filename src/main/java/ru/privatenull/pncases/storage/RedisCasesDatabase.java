package ru.privatenull.pncases.storage;

import org.bukkit.inventory.ItemStack;
import redis.clients.jedis.JedisPooled;
import ru.privatenull.pncases.cases.model.AnimationType;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pncases.support.database.DatabaseType;
import ru.privatenull.pncases.support.database.RedisDatabaseManager;

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
    private final String favoritePrefix;
    private final String historyPrefix;
    private final String pendingPrefix;
    private final String pendingPlayers;
    private final String guaranteePrefix;
    private final String batchPrefix;
    private final String batchPlayers;

    public RedisCasesDatabase(RedisDatabaseManager database) {
        this.redis = database.client();
        this.keyPrefix = database.key("keys:");
        this.preferencePrefix = database.key("preferences:");
        this.favoritePrefix = database.key("animation-favorites:");
        this.historyPrefix = database.key("history:");
        this.pendingPrefix = database.key("pending:");
        this.pendingPlayers = database.key("pending:players");
        this.guaranteePrefix = database.key("guarantee:");
        this.batchPrefix = database.key("pending-batch:");
        this.batchPlayers = database.key("pending-batch:players");
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
    public boolean transferKeys(UUID sender, UUID receiver, String keyId, int amount) {
        if (sender == null || receiver == null || sender.equals(receiver) || amount <= 0) return false;
        Object result = redis.eval("""
                local source = tonumber(redis.call('HGET', KEYS[1], ARGV[1]) or '0')
                local target = tonumber(redis.call('HGET', KEYS[2], ARGV[1]) or '0')
                local amount = tonumber(ARGV[2])
                if source < amount then return 0 end
                if target + amount > 2147483647 then return redis.error_reply('Receiver key balance would overflow') end
                redis.call('HSET', KEYS[1], ARGV[1], source - amount)
                redis.call('HSET', KEYS[2], ARGV[1], target + amount)
                return 1
                """, List.of(keys(sender), keys(receiver)), List.of(normalize(keyId), Integer.toString(amount)));
        return result instanceof Number number && number.longValue() == 1L;
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
    public Set<AnimationType> getFavoriteAnimations(UUID uuid, String caseId) {
        String value = redis.hget(favorites(uuid), normalize(caseId));
        if (value == null || value.isBlank()) return Set.of();
        return CasesDatabase.parseFavoriteAnimations(List.of(value.split(",")));
    }

    @Override
    public void setFavoriteAnimations(UUID uuid, String caseId, Set<AnimationType> types) {
        String field = normalize(caseId);
        if (types == null || types.isEmpty()) {
            redis.hdel(favorites(uuid), field);
            return;
        }
        String value = types.stream().map(Enum::name).sorted().collect(java.util.stream.Collectors.joining(","));
        redis.hset(favorites(uuid), field, value);
    }

    @Override
    public int getGuaranteeProgress(UUID uuid, String caseId) {
        return integer(redis.hget(guarantee(uuid), normalize(caseId)), 0);
    }

    @Override
    public void setGuaranteeProgress(UUID uuid, String caseId, int openings) {
        if (openings <= 0) {
            redis.hdel(guarantee(uuid), normalize(caseId));
        } else {
            redis.hset(guarantee(uuid), normalize(caseId), Integer.toString(openings));
        }
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
                Reward.Type.fromStored(values.get("type")),
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
                null,
                Reward.deserializeCommands(blankToNull(values.get("command"))));
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
        for (String value : redis.smembers(batchPlayers)) {
            try {
                result.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }

    @Override
    public OpeningReservation reserveOpening(UUID uuid, String keyId, Reward reward) {
        PendingBatch batch = loadBatch(uuid);
        if (batch != null) return OpeningReservation.pending(batch.rewards().get(0));
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
    public PendingBatch loadBatch(UUID uuid) {
        Map<String, String> value = redis.hgetAll(batchPrefix + uuid);
        return value.isEmpty() ? null : PendingBatch.decode(value.get("payload"), integer(value.get("next_index"), 0));
    }

    @Override
    public OpeningReservation reserveBatch(UUID uuid, PendingBatch batch) {
        Object raw = redis.eval("""
                if redis.call('EXISTS', KEYS[2]) == 1 or redis.call('EXISTS', KEYS[3]) == 1 then return {-1, 0} end
                local current = tonumber(redis.call('HGET', KEYS[1], ARGV[1]) or '0')
                local count = tonumber(ARGV[2])
                if current < count then return {0, current} end
                redis.call('HSET', KEYS[3], 'batch_id', ARGV[4], 'payload', ARGV[5], 'next_index', 0, 'item_count', count)
                redis.call('HSET', KEYS[1], ARGV[1], current - count)
                redis.call('SADD', KEYS[4], ARGV[3])
                if tonumber(ARGV[6]) >= 0 then redis.call('HSET', KEYS[5], ARGV[1], ARGV[6]) end
                return {1, current - count}
                """, List.of(keys(uuid), pending(uuid), batchPrefix + uuid, batchPlayers, guarantee(uuid)),
                List.of(normalize(batch.caseId()), String.valueOf(batch.rewards().size()), uuid.toString(),
                        batch.id().toString(), batch.encode(), String.valueOf(batch.guaranteeProgress())));
        if (!(raw instanceof List<?> result) || result.size() < 2) {
            throw new IllegalStateException("Unexpected Redis batch response: " + raw);
        }
        long state = ((Number) result.get(0)).longValue();
        if (state > 0) return OpeningReservation.success();
        if (state < 0) {
            Reward old = loadPending(uuid);
            PendingBatch existing = loadBatch(uuid);
            if (old != null) return OpeningReservation.pending(old);
            if (existing != null) return OpeningReservation.pending(existing.rewards().get(0));
            throw new IllegalStateException("Pending reward changed during batch reservation");
        }
        return OpeningReservation.insufficient(((Number) result.get(1)).intValue());
    }

    @Override
    public void acknowledgeBatchReward(UUID uuid, UUID batchId, int index) {
        redis.eval("""
                if redis.call('HGET', KEYS[1], 'batch_id') ~= ARGV[1] then return 0 end
                local next = tonumber(redis.call('HGET', KEYS[1], 'next_index'))
                if next == tonumber(ARGV[2]) then next = redis.call('HINCRBY', KEYS[1], 'next_index', 1) end
                if next >= tonumber(redis.call('HGET', KEYS[1], 'item_count')) then
                  redis.call('DEL', KEYS[1])
                  redis.call('SREM', KEYS[2], ARGV[3])
                end
                return 1
                """, List.of(batchPrefix + uuid, batchPlayers), List.of(batchId.toString(), String.valueOf(index), uuid.toString()));
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

    private String favorites(UUID uuid) {
        return favoritePrefix + uuid;
    }

    private String pending(UUID uuid) {
        return pendingPrefix + uuid;
    }

    private String guarantee(UUID uuid) {
        return guaranteePrefix + uuid;
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
        values.put("command", Reward.serializeCommands(reward.commands()));
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

}
