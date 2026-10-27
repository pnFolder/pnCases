package ru.privatenull.pncases.storage;

import ru.privatenull.pncases.cases.model.AnimationType;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pncases.support.database.DatabaseType;
import ru.privatenull.pncases.support.database.JdbcDatabase;
import ru.privatenull.pncases.support.database.JdbcMigration;
import ru.privatenull.pncases.storage.CasesDatabase.HistoryEntry;

import java.sql.*;
import java.util.*;

public final class JdbcCasesDatabase implements CasesDatabase {
    private final JdbcDatabase database;
    private final DatabaseType type;

    public JdbcCasesDatabase(JdbcDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
        type = database.type();
    }

    @Override
    public DatabaseType type() {
        return type;
    }

    @Override
    public void initialize() throws Exception {
        database.migrate("pncases", migrations());
        ensureRarityColumn();
    }

    @Override
    public void addKeys(UUID uuid, String keyId, int amount) {
        if (amount <= 0) return;
        String sql = type == DatabaseType.MYSQL
                ? "INSERT INTO player_keys(player_uuid,key_id,amount) VALUES (?,?,?) ON DUPLICATE KEY UPDATE amount=amount+VALUES(amount)"
                : "INSERT INTO player_keys(player_uuid,key_id,amount) VALUES (?,?,?) ON CONFLICT(player_uuid,key_id) DO UPDATE SET amount=player_keys.amount+excluded.amount";
        execute(sql, statement -> {
            statement.setString(1, uuid.toString());
            statement.setString(2, normalize(keyId));
            statement.setInt(3, amount);
        }, "add keys");
    }

    @Override
    public int getKeys(UUID uuid, String keyId) {
        try (Connection connection = database.connection()) {
            return getKeys(connection, uuid, keyId);
        } catch (Exception exception) {
            throw failure("read keys", exception);
        }
    }

