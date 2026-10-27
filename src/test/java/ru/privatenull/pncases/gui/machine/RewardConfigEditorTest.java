package ru.privatenull.pncases.gui.machine;

import org.junit.jupiter.api.Test;
import ru.privatenull.pncases.cases.model.Reward;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardConfigEditorTest {

    @Test
    void aLoneRewardAlwaysGetsFullWeight() {
        assertEquals(100, RewardConfigEditor.chanceForPercent(0, 7.5));
    }

    @Test
    void weightGivesTheRequestedShareOfThePool() {
        // others weigh 90, so weight 10 is exactly 10% of 100
        assertEquals(10, RewardConfigEditor.chanceForPercent(90, 10.0));
        // 50% needs a weight equal to all the others
        assertEquals(90, RewardConfigEditor.chanceForPercent(90, 50.0));
    }

    @Test
    void weightStaysPositiveAndFiniteAtTheExtremes() {
        assertEquals(1, RewardConfigEditor.chanceForPercent(1, 0.01));
        assertTrue(RewardConfigEditor.chanceForPercent(1_000_000, 100.0) > 0);
    }

    @Test
    void newRewardsAreBuiltFromTypedValues() {
        Map<String, Object> vault = RewardConfigEditor.newReward(Reward.Type.VAULT, "500");
        assertNotNull(vault);
        assertEquals("VAULT", vault.get("type"));
        assertEquals(Map.of("amount", 500.0), vault.get("vault"));

        Map<String, Object> points = RewardConfigEditor.newReward(Reward.Type.PLAYERPOINTS, "25");
        assertEquals(Map.of("amount", 25), points.get("playerpoints"));

        Map<String, Object> command = RewardConfigEditor.newReward(Reward.Type.PERSONAL, "/say hi");
        assertEquals("say hi", command.get("command"));
    }

    @Test
    void invalidTypedValuesAreRejected() {
        assertNull(RewardConfigEditor.newReward(Reward.Type.VAULT, "abc"));
        assertNull(RewardConfigEditor.newReward(Reward.Type.VAULT, "-5"));
        assertNull(RewardConfigEditor.newReward(Reward.Type.PLAYERPOINTS, "1.5"));
        assertNull(RewardConfigEditor.newReward(Reward.Type.PERSONAL, "/"));
        assertNull(RewardConfigEditor.newReward(Reward.Type.ITEM, "anything"));
    }

    @Test
    void inputErrorMatchesWhatNewRewardAccepts() {
        assertNull(RewardConfigEditor.inputError(Reward.Type.VAULT, "12,5"));
        assertEquals("Введите число", RewardConfigEditor.inputError(Reward.Type.VAULT, "x"));
        assertEquals("Введите целое число", RewardConfigEditor.inputError(Reward.Type.PLAYERPOINTS, "0"));
        assertEquals("Только название", RewardConfigEditor.inputError(Reward.Type.LUCKPERMS, "vip plus"));
        assertEquals("Введите значение", RewardConfigEditor.inputError(Reward.Type.PERSONAL, " "));
    }

    @Test
    void changingAVaultAmountKeepsTheRestOfItsSection() {
        Map<String, Object> reward = new LinkedHashMap<>();
        reward.put("vault", new LinkedHashMap<>(Map.of("amount", 1.0, "message", "hello")));

        RewardConfigEditor.applyValue(reward, Reward.Type.VAULT, "250");

        assertEquals(Map.of("amount", 250.0, "message", "hello"), reward.get("vault"));
        assertThrows(IllegalArgumentException.class,
                () -> RewardConfigEditor.applyValue(reward, Reward.Type.VAULT, "zero"));
    }

    @Test
    void changingACommandKeepsTheListFormWhenThereAreSeveralCommands() {
        Map<String, Object> reward = new LinkedHashMap<>();
        reward.put("commands", new ArrayList<>(List.of("first", "second")));

        RewardConfigEditor.applyValue(reward, Reward.Type.PERSONAL, "/changed");

        assertEquals(List.of("changed", "second"), reward.get("commands"));
        assertNull(reward.get("command"));
    }

    @Test
    void luckPermsValueEditsTheNodeWhenOnlyANodeIsConfigured() {
        Map<String, Object> reward = new LinkedHashMap<>();
        reward.put("luckperms", new LinkedHashMap<>(Map.of("node", "old.node")));

        RewardConfigEditor.applyValue(reward, Reward.Type.LUCKPERMS, "new.node");

        assertEquals(Map.of("node", "new.node"), reward.get("luckperms"));
    }

    @Test
    void copiedMapsDoNotShareNestedStateWithTheSource() {
        Map<String, Object> nested = new LinkedHashMap<>(Map.of("amount", 1));
        Map<String, Object> source = new LinkedHashMap<>(Map.of("vault", nested));

        Map<String, Object> copy = RewardConfigEditor.deepMap(source);
        ((Map<String, Object>) copy.get("vault")).put("amount", 99);

        assertEquals(1, nested.get("amount"));
    }

    @Test
    void commandsAreReadFromEitherFormAndStripped() {
        assertEquals(List.of("a", "b"),
                RewardConfigEditor.configuredCommands(Map.of("commands", List.of("/a", " b ", " "))));
        assertEquals(List.of("solo"),
                RewardConfigEditor.configuredCommands(Map.of("command", "/solo")));
    }

    @Test
    void removingTheNestedMessageLeavesTheOtherFields() {
        Map<String, Object> reward = new LinkedHashMap<>();
        reward.put("vault", new LinkedHashMap<>(Map.of("amount", 3.0, "message", "bye")));

        RewardConfigEditor.removeNestedMessage(reward, Reward.Type.VAULT);

        assertEquals(Map.of("amount", 3.0), reward.get("vault"));
    }

    @Test
    void steppingAShareMovesItByTheDeltaAndStaysInRange() {
        assertEquals(11.0, RewardConfigEditor.steppedPercent(10.0, 1.0), 0.0001);
        assertEquals(0.0 + 0.01, RewardConfigEditor.steppedPercent(0.5, -10.0), 0.0001);
        assertEquals(100.0, RewardConfigEditor.steppedPercent(95.0, 10.0), 0.0001);
        assertEquals(12.35, RewardConfigEditor.steppedPercent(12.349, 0.0), 0.0001);
    }

    @Test
    void equalizingGivesEveryRewardTheSameWeight() {
        List<Map<String, Object>> rewards = new ArrayList<>();
        rewards.add(new LinkedHashMap<>(Map.of("chance", 5)));
        rewards.add(new LinkedHashMap<>(Map.of("chance", 900)));
        rewards.add(new LinkedHashMap<>(Map.of("type", "ITEM")));

        RewardConfigEditor.equalizeChances(rewards);

        for (Map<String, Object> reward : rewards) assertEquals(100, reward.get("chance"));
        assertEquals("ITEM", rewards.get(2).get("type"));
    }
}