package ru.privatenull.pncases.integration;

import java.util.Map;
import java.util.UUID;

/**
 * Runtime values for pnLibrary that exist before the cases are loaded: pnCases registers with pnLibrary first,
 * so that scheduling is ready for the whole start, and plugs the real data in once it is built.
 */
public final class DeferredRuntimeData implements RuntimeData {

    private final String version;
    private volatile RuntimeData target;

    public DeferredRuntimeData(String version) {
        this.version = version;
    }

    public void set(RuntimeData target) {
        this.target = target;
    }

    @Override
    public String keys(UUID playerId, String caseId) {
        RuntimeData current = target;
        return current == null ? "0" : current.keys(playerId, caseId);
    }

    @Override
    public int caseCount() {
        RuntimeData current = target;
        return current == null ? 0 : current.caseCount();
    }

    @Override
    public boolean opening(UUID playerId) {
        RuntimeData current = target;
        return current != null && current.opening(playerId);
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public Map<String, Object> diagnostics() {
        RuntimeData current = target;
        return current == null ? Map.of("state", "starting") : current.diagnostics();
    }
}
