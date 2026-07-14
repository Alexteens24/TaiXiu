package com.cortezromeo.taixiu.api;

import java.util.Map;

public record SessionSnapshot(long id, int dice1, int dice2, int dice3, TaiXiuResult result,
                              CurrencyType currency, Map<String, Long> taiBets, Map<String, Long> xiuBets) {
    public SessionSnapshot {
        taiBets = Map.copyOf(taiBets);
        xiuBets = Map.copyOf(xiuBets);
    }
}
