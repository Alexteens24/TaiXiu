package com.cortezromeo.taixiu.storage;

import com.cortezromeo.taixiu.TaiXiu;
import com.cortezromeo.taixiu.api.storage.ISession;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Optional;
import java.util.Collection;
import java.util.function.Supplier;
import java.util.concurrent.RejectedExecutionException;

public class SessionDataStorage {

    private static SessionStorage STORAGE;
    private static ExecutorService databaseExecutor;
    private static final Object LIFECYCLE_LOCK = new Object();
    private static volatile boolean closing;

    public static void init() {
        closing = false;
        databaseExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "TaiXiu-SQLite");
            thread.setDaemon(true);
            return thread;
        });
        File file = new File(TaiXiu.plugin.getDataFolder(), TaiXiu.plugin.getConfig().getString("database.file", "taixiu.db"));
        try {
            SessionDataStorage.STORAGE = new SQLiteSessionStorage(file);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not initialize SQLite storage", exception);
        }
    }

    public static ISession getSessionData(long session) {
        return SessionDataStorage.STORAGE.getData(session);
    }

    public static CompletableFuture<ISession> getSessionDataAsync(long session) {
        return submit(() -> STORAGE.getData(session));
    }

    public static CompletableFuture<Void> saveAsync(long session, ISession data) {
        return submit(() -> STORAGE.saveData(session, data));
    }

    public static CompletableFuture<Void> recordIntentAsync(JournalEntry entry) {
        return submit(() -> ((SQLiteSessionStorage) STORAGE).recordIntent(entry));
    }

    public static CompletableFuture<Void> recordIntentsAsync(List<JournalEntry> entries) {
        return submit(() -> ((SQLiteSessionStorage) STORAGE).recordIntents(entries));
    }

    public static CompletableFuture<Void> prepareSettlementAsync(ISession data, List<JournalEntry> payouts) {
        return submit(() -> {
            ((SQLiteSessionStorage) STORAGE).prepareSettlement(data, payouts);
            com.cortezromeo.taixiu.manager.DatabaseManager.clearHistoryCache();
        });
    }

    public static CompletableFuture<SettlementPreparation> prepareSettlementAsync(
            ISession data, List<JournalEntry> payouts, List<RolloverOffer> offers, InsuranceSettings insuranceSettings) {
        return submit(() -> {
            SettlementPreparation result = ((SQLiteSessionStorage) STORAGE)
                    .prepareSettlement(data, payouts, offers, insuranceSettings);
            com.cortezromeo.taixiu.manager.DatabaseManager.clearHistoryCache();
            return result;
        });
    }

    public static CompletableFuture<Void> activateRolloverOffersAsync(long targetSessionId, long expiresAt) {
        return submit(() -> ((SQLiteSessionStorage) STORAGE).activateRolloverOffers(targetSessionId, expiresAt));
    }

    public static CompletableFuture<Optional<RolloverOffer>> findAvailableRolloverAsync(
            java.util.UUID playerId, long targetSessionId) {
        return submit(() -> ((SQLiteSessionStorage) STORAGE).findAvailableRollover(playerId, targetSessionId));
    }

    public static CompletableFuture<Optional<RolloverOffer>> consumeRolloverAsync(
            String offerId, long targetSessionId, com.cortezromeo.taixiu.api.TaiXiuResult side,
            BetMetadata metadata) {
        return submit(() -> ((SQLiteSessionStorage) STORAGE)
                .consumeRollover(offerId, targetSessionId, side, metadata));
    }

    public static CompletableFuture<Optional<JournalEntry>> prepareRolloverCashoutAsync(
            java.util.UUID playerId, long targetSessionId) {
        return submit(() -> ((SQLiteSessionStorage) STORAGE).prepareRolloverCashout(playerId, targetSessionId));
    }

    public static CompletableFuture<List<JournalEntry>> expireRolloverOffersAsync(long targetSessionId, long now) {
        return submit(() -> ((SQLiteSessionStorage) STORAGE).expireRolloverOffers(targetSessionId, now));
    }

    public static CompletableFuture<Void> markJournalAsync(String id, String status) {
        return submit(() -> {
            ((SQLiteSessionStorage) STORAGE).markJournal(id, status);
            clearHistoryAfterTerminal(status);
        });
    }

    public static CompletableFuture<Void> markJournalAsync(String id, String status, String error) {
        return submit(() -> {
            ((SQLiteSessionStorage) STORAGE).markJournal(id, status, error);
            clearHistoryAfterTerminal(status);
        });
    }

    public static CompletableFuture<Void> markJournalAsync(String id, String status, String error,
                                                            String actor, String reason) {
        return submit(() -> {
            ((SQLiteSessionStorage) STORAGE).markJournal(id, status, error, actor, reason);
            clearHistoryAfterTerminal(status);
        });
    }

    public static void recordIntent(JournalEntry entry) {
        ((SQLiteSessionStorage) STORAGE).recordIntent(entry);
    }

    public static void markJournal(String id, String status) {
        ((SQLiteSessionStorage) STORAGE).markJournal(id, status);
        clearHistoryAfterTerminal(status);
    }

    public static void markJournal(String id, String status, String error) {
        ((SQLiteSessionStorage) STORAGE).markJournal(id, status, error);
        clearHistoryAfterTerminal(status);
    }

    public static List<JournalEntry> unresolvedJournal() {
        return ((SQLiteSessionStorage) STORAGE).unresolvedJournal();
    }

    public static CompletableFuture<List<JournalEntry>> unresolvedJournalAsync() {
        return submit(() -> ((SQLiteSessionStorage) STORAGE).unresolvedJournal());
    }

    public static CompletableFuture<Optional<JournalEntry>> findJournalAsync(String id) {
        return submit(() -> ((SQLiteSessionStorage) STORAGE).findJournal(id));
    }

    public static boolean betExists(JournalEntry entry) {
        return ((SQLiteSessionStorage) STORAGE).betExists(entry.sessionId(), entry.playerId());
    }

    public static boolean exists(long session) {
        return ((SQLiteSessionStorage) STORAGE).exists(session);
    }

    public static CompletableFuture<Boolean> existsAsync(long session) {
        return submit(() -> ((SQLiteSessionStorage) STORAGE).exists(session));
    }

    public static long getLastSessionId() {
        return ((SQLiteSessionStorage) STORAGE).getLastSessionId();
    }

    public static long getBettingDeadline(long session) {
        return ((SQLiteSessionStorage) STORAGE).getBettingDeadline(session);
    }

    public static CompletableFuture<Void> updateBettingDeadlineAsync(long session, long deadline) {
        return submit(() -> ((SQLiteSessionStorage) STORAGE).updateBettingDeadline(session, deadline));
    }

    public static CompletableFuture<Void> reloadRetentionAsync() {
        String mode = TaiXiu.plugin.getConfig().getString("database.retention.mode", "ALL");
        long days = TaiXiu.plugin.getConfig().getLong("database.retention.days", 90);
        long count = TaiXiu.plugin.getConfig().getLong("database.retention.max-sessions", 10_000);
        return submit(() -> {
            ((SQLiteSessionStorage) STORAGE).updateRetention(mode, days, count);
            com.cortezromeo.taixiu.manager.DatabaseManager.clearHistoryCache();
        });
    }

    public static CompletableFuture<Void> markJournalsUnknownAsync(Collection<String> ids, String error) {
        return submit(() -> {
            SQLiteSessionStorage sqlite = (SQLiteSessionStorage) STORAGE;
            for (String id : ids) sqlite.markJournal(id, "UNKNOWN", error);
        });
    }

    public static void flushAndClose(Collection<ISession> finalSnapshots) {
        CompletableFuture<Void> flush;
        synchronized (LIFECYCLE_LOCK) {
            if (closing) return;
            closing = true;
            flush = CompletableFuture.runAsync(() -> {
                for (ISession snapshot : finalSnapshots) {
                    // Session mutations are write-through. Never let a stale shutdown snapshot overwrite a
                    // rollover bet that was atomically consumed on the database executor just before shutdown.
                    if (STORAGE instanceof SQLiteSessionStorage sqlite && sqlite.exists(snapshot.getSession())) continue;
                    STORAGE.saveData(snapshot.getSession(), snapshot);
                }
            }, databaseExecutor);
        }
        try {
            flush.get(15, TimeUnit.SECONDS);
        } catch (Exception exception) {
            if (TaiXiu.plugin != null)
                TaiXiu.plugin.getLogger().severe("Could not flush final SQLite snapshots: " + exception.getMessage());
        }
        if (databaseExecutor != null) {
            databaseExecutor.shutdown();
            try {
                if (!databaseExecutor.awaitTermination(10, TimeUnit.SECONDS)) databaseExecutor.shutdownNow();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                databaseExecutor.shutdownNow();
            }
        }
        if (STORAGE instanceof SQLiteSessionStorage sqlite) sqlite.close();
    }

    public static void close() {
        flushAndClose(List.of());
    }

    private static void clearHistoryAfterTerminal(String status) {
        if ("COMPLETED".equals(status) || "COMPENSATED".equals(status) || "FAILED".equals(status))
            com.cortezromeo.taixiu.manager.DatabaseManager.clearHistoryCache();
    }

    private static CompletableFuture<Void> submit(Runnable operation) {
        synchronized (LIFECYCLE_LOCK) {
            if (closing || databaseExecutor == null || databaseExecutor.isShutdown())
                return CompletableFuture.failedFuture(new RejectedExecutionException("SQLite storage is closing"));
            try {
                return CompletableFuture.runAsync(operation, databaseExecutor);
            } catch (RejectedExecutionException error) {
                return CompletableFuture.failedFuture(error);
            }
        }
    }

    private static <T> CompletableFuture<T> submit(Supplier<T> operation) {
        synchronized (LIFECYCLE_LOCK) {
            if (closing || databaseExecutor == null || databaseExecutor.isShutdown())
                return CompletableFuture.failedFuture(new RejectedExecutionException("SQLite storage is closing"));
            try {
                return CompletableFuture.supplyAsync(operation, databaseExecutor);
            } catch (RejectedExecutionException error) {
                return CompletableFuture.failedFuture(error);
            }
        }
    }

}
