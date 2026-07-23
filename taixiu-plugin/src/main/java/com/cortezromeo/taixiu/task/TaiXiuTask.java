package com.cortezromeo.taixiu.task;

import com.cortezromeo.taixiu.TaiXiu;
import com.cortezromeo.taixiu.api.CurrencyTyppe;
import com.cortezromeo.taixiu.api.TaiXiuResult;
import com.cortezromeo.taixiu.api.TaiXiuState;
import com.cortezromeo.taixiu.api.event.SessionSwapEvent;
import com.cortezromeo.taixiu.api.storage.ISession;
import com.cortezromeo.taixiu.storage.SessionDataStorage;
import com.cortezromeo.taixiu.language.Messages;
import com.cortezromeo.taixiu.manager.BossBarManager;
import com.cortezromeo.taixiu.manager.DatabaseManager;
import com.cortezromeo.taixiu.manager.TaiXiuManager;
import com.cortezromeo.taixiu.util.MessageUtil;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;

import static com.cortezromeo.taixiu.manager.DebugManager.debug;

public class TaiXiuTask implements Runnable {
    private volatile ScheduledTask task;
    private volatile int time;
    private volatile TaiXiuState state;
    private volatile ISession data;
    private volatile boolean settlementInProgress;
    private volatile boolean rolloverExpiryInProgress;
    private volatile long rolloverExpiredSession = Long.MIN_VALUE;

    public TaiXiuTask(int time) {
        long latestSession = DatabaseManager.getLastSessionFromFile();
        DatabaseManager.loadSessionData(latestSession);
        data = DatabaseManager.getSessionData(latestSession);
        long deadline = SessionDataStorage.getBettingDeadline(data.getSession());
        if (deadline > 0) {
            this.time = (int) Math.max(0, Math.ceil((deadline - System.currentTimeMillis()) / 1000.0));
        } else {
            this.time = time;
            SessionDataStorage.saveAsync(data.getSession(), com.cortezromeo.taixiu.storage.SessionData.copyOf(data))
                    .exceptionally(error -> databaseFailure("create startup session", error));
            SessionDataStorage.updateBettingDeadlineAsync(data.getSession(), System.currentTimeMillis() + time * 1000L)
                    .exceptionally(error -> databaseFailure("persist startup deadline", error));
        }
        this.state = TaiXiuManager.isHealthy() ? TaiXiuState.PLAYING : TaiXiuState.PAUSING;
        long cutoffDeadline = System.currentTimeMillis()
                + Math.max(0, this.time - TaiXiu.plugin.getConfig().getInt("bet-settings.disable-while-remaining")) * 1000L;
        TaiXiuManager.activateRolloverOffers(data.getSession(), cutoffDeadline)
                .exceptionally(error -> databaseFailure("activate startup rollover offers", error));
        this.task = TaiXiu.scheduler.runGlobalTimer(this, 1, 20L);

        debug("TAIXIU TASK", "RUNNING TASK ID: " + getTaskID() + " | SESSION NUMBER: " + data.getSession());
    }

    public ScheduledTask getWrappedTask() {
        return task;
    }

