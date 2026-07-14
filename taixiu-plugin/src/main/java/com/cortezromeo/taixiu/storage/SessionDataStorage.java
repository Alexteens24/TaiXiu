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

public class SessionDataStorage {

    private static SessionStorage STORAGE;
    private static ExecutorService databaseExecutor;

    public static void init() {
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
        return CompletableFuture.supplyAsync(() -> STORAGE.getData(session), databaseExecutor);
    }

    public static CompletableFuture<Void> saveAsync(long session, ISession data) {
        return CompletableFuture.runAsync(() -> STORAGE.saveData(session, data), databaseExecutor);
    }

    public static CompletableFuture<Void> recordIntentAsync(JournalEntry entry) {
        return CompletableFuture.runAsync(() -> ((SQLiteSessionStorage) STORAGE).recordIntent(entry), databaseExecutor);
    }

    public static CompletableFuture<Void> recordIntentsAsync(List<JournalEntry> entries) {
        return CompletableFuture.runAsync(() -> ((SQLiteSessionStorage) STORAGE).recordIntents(entries), databaseExecutor);
    }

    public static CompletableFuture<Void> prepareSettlementAsync(ISession data, List<JournalEntry> payouts) {
        return CompletableFuture.runAsync(() -> {
            ((SQLiteSessionStorage) STORAGE).prepareSettlement(data, payouts);
            com.cortezromeo.taixiu.manager.DatabaseManager.clearHistoryCache();
        }, databaseExecutor);
    }

    public static CompletableFuture<Void> markJournalAsync(String id, String status) {
        return CompletableFuture.runAsync(() -> {
            ((SQLiteSessionStorage) STORAGE).markJournal(id, status);
            clearHistoryAfterTerminal(status);
        }, databaseExecutor);
    }

    public static CompletableFuture<Void> markJournalAsync(String id, String status, String error) {
        return CompletableFuture.runAsync(() -> {
            ((SQLiteSessionStorage) STORAGE).markJournal(id, status, error);
            clearHistoryAfterTerminal(status);
        }, databaseExecutor);
    }

    public static CompletableFuture<Void> markJournalAsync(String id, String status, String error,
                                                            String actor, String reason) {
        return CompletableFuture.runAsync(() -> {
            ((SQLiteSessionStorage) STORAGE).markJournal(id, status, error, actor, reason);
            clearHistoryAfterTerminal(status);
        }, databaseExecutor);
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
        return CompletableFuture.supplyAsync(() -> ((SQLiteSessionStorage) STORAGE).unresolvedJournal(), databaseExecutor);
    }

    public static CompletableFuture<Optional<JournalEntry>> findJournalAsync(String id) {
        return CompletableFuture.supplyAsync(() -> ((SQLiteSessionStorage) STORAGE).findJournal(id), databaseExecutor);
    }

    public static boolean betExists(JournalEntry entry) {
        return ((SQLiteSessionStorage) STORAGE).betExists(entry.sessionId(), entry.playerId());
    }

    public static boolean exists(long session) {
        return ((SQLiteSessionStorage) STORAGE).exists(session);
    }

    public static CompletableFuture<Boolean> existsAsync(long session) {
        return CompletableFuture.supplyAsync(() -> ((SQLiteSessionStorage) STORAGE).exists(session), databaseExecutor);
    }

    public static long getLastSessionId() {
        return ((SQLiteSessionStorage) STORAGE).getLastSessionId();
    }

    public static long getBettingDeadline(long session) {
        return ((SQLiteSessionStorage) STORAGE).getBettingDeadline(session);
    }

    public static CompletableFuture<Void> updateBettingDeadlineAsync(long session, long deadline) {
        return CompletableFuture.runAsync(
                () -> ((SQLiteSessionStorage) STORAGE).updateBettingDeadline(session, deadline), databaseExecutor);
    }

    public static CompletableFuture<Void> reloadRetentionAsync() {
        String mode = TaiXiu.plugin.getConfig().getString("database.retention.mode", "ALL");
        long days = TaiXiu.plugin.getConfig().getLong("database.retention.days", 90);
        long count = TaiXiu.plugin.getConfig().getLong("database.retention.max-sessions", 10_000);
        return CompletableFuture.runAsync(() -> {
            ((SQLiteSessionStorage) STORAGE).updateRetention(mode, days, count);
            com.cortezromeo.taixiu.manager.DatabaseManager.clearHistoryCache();
        }, databaseExecutor);
    }

    public static void flushAndClose(Collection<ISession> finalSnapshots) {
        CompletableFuture<Void> flush = CompletableFuture.runAsync(() -> {
            for (ISession snapshot : finalSnapshots) STORAGE.saveData(snapshot.getSession(), snapshot);
        }, databaseExecutor);
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

}