    private int getKeys(Connection connection, UUID uuid, String keyId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                     "SELECT amount FROM player_keys WHERE player_uuid=? AND key_id=?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, normalize(keyId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Math.max(0, result.getInt(1)) : 0;
            }
        }
    }

    @Override
    public boolean takeKeys(UUID uuid, String keyId, int amount) {
        if (amount <= 0) return true;
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                int changed;
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE player_keys SET amount=amount-? WHERE player_uuid=? AND key_id=? AND amount>=?")) {
                    statement.setInt(1, amount);
                    statement.setString(2, uuid.toString());
                    statement.setString(3, normalize(keyId));
                    statement.setInt(4, amount);
                    changed = statement.executeUpdate();
                }
                if (changed > 0) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "DELETE FROM player_keys WHERE player_uuid=? AND key_id=? AND amount<=0")) {
                        statement.setString(1, uuid.toString());
                        statement.setString(2, normalize(keyId));
                        statement.executeUpdate();
                    }
                }
                connection.commit();
                return changed > 0;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        } catch (Exception exception) {
            throw failure("take keys", exception);
        }
    }

    @Override
    public Map<String, Integer> getAllKeys(UUID uuid) {
        Map<String, Integer> result = new LinkedHashMap<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT key_id,amount FROM player_keys WHERE player_uuid=? AND amount>0 ORDER BY key_id")) {
            statement.setString(1, uuid.toString());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.put(rows.getString(1), Math.max(0, rows.getInt(2)));
            }
            return result;
        } catch (Exception exception) {
            throw failure("read key balances", exception);
        }
    }

    @Override
    public boolean transferKeys(UUID sender, UUID receiver, String keyId, int amount) {
        if (sender == null || receiver == null || sender.equals(receiver) || amount <= 0) return false;
        String normalized = normalize(keyId);
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement debit = connection.prepareStatement(
                        "UPDATE player_keys SET amount=amount-? WHERE player_uuid=? AND key_id=? AND amount>=?")) {
                    debit.setInt(1, amount);
                    debit.setString(2, sender.toString());
                    debit.setString(3, normalized);
                    debit.setInt(4, amount);
                    if (debit.executeUpdate() == 0) {
                        connection.rollback();
                        return false;
                    }
                }
                String lock = type == DatabaseType.MYSQL ? " FOR UPDATE" : "";
                try (PreparedStatement read = connection.prepareStatement(
                        "SELECT amount FROM player_keys WHERE player_uuid=? AND key_id=?" + lock)) {
                    read.setString(1, receiver.toString());
                    read.setString(2, normalized);
                    try (ResultSet rows = read.executeQuery()) {
                        if (rows.next() && rows.getLong(1) > Integer.MAX_VALUE - (long) amount) {
                            throw new IllegalStateException("Receiver key balance would overflow");
                        }
                    }
                }
                String creditSql = type == DatabaseType.MYSQL
                        ? "INSERT INTO player_keys(player_uuid,key_id,amount) VALUES (?,?,?) ON DUPLICATE KEY UPDATE amount=amount+VALUES(amount)"
                        : "INSERT INTO player_keys(player_uuid,key_id,amount) VALUES (?,?,?) ON CONFLICT(player_uuid,key_id) DO UPDATE SET amount=player_keys.amount+excluded.amount";
                try (PreparedStatement credit = connection.prepareStatement(creditSql)) {
                    credit.setString(1, receiver.toString());
                    credit.setString(2, normalized);
                    credit.setInt(3, amount);
                    credit.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        } catch (Exception exception) {
            throw failure("transfer keys", exception);
        }
    }

    @Override
    public AnimationType getAnimation(UUID uuid, String caseId) {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT animation FROM player_case_prefs WHERE player_uuid=? AND case_id=?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, normalize(caseId));
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) return parseAnimation(result.getString(1));
            }
            return getLegacyAnimation(connection, uuid);
        } catch (Exception exception) {
            throw failure("read player case animation", exception);
        }
    }

    @Override
    public void setAnimation(UUID uuid, String caseId, AnimationType animation) {
        String sql = type == DatabaseType.MYSQL
                ? "INSERT INTO player_case_prefs(player_uuid,case_id,animation) VALUES (?,?,?) ON DUPLICATE KEY UPDATE animation=VALUES(animation)"
                : "INSERT INTO player_case_prefs(player_uuid,case_id,animation) VALUES (?,?,?) ON CONFLICT(player_uuid,case_id) DO UPDATE SET animation=excluded.animation";
        execute(sql, statement -> {
            statement.setString(1, uuid.toString());
            statement.setString(2, normalize(caseId));
            statement.setString(3, animation.name());
        }, "save player case animation");
    }

    @Override
    public Set<AnimationType> getFavoriteAnimations(UUID uuid, String caseId) {
        List<String> result = new ArrayList<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT animation FROM player_animation_favorites WHERE player_uuid=? AND case_id=?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, normalize(caseId));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(rows.getString(1));
                }
            }
            return CasesDatabase.parseFavoriteAnimations(result);
        } catch (Exception exception) {
            throw failure("read favorite animations", exception);
        }
    }

    @Override
    public void setFavoriteAnimations(UUID uuid, String caseId, Set<AnimationType> types) {
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM player_animation_favorites WHERE player_uuid=? AND case_id=?")) {
                    delete.setString(1, uuid.toString());
                    delete.setString(2, normalize(caseId));
                    delete.executeUpdate();
                }
                if (types != null && !types.isEmpty()) {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO player_animation_favorites(player_uuid,case_id,animation) VALUES (?,?,?)")) {
                        for (AnimationType type : types) {
                            insert.setString(1, uuid.toString());
                            insert.setString(2, normalize(caseId));
                            insert.setString(3, type.name());
                            insert.addBatch();
                        }
                        insert.executeBatch();
                    }
                }
                connection.commit();
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        } catch (Exception exception) {
            throw failure("save favorite animations", exception);
        }
    }

    @Override
    public int getGuaranteeProgress(UUID uuid, String caseId) {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT openings FROM guarantee_progress WHERE player_uuid=? AND case_id=?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, normalize(caseId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Math.max(0, result.getInt(1)) : 0;
            }
        } catch (Exception exception) {
            throw failure("read guarantee progress", exception);
        }
    }

    @Override
    public void setGuaranteeProgress(UUID uuid, String caseId, int openings) {
        String normalizedCase = normalize(caseId);
        if (openings <= 0) {
            execute("DELETE FROM guarantee_progress WHERE player_uuid=? AND case_id=?", statement -> {
                statement.setString(1, uuid.toString());
                statement.setString(2, normalizedCase);
            }, "reset guarantee progress");
            return;
        }
        String sql = type == DatabaseType.MYSQL
                ? "INSERT INTO guarantee_progress(player_uuid,case_id,openings) VALUES (?,?,?) ON DUPLICATE KEY UPDATE openings=VALUES(openings)"
                : "INSERT INTO guarantee_progress(player_uuid,case_id,openings) VALUES (?,?,?) ON CONFLICT(player_uuid,case_id) DO UPDATE SET openings=excluded.openings";
        execute(sql, statement -> {
            statement.setString(1, uuid.toString());
            statement.setString(2, normalizedCase);
            statement.setInt(3, openings);
        }, "save guarantee progress");
    }

    @Override
    public void addHistory(String caseName, String playerName, String rewardName, long openedAt) {
        execute("INSERT INTO open_history(case_name,player_name,reward_name,opened_at) VALUES (?,?,?,?)", statement -> {
            statement.setString(1, normalize(caseName));
            statement.setString(2, playerName == null ? "Unknown" : playerName);
            statement.setString(3, rewardName == null ? "Reward" : rewardName);
            statement.setLong(4, openedAt);
        }, "save open history");
        trimHistory(normalize(caseName), 9);
    }

    @Override
    public List<HistoryEntry> getHistory(String caseName, int limit) {
        List<HistoryEntry> result = new ArrayList<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_name,reward_name,opened_at FROM open_history WHERE case_name=? ORDER BY opened_at DESC,id DESC LIMIT ?")) {
            statement.setString(1, normalize(caseName));
            statement.setInt(2, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(new HistoryEntry(rows.getString(1), rows.getString(2), rows.getLong(3)));
            }
            return result;
        } catch (Exception exception) {
            throw failure("read open history", exception);
        }
    }

    @Override
    public void savePending(UUID uuid, Reward reward) {
        execute(pendingInsertSql(true), statement -> writeReward(statement, uuid, reward), "save pending reward");
    }

    @Override
    public Reward loadPending(UUID uuid) {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT type,chance,rarity,item_base64,lp_group,lp_node,lp_duration,vault_amount,player_points_amount,message,display_name,command FROM pending_rewards WHERE player_uuid=?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                return readReward(result);
            }
        } catch (Exception exception) {
            throw failure("read pending reward", exception);
        }
    }

    @Override
    public void clearPending(UUID uuid) {
        execute("DELETE FROM pending_rewards WHERE player_uuid=?", statement -> statement.setString(1, uuid.toString()), "delete pending reward");
    }

    @Override
    public Set<UUID> getPendingPlayers() {
        Set<UUID> result = new HashSet<>();
        try (Connection connection = database.connection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT player_uuid FROM pending_rewards")) {
            while (rows.next()) addUuid(result, rows.getString(1));
            try (Statement batches = connection.createStatement();
                 ResultSet batchRows = batches.executeQuery("SELECT player_uuid FROM pending_batches")) {
                while (batchRows.next()) addUuid(result, batchRows.getString(1));
            }
            return result;
        } catch (Exception exception) {
            throw failure("read pending players", exception);
        }
    }

    @Override
    public OpeningReservation reserveOpening(UUID uuid, String keyId, Reward reward) {
        String normalizedKey = normalize(keyId);
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                Reward existing = loadPending(connection, uuid);
                PendingBatch batch = loadBatch(connection, uuid);
                if (batch != null) {
                    connection.rollback();
                    return OpeningReservation.pending(batch.rewards().get(0));
                }
                if (existing != null) {
                    connection.rollback();
                    return OpeningReservation.pending(existing);
                }
                try (PreparedStatement statement = connection.prepareStatement(pendingInsertSql(false))) {
                    writeReward(statement, uuid, reward);
                    statement.executeUpdate();
                }
                int changed;
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE player_keys SET amount=amount-1 WHERE player_uuid=? AND key_id=? AND amount>=1")) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, normalizedKey);
                    changed = statement.executeUpdate();
                }
                if (changed == 0) {
                    connection.rollback();
                    return OpeningReservation.insufficient(getKeys(connection, uuid, normalizedKey));
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM player_keys WHERE player_uuid=? AND key_id=? AND amount<=0")) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, normalizedKey);
                    statement.executeUpdate();
                }
                connection.commit();
                return OpeningReservation.success();
            } catch (SQLException exception) {
                connection.rollback();
                if (isConstraintViolation(exception)) {
                    Reward existing = loadPending(connection, uuid);
                    if (existing != null) return OpeningReservation.pending(existing);
                }
                throw exception;
            } catch (RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (Exception exception) {
            throw failure("reserve case opening", exception);
        }
    }

    @Override
    public PendingBatch loadBatch(UUID uuid) {
        try (Connection connection = database.connection()) {
            return loadBatch(connection, uuid);
        } catch (Exception exception) {
            throw failure("read pending batch", exception);
        }
    }

    private PendingBatch loadBatch(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT payload,next_index FROM pending_batches WHERE player_uuid=?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? PendingBatch.decode(rows.getString(1), rows.getInt(2)) : null;
            }
        }
    }

    @Override
    public OpeningReservation reserveBatch(UUID uuid, PendingBatch batch) {
        String payload = batch.encode();
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                Reward old = loadPending(connection, uuid);
                PendingBatch existing = loadBatch(connection, uuid);
                if (old != null || existing != null) {
                    connection.rollback();
                    return OpeningReservation.pending(old != null ? old : existing.rewards().get(0));
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO pending_batches(player_uuid,batch_id,payload,next_index,item_count) VALUES (?,?,?,0,?)")) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, batch.id().toString());
                    statement.setString(3, payload);
                    statement.setInt(4, batch.rewards().size());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE player_keys SET amount=amount-? WHERE player_uuid=? AND key_id=? AND amount>=?")) {
                    statement.setInt(1, batch.rewards().size());
                    statement.setString(2, uuid.toString());
                    statement.setString(3, normalize(batch.caseId()));
                    statement.setInt(4, batch.rewards().size());
                    if (statement.executeUpdate() == 0) {
                        connection.rollback();
                        return OpeningReservation.insufficient(getKeys(connection, uuid, batch.caseId()));
                    }
                }
                if (batch.guaranteeProgress() >= 0) {
                    String update = type == DatabaseType.MYSQL
                            ? " ON DUPLICATE KEY UPDATE openings=VALUES(openings)"
                            : " ON CONFLICT(player_uuid,case_id) DO UPDATE SET openings=excluded.openings";
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO guarantee_progress(player_uuid,case_id,openings) VALUES (?,?,?)" + update)) {
                        statement.setString(1, uuid.toString());
                        statement.setString(2, normalize(batch.caseId()));
                        statement.setInt(3, batch.guaranteeProgress());
                        statement.executeUpdate();
                    }
                }
                connection.commit();
                return OpeningReservation.success();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        } catch (Exception exception) {
            throw failure("reserve batch opening", exception);
        }
    }

    @Override
    public void acknowledgeBatchReward(UUID uuid, UUID batchId, int index) {
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE pending_batches SET next_index=next_index+1 WHERE player_uuid=? AND batch_id=? AND next_index=?")) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, batchId.toString());
                    statement.setInt(3, index);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM pending_batches WHERE player_uuid=? AND batch_id=? AND next_index>=item_count")) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, batchId.toString());
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        } catch (Exception exception) {
            throw failure("acknowledge batch reward", exception);
        }
    }

    @Override
    public void cancelOpening(UUID uuid, String keyId) {
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                int removed;
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM pending_rewards WHERE player_uuid=?")) {
                    statement.setString(1, uuid.toString());
                    removed = statement.executeUpdate();
                }
                if (removed > 0) addKeys(connection, uuid, normalize(keyId), 1);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        } catch (Exception exception) {
            throw failure("cancel case opening", exception);
        }
    }

    @Override
    public void close() {
        // DatabaseRouter owns the shared JDBC pool.
    }

    private void trimHistory(String caseName, int limit) {
        String sql = "DELETE FROM open_history WHERE case_name=? AND id NOT IN (SELECT id FROM (SELECT id FROM open_history WHERE case_name=? ORDER BY opened_at DESC,id DESC LIMIT ?) kept)";
        execute(sql, statement -> {
            statement.setString(1, caseName);
            statement.setString(2, caseName);
            statement.setInt(3, limit);
        }, "trim open history");
    }

    private void writeReward(PreparedStatement statement, UUID uuid, Reward reward) throws SQLException {
        statement.setString(1, uuid.toString());
        statement.setString(2, reward.type().name());
        statement.setInt(3, reward.chance());
        statement.setString(4, reward.rarityId());
        statement.setString(5, CasesDatabase.encodeItem(reward.item()));
        statement.setString(6, reward.lpGroup());
        statement.setString(7, reward.lpNode());
        statement.setString(8, reward.lpDuration());
        statement.setDouble(9, reward.vaultAmount());
        statement.setInt(10, reward.playerPointsAmount());
        statement.setString(11, reward.message());
        statement.setString(12, reward.displayName());
        statement.setString(13, Reward.serializeCommands(reward.commands()));
    }

    private Reward loadPending(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT type,chance,rarity,item_base64,lp_group,lp_node,lp_duration,vault_amount,player_points_amount,message,display_name,command FROM pending_rewards WHERE player_uuid=?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? readReward(result) : null;
            }
        }
    }

    private String pendingInsertSql(boolean upsert) {
        String columns = "player_uuid,type,chance,rarity,item_base64,lp_group,lp_node,lp_duration,vault_amount,player_points_amount,message,display_name,command";
        String sql = "INSERT INTO pending_rewards(" + columns + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        if (!upsert) return sql;
        String update = type == DatabaseType.MYSQL
                ? "type=VALUES(type),chance=VALUES(chance),rarity=VALUES(rarity),item_base64=VALUES(item_base64),lp_group=VALUES(lp_group),lp_node=VALUES(lp_node),lp_duration=VALUES(lp_duration),vault_amount=VALUES(vault_amount),player_points_amount=VALUES(player_points_amount),message=VALUES(message),display_name=VALUES(display_name),command=VALUES(command)"
                : "type=excluded.type,chance=excluded.chance,rarity=excluded.rarity,item_base64=excluded.item_base64,lp_group=excluded.lp_group,lp_node=excluded.lp_node,lp_duration=excluded.lp_duration,vault_amount=excluded.vault_amount,player_points_amount=excluded.player_points_amount,message=excluded.message,display_name=excluded.display_name,command=excluded.command";
        return sql + (type == DatabaseType.MYSQL
                ? " ON DUPLICATE KEY UPDATE " : " ON CONFLICT(player_uuid) DO UPDATE SET ") + update;
    }

    private void addKeys(Connection connection, UUID uuid, String keyId, int amount) throws SQLException {
        String sql = type == DatabaseType.MYSQL
                ? "INSERT INTO player_keys(player_uuid,key_id,amount) VALUES (?,?,?) ON DUPLICATE KEY UPDATE amount=amount+VALUES(amount)"
                : "INSERT INTO player_keys(player_uuid,key_id,amount) VALUES (?,?,?) ON CONFLICT(player_uuid,key_id) DO UPDATE SET amount=player_keys.amount+excluded.amount";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, keyId);
            statement.setInt(3, amount);
            statement.executeUpdate();
        }
    }

    private static boolean isConstraintViolation(SQLException exception) {
        String state = exception.getSQLState();
        return state != null && state.startsWith("23");
    }

    private Reward readReward(ResultSet result) throws SQLException {
        return new Reward(result.getInt("chance"), Reward.Type.fromStored(result.getString("type")),
                CasesDatabase.decodeItem(result.getString("item_base64")), result.getString("lp_group"),
                result.getString("lp_node"), result.getString("lp_duration"), result.getDouble("vault_amount"),
                result.getInt("player_points_amount"), result.getString("message"), result.getString("display_name"),
                result.getString("rarity"), null, null,
                Reward.deserializeCommands(result.getString("command")));
    }

    private void execute(String sql, SqlBinder binder, String action) {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            statement.executeUpdate();
        } catch (Exception exception) {
            throw failure(action, exception);
        }
    }

    private void ensureRarityColumn() throws Exception {
        try (Connection connection = database.connection()) {
            boolean exists = false;
            try (ResultSet columns = connection.getMetaData().getColumns(null, null, "pending_rewards", "rarity")) {
                exists = columns.next();
            }
            if (!exists) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("ALTER TABLE pending_rewards ADD COLUMN rarity VARCHAR(64)");
                }
            }
        }
    }

    private List<JdbcMigration> migrations() {
        return List.of(new JdbcMigration(1, "create pnCases storage", List.of(
                "CREATE TABLE IF NOT EXISTS player_keys(player_uuid TEXT NOT NULL,key_id TEXT NOT NULL,amount INTEGER NOT NULL CHECK(amount>=0),PRIMARY KEY(player_uuid,key_id))",
                "CREATE TABLE IF NOT EXISTS player_prefs(player_uuid TEXT PRIMARY KEY,animation TEXT NOT NULL)",
                "CREATE TABLE IF NOT EXISTS open_history(id INTEGER PRIMARY KEY AUTOINCREMENT,case_name TEXT NOT NULL,player_name TEXT NOT NULL,reward_name TEXT NOT NULL,opened_at INTEGER NOT NULL)",
                "CREATE INDEX IF NOT EXISTS idx_open_history_case_time ON open_history(case_name,opened_at DESC,id DESC)",
                "CREATE TABLE IF NOT EXISTS pending_rewards(player_uuid TEXT PRIMARY KEY,type TEXT NOT NULL,chance INTEGER NOT NULL,rarity VARCHAR(64),item_base64 TEXT,lp_group TEXT,lp_node TEXT,lp_duration TEXT,vault_amount REAL NOT NULL DEFAULT 0,player_points_amount INTEGER NOT NULL DEFAULT 0,message TEXT,display_name TEXT)"
        ), List.of(
                "CREATE TABLE IF NOT EXISTS player_keys(player_uuid VARCHAR(36) NOT NULL,key_id VARCHAR(191) NOT NULL,amount INT NOT NULL,PRIMARY KEY(player_uuid,key_id)) ENGINE=InnoDB",
                "CREATE TABLE IF NOT EXISTS player_prefs(player_uuid VARCHAR(36) PRIMARY KEY,animation VARCHAR(64) NOT NULL) ENGINE=InnoDB",
                "CREATE TABLE IF NOT EXISTS open_history(id BIGINT AUTO_INCREMENT PRIMARY KEY,case_name VARCHAR(191) NOT NULL,player_name VARCHAR(191) NOT NULL,reward_name TEXT NOT NULL,opened_at BIGINT NOT NULL,INDEX idx_open_history_case_time(case_name,opened_at,id)) ENGINE=InnoDB",
                "CREATE TABLE IF NOT EXISTS pending_rewards(player_uuid VARCHAR(36) PRIMARY KEY,type VARCHAR(64) NOT NULL,chance INT NOT NULL,rarity VARCHAR(64),item_base64 LONGTEXT,lp_group VARCHAR(191),lp_node TEXT,lp_duration VARCHAR(191),vault_amount DOUBLE NOT NULL DEFAULT 0,player_points_amount INT NOT NULL DEFAULT 0,message TEXT,display_name TEXT) ENGINE=InnoDB"
        )), new JdbcMigration(2, "persist pending PERSONAL reward commands", List.of(
                "ALTER TABLE pending_rewards ADD COLUMN command TEXT"
        ), List.of(
                "ALTER TABLE pending_rewards ADD COLUMN command TEXT"
        )), new JdbcMigration(3, "store animation preference per case", List.of(
                "CREATE TABLE IF NOT EXISTS player_case_prefs(player_uuid TEXT NOT NULL,case_id TEXT NOT NULL,animation TEXT NOT NULL,PRIMARY KEY(player_uuid,case_id))"
        ), List.of(
                "CREATE TABLE IF NOT EXISTS player_case_prefs(player_uuid VARCHAR(36) NOT NULL,case_id VARCHAR(191) NOT NULL,animation VARCHAR(64) NOT NULL,PRIMARY KEY(player_uuid,case_id)) ENGINE=InnoDB"
        )), new JdbcMigration(4, "store per-case guarantee progress", List.of(
                "CREATE TABLE IF NOT EXISTS guarantee_progress(player_uuid TEXT NOT NULL,case_id TEXT NOT NULL,openings INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(player_uuid,case_id))"
        ), List.of(
                "CREATE TABLE IF NOT EXISTS guarantee_progress(player_uuid VARCHAR(36) NOT NULL,case_id VARCHAR(191) NOT NULL,openings INT NOT NULL DEFAULT 0,PRIMARY KEY(player_uuid,case_id)) ENGINE=InnoDB"
        )), new JdbcMigration(5, "store favorite animations per case", List.of(
                "CREATE TABLE IF NOT EXISTS player_animation_favorites(player_uuid TEXT NOT NULL,case_id TEXT NOT NULL,animation TEXT NOT NULL,PRIMARY KEY(player_uuid,case_id,animation))"
        ), List.of(
                "CREATE TABLE IF NOT EXISTS player_animation_favorites(player_uuid VARCHAR(36) NOT NULL,case_id VARCHAR(191) NOT NULL,animation VARCHAR(64) NOT NULL,PRIMARY KEY(player_uuid,case_id,animation)) ENGINE=InnoDB"
        )), new JdbcMigration(6, "store durable multi-open rewards", List.of(
                "CREATE TABLE IF NOT EXISTS pending_batches(player_uuid TEXT PRIMARY KEY,batch_id TEXT NOT NULL,payload TEXT NOT NULL,next_index INTEGER NOT NULL DEFAULT 0,item_count INTEGER NOT NULL)"
        ), List.of(
                "CREATE TABLE IF NOT EXISTS pending_batches(player_uuid VARCHAR(36) PRIMARY KEY,batch_id VARCHAR(36) NOT NULL,payload LONGTEXT NOT NULL,next_index INT NOT NULL DEFAULT 0,item_count INT NOT NULL) ENGINE=InnoDB"
        )));
    }

    private AnimationType getLegacyAnimation(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT animation FROM player_prefs WHERE player_uuid=?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? parseAnimation(result.getString(1)) : AnimationType.ANVIL;
            }
        }
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


    private static void addUuid(Set<UUID> output, String value) {
        try {
            output.add(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
        }
    }

    private IllegalStateException failure(String action, Exception exception) {
        return new IllegalStateException("Could not " + action + " in " + type + " database", exception);
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement statement) throws Exception;
    }
}
