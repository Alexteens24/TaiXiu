package com.cortezromeo.taixiu.storage;

import com.cortezromeo.taixiu.TaiXiu;
import com.cortezromeo.taixiu.api.CurrencyTyppe;
import com.cortezromeo.taixiu.api.SessionSnapshot;
import com.cortezromeo.taixiu.api.TaiXiuResult;
import com.cortezromeo.taixiu.api.storage.ISession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SessionData implements ISession {

    private long session;
    private int dice1;
    private int dice2;
    private int dice3;
    private HashMap<String, Long> taiPlayers;
    private HashMap<String, Long> xiuPlayers;
    private TaiXiuResult result;
    private CurrencyTyppe currencyType;
    private final HashMap<String, UUID> playerIds = new HashMap<>();
    private final HashMap<String, Boolean> taxBypass = new HashMap<>();

    public SessionData(long session, int dice1, int dice2, int dice3, TaiXiuResult result, HashMap<String, Long> taiPlayers, HashMap<String, Long> xiuPlayers, CurrencyTyppe currencyType) {
        this.session = session;
        this.dice1 = dice1;
        this.dice2 = dice2;
        this.dice3 = dice3;
        this.result = result;
        this.taiPlayers = new HashMap<>(taiPlayers);
        this.xiuPlayers = new HashMap<>(xiuPlayers);
        this.currencyType = currencyType == null ? CurrencyTyppe.VAULT : currencyType;
    }

    public static SessionData copyOf(ISession source) {
        if (source instanceof SessionData sessionData) return sessionData.copy();
        SessionData copy = new SessionData(source.getSession(), source.getDice1(), source.getDice2(), source.getDice3(),
                source.getResult(), new HashMap<>(source.getTaiPlayerSnapshot()), new HashMap<>(source.getXiuPlayerSnapshot()),
                source.getCurrencyType());
        return copy;
    }

    private synchronized SessionData copy() {
        SessionData copy = new SessionData(session, dice1, dice2, dice3, result, taiPlayers, xiuPlayers, currencyType);
        copy.playerIds.putAll(playerIds);
        copy.taxBypass.putAll(taxBypass);
        return copy;
    }

    public synchronized void registerPlayer(String name, UUID playerId, boolean bypassTax) {
        playerIds.put(name, playerId);
        taxBypass.put(name, bypassTax);
    }

    public synchronized UUID getPlayerId(String name) { return playerIds.get(name); }
    public synchronized boolean hasTaxBypass(String name) { return taxBypass.getOrDefault(name, false); }

    @Override
    public synchronized long getSession() {
        return session;
    }

    @Override
    public synchronized void setSession(long session) {
        this.session = session;
    }

    @Override
    public synchronized int getDice1() {
        return dice1;
    }

    @Override
    public synchronized void setDice1(int dice1) {
        this.dice1 = dice1;
    }

    @Override
    public synchronized int getDice2() {
        return dice2;
    }

    @Override
    public synchronized void setDice2(int dice2) {
        this.dice2 = dice2;
    }

    @Override
    public synchronized int getDice3() {
        return dice3;
    }

    @Override
    public synchronized void setDice3(int dice3) {
        this.dice3 = dice3;
    }

    @Override
    public synchronized TaiXiuResult getResult() {
        return result;
    }

    @Override
    public synchronized void setResult(TaiXiuResult result) {
        this.result = result;
    }

    @Override
    public synchronized HashMap<String, Long> getTaiPlayers() {
        return new HashMap<>(taiPlayers);
    }

    @Override
    public synchronized HashMap<String, Long> getXiuPlayers() {
        return new HashMap<>(xiuPlayers);
    }

    @Override
    public synchronized void addTaiPlayer(String playerName, Long money) {
        taiPlayers.put(playerName, money);
    }

    @Override
    public synchronized void addXiuPlayer(String playerName, Long money) {
        xiuPlayers.put(playerName, money);
    }

    @Override
    public synchronized void removeTaiPlayer(String playerName) {
        taiPlayers.remove(playerName);
        cleanupPlayerMetadata(playerName);
    }

    @Override
    public synchronized void removeXiuPlayer(String playerName) {
        xiuPlayers.remove(playerName);
        cleanupPlayerMetadata(playerName);
    }

    @Override
    public synchronized void setTaiPlayer(HashMap<String, Long> hashmap) {
        taiPlayers = new HashMap<>(hashmap);
    }

    @Override
    public synchronized void setXiuPlayer(HashMap<String, Long> hashmap) {
        xiuPlayers = new HashMap<>(hashmap);
    }

    @Override
    public synchronized CurrencyTyppe getCurrencyType() {
        if (this.currencyType == null )
            return CurrencyTyppe.VAULT;
        return this.currencyType;
    }

    @Override
    public synchronized void setCurrencyType(CurrencyTyppe currencyType) {
        this.currencyType = currencyType == null ? CurrencyTyppe.VAULT : currencyType;
    }

    @Override
    public synchronized Map<String, Long> getTaiPlayerSnapshot() {
        return Map.copyOf(taiPlayers);
    }

    @Override
    public synchronized Map<String, Long> getXiuPlayerSnapshot() {
        return Map.copyOf(xiuPlayers);
    }

    @Override
    public synchronized SessionSnapshot snapshot() {
        return new SessionSnapshot(session, dice1, dice2, dice3, result,
                getCurrencyType().toCurrencyType(), taiPlayers, xiuPlayers);
    }

    private void cleanupPlayerMetadata(String playerName) {
        if (!taiPlayers.containsKey(playerName) && !xiuPlayers.containsKey(playerName)) {
            playerIds.remove(playerName);
            taxBypass.remove(playerName);
        }
    }
}
