package com.cortezromeo.taixiu.storage;

import com.cortezromeo.taixiu.TaiXiu;
import com.cortezromeo.taixiu.api.CurrencyTyppe;
import com.cortezromeo.taixiu.api.TaiXiuResult;
import com.cortezromeo.taixiu.api.storage.ISession;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;

public final class SQLiteSessionStorage implements SessionStorage, AutoCloseable {
    private static final int SCHEMA_VERSION = 3;
    private final Connection connection;
    private final File dataFolder;
    private final CurrencyTyppe defaultCurrency;
    private String retentionMode;
    private long retentionDays;
    private long retentionCount;
    private final boolean migrateLegacy;
    private long legacySeed;

    public SQLiteSessionStorage(File databaseFile) throws SQLException {
        this(databaseFile, TaiXiu.plugin.getDataFolder(),
                CurrencyTyppe.valueOf(TaiXiu.plugin.getConfig().getString("currency-settings.default", "VAULT")
                        .toUpperCase(Locale.ROOT)),
                TaiXiu.plugin.getConfig().getString("database.retention.mode", "ALL"),
                TaiXiu.plugin.getConfig().getLong("database.retention.days", 90),
                TaiXiu.plugin.getConfig().getLong("database.retention.max-sessions", 10_000), true);
    }

