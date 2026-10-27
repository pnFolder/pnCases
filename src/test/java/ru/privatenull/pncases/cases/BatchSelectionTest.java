package ru.privatenull.pncases.cases;

import java.util.List;
import org.junit.jupiter.api.Test;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.CaseGuiLayout;
import ru.privatenull.pncases.cases.model.GuaranteeSettings;
import ru.privatenull.pncases.cases.model.Reward;

import static org.junit.jupiter.api.Assertions.*;

class BatchSelectionTest {
    private final Reward common = reward("common", Reward.Rarity.COMMON);
    private final Reward rare = reward("legendary", Reward.Rarity.LEGENDARY);

    @Test
    void guaranteeIsAppliedToEveryOpeningWithinTheBatch() {
        var definition = definition(new GuaranteeSettings(true, 3, Reward.Rarity.LEGENDARY, false));
        var batch = CaseManager.selectBatch(definition, 2, 10);
        assertEquals(10, batch.rewards().size());
        for (int index : List.of(0, 3, 6, 9)) assertSame(rare, batch.rewards().get(index));
        assertEquals(0, batch.guaranteeProgress());
    }

    @Test
    void disabledGuaranteeDoesNotWriteProgress() {
        var batch = CaseManager.selectBatch(definition(GuaranteeSettings.disabled()), 0, 10);
        assertEquals(-1, batch.guaranteeProgress());
        assertEquals(10, batch.rewards().size());
        assertTrue(batch.rewards().stream().allMatch(reward -> reward == common || reward == rare));
    }

    private CaseDefinition definition(GuaranteeSettings guarantee) {
        CaseGuiLayout layout = new CaseGuiLayout(54, 22, 49, false, List.of(), null, List.of(), null);
        return new CaseDefinition("test", "Test", List.of(), "Test", null, layout, null,
                0, null, List.of(), false, List.of(), guarantee, List.of(common, rare));
    }

    private static Reward reward(String name, Reward.Rarity rarity) {
        return new Reward(10, Reward.Type.PERSONAL, null, null, null, null, null, name, rarity);
    }
}
