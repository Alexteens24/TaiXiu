package com.cortezromeo.taixiu.manager;

import com.cortezromeo.taixiu.TaiXiu;
import com.cortezromeo.taixiu.api.storage.ISession;
import com.cortezromeo.taixiu.api.CurrencyTyppe;
import com.cortezromeo.taixiu.api.TaiXiuResult;
import com.cortezromeo.taixiu.storage.SessionData;
import com.cortezromeo.taixiu.storage.SessionDataStorage;
import com.cortezromeo.taixiu.util.MessageUtil;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;

import static com.cortezromeo.taixiu.manager.DebugManager.debug;
import static com.cortezromeo.taixiu.util.MessageUtil.throwErrorMessage;

public class DatabaseManager {

    public static final List<String> togglePlayers = new CopyOnWriteArrayList<>();
    /** Active sessions only. Historical reads live in the bounded cache below. */
    public static final Map<Long, ISession> taiXiuData = new ConcurrentHashMap<>();
    private static final Map<Long, ISession> historyCache = Collections.synchronizedMap(
            new LinkedHashMap<>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, ISession> eldest) {
                    return size() > historyCacheSize();
                }
            });
    private static final Map<Long, CompletableFuture<ISession>> historyLoads = new ConcurrentHashMap<>();
    public static volatile long lastSession;

    private static int historyCacheSize() {
        return Math.max(1, TaiXiu.plugin.getConfig().getInt("database.history-cache-size", 256));
    }

    public static ISession getSessionData(long session) {
        ISession active = taiXiuData.get(session);
        return active != null ? active : historyCache.get(session);
    }

    public static long getLastSession() {

        try {
            long activeMaximum = taiXiuData.keySet().stream().mapToLong(Long::longValue).max().orElse(0);
            lastSession = Math.max(lastSession, activeMaximum);
        } catch (Exception e) {
            MessageUtil.throwErrorMessage("<databasemanager.java<getLastSession>>" + e);
            return getLastSessionFromFile();
        }

        return lastSession;
    }

    public static long getLastSessionFromFile() {
        lastSession = SessionDataStorage.getLastSessionId();
        return lastSession;
    }

    public static void loadSessionData(long session) {

        debug("LOADING SESSION DATA", "Session number " + session);
        if (taiXiuData.containsKey(session))
            return;

        ISession data = SessionDataStorage.getSessionData(session);
        taiXiuData.put(session, data);
        lastSession = Math.max(lastSession, session);
        debug("SESSION DATA LOADED", "Session number " + session);
    }

    public static CompletableFuture<ISession> loadSessionDataAsync(long session) {
        ISession cached = getSessionData(session);
        if (cached != null) return CompletableFuture.completedFuture(cached);
        return historyLoads.computeIfAbsent(session, id -> SessionDataStorage.existsAsync(id).thenCompose(exists -> {
            if (!exists) return CompletableFuture.completedFuture(null);
            return SessionDataStorage.getSessionDataAsync(id).thenApply(data -> {
                historyCache.put(id, data);
                return data;
            });
        }).whenComplete((ignored, error) -> historyLoads.remove(id)));
    }

    public static ISession createSessionData(long session) {
        CurrencyTyppe currency = CurrencyTyppe.valueOf(TaiXiu.plugin.getConfig()
                .getString("currency-settings.default", "VAULT").toUpperCase(Locale.ROOT));
        SessionData data = new SessionData(session, 0, 0, 0, TaiXiuResult.NONE,
                new HashMap<>(), new HashMap<>(), currency);
        taiXiuData.put(session, data);
        historyCache.remove(session);
        lastSession = Math.max(lastSession, session);
        SessionDataStorage.saveAsync(session, SessionData.copyOf(data)).exceptionally(error -> {
            TaiXiu.scheduler.runGlobal(() -> TaiXiuManager.setState(com.cortezromeo.taixiu.api.TaiXiuState.PAUSING));
            throwErrorMessage("Could not create session #" + session + " in SQLite: " + error.getMessage());
            return null;
        });
        return data;
    }

    public static void unloadSessionData(long session) {

        debug("UNLOADING SESSION DATA", "Session number " + session);
        taiXiuData.remove(session);
        debug("SESSION DATA UNLOADED", "Session number " + session);
    }

    public static boolean checkExistsFileData(long session) {

        if (getSessionData(session) != null)
            return true;

        return SessionDataStorage.exists(session);
    }

    public static List<ISession> activeSnapshots() {
        return taiXiuData.values().stream().map(SessionData::copyOf).map(ISession.class::cast).toList();
    }

    public static void clearHistoryCache() {
        historyCache.clear();
    }
}
