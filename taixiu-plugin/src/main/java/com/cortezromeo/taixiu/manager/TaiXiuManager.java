package com.cortezromeo.taixiu.manager;

import com.cortezromeo.taixiu.TaiXiu;
import com.cortezromeo.taixiu.api.CurrencyTyppe;
import com.cortezromeo.taixiu.api.TaiXiuResult;
import com.cortezromeo.taixiu.api.TaiXiuState;
import com.cortezromeo.taixiu.api.event.PlayerBetEvent;
import com.cortezromeo.taixiu.api.event.PlayerBetPreEvent;
import com.cortezromeo.taixiu.api.event.SessionResultEvent;
import com.cortezromeo.taixiu.api.storage.ISession;
import com.cortezromeo.taixiu.enums.SoundType;
import com.cortezromeo.taixiu.language.Messages;
import com.cortezromeo.taixiu.task.TaiXiuTask;
import com.cortezromeo.taixiu.economy.CurrencyGateway;
import com.cortezromeo.taixiu.economy.CurrencyTransaction;
import com.cortezromeo.taixiu.domain.DiceRules;
import com.cortezromeo.taixiu.domain.PayoutCalculator;
import com.cortezromeo.taixiu.storage.JournalEntry;
import com.cortezromeo.taixiu.storage.BetMetadata;
import com.cortezromeo.taixiu.storage.InsuranceSettings;
import com.cortezromeo.taixiu.storage.RolloverOffer;
import com.cortezromeo.taixiu.storage.SettlementPreparation;
import com.cortezromeo.taixiu.storage.SessionData;
import com.cortezromeo.taixiu.storage.SessionDataStorage;
import com.cortezromeo.taixiu.util.MessageUtil;
import com.cortezromeo.taixiu.util.BetPermissionPolicy;
import com.cortezromeo.taixiu.geyserform.RolloverGeyserForm;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.cortezromeo.taixiu.manager.DebugManager.debug;
import static com.cortezromeo.taixiu.util.MessageUtil.sendBroadCast;
import static com.cortezromeo.taixiu.util.MessageUtil.sendMessage;

public class TaiXiuManager {

    private static TaiXiuTask taiXiuTask = null;
    private static final Set<UUID> pendingBets = ConcurrentHashMap.newKeySet();
    private static final Map<Long, CompletableFuture<Boolean>> settlementFutures = new HashMap<>();
    private static final Set<String> healthBlocks = ConcurrentHashMap.newKeySet();
    private static final Set<CompletableFuture<Void>> inFlightCurrency = ConcurrentHashMap.newKeySet();
    private static final Map<CompletableFuture<Void>, String> inFlightJournalIds = new ConcurrentHashMap<>();
    private static final Object CURRENCY_LIFECYCLE_LOCK = new Object();
    private static volatile boolean acceptingTransactions = true;
    private static volatile boolean shuttingDown;

    public static TaiXiuTask getTaiXiuTask() {
        return taiXiuTask;
    }

    public static void startTask(int time) {
        taiXiuTask = new TaiXiuTask(time);
    }

    public static TaiXiuState getState() {
        return getTaiXiuTask().getState();
    }

    public static void setState(TaiXiuState state) {
        if (state == TaiXiuState.PLAYING && !isHealthy())
            throw new IllegalStateException("TaiXiu health lock is active: " + healthSummary());
        getTaiXiuTask().setState(state);
    }

    public static boolean isHealthy() {
        return acceptingTransactions && healthBlocks.isEmpty();
    }

    public static String healthSummary() {
        if (!acceptingTransactions) return "SHUTTING_DOWN";
        return healthBlocks.isEmpty() ? "HEALTHY" : String.join("; ", healthBlocks);
    }

    public static void markUnhealthy(String reason) {
        healthBlocks.add(reason);
        if (taiXiuTask != null) taiXiuTask.setState(TaiXiuState.PAUSING);
    }

    public static void acknowledgeHealth(String actor) {
        TaiXiu.plugin.getLogger().warning(actor + " acknowledged and cleared TaiXiu health lock: " + healthSummary());
        healthBlocks.clear();
    }

    private static void refreshTransactionHealth() {
        SessionDataStorage.unresolvedJournalAsync().whenComplete((entries, error) -> {
            if (error == null && entries.isEmpty()) healthBlocks.remove("UNRESOLVED_TRANSACTIONS");
        });
    }

    public static void beginShutdown() {
        synchronized (CURRENCY_LIFECYCLE_LOCK) {
            acceptingTransactions = false;
            shuttingDown = true;
        }
        if (taiXiuTask != null) taiXiuTask.setState(TaiXiuState.PAUSING);
    }

    public static void beginStartup() {
        synchronized (CURRENCY_LIFECYCLE_LOCK) {
            acceptingTransactions = false;
            shuttingDown = false;
        }
        healthBlocks.clear();
        pendingBets.clear();
        settlementFutures.clear();
        inFlightCurrency.clear();
        inFlightJournalIds.clear();
    }

    public static void finishStartup() {
        acceptingTransactions = true;
    }

    public static Set<String> awaitInFlightTransactions(long timeout, TimeUnit unit) {
        CompletableFuture<?>[] barriers = inFlightCurrency.toArray(CompletableFuture[]::new);
        if (barriers.length == 0) return Set.of();
        try {
            CompletableFuture.allOf(barriers).get(timeout, unit);
            return Set.of();
        } catch (TimeoutException timeoutError) {
            Set<String> unresolved = Set.copyOf(inFlightJournalIds.values());
            TaiXiu.plugin.getLogger().severe("Timed out waiting for " + inFlightCurrency.size()
                    + " economy operation(s); marking " + unresolved.size() + " journal(s) UNKNOWN.");
            return unresolved;
        } catch (Exception error) {
            TaiXiu.plugin.getLogger().severe("Could not await economy operations: " + error.getMessage());
            return Set.copyOf(inFlightJournalIds.values());
        }
    }

    public static int getTimeLeft() {
        return getTaiXiuTask().getTime();
    }

    public static void setTime(int time) {
        getTaiXiuTask().setTime(time);
        BossBarManager.timePerSession = time;
    }

    public static void setCurrencyType(CurrencyTyppe currencyType) {
        ISession session = getTaiXiuTask().getSession();
        if (hasPendingBets() || !session.getTaiPlayerSnapshot().isEmpty() || !session.getXiuPlayerSnapshot().isEmpty())
            throw new IllegalStateException("Currency cannot change after betting has started");
        session.setCurrencyType(currencyType);
        SessionDataStorage.saveAsync(session.getSession(), SessionData.copyOf(session)).exceptionally(error -> {
            TaiXiu.scheduler.runGlobal(() -> markUnhealthy("DATABASE_CURRENCY_SAVE_FAILURE"));
            MessageUtil.throwErrorMessage("Could not persist currency for session #" + session.getSession()
                    + ": " + error.getMessage());
            return null;
        });
    }

    public static ISession getSessionData() {
        return getTaiXiuTask().getSession();
    }

    /** @deprecated Replacing a live session is unsafe and retained only for compatibility. */
    @Deprecated(since = "3.0.0")
    public static void setSessionData(ISession sessionData) {
        if (sessionData == null || getTaiXiuTask() == null)
            throw new IllegalArgumentException("An active session is required");
        if (hasPendingBets() || !settlementFutures.isEmpty())
            throw new IllegalStateException("Cannot replace a session while transactions are in progress");
        getTaiXiuTask().setSession(sessionData);
        DatabaseManager.taiXiuData.put(sessionData.getSession(), sessionData);
        SessionDataStorage.saveAsync(sessionData.getSession(), SessionData.copyOf(sessionData));
    }

