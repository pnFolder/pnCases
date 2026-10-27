package ru.privatenull.pncases.gui.machine;

import ru.privatenull.pncases.cases.model.Reward;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Finds what keeps a case from working before a player discovers it. Pure logic, no server access. */
final class CaseHealthCheck {

    enum Severity { ERROR, WARNING }

    record Issue(Severity severity, String text) { }

    /** What the check needs to know about a case and about the plugins installed on the server. */
    record Input(
            int rewardCount,
            int blockCount,
            Set<Reward.Type> rewardTypes,
            boolean vaultAvailable,
            boolean playerPointsAvailable,
            boolean luckPermsAvailable
    ) { }

    private CaseHealthCheck() {
    }

    static List<Issue> evaluate(Input input) {
        List<Issue> issues = new ArrayList<>();
        if (input.blockCount() == 0) {
            issues.add(new Issue(Severity.ERROR,
                    "Кейс не привязан к блоку: игроки не смогут его открыть"));
        }
        if (input.rewardCount() == 0) {
            issues.add(new Issue(Severity.ERROR, "В кейсе нет наград"));
        }
        if (input.rewardTypes().contains(Reward.Type.VAULT) && !input.vaultAvailable()) {
            issues.add(new Issue(Severity.ERROR,
                    "Есть награды-монеты, но Vault или экономика не найдены: они не будут выданы"));
        }
        if (input.rewardTypes().contains(Reward.Type.PLAYERPOINTS) && !input.playerPointsAvailable()) {
            issues.add(new Issue(Severity.ERROR,
                    "Есть награды-поинты, но PlayerPoints не найден: они не будут выданы"));
        }
        if (input.rewardTypes().contains(Reward.Type.LUCKPERMS) && !input.luckPermsAvailable()) {
            issues.add(new Issue(Severity.ERROR,
                    "Есть награды LuckPerms, но плагин не найден: они не будут выданы"));
        }
        return List.copyOf(issues);
    }

    enum Status { READY, WARNINGS, ERRORS }

    static Status status(List<Issue> issues) {
        if (hasErrors(issues)) return Status.ERRORS;
        return issues.isEmpty() ? Status.READY : Status.WARNINGS;
    }

    static boolean hasErrors(List<Issue> issues) {
        return issues.stream().anyMatch(issue -> issue.severity() == Severity.ERROR);
    }
}
