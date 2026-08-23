package ru.privatenull.pncases.storage;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.ClientSession;
import com.mongodb.MongoException;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.bson.conversions.Bson;
import ru.privatenull.pncases.cases.model.AnimationType;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pnlibrary.database.DatabaseType;
import ru.privatenull.pnlibrary.database.MongoDatabaseManager;
import ru.privatenull.pncases.storage.CasesDatabase.HistoryEntry;

import java.util.*;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Indexes.*;
import static com.mongodb.client.model.Sorts.orderBy;
import static com.mongodb.client.model.Updates.*;

public final class MongoCasesDatabase implements CasesDatabase {
    private final MongoDatabaseManager manager;
    private final String prefix;
    private MongoCollection<Document> keys;
    private MongoCollection<Document> preferences;
    private MongoCollection<Document> casePreferences;
    private MongoCollection<Document> history;
    private MongoCollection<Document> pending;

    public MongoCasesDatabase(MongoDatabaseManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
        prefix = manager.settings().collection();
    }

    @Override
    public DatabaseType type() {
        return DatabaseType.MONGODB;
    }

    @Override
    public void initialize() {
        var database = manager.database();
        keys = database.getCollection(prefix + "_keys");
        preferences = database.getCollection(prefix + "_preferences");
        casePreferences = database.getCollection(prefix + "_case_preferences");
        history = database.getCollection(prefix + "_history");
        pending = database.getCollection(prefix + "_pending_rewards");
        keys.createIndex(compoundIndex(ascending("player_uuid"), ascending("key_id")), new IndexOptions().unique(true));
        preferences.createIndex(ascending("player_uuid"), new IndexOptions().unique(true));
        casePreferences.createIndex(
                compoundIndex(ascending("player_uuid"), ascending("case_id")),
                new IndexOptions().unique(true)
        );
        history.createIndex(compoundIndex(ascending("case_name"), descending("opened_at")));
        pending.createIndex(ascending("player_uuid"), new IndexOptions().unique(true));
    }

    @Override
    public void addKeys(UUID uuid, String keyId, int amount) {
        if (amount <= 0) return;
        keys.updateOne(keyFilter(uuid, keyId), combine(
                setOnInsert("player_uuid", uuid.toString()),
                setOnInsert("key_id", normalize(keyId)),
                inc("amount", amount)
        ), new UpdateOptions().upsert(true));
    }

    @Override
    public int getKeys(UUID uuid, String keyId) {
        Document document = keys.find(keyFilter(uuid, keyId)).first();
        return document == null ? 0 : Math.max(0, document.getInteger("amount", 0));
    }