    /**
     * @deprecated This method only checks the active and bounded history caches and never blocks for SQLite.
     * Use {@link #getSessionDataAsync(long)} when historical data may not be cached.
     */
    @Deprecated(since = "3.0.0")
    public static ISession getSessionData(long session) {
        return DatabaseManager.getSessionData(session);
    }

    public static CompletableFuture<ISession> getSessionDataAsync(long session) {
        return DatabaseManager.loadSessionDataAsync(session);
    }

    public static void playerBet(Player player, long money, TaiXiuResult result) {
        if (!acceptingTransactions || !healthBlocks.isEmpty()) {
            sendMessage(player, "&cTaiXiu is paused for a safety check. Please try again later.");
            return;
        }
        if (!Bukkit.isOwnedByCurrentRegion(player)) {
            TaiXiu.scheduler.runEntity(player, () -> playerBet(player, money, result));
            return;
        }
        PlayerBetPreEvent preEvent = new PlayerBetPreEvent(player, result, money);
        Bukkit.getPluginManager().callEvent(preEvent);
        if (preEvent.isCancelled()) return;
        String playerName = player.getName();
        UUID playerId = player.getUniqueId();
        FileConfiguration cfg = TaiXiu.plugin.getConfig();
        double effectiveTax = BetPermissionPolicy.effectiveTax(player, cfg.getDouble("bet-settings.tax"));
        boolean rolloverEligible = cfg.getBoolean("rollover.enabled") && player.hasPermission("taixiu.rollover");
        boolean insuranceEligible = cfg.getBoolean("insurance.enabled")
                && player.hasPermission("taixiu.insurance.claim");
        if (!Bukkit.isGlobalTickThread()) {
            TaiXiu.scheduler.runGlobal(() -> playerBetGlobal(player, playerId, playerName, effectiveTax,
                    rolloverEligible, insuranceEligible, money, result));
            return;
        }
        playerBetGlobal(player, playerId, playerName, effectiveTax, rolloverEligible, insuranceEligible, money, result);
    }

    private static void playerBetGlobal(Player player, UUID playerId, String pName, double effectiveTax,
                                        boolean rolloverEligible, boolean insuranceEligible,
                                        long money, TaiXiuResult result) {
        ISession data = getSessionData();
        FileConfiguration cfg = TaiXiu.plugin.getConfig();

        if (getState() != TaiXiuState.PLAYING || data.getResult() != TaiXiuResult.NONE
                || settlementFutures.containsKey(data.getSession())) {
            sendMessage(player, Messages.LATE_BET
                    .replace("%time%", String.valueOf(Math.max(0, TaiXiuManager.getTimeLeft())))
                    .replace("%configDisableTime%", String.valueOf(cfg.getInt("bet-settings.disable-while-remaining"))));
            return;
        }

        if (result != TaiXiuResult.TAI && result != TaiXiuResult.XIU) {
            sendMessage(player, Messages.INVALID_BET.replace("%bet%", String.valueOf(result)));
            return;
        }
        if (money <= 0 || pendingBets.contains(playerId)) {
            sendMessage(player, Messages.INVALID_CURRENCY
                    .replace("%currencyName%", MessageUtil.getCurrencyName(data.getCurrencyType()))
                    .replace("%currencySymbol%", MessageUtil.getCurrencySymbol(data.getCurrencyType())));
            return;
        }

        Map<String, Long> xiuBets = data.getXiuPlayerSnapshot();
        Map<String, Long> taiBets = data.getTaiPlayerSnapshot();
        if (xiuBets.containsKey(pName) || taiBets.containsKey(pName)) {
            sendMessage(player, Messages.ALREADY_BET
                    .replace("%bet%", MessageUtil.getFormatResultName((xiuBets.containsKey(pName)
                            ? TaiXiuResult.XIU
                            : TaiXiuResult.TAI)))
                    .replace("%currencyName%", MessageUtil.getCurrencyName(data.getCurrencyType()))
                    .replace("%currencySymbol%", MessageUtil.getCurrencySymbol(data.getCurrencyType()))
                    .replace("%money%", (xiuBets.containsKey(pName)
                            ? MessageUtil.getFormatMoneyDisplay(xiuBets.get(pName))
                            : MessageUtil.getFormatMoneyDisplay(taiBets.get(pName)))));
            return;
        }

        int configDisableTime = cfg.getInt("bet-settings.disable-while-remaining");
        if (TaiXiuManager.getTimeLeft() <= configDisableTime) {
            sendMessage(player, Messages.LATE_BET
                    .replace("%time%", String.valueOf(TaiXiuManager.getTimeLeft()))
                    .replace("%configDisableTime%", String.valueOf(configDisableTime)));
            return;
        }

        if (!TaiXiu.currencies.supports(data.getCurrencyType())) {
            markUnhealthy("CURRENCY_PROVIDER_UNAVAILABLE");
            MessageUtil.throwErrorMessage("Currency provider unavailable for active session: " + data.getCurrencyType());
            sendMessage(player, Messages.NOT_ENOUGH_CURRENCY
                    .replace("%currencyName%", MessageUtil.getCurrencyName(data.getCurrencyType()))
                    .replace("%currencySymbol%", MessageUtil.getCurrencySymbol(data.getCurrencyType())));
            return;
        }

        CurrencyGateway gateway = TaiXiu.currencies.gateway(data.getCurrencyType());
        org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
        long minBet = cfg.getLong("bet-settings.min-bet");
        if (money < minBet) {
            sendMessage(player, Messages.MIN_BET
                    .replace("%currencyName%", MessageUtil.getCurrencyName(data.getCurrencyType()))
                    .replace("%currencySymbol%", MessageUtil.getCurrencySymbol(data.getCurrencyType()))
                    .replace("%minBet%", MessageUtil.getFormatMoneyDisplay(minBet)));
            return;
        }

        long maxBet = BetPermissionPolicy.effectiveMaxBet(player, cfg.getLong("bet-settings.max-bet"));
        long providerSafeStake = gateway.maximumTransaction() / 2;
        if (money > maxBet || money > providerSafeStake) {
            sendMessage(player, Messages.MAX_BET
                    .replace("%currencyName%", MessageUtil.getCurrencyName(data.getCurrencyType()))
                    .replace("%currencySymbol%", MessageUtil.getCurrencySymbol(data.getCurrencyType()))
                    .replace("%maxBet%", MessageUtil.getFormatMoneyDisplay(maxBet)));
            return;
        }

        pendingBets.add(playerId);
        executeCurrency(playerId, null, () -> gateway.balance(offlinePlayer)).whenComplete((balance, providerError) ->
                TaiXiu.scheduler.runGlobal(() -> {
                    if (providerError != null) {
                        pendingBets.remove(playerId);
                        if (!schedulerRetired(providerError)) markUnhealthy("ECONOMY_BALANCE_FAILURE");
                        MessageUtil.throwErrorMessage("Economy provider failed while reading " + pName + " balance: "
                                + providerError.getMessage());
                        sendNotEnough(player, data);
                        return;
                    }
                    if (balance < money) {
                        pendingBets.remove(playerId);
                        sendNotEnough(player, data);
                        return;
                    }
                    beginDebit(player, playerId, pName, effectiveTax, rolloverEligible, insuranceEligible,
                            money, result, data, gateway, offlinePlayer);
                }));
    }

