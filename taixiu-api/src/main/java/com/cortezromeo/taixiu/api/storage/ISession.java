package com.cortezromeo.taixiu.api.storage;

import com.cortezromeo.taixiu.api.CurrencyTyppe;
import com.cortezromeo.taixiu.api.CurrencyType;
import com.cortezromeo.taixiu.api.SessionSnapshot;
import com.cortezromeo.taixiu.api.TaiXiuResult;

import java.util.HashMap;
import java.util.Map;

public interface ISession {

    long getSession();

    /** @deprecated Session mutation is internal; API consumers should use {@link #snapshot()}. */
    @Deprecated(since = "3.0.0") void setSession(long session);

    int getDice1();

    /** @deprecated Session mutation is internal. */
    @Deprecated(since = "3.0.0") void setDice1(int dice1);

    int getDice2();

    /** @deprecated Session mutation is internal. */
    @Deprecated(since = "3.0.0") void setDice2(int dice2);

    int getDice3();

    /** @deprecated Session mutation is internal. */
    @Deprecated(since = "3.0.0") void setDice3(int dice3);

    TaiXiuResult getResult();

    /** @deprecated Session mutation is internal. */
    @Deprecated(since = "3.0.0") void setResult(TaiXiuResult result);

    /** @deprecated Returns a defensive copy. Use {@link #getTaiPlayerSnapshot()}. */
    @Deprecated(since = "3.0.0")
    HashMap<String, Long> getTaiPlayers();

    /** @deprecated Returns a defensive copy. Use {@link #getXiuPlayerSnapshot()}. */
    @Deprecated(since = "3.0.0")
    HashMap<String, Long> getXiuPlayers();

    /** @deprecated Session mutation is internal. */
    @Deprecated(since = "3.0.0") void addTaiPlayer(String playerName, Long money);

    /** @deprecated Session mutation is internal. */
    @Deprecated(since = "3.0.0") void addXiuPlayer(String playerName, Long money);

    /** @deprecated Session mutation is internal. */
    @Deprecated(since = "3.0.0") void removeTaiPlayer(String playerName);

    /** @deprecated Session mutation is internal. */
    @Deprecated(since = "3.0.0") void removeXiuPlayer(String playerName);

    /** @deprecated Session mutation is internal. */
    @Deprecated(since = "3.0.0") void setTaiPlayer(HashMap<String, Long> hashmap);

    /** @deprecated Session mutation is internal. */
    @Deprecated(since = "3.0.0") void setXiuPlayer(HashMap<String, Long> hashmap);

    /** @deprecated Use {@link #getCurrency()}. */
    @Deprecated(since = "3.0.0")
    CurrencyTyppe getCurrencyType();

    /** @deprecated Use {@link #setCurrency(CurrencyType)}. */
    @Deprecated(since = "3.0.0")
    void setCurrencyType(CurrencyTyppe currencyType);

    default Map<String, Long> getTaiPlayerSnapshot() {
        return Map.copyOf(getTaiPlayers());
    }

    default Map<String, Long> getXiuPlayerSnapshot() {
        return Map.copyOf(getXiuPlayers());
    }

    default CurrencyType getCurrency() {
        return getCurrencyType().toCurrencyType();
    }

    default void setCurrency(CurrencyType currencyType) {
        setCurrencyType(currencyType.toLegacyType());
    }

    default SessionSnapshot snapshot() {
        return new SessionSnapshot(getSession(), getDice1(), getDice2(), getDice3(), getResult(), getCurrency(),
                getTaiPlayerSnapshot(), getXiuPlayerSnapshot());
    }

}
