package ru.privatenull.pncases.cases.opening;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Owns player-opening, selected-block and physical-case lock state. */
public final class CaseOpeningLocks {

    private final Set<UUID> openingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<CaseBlockKey, UUID> busyCases = new ConcurrentHashMap<>();
    private final Map<UUID, CaseBlockKey> selectedBlocks = new ConcurrentHashMap<>();

    public boolean isOpening(UUID playerId) {
        return openingPlayers.contains(playerId);
    }

    public void markOpening(UUID playerId) {
        openingPlayers.add(playerId);
    }

    public void unmarkOpening(UUID playerId) {
        openingPlayers.remove(playerId);
    }

    public CaseBlockKey selected(UUID playerId) {
        return selectedBlocks.get(playerId);
    }

    public void select(UUID playerId, CaseBlockKey block) {
        if (block == null) selectedBlocks.remove(playerId);
        else selectedBlocks.put(playerId, block);
    }

    public boolean clearSelection(UUID playerId, CaseBlockKey expected) {
        return expected == null ? selectedBlocks.remove(playerId) != null
                : selectedBlocks.remove(playerId, expected);
    }

    public CaseBlockKey clearSelection(UUID playerId) {
        return selectedBlocks.remove(playerId);
    }

    public boolean tryLock(UUID playerId, CaseBlockKey block) {
        if (playerId == null || block == null) return false;
        UUID previous = busyCases.putIfAbsent(block, playerId);
        return previous == null || previous.equals(playerId);
    }

    public void unlock(UUID playerId, CaseBlockKey block) {
        if (playerId != null && block != null) busyCases.remove(block, playerId);
    }

    public boolean isBusy(CaseBlockKey block, UUID viewer) {
        if (block == null) return false;
        UUID owner = busyCases.get(block);
        return owner != null && !owner.equals(viewer);
    }

    public boolean isBusy(CaseBlockKey block) {
        return block != null && busyCases.containsKey(block);
    }

    public void release(UUID playerId, CaseBlockKey block) {
        openingPlayers.remove(playerId);
        if (block != null) {
            selectedBlocks.remove(playerId, block);
            unlock(playerId, block);
        } else {
            selectedBlocks.remove(playerId);
        }
    }

    public void clear() {
        openingPlayers.clear();
        busyCases.clear();
        selectedBlocks.clear();
    }
}
