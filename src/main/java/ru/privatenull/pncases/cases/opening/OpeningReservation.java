package ru.privatenull.pncases.cases.opening;

import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pncases.storage.CasesDatabase;
import ru.privatenull.pncases.storage.PendingBatch;

/** Storage reservation together with the immutable reward selection it owns. */
public record OpeningReservation(
        CasesDatabase.OpeningReservation storage,
        Reward reward,
        PendingBatch batch
) {
}
