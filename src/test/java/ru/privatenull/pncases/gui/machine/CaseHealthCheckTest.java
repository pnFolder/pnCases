package ru.privatenull.pncases.gui.machine;

import org.junit.jupiter.api.Test;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pncases.gui.machine.CaseHealthCheck.Input;
import ru.privatenull.pncases.gui.machine.CaseHealthCheck.Issue;
import ru.privatenull.pncases.gui.machine.CaseHealthCheck.Severity;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseHealthCheckTest {

    private static Input input(int rewards, int blocks, Set<Reward.Type> types,
                               boolean vault, boolean points, boolean luckPerms) {
        return new Input(rewards, blocks, types, vault, points, luckPerms);
    }

    @Test
    void aReadyCaseHasNoIssues() {
        List<Issue> issues = CaseHealthCheck.evaluate(
                input(5, 1, EnumSet.of(Reward.Type.ITEM, Reward.Type.VAULT), true, true, true));

        assertTrue(issues.isEmpty(), issues.toString());
    }

    @Test
    void aCaseWithoutABlockIsAnError() {
        List<Issue> issues = CaseHealthCheck.evaluate(input(5, 0, EnumSet.of(Reward.Type.ITEM), true, true, true));

        assertEquals(1, issues.size());
        assertEquals(Severity.ERROR, issues.get(0).severity());
        assertTrue(issues.get(0).text().contains("не привязан"));
    }

    @Test
    void aSingleRewardIsNotAProblem() {
        List<Issue> issues = CaseHealthCheck.evaluate(input(1, 1, EnumSet.of(Reward.Type.ITEM), true, true, true));

        assertTrue(issues.isEmpty(), issues.toString());
        assertFalse(CaseHealthCheck.hasErrors(issues));
    }

    @Test
    void aCaseWithoutRewardsIsAnError() {
        List<Issue> issues = CaseHealthCheck.evaluate(input(0, 1, EnumSet.noneOf(Reward.Type.class), true, true, true));

        assertEquals(1, issues.size());
        assertEquals(Severity.ERROR, issues.get(0).severity());
    }

    @Test
    void rewardsThatNeedAMissingPluginAreErrors() {
        List<Issue> issues = CaseHealthCheck.evaluate(input(4, 1,
                EnumSet.of(Reward.Type.VAULT, Reward.Type.PLAYERPOINTS, Reward.Type.LUCKPERMS), false, false, false));

        assertEquals(3, issues.size());
        assertTrue(CaseHealthCheck.hasErrors(issues));
        assertTrue(issues.stream().anyMatch(issue -> issue.text().contains("Vault")));
        assertTrue(issues.stream().anyMatch(issue -> issue.text().contains("PlayerPoints")));
        assertTrue(issues.stream().anyMatch(issue -> issue.text().contains("LuckPerms")));
    }

    @Test
    void aMissingPluginIsIgnoredWhenNoRewardUsesIt() {
        List<Issue> issues = CaseHealthCheck.evaluate(input(3, 1, EnumSet.of(Reward.Type.ITEM), false, false, false));

        assertTrue(issues.isEmpty(), issues.toString());
    }

    @Test
    void severalProblemsAreReportedTogether() {
        List<Issue> issues = CaseHealthCheck.evaluate(input(1, 0, EnumSet.of(Reward.Type.VAULT), false, true, true));

        assertEquals(2, issues.size());
    }

    @Test
    void theStatusSummarisesTheIssues() {
        assertEquals(CaseHealthCheck.Status.READY, CaseHealthCheck.status(List.of()));
        assertEquals(CaseHealthCheck.Status.READY,
                CaseHealthCheck.status(CaseHealthCheck.evaluate(input(1, 1, EnumSet.of(Reward.Type.ITEM), true, true, true))));
        assertEquals(CaseHealthCheck.Status.ERRORS,
                CaseHealthCheck.status(CaseHealthCheck.evaluate(input(3, 0, EnumSet.of(Reward.Type.ITEM), true, true, true))));
    }
}