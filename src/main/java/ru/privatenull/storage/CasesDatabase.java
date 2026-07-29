package ru.privatenull.storage;

import ru.privatenull.cases.model.AnimationType;
import ru.privatenull.cases.model.Reward;
import ru.privatenull.pnlibrary.database.DatabaseType;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface CasesDatabase extends AutoCloseable {
    DatabaseType type();

    void open(File dataFolder) throws Exception;

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

    static String encodeItem(ItemStack item) {
        if (item == null) return null;
        try {
            return Base64.getEncoder().encodeToString(item.serializeAsBytes());
        } catch (Throwable ignored) {
            return null;
        }
    }

    static ItemStack decodeItem(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(value));
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    void close();

    record HistoryEntry(String playerName, String rewardName, long openedAt) {
    }
}
