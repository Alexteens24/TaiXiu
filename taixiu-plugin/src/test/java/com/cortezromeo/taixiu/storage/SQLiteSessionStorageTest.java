package com.cortezromeo.taixiu.storage;

import com.cortezromeo.taixiu.api.CurrencyTyppe;
import com.cortezromeo.taixiu.api.TaiXiuResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SQLiteSessionStorageTest {

    @TempDir File directory;

    @Test
    void preparesAllPayoutsAtomicallyAndCompletesThem() throws Exception {
        File database = new File(directory, "sessions.db");
        UUID playerId = UUID.randomUUID();
        try (SQLiteSessionStorage storage = storage(database)) {
            SessionData session = activeSession(7, "Alex", playerId, 1_000);
            storage.saveData(7, session);
            session.setDice1(4);
            session.setDice2(4);
            session.setDice3(4);
            session.setResult(TaiXiuResult.TAI);
            JournalEntry payout = new JournalEntry("payout-1", 7, playerId, "Alex",
                    CurrencyTyppe.VAULT, "PAYOUT", 2_000);

            storage.prepareSettlement(session, List.of(payout));

            assertEquals("PREPARED", storage.findJournal("payout-1").orElseThrow().status());
            assertEquals(TaiXiuResult.TAI, storage.getData(7).getResult());
            storage.markJournal("payout-1", "COMPLETED");
            assertTrue(storage.unresolvedJournal().isEmpty());
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.getAbsolutePath());
             var result = connection.createStatement().executeQuery("SELECT status FROM sessions WHERE id=7")) {
            assertTrue(result.next());
            assertEquals("SETTLED", result.getString(1));
        }
    }

    @Test
    void rollsBackWholeSettlementWhenAnyPayoutIsInvalid() throws Exception {
        File database = new File(directory, "rollback.db");
        UUID playerId = UUID.randomUUID();
        try (SQLiteSessionStorage storage = storage(database)) {
            SessionData session = activeSession(3, "Alex", playerId, 500);
            storage.saveData(3, session);
            session.setResult(TaiXiuResult.TAI);
            JournalEntry invalid = new JournalEntry("bad", 3, playerId, "Alex",
                    CurrencyTyppe.VAULT, "INVALID", 1_000);

            assertThrows(IllegalStateException.class,
                    () -> storage.prepareSettlement(session, List.of(invalid)));
            assertEquals(TaiXiuResult.NONE, storage.getData(3).getResult());
            assertTrue(storage.findJournal("bad").isEmpty());
        }
    }

    @Test
    void migratesAmbiguousV1IntentsToUnknown() throws Exception {
        File database = new File(directory, "legacy.db");
        Class.forName("org.sqlite.JDBC");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.getAbsolutePath());
             var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE schema_migrations(version INTEGER PRIMARY KEY, applied_at INTEGER NOT NULL)");
            statement.executeUpdate("INSERT INTO schema_migrations VALUES(1,0)");
            statement.executeUpdate("CREATE TABLE transaction_journal(id TEXT PRIMARY KEY,session_id INTEGER NOT NULL," +
                    "player_uuid TEXT NOT NULL,player_name TEXT NOT NULL,currency TEXT NOT NULL,kind TEXT NOT NULL," +
                    "amount INTEGER NOT NULL,status TEXT NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
            statement.executeUpdate("INSERT INTO transaction_journal VALUES('old',1,'" + UUID.randomUUID() +
                    "','Alex','VAULT','DEBIT',100,'INTENT',0,0)");
        }
        try (SQLiteSessionStorage storage = storage(database)) {
            assertEquals("UNKNOWN", storage.findJournal("old").orElseThrow().status());
        }
    }

    @Test
    void legacyMutableMapsAreDefensiveCopies() {
        SessionData session = activeSession(1, "Alex", UUID.randomUUID(), 100);
        session.getTaiPlayers().clear();
        assertEquals(100, session.getTaiPlayerSnapshot().get("Alex"));
    }

    @Test
    void onlyActionableFailuresRemainInReconciliationQueue() throws Exception {
        File database = new File(directory, "reconciliation.db");
        UUID playerId = UUID.randomUUID();
        try (SQLiteSessionStorage storage = storage(database)) {
            SessionData session = activeSession(9, "Alex", playerId, 100);
            storage.saveData(9, session);
            JournalEntry debit = new JournalEntry("debit-failed", 9, playerId, "Alex",
                    CurrencyTyppe.VAULT, "DEBIT", 100);
            storage.recordIntent(debit);
            storage.markJournal(debit.id(), "FAILED", "provider rejected");
            assertTrue(storage.unresolvedJournal().isEmpty());

            JournalEntry payout = new JournalEntry("payout-failed", 9, playerId, "Alex",
                    CurrencyTyppe.VAULT, "PAYOUT", 200);
            storage.recordIntent(payout);
            storage.markJournal(payout.id(), "FAILED", "provider rejected");
            assertEquals(List.of("payout-failed"), storage.unresolvedJournal().stream().map(JournalEntry::id).toList());
        }
    }

    @Test
    void schemaV3IndexesAndManualAuditArePersisted() throws Exception {
        File database = new File(directory, "schema-v3.db");
        UUID playerId = UUID.randomUUID();
        try (SQLiteSessionStorage storage = storage(database)) {
            storage.saveData(12, activeSession(12, "Alex", playerId, 100));
            JournalEntry entry = new JournalEntry("audit", 12, playerId, "Alex",
                    CurrencyTyppe.VAULT, "DEBIT", 100);
            storage.recordIntent(entry);
            storage.markJournal(entry.id(), "FAILED", "provider rejected", "Console", "verified statement");
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.getAbsolutePath())) {
            try (var row = connection.createStatement().executeQuery(
                    "SELECT last_error,admin_actor,admin_reason FROM transaction_journal WHERE id='audit'")) {
                assertTrue(row.next());
                assertEquals("provider rejected", row.getString(1));
                assertEquals("Console", row.getString(2));
                assertEquals("verified statement", row.getString(3));
            }
            try (var indexes = connection.createStatement().executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='index' AND name LIKE 'idx_journal_%'")) {
                var names = new java.util.HashSet<String>();
                while (indexes.next()) names.add(indexes.getString(1));
                assertEquals(java.util.Set.of("idx_journal_status_created", "idx_journal_session"), names);
            }
        }
    }

    @Test
    void resavingSettledSessionDoesNotRefreshRetentionTimestamp() throws Exception {
        File database = new File(directory, "settled-at.db");
        SessionData session = activeSession(20, "Alex", UUID.randomUUID(), 100);
        session.setResult(TaiXiuResult.TAI);
        try (SQLiteSessionStorage storage = storage(database)) {
            storage.saveData(20, session);
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.getAbsolutePath())) {
                connection.createStatement().executeUpdate("UPDATE sessions SET settled_at=1234 WHERE id=20");
            }
            storage.saveData(20, session);
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.getAbsolutePath());
             var row = connection.createStatement().executeQuery("SELECT settled_at FROM sessions WHERE id=20")) {
            assertTrue(row.next());
            assertEquals(1234, row.getLong(1));
        }
    }

    private SQLiteSessionStorage storage(File database) throws Exception {
        return new SQLiteSessionStorage(database, directory, CurrencyTyppe.VAULT,
                "ALL", 90, 10_000, false);
    }

    private SessionData activeSession(long id, String name, UUID playerId, long stake) {
        SessionData session = new SessionData(id, 0, 0, 0, TaiXiuResult.NONE,
                new HashMap<>(), new HashMap<>(), CurrencyTyppe.VAULT);
        session.registerPlayer(name, playerId, false);
        session.addTaiPlayer(name, stake);
        return session;
    }
}