    private static void beginDebit(Player player, UUID playerId, String pName, double effectiveTax,
                                   boolean rolloverEligible, boolean insuranceEligible, long money,
                                   TaiXiuResult result, ISession data, CurrencyGateway gateway,
                                   org.bukkit.OfflinePlayer offlinePlayer) {
        UUID transactionId = UUID.randomUUID();
        JournalEntry intent = new JournalEntry(transactionId.toString(), data.getSession(), playerId,
                pName, data.getCurrencyType(), "DEBIT", money);
        SessionDataStorage.recordIntentAsync(intent).whenComplete((ignored, intentError) ->
                TaiXiu.scheduler.runGlobal(() -> {
                    if (intentError != null) {
                        pendingBets.remove(playerId);
                        MessageUtil.throwErrorMessage("Could not persist bet intent: " + intentError.getMessage());
                        sendMessage(player, Messages.INVALID_CURRENCY
                                .replace("%currencyName%", MessageUtil.getCurrencyName(data.getCurrencyType()))
                                .replace("%currencySymbol%", MessageUtil.getCurrencySymbol(data.getCurrencyType())));
                        return;
                    }

                    if (!acceptingTransactions) {
                        pendingBets.remove(playerId);
                        return;
                    }
                    executeCurrency(playerId, transactionId.toString(), () -> gateway.debit(offlinePlayer, money))
                            .whenComplete((debit, providerError) ->
                            TaiXiu.scheduler.runGlobal(() -> {
                    if (providerError != null) {
                        pendingBets.remove(playerId);
                        if (schedulerRetired(providerError)) {
                            SessionDataStorage.markJournalAsync(transactionId.toString(), "FAILED",
                                    "Player disconnected before debit");
                            return;
                        }
                        markUnhealthy("ECONOMY_DEBIT_UNKNOWN");
                        SessionDataStorage.markJournalAsync(transactionId.toString(), "UNKNOWN", providerError.getMessage())
                                .exceptionally(markError -> {
                                    MessageUtil.throwErrorMessage("Could not persist UNKNOWN debit " + transactionId
                                            + ": " + markError.getMessage());
                                    return null;
                                });
                        MessageUtil.throwErrorMessage("Economy provider threw during debit " + transactionId + ": "
                                + providerError.getMessage());
                        return;
                    }
                    if (!debit.successful()) {
                        pendingBets.remove(playerId);
                        SessionDataStorage.markJournalAsync(transactionId.toString(), "FAILED");
                        sendMessage(player, Messages.NOT_ENOUGH_CURRENCY
                                .replace("%currencyName%", MessageUtil.getCurrencyName(data.getCurrencyType()))
                                .replace("%currencySymbol%", MessageUtil.getCurrencySymbol(data.getCurrencyType())));
                        return;
                    }

                    SessionDataStorage.markJournalAsync(transactionId.toString(), "APPLIED")
                            .whenComplete((marked, markError) -> TaiXiu.scheduler.runGlobal(() -> {
                                if (markError != null) {
                                    pendingBets.remove(playerId);
                                    markUnhealthy("JOURNAL_APPLIED_WRITE_FAILURE");
                                    MessageUtil.throwErrorMessage("Debit applied but journal update failed for " + transactionId);
                                    return;
                                }
                                if (data instanceof SessionData sessionData)
                                    sessionData.registerPlayer(pName, new BetMetadata(playerId, effectiveTax,
                                            rolloverEligible, insuranceEligible, BetMetadata.FundingSource.WALLET, 0));
                                if (result == TaiXiuResult.XIU) data.addXiuPlayer(pName, money);
                                else data.addTaiPlayer(pName, money);
                                SessionData snapshot = SessionData.copyOf(data);
                                SessionDataStorage.saveAsync(data.getSession(), snapshot).whenComplete((saved, saveError) ->
                                        TaiXiu.scheduler.runGlobal(() -> {
                                            if (saveError != null) {
                                                pendingBets.remove(playerId);
                                                data.removeXiuPlayer(pName);
                                                data.removeTaiPlayer(pName);
                                                markUnhealthy("BET_PERSISTENCE_FAILURE");
                                                compensateFailedBet(gateway, offlinePlayer, transactionId, money, saveError);
                                                return;
                                            }
                                            SessionDataStorage.markJournalAsync(transactionId.toString(), "COMPLETED")
                                                    .whenComplete((completed, completionError) -> TaiXiu.scheduler.runGlobal(() -> {
                                                        pendingBets.remove(playerId);
                                                        if (completionError != null) {
                                                            markUnhealthy("JOURNAL_COMPLETION_WRITE_FAILURE");
                                                            MessageUtil.throwErrorMessage("Bet saved but journal completion failed for " + transactionId);
                                                            return;
                                                        }
                                                        completeAcceptedBet(player, pName, money, result, data);
                                                    }));
                                        }));
                            }));
                            }));
                }));
    }

    private static <T> CompletableFuture<T> executeCurrency(UUID playerId, String journalId,
                                                             Supplier<T> operation) {
        CompletableFuture<T> result = new CompletableFuture<>();
        CompletableFuture<Void> barrier = new CompletableFuture<>();
        synchronized (CURRENCY_LIFECYCLE_LOCK) {
            if (shuttingDown)
                return CompletableFuture.failedFuture(new IllegalStateException("Plugin is shutting down"));
            inFlightCurrency.add(barrier);
            if (journalId != null) inFlightJournalIds.put(barrier, journalId);
        }
        Runnable command = () -> {
            try {
                result.complete(operation.get());
            } catch (Throwable error) {
                result.completeExceptionally(error);
            } finally {
                barrier.complete(null);
                inFlightCurrency.remove(barrier);
                inFlightJournalIds.remove(barrier);
            }
        };
        try {
            Player online = Bukkit.getPlayer(playerId);
            if (online != null) {
                if (!TaiXiu.scheduler.runEntity(online, command)) commandFailed(result, barrier, journalId,
                        new EntitySchedulerRetiredException());
            } else {
                TaiXiu.scheduler.runGlobal(command);
            }
        } catch (RuntimeException schedulingError) {
            commandFailed(result, barrier, journalId, schedulingError);
        }
        return result;
    }

    private static <T> void commandFailed(CompletableFuture<T> result, CompletableFuture<Void> barrier,
                                          String journalId, Throwable error) {
        result.completeExceptionally(error);
        barrier.complete(null);
        inFlightCurrency.remove(barrier);
        if (journalId != null) inFlightJournalIds.remove(barrier);
    }

    private static void sendNotEnough(Player player, ISession data) {
        sendMessage(player, Messages.NOT_ENOUGH_CURRENCY
                .replace("%currencyName%", MessageUtil.getCurrencyName(data.getCurrencyType()))
                .replace("%currencySymbol%", MessageUtil.getCurrencySymbol(data.getCurrencyType())));
    }

    private static boolean schedulerRetired(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current instanceof EntitySchedulerRetiredException;
    }

    private static void completeAcceptedBet(Player player, String pName, long money, TaiXiuResult result, ISession data) {

        sendMessage(player, Messages.PLAYER_BET
                .replace("%bet%", MessageUtil.getFormatResultName(result))
                .replace("%money%", MessageUtil.getFormatMoneyDisplay(money))
                .replace("%session%", String.valueOf(data.getSession()))
                .replace("%currencyName%", MessageUtil.getCurrencyName(data.getCurrencyType()))
                .replace("%currencySymbol%", MessageUtil.getCurrencySymbol(data.getCurrencyType()))
                .replace("%time%", String.valueOf(TaiXiuManager.getTimeLeft())));

        String messageBroadcastPlayerBet = Messages.BROADCAST_PLAYER_BET
                .replace("%prefix%", Messages.PREFIX)
                .replace("%player%", player.getName())
                .replace("%bet%", MessageUtil.getFormatResultName(result))
                .replace("%currencyName%", MessageUtil.getCurrencyName(data.getCurrencyType()))
                .replace("%currencySymbol%", MessageUtil.getCurrencySymbol(data.getCurrencyType()))
                .replace("%money%", MessageUtil.getFormatMoneyDisplay(money));

        String broadcast = messageBroadcastPlayerBet;
        TaiXiu.scheduler.runEntity(player, () -> {
            String formatted = TaiXiu.support.isPlaceholderAPISupported()
                    ? PlaceholderAPI.setPlaceholders(player, broadcast) : broadcast;
            sendBroadCast(formatted);
            Bukkit.getServer().getPluginManager().callEvent(new PlayerBetEvent(player, result, money));
        });

        debug("PLAYER BETTED",
                "Name: " + pName + " " +
                        "| Bet: " + result.toString() + " " +
                        "| Money: " + money + " " +
                        "| Session: " + data.getSession());

        // discord web hook
        if (TaiXiu.support.getDiscordSupport() != null) {
            SessionData snapshot = SessionData.copyOf(data);
            UUID playerId = player.getUniqueId();
            TaiXiu.support.getDiscordSupport().sendPlayerBet(
                    TaiXiu.plugin.getDataFolder() + "/discordsrv-playerbet-message.json",
                    snapshot, pName, playerId, result, money);
        }
    }

