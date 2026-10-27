package ru.privatenull.pncases.cases.opening;

import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.GuaranteeSettings;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pncases.storage.CasesDatabase;
import ru.privatenull.pncases.storage.PendingBatch;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Selects rewards and atomically reserves their durable delivery state. */
public final class OpeningReservationService {

    private final CasesDatabase database;
    private final Logger logger;

    public OpeningReservationService(CasesDatabase database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    public OpeningReservation reserve(UUID playerId, CaseDefinition definition, int amount) {
        if (amount > 1) {
            int progress = definition.guarantee().enabled()
                    ? database.getGuaranteeProgress(playerId, definition.name()) : 0;
            PendingBatch batch = selectBatch(definition, progress, amount);
            CasesDatabase.OpeningReservation storage = database.reserveBatch(playerId, batch);
            return new OpeningReservation(storage, storage.reserved() ? batch.rewards().get(0) : null,
                    storage.reserved() ? batch : null);
        }

        Reward selected = selectReward(playerId, definition);
        CasesDatabase.OpeningReservation storage = database.reserveOpening(playerId, definition.name(), selected);
        if (storage.reserved()) updateGuaranteeProgress(playerId, definition, selected);
        return new OpeningReservation(storage, storage.reserved() ? selected : null, null);
    }

    public static PendingBatch selectBatch(CaseDefinition definition, int progress, int amount) {
        GuaranteeSettings guarantee = definition.guarantee();
        List<Reward> eligible = definition.rewards().stream().filter(guarantee::qualifies).toList();
        List<Reward> rewards = new ArrayList<>();
        int current = progress;
        for (int index = 0; index < amount; index++) {
            boolean forced = guarantee.enabled() && !eligible.isEmpty() && current + 1 >= guarantee.openings();
            Reward reward = Reward.select(forced ? eligible : definition.rewards());
            rewards.add(reward);
            if (guarantee.enabled()) {
                current = forced || (guarantee.resetAfterWin() && guarantee.qualifies(reward))
                        ? 0 : Math.min(guarantee.openings() - 1, current + 1);
            }
        }
        return new PendingBatch(UUID.randomUUID(), definition.name(), rewards, 0,
                guarantee.enabled() ? current : -1);
    }

    private Reward selectReward(UUID playerId, CaseDefinition definition) {
        GuaranteeSettings guarantee = definition.guarantee();
        if (!guarantee.enabled()) return Reward.select(definition.rewards());
        int progress = database.getGuaranteeProgress(playerId, definition.name());
        if (progress + 1 < guarantee.openings()) return Reward.select(definition.rewards());
        List<Reward> eligible = definition.rewards().stream().filter(guarantee::qualifies).toList();
        if (!eligible.isEmpty()) return Reward.select(eligible);
        logger.warning("Гарант кейса '" + definition.name() + "' не нашёл наград редкости "
                + guarantee.minimumRarity() + " или выше; используется обычный выбор.");
        return Reward.select(definition.rewards());
    }

    private void updateGuaranteeProgress(UUID playerId, CaseDefinition definition, Reward selected) {
        GuaranteeSettings guarantee = definition.guarantee();
        if (!guarantee.enabled()) return;
        try {
            int current = database.getGuaranteeProgress(playerId, definition.name());
            boolean hasGuaranteedReward = definition.rewards().stream().anyMatch(guarantee::qualifies);
            boolean forcedOpening = hasGuaranteedReward && current + 1 >= guarantee.openings();
            int next = forcedOpening || (guarantee.resetAfterWin() && guarantee.qualifies(selected))
                    ? 0 : Math.min(guarantee.openings() - 1, current + 1);
            database.setGuaranteeProgress(playerId, definition.name(), next);
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING,
                    "Награда зарезервирована, но прогресс гаранта кейса '" + definition.name()
                            + "' не удалось сохранить для " + playerId + ".", failure);
        }
    }
}
