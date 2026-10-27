package ru.privatenull.pncases.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.pncases.cases.model.Reward;

/** Durable snapshot of a multi-open. The database cursor acknowledges rewards individually. */
public record PendingBatch(UUID id, String caseId, List<Reward> rewards, int nextIndex, int guaranteeProgress) {
    public PendingBatch {
        rewards = List.copyOf(rewards);
        if (id == null || caseId == null || rewards.isEmpty() || rewards.size() > 64
                || nextIndex < 0 || nextIndex > rewards.size()) {
            throw new IllegalArgumentException("Invalid pending batch");
        }
    }

    public String encode() {
        JsonObject root = new JsonObject();
        root.addProperty("id", id.toString());
        root.addProperty("case", caseId);
        root.addProperty("guarantee", guaranteeProgress);
        JsonArray entries = new JsonArray();
        for (Reward reward : rewards) {
            JsonObject entry = new JsonObject();
            entry.addProperty("type", reward.type().name());
            entry.addProperty("chance", reward.chance());
            entry.addProperty("rarity", reward.rarityId());
            entry.addProperty("item", encodeItem(reward.item()));
            entry.addProperty("visual", encodeItem(reward.visualItem()));
            entry.addProperty("group", reward.lpGroup());
            entry.addProperty("node", reward.lpNode());
            entry.addProperty("duration", reward.lpDuration());
            entry.addProperty("money", reward.vaultAmount());
            entry.addProperty("points", reward.playerPointsAmount());
            entry.addProperty("message", reward.message());
            entry.addProperty("name", reward.displayName());
            entry.addProperty("commands", Reward.serializeCommands(reward.commands()));
            entries.add(entry);
        }
        root.add("rewards", entries);
        return root.toString();
    }

    public static PendingBatch decode(String json, int nextIndex) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        List<Reward> rewards = new ArrayList<>();
        for (var element : root.getAsJsonArray("rewards")) {
            JsonObject entry = element.getAsJsonObject();
            rewards.add(new Reward(entry.get("chance").getAsInt(), Reward.Type.valueOf(text(entry, "type")),
                    decodeItem(text(entry, "item")), text(entry, "group"), text(entry, "node"),
                    text(entry, "duration"), entry.get("money").getAsDouble(), entry.get("points").getAsInt(),
                    text(entry, "message"), text(entry, "name"), text(entry, "rarity"),
                    decodeItem(text(entry, "visual")), null,
                    Reward.deserializeCommands(text(entry, "commands"))));
        }
        return new PendingBatch(UUID.fromString(text(root, "id")), text(root, "case"), rewards, nextIndex,
                root.has("guarantee") ? root.get("guarantee").getAsInt() : -1);
    }

    private static String text(JsonObject value, String key) {
        return value.has(key) && !value.get(key).isJsonNull() ? value.get(key).getAsString() : null;
    }

    private static String encodeItem(ItemStack item) {
        String encoded = CasesDatabase.encodeItem(item);
        if (item != null && encoded == null) {
            throw new IllegalStateException("Cannot persist batch item; keys must not be consumed");
        }
        return encoded;
    }

    private static ItemStack decodeItem(String encoded) {
        ItemStack item = CasesDatabase.decodeItem(encoded);
        if (encoded != null && item == null) {
            throw new IllegalStateException("Cannot restore batch item; keep the pending reward");
        }
        return item;
    }
}