    public int getTaskID() {
        return task.hashCode();
    }

    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        this.time = time;
        SessionDataStorage.updateBettingDeadlineAsync(data.getSession(), System.currentTimeMillis() + time * 1000L)
                .exceptionally(error -> databaseFailure("update betting deadline", error));
    }

    public TaiXiuState getState() {
        return state;
    }

    public void setState(TaiXiuState state) {
        this.state = state;
    }

    public ISession getSession() {
        return data;
    }

    public void setSession(long session) {
        this.data = DatabaseManager.createSessionData(session);
    }

    public void setSession(ISession session) {
        this.data = session;
    }

    @Override
    public void run() {
        if (state == TaiXiuState.PLAYING) {
            try {
                if (settlementInProgress || rolloverExpiryInProgress) return;
                time--;

                int cutoff = TaiXiu.plugin.getConfig().getInt("bet-settings.disable-while-remaining");
                if (time <= cutoff && rolloverExpiredSession != getSession().getSession()) {
                    // Preserve an already elapsed persisted deadline on restart instead of extending the round.
                    time = Math.max(0, time);
                    rolloverExpiryInProgress = true;
                    long expiringSession = getSession().getSession();
                    TaiXiuManager.expireRolloverOffers(expiringSession).whenComplete((ignored, error) ->
                            TaiXiu.scheduler.runGlobal(() -> {
                                rolloverExpiryInProgress = false;
                                if (error != null) {
                                    TaiXiuManager.markUnhealthy("ROLLOVER_EXPIRY_FAILURE");
                                    MessageUtil.throwErrorMessage("Could not expire rollover offers: " + error.getMessage());
                                } else {
                                    rolloverExpiredSession = expiringSession;
                                }
                            }));
                    return;
                }

                if (getSession().getResult() != TaiXiuResult.NONE)
                    time = 0;

                if (time <= 0) {
                    if (TaiXiuManager.hasPendingBets()) {
                        time = 1;
                        return;
                    }
                    time = 0;
                    if ((getSession().getTaiPlayerSnapshot().isEmpty() && getSession().getXiuPlayerSnapshot().isEmpty()) && getSession().getResult() == TaiXiuResult.NONE) {
                        MessageUtil.sendBroadCast(Messages.NOT_ENOUGH_PLAYER.replace("%session%", String.valueOf(getSession().getSession())));
                    } else {
                        settlementInProgress = true;
                        TaiXiuManager.resultSeasonAsync(getSession(), 0, 0, 0).whenComplete((settled, error) ->
                                TaiXiu.scheduler.runGlobal(() -> {
                                    settlementInProgress = false;
                                    if (error != null || !Boolean.TRUE.equals(settled)) {
                                        time = 1;
                                        MessageUtil.throwErrorMessage("Session settlement will retry: "
                                                + (error == null ? "preparation failed" : error.getMessage()));
                                        return;
                                    }
                                    swapSession();
                                }));
                        return;
                    }
                    TaiXiuManager.setTime(TaiXiu.plugin.getConfig().getInt("task.taiXiuTask.time-per-session"));
                    TaiXiuManager.setCurrencyType(CurrencyTyppe.valueOf(TaiXiu.plugin.getConfig().getString("currency-settings.default").toUpperCase()));
                } else {
                    if (!DatabaseManager.togglePlayers.isEmpty())
                        for (String playerBossBar : DatabaseManager.togglePlayers)
                            BossBarManager.putValueBossBar(Bukkit.getPlayer(playerBossBar), time);
                }
            } catch (Exception e) {
                MessageUtil.throwErrorMessage("<taixiutask.java<run>>" + e);
                TaiXiuManager.markUnhealthy("TASK_FAILURE");
                time = Math.max(1, time);
            }
        }
    }

    private void swapSession() {
        ISession oldSessionData = getSession();
        long newSession = DatabaseManager.getLastSession() + 1;
        setSession(newSession);

        debug("SESSION SWAPPED", "Old session: " + oldSessionData.getSession() + " | New session: " + newSession);
        SessionSwapEvent event = new SessionSwapEvent(oldSessionData.snapshot(), getSession().snapshot());
        TaiXiu.plugin.getServer().getPluginManager().callEvent(event);
        DatabaseManager.unloadSessionData(oldSessionData.getSession());
        BossBarManager.onSessionSwap(oldSessionData, getSession());

        TaiXiuManager.setTime(TaiXiu.plugin.getConfig().getInt("task.taiXiuTask.time-per-session"));
        int cutoff = TaiXiu.plugin.getConfig().getInt("bet-settings.disable-while-remaining");
        long cutoffDeadline = System.currentTimeMillis() + Math.max(0, time - cutoff) * 1000L;
        TaiXiuManager.activateRolloverOffers(newSession, cutoffDeadline).exceptionally(error -> {
            databaseFailure("activate rollover offers", error);
            return null;
        });
        TaiXiuManager.setCurrencyType(CurrencyTyppe.valueOf(
                TaiXiu.plugin.getConfig().getString("currency-settings.default").toUpperCase()));
        for (String playerBossBar : DatabaseManager.togglePlayers)
            BossBarManager.putValueBossBar(Bukkit.getPlayer(playerBossBar), time);
    }

    public void cancel() {
        task.cancel();
    }

    private Void databaseFailure(String operation, Throwable error) {
        TaiXiu.scheduler.runGlobal(() -> TaiXiuManager.markUnhealthy("DATABASE_FAILURE: " + operation));
        MessageUtil.throwErrorMessage("Could not " + operation + " for session #" + data.getSession()
                + ": " + error.getMessage());
        return null;
    }
}
