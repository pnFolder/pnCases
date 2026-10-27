package ru.privatenull.pncases.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.privatenull.pncases.cases.model.Reward;

import static org.junit.jupiter.api.Assertions.*;

class PendingBatchTest {
    @Test
    void snapshotPreservesOrderedCommandsAndRewardMetadata() {
        Reward original = new Reward(15, Reward.Type.PERSONAL, null, "knight", "group.knight", "7d",
                125.5, 42, "&fНаграда {player}", "Рыцарь на 7 дней", "custom_rarity", null, null,
                List.of("lp user {player} parent removetemp knight", "lp user {player} parent add knight"));
        UUID id = UUID.randomUUID();
        PendingBatch decoded = PendingBatch.decode(new PendingBatch(id, "donate", List.of(original), 0, 4).encode(), 0);
        assertEquals(id, decoded.id());
        assertEquals("donate", decoded.caseId());
        assertEquals(4, decoded.guaranteeProgress());
        Reward copy = decoded.rewards().get(0);
        assertEquals(original.type(), copy.type());
        assertEquals(original.chance(), copy.chance());
        assertEquals(original.rarityId(), copy.rarityId());
        assertEquals(original.lpGroup(), copy.lpGroup());
        assertEquals(original.lpNode(), copy.lpNode());
        assertEquals(original.lpDuration(), copy.lpDuration());
        assertEquals(original.vaultAmount(), copy.vaultAmount());
        assertEquals(original.playerPointsAmount(), copy.playerPointsAmount());
        assertEquals(original.message(), copy.message());
        assertEquals(original.displayName(), copy.displayName());
        assertEquals(original.commands(), copy.commands());
    }

    @Test
    void cursorIsIndependentOfImmutableRewardSnapshot() {
        List<Reward> source = new ArrayList<>(List.of(reward("first"), reward("second")));
        PendingBatch batch = new PendingBatch(UUID.randomUUID(), "test", source, 0, -1);
        source.clear();
        assertEquals(2, batch.rewards().size());
        assertThrows(UnsupportedOperationException.class, () -> batch.rewards().clear());
        PendingBatch restored = PendingBatch.decode(batch.encode(), 1);
        assertEquals(1, restored.nextIndex());
        assertEquals("second", restored.rewards().get(restored.nextIndex()).displayName());
        assertThrows(IllegalArgumentException.class, () -> PendingBatch.decode(batch.encode(), 3));
    }

    static Reward reward(String name) {
        return new Reward(10, Reward.Type.PERSONAL, null, null, null, null, null, name);
    }

    @Test
    void brokenItemPayloadMustNotSilentlyTurnIntoAnEmptyReward() {
        PendingBatch batch = new PendingBatch(UUID.randomUUID(), "test", List.of(reward("item")), 0, -1);
        var json = com.google.gson.JsonParser.parseString(batch.encode()).getAsJsonObject();
        var entry = json.getAsJsonArray("rewards").get(0).getAsJsonObject();
        entry.addProperty("item", "not-base64!");
        entry.addProperty("type", "ITEM");
        assertThrows(IllegalStateException.class, () -> PendingBatch.decode(json.toString(), 0));
    }
}
