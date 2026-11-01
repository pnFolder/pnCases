package ru.privatenull.pncases.integration;

import java.util.UUID;

/**
 * Live values pnCases hands to pnLibrary: PlaceholderAPI placeholders and the support report.
 * Methods may be called from any thread and must answer immediately.
 */
public interface RuntimeData {

    /** Key balance of a player for a case, as text. Unknown values are "0". */
    String keys(UUID playerId, String caseId);

    /** Number of loaded cases. */
    int caseCount();

    /** Whether the player is currently opening a case. */
    boolean opening(UUID playerId);

    String version();

    /** Summary for the /pn debug support report. Must not contain secrets. */
    java.util.Map<String, Object> diagnostics();
}