    SQLiteSessionStorage(File databaseFile, File dataFolder, CurrencyTyppe defaultCurrency,
                         String retentionMode, long retentionDays, long retentionCount,
                         boolean migrateLegacy) throws SQLException {
        this.dataFolder = dataFolder;
        this.defaultCurrency = defaultCurrency;
        this.retentionMode = retentionMode.toUpperCase(Locale.ROOT);
        this.retentionDays = retentionDays;
        this.retentionCount = retentionCount;
        this.migrateLegacy = migrateLegacy;
        File parent = databaseFile.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Could not create plugin data directory " + parent);
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("SQLite JDBC driver is unavailable", exception);
        }
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        configure();
        migrateSchema();
        if (migrateLegacy) migrateActiveLegacySession();
        cleanupRetention();
        cleanupJournal();
    }

    private void configure() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=FULL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
        }
    }

    private void migrateSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS schema_migrations (version INTEGER PRIMARY KEY, applied_at INTEGER NOT NULL)");
        }
        int version = 0;
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_migrations")) {
            if (result.next()) version = result.getInt(1);
        }
        if (version >= SCHEMA_VERSION) return;
        if (version == 1) {
            migrateJournalV2();
            version = 2;
        }
        if (version == 2) {
            migrateJournalV3();
            return;
        }

        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE sessions (" +
                    "id INTEGER PRIMARY KEY, status TEXT NOT NULL CHECK(status IN ('ACTIVE','SETTLING','SETTLED'))," +
                    "currency TEXT NOT NULL, betting_ends_at INTEGER NOT NULL DEFAULT 0," +
                    "dice1 INTEGER NOT NULL DEFAULT 0, dice2 INTEGER NOT NULL DEFAULT 0, dice3 INTEGER NOT NULL DEFAULT 0," +
                    "result TEXT NOT NULL DEFAULT 'NONE', created_at INTEGER NOT NULL, settled_at INTEGER)");
            statement.executeUpdate("CREATE TABLE bets (" +
                    "session_id INTEGER NOT NULL REFERENCES sessions(id) ON DELETE CASCADE," +
                    "player_uuid TEXT NOT NULL, player_name TEXT NOT NULL, side TEXT NOT NULL CHECK(side IN ('TAI','XIU'))," +
                    "stake INTEGER NOT NULL CHECK(stake > 0), tax_bypass INTEGER NOT NULL DEFAULT 0," +
                    "debit_status TEXT NOT NULL DEFAULT 'COMPLETED', created_at INTEGER NOT NULL," +
                    "PRIMARY KEY(session_id, player_uuid))");
            statement.executeUpdate("CREATE TABLE payouts (" +
                    "session_id INTEGER NOT NULL REFERENCES sessions(id) ON DELETE CASCADE, player_uuid TEXT NOT NULL," +
                    "amount INTEGER NOT NULL, status TEXT NOT NULL CHECK(status IN ('PENDING','COMPLETED','FAILED'))," +
                    "attempts INTEGER NOT NULL DEFAULT 0, last_error TEXT, updated_at INTEGER NOT NULL," +
                    "PRIMARY KEY(session_id, player_uuid))");
            statement.executeUpdate("CREATE TABLE transaction_journal (" +
                    "id TEXT PRIMARY KEY, session_id INTEGER NOT NULL, player_uuid TEXT NOT NULL, player_name TEXT NOT NULL," +
                    "currency TEXT NOT NULL, kind TEXT NOT NULL CHECK(kind IN ('DEBIT','PAYOUT','REFUND')), amount INTEGER NOT NULL," +
                    "status TEXT NOT NULL CHECK(status IN ('PREPARED','APPLIED','UNKNOWN','COMPLETED','COMPENSATED','FAILED'))," +
                    "created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,last_error TEXT," +
                    "admin_actor TEXT,admin_reason TEXT)");
            statement.executeUpdate("CREATE INDEX idx_sessions_status ON sessions(status)");
            statement.executeUpdate("CREATE INDEX idx_bets_session_side ON bets(session_id, side)");
            statement.executeUpdate("CREATE INDEX idx_payouts_status ON payouts(status)");
            statement.executeUpdate("CREATE INDEX idx_journal_status_created ON transaction_journal(status,created_at)");
            statement.executeUpdate("CREATE INDEX idx_journal_session ON transaction_journal(session_id)");
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO schema_migrations(version, applied_at) VALUES (?, ?)")) {
                insert.setInt(1, SCHEMA_VERSION);
                insert.setLong(2, System.currentTimeMillis());
                insert.executeUpdate();
            }
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void migrateJournalV3() throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE transaction_journal ADD COLUMN last_error TEXT");
            statement.executeUpdate("ALTER TABLE transaction_journal ADD COLUMN admin_actor TEXT");
            statement.executeUpdate("ALTER TABLE transaction_journal ADD COLUMN admin_reason TEXT");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_journal_status_created " +
                    "ON transaction_journal(status,created_at)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_journal_session ON transaction_journal(session_id)");
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO schema_migrations(version, applied_at) VALUES (3, ?)")) {
                insert.setLong(1, System.currentTimeMillis());
                insert.executeUpdate();
            }
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void migrateJournalV2() throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE transaction_journal RENAME TO transaction_journal_v1");
            statement.executeUpdate("CREATE TABLE transaction_journal (" +
                    "id TEXT PRIMARY KEY, session_id INTEGER NOT NULL, player_uuid TEXT NOT NULL, player_name TEXT NOT NULL," +
                    "currency TEXT NOT NULL, kind TEXT NOT NULL CHECK(kind IN ('DEBIT','PAYOUT','REFUND')), amount INTEGER NOT NULL," +
                    "status TEXT NOT NULL CHECK(status IN ('PREPARED','APPLIED','UNKNOWN','COMPLETED','COMPENSATED','FAILED'))," +
                    "created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
            statement.executeUpdate("INSERT INTO transaction_journal SELECT id,session_id,player_uuid,player_name,currency,kind,amount," +
                    "CASE status WHEN 'INTENT' THEN 'UNKNOWN' ELSE status END,created_at,updated_at FROM transaction_journal_v1");
            statement.executeUpdate("DROP TABLE transaction_journal_v1");
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO schema_migrations(version, applied_at) VALUES (?, ?)")) {
                insert.setInt(1, 2);
                insert.setLong(2, System.currentTimeMillis());
                insert.executeUpdate();
            }
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void migrateActiveLegacySession() throws SQLException {
        if (countSessions() > 0) return;
        File folder = new File(dataFolder, "session");
        File[] files = folder.listFiles((dir, name) -> name.matches("\\d+\\.yml"));
        if (files == null || files.length == 0) return;

        File latest = java.util.Arrays.stream(files)
                .max(Comparator.comparingLong(this::sessionIdFromFile)).orElse(null);
        if (latest == null) return;
        legacySeed = sessionIdFromFile(latest);

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(latest);
        String result = yaml.getString("data.result", "NONE");
        if ("NONE".equalsIgnoreCase(result)) {
            saveData(legacySeed, SessionFileStorage.fromFile(latest, legacySeed));
            TaiXiu.plugin.getLogger().info("Imported unfinished legacy session #" + legacySeed + " into SQLite.");
        } else {
            legacySeed++;
        }

        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneOffset.UTC).format(Instant.now());
        File archive = new File(dataFolder, "session-legacy-" + stamp);
        try {
            Files.move(folder.toPath(), archive.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailure) {
            try {
                Files.move(folder.toPath(), archive.toPath());
            } catch (IOException failure) {
                TaiXiu.plugin.getLogger().warning("Could not archive legacy YAML sessions: " + failure.getMessage());
            }
        }
    }

    private long sessionIdFromFile(File file) {
        try {
            return Long.parseLong(file.getName().substring(0, file.getName().length() - 4));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private long countSessions() throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM sessions")) {
            return result.next() ? result.getLong(1) : 0;
        }
    }

    public synchronized boolean exists(long session) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM sessions WHERE id=?")) {
            statement.setLong(1, session);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not check session #" + session, exception);
        }
    }

    public synchronized long getLastSessionId() {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COALESCE(MAX(id), 0) FROM sessions")) {
            long maximum = result.next() ? result.getLong(1) : 0;
            return Math.max(maximum, legacySeed);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read latest session id", exception);
        }
    }

    public synchronized long getBettingDeadline(long session) {
        try (PreparedStatement query = connection.prepareStatement("SELECT betting_ends_at FROM sessions WHERE id=?")) {
            query.setLong(1, session);
            try (ResultSet result = query.executeQuery()) { return result.next() ? result.getLong(1) : 0; }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load session deadline", exception);
        }
    }

    public synchronized void updateBettingDeadline(long session, long deadline) {
        try (PreparedStatement update = connection.prepareStatement("UPDATE sessions SET betting_ends_at=? WHERE id=?")) {
            update.setLong(1, deadline);
            update.setLong(2, session);
            if (update.executeUpdate() != 1)
                throw new SQLException("Session does not exist: " + session);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not update session deadline", exception);
        }
    }

    @Override
    public synchronized ISession getData(long session) {
        try (PreparedStatement query = connection.prepareStatement("SELECT * FROM sessions WHERE id=?")) {
            query.setLong(1, session);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) return emptySession(session);
                SessionData data = new SessionData(session, row.getInt("dice1"), row.getInt("dice2"), row.getInt("dice3"),
                        TaiXiuResult.valueOf(row.getString("result")), new HashMap<>(), new HashMap<>(),
                        CurrencyTyppe.valueOf(row.getString("currency")));
                loadBets(data);
                return data;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load session #" + session, exception);
        }
    }

    private SessionData emptySession(long session) {
        return new SessionData(session, 0, 0, 0, TaiXiuResult.NONE, new HashMap<>(), new HashMap<>(),
                defaultCurrency);
    }

    private void loadBets(SessionData data) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT player_uuid, player_name, side, stake, tax_bypass FROM bets WHERE session_id=? ORDER BY created_at")) {
            query.setLong(1, data.getSession());
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    data.registerPlayer(rows.getString("player_name"), java.util.UUID.fromString(rows.getString("player_uuid")),
                            rows.getBoolean("tax_bypass"));
                    if ("TAI".equals(rows.getString("side")))
                        data.addTaiPlayer(rows.getString("player_name"), rows.getLong("stake"));
                    else
                        data.addXiuPlayer(rows.getString("player_name"), rows.getLong("stake"));
                }
            }
        }
    }

    @Override
    public synchronized void saveData(long session, ISession data) {
        if (data == null) return;
        try {
            connection.setAutoCommit(false);
            persistData(session, data, System.currentTimeMillis());
            connection.commit();
        } catch (SQLException exception) {
            try { connection.rollback(); } catch (SQLException ignored) { }
            throw new IllegalStateException("Could not save session #" + session, exception);
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ignored) { }
        }
    }

    public synchronized void prepareSettlement(ISession data, List<JournalEntry> payouts) {
        try {
            connection.setAutoCommit(false);
            persistData(data.getSession(), data, System.currentTimeMillis());
            for (JournalEntry payout : payouts) insertIntent(payout);
            if (!payouts.isEmpty()) {
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE sessions SET status='SETTLING',settled_at=NULL WHERE id=?")) {
                    update.setLong(1, data.getSession());
                    update.executeUpdate();
                }
            }
            connection.commit();
            cleanupRetention();
            cleanupJournal();
        } catch (SQLException exception) {
            try { connection.rollback(); } catch (SQLException ignored) { }
            throw new IllegalStateException("Could not prepare settlement #" + data.getSession(), exception);
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ignored) { }
        }
    }

    private void persistData(long session, ISession data, long now) throws SQLException {
            try (PreparedStatement upsert = connection.prepareStatement(
                    "INSERT INTO sessions(id,status,currency,dice1,dice2,dice3,result,created_at,settled_at) " +
                            "VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET status=excluded.status,currency=excluded.currency," +
                            "dice1=excluded.dice1,dice2=excluded.dice2,dice3=excluded.dice3,result=excluded.result," +
                            "settled_at=CASE WHEN sessions.settled_at IS NOT NULL AND excluded.status='SETTLED' " +
                            "THEN sessions.settled_at ELSE excluded.settled_at END")) {
                boolean settled = data.getResult() != TaiXiuResult.NONE;
                boolean payoutPending = settled && hasPendingPayout(session);
                upsert.setLong(1, session);
                upsert.setString(2, settled ? (payoutPending ? "SETTLING" : "SETTLED") : "ACTIVE");
                upsert.setString(3, data.getCurrencyType().name());
                upsert.setInt(4, data.getDice1());
                upsert.setInt(5, data.getDice2());
                upsert.setInt(6, data.getDice3());
                upsert.setString(7, data.getResult().name());
                upsert.setLong(8, now);
                if (settled && !payoutPending) upsert.setLong(9, now); else upsert.setNull(9, Types.BIGINT);
                upsert.executeUpdate();
            }
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM bets WHERE session_id=?")) {
                delete.setLong(1, session);
                delete.executeUpdate();
            }
            insertBets(data, now);
    }

    private boolean hasPendingPayout(long session) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT 1 FROM payouts WHERE session_id=? AND status<>'COMPLETED' LIMIT 1")) {
            query.setLong(1, session);
            try (ResultSet result = query.executeQuery()) { return result.next(); }
        }
    }

    private void insertBets(ISession data, long now) throws SQLException {
        var taiBets = data.getTaiPlayerSnapshot();
        var xiuBets = data.getXiuPlayerSnapshot();
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO bets(session_id,player_uuid,player_name,side,stake,tax_bypass,created_at) VALUES(?,?,?,?,?,?,?)")) {
            for (var entry : taiBets.entrySet()) addBetBatch(insert, data, entry.getKey(), "TAI", entry.getValue(), now);
            for (var entry : xiuBets.entrySet()) addBetBatch(insert, data, entry.getKey(), "XIU", entry.getValue(), now);
            insert.executeBatch();
        }
    }

    private void addBetBatch(PreparedStatement insert, ISession data, String name, String side, long stake, long now) throws SQLException {
        if (!(data instanceof SessionData sessionData) || sessionData.getPlayerId(name) == null)
            throw new SQLException("Missing UUID metadata for bet player " + name);
        insert.setLong(1, data.getSession());
        insert.setString(2, sessionData.getPlayerId(name).toString());
        insert.setString(3, name);
        insert.setString(4, side);
        insert.setLong(5, stake);
        insert.setBoolean(6, sessionData.hasTaxBypass(name));
        insert.setLong(7, now);
        insert.addBatch();
    }

    public synchronized void cleanupRetention() throws SQLException {
        String mode = retentionMode;
        if ("ALL".equals(mode)) return;
        if ("DAYS".equals(mode)) {
            long days = Math.max(1, retentionDays);
            long cutoff = System.currentTimeMillis() - days * 86_400_000L;
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM sessions WHERE status='SETTLED' AND settled_at<? AND NOT EXISTS " +
                            "(SELECT 1 FROM payouts WHERE payouts.session_id=sessions.id AND status<>'COMPLETED')")) {
                delete.setLong(1, cutoff);
                delete.executeUpdate();
            }
        } else if ("COUNT".equals(mode)) {
            long keep = Math.max(1, retentionCount);
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM sessions WHERE status='SETTLED' AND id NOT IN " +
                            "(SELECT id FROM sessions WHERE status='SETTLED' ORDER BY id DESC LIMIT ?) AND NOT EXISTS " +
                            "(SELECT 1 FROM payouts WHERE payouts.session_id=sessions.id AND status<>'COMPLETED')")) {
                delete.setLong(1, keep);
                delete.executeUpdate();
            }
        }
    }

    public synchronized void cleanupJournal() throws SQLException {
        long days = 90;
        if (TaiXiu.plugin != null)
            days = Math.max(1, TaiXiu.plugin.getConfig().getLong("database.journal-retention-days", 90));
        long cutoff = System.currentTimeMillis() - days * 86_400_000L;
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM transaction_journal WHERE status IN ('COMPLETED','COMPENSATED','FAILED') " +
                        "AND updated_at<? AND NOT (kind='PAYOUT' AND status='FAILED')")) {
            delete.setLong(1, cutoff);
            delete.executeUpdate();
        }
    }

    public synchronized void updateRetention(String mode, long days, long count) {
        retentionMode = mode.toUpperCase(Locale.ROOT);
        retentionDays = days;
        retentionCount = count;
        try {
            cleanupRetention();
            cleanupJournal();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not apply retention settings", exception);
        }
    }

    public synchronized void recordIntent(JournalEntry entry) {
        recordIntents(List.of(entry));
    }

    public synchronized void recordIntents(List<JournalEntry> entries) {
        if (entries.isEmpty()) return;
        try {
            connection.setAutoCommit(false);
            for (JournalEntry entry : entries) insertIntent(entry);
            connection.commit();
        } catch (SQLException exception) {
            try { connection.rollback(); } catch (SQLException ignored) { }
            throw new IllegalStateException("Could not write transaction intents", exception);
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ignored) { }
        }
    }

    private void insertIntent(JournalEntry entry) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO transaction_journal(id,session_id,player_uuid,player_name,currency,kind,amount,status,created_at,updated_at) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?)")) {
            long now = System.currentTimeMillis();
            insert.setString(1, entry.id());
            insert.setLong(2, entry.sessionId());
            insert.setString(3, entry.playerId().toString());
            insert.setString(4, entry.playerName());
            insert.setString(5, entry.currency().name());
            insert.setString(6, entry.kind());
            insert.setLong(7, entry.amount());
            insert.setString(8, entry.status());
            insert.setLong(9, now);
            insert.setLong(10, now);
            insert.executeUpdate();
            if ("PAYOUT".equals(entry.kind())) {
                try (PreparedStatement payout = connection.prepareStatement(
                        "INSERT INTO payouts(session_id,player_uuid,amount,status,attempts,updated_at) VALUES(?,?,?,'PENDING',1,?) " +
                                "ON CONFLICT(session_id,player_uuid) DO UPDATE SET amount=excluded.amount,status='PENDING'," +
                                "attempts=payouts.attempts+1,updated_at=excluded.updated_at")) {
                    payout.setLong(1, entry.sessionId());
                    payout.setString(2, entry.playerId().toString());
                    payout.setLong(3, entry.amount());
                    payout.setLong(4, now);
                    payout.executeUpdate();
                }
            }
        }
    }

    public synchronized void markJournal(String id, String status) {
        markJournal(id, status, null);
    }

    public synchronized void markJournal(String id, String status, String error) {
        markJournal(id, status, error, null, null);
    }

    public synchronized void markJournal(String id, String status, String error, String actor, String reason) {
        try {
            connection.setAutoCommit(false);
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE transaction_journal SET status=?,updated_at=?,last_error=?," +
                        "admin_actor=COALESCE(?,admin_actor),admin_reason=COALESCE(?,admin_reason) WHERE id=?")) {
            update.setString(1, status);
            update.setLong(2, System.currentTimeMillis());
            update.setString(3, error);
            update.setString(4, actor);
            update.setString(5, reason);
            update.setString(6, id);
            if (update.executeUpdate() != 1)
                throw new SQLException("Transaction does not exist: " + id);
            if ("COMPLETED".equals(status)) {
                try (PreparedStatement payout = connection.prepareStatement(
                        "UPDATE payouts SET status='COMPLETED',updated_at=? WHERE (session_id,player_uuid)=" +
                                "(SELECT session_id,player_uuid FROM transaction_journal WHERE id=? AND kind='PAYOUT')")) {
                    payout.setLong(1, System.currentTimeMillis());
                    payout.setString(2, id);
                    payout.executeUpdate();
                }
                try (PreparedStatement settle = connection.prepareStatement(
                        "UPDATE sessions SET status='SETTLED',settled_at=? WHERE id=" +
                                "(SELECT session_id FROM transaction_journal WHERE id=?) AND result<>'NONE' AND NOT EXISTS " +
                                "(SELECT 1 FROM payouts WHERE payouts.session_id=sessions.id AND status<>'COMPLETED')")) {
                    settle.setLong(1, System.currentTimeMillis());
                    settle.setString(2, id);
                    settle.executeUpdate();
                }
            } else if ("FAILED".equals(status)) {
                try (PreparedStatement payout = connection.prepareStatement(
                        "UPDATE payouts SET status='FAILED',last_error=?,updated_at=? WHERE (session_id,player_uuid)=" +
                                "(SELECT session_id,player_uuid FROM transaction_journal WHERE id=? AND kind='PAYOUT')")) {
                    payout.setString(1, error);
                    payout.setLong(2, System.currentTimeMillis());
                    payout.setString(3, id);
                    payout.executeUpdate();
                }
            } else if ("PREPARED".equals(status)) {
                try (PreparedStatement payout = connection.prepareStatement(
                        "UPDATE payouts SET status='PENDING',attempts=attempts+1,last_error=NULL,updated_at=? WHERE (session_id,player_uuid)=" +
                                "(SELECT session_id,player_uuid FROM transaction_journal WHERE id=? AND kind='PAYOUT')")) {
                    payout.setLong(1, System.currentTimeMillis());
                    payout.setString(2, id);
                    payout.executeUpdate();
                }
            } else if ("UNKNOWN".equals(status)) {
                try (PreparedStatement payout = connection.prepareStatement(
                        "UPDATE payouts SET status='PENDING',last_error=?,updated_at=? WHERE (session_id,player_uuid)=" +
                                "(SELECT session_id,player_uuid FROM transaction_journal WHERE id=? AND kind='PAYOUT')")) {
                    payout.setString(1, error);
                    payout.setLong(2, System.currentTimeMillis());
                    payout.setString(3, id);
                    payout.executeUpdate();
                }
            }
        }
            connection.commit();
            if ("COMPLETED".equals(status) || "COMPENSATED".equals(status) || "FAILED".equals(status)) {
                cleanupRetention();
                cleanupJournal();
            }
        } catch (SQLException exception) {
            try { connection.rollback(); } catch (SQLException ignored) { }
            throw new IllegalStateException("Could not update transaction journal " + id, exception);
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ignored) { }
        }
    }

    public synchronized java.util.List<JournalEntry> unresolvedJournal() {
        java.util.List<JournalEntry> entries = new java.util.ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT * FROM transaction_journal WHERE status IN ('PREPARED','APPLIED','UNKNOWN') " +
                        "OR (status='FAILED' AND kind='PAYOUT') ORDER BY created_at");
             ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                entries.add(new JournalEntry(rows.getString("id"), rows.getLong("session_id"),
                        java.util.UUID.fromString(rows.getString("player_uuid")), rows.getString("player_name"),
                        CurrencyTyppe.valueOf(rows.getString("currency")), rows.getString("kind"), rows.getLong("amount"),
                        rows.getString("status")));
            }
            return entries;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load pending transactions", exception);
        }
    }

    public synchronized java.util.Optional<JournalEntry> findJournal(String id) {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT * FROM transaction_journal WHERE id=?")) {
            query.setString(1, id);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) return java.util.Optional.empty();
                return java.util.Optional.of(new JournalEntry(row.getString("id"), row.getLong("session_id"),
                        java.util.UUID.fromString(row.getString("player_uuid")), row.getString("player_name"),
                        CurrencyTyppe.valueOf(row.getString("currency")), row.getString("kind"), row.getLong("amount"),
                        row.getString("status")));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load transaction " + id, exception);
        }
    }

    public synchronized boolean betExists(long sessionId, java.util.UUID playerId) {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT 1 FROM bets WHERE session_id=? AND player_uuid=?")) {
            query.setLong(1, sessionId);
            query.setString(2, playerId.toString());
            try (ResultSet result = query.executeQuery()) { return result.next(); }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not inspect persisted bet", exception);
        }
    }

    @Override
    public synchronized void close() {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        } catch (SQLException ignored) {
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            if (TaiXiu.plugin != null) TaiXiu.plugin.getLogger().warning("Could not close SQLite database: " + exception.getMessage());
        }
    }
}
