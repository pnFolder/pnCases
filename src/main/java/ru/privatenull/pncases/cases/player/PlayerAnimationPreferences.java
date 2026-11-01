package ru.privatenull.pncases.cases.player;

import ru.privatenull.pncases.integration.Log;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.privatenull.pncases.PnCasesPlugin;
import ru.privatenull.pncases.cases.config.CaseConfigRepository;
import ru.privatenull.pncases.cases.model.AnimationType;
import ru.privatenull.pncases.storage.CasesDatabase;
import ru.privatenull.pncases.support.compat.ServerVersion;
import ru.privatenull.pncases.support.database.DatabaseExecutor;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/** Owns cached and persisted per-player animation choices. */
public final class PlayerAnimationPreferences {

    private final PnCasesPlugin plugin;
    private final CasesDatabase database;
    private final DatabaseExecutor executor;
    private final Map<PreferenceKey, AnimationType> selected = new ConcurrentHashMap<>();
    private final Map<PreferenceKey, Set<AnimationType>> favorites = new ConcurrentHashMap<>();

    public PlayerAnimationPreferences(PnCasesPlugin plugin, CasesDatabase database, DatabaseExecutor executor) {
        this.plugin = plugin;
        this.database = database;
        this.executor = executor;
    }

    public AnimationType get(UUID playerId, String caseId) {
        if (legacyOnly()) return AnimationType.FORTUNE_RING;
        return selected.getOrDefault(new PreferenceKey(playerId, caseId), AnimationType.ANVIL);
    }

    public void set(UUID playerId, String caseId, AnimationType type) {
        if (type == null) return;
        AnimationType effective = legacyOnly() ? AnimationType.FORTUNE_RING : type;
        PreferenceKey key = new PreferenceKey(playerId, caseId);
        selected.put(key, effective);
        executor.completeSync(
                executor.run(playerId, () -> database.setAnimation(playerId, key.caseId(), effective)),
                ignored -> { },
                failure -> saveFailed(playerId, failure));
    }

    public Set<AnimationType> favorites(UUID playerId, String caseId) {
        if (legacyOnly()) return Set.of();
        return favorites.getOrDefault(new PreferenceKey(playerId, caseId), Set.of());
    }

    public void cache(UUID playerId, String caseId, AnimationType animation, Set<AnimationType> favoriteTypes) {
        PreferenceKey key = new PreferenceKey(playerId, caseId);
        selected.put(key, animation == null ? AnimationType.ANVIL : animation);
        favorites.put(key, immutable(favoriteTypes));
    }

    public boolean toggleFavorite(UUID playerId, String caseId, AnimationType type) {
        if (type == null || legacyOnly()) return false;
        PreferenceKey key = new PreferenceKey(playerId, caseId);
        Set<AnimationType> changed = EnumSet.noneOf(AnimationType.class);
        changed.addAll(favorites(playerId, caseId));
        boolean added = !changed.remove(type);
        if (added) changed.add(type);
        Set<AnimationType> saved = immutable(changed);
        favorites.put(key, saved);
        executor.completeSync(
                executor.run(playerId, () -> database.setFavoriteAnimations(playerId, key.caseId(), saved)),
                ignored -> { },
                failure -> saveFailed(playerId, failure));
        return added;
    }

    public void clearFavorites(UUID playerId, String caseId) {
        PreferenceKey key = new PreferenceKey(playerId, caseId);
        favorites.put(key, Set.of());
        executor.completeSync(
                executor.run(playerId, () -> database.setFavoriteAnimations(playerId, key.caseId(), Set.of())),
                ignored -> { },
                failure -> saveFailed(playerId, failure));
    }

    public AnimationType selectForOpening(UUID playerId, String caseId) {
        Set<AnimationType> choices = favorites(playerId, caseId);
        if (choices.isEmpty()) return get(playerId, caseId);
        int target = ThreadLocalRandom.current().nextInt(choices.size());
        int index = 0;
        for (AnimationType type : choices) {
            if (index++ == target) return type;
        }
        return get(playerId, caseId);
    }

    public void clearPlayer(UUID playerId) {
        selected.keySet().removeIf(key -> key.playerId().equals(playerId));
        favorites.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    public void clear() {
        selected.clear();
        favorites.clear();
    }

    private void saveFailed(UUID playerId, Throwable failure) {
        Log.error("Не удалось сохранить настройки анимации игрока " + playerId + ".", failure);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) player.sendMessage(plugin.getUi().get("animation-save-error"));
    }

    private static boolean legacyOnly() {
        return ServerVersion.current().isReleaseLine(1, 16);
    }

    private static Set<AnimationType> immutable(Set<AnimationType> types) {
        if (types == null || types.isEmpty()) return Set.of();
        return Collections.unmodifiableSet(EnumSet.copyOf(types));
    }

    private record PreferenceKey(UUID playerId, String caseId) {
        private PreferenceKey {
            if (playerId == null) throw new IllegalArgumentException("player id is required");
            caseId = CaseConfigRepository.normalizeName(caseId);
        }
    }
}