    @Override
    public boolean takeKeys(UUID uuid, String keyId, int amount) {
        if (amount <= 0) return true;
        Document changed = keys.findOneAndUpdate(and(keyFilter(uuid, keyId), gte("amount", amount)),
                inc("amount", -amount), new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        if (changed == null) return false;
        if (changed.getInteger("amount", 0) <= 0) {
            try {
                keys.deleteOne(eq("_id", changed.getObjectId("_id")));
            } catch (RuntimeException ignored) {
                // The atomic decrement already succeeded. A zero-value row is
                // harmless and must not turn a paid opening into an ambiguous
                // failure that could be charged twice on retry.
            }
        }
        return true;
    }

    @Override
    public Map<String, Integer> getAllKeys(UUID uuid) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Document document : keys.find(eq("player_uuid", uuid.toString())).sort(ascending("key_id"))) {
            int amount = document.getInteger("amount", 0);
            if (amount > 0) result.put(document.getString("key_id"), amount);
        }
        return result;
    }

    @Override
    public AnimationType getAnimation(UUID uuid, String caseId) {
        Document document = casePreferences.find(and(
                eq("player_uuid", uuid.toString()),
                eq("case_id", normalize(caseId))
        )).first();
        if (document == null) {
            document = preferences.find(eq("player_uuid", uuid.toString())).first();
        }
        return parseAnimation(document == null ? null : document.getString("animation"));
    }

    @Override
    public void setAnimation(UUID uuid, String caseId, AnimationType type) {
        Bson filter = and(
                eq("player_uuid", uuid.toString()),
                eq("case_id", normalize(caseId))
        );
        casePreferences.updateOne(filter, combine(
                setOnInsert("player_uuid", uuid.toString()),
                setOnInsert("case_id", normalize(caseId)),
                set("animation", type.name())
        ), new UpdateOptions().upsert(true));
    }

    @Override
    public void addHistory(String caseName, String playerName, String rewardName, long openedAt) {
        String normalized = normalize(caseName);
        history.insertOne(new Document("case_name", normalized)
                .append("player_name", playerName == null ? "Unknown" : playerName)
                .append("reward_name", rewardName == null ? "Reward" : rewardName)
                .append("opened_at", openedAt));
        List<Object> kept = history.find(eq("case_name", normalized))
                .sort(orderBy(descending("opened_at"), descending("_id"))).limit(9)
                .map(document -> document.get("_id")).into(new ArrayList<>());
        if (!kept.isEmpty()) history.deleteMany(and(eq("case_name", normalized), com.mongodb.client.model.Filters.nin("_id", kept)));
    }

    @Override
    public List<HistoryEntry> getHistory(String caseName, int limit) {
        List<HistoryEntry> result = new ArrayList<>();
        for (Document document : history.find(eq("case_name", normalize(caseName)))
                .sort(orderBy(descending("opened_at"), descending("_id"))).limit(limit)) {
            result.add(new HistoryEntry(document.getString("player_name"),
                    document.getString("reward_name"), longValue(document, "opened_at")));
        }
        return result;
    }

    @Override
    public void savePending(UUID uuid, Reward reward) {
        pending.replaceOne(eq("player_uuid", uuid.toString()), rewardDocument(uuid, reward),
                new com.mongodb.client.model.ReplaceOptions().upsert(true));
    }

    @Override
    public Reward loadPending(UUID uuid) {
        Document value = pending.find(eq("player_uuid", uuid.toString())).first();
        return value == null ? null : reward(value);
    }

    @Override
    public void clearPending(UUID uuid) {
        pending.deleteOne(eq("player_uuid", uuid.toString()));
    }

    @Override
    public Set<UUID> getPendingPlayers() {
        Set<UUID> result = new HashSet<>();
        for (Document document : pending.find()) {
            try {
                result.add(UUID.fromString(document.getString("player_uuid")));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    @Override
    public OpeningReservation reserveOpening(UUID uuid, String keyId, Reward reward) {
        try (ClientSession session = manager.client().startSession()) {
            session.startTransaction();
            try {
                Document existing = pending.find(session, eq("player_uuid", uuid.toString())).first();
                if (existing != null) {
                    session.abortTransaction();
                    return OpeningReservation.pending(reward(existing));
                }
                Document changed = keys.findOneAndUpdate(session,
                        and(keyFilter(uuid, keyId), gte("amount", 1)),
                        inc("amount", -1),
                        new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
                if (changed == null) {
                    session.abortTransaction();
                    return OpeningReservation.insufficient(getKeys(uuid, keyId));
                }
                pending.insertOne(session, rewardDocument(uuid, reward));
                if (changed.getInteger("amount", 0) <= 0) keys.deleteOne(session, eq("_id", changed.get("_id")));
                session.commitTransaction();
                return OpeningReservation.success();
            } catch (RuntimeException failure) {
                if (session.hasActiveTransaction()) session.abortTransaction();
                throw failure;
            }
        } catch (MongoException unsupported) {
            if (!transactionUnsupported(unsupported)) throw unsupported;
            // Standalone MongoDB has no cross-collection transactions. The
            // per-player DatabaseExecutor lane remains the safe local fallback.
            return CasesDatabase.super.reserveOpening(uuid, keyId, reward);
        }
    }

    @Override
    public void cancelOpening(UUID uuid, String keyId) {
        try (ClientSession session = manager.client().startSession()) {
            session.startTransaction();
            try {
                long removed = pending.deleteOne(session, eq("player_uuid", uuid.toString())).getDeletedCount();
                if (removed > 0) keys.updateOne(session, keyFilter(uuid, keyId), combine(
                        setOnInsert("player_uuid", uuid.toString()),
                        setOnInsert("key_id", normalize(keyId)),
                        inc("amount", 1)
                ), new UpdateOptions().upsert(true));
                session.commitTransaction();
            } catch (RuntimeException failure) {
                if (session.hasActiveTransaction()) session.abortTransaction();
                throw failure;
            }
        } catch (MongoException unsupported) {
            if (!transactionUnsupported(unsupported)) throw unsupported;
            CasesDatabase.super.cancelOpening(uuid, keyId);
        }
    }

    @Override
    public void close() {
        // DatabaseRouter owns the shared Mongo client.
    }

    private Bson keyFilter(UUID uuid, String keyId) {
        return and(eq("player_uuid", uuid.toString()), eq("key_id", normalize(keyId)));
    }

    private static Document rewardDocument(UUID uuid, Reward reward) {
        return new Document("player_uuid", uuid.toString())
                .append("type", reward.type().name()).append("chance", reward.chance())
                .append("rarity", reward.rarityId()).append("item_base64", CasesDatabase.encodeItem(reward.item()))
                .append("lp_group", reward.lpGroup()).append("lp_node", reward.lpNode())
                .append("lp_duration", reward.lpDuration()).append("vault_amount", reward.vaultAmount())
                .append("player_points_amount", reward.playerPointsAmount()).append("message", reward.message())
                .append("display_name", reward.displayName()).append("command", reward.command());
    }

    private static Reward reward(Document value) {
        return new Reward(value.getInteger("chance", 100), parseType(value.getString("type")),
                CasesDatabase.decodeItem(value.getString("item_base64")), value.getString("lp_group"),
                value.getString("lp_node"), value.getString("lp_duration"), doubleValue(value, "vault_amount"),
                value.getInteger("player_points_amount", 0), value.getString("message"),
                value.getString("display_name"), value.getString("rarity"), null, value.getString("command"));
    }

    private static boolean transactionUnsupported(MongoException exception) {
        return exception.getCode() == 20 || exception.getCode() == 303;
    }

    private static String normalize(String value) {
        return (value == null ? "" : value).toLowerCase(Locale.ROOT);
    }

    private static AnimationType parseAnimation(String value) {
        try {
            return AnimationType.valueOf(value == null ? "" : value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return AnimationType.ANVIL;
        }
    }

    private static Reward.Type parseType(String value) {
        try {
            return Reward.Type.valueOf(value);
        } catch (Exception ignored) {
            return Reward.Type.ITEM;
        }
    }

    private static long longValue(Document document, String key) {
        Number value = document.get(key, Number.class);
        return value == null ? 0L : value.longValue();
    }

    private static double doubleValue(Document document, String key) {
        Number value = document.get(key, Number.class);
        return value == null ? 0.0 : value.doubleValue();
    }
}
