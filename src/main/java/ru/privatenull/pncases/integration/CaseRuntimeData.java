package ru.privatenull.pncases.integration;

import ru.privatenull.pncases.PnCasesPlugin;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.KeyBalanceCache;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Answers placeholders from {@link KeyBalanceCache}, refreshing old balances in the background. */
public final class CaseRuntimeData implements RuntimeData {

    private final PnCasesPlugin plugin;
    private final CaseManager caseManager;
    private final KeyBalanceCache balances;

    public CaseRuntimeData(PnCasesPlugin plugin, CaseManager caseManager) {
        this.plugin = plugin;
        this.caseManager = caseManager;
        this.balances = caseManager.getKeyBalances();
    }

    @Override
    public String keys(UUID playerId, String rawCaseId) {
        if (playerId == null || rawCaseId == null) return "0";
        String caseId = rawCaseId.toLowerCase(Locale.ROOT);
        if (!KeyBalanceCache.isStorableId(caseId)) return "0";
        Integer fresh = balances.fresh(playerId, caseId);
        if (fresh != null) return String.valueOf(fresh);
        refresh(playerId, caseId);
        Integer known = balances.any(playerId, caseId);
        return String.valueOf(known == null ? 0 : known);
    }

    private void refresh(UUID playerId, String caseId) {
        if (!plugin.isEnabled() || !balances.beginLoad(playerId, caseId)) return;
        try {
            plugin.getDatabaseExecutor()
                    .supply(playerId, () -> plugin.getDatabase().getKeys(playerId, caseId))
                    .whenComplete((balance, failure) -> {
                        balances.endLoad(playerId, caseId);
                        if (failure != null) {
                            plugin.getLogger().log(Level.FINE,
                                    "Не удалось обновить баланс ключей для плейсхолдера.", failure);
                        } else if (balance != null) {
                            balances.put(playerId, caseId, balance);
                        }
                    });
        } catch (RuntimeException failure) {
            balances.endLoad(playerId, caseId);
        }
    }

    @Override
    public int caseCount() {
        return caseManager.getLoadedCaseCount();
    }

    @Override
    public boolean opening(UUID playerId) {
        return playerId != null && caseManager.isOpening(playerId);
    }

    @Override
    public String version() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public Map<String, Object> diagnostics() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("version", plugin.getDescription().getVersion());
        report.put("server", plugin.getServer().getVersion());
        report.put("minecraft", plugin.getServer().getBukkitVersion());
        report.put("java", System.getProperty("java.version"));
        report.put("database", plugin.getDatabase() == null ? "none" : plugin.getDatabase().type().name());
        report.put("hologramProvider", plugin.getHolograms() == null ? "none" : plugin.getHolograms().providerName());
        for (String dependency : List.of("ProtocolLib", "Vault", "PlayerPoints", "LuckPerms",
                "FancyHolograms", "DecentHolograms", "PlaceholderAPI", "WorldGuard")) {
            var found = plugin.getServer().getPluginManager().getPlugin(dependency);
            report.put("plugin." + dependency, found == null ? "not installed"
                    : found.getDescription().getVersion() + (found.isEnabled() ? "" : " (disabled)"));
        }
        try {
            List<String> cases = new ArrayList<>();
            for (var definition : caseManager.getCases()) {
                cases.add(definition.name() + ": rewards=" + definition.rewards().size()
                        + ", blocks=" + definition.blockLocations().size());
            }
            report.put("caseCount", cases.size());
            report.put("cases", cases);
        } catch (RuntimeException failure) {
            report.put("cases", "unavailable while cases are reloading");
        }
        return report;
    }
}
