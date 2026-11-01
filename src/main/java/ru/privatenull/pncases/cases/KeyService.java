package ru.privatenull.pncases.cases;

import ru.privatenull.pncases.integration.Log;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.privatenull.pncases.PnCasesPlugin;
import ru.privatenull.pncases.api.event.CaseKeyGiveEvent;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.storage.CasesDatabase;
import ru.privatenull.pncases.support.database.DatabaseExecutor;
import ru.privatenull.pncases.support.text.ColorUtil;

import java.util.Locale;
import java.util.UUID;
import java.util.function.IntConsumer;
import java.util.logging.Level;

/** Virtual keys: one balance per case, stored through the shared database executor. */
public final class KeyService {

    private final PnCasesPlugin plugin;
    private final CaseRegistry registry;
    private final CasesDatabase database;
    private final DatabaseExecutor databaseExecutor;

    public KeyService(PnCasesPlugin plugin, CaseRegistry registry, CasesDatabase database,
                      DatabaseExecutor databaseExecutor) {
        this.plugin = plugin;
        this.registry = registry;
        this.database = database;
        this.databaseExecutor = databaseExecutor;
    }

    /** Adds keys asynchronously and reports the new balance on the main thread. */
    public void give(Player player, String keyId, int amount, IntConsumer balanceCallback) {
        give(player, keyId, amount, balanceCallback, failure -> { });
    }

    /** Same, and {@code onFailure} runs on the main thread when the keys could not be stored. */
    public void give(Player player, String keyId, int amount, IntConsumer balanceCallback,
                     java.util.function.Consumer<Throwable> onFailure) {
        if (amount <= 0) return;
        UUID playerId = player.getUniqueId();
        String normalizedKey = keyId.toLowerCase(Locale.ROOT);
        databaseExecutor.completeSync(
                databaseExecutor.supply(playerId, () -> {
                    database.addKeys(playerId, normalizedKey, amount);
                    return database.getKeys(playerId, normalizedKey);
                }),
                balance -> {
                    balanceCallback.accept(balance);
                    Bukkit.getPluginManager().callEvent(new CaseKeyGiveEvent(player, normalizedKey, amount, balance));
                },
                failure -> {
                    Log.error("Не удалось выдать ключи игроку " + player.getName() + ".", failure);
                    onFailure.accept(failure);
                });
    }

    public String displayName(String keyId) {
        if (keyId == null || keyId.isBlank()) return "";
        CaseDefinition matchingCase = registry.byName(keyId);
        if (matchingCase != null) return ColorUtil.colorize(matchingCase.displayName());
        return "&f" + keyId.replace('_', ' ');
    }
}
