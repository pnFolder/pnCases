package ru.privatenull.pncases.storage;

import ru.privatenull.pncases.cases.model.AnimationType;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pnlibrary.database.DatabaseType;
import ru.privatenull.pnlibrary.item.ItemStackCodec;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface CasesDatabase extends AutoCloseable {
    DatabaseType type();

    void initialize() throws Exception;

    void addKeys(UUID uuid, String keyId, int amount);

    int getKeys(UUID uuid, String keyId);

    boolean takeKeys(UUID uuid, String keyId, int amount);

    Map<String, Integer> getAllKeys(UUID uuid);

    AnimationType getAnimation(UUID uuid, String caseId);

    void setAnimation(UUID uuid, String caseId, AnimationType type);

    void addHistory(String caseName, String playerName, String rewardName, long openedAt);

    List<HistoryEntry> getHistory(String caseName, int limit);

    void savePending(UUID uuid, Reward reward);

    Reward loadPending(UUID uuid);

    void clearPending(UUID uuid);

    Set<UUID> getPendingPlayers();

    /**
     * Reserves one key and persists the selected reward before an animation starts.
     * Calls for the same player are serialized by DatabaseExecutor.
     */
    default OpeningReservation reserveOpening(UUID uuid, String keyId, Reward reward) {
        Reward pending = loadPending(uuid);
        if (pending != null) return OpeningReservation.pending(pending);
        int balance = getKeys(uuid, keyId);
        if (balance < 1) return OpeningReservation.insufficient(balance);
        if (!takeKeys(uuid, keyId, 1)) return OpeningReservation.insufficient(getKeys(uuid, keyId));
        try {
            savePending(uuid, reward);
            return OpeningReservation.success();
        } catch (RuntimeException failure) {
            addKeys(uuid, keyId, 1);
            throw failure;
        }
    }

    /** Removes a not-yet-delivered reservation before returning its key. */
    default void cancelOpening(UUID uuid, String keyId) {
        clearPending(uuid);
        addKeys(uuid, keyId, 1);
    }

    static String encodeItem(ItemStack item) {
        return ItemStackCodec.encode(item);
    }

    static ItemStack decodeItem(String value) {
        return ItemStackCodec.decode(value);
    }

    @Override
    void close();

    record HistoryEntry(String playerName, String rewardName, long openedAt) {
    }

    record OpeningReservation(boolean reserved, Reward pendingReward, int balance) {
        public static OpeningReservation success() {
            return new OpeningReservation(true, null, 0);
        }

        public static OpeningReservation pending(Reward reward) {
            return new OpeningReservation(false, reward, 0);
        }

        public static OpeningReservation insufficient(int balance) {
            return new OpeningReservation(false, null, Math.max(0, balance));
        }
    }
}