    public static boolean hasPendingBets() {
        return !pendingBets.isEmpty();
    }

    public static CompletableFuture<Void> activateRolloverOffers(long targetSessionId, long expiresAt) {
        return SessionDataStorage.activateRolloverOffersAsync(targetSessionId, expiresAt)
                .thenRun(() -> TaiXiu.scheduler.runGlobal(() -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        SessionDataStorage.findAvailableRolloverAsync(player.getUniqueId(), targetSessionId)
                                .thenAccept(found -> found.ifPresent(offer -> TaiXiu.scheduler.runEntity(player,
                                        () -> sendRolloverPrompt(player, offer))));
                    }
                }));
    }

    public static void showRolloverOffer(Player player) {
        long sessionId = getSessionData().getSession();
        SessionDataStorage.findAvailableRolloverAsync(player.getUniqueId(), sessionId)
                .thenAccept(found -> found.ifPresentOrElse(
                        offer -> TaiXiu.scheduler.runEntity(player, () -> sendRolloverPrompt(player, offer)),
                        () -> TaiXiu.scheduler.runEntity(player, () -> sendMessage(player, Messages.ROLLOVER_NOT_AVAILABLE))));
    }

    public static void rollover(Player player, TaiXiuResult side) {
        if (!Bukkit.isOwnedByCurrentRegion(player)) {
            TaiXiu.scheduler.runEntity(player, () -> rollover(player, side));
            return;
        }
        if (side != TaiXiuResult.TAI && side != TaiXiuResult.XIU) {
            cashoutRollover(player);
            return;
        }
        if (!player.hasPermission("taixiu.rollover")) {
            sendMessage(player, Messages.NO_PERMISSION);
            return;
        }
        long targetSession = getSessionData().getSession();
        int cutoff = TaiXiu.plugin.getConfig().getInt("bet-settings.disable-while-remaining");
        if (getState() != TaiXiuState.PLAYING || getTimeLeft() <= cutoff) {
            sendMessage(player, Messages.LATE_BET.replace("%time%", String.valueOf(getTimeLeft()))
                    .replace("%configDisableTime%", String.valueOf(cutoff)));
            return;
        }
        double tax = BetPermissionPolicy.effectiveTax(player,
                TaiXiu.plugin.getConfig().getDouble("bet-settings.tax"));
        long maxBet = BetPermissionPolicy.effectiveMaxBet(player,
                TaiXiu.plugin.getConfig().getLong("bet-settings.max-bet"));
        UUID playerId = player.getUniqueId();
        if (!pendingBets.add(playerId)) return;
        SessionDataStorage.findAvailableRolloverAsync(playerId, targetSession).whenComplete((found, lookupError) ->
                TaiXiu.scheduler.runGlobal(() -> {
                    if (lookupError != null || found.isEmpty()) {
                        pendingBets.remove(playerId);
                        sendMessage(player, Messages.ROLLOVER_NOT_AVAILABLE);
                        return;
                    }
                    RolloverOffer offer = found.get();
                    boolean scheduled = TaiXiu.scheduler.runEntity(player, () -> {
                        PlayerBetPreEvent preEvent = new PlayerBetPreEvent(player, side, offer.amount());
                        Bukkit.getPluginManager().callEvent(preEvent);
                        if (preEvent.isCancelled()) {
                            pendingBets.remove(playerId);
                            return;
                        }
                        TaiXiu.scheduler.runGlobal(() -> consumeRolloverOffer(player, side, targetSession,
                                tax, maxBet, offer));
                    });
                    if (!scheduled) pendingBets.remove(playerId);
                }));
    }

    private static void consumeRolloverOffer(Player player, TaiXiuResult side, long targetSession,
                                             double tax, long maxBet, RolloverOffer offer) {
        UUID playerId = player.getUniqueId();
        ISession session = getSessionData();
        if (session.getSession() != targetSession || session.getTaiPlayerSnapshot().containsKey(player.getName())
                || session.getXiuPlayerSnapshot().containsKey(player.getName())) {
            pendingBets.remove(playerId);
            sendMessage(player, Messages.ALREADY_BET
                    .replace("%bet%", "-").replace("%money%", "-")
                    .replace("%currencyName%", MessageUtil.getCurrencyName(session.getCurrencyType()))
                    .replace("%currencySymbol%", MessageUtil.getCurrencySymbol(session.getCurrencyType())));
            return;
        }
        if (session.getCurrencyType() != offer.currency()) {
            pendingBets.remove(playerId);
            cashoutRollover(player);
            return;
        }
        CurrencyGateway gateway = TaiXiu.currencies.gateway(offer.currency());
        long payoutCap;
        boolean payoutWithinCap;
        try {
            payoutCap = Math.multiplyExact(TaiXiu.plugin.getConfig().getLong("bet-settings.max-bet"),
                    TaiXiu.plugin.getConfig().getLong("rollover.max-payout-multiplier", 10));
            payoutWithinCap = PayoutCalculator.calculate(offer.amount(), tax, false) <= payoutCap;
        } catch (ArithmeticException overflow) {
            payoutWithinCap = false;
        }
        if (offer.amount() < TaiXiu.plugin.getConfig().getLong("bet-settings.min-bet")
                || offer.amount() > maxBet || offer.amount() > gateway.maximumTransaction() / 2
                || !payoutWithinCap) {
            pendingBets.remove(playerId);
            cashoutRollover(player);
            return;
        }
        BetMetadata metadata = new BetMetadata(playerId, tax, true, false,
                BetMetadata.FundingSource.ESCROW, offer.depth());
        SessionDataStorage.consumeRolloverAsync(offer.id(), targetSession, side, metadata)
                .whenComplete((consumed, consumeError) -> TaiXiu.scheduler.runGlobal(() -> {
                    pendingBets.remove(playerId);
                    if (consumeError != null || consumed.isEmpty()) {
                        sendMessage(player, Messages.ROLLOVER_NOT_AVAILABLE);
                        return;
                    }
                    if (session instanceof SessionData data) data.registerPlayer(player.getName(), metadata);
                    if (side == TaiXiuResult.TAI) session.addTaiPlayer(player.getName(), offer.amount());
                    else session.addXiuPlayer(player.getName(), offer.amount());
                    completeAcceptedBet(player, player.getName(), offer.amount(), side, session);
                    sendMessage(player, Messages.ROLLOVER_ACCEPTED
                            .replace("%money%", MessageUtil.getFormatMoneyDisplay(offer.amount()))
                            .replace("%currencySymbol%", MessageUtil.getCurrencySymbol(offer.currency())));
                }));
    }

    public static void cashoutRollover(Player player) {
        UUID playerId = player.getUniqueId();
        long targetSession = getSessionData().getSession();
        if (!pendingBets.add(playerId)) return;
        SessionDataStorage.prepareRolloverCashoutAsync(playerId, targetSession).whenComplete((prepared, error) -> {
            if (error != null || prepared.isEmpty()) {
                pendingBets.remove(playerId);
                TaiXiu.scheduler.runEntity(player, () -> sendMessage(player, Messages.ROLLOVER_NOT_AVAILABLE));
                return;
            }
            JournalEntry entry = prepared.get();
            executeStandalonePayouts(List.of(new PayoutWork(entry, Messages.ROLLOVER_CASHED_OUT
                            .replace("%money%", MessageUtil.getFormatMoneyDisplay(entry.amount()))
                            .replace("%currencySymbol%", MessageUtil.getCurrencySymbol(entry.currency())))))
                    .whenComplete((ignored, payoutError) -> {
                        pendingBets.remove(playerId);
                        if (payoutError != null) markUnhealthy("ROLLOVER_CASHOUT_FAILURE");
                    });
        });
    }

    public static CompletableFuture<Void> expireRolloverOffers(long targetSessionId) {
        return SessionDataStorage.expireRolloverOffersAsync(targetSessionId, System.currentTimeMillis())
                .thenCompose(entries -> {
                    if (entries.isEmpty()) return CompletableFuture.completedFuture(null);
                    entries.forEach(entry -> pendingBets.add(entry.playerId()));
                    List<PayoutWork> work = entries.stream().map(entry -> new PayoutWork(entry,
                            Messages.ROLLOVER_CASHED_OUT
                                    .replace("%money%", MessageUtil.getFormatMoneyDisplay(entry.amount()))
                                    .replace("%currencySymbol%", MessageUtil.getCurrencySymbol(entry.currency())))).toList();
                    return executeStandalonePayouts(work).whenComplete((ignored, error) -> {
                        entries.forEach(entry -> pendingBets.remove(entry.playerId()));
                        if (error != null) markUnhealthy("ROLLOVER_EXPIRY_FAILURE");
                    });
                });
    }

    private static CompletableFuture<Void> executeStandalonePayouts(List<PayoutWork> work) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        TaiXiu.scheduler.runGlobal(() -> executePreparedPayouts(work).whenComplete((ignored, error) -> {
            if (error == null) result.complete(null); else result.completeExceptionally(error);
        }));
        return result;
    }

    private static void sendRolloverPrompt(Player player, RolloverOffer offer) {
        String message = Messages.ROLLOVER_OFFER
                .replace("%money%", MessageUtil.getFormatMoneyDisplay(offer.amount()))
                .replace("%currencySymbol%", MessageUtil.getCurrencySymbol(offer.currency()))
                .replace("%session%", String.valueOf(offer.targetSessionId()));
        if (TaiXiu.support.isFloodgateSupported()
                && FloodgateApi.getInstance().isFloodgateId(player.getUniqueId())) {
            sendMessage(player, message);
            RolloverGeyserForm.openForm(player);
            return;
        }
        String colored = TaiXiu.nms.addColor(message.replace("%prefix%", Messages.PREFIX));
        boolean vietnamese = "vi".equals(Messages.locale);
        Component prompt = LegacyComponentSerializer.legacySection().deserialize(colored)
                .append(Component.newline())
                .append(Component.text(vietnamese ? "[Tài]" : "[High]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand(vietnamese ? "/taixiu nhoi tai" : "/taixiu rollover high")))
                .append(Component.space())
                .append(Component.text(vietnamese ? "[Xỉu]" : "[Low]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand(vietnamese ? "/taixiu nhoi xiu" : "/taixiu rollover low")))
                .append(Component.space())
                .append(Component.text(vietnamese ? "[Nhận tiền]" : "[Cash out]", NamedTextColor.GOLD)
                        .clickEvent(ClickEvent.runCommand(vietnamese ? "/taixiu nhoi nhantien" : "/taixiu rollover cashout")));
        player.sendMessage(prompt);
    }

    public static CompletableFuture<Void> recoverPendingTransactionsAsync() {
        CompletableFuture<Void> recovery = CompletableFuture.completedFuture(null);
        for (JournalEntry entry : SessionDataStorage.unresolvedJournal()) {
            recovery = recovery.thenCompose(ignored -> recoverTransaction(entry));
        }
        return recovery.whenComplete((ignored, error) -> {
            if (shuttingDown) return;
            if (error != null) markUnhealthy("RECOVERY_FAILURE");
            if (!SessionDataStorage.unresolvedJournal().isEmpty()) markUnhealthy("UNRESOLVED_TRANSACTIONS");
        });
    }

    private static CompletableFuture<Void> recoverTransaction(JournalEntry entry) {
            if ("PREPARED".equals(entry.status())) {
                TaiXiu.plugin.getLogger().severe("Transaction " + entry.id() + " became UNKNOWN after an interrupted "
                        + entry.kind() + ". No automatic credit was performed; reconcile it with /taixiuadmin transaction.");
                return SessionDataStorage.markJournalAsync(entry.id(), "UNKNOWN",
                        "Interrupted before provider outcome was persisted");
            }
            if (!"APPLIED".equals(entry.status())) {
                TaiXiu.plugin.getLogger().warning("Unresolved transaction " + entry.id() + " status=" + entry.status());
                return CompletableFuture.completedFuture(null);
            }
            if ("PAYOUT".equals(entry.kind()) || SessionDataStorage.betExists(entry)) {
                return SessionDataStorage.markJournalAsync(entry.id(), "COMPLETED");
            }
            if (!TaiXiu.currencies.supports(entry.currency())) {
                MessageUtil.throwErrorMessage("Cannot compensate applied debit " + entry.id()
                        + ": missing provider " + entry.currency());
                return CompletableFuture.completedFuture(null);
            }
            return SessionDataStorage.markJournalAsync(entry.id(), "PREPARED").thenCompose(ignored ->
                    executeCurrency(entry.playerId(), entry.id(), () -> TaiXiu.currencies.gateway(entry.currency())
                            .credit(Bukkit.getOfflinePlayer(entry.playerId()), entry.amount()))
                            .handle((transaction, providerError) -> {
                                if (providerError != null)
                                    return new RecoveryState("UNKNOWN", providerError.getMessage());
                                if (transaction.successful()) return new RecoveryState("COMPENSATED", null);
                                return new RecoveryState("UNKNOWN", transaction.error());
                            }).thenCompose(state -> SessionDataStorage.markJournalAsync(
                                    entry.id(), state.status(), state.error())));
    }

    public static CompletableFuture<String> reconcileTransaction(String id, String requestedAction,
                                                                 String actor, String reason) {
        String action = requestedAction.toUpperCase(Locale.ROOT);
        CompletableFuture<String> result = new CompletableFuture<>();
        SessionDataStorage.findJournalAsync(id).whenComplete((found, lookupError) -> {
            if (lookupError != null) {
                result.complete("Lookup failed: " + lookupError.getMessage());
                return;
            }
            if (found.isEmpty()) {
                result.complete("Transaction not found: " + id);
                return;
            }
            JournalEntry entry = found.get();
            if (Set.of("COMPLETED", "COMPENSATED").contains(entry.status())) {
                result.complete("Transaction is already terminal: " + entry.status());
                return;
            }
            if ("COMPLETE".equals(action) || "FAIL".equals(action)) {
                String status = "COMPLETE".equals(action) ? "COMPLETED" : "FAILED";
                SessionDataStorage.markJournalAsync(id, status, "Manually reconciled", actor, reason)
                        .whenComplete((ignored, error) -> result.complete(error == null
                                ? "Transaction " + id + " marked " + status
                                : "Update failed: " + error.getMessage()));
                return;
            }
            if (!("REFUND".equals(action) && "DEBIT".equals(entry.kind()))
                    && !("RETRY".equals(action) && "PAYOUT".equals(entry.kind()))) {
                result.complete("Allowed actions: COMPLETE, FAIL; REFUND for DEBIT; RETRY for PAYOUT");
                return;
            }
            if ("REFUND".equals(action) && "FAILED".equals(entry.status())) {
                result.complete("Refund refused: this debit was definitively rejected by the provider");
                return;
            }
            if (!TaiXiu.currencies.supports(entry.currency())) {
                result.complete("Currency provider unavailable: " + entry.currency());
                return;
            }
            SessionDataStorage.markJournalAsync(id, "PREPARED").whenComplete((prepared, prepareError) -> {
                if (prepareError != null) {
                    result.complete("Could not prepare reconciliation: " + prepareError.getMessage());
                    return;
                }
                if (!acceptingTransactions) {
                    result.complete("Reconciliation cancelled because the plugin is shutting down");
                    return;
                }
                executeCurrency(entry.playerId(), id, () -> TaiXiu.currencies.gateway(entry.currency())
                        .credit(Bukkit.getOfflinePlayer(entry.playerId()), entry.amount()))
                        .whenComplete((transaction, exception) -> {
                if (exception != null) {
                    SessionDataStorage.markJournalAsync(id, "UNKNOWN", exception.getMessage())
                            .whenComplete((ignored, markError) -> result.complete(markError == null
                                    ? "Provider threw; transaction remains UNKNOWN: " + exception.getMessage()
                                    : "Provider threw and UNKNOWN state could not be saved: " + markError.getMessage()));
                    return;
                }
                if (!transaction.successful()) {
                    SessionDataStorage.markJournalAsync(id, "FAILED", transaction.error())
                            .whenComplete((ignored, markError) -> result.complete(markError == null
                                    ? "Provider rejected transaction: " + transaction.error()
                                    : "Provider rejected transaction and FAILED state could not be saved: " + markError.getMessage()));
                    return;
                }
                String finalStatus = "REFUND".equals(action) ? "COMPENSATED" : "COMPLETED";
                SessionDataStorage.markJournalAsync(id, finalStatus, null, actor, reason)
                        .whenComplete((ignored, error) -> result.complete(error == null
                                ? action + " succeeded for " + id
                                : "Money changed but journal update failed; inspect " + id));
                });
            });
        });
        result.whenComplete((message, error) -> {
            if (error == null && message != null
                    && (message.contains(" marked ") || message.contains(" succeeded ")))
                refreshTransactionHealth();
        });
        return result;
    }

    /** @deprecated Supply an audit actor and reason. */
    @Deprecated(since = "3.0.0")
    public static CompletableFuture<String> reconcileTransaction(String id, String action) {
        return reconcileTransaction(id, action, "legacy-api", "Legacy API reconciliation");
    }

    /**
     * @deprecated Settlement is asynchronous in 3.0. Returning from this method does not mean payouts completed.
     * Use {@link #resultSeasonAsync(ISession, int, int, int)} and await its future.
     */
    @Deprecated(since = "3.0.0")
    public static void resultSeason(@NotNull ISession session, int dice1, int dice2, int dice3) {
        resultSeasonAsync(session, dice1, dice2, dice3);
    }

    public static CompletableFuture<Boolean> resultSeasonAsync(@NotNull ISession session, int dice1, int dice2, int dice3) {
        debug("RESULTING SESSION", "Session number " + session.getSession());

        if (!acceptingTransactions || shuttingDown) return CompletableFuture.completedFuture(false);

        CompletableFuture<Boolean> existing = settlementFutures.get(session.getSession());
        if (existing != null) return existing;

        if (hasPendingBets()) return CompletableFuture.completedFuture(false);

        if (session.getResult() != TaiXiuResult.NONE) {
            return CompletableFuture.completedFuture(true);
        }

        FileConfiguration cfg = TaiXiu.plugin.getConfig();

        if (dice1 == 0)
            dice1 = ThreadLocalRandom.current().nextInt(1, 6 + 1);
        session.setDice1(dice1);
        if (dice2 == 0)
            dice2 = ThreadLocalRandom.current().nextInt(1, 6 + 1);
        session.setDice2(dice2);
        if (dice3 == 0)
            dice3 = ThreadLocalRandom.current().nextInt(1, 6 + 1);
        session.setDice3(dice3);

        DiceRules.DiceOutcome outcome = DiceRules.resolve(dice1, dice2, dice3,
                cfg.getBoolean("bet-settings.disable-special"));
        dice1 = outcome.dice1();
        dice2 = outcome.dice2();
        dice3 = outcome.dice3();
        int total = outcome.total();
        session.setDice1(dice1);
        session.setDice2(dice2);
        session.setDice3(dice3);
        session.setResult(outcome.result());

        Map<String, Long> winners = session.getResult() == TaiXiuResult.XIU
                ? session.getXiuPlayerSnapshot()
                : session.getResult() == TaiXiuResult.TAI ? session.getTaiPlayerSnapshot() : Map.of();
        SettlementPlan settlementPlan;
        try {
            settlementPlan = createSettlementPlan(session, winners, session.getResult());
        } catch (RuntimeException exception) {
            session.setResult(TaiXiuResult.NONE);
            MessageUtil.throwErrorMessage("Could not calculate settlement #" + session.getSession() + ": " + exception);
            return CompletableFuture.completedFuture(false);
        }

        List<JournalEntry> intents = settlementPlan.payoutWork().stream().map(PayoutWork::entry).toList();
        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();
        settlementFutures.put(session.getSession(), resultFuture);
        resultFuture.whenComplete((result, error) -> settlementFutures.remove(session.getSession(), resultFuture));
        InsuranceSettings insuranceSettings = new InsuranceSettings(cfg.getBoolean("insurance.enabled"),
                cfg.getInt("insurance.losses-required", 3), cfg.getDouble("insurance.refund-percent", 20),
                cfg.getLong("insurance.max-refund-per-24-hours", 1_000_000));
        SessionDataStorage.prepareSettlementAsync(SessionData.copyOf(session), intents,
                        settlementPlan.offers(), insuranceSettings)
                .whenComplete((prepared, prepareError) -> TaiXiu.scheduler.runGlobal(() -> {
                    if (prepareError != null) {
                        session.setResult(TaiXiuResult.NONE);
                        MessageUtil.throwErrorMessage("Could not atomically prepare settlement #" + session.getSession()
                                + ": " + prepareError.getMessage());
                        resultFuture.complete(false);
                        return;
                    }
                    List<PayoutWork> allPayouts = new ArrayList<>(settlementPlan.payoutWork());
                    for (JournalEntry insurance : prepared.insurancePayouts()) {
                        String message = Messages.INSURANCE_PAID
                                .replace("%currencyName%", MessageUtil.getCurrencyName(insurance.currency()))
                                .replace("%currencySymbol%", MessageUtil.getCurrencySymbol(insurance.currency()))
                                .replace("%money%", MessageUtil.getFormatMoneyDisplay(insurance.amount()));
                        allPayouts.add(new PayoutWork(insurance, message));
                    }
                    finishPreparedSettlement(session, outcome.total(), allPayouts, resultFuture);
                }));
        return resultFuture;
    }

    private static void finishPreparedSettlement(ISession session, int total, List<PayoutWork> payoutWork,
                                                  CompletableFuture<Boolean> resultFuture) {
        CompletableFuture<Void> updates = executePreparedPayouts(payoutWork);
        try {
            for (String string : Messages.SESSION_RESULT) {
                string = string.replace("%session%", String.valueOf(session.getSession()));
                string = string.replace("%dice1%", String.valueOf(session.getDice1()));
                string = string.replace("%dice2%", String.valueOf(session.getDice2()));
                string = string.replace("%dice3%", String.valueOf(session.getDice3()));
                string = string.replace("%total%", String.valueOf(total));
                string = string.replace("%result%", MessageUtil.getFormatResultName(session.getResult()));
                string = string.replace("%bestWinners%", getBestWinner(session));

                sendBroadCast(string);
            }

            if (session.getResult() == TaiXiuResult.XIU) {
                for (Map.Entry<String, Long> loser : session.getTaiPlayerSnapshot().entrySet()) {
                    String taiPlayer = loser.getKey();
                    String message = Messages.SESSION_PLAYER_LOSE
                            .replace("%result%", MessageUtil.getFormatResultName(TaiXiuResult.TAI))
                            .replace("%currencyName%", MessageUtil.getCurrencyName(session.getCurrencyType()))
                            .replace("%currencySymbol%", MessageUtil.getCurrencySymbol(session.getCurrencyType()))
                            .replace("%money%", MessageUtil.getFormatMoneyDisplay(loser.getValue()));

                    playSound(Bukkit.getPlayer(taiPlayer), SoundType.lose);
                    sendMessage(Bukkit.getPlayer(taiPlayer), message);
                }
            } else if (session.getResult() == TaiXiuResult.TAI) {
                for (Map.Entry<String, Long> loser : session.getXiuPlayerSnapshot().entrySet()) {
                    String xiuPlayer = loser.getKey();
                    String message = Messages.SESSION_PLAYER_LOSE
                            .replace("%result%", MessageUtil.getFormatResultName(TaiXiuResult.XIU))
                            .replace("%currencyName%", MessageUtil.getCurrencyName(session.getCurrencyType()))
                            .replace("%currencySymbol%", MessageUtil.getCurrencySymbol(session.getCurrencyType()))
                            .replace("%money%", MessageUtil.getFormatMoneyDisplay(loser.getValue()));

                    playSound(Bukkit.getPlayer(xiuPlayer), SoundType.lose);
                    sendMessage(Bukkit.getPlayer(xiuPlayer), message);
                }
            } else
                sendBroadCast(Messages.SESSION_SPECIAL_WIN
                        .replace("%currencyName%", MessageUtil.getCurrencyName(session.getCurrencyType()))
                        .replace("%currencySymbol%", MessageUtil.getCurrencySymbol(session.getCurrencyType())));

        } catch (Exception e) {
            MessageUtil.throwErrorMessage("<taixiumanager.java<resultSeason>>" + e);
        }
        updates.whenComplete((done, payoutError) ->
                TaiXiu.scheduler.runGlobal(() -> {
                    SessionResultEvent event = new SessionResultEvent(session.snapshot());
                    TaiXiu.plugin.getServer().getPluginManager().callEvent(event);
                    debug("SESSION RESULTED", "Session: " + session.getSession() + " | Result: " + session.getResult());
                    sendResultToDiscordAsync(session);
                    if (payoutError != null)
                        MessageUtil.throwErrorMessage("Settlement journal update failed for #" + session.getSession()
                                + ": " + payoutError.getMessage());
                    if (payoutError != null) {
                        markUnhealthy("PAYOUT_SETTLEMENT_FAILURE");
                        resultFuture.complete(false);
                    } else {
                        resultFuture.complete(true);
                    }
                }));
    }

    private static void compensateFailedBet(CurrencyGateway gateway, org.bukkit.OfflinePlayer player,
                                            UUID transactionId, long amount, Throwable persistenceError) {
        String id = transactionId.toString();
        SessionDataStorage.markJournalAsync(id, "PREPARED", "Bet persistence failed; refund pending")
                .whenComplete((prepared, prepareError) -> TaiXiu.scheduler.runGlobal(() -> {
                    if (prepareError != null) {
                        MessageUtil.throwErrorMessage("Bet persistence failed and refund intent could not be saved for "
                                + id + ": " + prepareError.getMessage());
                        return;
                    }
                    executeCurrency(player.getUniqueId(), id, () -> gateway.credit(player, amount))
                            .whenComplete((refund, refundFailure) -> {
                    if (refundFailure == null) {
                        String refundStatus = refund.successful() ? "COMPENSATED" : "UNKNOWN";
                        String refundError = refund.successful() ? null : refund.error();
                        SessionDataStorage.markJournalAsync(id, refundStatus, refundError)
                                .exceptionally(refundStateError -> {
                                    MessageUtil.throwErrorMessage("Could not persist refund state for " + id
                                            + ": " + refundStateError.getMessage());
                                    return null;
                                });
                        MessageUtil.throwErrorMessage("Bet persistence failed; refund=" + refund.successful()
                                + ": " + persistenceError.getMessage());
                    } else {
                        SessionDataStorage.markJournalAsync(id, "UNKNOWN", refundFailure.getMessage());
                        MessageUtil.throwErrorMessage("Bet persistence and refund became UNKNOWN for "
                                + id + ": " + refundFailure.getMessage());
                    }
                            });
                }));
    }

    private static void sendResultToDiscordAsync(ISession session) {
        if (TaiXiu.support.getDiscordSupport() != null) {
            SessionData snapshot = SessionData.copyOf(session);
            TaiXiu.support.getDiscordSupport().sendResult(
                    TaiXiu.plugin.getDataFolder() + "/discordsrv-result-message.json", snapshot);
        }
    }

    private static SettlementPlan createSettlementPlan(ISession sessionData, Map<String, Long> players,
                                                        TaiXiuResult result) {
        List<PayoutWork> work = new ArrayList<>();
        List<RolloverOffer> offers = new ArrayList<>();
        FileConfiguration cfg = TaiXiu.plugin.getConfig();
        double configuredTax = cfg.getDouble("bet-settings.tax");
        int maxDepth = cfg.getInt("rollover.max-consecutive", 3);
        long payoutCap = Math.multiplyExact(cfg.getLong("bet-settings.max-bet"),
                cfg.getLong("rollover.max-payout-multiplier", 10));
        for (Map.Entry<String, Long> entry : players.entrySet()) {
            String player = entry.getKey();
            long stake = entry.getValue();
            String message;

            BetMetadata metadata = sessionData instanceof SessionData data ? data.getBetMetadata(player) : null;
            double playerTax = metadata == null || Double.isNaN(metadata.effectiveTax())
                    ? configuredTax : metadata.effectiveTax();
            long money = PayoutCalculator.calculate(stake, playerTax, false);

            if (playerTax > 0) {
                message = Messages.SESSION_PLAYER_WIN_WITH_TAX
                        .replace("%result%", MessageUtil.getFormatResultName(result))
                        .replace("%currencyName%", MessageUtil.getCurrencyName(sessionData.getCurrencyType()))
                        .replace("%currencySymbol%", MessageUtil.getCurrencySymbol(sessionData.getCurrencyType()))
                        .replace("%money%", MessageUtil.getFormatMoneyDisplay(money))
                        .replace("%tax%", String.valueOf(playerTax));
            } else {
                message = Messages.SESSION_PLAYER_WIN
                        .replace("%result%", MessageUtil.getFormatResultName(result))
                        .replace("%currencyName%", MessageUtil.getCurrencyName(sessionData.getCurrencyType()))
                        .replace("%currencySymbol%", MessageUtil.getCurrencySymbol(sessionData.getCurrencyType()))
                        .replace("%money%", MessageUtil.getFormatMoneyDisplay(money));
            }
            UUID playerId = metadata == null ? null : metadata.playerId();
            if (playerId == null) playerId = Bukkit.getOfflinePlayer(player).getUniqueId();
            boolean offerRollover = cfg.getBoolean("rollover.enabled") && metadata != null
                    && metadata.rolloverEligible() && metadata.rolloverDepth() < maxDepth;
            if (offerRollover) {
                try {
                    offerRollover = PayoutCalculator.calculate(money, playerTax, false) <= payoutCap;
                } catch (ArithmeticException overflow) {
                    offerRollover = false;
                }
            }
            if (offerRollover) {
                offers.add(new RolloverOffer(UUID.randomUUID().toString(), sessionData.getSession(),
                        sessionData.getSession() + 1, playerId, player, sessionData.getCurrencyType(), money,
                        metadata.rolloverDepth() + 1, 0, "PENDING_TARGET", null));
            } else {
                String journalId = UUID.randomUUID().toString();
                work.add(new PayoutWork(new JournalEntry(journalId, sessionData.getSession(), playerId, player,
                        sessionData.getCurrencyType(), "PAYOUT", money).withContext("SESSION_WIN"), message));
            }
        }
        return new SettlementPlan(work, offers);
    }

    private static CompletableFuture<Void> executePreparedPayouts(List<PayoutWork> payoutWork) {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        processNextPayout(new ArrayDeque<>(payoutWork), completion, new AtomicReference<>());
        return completion;
    }

    private static void processNextPayout(Deque<PayoutWork> queue, CompletableFuture<Void> completion,
                                          AtomicReference<Throwable> firstFailure) {
        if (!acceptingTransactions) {
            completion.completeExceptionally(new IllegalStateException("Payout queue stopped during shutdown"));
            return;
        }
        PayoutWork work = queue.pollFirst();
        if (work == null) {
            Throwable failure = firstFailure.get();
            if (failure == null) completion.complete(null); else completion.completeExceptionally(failure);
            return;
        }
        JournalEntry entry = work.entry();
        if (!TaiXiu.currencies.supports(entry.currency())) {
            finishPayoutAttempt(queue, completion, firstFailure,
                    SessionDataStorage.markJournalAsync(entry.id(), "FAILED", "Currency provider unavailable"),
                    new IllegalStateException("Currency provider unavailable: " + entry.currency()));
            return;
        }
        executeCurrency(entry.playerId(), entry.id(), () -> TaiXiu.currencies.gateway(entry.currency())
                .credit(Bukkit.getOfflinePlayer(entry.playerId()), entry.amount()))
                .whenComplete((payout, error) -> {
            CompletableFuture<Void> journalUpdate;
            Throwable providerFailure = null;
            if (error == null) {
                if (payout.successful()) {
                    playSound(Bukkit.getPlayer(entry.playerId()), SoundType.win);
                    sendMessage(Bukkit.getPlayer(entry.playerId()), work.message());
                    journalUpdate = SessionDataStorage.markJournalAsync(entry.id(), "COMPLETED");
                } else {
                    providerFailure = new IllegalStateException(payout.error());
                    journalUpdate = SessionDataStorage.markJournalAsync(entry.id(), "FAILED", payout.error());
                }
            } else {
                providerFailure = error;
                journalUpdate = SessionDataStorage.markJournalAsync(entry.id(), "UNKNOWN", error.getMessage());
            }
            finishPayoutAttempt(queue, completion, firstFailure, journalUpdate, providerFailure);
                });
    }

    private static void finishPayoutAttempt(Deque<PayoutWork> queue, CompletableFuture<Void> completion,
                                            AtomicReference<Throwable> firstFailure,
                                            CompletableFuture<Void> journalUpdate, Throwable providerFailure) {
        if (providerFailure != null) firstFailure.compareAndSet(null, providerFailure);
        journalUpdate.whenComplete((ignored, journalError) -> {
            if (journalError != null) firstFailure.compareAndSet(null, journalError);
            TaiXiu.scheduler.runGlobal(() -> processNextPayout(queue, completion, firstFailure));
        });
    }

    private record PayoutWork(JournalEntry entry, String message) { }
    private record SettlementPlan(List<PayoutWork> payoutWork, List<RolloverOffer> offers) { }
    private record RecoveryState(String status, String error) { }

    private static final class EntitySchedulerRetiredException extends IllegalStateException {
        private EntitySchedulerRetiredException() { super("Player entity scheduler retired"); }
    }

    private static void playSound(Player player, SoundType soundType) {
        if (player == null)
            return;

        if (!Bukkit.isOwnedByCurrentRegion(player)) {
            TaiXiu.scheduler.runEntity(player, () -> playSound(player, soundType));
            return;
        }

        if (TaiXiu.plugin.getConfig().getBoolean("sound." + soundType + ".enabled")) {
            player.playSound(player.getLocation(),
                    TaiXiu.nms.createSound(TaiXiu.plugin.getConfig().getString("sound." + soundType + ".sound-name")),
                    TaiXiu.plugin.getConfig().getInt("sound." + soundType + ".volume"),
                    TaiXiu.plugin.getConfig().getInt("sound." + soundType + ".pitch"));
        }
    }


    public static Long getXiuBet(@NotNull ISession session) {
        return session.getXiuPlayerSnapshot().values().stream().mapToLong(Long::longValue).sum();
    }

    public static String getXiuBetFormat(@NotNull ISession session) {
        return MessageUtil.getFormatMoneyDisplay(getXiuBet(session));
    }

    public static Long getTaiBet(@NotNull ISession session) {
        return session.getTaiPlayerSnapshot().values().stream().mapToLong(Long::longValue).sum();
    }

    public static String getTaiBetFormat(@NotNull ISession session) {
        return MessageUtil.getFormatMoneyDisplay(getTaiBet(session));
    }

    public static Long getTotalBet(@NotNull ISession session) {
        return getXiuBet(session) + getTaiBet(session);
    }

    public static String getBestWinner(@NotNull ISession session) {
        TaiXiuResult result = session.getResult();

        try {
            if (result == TaiXiuResult.NONE) {
                return Messages.RESULT_PLAYER_FORMAT_INVALID;
            }

            if (result == TaiXiuResult.SPECIAL) {
                return Messages.RESULT_PLAYER_FORMAT_VALID_SPECIAL
                        .replace("%currencyName%", MessageUtil.getCurrencyName(session.getCurrencyType()))
                        .replace("%currencySymbol%", MessageUtil.getCurrencySymbol(session.getCurrencyType()))
                        .replace("%allBet%", MessageUtil.getFormatMoneyDisplay(getTotalBet(session)));
            }

            Map<String, Long> bestWinners = result == TaiXiuResult.XIU
                    ? session.getXiuPlayerSnapshot() : session.getTaiPlayerSnapshot();
            if (bestWinners.isEmpty())
                return Messages.RESULT_PLAYER_FORMAT_INVALID;
            Long bestWinnersBet = Collections.max(bestWinners.values());

            List<String> players = new ArrayList<>();
            for (Map.Entry<String, Long> entry : bestWinners.entrySet())
                if (entry.getValue() >= bestWinnersBet)
                    players.add(entry.getKey());

            String delim = Messages.RESULT_PLAYER_FORMAT_PLAYER_DELIM;
            String bestWinnersName = String.join(delim, players);

            return Messages.RESULT_PLAYER_FORMAT_VALID
                    .replace("%playerName%", bestWinnersName)
                    .replace("%currencyName%", MessageUtil.getCurrencyName(session.getCurrencyType()))
                    .replace("%currencySymbol%", MessageUtil.getCurrencySymbol(session.getCurrencyType()))
                    .replace("%bet%", MessageUtil.getFormatMoneyDisplay(bestWinnersBet * 2));
        } catch (Exception e) {
            return Messages.RESULT_PLAYER_FORMAT_INVALID;
        }
    }

    public void stopTask() {
        getTaiXiuTask().cancel();
    }

}
