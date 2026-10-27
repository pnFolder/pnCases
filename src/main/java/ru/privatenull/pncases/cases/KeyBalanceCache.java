package ru.privatenull.pncases.cases;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * Last known key balances, readable from any thread. Placeholders use it because they must answer
 * immediately and cannot wait for the database.
 */
public final class KeyBalanceCache {

    private static final Pattern CASE_ID = Pattern.compile("[a-z0-9_-]{1,48}");
    private static final int MAX_CASES_PER_PLAYER = 64;

    private record Entry(int balance, long loadedAt) { }

    private final Map<UUID, Map<String, Entry>> entries = new ConcurrentHashMap<>();
    private final Set<String> loading = ConcurrentHashMap.newKeySet();
    private final LongSupplier clock;
    private final long freshForMillis;

    public KeyBalanceCache(long freshForMillis) {
        this(freshForMillis, System::currentTimeMillis);
    }

    KeyBalanceCache(long freshForMillis, LongSupplier clock) {
        this.freshForMillis = freshForMillis;
        this.clock = clock;
    }

    /** True for an id that may be stored: lowercase letters, digits, '_' and '-'. */
    public static boolean isStorableId(String caseId) {
        return caseId != null && CASE_ID.matcher(caseId).matches();
    }

    public void put(UUID playerId, String caseId, int balance) {
        if (playerId == null || !isStorableId(caseId)) return;
        Map<String, Entry> perPlayer = entries.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>());
        if (perPlayer.size() >= MAX_CASES_PER_PLAYER && !perPlayer.containsKey(caseId)) return;
        perPlayer.put(caseId, new Entry(Math.max(0, balance), clock.getAsLong()));
    }

    /** Balance younger than the freshness window, or null. */
    public Integer fresh(UUID playerId, String caseId) {
        Entry entry = entry(playerId, caseId);
        return entry != null && clock.getAsLong() - entry.loadedAt < freshForMillis ? entry.balance : null;
    }

    /** Last known balance even if it is old, or null when none was ever stored. */
    public Integer any(UUID playerId, String caseId) {
        Entry entry = entry(playerId, caseId);
        return entry == null ? null : entry.balance;
    }

    /** Marks a load as running; false when one is already in flight for the same balance. */
    public boolean beginLoad(UUID playerId, String caseId) {
        return loading.add(playerId + "/" + caseId);
    }

    public void endLoad(UUID playerId, String caseId) {
        loading.remove(playerId + "/" + caseId);
    }

    public void forget(UUID playerId) {
        entries.remove(playerId);
        loading.removeIf(key -> key.startsWith(playerId + "/"));
    }

    public void clear() {
        entries.clear();
        loading.clear();
    }

    private Entry entry(UUID playerId, String caseId) {
        if (playerId == null || caseId == null) return null;
        Map<String, Entry> perPlayer = entries.get(playerId);
        return perPlayer == null ? null : perPlayer.get(caseId);
    }
}
