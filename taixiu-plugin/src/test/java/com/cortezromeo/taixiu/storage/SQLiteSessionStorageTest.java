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

    @Test
    void rolloverOfferIsConsumedAtomicallyIntoNextSession() throws Exception {
        File database = new File(directory, "rollover.db");
        UUID playerId = UUID.randomUUID();
        try (SQLiteSessionStorage storage = storage(database)) {
            SessionData source = activeSession(30, "Alex", playerId, 500);
            source.registerPlayer("Alex", new BetMetadata(playerId, 5, true, true,
                    BetMetadata.FundingSource.WALLET, 0));
            source.setResult(TaiXiuResult.TAI);
            RolloverOffer offer = new RolloverOffer("offer", 30, 31, playerId, "Alex",
                    CurrencyTyppe.VAULT, 950, 1, 0, "PENDING_TARGET", null);
            storage.prepareSettlement(source, List.of(), List.of(offer),
                    new InsuranceSettings(false, 3, 20, 1_000));
            storage.saveData(31, new SessionData(31, 0, 0, 0, TaiXiuResult.NONE,
                    new HashMap<>(), new HashMap<>(), CurrencyTyppe.VAULT));
            storage.activateRolloverOffers(31, System.currentTimeMillis() + 60_000);

            RolloverOffer available = storage.findAvailableRollover(playerId, 31).orElseThrow();
            BetMetadata metadata = new BetMetadata(playerId, 4, true, false,
                    BetMetadata.FundingSource.ESCROW, 1);
            assertTrue(storage.consumeRollover(available.id(), 31, TaiXiuResult.XIU, metadata).isPresent());
            assertTrue(storage.findAvailableRollover(playerId, 31).isEmpty());
            SessionData target = (SessionData) storage.getData(31);
            assertEquals(950, target.getXiuPlayerSnapshot().get("Alex"));
            assertEquals(BetMetadata.FundingSource.ESCROW, target.getBetMetadata("Alex").fundingSource());
        }
    }

    @Test
    void expiredRolloverCreatesOneJournaledCashout() throws Exception {
        File database = new File(directory, "cashout.db");
        UUID playerId = UUID.randomUUID();
        try (SQLiteSessionStorage storage = storage(database)) {
            SessionData source = activeSession(40, "Alex", playerId, 500);
            source.setResult(TaiXiuResult.TAI);
            storage.prepareSettlement(source, List.of(), List.of(new RolloverOffer("offer", 40, 41,
                    playerId, "Alex", CurrencyTyppe.VAULT, 1_000, 1, 0, "PENDING_TARGET", null)),
                    new InsuranceSettings(false, 3, 20, 1_000));
            storage.activateRolloverOffers(41, 1);
            List<JournalEntry> cashouts = storage.expireRolloverOffers(41, 2);
            assertEquals(1, cashouts.size());
            assertEquals("ROLLOVER_CASHOUT", cashouts.getFirst().context());
            assertTrue(storage.expireRolloverOffers(41, 3).isEmpty());
        }
    }

    @Test
    void thirdEligibleWalletLossReceivesCappedInsurance() throws Exception {
        File database = new File(directory, "insurance.db");
        UUID playerId = UUID.randomUUID();
        try (SQLiteSessionStorage storage = storage(database)) {
            InsuranceSettings settings = new InsuranceSettings(true, 3, 20, 150);
            for (int i = 1; i <= 3; i++) {
                SessionData session = activeSession(50 + i, "Alex", playerId, 1_000);
                session.registerPlayer("Alex", new BetMetadata(playerId, 0, false, true,
                        BetMetadata.FundingSource.WALLET, 0));
                session.setResult(TaiXiuResult.XIU);
                SettlementPreparation prepared = storage.prepareSettlement(session, List.of(), List.of(), settings);
                assertEquals(i == 3 ? 1 : 0, prepared.insurancePayouts().size());
                if (i == 3) {
                    assertEquals(150, prepared.insurancePayouts().getFirst().amount());
                    assertEquals("INSURANCE_REFUND", prepared.insurancePayouts().getFirst().context());
                }
            }
        }
    }

    @Test
    void committedSettlementSucceedsWhenRetentionCleanupFails() throws Exception {
        File database = new File(directory, "settlement-cleanup.db");
        try (SQLiteSessionStorage storage = storage(database, "COUNT", 90, 1)) {
            SessionData old = activeSession(60, "Old", UUID.randomUUID(), 100);
            old.setResult(TaiXiuResult.TAI);
            storage.saveData(60, old);
            installSessionDeleteFailure(database);

            SessionData current = activeSession(61, "Alex", UUID.randomUUID(), 100);
            current.setResult(TaiXiuResult.TAI);
            assertDoesNotThrow(() -> storage.prepareSettlement(current, List.of()));
            assertEquals(TaiXiuResult.TAI, storage.getData(61).getResult());
        }
    }

    @Test
    void committedJournalUpdateSucceedsWhenRetentionCleanupFails() throws Exception {
        File database = new File(directory, "journal-cleanup.db");
        UUID playerId = UUID.randomUUID();
        try (SQLiteSessionStorage storage = storage(database, "COUNT", 90, 1)) {
            SessionData old = activeSession(70, "Old", UUID.randomUUID(), 100);
            old.setResult(TaiXiuResult.TAI);
            storage.saveData(70, old);

            SessionData current = activeSession(71, "Alex", playerId, 100);
            current.setResult(TaiXiuResult.TAI);
            JournalEntry payout = new JournalEntry("cleanup-payout", 71, playerId, "Alex",
                    CurrencyTyppe.VAULT, "PAYOUT", 200);
            storage.prepareSettlement(current, List.of(payout));
            installSessionDeleteFailure(database);

            assertDoesNotThrow(() -> storage.markJournal(payout.id(), "COMPLETED"));
            assertEquals("COMPLETED", storage.findJournal(payout.id()).orElseThrow().status());
        }
    }

    @Test
    void retentionKeepsSessionsBackingLiveRolloverEscrow() throws Exception {
        for (String mode : List.of("COUNT", "DAYS")) {
            File database = new File(directory, "retention-" + mode + ".db");
            UUID playerId = UUID.randomUUID();
            try (SQLiteSessionStorage storage = storage(database, mode, 1, 1)) {
                SessionData source = activeSession(80, "Alex", playerId, 100);
                source.setResult(TaiXiuResult.TAI);
                storage.prepareSettlement(source, List.of(), List.of(new RolloverOffer(
                                "offer-" + mode, 80, 81, playerId, "Alex", CurrencyTyppe.VAULT,
                                200, 1, 0, "PENDING_TARGET", null)),
                        new InsuranceSettings(false, 3, 20, 0));

                if ("COUNT".equals(mode)) {
                    SessionData newer = activeSession(81, "New", UUID.randomUUID(), 100);
                    newer.setResult(TaiXiuResult.TAI);
                    storage.saveData(81, newer);
                } else {
                    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.getAbsolutePath())) {
                        connection.createStatement().executeUpdate("UPDATE sessions SET settled_at=0 WHERE id=80");
                    }
                }

                storage.cleanupRetention();
                assertTrue(storage.exists(80), mode + " retention deleted live rollover escrow source");
            }
        }
    }

    @Test
    void staleRolloverOfferMovesToCurrentSessionAndTerminalOfferIsCleaned() throws Exception {
        File database = new File(directory, "rollover-recovery.db");
        UUID playerId = UUID.randomUUID();
        try (SQLiteSessionStorage storage = storage(database)) {
            SessionData source = activeSession(90, "Alex", playerId, 100);
            source.setResult(TaiXiuResult.TAI);
            storage.prepareSettlement(source, List.of(), List.of(new RolloverOffer(
                            "stale-offer", 90, 91, playerId, "Alex", CurrencyTyppe.VAULT,
                            200, 1, 0, "PENDING_TARGET", null)),
                    new InsuranceSettings(false, 3, 20, 0));
            storage.saveData(95, new SessionData(95, 0, 0, 0, TaiXiuResult.NONE,
                    new HashMap<>(), new HashMap<>(), CurrencyTyppe.VAULT));

            storage.activateRolloverOffers(95, System.currentTimeMillis() + 60_000);
            RolloverOffer recovered = storage.findAvailableRollover(playerId, 95).orElseThrow();
            assertEquals(95, recovered.targetSessionId());

            BetMetadata metadata = new BetMetadata(playerId, 0, true, false,
                    BetMetadata.FundingSource.ESCROW, 1);
            assertTrue(storage.consumeRollover(recovered.id(), 95, TaiXiuResult.TAI, metadata).isPresent());
            storage.cleanupRetention();
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.getAbsolutePath());
                 var row = connection.createStatement().executeQuery(
                         "SELECT COUNT(*) FROM rollover_offers WHERE id='stale-offer'")) {
                assertTrue(row.next());
                assertEquals(0, row.getInt(1));
            }
        }
    }

    @Test
    void pendingRolloverTargetPreventsSessionCounterFromResetting() throws Exception {
        File database = new File(directory, "rollover-session-seed.db");
        UUID playerId = UUID.randomUUID();
        try (SQLiteSessionStorage storage = storage(database)) {
            SessionData source = activeSession(100, "Alex", playerId, 100);
            source.setResult(TaiXiuResult.TAI);
            storage.prepareSettlement(source, List.of(), List.of(new RolloverOffer(
                            "seed-offer", 100, 101, playerId, "Alex", CurrencyTyppe.VAULT,
                            200, 1, 0, "PENDING_TARGET", null)),
                    new InsuranceSettings(false, 3, 20, 0));
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.getAbsolutePath())) {
            connection.createStatement().executeUpdate("DELETE FROM sessions");
        }
        try (SQLiteSessionStorage storage = storage(database)) {
            assertEquals(101, storage.getLastSessionId());
            assertTrue(storage.exists(100));
            storage.activateRolloverOffers(101, System.currentTimeMillis() + 60_000);
            JournalEntry cashout = storage.prepareRolloverCashout(playerId, 101).orElseThrow();
            assertEquals(100, cashout.sessionId());
        }
    }

    private void installSessionDeleteFailure(File database) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.getAbsolutePath())) {
            connection.createStatement().executeUpdate(
                    "CREATE TRIGGER reject_session_cleanup BEFORE DELETE ON sessions " +
                            "BEGIN SELECT RAISE(ABORT, 'forced cleanup failure'); END");
        }
    }

    private SQLiteSessionStorage storage(File database) throws Exception {
        return storage(database, "ALL", 90, 10_000);
    }

    private SQLiteSessionStorage storage(File database, String mode, long days, long count) throws Exception {
        return new SQLiteSessionStorage(database, directory, CurrencyTyppe.VAULT, mode, days, count, false);
    }

    private SessionData activeSession(long id, String name, UUID playerId, long stake) {
        SessionData session = new SessionData(id, 0, 0, 0, TaiXiuResult.NONE,
                new HashMap<>(), new HashMap<>(), CurrencyTyppe.VAULT);
        session.registerPlayer(name, playerId, false);
        session.addTaiPlayer(name, stake);
        return session;
    }
}
